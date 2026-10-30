#include "vehicle_commands_runtime.h"
#include "adapter_status.h"
#include "transfer_gate.h"
#include "freertos/queue.h"
#include "nvs.h"
#include "worker_health.h"
#include <setjmp.h>
#include <assert.h>
#include <string.h>
#include <stdio.h>

static uint32_t clock_ms, generation;
static unsigned engine_rpm, speed_kph, coolant_c, starts, returns, reads;
static bool     connected, bad_identity, missing_coolant, lose_start_reply, control_active, stored;
static uint8_t  journal[VEHICLE_COMMAND_MARKER_SIZE];
static size_t   journal_length = 28;
static uint8_t  staged_journal[VEHICLE_COMMAND_MARKER_SIZE];
static size_t   staged_length;
static bool     nvs_opened, staged, fail_read_open, fail_write_open, fail_commit;
static unsigned set_attempts, commit_attempts;
static jmp_buf  reboot_boundary;
static enum
{
    NO_POWER_CUT,
    AFTER_SET,
    AFTER_COMMIT
} power_cut;
static bool     release_during_save, released_during_save;
static unsigned burnout_starts, burnout_returns, abs_starts, abs_returns;
static unsigned header, reports;
static bool recovery_only, fail_set, fail_report, negative_report, diagnostic_session, fail_session, negative_session;
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
    assert(!nvs_opened && !staged);
    if ((mode == NVS_READONLY && fail_read_open) || (mode == NVS_READWRITE && fail_write_open))
        return -1;
    if (mode == NVS_READONLY && !stored)
        return ESP_ERR_NVS_NOT_FOUND;
    nvs_opened = true;
    *out       = 1;
    return ESP_OK;
}
esp_err_t nvs_get_blob(nvs_handle_t h, const char *key, void *out, size_t *length)
{
    /* NVS takes buffer capacity and returns the actual stored record length. */
    assert(h && nvs_opened && stored && !strcmp(key, "intent_v1") && *length >= journal_length);
    memcpy(out, journal, journal_length);
    *length = journal_length;
    return ESP_OK;
}
esp_err_t nvs_set_blob(nvs_handle_t h, const char *key, const void *in, size_t length)
{
    assert(h && !strcmp(key, "intent_v1") && (length == 28 || length == VEHICLE_COMMAND_MARKER_SIZE));
    assert(nvs_opened);
    ++set_attempts;
    if (fail_set)
        return -1;
    memcpy(staged_journal, in, length);
    staged_length = length;
    staged        = true;
    if (power_cut == AFTER_SET)
        longjmp(reboot_boundary, 1);
    if (release_during_save && (staged_journal[1] & 4U))
    {
        released_during_save = true;
        release_during_save  = false;
        clock_ms += 600;
        vehicle_commands_runtime_burnout_hold(false);
    }
    return ESP_OK;
}
esp_err_t nvs_commit(nvs_handle_t h)
{
    assert(h && nvs_opened && staged);
    ++commit_attempts;
    if (fail_commit)
        return -1;
    memcpy(journal, staged_journal, staged_length);
    journal_length = staged_length;
    stored         = true;
    staged         = false;
    if (power_cut == AFTER_COMMIT)
        longjmp(reboot_boundary, 1);
    return ESP_OK;
}
void nvs_close(nvs_handle_t h)
{
    assert(h && nvs_opened);
    nvs_opened = staged = false;
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
    else if (!strcmp(text, "AT SH 784"))
        header = 0x784;
    else if (!strcmp(text, "AT SH 620"))
        header = 0x620;
    else if (header == 0x620)
    {
        if (!strcmp(text, "AT FC SH 620") || !strcmp(text, "AT CRA 504"))
        {
        }
        else if (!strcmp(text, "10 92 1"))
            reply = "504 02 50 92\r>";
        else if (!strcmp(text, "31 A4"))
        {
            ++abs_starts;
            reply = "504 02 71 A4\r>";
        }
        else if (!strcmp(text, "32 A4"))
        {
            ++abs_returns;
            reply = "504 02 72 A4\r>";
        }
        else
            assert(0);
    }
    else if (header == 0x784)
    {
        if (!strcmp(text, "AT FC SH 784") || !strcmp(text, "AT CRA 785"))
        {
        }
        else if (!strcmp(text, "10 92 1"))
            reply = "785 02 50 92\r>";
        else if (!strcmp(text, "30 11 07 A0 00"))
        {
            ++burnout_starts;
            reply = "785 03 70 11 07\r>";
        }
        else if (!strcmp(text, "30 11 07 00 00"))
        {
            ++burnout_returns;
            reply = "785 03 70 11 07\r>";
        }
        else
            assert(0);
    }
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
    recovery_only = fail_set = fail_report = negative_report = fail_session = negative_session = false;
    connected                                                                                  = true;
    bad_identity                                                                               = wrong;
    missing_coolant = lose_start_reply = control_active = stored = false;
    memset(journal, 0, sizeof(journal));
    journal_length      = 28;
    release_during_save = released_during_save = false;
    nvs_opened = staged = fail_read_open = fail_write_open = fail_commit = false;
    set_attempts = commit_attempts = 0;
    power_cut                      = NO_POWER_CUT;
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
/* All reboots discard handle-local writes and queued intents, retaining only
 * the last successfully committed marker. No physical flash claim is made. */
static void reboot(void)
{
    nvs_opened = staged = false;
    power_cut           = NO_POWER_CUT;
    transfer_gate_release(TRANSFER_GATE_VEHICLE_COMMAND);
    ++generation;
    initialize();
}
static void persistence_failures_and_reboot_boundaries(void)
{
    for (unsigned failure = 0; failure < 3; ++failure)
    {
        setup(false);
        vehicle_command_marker_t empty = {0};
        journal_length                 = vehicle_command_marker_encode(&empty, journal);
        stored                         = true;
        uint8_t previous[VEHICLE_COMMAND_MARKER_SIZE];
        memcpy(previous, journal, sizeof(previous));
        fail_write_open = failure == 0;
        fail_set        = failure == 1;
        fail_commit     = failure == 2;
        assert(vehicle_commands_runtime_intent(VEHICLE_IDLE, false, 1, 300));
        run(20);
        phase(VEHICLE_REJECTED);
        assert(status[3] == VEHICLE_STORAGE && !starts && !returns && !staged);
        assert(set_attempts == (failure != 0) && commit_attempts == (failure == 2));
        assert(!memcmp(previous, journal, sizeof(previous)));
        fail_write_open = fail_set = fail_commit = false;
        reboot();
        run(20);
        phase(VEHICLE_READY);
        assert(!starts && !returns && !vehicle_commands_runtime_pending());
    }
    for (unsigned boundary = AFTER_SET; boundary <= AFTER_COMMIT; ++boundary)
    {
        setup(false);
        vehicle_command_marker_t empty = {0};
        journal_length                 = vehicle_command_marker_encode(&empty, journal);
        stored                         = true;
        assert(vehicle_commands_runtime_intent(VEHICLE_IDLE, false, 1, 300));
        power_cut = boundary;
        if (!setjmp(reboot_boundary))
        {
            run(20);
            assert(0 && "Expected simulated power cut at the storage boundary");
        }
        assert(!starts && !returns);
        assert(journal[1] == (boundary == AFTER_COMMIT ? 1 : 0));
        recovery_only = true;
        reboot();
        phase(boundary == AFTER_COMMIT ? VEHICLE_UNKNOWN : VEHICLE_UNAVAILABLE);
        assert(!vehicle_commands_runtime_pending());
        run(100);
        phase(boundary == AFTER_COMMIT ? VEHICLE_RESTORED : VEHICLE_READY);
        assert(!starts && !returns && !journal[1]);
    }
    /* A failed read cannot silently replace committed uncertainty with Ready. */
    setup(false);
    start();
    uint8_t previous[VEHICLE_COMMAND_MARKER_SIZE];
    memcpy(previous, journal, sizeof(previous));
    fail_read_open = true;
    recovery_only  = true;
    reboot();
    run(20);
    phase(VEHICLE_UNKNOWN);
    assert(starts == 1 && !returns && !memcmp(previous, journal, sizeof(previous)));
    fail_read_open = false;
    control_active = false;
    engine_rpm     = 700;
    reboot();
    run(100);
    phase(VEHICLE_RESTORED);
    assert(starts == 1 && !returns && !journal[1]);
}
static void queue_saturation_and_drain(void)
{
    setup(false);
    uint32_t drops       = worker_health_snapshot(WORKER_ECM, clock_ms).queue_drops;
    uint32_t child_drops = worker_health_snapshot(WORKER_TCM, clock_ms).queue_drops;
    /* Invalid/disabled requests never reach the queue and are not queue drops. */
    assert(!vehicle_commands_runtime_submit(NULL, 0));
    assert(worker_health_snapshot(WORKER_ECM, clock_ms).queue_drops == drops);
    for (unsigned i = 0; i < 4; ++i)
        assert(vehicle_commands_runtime_intent(VEHICLE_IDLE, false, 1, 300));
    assert(!vehicle_commands_runtime_intent(VEHICLE_IDLE, false, 1, 300));
    assert(worker_health_snapshot(WORKER_ECM, clock_ms).queue_drops == drops + 1);
    assert(!starts && vehicle_commands_runtime_pending());
    run(20);
    phase(VEHICLE_ACTIVE);
    assert(starts == 1 && !vehicle_commands_runtime_pending());
    for (unsigned i = 0; i < 4; ++i)
        assert(vehicle_commands_runtime_intent(VEHICLE_IDLE, true, 0, 0));
    assert(!vehicle_commands_runtime_intent(VEHICLE_IDLE, true, 0, 0));
    assert(worker_health_snapshot(WORKER_ECM, clock_ms).queue_drops == drops + 2);
    unsigned before_reads = reads;
    tick();
    assert(reads == before_reads && !vehicle_commands_runtime_pending());
    run(40);
    phase(VEHICLE_RESTORED);
    assert(starts == 1 && returns == 1);
    run(100); /* Drained or rejected duplicate intents never replay. */
    assert(starts == 1 && returns == 1);
    assert(worker_health_snapshot(WORKER_TCM, clock_ms).queue_drops == child_drops);
    /* Queue capacity is reusable after drain, and reboot drops queued RAM intent. */
    assert(vehicle_commands_runtime_intent(VEHICLE_IDLE, false, 1, 300));
    reboot();
    run(20);
    phase(VEHICLE_READY);
    assert(!vehicle_commands_runtime_pending() && starts == 1 && returns == 1);
}
static void combined_read_only_recovery_pressure(void)
{
    setup(false);
    lose_start_reply = true;
    assert(vehicle_commands_runtime_intent(VEHICLE_IDLE, false, 1, 300));
    run(20);
    phase(VEHICLE_UNKNOWN);
    control_active = true;
    engine_rpm     = 700;
    recovery_only  = true; /* The transport fake rejects every control procedure. */
    reboot();
    /* Reacquire scoped identity before exercising stale queued requests. */
    run(20);
    phase(VEHICLE_UNKNOWN);
    uint8_t request[24] = {1, VEHICLE_IDLE, VEHICLE_STOP};
    memcpy(request + 4, status + 4, 16);
    uint32_t drops = worker_health_snapshot(WORKER_ECM, clock_ms).queue_drops;
    for (unsigned i = 0; i < 4; ++i)
        assert(vehicle_commands_runtime_submit(request, sizeof(request)));
    assert(!vehicle_commands_runtime_submit(request, sizeof(request)));
    assert(worker_health_snapshot(WORKER_ECM, clock_ms).queue_drops == drops + 1);
    assert(!vehicle_commands_runtime_maintenance_allowed(false));
    assert(!vehicle_commands_runtime_maintenance_allowed(true));
    unsigned before_reads = reads;
    uint8_t  ui[32];
    for (unsigned i = 0; i < 100; ++i)
    {
        assert(vehicle_commands_runtime_ui_status(VEHICLE_IDLE, ui) == 32);
        (void)vehicle_commands_runtime_poll_reserve(0, 500, clock_ms);
    }
    assert(reads == before_reads); /* UI/reservation queries are read-only snapshots. */
    /* A new primary connection retires queued link proof and recovery evidence. */
    ++generation;
    control_active = false;
    run(300);
    phase(VEHICLE_RESTORED);
    assert(!vehicle_commands_runtime_pending() && !journal[1]);
    assert(starts == 1 && !returns);
    run(100);
    assert(starts == 1 && !returns); /* No stale/rejected intent is replayed. */
}
static void burnout_release_during_journal_write(void)
{
    transfer_gate_release(TRANSFER_GATE_VEHICLE_COMMAND);
    stored = recovery_only = false;
    release_during_save    = true;
    released_during_save   = false;
    burnout_starts = burnout_returns = 0;
    config_runtime_t runtime         = {0}; /* Disable the parked-idle fixture policy. */
    assert(vehicle_commands_runtime_init(&runtime) == ESP_OK);
    vehicle_command_context_t c = {.revision             = 7,
                                   .link                 = generation,
                                   .identity             = 99,
                                   .qualified_mask       = 4,
                                   .identity_mask        = 4,
                                   .connected            = true,
                                   .guards_fresh_and_met = true};
    c.guard_at                  = clock_ms;
    vehicle_commands_runtime_worker(0, adapter, c, clock_ms);
    vehicle_commands_runtime_burnout_hold(true);
    assert(vehicle_commands_runtime_intent(VEHICLE_BURNOUT, false, 0, 0));
    for (unsigned i = 0; i < 15; ++i)
    {
        if (!released_during_save)
            vehicle_commands_runtime_burnout_hold(true);
        c.guard_at = clock_ms;
        vehicle_commands_runtime_worker(0, adapter, c, clock_ms);
        clock_ms += 100;
    }
    uint8_t snapshot[32];
    assert(vehicle_commands_runtime_status(VEHICLE_BURNOUT, snapshot) == 32);
    assert(released_during_save && !burnout_starts && burnout_returns == 1);
    assert(snapshot[2] == VEHICLE_VERIFYING && journal[1] == 4 && journal_length == 40);
    /* Initialization must block maintenance before the primary worker claims
     * its lease, while the durable operation is already known unresolved. */
    transfer_gate_release(TRANSFER_GATE_VEHICLE_COMMAND);
    assert(vehicle_commands_runtime_init(&runtime) == ESP_OK);
    assert(vehicle_commands_runtime_status(VEHICLE_BURNOUT, snapshot) == 32);
    assert(snapshot[2] == VEHICLE_UNKNOWN && snapshot[4]);
    assert(!vehicle_commands_runtime_pending());
    assert(!vehicle_commands_runtime_maintenance_allowed(false));
    assert(!vehicle_commands_runtime_maintenance_allowed(true));
}

#if CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST
static void owner_brake_runtime_paths(void)
{
    setup(false);
    abs_starts = abs_returns = burnout_starts = burnout_returns = 0;
    uint8_t snapshot[32];
    assert(vehicle_commands_runtime_ui_status(VEHICLE_ABS_TCS, snapshot));
    assert((snapshot[27] & (1 | 128)) == (1 | 128));
    assert(vehicle_commands_runtime_intent(VEHICLE_ABS_TCS, false, 0, 0));
    run(30);
    assert(abs_starts == 1 && journal[1] == 2);
    assert(vehicle_commands_runtime_status(VEHICLE_ABS_TCS, snapshot) && snapshot[2] == VEHICLE_ACTIVE);
    assert(vehicle_commands_runtime_intent(VEHICLE_ABS_TCS, true, 0, 0));
    run(30);
    assert(abs_returns == 1 && journal[1] == 2);
    uint32_t drops = worker_health_snapshot(WORKER_ECM, clock_ms).queue_drops;
    for (unsigned i = 0; i < 4; ++i)
        assert(vehicle_commands_runtime_confirm(VEHICLE_ABS_TCS));
    assert(!vehicle_commands_runtime_confirm(VEHICLE_ABS_TCS));
    assert(worker_health_snapshot(WORKER_ECM, clock_ms).queue_drops == drops + 1);
    run(5);
    assert(!vehicle_commands_runtime_pending() && abs_starts == 1 && abs_returns == 1);
    assert(vehicle_commands_runtime_status(VEHICLE_ABS_TCS, snapshot) && snapshot[2] == VEHICLE_RESTORED);
    assert(!journal[1]);
    setup(false);
    vehicle_commands_runtime_burnout_hold(true);
    assert(vehicle_commands_runtime_intent(VEHICLE_BURNOUT, false, 0, 0));
    for (unsigned i = 0; i < 12; ++i)
    {
        vehicle_commands_runtime_burnout_hold(true);
        tick();
    }
    assert(burnout_starts == 1 && !burnout_returns && journal[1] == 4);
    vehicle_commands_runtime_burnout_hold(false);
    run(30);
    assert(burnout_returns == 1 && journal[1] == 4);
    assert(vehicle_commands_runtime_confirm(VEHICLE_BURNOUT));
    run(5);
    assert(vehicle_commands_runtime_status(VEHICLE_BURNOUT, snapshot) && snapshot[2] == VEHICLE_RESTORED);
    assert(!journal[1]);
}
#endif

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
#if !CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST
    assert(!vehicle_commands_runtime_intent(VEHICLE_ABS_TCS, false, 0, 0));
#endif
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
    for (unsigned failure = 0; failure < 10; ++failure)
    {
        setup(false);
        lose_start_reply = true;
        assert(vehicle_commands_runtime_intent(VEHICLE_IDLE, false, 1, 300));
        run(20);
        phase(VEHICLE_UNKNOWN);
        assert(journal[1] == 1);
        uint8_t previous[VEHICLE_COMMAND_MARKER_SIZE];
        memcpy(previous, journal, sizeof(previous));
        recovery_only    = true;
        control_active   = failure == 0;
        bad_identity     = failure == 1;
        negative_report  = failure == 2;
        fail_report      = failure == 3;
        fail_set         = failure == 4;
        fail_write_open  = failure == 8;
        fail_commit      = failure == 9;
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
        assert(!memcmp(previous, journal, sizeof(previous)));
        if (failure == 1 || failure == 5)
            assert(reports == before_reports);
        if (failure == 4 || failure == 8 || failure == 9)
        {
            uint8_t durable[VEHICLE_COMMAND_MARKER_SIZE];
            memcpy(durable, journal, sizeof(durable));
            initialize(); /* Failed clear must remain unresolved across reboot. */
            phase(VEHICLE_UNKNOWN);
            assert(!memcmp(durable, journal, sizeof(durable)) && !staged);
        }
        /* Retry is bounded, even for active/NRC/transport/storage failures. */
        assert(reports - before_reports <= 1);
        control_active = bad_identity = negative_report = fail_report = fail_set = fail_session = negative_session =
            false;
        fail_write_open = fail_commit = false;
        speed_kph                     = 0;
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
    persistence_failures_and_reboot_boundaries();
    queue_saturation_and_drain();
    combined_read_only_recovery_pressure();
    burnout_release_during_journal_write();
#if CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST
    owner_brake_runtime_paths();
#endif
    puts("primary worker parked idle replay passed; local Start, five-minute expiry, coolant, Stop, restoration, "
         "uncertainty, staged/committed storage failures, reboot boundaries, bounded queue pressure and child "
         "isolation");
}
