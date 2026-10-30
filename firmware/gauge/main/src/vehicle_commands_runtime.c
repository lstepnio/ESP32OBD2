#include "vehicle_commands_runtime.h"
#include "esp_random.h"
#include "esp_attr.h"
#include "nvs.h"
#include "transfer_gate.h"
#include "ble_mgr.h"
#include "adapter_status.h"
#include "worker_health.h"
#include "vehicle_idle_coolant.h"
#include "jeep_idle_parked_test.h"
#include "sdkconfig.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include <string.h>

/* CPU-only driver buffers and history stay out of scarce internal Wi-Fi RAM. */
static EXT_RAM_BSS_ATTR vehicle_commands_t commands;
static EXT_RAM_BSS_ATTR uint8_t            control_response[ELM_RESPONSE_CAPACITY + 1];
static uint8_t                             cached_status[VEHICLE_COMMAND_KIND_COUNT][32];
static uint8_t                             cached_ui_flags[VEHICLE_COMMAND_KIND_COUNT];
static struct
{
    bool     held;
    uint32_t at;
} burnout_hold;
static portMUX_TYPE  cache_lock = portMUX_INITIALIZER_UNLOCKED;
static StaticQueue_t queue_storage;
static uint8_t       queue_bytes[4 * sizeof(vehicle_command_request_t)];
static QueueHandle_t requests;
/* Primary-worker owned. No new task, callback work, sample history or heap. */
static struct
{
    bool     configured, valid, attempted;
    double   limit_c, value_c;
    uint32_t observed_at, attempted_at, link, revision, identity;
} coolant;
#if CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST || CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST
static jeep_idle_parked_test_t parked;
static bool                    parked_enabled, identity_attempted, speed_attempted, rpm_attempted;
static uint8_t                 identity_step;
static uint32_t                identity_attempt_at, speed_attempt_at, rpm_attempt_at;

static bool parked_read(ble_obd_ctx_t             *adapter,
                        vehicle_command_context_t *context,
                        jeep_test_input_t          input,
                        uint32_t                  *now)
{
    static const uint8_t pids[]     = {4, 6, 12, 13};
    elm_payload_t        sample     = {0};
    elm_result_t         result     = ELM_ADAPTER_ERROR;
    uint32_t             generation = adapter_status_generation(0);
    int                  err        = ble_obd_read_primary_input(adapter, input < JEEP_TEST_RPM ? 9 : 1, pids[input],
                                                                 input < JEEP_TEST_RPM ? 1200 : 300, &result, &sample);
    *now                            = (uint32_t)(xTaskGetTickCount() * portTICK_PERIOD_MS);
    if (generation != context->link || generation != adapter_status_generation(0) || !ble_obd_is_connected(adapter))
    {
        context->connected = false;
        jeep_idle_parked_test_scope(&parked, *context);
        return false;
    }
    return jeep_idle_parked_test_feed(&parked, input, !err && result == ELM_OK ? &sample : NULL, *now);
}

static void parked_context(vehicle_command_context_t *context, uint32_t now)
{
    if (!parked_enabled)
        return;
    if (!context->connected || context->revision != parked.revision || context->link != parked.link)
    {
        identity_attempted = speed_attempted = rpm_attempted = false;
        identity_step                                        = 0;
    }
    jeep_idle_parked_test_scope(&parked, *context);
    jeep_idle_parked_test_context(&parked, context, &commands.states[0], now);
#if CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST
    context->parked_idle_test = true;
#else
    context->parked_idle_test = false;
#endif
#if CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST
    jeep_brake_owner_test_context(&parked, context, commands.states, now);
#endif
}

