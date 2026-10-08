#include "worker_health.h"
// ---------------------------------------------------------------------------------------------------------------------
// Includes
// ---------------------------------------------------------------------------------------------------------------------

#include <inttypes.h>
#include <math.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <ctype.h>
#include <stdatomic.h>
#include "sdkconfig.h"

#include "esp_err.h"
#include "esp_log.h"
#include "esp_log_color.h"
#include "esp_log_level.h"

#include "freertos/FreeRTOS.h"  // IWYU pragma: keep
#include "lvgl.h"               // IWYU pragma: keep

#include "freertos/projdefs.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include "portmacro.h"

#include "display/lv_display.h"
#include "misc/lv_event.h"

#include "bsp_init.h"
#include "bsp_lcd.h"
#include "bsp_lvgl.h"
#include "esp_lvgl_port.h"

#include "ble_obd.h"
#include "obd_trace.h"
#include "ble_companion.h"
#include "ble_mgr.h"
#include "config.h"
#include "display_settings.h"
#include "config_store.h"
#include "config_transfer.h"
#include "config_runtime.h"
#include "alert_engine.h"
#include "ota_transfer.h"
#include "wifi_bulk.h"
#include "esp_ota_ops.h"
#include "transfer_gate.h"
#include "adapter_registry.h"
#include "adapter_status.h"
#include "elm_response.h"
#include "diagnostics_state.h"
#include "hardware_probe.h"
#include "esp_heap_caps.h"
#include "config_document.h"
#include "config_trial.h"
#include "obd.h"
#include "transmission_gear.h"
#include "poll_scheduler.h"
#include "page_save_policy.h"
#include "ui.h"
#include "util.h"

// ---------------------------------------------------------------------------------------------------------------------
// Definitions
// ---------------------------------------------------------------------------------------------------------------------

static const char *TAG = "main";

#define LV_DISPLAY_ROTATION_MAX LV_DISPLAY_ROTATION_270

// ---------------------------------------------------------------------------------------------------------------------
// Constants / Config
// ---------------------------------------------------------------------------------------------------------------------

static const obd_pid_cfg_t g_obd_pids[] = {
    {.pid = 0x0C, .len = 2, .name = "RPM", .unit = "/min",
     .decoder = {.byte_length = 2, .numerator = 1, .denominator = 4,
                 .minimum = 0, .maximum = 16383.75}},
    {.pid = 0x0D, .len = 1, .name = "SPEED", .unit = "km/h",
     .decoder = {.byte_length = 1, .numerator = 1, .denominator = 1,
                 .minimum = 0, .maximum = 255}},
    {.pid = 0x04, .len = 1, .name = "ENGINE", .unit = "%",
     .decoder = {.byte_length = 1, .numerator = 100, .denominator = 255,
                 .minimum = 0, .maximum = 100}},
    {.pid = 0x05, .len = 1, .name = "TEMP", .unit = "°C",
     .decoder = {.byte_length = 1, .numerator = 1, .denominator = 1,
                 .offset = -40, .minimum = -40, .maximum = 215}},
    {.pid = 0x2F, .len = 1, .name = "FUEL", .unit = "%",
     .decoder = {.byte_length = 1, .numerator = 100, .denominator = 255,
                 .minimum = 0, .maximum = 100}},
};

// ---------------------------------------------------------------------------------------------------------------------
// Global Variables
// ---------------------------------------------------------------------------------------------------------------------

static atomic_uchar g_selected_page;
static atomic_uint g_local_page_generation;
static atomic_uint g_page_activity_ms;
static config_runtime_t *g_runtime;
static config_store_record_t g_running_config_record;
static bool g_running_previous_config;

static unsigned page_count(void)
{
    return g_runtime ? g_runtime->page_count : ARRAY_SIZE(g_obd_pids);
}

static ui_renderer_t ui_renderer(runtime_renderer_t renderer)
{
    _Static_assert((int)RUNTIME_RENDERER_NUMERIC == (int)UI_RENDERER_NUMERIC &&
                   (int)RUNTIME_RENDERER_ARC == (int)UI_RENDERER_ARC &&
                   (int)RUNTIME_RENDERER_BAR == (int)UI_RENDERER_BAR &&
                   (int)RUNTIME_RENDERER_TREND == (int)UI_RENDERER_TREND &&
                   (int)RUNTIME_RENDERER_DUAL == (int)UI_RENDERER_DUAL,
                   "Runtime and UI renderer values must remain aligned");
    return (ui_renderer_t)renderer;
}


