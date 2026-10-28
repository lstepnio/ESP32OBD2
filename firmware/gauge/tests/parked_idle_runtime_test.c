#include "vehicle_commands_runtime.h"
#include "adapter_status.h"
#include "transfer_gate.h"
#include "freertos/queue.h"
#include "nvs.h"
#include <assert.h>
#include <string.h>
#include <stdio.h>

static uint32_t clock_ms, generation;
static unsigned engine_rpm, speed_kph, coolant_c, starts, returns, reads;
static bool     connected, bad_identity, missing_coolant, lose_start_reply, control_active, stored;
static uint8_t  journal[28];
static unsigned header, reports;
static bool recovery_only, fail_save, fail_report, negative_report, diagnostic_session, fail_session, negative_session;
static ble_obd_ctx_t *const adapter = (ble_obd_ctx_t *)1;

uint32_t xTaskGetTickCount(void)
{
    return clock_ms;
}
uint32_t esp_random(void)
{
    return 17;
}
uint32_t adapter_status_generation(unsigned source)
{
    assert(source == 0);
    return generation;
}
bool ble_mgr_is_paused(void)
{
    return false;
}
bool ble_obd_is_connected(ble_obd_ctx_t *a)
{
    assert(a == adapter);
    return connected;
}
QueueHandle_t xQueueCreateStatic(unsigned n, unsigned size, uint8_t *bytes, StaticQueue_t *q)
{
    *q = (StaticQueue_t){bytes, size, n, 0};
    return q;
}
int xQueueSend(QueueHandle_t q, const void *item, TickType_t wait)
{
    assert(!wait);
    if (q->count == q->capacity)
        return 0;
    memcpy(q->bytes + q->count++ * q->size, item, q->size);
    return pdTRUE;
}
int xQueueReceive(QueueHandle_t q, void *item, TickType_t wait)
{
    assert(!wait);
    if (!q->count)
        return 0;
    memcpy(item, q->bytes, q->size);
    --q->count;
    memmove(q->bytes, q->bytes + q->size, q->count * q->size);
    return pdTRUE;
}
unsigned uxQueueMessagesWaiting(QueueHandle_t q)
{
    return q->count;
}
esp_err_t nvs_open(const char *name, unsigned mode, nvs_handle_t *out)
{
    assert(!strcmp(name, "veh_commands"));
    *out = 1;
    return mode == NVS_READONLY && !stored ? ESP_ERR_NVS_NOT_FOUND : ESP_OK;
}
esp_err_t nvs_get_blob(nvs_handle_t h, const char *key, void *out, size_t *length)
{
    assert(h && !strcmp(key, "intent_v1") && *length == sizeof(journal));
    memcpy(out, journal, *length);
    return ESP_OK;
}
esp_err_t nvs_set_blob(nvs_handle_t h, const char *key, const void *in, size_t length)
{
    assert(h && !strcmp(key, "intent_v1") && length == sizeof(journal));
    if (fail_save)
        return -1;
    memcpy(journal, in, length);
    stored = true;
    return ESP_OK;
}
esp_err_t nvs_commit(nvs_handle_t h)
{
    assert(h);
    return ESP_OK;
}
void nvs_close(nvs_handle_t h)
{
    assert(h);
}
int ble_obd_read_primary_input(ble_obd_ctx_t *a,
                               uint8_t        mode,
                               uint8_t        pid,
                               uint32_t       timeout,
                               elm_result_t  *result,
                               elm_payload_t *p)
{
    assert(a == adapter && timeout && timeout <= 1200);
    ++reads;
    clock_ms += 40;
    header  = 0x7e0;
    *p      = (elm_payload_t){.has_responder = true, .responder = 0x7e8};
    *result = ELM_OK;
    if (mode == 9 && pid == 4)
    {
        p->length = 16;
        memcpy(p->bytes, bad_identity ? "68059434AE" : "68059434AD", 10);
    }
    else if (mode == 9 && pid == 6)
    {
        p->length = 4;
        memcpy(p->bytes, "\xca\x36\x37\x8c", 4);
    }
    else if (mode == 1 && pid == 12)
    {
        p->length   = 2;
        p->bytes[0] = engine_rpm * 4 >> 8;
        p->bytes[1] = engine_rpm * 4;
    }
    else if (mode == 1 && pid == 13)
    {
        p->length   = 1;
        p->bytes[0] = speed_kph;
    }
    else if (mode == 1 && pid == 5)
    {
        if (missing_coolant)
        {
            *result = ELM_NO_DATA;
            return -1;
        }
        p->length   = 1;
        p->bytes[0] = coolant_c + 40;
    }
    else
        assert(0);
    return 0;
}
int ble_obd_read_pid(ble_obd_ctx_t *a,
                     uint8_t        mode,
                     uint16_t       pid,
                     uint32_t       ecu,
                     uint32_t       timeout,
                     elm_result_t  *result,
                     elm_payload_t *p)
{
    (void)a;
    (void)mode;
    (void)pid;
    (void)ecu;
    (void)timeout;
    (void)result;
    (void)p;
    assert(0 && "A broadcast read must not replace the active 7E0 control route");
    return -1;
}
int ble_obd_control_exchange(ble_obd_ctx_t *a, const char *text, uint32_t timeout, uint8_t *out, size_t *length)
{
    assert(a == adapter && timeout && timeout <= 1200);
    if (recovery_only)
        assert(!strncmp(text, "AT ", 3) || !strcmp(text, "10 92 1") || !strcmp(text, "33 05 1"));
    const char *reply = "OK\r>";
    clock_ms += 40;
    if (!strcmp(text, "AT SH 7E0"))
        header = 0x7e0;
    else if (!strcmp(text, "AT FC SH 7E0") || !strcmp(text, "AT CRA 7E8"))
    {
    }
    else
    {
        assert(header == 0x7e0);
        if (!strcmp(text, "10 92 1"))
        {
            if (fail_session)
                return -1;
            diagnostic_session = !negative_session;
            reply              = negative_session ? "7E8 03 7F 10 22\r>" : "7E8 02 50 92\r>";
        }
        else if (!strcmp(text, "33 05 1"))
        {
            ++reports;
            if (fail_report)
                return -1;
            reply = !diagnostic_session ? "7E8 03 7F 33 80\r>"
                    : negative_report   ? "7E8 03 7F 33 22\r>"
                    : control_active    ? "7E8 03 73 05 02\r>"
                                        : "7E8 03 73 05 01\r>";
        }
        else if (!strcmp(text, "31 05 06 80 1"))
        {
            ++starts;
            control_active = true;
            engine_rpm     = 1700;
            if (lose_start_reply)
                return -1;
            reply = "7E8 02 71 05\r>";
        }
        else if (!strcmp(text, "32 05 1"))
        {
            ++returns;
            control_active = false;
            engine_rpm     = 700;
            reply          = "7E8 02 72 05\r>";
        }
        else
            assert(0);
    }
    assert(*length >= strlen(reply));
    *length = strlen(reply);
    memcpy(out, reply, *length);
    return 0;
}
int ble_obd_control_restore(ble_obd_ctx_t *a, uint32_t timeout, uint8_t *out, size_t capacity)
{
    assert(a == adapter && timeout && out && capacity >= 513);
    header = 0x7df;
    return 0;
}