static void parked_poll(ble_obd_ctx_t *adapter, vehicle_command_context_t *context, bool stop, uint32_t *now)
{
    if (!parked_enabled || !context->connected || !adapter || stop || commands.states[0].pending_stop ||
        commands.states[0].phase == VEHICLE_STOPPING)
        return;
    vehicle_command_state_t *idle = &commands.states[0];
    /* Engine guard reads must not corrupt a routed brake-controller transaction. */
    if (commands.worker_kind == VEHICLE_ABS_TCS || commands.worker_kind == VEHICLE_BURNOUT)
        return;
    bool brake_recorded = (commands.marker.mask & 6U) && parked.calibration && parked.cvn;
    bool busy =
        commands.worker_kind || idle->phase == VEHICLE_ACTIVE || idle->phase == VEHICLE_VERIFYING || brake_recorded;
    if (!busy && (identity_step || (!identity_attempted || *now - identity_attempt_at >= 30000)))
    {
        if (!identity_step)
        {
            identity_attempted  = true;
            identity_attempt_at = *now;
            identity_step       = parked_read(adapter, context, JEEP_TEST_CALIBRATION, now) ? 1 : 0;
        }
        else
        {
            bool matched  = parked_read(adapter, context, JEEP_TEST_CVN, now);
            identity_step = 0;
            ESP_LOGI("idle_test", "primary identity %s; parked test only", matched ? "matched" : "not matched");
        }
    }
    if (!parked.calibration || !parked.cvn || !context->connected)
        return;
    if (!speed_attempted || *now - speed_attempt_at >= 500)
    {
        speed_attempted  = true;
        speed_attempt_at = *now;
        parked_read(adapter, context, JEEP_TEST_SPEED, now);
        parked_context(context, *now);
        vehicle_commands_observe(&commands, *context, *now);
    }
    if (!context->connected || idle->pending_stop)
        return;
    if (!rpm_attempted || *now - rpm_attempt_at >= 500)
    {
        rpm_attempted  = true;
        rpm_attempt_at = *now;
        parked_read(adapter, context, JEEP_TEST_RPM, now);
    }
    parked_context(context, *now);
}
#endif
static uint32_t u32(const uint8_t *p)
{
    return p[0] | (uint32_t)p[1] << 8 | (uint32_t)p[2] << 16 | (uint32_t)p[3] << 24;
}
static void put32(uint8_t *p, uint32_t n)
{
    for (unsigned i = 0; i < 4; ++i)
        p[i] = n >> (8 * i);
}
static bool save(const vehicle_command_marker_t *marker, void *unused)
{
    (void)unused;
    uint8_t      bytes[VEHICLE_COMMAND_MARKER_SIZE];
    size_t       length = vehicle_command_marker_encode(marker, bytes);
    nvs_handle_t handle;
    if (nvs_open("veh_commands", NVS_READWRITE, &handle) != ESP_OK)
        return false;
    esp_err_t err = nvs_set_blob(handle, "intent_v1", bytes, length);
    if (err == ESP_OK)
        err = nvs_commit(handle);
    nvs_close(handle);
    return err == ESP_OK;
}
static void publish(vehicle_command_context_t context, uint32_t now)
{
    uint8_t snapshot[VEHICLE_COMMAND_KIND_COUNT][32];
    uint8_t ui_flags[VEHICLE_COMMAND_KIND_COUNT];
    for (unsigned i = 0; i < VEHICLE_COMMAND_KIND_COUNT; ++i)
    {
        vehicle_commands_status(&commands, i + 1, context, now, snapshot[i]);
        ui_flags[i] = vehicle_commands_ui_flags(i + 1, context);
        if (i == 2 && (context.supervised_mask & 4U) && commands.states[i].release_attempted)
            ui_flags[i] |= 32U;
        if (i && (context.supervised_start_mask & (1U << i)) && context.supervised_at &&
            now - context.supervised_at <= 2000 && !commands.worker_kind && !commands.states[i].pending_stop &&
            !commands.states[i].pending_start)
            ui_flags[i] |= 16U;
    }
#if CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST || CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST
    static uint32_t logged_sequence = UINT32_MAX;
    if (parked_enabled && logged_sequence != commands.sequence)
    {
        logged_sequence = commands.sequence;
        ESP_LOGI("idle_test", "phase=%u reason=%u operation=%lu at_ms=%lu guard_age_ms=%lu remaining_s=%u",
                 snapshot[0][2], snapshot[0][3], (unsigned long)u32(snapshot[0] + 4), (unsigned long)now,
                 (unsigned long)(now - context.guard_at), snapshot[0][24] | (unsigned)snapshot[0][25] << 8);
    }
#endif
    portENTER_CRITICAL(&cache_lock);
    memcpy(cached_status, snapshot, sizeof(snapshot));
    memcpy(cached_ui_flags, ui_flags, sizeof(ui_flags));
    portEXIT_CRITICAL(&cache_lock);
}
esp_err_t vehicle_commands_runtime_init(const config_runtime_t *runtime)
{
#if CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST || CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST
    memset(&parked, 0, sizeof(parked));
    parked_enabled     = runtime && runtime->source_count && !runtime->sources[0].simulated &&
                         !runtime->sources[0].transmission && runtime->sources[0].address[0];
    identity_attempted = speed_attempted = rpm_attempted = false;
    identity_step                                        = 0;
#endif
    portENTER_CRITICAL(&cache_lock);
    memset(&burnout_hold, 0, sizeof(burnout_hold));
    portEXIT_CRITICAL(&cache_lock);
    memset(&coolant, 0, sizeof(coolant));
    coolant.configured              = vehicle_idle_coolant_limit(runtime, &coolant.limit_c);
    vehicle_command_marker_t marker = {0};
    bool                     ready  = true;
    nvs_handle_t             handle;
    esp_err_t                opened = nvs_open("veh_commands", NVS_READONLY, &handle);
    if (opened == ESP_OK)
    {
        uint8_t   bytes[VEHICLE_COMMAND_MARKER_SIZE];
        size_t    n      = sizeof(bytes);
        esp_err_t loaded = nvs_get_blob(handle, "intent_v1", bytes, &n);
        if (loaded == ESP_OK)
        {
            ready = vehicle_command_marker_decode(bytes, n, &marker);
        }
        else if (loaded != ESP_ERR_NVS_NOT_FOUND)
            ready = false;
        nvs_close(handle);
    }
    else if (opened != ESP_ERR_NVS_NOT_FOUND)
        ready = false;
    uint32_t boot = esp_random();
    if (!boot)
        boot = 1;
    vehicle_commands_init(&commands, boot, save, NULL, &marker, ready);
    requests = xQueueCreateStatic(4, sizeof(vehicle_command_request_t), queue_bytes, &queue_storage);
    publish((vehicle_command_context_t){0}, 0);
    return requests ? ESP_OK : ESP_ERR_NO_MEM;
}
static bool enqueue(const vehicle_command_request_t *request)
{
    if (xQueueSend(requests, request, 0) == pdTRUE)
        return true;
    worker_health_queue_drop(WORKER_ECM);
    return false;
}
static bool submit(const uint8_t *bytes, size_t length, bool gauge_confirmed)
{
    vehicle_command_request_t request;
    if (!requests || !transfer_gate_vehicle_allowed() || ble_mgr_is_paused() ||
        !vehicle_command_decode(bytes, length, &request))
        return false;
    /* A readable interface is not an enabled controller. Fail closed at
     * admission so old/unqualified profiles never acquire a worker lease. */
    uint8_t status[32];
    vehicle_commands_runtime_status(request.kind, status);
    if (!(status[27] & 1U))
        return false;
#if CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST || CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST
    if (parked_enabled && request.verb == VEHICLE_START && !gauge_confirmed)
        return false;
#endif
    if (request.kind == VEHICLE_BURNOUT && request.verb == VEHICLE_START && !gauge_confirmed)
        return false;
    request.gauge_confirmed = gauge_confirmed;
    return enqueue(&request);
}
bool vehicle_commands_runtime_submit(const uint8_t *bytes, size_t length)
{
    return submit(bytes, length, false);
}
size_t vehicle_commands_runtime_status(uint8_t kind, uint8_t out[32])
{
    if (kind < 1 || kind > VEHICLE_COMMAND_KIND_COUNT || !out)
        return 0;
    portENTER_CRITICAL(&cache_lock);
    memcpy(out, cached_status[kind - 1], 32);
    portEXIT_CRITICAL(&cache_lock);
    return 32;
}
void vehicle_commands_runtime_burnout_hold(bool held)
{
    uint32_t now = (uint32_t)(xTaskGetTickCount() * portTICK_PERIOD_MS);
    portENTER_CRITICAL(&cache_lock);
    burnout_hold.held = held;
    burnout_hold.at   = now;
    portEXIT_CRITICAL(&cache_lock);
}
static void burnout_context(vehicle_command_context_t *context)
{
    portENTER_CRITICAL(&cache_lock);
    context->burnout_held    = burnout_hold.held;
    context->burnout_hold_at = burnout_hold.at;
    portEXIT_CRITICAL(&cache_lock);
}
static void coolant_context(vehicle_command_context_t *context)
{
    context->coolant_configured = coolant.configured;
    context->coolant_valid = coolant.valid && coolant.link == context->link && coolant.revision == context->revision &&
                             coolant.identity == context->identity;
    context->coolant_c     = coolant.value_c;
    context->coolant_limit_c = coolant.limit_c;
    context->coolant_at      = coolant.observed_at;
}