static void page_ui(unsigned index, ui_page_t *out)
{
    memset(out, 0, sizeof(*out));
    if (!g_runtime) {
        const obd_pid_cfg_t *pid = &g_obd_pids[index % ARRAY_SIZE(g_obd_pids)];
        out->name = pid->name;
        out->renderer = UI_RENDERER_NUMERIC;
        out->metric_count = 1;
        out->metrics[0] = (ui_metric_t){
            .pid = pid->pid, .name = pid->name, .unit = pid->unit,
            .minimum = pid->decoder.minimum,
            .maximum = pid->decoder.maximum, .stale_after_ms = 1500,
        };
        return;
    }
    const runtime_page_t *page = &g_runtime->pages[index % g_runtime->page_count];
    out->name = page->name;
    out->renderer = ui_renderer(page->renderer);
    out->metric_count = page->pid_count;
    for (unsigned i = 0; i < page->pid_count; ++i) {
        const runtime_pid_t *pid = &g_runtime->pids[page->pid_indices[i]];
        out->metrics[i] = (ui_metric_t){
            .pid = pid->obd.pid, .name = pid->name, .unit = pid->unit,
            .minimum = pid->obd.decoder.minimum,
            .maximum = pid->obd.decoder.maximum,
            .stale_after_ms = pid->stale_ms,
        };
    }
}

typedef struct { ui_t *ui; unsigned source; } source_worker_t;
static source_worker_t source_workers[2];
static unsigned runtime_source_count(void) { return g_runtime ? g_runtime->source_count : 1; }
static bool runtime_simulated(void)
{
    if (!g_runtime) return false;
    for (unsigned source=0; source<g_runtime->source_count; ++source)
        if (g_runtime->sources[source].simulated) return true;
    return false;
}
static void clear_source(ui_t *ui, unsigned source)
{
    /* Only visible metrics need queue entries; page switches reset hidden samples.
     * Bound invalidation to two entries even when an absent source owns 32 PIDs. */
    ui_page_t page;
    page_ui(atomic_load(&g_selected_page), &page);
    for (unsigned metric=0; metric<page.metric_count; ++metric) {
        if (!g_runtime) { ui_set_value(ui, page.metrics[metric].pid, NULL); continue; }
        for (unsigned i=0; i<g_runtime->pid_count; ++i)
            if (g_runtime->pids[i].source_index == source && g_runtime->pids[i].obd.pid == page.metrics[metric].pid) {
                ui_set_value(ui, page.metrics[metric].pid, NULL);
                break;
            }
    }
}

static const obd_pid_cfg_t *poll_cfg(unsigned index)
{
    return g_runtime ? &g_runtime->pids[index].obd : &g_obd_pids[index];
}

static unsigned poll_count(void)
{
    return g_runtime ? g_runtime->pid_count : ARRAY_SIZE(g_obd_pids);
}

static config_t g_config = {
    .cfg_idx  = 0,
    .disp_rot = LV_DISPLAY_ROTATION_0,
};
static QueueHandle_t g_phone_command_queue;
typedef struct {
    uint8_t pid_index;
    uint32_t generation;
    double value;
    uint32_t observed_at_ms;
} alert_sample_event_t;
#define ALERT_SAMPLE_QUEUE_LENGTH 16U
_Static_assert(EGAUGE_RUNTIME_PIDS <= 32, "Lost PID mask must cover every runtime reading");
static QueueHandle_t g_alert_sample_queue;
static atomic_uint g_app_tick_count;
static atomic_uint g_alert_sample_drops;
static atomic_uint g_alert_lost_pids;

/* NaN is an internal unavailable marker, never a decoded numeric sample.
 * The application task owns alert state. Queue loss also breaks pending dwell. */
static void queue_alert_sample(unsigned source, uint16_t pid, uint32_t generation, double value)
{
    if (!g_runtime) return;
    for (unsigned i=0; i<g_runtime->pid_count; ++i) {
        if (g_runtime->pids[i].obd.pid != pid || g_runtime->pids[i].source_index != source) continue;
        alert_sample_event_t event = {.pid_index=i, .generation=generation, .value=value,
            .observed_at_ms=pdTICKS_TO_MS(xTaskGetTickCount())};
        if (g_alert_sample_queue && xQueueSend(g_alert_sample_queue,&event,0) != pdTRUE) {
            atomic_fetch_add(&g_alert_sample_drops,1);
            atomic_fetch_or(&g_alert_lost_pids,UINT32_C(1)<<i);
        }
        return;
    }
}

static bool ota_trial_pending(void)
{
    const esp_partition_t *running = esp_ota_get_running_partition();
    esp_ota_img_states_t state;
    return running && esp_ota_get_state_partition(running, &state) == ESP_OK &&
           state == ESP_OTA_IMG_PENDING_VERIFY;
}