static void tick(void)
{
    vehicle_command_context_t c = {.revision = 7, .link = generation, .connected = connected};
    vehicle_commands_runtime_worker(0, adapter, c, clock_ms);
    clock_ms += 100;
}
static uint8_t status[32];
static void    phase(unsigned expected)
{
    assert(vehicle_commands_runtime_status(VEHICLE_IDLE, status));
    assert(status[2] == expected);
}
static void run(unsigned count)
{
    for (unsigned i = 0; i < count; ++i)
        tick();
}
static void initialize(void)
{
    /* Model the recorded PCM restart/session expiry: status is rejected until
     * the captured diagnostic-session entry has been acknowledged. */
    diagnostic_session = false;
    config_runtime_t r = {.source_count = 1, .pid_count = 2, .page_count = 1, .alert_count = 1};
    strcpy(r.sources[0].address, "00:00:00:00:00:01");
    r.pids[0] = (runtime_pid_t){.service = 1, .responder = 0x7e8, .obd.pid = 5};
    strcpy(r.pids[0].unit, "°C");
    /* Only RPM is displayed; coolant protection must poll independently. */
    r.pids[1]   = (runtime_pid_t){.service = 1, .responder = 0x7e8, .obd.pid = 12};
    r.pages[0]  = (runtime_page_t){.pid_count = 1, .pid_indices = {1}};
    r.alerts[0] = (runtime_alert_t){.above = true, .has_warning = true, .warning = 105};
    assert(vehicle_commands_runtime_init(&r) == ESP_OK);
}
static void setup(bool wrong)
{
    transfer_gate_release(TRANSFER_GATE_VEHICLE_COMMAND);
    clock_ms   = 100;
    generation = 9;
    engine_rpm = 700;
    speed_kph  = 0;
    coolant_c  = 90;
    starts = returns = reads = reports = 0;
    recovery_only = fail_save = fail_report = negative_report = fail_session = negative_session = false;
    connected                                                                                   = true;
    bad_identity                                                                                = wrong;
    missing_coolant = lose_start_reply = control_active = stored = false;
    memset(journal, 0, sizeof(journal));
    initialize();
    run(20);
}
static void start(void)
{
    phase(VEHICLE_READY);
    assert((status[27] & 25) == 9); /* Available, local Start, fresh coolant. */
    assert(vehicle_commands_runtime_intent(VEHICLE_IDLE, false, 1, 300));
    assert(vehicle_commands_runtime_pending());
    run(20);
    phase(VEHICLE_ACTIVE);
    assert(starts == 1 && status[24] + 256U * status[25] > 290 && journal[1] == 1);
}
int main(void)
{
    assert(vehicle_commands_runtime_poll_reserve(1, 1500, 0) == 0);
    setup(true);
    phase(VEHICLE_UNAVAILABLE);
    assert(!vehicle_commands_runtime_intent(VEHICLE_IDLE, false, 1, 300) && !starts);
    setup(false);
    phase(VEHICLE_READY);
    uint8_t request[24] = {1, 1, 1, 1, 1, 0, 0, 0, 7, 0, 0, 0, 9, 0, 0, 0, 17, 0, 0, 0, 44, 1, 0, 0};
    assert(!vehicle_commands_runtime_submit(request, sizeof(request)));
    assert(!vehicle_commands_runtime_intent(VEHICLE_ABS_TCS, false, 0, 0));
    unsigned before = reads;
    assert(!vehicle_commands_runtime_worker(1, adapter, (vehicle_command_context_t){0}, clock_ms));
    assert(before == reads);
    start();
    memcpy(request + 4, status + 4, 4);
    request[2] = 2;
    request[3] = request[20] = request[21] = 0;
    assert(vehicle_commands_runtime_submit(request, sizeof(request)));
    before = reads;
    tick();
    assert(reads == before); /* Stop gets the next transaction. */
    run(40);
    phase(VEHICLE_RESTORED);
    assert(returns == 1 && !journal[1]);
    setup(false);
    start();
    speed_kph = 2;
    run(20);
    assert(returns == 1);
    speed_kph = 0;
    run(30);
    phase(VEHICLE_RESTORED);
    setup(false);
    start();
    coolant_c = 105;
    run(40);
    phase(VEHICLE_RESTORED);
    assert(returns == 1);
    setup(false);
    start();
    missing_coolant = true;
    run(20);
    phase(VEHICLE_ACTIVE);
    assert(status[27] & 4);
    for (unsigned i = 0; i < 550; ++i)
        tick();
    phase(VEHICLE_RESTORED);
    assert(returns == 1);
    setup(false);
    start();
    for (unsigned i = 0; i < 2100; ++i)
        tick();
    phase(VEHICLE_RESTORED);
    assert(returns == 1); /* Full five-minute timer, no user input. */
    setup(false);
    lose_start_reply = true;
    assert(vehicle_commands_runtime_intent(VEHICLE_IDLE, false, 1, 300));
    assert(vehicle_commands_runtime_pending());
    run(20);
    phase(VEHICLE_UNKNOWN);
    assert(journal[1] == 1 && starts == 1);
    engine_rpm = 700;
    run(50);
    phase(VEHICLE_UNKNOWN);
    assert(!vehicle_commands_runtime_intent(VEHICLE_IDLE, false, 1, 300) && starts == 1);
    /* Reboot preserves the unresolved journal, with a fresh link and no RAM baseline. */
    for (unsigned failure = 0; failure < 8; ++failure)
    {
        setup(false);
        lose_start_reply = true;
        assert(vehicle_commands_runtime_intent(VEHICLE_IDLE, false, 1, 300));
        run(20);
        phase(VEHICLE_UNKNOWN);
        assert(journal[1] == 1);
        recovery_only    = true;
        control_active   = failure == 0;
        bad_identity     = failure == 1;
        negative_report  = failure == 2;
        fail_report      = failure == 3;
        fail_save        = failure == 4;
        fail_session     = failure == 6;
        negative_session = failure == 7;
        speed_kph        = failure == 5 ? 1 : 0;
        engine_rpm       = 700;
        missing_coolant  = true; /* Read-only reconciliation does not need coolant admission. */
        generation++;
        initialize();
        unsigned before_reports = reports;
        run(25);
        phase(VEHICLE_UNKNOWN);
        assert(journal[1] == 1 && !returns && starts == 1);
        if (failure == 1 || failure == 5)
            assert(reports == before_reports);
        /* Retry is bounded, even for active/NRC/transport/storage failures. */
        assert(reports - before_reports <= 1);
        control_active = bad_identity = negative_report = fail_report = fail_save = fail_session = negative_session =
            false;
        speed_kph = 0;
        run(300); /* Identity retry may wait 30 seconds. */
        phase(VEHICLE_RESTORED);
        assert(!journal[1] && !returns && starts == 1);
    }
    setup(false);
    lose_start_reply = true;
    assert(vehicle_commands_runtime_intent(VEHICLE_IDLE, false, 1, 300));
    run(20);
    phase(VEHICLE_UNKNOWN);
    recovery_only  = true;
    control_active = false;
    engine_rpm     = 700;
    generation++;
    initialize();
    before = reports;
    for (unsigned i = 0; i < 20 && reports == before; ++i)
        tick();
    phase(VEHICLE_VERIFYING);
    assert(journal[1] == 1); /* Inactive confirmation alone never clears intent. */
    engine_rpm = 1700;       /* Prevent settled proof, allowing identity age to expire. */
    run(250);
    phase(VEHICLE_UNKNOWN);
    assert(journal[1] == 1 && !returns && starts == 1);
    engine_rpm = 700;
    run(100);
    phase(VEHICLE_RESTORED);
    assert(!journal[1] && !returns && starts == 1);
    puts("primary worker parked idle replay passed; local Start, five-minute expiry, coolant, Stop, restoration, "
         "uncertainty and child isolation");
}