bool vehicle_commands_runtime_worker(unsigned                  source,
                                     ble_obd_ctx_t            *adapter,
                                     vehicle_command_context_t context,
                                     uint32_t                  now)
{
    if (source != 0 || !requests)
        return false;
    /* Share the existing atomic transfer gate. A cached phase check alone has a
     * check/claim race with configuration, OTA and Wi-Fi startup. Keep ownership
     * through active control, including gaps between adapter jobs. */
    if (transfer_gate_current() != TRANSFER_GATE_VEHICLE_COMMAND && !transfer_gate_claim(TRANSFER_GATE_VEHICLE_COMMAND))
        return false;
    vehicle_command_request_t queued[4];
    unsigned                  queued_count = 0;
    bool                      queued_stop = false, queued_idle_start = false;
    while (queued_count < 4 && xQueueReceive(requests, &queued[queued_count], 0) == pdTRUE)
    {
        queued_stop |= queued[queued_count].verb == VEHICLE_STOP;
        queued_idle_start |= queued[queued_count].kind == VEHICLE_IDLE && queued[queued_count].verb == VEHICLE_START;
        ++queued_count;
    }
#if CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST || CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST
    parked_context(&context, now);
#endif
    if (coolant.link != context.link || coolant.revision != context.revision || coolant.identity != context.identity ||
        !context.connected)
    {
        coolant.valid = coolant.attempted = false;
    }
    coolant_context(&context);
    burnout_context(&context);
    /* Once a running guard fails, latch Stop before a later cooler sample can
     * erase it. Never delay a queued Stop behind another guard read. */
    vehicle_commands_observe(&commands, context, now);
#if CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST || CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST
    parked_poll(adapter, &context, queued_stop, &now);
    coolant_context(&context);
    burnout_context(&context);
    vehicle_commands_observe(&commands, context, now);
#endif
    vehicle_command_state_t *idle = &commands.states[0];
    if (commands.worker_kind != VEHICLE_ABS_TCS && commands.worker_kind != VEHICLE_BURNOUT &&
        (!commands.worker_kind || context.parked_idle_test) && !idle->pending_stop && !queued_stop && adapter &&
        context.connected &&
        ((context.qualified_mask | (context.parked_idle_test ? 1U : 0U)) & context.identity_mask & 1U) &&
        coolant.configured && (queued_idle_start || idle->phase == VEHICLE_ACTIVE || context.parked_idle_test) &&
        (!coolant.attempted || now - coolant.attempted_at >= VEHICLE_IDLE_COOLANT_POLL_MS))
    {
        elm_result_t  result = ELM_ADAPTER_ERROR;
        elm_payload_t sample = {0};
        coolant.attempted    = true;
        coolant.attempted_at = now;
        uint32_t generation  = adapter_status_generation(0);
        int      error   = context.parked_idle_test ? ble_obd_read_primary_input(adapter, 1, 5, 300, &result, &sample)
                                                    : ble_obd_read_pid(adapter, 1, 5, 0x7e8, 300, &result, &sample);
        now              = (uint32_t)(xTaskGetTickCount() * portTICK_PERIOD_MS);
        coolant.link     = context.link;
        coolant.revision = context.revision;
        coolant.identity = context.identity;
        bool valid = !error && result == ELM_OK && sample.has_responder && sample.responder == 0x7e8 &&
                     sample.length == 1 && generation == context.link && generation == adapter_status_generation(0) &&
                     ble_obd_is_connected(adapter);
        /* Retain the last real sample and its original age. A failed read must
         * neither refresh freshness nor erase a known high temperature. */
        if (valid)
        {
            coolant.valid       = true;
            coolant.value_c     = (double)sample.bytes[0] - 40.0;
            coolant.observed_at = now;
        }
        if (generation != adapter_status_generation(0) || !ble_obd_is_connected(adapter))
            context.connected = false;
        coolant_context(&context);
        burnout_context(&context);
    }
#if CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST || CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST
    parked_context(&context, now);
#endif
    /* Stop submissions do not depend on a passing coolant guard. */
    for (unsigned i = 0; i < queued_count; ++i)
    {
        bool accepted = vehicle_commands_submit(&commands, queued[i], context, now);
        if (accepted && queued[i].verb == VEHICLE_OWNER_CONFIRM)
            ESP_LOGI("brake_test", "owner checked kind=%u operation=%lu", queued[i].kind,
                     (unsigned long)queued[i].operation);
#if CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST || CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST
        if (accepted && context.parked_idle_test && queued[i].kind == VEHICLE_IDLE && queued[i].verb == VEHICLE_START)
            jeep_idle_parked_test_started(&parked, idle);
#else
        (void)accepted;
#endif
    }
    bool                had_lease = commands.worker_kind != 0;
    jeep_idle_command_t command   = vehicle_commands_next(&commands, context, now);
    if (command.text)
    {
        uint8_t *response = control_response;
        size_t   length   = sizeof(control_response);
        burnout_context(&context);
        uint32_t before_io = (uint32_t)(xTaskGetTickCount() * portTICK_PERIOD_MS);
        uint32_t spent     = before_io - now;
        bool     hold_lost = commands.worker_kind == VEHICLE_BURNOUT &&
                             commands.driver.abs.request.kind == JEEP_ABS_DISABLE &&
                             (!context.burnout_held || !context.burnout_hold_at ||
                              before_io - context.burnout_hold_at > VEHICLE_BURNOUT_HOLD_MS);
        if (hold_lost)
        {
            /* Persistence may have outlived the last physical-contact proof. */
            vehicle_commands_observe(&commands, context, before_io);
            vehicle_commands_transport_failed(&commands, before_io);
        }
        else if (!adapter || spent >= JEEP_IDLE_IO_TIMEOUT_MS ||
                 ble_obd_control_exchange(adapter, command.text, JEEP_IDLE_IO_TIMEOUT_MS - spent, response, &length) !=
                     0)
            vehicle_commands_transport_failed(&commands, (uint32_t)(xTaskGetTickCount() * portTICK_PERIOD_MS));
        else
        {
            /* Use elapsed worker time, never the pre-IO timestamp. */
            uint32_t completed = (uint32_t)(xTaskGetTickCount() * portTICK_PERIOD_MS);
            burnout_context(&context);
            vehicle_commands_feed(&commands, command.ticket, response, length, context, completed);
        }
    }
#if CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST || CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST
    if ((command.text || had_lease) && !commands.worker_kind &&
        commands.driver.idle.request.kind == JEEP_IDLE_RECOVERY_REPORT)
        ESP_LOGI("idle_test", "recovery phase=%u result=%u error=%u nrc=%02x", commands.driver.idle.phase,
                 commands.driver.idle.result, commands.driver.idle.error, commands.driver.idle.negative_code);
#endif
    if ((command.text || had_lease) && !commands.worker_kind && adapter &&
        ble_obd_control_restore(adapter, JEEP_IDLE_IO_TIMEOUT_MS, control_response, sizeof(control_response)) != 0)
    {
        context.connected = false;
        vehicle_commands_observe(&commands, context, (uint32_t)(xTaskGetTickCount() * portTICK_PERIOD_MS));
    }
    publish(context, (uint32_t)(xTaskGetTickCount() * portTICK_PERIOD_MS));
    bool reserved = commands.worker_kind != 0 || command.text != NULL;
    if (!commands.worker_kind && vehicle_commands_maintenance_allowed(commands.states, false))
        transfer_gate_release(TRANSFER_GATE_VEHICLE_COMMAND);
    return reserved;
}