static void boot_health_task(void *arg)
{
    (void)arg;
    uint32_t initial_ticks = atomic_load(&g_app_tick_count);
    for (unsigned i = 0; i < 60; ++i) {
        if (ble_companion_ready() &&
            atomic_load(&g_app_tick_count) - initial_ticks >= 50) {
            if (g_runtime && config_trial_needs_confirmation(g_running_config_record.revision)) {
                esp_err_t err = config_trial_confirm(g_running_config_record.revision);
                if (err != ESP_OK) {
                    ESP_LOGE(TAG, "Failed to confirm configuration trial: %s",
                             esp_err_to_name(err));
                    if (ota_trial_pending()) esp_ota_mark_app_invalid_rollback_and_reboot();
                    else esp_restart();
                }
                config_runtime_mark_confirmed();
                ESP_LOGI(TAG, "Configuration revision %" PRIu32 " confirmed healthy",
                         g_running_config_record.revision);
            }
            if (ota_trial_pending()) {
                esp_err_t err = esp_ota_mark_app_valid_cancel_rollback();
                if (err != ESP_OK) {
                    ESP_LOGE(TAG, "Failed to confirm trial firmware: %s", esp_err_to_name(err));
                    esp_ota_mark_app_invalid_rollback_and_reboot();
                }
                ESP_LOGI(TAG, "Trial firmware confirmed after UI, BLE, and application progress");
            }
            vTaskDelete(NULL);
        }
        vTaskDelay(pdMS_TO_TICKS(500));
    }
    ESP_LOGE(TAG, "Boot health confirmation timed out");
    if (ota_trial_pending()) esp_ota_mark_app_invalid_rollback_and_reboot();
    else esp_restart();
    vTaskDelete(NULL);
}

// ---------------------------------------------------------------------------------------------------------------------
// Private Function Definitions
// ---------------------------------------------------------------------------------------------------------------------

static void obd_response_cb(int pid, uint8_t const *data, size_t len, uint32_t generation, void *usr_ctx)
{
    ESP_NULL_CHECK(usr_ctx, TAG, "User context is NULL");
    source_worker_t *worker = usr_ctx;
    ui_t *ui = worker->ui;
    unsigned source = worker->source;
    if (!adapter_status_ready_for(source) || generation != adapter_status_generation(source)) return;

    if (pid < 0)
    {
        ESP_LOGW(TAG, "Error in RX/TX response callback: %d", pid);
        return;
    }

    if (data == NULL || len == 0)
    {
        ESP_LOGE(TAG, "Received empty data for PID 0x%02X", pid);
        queue_alert_sample(source, pid, generation, NAN);
        ui_set_value(ui, pid, NULL);
        return;
    }

    if (pid == 0x01) {
        if (len >= 4) {
            diagnostics_state_mil_for(source, (data[0] & 0x80) != 0, data[0] & 0x7f,
                                  pdTICKS_TO_MS(xTaskGetTickCount()));
        }
        /* PID 01 is reserved for the diagnostics monitor and cannot be a
         * runtime-configured gauge definition. */
        return;
    }

    const obd_pid_cfg_t *definition = NULL;
    for (unsigned i = 0; i < poll_count(); ++i)
        if (poll_cfg(i)->pid == pid && (!g_runtime || g_runtime->pids[i].source_index == source)) { definition = poll_cfg(i); break; }
    if (!definition) return;
    if (len != definition->len)
    {
        ESP_LOGW(TAG, "Invalid length for PID 0x%02X: expected %zu, got %zu", pid, definition->len, len);
        queue_alert_sample(source, pid, generation, NAN);
        ui_set_value(ui, pid, NULL);
        return;
    }

    double decoded;
    if (g_runtime && pid == 0x5503 &&
        !transmission_gear_label(data[0])) {
        queue_alert_sample(source, pid, generation, NAN);
        ui_set_value(ui, pid, NULL);
        return;
    }
    if (!pid_decoder_eval(&definition->decoder, data, len, &decoded)) {
        ESP_LOGW(TAG, "Rejected invalid value for PID 0x%02X", pid);
        queue_alert_sample(source, pid, generation, NAN);
        ui_set_value(ui, pid, NULL);
        return;
    }

    queue_alert_sample(source, pid, generation, decoded);

    ESP_LOGD(TAG, "Received PID 0x%02X (%s): %.3f", pid, definition->name, decoded);
    ui_set_value(ui, pid, &decoded);
}

static void app_tick_task(void *arg)
{
    ui_t *ui = arg;
    TickType_t last_wake = xTaskGetTickCount();
    for (;;) {
        vTaskDelayUntil(&last_wake, pdMS_TO_TICKS(100));
        uint32_t now_ms = pdTICKS_TO_MS(xTaskGetTickCount());
        ble_companion_tick();
        alert_sample_event_t event;
        for (unsigned drained=0; drained<ALERT_SAMPLE_QUEUE_LENGTH && xQueueReceive(g_alert_sample_queue, &event, 0)==pdTRUE; ++drained) {
            if (!g_runtime || event.pid_index >= g_runtime->pid_count) continue;
            unsigned source = g_runtime ? g_runtime->pids[event.pid_index].source_index : 0;
            if (adapter_status_ready_for(source) && event.generation == adapter_status_generation(source))
                alert_engine_sample(event.pid_index, event.value, event.observed_at_ms);
        }
        unsigned lost = atomic_exchange(&g_alert_lost_pids,0);
        for (unsigned i=0; i<EGAUGE_RUNTIME_PIDS; ++i)
            if (lost & (UINT32_C(1)<<i)) alert_engine_invalidate_pid(i);
        for (unsigned source=0; source<runtime_source_count(); ++source)
            if (!adapter_status_ready_for(source)) alert_engine_invalidate_source(source);
        alert_summary_t alert = alert_engine_tick(now_ms);
        ui_set_alert(ui, alert.severity, alert.unavailable, alert.label);
        diagnostics_snapshot_t diagnostics;
        unsigned display_source = 0;
        if (g_runtime) {
            const runtime_page_t *page = &g_runtime->pages[atomic_load(&g_selected_page)];
            display_source = g_runtime->pids[page->pid_indices[0]].source_index;
        }
        diagnostics_state_snapshot_for(display_source, now_ms, &diagnostics);
        ui_set_diagnostics(ui, diagnostics.valid, diagnostics.mil_on, diagnostics.transmission,
                           diagnostics.reported_count, diagnostics.first_code);
        worker_health_progress(WORKER_APP, now_ms);
        uint32_t ticks = atomic_fetch_add(&g_app_tick_count, 1) + 1;
        if (ticks % 600 == 0) {
            ESP_LOGI(TAG, "health app_stack=%u internal_min=%u psram_min=%u alert_free=%u alert_drops=%u",
                     (unsigned)uxTaskGetStackHighWaterMark(NULL),
                     (unsigned)heap_caps_get_minimum_free_size(MALLOC_CAP_INTERNAL),
                     (unsigned)heap_caps_get_minimum_free_size(MALLOC_CAP_SPIRAM),
                     (unsigned)uxQueueSpacesAvailable(g_alert_sample_queue),
                     (unsigned)atomic_load(&g_alert_sample_drops));
            ESP_LOGI(TAG, "health internal_free=%u internal_largest=%u psram_free=%u psram_largest=%u",
                     (unsigned)heap_caps_get_free_size(MALLOC_CAP_INTERNAL),
                     (unsigned)heap_caps_get_largest_free_block(MALLOC_CAP_INTERNAL),
                     (unsigned)heap_caps_get_free_size(MALLOC_CAP_SPIRAM),
                     (unsigned)heap_caps_get_largest_free_block(MALLOC_CAP_SPIRAM));
            for (worker_id_t id = 0; id < WORKER_COUNT; ++id) {
                worker_health_t health = worker_health_snapshot(id, now_ms);
                if (health.observed) ESP_LOGI(TAG, "health worker=%s progress=%" PRIu32
                    " age_ms=%" PRIu32 " max_gap_ms=%" PRIu32 " queue_drops=%" PRIu32,
                    worker_health_name(id), health.progress_count, health.age_ms,
                    health.maximum_gap_ms, health.queue_drops);
            }
        }
    }
}