bool vehicle_commands_runtime_intent(uint8_t kind, bool stop, uint8_t preset, uint16_t seconds)
{
    uint8_t status[32];
    if (!vehicle_commands_runtime_status(kind, status) || !(status[27] & 1U))
        return false;
    bool can = stop ? status[2] == VEHICLE_STARTING || status[2] == VEHICLE_ACTIVE ||
                          (kind != VEHICLE_IDLE && status[2] == VEHICLE_UNKNOWN)
                    : status[2] == VEHICLE_READY || status[2] == VEHICLE_RESTORED || status[2] == VEHICLE_REJECTED;
    if (!can || (!stop && u32(status + 28) == UINT32_MAX))
        return false;
    uint8_t bytes[24] = {kind == VEHICLE_BURNOUT ? 2 : 1, kind, stop ? VEHICLE_STOP : VEHICLE_START, preset};
    put32(bytes + 4, stop ? u32(status + 4) : u32(status + 28) + 1);
    put32(bytes + 8, u32(status + 8));
    put32(bytes + 12, u32(status + 12));
    put32(bytes + 16, u32(status + 16));
    bytes[20] = seconds;
    bytes[21] = seconds >> 8;
    return submit(bytes, sizeof(bytes), true);
}

bool vehicle_commands_runtime_maintenance_allowed(bool configuration)
{
    if (vehicle_commands_runtime_pending())
        return false;
    vehicle_command_state_t values[VEHICLE_COMMAND_KIND_COUNT] = {0};
    portENTER_CRITICAL(&cache_lock);
    for (unsigned i = 0; i < VEHICLE_COMMAND_KIND_COUNT; ++i)
    {
        values[i].phase             = cached_status[i][2];
        values[i].request.operation = u32(cached_status[i] + 4);
    }
    portEXIT_CRITICAL(&cache_lock);
    return vehicle_commands_maintenance_allowed(values, configuration);
}

size_t vehicle_commands_runtime_ui_status(uint8_t kind, uint8_t out[32])
{
    if (!out || kind < 1 || kind > VEHICLE_COMMAND_KIND_COUNT)
        return 0;
    portENTER_CRITICAL(&cache_lock);
    memcpy(out, cached_status[kind - 1], 32);
    out[27] |= cached_ui_flags[kind - 1];
    portEXIT_CRITICAL(&cache_lock);
    return 32;
}

uint32_t vehicle_commands_runtime_poll_reserve(unsigned source, uint32_t horizon, uint32_t now)
{
#if CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST || CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST
    if (source != 0 || !parked_enabled)
        return 0;
    uint32_t reserve = 0;
    bool     busy    = commands.worker_kind || commands.states[0].phase == VEHICLE_ACTIVE ||
                       commands.states[0].phase == VEHICLE_VERIFYING ||
                       ((commands.marker.mask & 6U) && parked.calibration && parked.cvn);
    if (!busy && (identity_step || !identity_attempted || now - identity_attempt_at + horizon >= 30000))
        reserve += 1200;
    if (!speed_attempted || now - speed_attempt_at + horizon >= 500)
        reserve += 300;
    if (!rpm_attempted || now - rpm_attempt_at + horizon >= 500)
        reserve += 300;
    if (coolant.configured &&
        (!coolant.attempted || now - coolant.attempted_at + horizon >= VEHICLE_IDLE_COOLANT_POLL_MS))
        reserve += 300;
    return reserve;
#else
    (void)source;
    (void)horizon;
    (void)now;
    return 0; /* Ordinary builds have no qualified control profiles. */
#endif
}

bool vehicle_commands_runtime_pending(void)
{
    return requests && uxQueueMessagesWaiting(requests) != 0;
}
bool vehicle_commands_runtime_confirm(uint8_t kind)
{
    uint8_t status[32];
    if (!requests || kind == VEHICLE_IDLE || !vehicle_commands_runtime_ui_status(kind, status) ||
        !(status[27] & VEHICLE_UI_OWNER_TEST) || !(status[27] & 1U) || !u32(status + 4) ||
        !transfer_gate_vehicle_allowed() || ble_mgr_is_paused())
        return false;
    vehicle_command_request_t request = {.kind            = kind,
                                         .verb            = VEHICLE_OWNER_CONFIRM,
                                         .operation       = u32(status + 4),
                                         .revision        = u32(status + 8),
                                         .link            = u32(status + 12),
                                         .boot            = u32(status + 16),
                                         .gauge_confirmed = true};
    return enqueue(&request);
}