static void obd_task(void *arg)
{
    ESP_NULL_CHECK(arg, TAG, "task arg is NULL");
    source_worker_t *worker = arg;
    ui_t *ui = worker->ui;
    unsigned source = worker->source;
    const runtime_source_t *configured = g_runtime ? &g_runtime->sources[source] : NULL;
    bool transmission = configured && configured->transmission;
    bool simulated = configured && configured->simulated;
    uint32_t responder = transmission ? 0x7e9 : 0x7e8;
    uint8_t pid_indices[POLL_SCHEDULER_MAX_PIDS];
    uint8_t count = 0;
    for (unsigned i=0; i<poll_count(); ++i)
        if (!g_runtime || g_runtime->pids[i].source_index == source) pid_indices[count++] = i;
    ESP_LOGI(TAG, "RX/TX source %u task started", source);

    ble_obd_ctx_t *obd = NULL;
    uint32_t reconnect_delay_ms = 500;

    const uint32_t period_ms  = 100;
    const uint32_t timeout_ms = 300;
    const uint8_t dtc_modes[3] = {3, 7, 10};
    poll_scheduler_t scheduler;
    poll_scheduler_init(&scheduler);

    while (true)
    {
        vTaskDelay(pdMS_TO_TICKS(period_ms));
        worker_health_progress(source == 0 ? WORKER_ECM : WORKER_TCM, (uint32_t)(esp_timer_get_time() / 1000));
        if (transfer_gate_current() == 2 || ble_mgr_is_paused()) {
            clear_source(ui, source);
            diagnostics_state_disconnected_for(source);
            continue;
        }

        if (obd == NULL || !ble_obd_is_connected(obd)) {
            clear_source(ui, source);
            diagnostics_state_disconnected_for(source);
            if (g_runtime && !g_runtime->legacy_auto_discovery && !configured->address[0])
                continue;
            const char *address = configured && configured->address[0]
                ? configured->address : CONFIG_EGAUGE_ECM_ADAPTER_MAC;
            uint8_t address_type = configured ? configured->address_type : 0;
            obd = ble_obd_connect_profile_ecu(source, address, address_type,
                simulated ? "elm-bench-v1" : "elm-18f0-v1",
                responder, obd_response_cb, worker);
            if (!obd) {
                ESP_LOGW(TAG, "Vehicle adapter unavailable; retrying in %" PRIu32 " ms",
                         reconnect_delay_ms);
                vTaskDelay(pdMS_TO_TICKS(reconnect_delay_ms));
                if (reconnect_delay_ms < 8000) reconnect_delay_ms *= 2;
                continue;
            }
            ESP_LOGI(TAG, "Vehicle adapter link ready");
            diagnostics_state_connected_for(source);
            reconnect_delay_ms = 500;
            poll_scheduler_init(&scheduler);
            continue;
        }

        uint32_t now_ms = pdTICKS_TO_MS(xTaskGetTickCount());
        uint32_t intervals[POLL_SCHEDULER_MAX_PIDS];
        for (uint8_t i = 0; i < count; ++i)
            intervals[i] = g_runtime ? g_runtime->pids[pid_indices[i]].poll_ms : 500;
        poll_job_t job = poll_scheduler_next(&scheduler, now_ms, intervals, count);
        if (job.kind == POLL_JOB_NONE) continue;
        if (simulated &&
            (job.kind == POLL_JOB_MIL || job.kind == POLL_JOB_DTC)) continue;
        if (job.kind == POLL_JOB_MIL) {
            elm_result_t response;
            if (ble_obd_rxtx_status_ecu(obd, 1, 0x01,
                responder, 700, &response) != 0)
                diagnostics_state_failed_for(source, 1, response == ELM_UNSUPPORTED ?
                    DIAGNOSTICS_UNSUPPORTED : DIAGNOSTICS_UNAVAILABLE);
            continue;
        }
        if (job.kind == POLL_JOB_DTC) {
            uint8_t codes[64];
            size_t code_length = sizeof(codes);
            elm_result_t response;
            if (ble_obd_read_service_status_ecu(obd, dtc_modes[job.index],
                    responder,
                    1500, codes, &code_length, &response) == 0)
                diagnostics_state_codes_for(source, dtc_modes[job.index], codes, code_length,
                                        pdTICKS_TO_MS(xTaskGetTickCount()));
            else diagnostics_state_failed_for(source, dtc_modes[job.index], response == ELM_UNSUPPORTED ?
                DIAGNOSTICS_UNSUPPORTED : DIAGNOSTICS_UNAVAILABLE);
            continue;
        }

        const obd_pid_cfg_t *requested = poll_cfg(pid_indices[job.index]);
        const uint8_t obd_mode = g_runtime ? g_runtime->pids[pid_indices[job.index]].service : 0x01;

        uint32_t poll_generation = adapter_status_generation(source);
        int status = ble_obd_rxtx_ecu(obd, obd_mode, requested->pid,
            g_runtime ? g_runtime->pids[pid_indices[job.index]].responder : ELM_ECU_ANY, timeout_ms);

        poll_scheduler_result(&scheduler, job.index, status == 0);
        if (status != 0)
        {
            queue_alert_sample(source, requested->pid, poll_generation, NAN);
            ui_set_value(ui, requested->pid, NULL);
            ESP_LOGW(TAG, "Failed to send request: %d", status);
        }
    }
}

static bool adapter_mac_valid(const char *mac)
{
    if (strlen(mac) != 17) return false;
    for (size_t i = 0; i < 17; i++) {
        if (i % 3 == 2) { if (mac[i] != ':') return false; }
        else if (!isxdigit((unsigned char)mac[i])) return false;
    }
    return true;
}

static void init_obd_task(ui_t *ui)
{
    for (unsigned source=0; source<runtime_source_count(); ++source) {
        source_workers[source] = (source_worker_t){.ui=ui, .source=source};
        BaseType_t started = xTaskCreate(obd_task, source ? "obd_child" : "obd_primary",
            4096, &source_workers[source], 5, NULL);
        ESP_CHECK(started == pdPASS, TAG, "Failed to create source worker");
    }
}

static void ui_touch_callback(ui_t *ui, lv_event_code_t event_code)
{
#if CONFIG_EGAUGE_DISPLAY_CALIBRATION
    if (event_code == LV_EVENT_CLICKED) ui_next_display_calibration(ui);
    return;
#endif
    switch (event_code)
    {
    case LV_EVENT_PRESSED:
        atomic_store(&g_page_activity_ms, pdTICKS_TO_MS(xTaskGetTickCount()));
        break;
    case UI_EVENT_PAGE_ACTION:
        if (transfer_gate_current() != 0 || !ble_companion_has_owner()) break;
        /* Fall through to the same local selection/persistence path. */
        __attribute__((fallthrough));
    case LV_EVENT_CLICKED:
    {
        /* Already on the LVGL task with its lock held. Browsing must not wait
         * for app_main, a flash commit, or another UI mailbox interval. */
        uint8_t selected = event_code == UI_EVENT_PAGE_ACTION ? ui_action_target(ui) :
            (atomic_load(&g_selected_page) + 1) % page_count();
        if (selected >= page_count()) break;
        ui_page_t page;
        page_ui(selected, &page);
        ui_set_page(ui, &page);
        atomic_store(&g_selected_page, selected);
        atomic_store(&g_page_activity_ms, pdTICKS_TO_MS(xTaskGetTickCount()));
        ble_companion_selection_applied(selected);
        atomic_fetch_add(&g_local_page_generation, 1);
        if (event_code == UI_EVENT_PAGE_ACTION) ui_action_applied(ui);
        break;
    }
    case LV_EVENT_LONG_PRESSED:
        ble_companion_open_pairing_window();
        break;
    case LV_EVENT_RELEASED:
    {
        if (ble_companion_has_owner()) ui_show_pairing_code(ui, UI_PAIRING_RESET_CONFIRM);
        break;
    }
    case LV_EVENT_VALUE_CHANGED:
    {
        companion_command_t command = {.opcode = 3};
        if (g_phone_command_queue) xQueueSend(g_phone_command_queue, &command, 0);
        break;
    }
    default:
        // ignore
        break;
    }
}

static void init_config(void)
{
    ESP_LOGI(TAG, "Initializing configuration...");

    ESP_ERROR_CHECK(config_init());
    ESP_ERROR_CHECK(config_store_init());
    ESP_ERROR_CHECK(config_trial_init());
    ESP_ERROR_CHECK(ota_transfer_init());
    ESP_ERROR_CHECK(diagnostics_state_init());

    g_runtime = heap_caps_malloc(sizeof(*g_runtime), MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (g_runtime && config_runtime_load(g_runtime, &g_running_config_record,
                                         &g_running_previous_config) == ESP_OK) {
        ESP_LOGI(TAG, "Running configuration revision %" PRIu32 "%s with %u PIDs, %u pages, %u alerts",
                 g_running_config_record.revision,
                 g_running_previous_config ? " (previous compatible generation)" : "",
                 g_runtime->pid_count, g_runtime->page_count, g_runtime->alert_count);
    } else {
        if (g_runtime) heap_caps_free(g_runtime);
        g_runtime = NULL;
    }
    ESP_ERROR_CHECK(config_transfer_init());
#if CONFIG_EGAUGE_WIFI_BULK_ENABLED
    ESP_ERROR_CHECK(wifi_bulk_init());
#endif

    esp_err_t err = config_load(&g_config);

    if (err == ESP_OK && (g_config.cfg_idx >= page_count() ||
                          g_config.disp_rot > LV_DISPLAY_ROTATION_270)) {
        ESP_LOGE(TAG, "Saved legacy configuration is invalid");
        g_config = (config_t){.cfg_idx = 0, .disp_rot = LV_DISPLAY_ROTATION_0};
        err = ESP_ERR_INVALID_ARG;
    }

    if (err != ESP_OK)
    {
        ESP_LOGW(TAG, "Failed to load configuration: %s", esp_err_to_name(err));

        err = config_save(&g_config);
        if (err != ESP_OK)
        {
            ESP_LOGE(TAG, "Failed to save default configuration: %s", esp_err_to_name(err));
        }
        else
        {
            ESP_LOGI(TAG, "Default configuration saved successfully");
        }
    }
    else
    {
        ESP_LOGI(TAG, "Configuration loaded successfully: idx=%d, disp_rot=%d", g_config.cfg_idx, g_config.disp_rot);
    }
    uint8_t default_rotation = g_runtime ? g_runtime->rotation : (uint8_t)g_config.disp_rot;
    uint8_t default_brightness = g_runtime ? g_runtime->brightness : 80;
    ESP_ERROR_CHECK(display_settings_init(default_rotation, default_brightness, 0));
    g_config.disp_rot = (lv_display_rotation_t)display_settings_snapshot().rotation;
    atomic_store(&g_selected_page, g_config.cfg_idx);
}

// ---------------------------------------------------------------------------------------------------------------------
// Main Function
// ---------------------------------------------------------------------------------------------------------------------

void app_main(void)
{
    esp_log_level_set("*", ESP_LOG_INFO);
    esp_log_level_set("NimBLE", ESP_LOG_WARN);
    // esp_log_level_set("BLE=INIT", ESP_LOG_DEBUG);
    // esp_log_level_set("BLE_MGR", ESP_LOG_DEBUG);
    // esp_log_level_set("BLE_GATT", ESP_LOG_DEBUG);
    // esp_log_level_set("OBD", ESP_LOG_DEBUG);
    // esp_log_level_set("UI", ESP_LOG_DEBUG);
    esp_log_level_set("main", ESP_LOG_INFO);

    config_document_init();
    init_config();
    for (unsigned source=0; source<runtime_source_count(); ++source)
        diagnostics_state_configure_for(source, g_runtime && g_runtime->sources[source].transmission,
            g_runtime ? g_running_config_record.revision : 0,
            g_runtime && g_runtime->sources[source].simulated);
    bool pairing_required = !ble_companion_load_owner();
    alert_engine_init(g_runtime);

    bsp_init();
    bsp_display_start();
    bsp_lvgl_init();
    bsp_touch_init();
    hardware_probe_set_ready(HARDWARE_FEATURE_DISPLAY | HARDWARE_FEATURE_TOUCH |
                             HARDWARE_FEATURE_BACKLIGHT, true);

    bsp_lv_disp_set_rotation(g_config.disp_rot);

    const uint32_t ui_interval_ms = 50;
    ui_page_t initial_page;
    page_ui(g_config.cfg_idx, &initial_page);
    ui_t          *ui             = ui_init(&initial_page, ui_interval_ms, ui_touch_callback,
                                            pairing_required, display_settings_snapshot().units == 1);
    ESP_NULL_CHECK(ui, TAG, "Failed to initialize UI");
    if (g_runtime && lvgl_port_lock(portMAX_DELAY)) {
        ui_configure_page_action(ui, g_runtime->page_action);
        lvgl_port_unlock();
    }
    bsp_display_on_off(true);
    ESP_ERROR_CHECK(bsp_display_backlight_set_percent(display_settings_snapshot().brightness));

#if CONFIG_EGAUGE_DISPLAY_CALIBRATION
    ui_start_display_calibration(ui);
    ESP_LOGW(TAG, "Display calibration build active; BLE and OBD tasks are disabled");
    return;
#endif

    g_phone_command_queue = xQueueCreate(4, sizeof(companion_command_t));
    ESP_NULL_CHECK(g_phone_command_queue, TAG, "Phone command queue creation failed");
    g_alert_sample_queue = xQueueCreate(ALERT_SAMPLE_QUEUE_LENGTH, sizeof(alert_sample_event_t));
    ESP_NULL_CHECK(g_alert_sample_queue, TAG, "Alert sample queue creation failed");
    ble_companion_set_control(ui, g_phone_command_queue, g_config.cfg_idx, g_runtime != NULL);

    ESP_NULL_CHECK(ble_mgr_init(0, 2000), TAG, "BLE ECM slot initialization failed");
    ESP_NULL_CHECK(ble_mgr_init(1, 2000), TAG, "BLE TCM slot initialization failed");
    if (CONFIG_EGAUGE_ECM_ADAPTER_MAC[0] && !adapter_mac_valid(CONFIG_EGAUGE_ECM_ADAPTER_MAC)) {
        ESP_LOGE(TAG, "Invalid ECM adapter MAC in firmware configuration");
        return;
    }
    adapter_status_init(g_runtime);
    if (runtime_simulated() && lvgl_port_lock(portMAX_DELAY)) {
        ui_set_simulated(ui);
        lvgl_port_unlock();
    }
    ESP_ERROR_CHECK(adapter_registry_init());
    obd_trace_init();
    for (unsigned source=0; source<runtime_source_count(); ++source)
        if (g_runtime && g_runtime->sources[source].simulated)
            obd_trace_emit(source, 0, "source_simulated", NULL, 0, 1);
    init_obd_task(ui);
    BaseType_t tick_started = xTaskCreate(app_tick_task, "app_tick", 3072, ui, 5, NULL);
    ESP_CHECK(tick_started == pdPASS, TAG, "Application tick task creation failed");
    bool config_trial = g_runtime &&
        config_trial_needs_confirmation(g_running_config_record.revision);
    if (ota_trial_pending() || config_trial) {
        BaseType_t started = xTaskCreate(boot_health_task, "boot_health", 3072,
                                         NULL, 5, NULL);
        ESP_CHECK(started == pdPASS, TAG, "Boot health task creation failed");
    }

    page_save_policy_t page_save = {0};
    atomic_store(&g_page_activity_ms, pdTICKS_TO_MS(xTaskGetTickCount()));
    while (true)
    {
        companion_command_t command;
        bool has_command = xQueueReceive(g_phone_command_queue, &command, pdMS_TO_TICKS(20)) == pdTRUE;
        if (has_command && command.opcode == 1 && command.value < page_count()) {
            config_t updated = g_config;
            updated.cfg_idx = command.value;
            if (config_save(&updated) == ESP_OK) {
                g_config = updated;
                if (lvgl_port_lock(portMAX_DELAY)) {
                    ui_page_t page;
                    page_ui(command.value, &page);
                    ui_set_page(ui, &page);
                    atomic_store(&g_selected_page, command.value);
                    atomic_store(&g_page_activity_ms, pdTICKS_TO_MS(xTaskGetTickCount()));
                    lvgl_port_unlock();
                    ble_companion_selection_applied(command.value);
                }
            }
        } else if (has_command && command.opcode == 2 && command.value <= LV_DISPLAY_ROTATION_270) {
            config_t saved;
            uint32_t revision;
            if (config_read_snapshot(&saved, &revision) != ESP_OK) continue;
            saved.disp_rot = (lv_display_rotation_t)command.value;
            if (config_save_if_revision(&saved, command.base_revision, NULL) != ESP_OK) continue;
            display_settings_t display = display_settings_snapshot();
            if (display.rotation != command.value &&
                display_settings_save(command.value, display.brightness, display.units, display.cycle_seconds, display.revision,
                                      NULL) != ESP_OK)
                ESP_LOGW(TAG, "Legacy rotation saved but display preference update failed");
            g_config = saved;
            if (lvgl_port_lock(portMAX_DELAY)) {
                ui_reset_action_sequence(ui);
                bsp_lv_disp_set_rotation(saved.disp_rot);
                lvgl_port_unlock();
            }
            ESP_LOGI(TAG, "Display rotation saved: %u", (unsigned)command.value * 90);
        } else if (has_command && command.opcode == 0x36) {
            display_settings_t saved;
            display_settings_t before = display_settings_snapshot();
            if (display_settings_save(command.value, command.brightness, before.units, before.cycle_seconds,
                                      command.base_revision, &saved) != ESP_OK) continue;
            g_config.disp_rot = (lv_display_rotation_t)saved.rotation;
            if (lvgl_port_lock(portMAX_DELAY)) {
                ui_reset_action_sequence(ui);
                bsp_lv_disp_set_rotation(g_config.disp_rot);
                lvgl_port_unlock();
            }
            ESP_ERROR_CHECK(bsp_display_backlight_set_percent(saved.brightness));
            ESP_LOGI(TAG, "Display settings saved: rotation=%u brightness=%u%%",
                     (unsigned)saved.rotation * 90, (unsigned)saved.brightness);
        } else if (has_command && command.opcode == 0x37) {
            display_settings_t saved;
            display_settings_t before = display_settings_snapshot();
            if (display_settings_save(command.value, command.brightness, command.units, before.cycle_seconds,
                                      command.base_revision, &saved) != ESP_OK) continue;
            g_config.disp_rot = (lv_display_rotation_t)saved.rotation;
            if (lvgl_port_lock(portMAX_DELAY)) {
                ui_reset_action_sequence(ui);
                bsp_lv_disp_set_rotation(g_config.disp_rot);
                ui_set_units(ui, saved.units == 1);
                lvgl_port_unlock();
            }
            ESP_ERROR_CHECK(bsp_display_backlight_set_percent(saved.brightness));
            ESP_LOGI(TAG, "Display settings saved: rotation=%u brightness=%u%% units=%u",
                     (unsigned)saved.rotation * 90, (unsigned)saved.brightness, (unsigned)saved.units);
        } else if (has_command && command.opcode == 0x38) {
            display_settings_t saved;
            if (display_settings_save(command.value, command.brightness, command.units, command.cycle_seconds,
                                      command.base_revision, &saved) != ESP_OK) continue;
            g_config.disp_rot = (lv_display_rotation_t)saved.rotation;
            if (lvgl_port_lock(portMAX_DELAY)) {
                ui_reset_action_sequence(ui);
                bsp_lv_disp_set_rotation(g_config.disp_rot);
                ui_set_units(ui, saved.units == 1);
                lvgl_port_unlock();
            }
            ESP_ERROR_CHECK(bsp_display_backlight_set_percent(saved.brightness));
            atomic_store(&g_page_activity_ms, pdTICKS_TO_MS(xTaskGetTickCount()));
            ESP_LOGI(TAG, "Display settings saved: rotation=%u brightness=%u%% units=%u cycle=%us",
                     (unsigned)saved.rotation * 90, (unsigned)saved.brightness,
                     (unsigned)saved.units, (unsigned)saved.cycle_seconds);
        } else if (has_command && command.opcode == 3) ble_companion_forget_owner();

        uint32_t now_ms = pdTICKS_TO_MS(xTaskGetTickCount());
        display_settings_t cycle = display_settings_snapshot();
        bool owner_ready = ble_companion_has_owner();
        bool transfer_idle = transfer_gate_current() == 0;
        ui_set_action_context(ui, owner_ready && transfer_idle);
        if (!owner_ready || !transfer_idle) atomic_store(&g_page_activity_ms, now_ms);
        if (page_cycle_due(cycle.cycle_seconds, page_count(), owner_ready,
            transfer_idle && !ui_action_pending(ui), atomic_load(&g_page_activity_ms), now_ms) &&
            lvgl_port_lock(0)) {
            uint8_t selected = (atomic_load(&g_selected_page) + 1) % page_count();
            ui_page_t page;
            page_ui(selected, &page);
            ui_set_page(ui, &page);
            atomic_store(&g_selected_page, selected);
            ble_companion_selection_applied(selected);
            atomic_store(&g_page_activity_ms, now_ms);
            lvgl_port_unlock();
        }
        page_save_observe(&page_save, atomic_load(&g_local_page_generation), now_ms);
        if (page_save_due(&page_save, now_ms) && transfer_gate_current() == 0) {
            config_t updated = g_config;
            uint32_t generation;
            /* Snapshot the page and generation together, then release LVGL
             * before any flash access. A tap during the save stays pending. */
            if (!lvgl_port_lock(0)) continue;
            generation = atomic_load(&g_local_page_generation);
            updated.cfg_idx = atomic_load(&g_selected_page);
            lvgl_port_unlock();
            if (generation != page_save.generation) {
                page_save_observe(&page_save, generation, pdTICKS_TO_MS(xTaskGetTickCount()));
                continue;
            }
            bool saved = updated.cfg_idx == g_config.cfg_idx || config_save(&updated) == ESP_OK;
            if (saved) g_config = updated;
            page_save_finished(&page_save, generation, saved, now_ms);
        }
    }
}
