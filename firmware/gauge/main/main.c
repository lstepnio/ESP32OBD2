#include "vehicle_commands_runtime.h"
#include "vehicle_idle_coolant.h"
#include "worker_health.h"
#include "boot_health.h"
#include "memory_health.h"
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

/* The main task boots BSP/LVGL before app_tick exists. Candidate dev.67 exhausted
 * its separate startup stack with a 3584-byte configured budget. IDF adds its own
 * logging allowance; app_tick's independent 4096-byte budget cannot cover boot. */
_Static_assert(CONFIG_ESP_MAIN_TASK_STACK_SIZE >= 4608, "Review the measured main-task startup stack budget");

#include "display/lv_display.h"
#include "misc/lv_event.h"

#include "gauge_board.h"
#include "gauge_presentation.h"
#include "esp_lvgl_port.h"

#include "ble_obd.h"
#include "obd_trace.h"
#include "ble_companion.h"
#include "ble_mgr.h"
#include "config.h"
#include "display_settings.h"
#include "smart_page_policy.h"
#include "smart_metric_catalog.h"
#include "orientation_runtime.h"
#include "config_store.h"
#include "config_transfer.h"
#include "config_runtime.h"
#include "alert_engine.h"
#include "alert_runtime.h"
#include "diagnostic_clear.h"
#include "ota_transfer.h"
#include "wifi_bulk.h"
#include "esp_ota_ops.h"
#include "transfer_gate.h"
#include "adapter_registry.h"
#include "adapter_status.h"
#include "elm_response.h"
#include "diagnostics_state.h"
#include "diagnostic_filter.h"
#include "diagnostic_alerts.h"
#include "hardware_probe.h"
#include "esp_heap_caps.h"
#include "config_document.h"
#include "config_trial.h"
#include "obd.h"
#include "transmission_gear.h"
#include "poll_scheduler.h"
#include "reading_policy.h"
#include "esp_attr.h"
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
    {.pid     = 0x0C,
     .len     = 2,
     .name    = "RPM",
     .unit    = "/min",
     .decoder = {.byte_length = 2, .numerator = 1, .denominator = 4, .minimum = 0, .maximum = 16383.75}},
    {.pid     = 0x0D,
     .len     = 1,
     .name    = "SPEED",
     .unit    = "km/h",
     .decoder = {.byte_length = 1, .numerator = 1, .denominator = 1, .minimum = 0, .maximum = 255}},
    {.pid     = 0x04,
     .len     = 1,
     .name    = "ENGINE",
     .unit    = "%",
     .decoder = {.byte_length = 1, .numerator = 100, .denominator = 255, .minimum = 0, .maximum = 100}},
    {.pid     = 0x05,
     .len     = 1,
     .name    = "TEMP",
     .unit    = "°C",
     .decoder = {.byte_length = 1, .numerator = 1, .denominator = 1, .offset = -40, .minimum = -40, .maximum = 215}},
    {.pid     = 0x2F,
     .len     = 1,
     .name    = "FUEL",
     .unit    = "%",
     .decoder = {.byte_length = 1, .numerator = 100, .denominator = 255, .minimum = 0, .maximum = 100}},
};

// ---------------------------------------------------------------------------------------------------------------------
// Global Variables
// ---------------------------------------------------------------------------------------------------------------------

static atomic_uchar          g_selected_page;
static atomic_uint           g_local_page_generation;
static atomic_uint           g_page_activity_ms;
static config_runtime_t     *g_runtime;
static config_store_record_t g_running_config_record;
static bool                  g_running_previous_config;

static EXT_RAM_BSS_ATTR smart_page_policy_t   smart_pages;
static EXT_RAM_BSS_ATTR alert_page_interest_t smart_alerts[EGAUGE_RUNTIME_ALERTS];
static atomic_uchar                           g_upcoming_page;
static uint32_t                               smart_maximum_us;
_Static_assert(sizeof(smart_pages) + sizeof(smart_alerts) <= 4096, "Smart PSRAM budget exceeded");

static unsigned page_count(void)
{
    return g_runtime ? g_runtime->page_count : ARRAY_SIZE(g_obd_pids);
}

static void smart_configure(void)
{
    if (!g_runtime)
        return;
    smart_pages.pid_count  = g_runtime->pid_count;
    smart_pages.page_count = g_runtime->page_count;
    for (unsigned i = 0; i < g_runtime->page_count; ++i)
    {
        smart_pages.pages[i].count = g_runtime->pages[i].pid_count;
        memcpy(smart_pages.pages[i].pids, g_runtime->pages[i].pid_indices, sizeof(smart_pages.pages[i].pids));
    }
    for (unsigned i = 0; i < g_runtime->pid_count; ++i)
    {
        const runtime_pid_t *pid = &g_runtime->pids[i];
        smart_pages.config[i].rule =
            smart_metric_rule(pid->id, pid->service, pid->obd.pid, pid->responder, pid->unit, &pid->obd.decoder);
        smart_pages.config[i].stale_ms = pid->stale_ms;
    }
}
static bool smart_sample(ui_t *ui, unsigned i, uint32_t now, double *value, uint32_t *at, uint32_t *generation)
{
    const runtime_pid_t *pid = &g_runtime->pids[i];
    return ui_source_sample(ui, pid->source_index, g_running_config_record.revision, pid->obd.pid, pid->stale_ms, now,
                            value, at, generation);
}
static bool smart_select(ui_t                  *ui,
                         display_settings_t     settings,
                         uint8_t                selected,
                         uint32_t               activity,
                         page_cycle_reason_t    pause,
                         uint32_t               now,
                         smart_page_decision_t *choice)
{
    if (!smart_page_prepare(&smart_pages, settings.cycle_seconds, selected, activity, pause != PAGE_CYCLE_RUNNING, now))
    {
        atomic_store(&g_upcoming_page, (selected + 1U) % page_count());
        return false;
    }
    int64_t started = esp_timer_get_time();
    if (!smart_pages.evaluated || now - smart_pages.evaluated_at >= SMART_PAGE_EVALUATE_MS)
    {
        if (!alert_runtime_page_interest(now, smart_alerts))
        {
            atomic_store(&g_upcoming_page,
                         smart_pages.cursor == selected ? (selected + 1U) % page_count() : smart_pages.cursor);
            return smart_page_fallback(&smart_pages, now, choice);
        }
        for (unsigned i = 0; i < g_runtime->pid_count; ++i)
        {
            double              value = 0;
            uint32_t            at = 0, generation = 0, episode = 0;
            uint8_t             severity   = 0;
            bool                suppressed = false;
            smart_pid_config_t *policy     = &smart_pages.config[i];
            policy->has_low = policy->has_high = false;
            for (unsigned a = 0; a < g_runtime->alert_count; ++a)
            {
                if (g_runtime->alerts[a].pid_index != i)
                    continue;
                const runtime_alert_t *rule = &g_runtime->alerts[a];
                suppressed |= smart_alerts[a].suppressed;
                if (!smart_alerts[a].suppressed && !rule->equals)
                {
                    float limit = (float)(rule->has_warning ? rule->warning : rule->critical);
                    if (rule->above)
                    {
                        if (!policy->has_high || limit < policy->high)
                            policy->high = limit;
                        policy->has_high = true;
                    }
                    else
                    {
                        if (!policy->has_low || limit > policy->low)
                            policy->low = limit;
                        policy->has_low = true;
                    }
                }
                if (smart_alerts[a].severity > severity)
                {
                    severity = smart_alerts[a].severity;
                    episode  = smart_alerts[a].episode;
                }
            }
            bool valid = smart_sample(ui, i, now, &value, &at, &generation);
            smart_page_observe(&smart_pages, i, valid, value, at, generation, severity, suppressed, episode);
        }
    }
    bool due = smart_page_choose(&smart_pages, now, choice);
    atomic_store(&g_upcoming_page, smart_page_upcoming(&smart_pages, now));
    uint32_t duration = (uint32_t)(esp_timer_get_time() - started);
    if (duration > smart_maximum_us)
        smart_maximum_us = duration;
    return due;
}

static ui_renderer_t ui_renderer(runtime_renderer_t renderer)
{
    _Static_assert((int)RUNTIME_RENDERER_NUMERIC == (int)UI_RENDERER_NUMERIC &&
                       (int)RUNTIME_RENDERER_ARC == (int)UI_RENDERER_ARC &&
                       (int)RUNTIME_RENDERER_BAR == (int)UI_RENDERER_BAR &&
                       (int)RUNTIME_RENDERER_TREND == (int)UI_RENDERER_TREND &&
                       (int)RUNTIME_RENDERER_DUAL == (int)UI_RENDERER_DUAL &&
                       (int)RUNTIME_RENDERER_DUAL_ARC == (int)UI_RENDERER_DUAL_ARC,
                   "Runtime and UI renderer values must remain aligned");
    return (ui_renderer_t)renderer;
}

static void page_ui(unsigned index, ui_page_t *out)
{
    memset(out, 0, sizeof(*out));
    if (!g_runtime)
    {
        const obd_pid_cfg_t *pid = &g_obd_pids[index % ARRAY_SIZE(g_obd_pids)];
        out->name                = pid->name;
        out->renderer            = UI_RENDERER_NUMERIC;
        out->metric_count        = 1;
        out->metrics[0]          = (ui_metric_t){
            .pid            = pid->pid,
            .scoped         = true,
            .source_index   = 0,
            .name           = pid->name,
            .unit           = pid->unit,
            .minimum        = pid->decoder.minimum,
            .maximum        = pid->decoder.maximum,
            .stale_after_ms = 3000,
        };
        return;
    }
    const runtime_page_t *page = &g_runtime->pages[index % g_runtime->page_count];
    out->name                  = page->name;
    out->renderer              = ui_renderer(page->renderer);
    out->metric_count          = page->pid_count;
    out->arc_metric_index      = page->arc_metric_index;
    for (unsigned i = 0; i < page->pid_count; ++i)
    {
        const runtime_pid_t *pid = &g_runtime->pids[page->pid_indices[i]];
        out->metrics[i]          = (ui_metric_t){
            .pid          = pid->obd.pid,
            .scoped       = true,
            .source_index = pid->source_index,
            .revision     = g_running_config_record.revision,
            .name         = pid->name,
            .unit         = pid->unit,
            .minimum      = pid->obd.decoder.minimum,
            .maximum      = pid->obd.decoder.maximum,
            .stale_after_ms =
                reading_policy(pid->id, pid->service, pid->obd.pid, pid->poll_ms, pid->stale_ms, false, false, false)
                    .display_age_ms,
        };
    }
}

typedef struct
{
    ui_t             *ui;
    unsigned          source;
    poll_scheduler_t *scheduler; /* Called only on this serial transport owner. */
    uint8_t           indices[POLL_SCHEDULER_MAX_PIDS], count;
    bool              decoded_sample, command_poll;
    uint32_t          reused_samples;
} source_worker_t;
static source_worker_t                      source_workers[2];
static EXT_RAM_BSS_ATTR poll_scheduler_t    source_schedulers[2];
static EXT_RAM_BSS_ATTR reading_stability_t reading_stability[EGAUGE_RUNTIME_PIDS];
static unsigned                             runtime_source_count(void)
{
    return g_runtime ? g_runtime->source_count : 1;
}
static bool runtime_simulated(void)
{
    if (!g_runtime)
        return false;
    for (unsigned source = 0; source < g_runtime->source_count; ++source)
        if (g_runtime->sources[source].simulated)
            return true;
    return false;
}
static void clear_source(ui_t *ui, unsigned source)
{
    ui_set_source_state(ui, source, false, g_runtime ? g_running_config_record.revision : 0,
                        adapter_status_generation(source));
}

static void publish_ui_sample(ui_t *ui, unsigned source, uint32_t generation, uint16_t pid, const double *value)
{
    if (ble_mgr_is_paused())
        return;
    ui_set_source_value(ui, source, g_runtime ? g_running_config_record.revision : 0, generation, pid, value,
                        (uint32_t)(esp_timer_get_time() / 1000));
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
/* Called under the rendering owner's existing LVGL lease. Keep wire applied
 * truth unchanged on a private board failure and retry through the main loop. */
static bool apply_display_rotation(lv_display_rotation_t rotation)
{
    if (gauge_display_set_rotation(rotation) == ESP_OK)
    {
        orientation_runtime_applied((uint8_t)rotation);
        return true;
    }
    orientation_runtime_apply_failed((uint32_t)(esp_timer_get_time() / 1000));
    return false;
}

static QueueHandle_t g_phone_command_queue;
typedef struct
{
    uint8_t  pid_index;
    uint32_t generation;
    double   value;
    uint32_t observed_at_ms;
} alert_sample_event_t;
#define ALERT_SAMPLE_QUEUE_LENGTH 16U
_Static_assert(EGAUGE_RUNTIME_PIDS <= 32, "Lost PID mask must cover every runtime reading");
static QueueHandle_t g_alert_sample_queue;
static atomic_uint   g_app_tick_count;
static atomic_uint   g_alert_sample_drops;
static atomic_uint   g_alert_lost_pids;
static uint32_t      engine_rpm_at, speed_at;
static bool          engine_running, vehicle_moving;

/* NaN is an internal unavailable marker, never a decoded numeric sample.
 * The application task owns alert state. Queue loss also breaks pending dwell. */
static void queue_alert_sample(unsigned source, uint16_t pid, uint32_t generation, double value)
{
    if (!g_runtime)
        return;
    for (unsigned i = 0; i < g_runtime->pid_count; ++i)
    {
        if (g_runtime->pids[i].obd.pid != pid || g_runtime->pids[i].source_index != source)
            continue;
        alert_sample_event_t event = {.pid_index      = i,
                                      .generation     = generation,
                                      .value          = value,
                                      .observed_at_ms = pdTICKS_TO_MS(xTaskGetTickCount())};
        if (g_alert_sample_queue && xQueueSend(g_alert_sample_queue, &event, 0) != pdTRUE)
        {
            atomic_fetch_add(&g_alert_sample_drops, 1);
            atomic_fetch_or(&g_alert_lost_pids, UINT32_C(1) << i);
        }
        return;
    }
}

static bool ota_trial_pending(void)
{
    const esp_partition_t *running = esp_ota_get_running_partition();
    esp_ota_img_states_t   state;
    return running && esp_ota_get_state_partition(running, &state) == ESP_OK && state == ESP_OTA_IMG_PENDING_VERIFY;
}

static void boot_health_task(void *arg)
{
    (void)arg;
    uint32_t initial_ticks = atomic_load(&g_app_tick_count);
    uint32_t initial_ui    = worker_health_snapshot(WORKER_UI, 0).progress_count;
    for (unsigned i = 0; i < 60; ++i)
    {
        worker_health_t ui_health = worker_health_snapshot(WORKER_UI, (uint32_t)(esp_timer_get_time() / 1000));
        if (boot_health_ready(ble_companion_ready(), initial_ticks, atomic_load(&g_app_tick_count), initial_ui,
                              ui_health))
        {
            if (g_runtime && config_trial_needs_confirmation(g_running_config_record.revision))
            {
                esp_err_t err = config_trial_confirm(g_running_config_record.revision);
                if (err != ESP_OK)
                {
                    ESP_LOGE(TAG, "Failed to confirm configuration trial: %s", esp_err_to_name(err));
                    if (ota_trial_pending())
                        esp_ota_mark_app_invalid_rollback_and_reboot();
                    else
                        esp_restart();
                }
                config_runtime_mark_confirmed();
                ESP_LOGI(TAG, "Configuration revision %" PRIu32 " confirmed healthy", g_running_config_record.revision);
            }
            if (ota_trial_pending())
            {
                esp_err_t err = esp_ota_mark_app_valid_cancel_rollback();
                if (err != ESP_OK)
                {
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
    if (ota_trial_pending())
        esp_ota_mark_app_invalid_rollback_and_reboot();
    else
        esp_restart();
    vTaskDelete(NULL);
}

// ---------------------------------------------------------------------------------------------------------------------
// Private Function Definitions
// ---------------------------------------------------------------------------------------------------------------------

static void obd_response_cb(uint8_t        service,
                            uint16_t       pid,
                            uint32_t       responder,
                            uint8_t const *data,
                            size_t         len,
                            uint32_t       generation,
                            uint32_t       observed_at_ms,
                            void          *usr_ctx)
{
    ESP_NULL_CHECK(usr_ctx, TAG, "User context is NULL");
    source_worker_t *worker = usr_ctx;
    ui_t            *ui     = worker->ui;
    unsigned         source = worker->source;
    if (!adapter_status_ready_for(source) || generation != adapter_status_generation(source))
        return;

    /* Diagnostic PID replies are consumed synchronously with endpoint identity. */
    if (pid == 0x01)
        return;

    const obd_pid_cfg_t *definition = NULL;
    for (unsigned i = 0; i < poll_count(); ++i)
        if (poll_cfg(i)->pid == pid &&
            (g_runtime ? g_runtime->pids[i].source_index == source && g_runtime->pids[i].service == service &&
                             g_runtime->pids[i].responder == responder
                       : source == 0 && service == 1 && (responder == 0x7e8 || responder == ELM_ECU_ANY)))
        {
            definition = poll_cfg(i);
            break;
        }
    if (!definition)
        return;
    if (data == NULL || len == 0)
    {
        ESP_LOGE(TAG, "Received empty data for PID 0x%02X", pid);
        queue_alert_sample(source, pid, generation, NAN);
        publish_ui_sample(ui, source, generation, pid, NULL);
        return;
    }

    if (len != definition->len)
    {
        ESP_LOGW(TAG, "Invalid length for PID 0x%02X: expected %zu, got %zu", pid, definition->len, len);
        queue_alert_sample(source, pid, generation, NAN);
        publish_ui_sample(ui, source, generation, pid, NULL);
        return;
    }

    double decoded;
    if (g_runtime && pid == 0x5503 && !transmission_gear_label(data[0]))
    {
        queue_alert_sample(source, pid, generation, NAN);
        publish_ui_sample(ui, source, generation, pid, NULL);
        return;
    }
    if (!pid_decoder_eval(&definition->decoder, data, len, &decoded))
    {
        ESP_LOGW(TAG, "Rejected invalid value for PID 0x%02X", pid);
        queue_alert_sample(source, pid, generation, NAN);
        publish_ui_sample(ui, source, generation, pid, NULL);
        return;
    }

    queue_alert_sample(source, pid, generation, decoded);

    ESP_LOGD(TAG, "Received PID 0x%02X (%s): %.3f", pid, definition->name, decoded);
    ui_set_source_value(ui, source, g_runtime ? g_running_config_record.revision : 0, generation, pid, &decoded,
                        observed_at_ms);
    worker->decoded_sample = true;
    if (g_runtime)
    {
        for (unsigned index = 0; index < g_runtime->pid_count; ++index)
        {
            const runtime_pid_t *configured = &g_runtime->pids[index];
            if (configured->source_index == source && &configured->obd == definition)
                reading_stability_observe(&reading_stability[index], configured->id, configured->service,
                                          configured->obd.pid, configured->poll_ms, configured->stale_ms,
                                          (float)decoded, observed_at_ms, generation);
        }
    }
    if (worker->command_poll && worker->reused_samples != UINT32_MAX)
        ++worker->reused_samples;
    for (unsigned i = 0; worker->scheduler && i < worker->count; ++i)
        if (poll_cfg(worker->indices[i]) == definition)
            poll_scheduler_sample(worker->scheduler, i, observed_at_ms);
}

static void app_tick_task(void *arg)
{
    ui_t      *ui                 = arg;
    TickType_t last_wake          = xTaskGetTickCount();
    uint32_t   diagnostic_view[2] = {UINT32_MAX, UINT32_MAX};
    for (;;)
    {
        vTaskDelayUntil(&last_wake, pdMS_TO_TICKS(100));
        uint32_t now_ms = pdTICKS_TO_MS(xTaskGetTickCount());
        ble_companion_tick();
        for (uint8_t kind = 1; kind <= 2; ++kind)
        {
            uint8_t status[32];
            if (vehicle_commands_runtime_ui_status(kind, status))
                ui_set_vehicle_action_snapshot(ui, status);
        }
        alert_sample_event_t event;
        for (unsigned drained = 0;
             drained < ALERT_SAMPLE_QUEUE_LENGTH && xQueueReceive(g_alert_sample_queue, &event, 0) == pdTRUE; ++drained)
        {
            if (!g_runtime || event.pid_index >= g_runtime->pid_count)
                continue;
            unsigned source = g_runtime ? g_runtime->pids[event.pid_index].source_index : 0;
            if (adapter_status_ready_for(source) && event.generation == adapter_status_generation(source))
            {
                if ((!g_runtime || g_runtime->pids[event.pid_index].responder != 0x7e9) &&
                    poll_cfg(event.pid_index)->pid == 0x0c && isfinite(event.value))
                {
                    engine_rpm_at  = event.observed_at_ms;
                    engine_running = event.value > 0;
                }
                if ((!g_runtime || g_runtime->pids[event.pid_index].responder != 0x7e9) &&
                    poll_cfg(event.pid_index)->pid == 0x0d && isfinite(event.value))
                {
                    speed_at       = event.observed_at_ms;
                    vehicle_moving = event.value > 0;
                }
                alert_runtime_sample(event.pid_index, event.value, event.observed_at_ms);
                alert_engine_sample(event.pid_index, event.value, event.observed_at_ms);
            }
        }
        unsigned lost = atomic_exchange(&g_alert_lost_pids, 0);
        for (unsigned i = 0; i < EGAUGE_RUNTIME_PIDS; ++i)
            if (lost & (UINT32_C(1) << i))
            {
                alert_engine_invalidate_pid(i);
                alert_runtime_sample(i, NAN, now_ms);
            }
        for (unsigned source = 0; source < runtime_source_count(); ++source)
        {
            if (!adapter_status_ready_for(source) || ble_mgr_is_paused() || transfer_gate_current() == 2)
                clear_source(ui, source);
            if (!adapter_status_ready_for(source))
            {
                alert_engine_invalidate_source(source);
                if (g_runtime)
                    for (unsigned i = 0; i < g_runtime->pid_count; i++)
                        if (g_runtime->pids[i].source_index == source)
                            alert_runtime_sample(i, NAN, now_ms);
            }
        }
        alert_summary_t alert = alert_engine_tick(now_ms);
        (void)alert;
        diagnostics_snapshot_t presentation          = {.mil_known = true};
        bool                   presentation_complete = true;
        for (unsigned endpoint = 0; endpoint < 2; ++endpoint)
        {
            if (!diagnostics_endpoint_enabled(endpoint))
                continue;
            uint8_t packet[248];
            if (!diagnostic_alerts_observe(endpoint, now_ms, packet, alert_runtime_observe))
            {
                presentation_complete  = false;
                presentation.mil_known = false;
                continue;
            }
            bool fresh = (packet[2] & 1) != 0;
            presentation.mil_known &= fresh;
            diagnostic_filter_result_t visible =
                diagnostic_filter_project(packet, endpoint, g_runtime ? g_runtime->diagnostic_filters : NULL,
                                          g_runtime ? g_runtime->diagnostic_filter_count : 0);
            uint32_t view = (fresh ? 1U : 0U) | (packet[2] & 2U) | (visible.complete ? 4U : 0U) |
                            (visible.count_known ? 8U : 0U) | (visible.suppress_mil ? 16U : 0U) |
                            (visible.show_mil ? 32U : 0U) | ((uint32_t)visible.visible_count << 8) |
                            ((uint32_t)visible.hidden_count << 16) | ((uint32_t)packet[22] << 24);
            if (view != diagnostic_view[endpoint])
            {
                diagnostic_view[endpoint] = view;
                ESP_LOGI(TAG,
                         "diag_view endpoint=%u usable_mil=%u mil_bit=%u reported=%u visible=%u hidden=%u complete=%u "
                         "suppressed=%u shown=%u",
                         endpoint, fresh, (packet[2] & 2) != 0, packet[22], visible.visible_count, visible.hidden_count,
                         visible.complete, visible.suppress_mil, visible.show_mil);
            }
            presentation.valid |= fresh || visible.count_known;
            presentation.mil_on |= visible.show_mil;
            presentation.count_known |= visible.count_known;
            presentation.distinct_count += visible.visible_count;
            if (!visible.complete || !visible.count_known)
                presentation_complete = false;
        }
        alert_runtime_tick(now_ms);
        diagnostic_clear_tick(now_ms, now_ms - engine_rpm_at <= 2000 && engine_running,
                              now_ms - speed_at <= 2000 && vehicle_moving);
        alert_event_t chosen     = {0};
        bool          attention  = false;
        uint8_t       additional = 0;
        bool          active     = alert_runtime_condition_summary(now_ms, &chosen, &attention, &additional);
        ui_set_alert_condition(ui,
                               active ? (chosen.severity == 4   ? 2
                                         : chosen.severity >= 3 ? 1
                                                                : 3)
                                      : 0,
                               chosen.unavailable, chosen.label, active && chosen.severity == 4 && attention,
                               chosen.key, chosen.boot, chosen.episode, chosen.value, chosen.limit, chosen.unit,
                               additional, (chosen.reserved & 1) != 0);
        presentation.count_partial = presentation.count_known && !presentation_complete;
        ui_set_vehicle_diagnostics(ui, &presentation);
        worker_health_progress(WORKER_APP, now_ms);
        uint32_t ticks = atomic_fetch_add(&g_app_tick_count, 1) + 1;
        if (ticks % 600 == 0)
        {
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
            for (worker_id_t id = 0; id < WORKER_COUNT; ++id)
            {
                worker_health_t health = worker_health_snapshot(id, now_ms);
                if (health.observed)
                    ESP_LOGI(TAG,
                             "health worker=%s progress=%" PRIu32 " age_ms=%" PRIu32 " max_gap_ms=%" PRIu32
                             " queue_drops=%" PRIu32 " stack_seen=%u stack_free=%" PRIu32,
                             worker_health_name(id), health.progress_count, health.age_ms, health.maximum_gap_ms,
                             health.queue_drops, health.stack_observed ? 1U : 0U, health.minimum_stack_free);
            }
            memory_health_log("periodic");
#if CONFIG_EGAUGE_AUTO_ORIENTATION_PROBE_ENABLED
            orientation_diagnostics_t orientation = orientation_runtime_diagnostics();
            ESP_LOGI(TAG, "orientation health=%u phase=%u failures=%lu stack_free=%u", orientation.healthy,
                     orientation.phase, (unsigned long)orientation.failures, orientation_runtime_stack_margin());
#endif
        }
    }
}

static void obd_task(void *arg)
{
    ESP_NULL_CHECK(arg, TAG, "task arg is NULL");
    source_worker_t        *worker       = arg;
    ui_t                   *ui           = worker->ui;
    unsigned                source       = worker->source;
    const runtime_source_t *configured   = g_runtime ? &g_runtime->sources[source] : NULL;
    bool                    transmission = configured && configured->transmission;
    bool                    simulated    = configured && configured->simulated;
    uint32_t                responder    = transmission ? 0x7e9 : 0x7e8;
    uint8_t                *pid_indices  = worker->indices;
    uint8_t                 count        = 0;
    for (unsigned i = 0; i < poll_count(); ++i)
        if (!g_runtime || g_runtime->pids[i].source_index == source)
            pid_indices[count++] = i;
    worker->count = count;
    ESP_LOGI(TAG, "RX/TX source %u task started", source);

    ble_obd_ctx_t *obd                = NULL;
    uint32_t       reconnect_delay_ms = 500;

    const uint32_t    period_ms    = 20;
    const uint32_t    timeout_ms   = 300;
    const uint8_t     dtc_modes[3] = {3, 7, 10};
    poll_scheduler_t *scheduler = worker->scheduler = &source_schedulers[source];
    uint8_t           endpoints[2], endpoint_count = 0;
    for (unsigned e = 0; e < 2; ++e)
        if (diagnostics_endpoint_enabled(e) && diagnostics_endpoint_transport(e) == source)
            endpoints[endpoint_count++] = e;
    poll_scheduler_init(scheduler);
    poll_scheduler_endpoints(scheduler, simulated ? 0 : endpoint_count);

    uint32_t logged_at = 0, transactions = 0, maximum_request_ms = 0;
    while (true)
    {
        vTaskDelay(pdMS_TO_TICKS(period_ms));
        uint32_t health_now = (uint32_t)(esp_timer_get_time() / 1000);
        if (health_now - logged_at >= 60000)
        {
            logged_at = health_now;
            ESP_LOGI(TAG,
                     "poll source=%u requests=%" PRIu32 " reused=%" PRIu32 " max_ms=%" PRIu32 " deferred=%" PRIu32
                     " forced=%" PRIu32 " gaps=%" PRIu32,
                     source, transactions, worker->reused_samples, maximum_request_ms, scheduler->deferred_diagnostics,
                     scheduler->forced_diagnostics, scheduler->deadline_misses);
        }
        worker_health_progress(source == 0 ? WORKER_ECM : WORKER_TCM, (uint32_t)(esp_timer_get_time() / 1000));
        /* Normal controller masks remain zero. An explicitly built parked-idle
         * test adapter may supply scoped evidence inside this primary owner. */
        vehicle_command_context_t command_context = {
            .revision  = g_runtime ? g_running_config_record.revision : 0,
            .link      = adapter_status_generation(source),
            .connected = obd && ble_obd_is_connected(obd) && transfer_gate_vehicle_allowed() && !ble_mgr_is_paused()};
        if (command_context.connected && transfer_gate_current() != 2)
            ui_set_source_state(ui, source, true, command_context.revision, command_context.link);
        worker->command_poll  = true;
        bool command_reserved = vehicle_commands_runtime_worker(source, obd, command_context,
                                                                (uint32_t)(xTaskGetTickCount() * portTICK_PERIOD_MS));
        worker->command_poll  = false;
        if (command_reserved)
            continue;
        if (transfer_gate_current() == 2 || ble_mgr_is_paused())
        {
            clear_source(ui, source);
            diagnostics_state_disconnected_for(source);
            for (unsigned e = 0; e < endpoint_count; ++e)
                diagnostics_state_disconnected_for(2 + endpoints[e]);
            continue;
        }

        if (obd == NULL || !ble_obd_is_connected(obd))
        {
            clear_source(ui, source);
            diagnostics_state_disconnected_for(source);
            for (unsigned e = 0; e < endpoint_count; ++e)
                diagnostics_state_disconnected_for(2 + endpoints[e]);
            if (g_runtime && !g_runtime->legacy_auto_discovery && !configured->address[0])
                continue;
            const char *address =
                configured && configured->address[0] ? configured->address : CONFIG_EGAUGE_ECM_ADAPTER_MAC;
            uint8_t address_type = configured ? configured->address_type : 0;
            obd = ble_obd_connect_profile_ecu(source, address, address_type, simulated ? "elm-bench-v1" : "elm-18f0-v1",
                                              responder, obd_response_cb, worker);
            if (!obd)
            {
                ESP_LOGW(TAG, "Vehicle adapter unavailable; retrying in %" PRIu32 " ms", reconnect_delay_ms);
                vTaskDelay(pdMS_TO_TICKS(reconnect_delay_ms));
                if (reconnect_delay_ms < 8000)
                    reconnect_delay_ms *= 2;
                continue;
            }
            ESP_LOGI(TAG, "Vehicle adapter link ready");
            diagnostics_state_connected_for(source);
            for (unsigned e = 0; e < endpoint_count; ++e)
                diagnostics_state_connected_for(2 + endpoints[e]);
            reconnect_delay_ms = 500;
            poll_scheduler_init(scheduler);
            poll_scheduler_endpoints(scheduler, simulated ? 0 : endpoint_count);
            continue;
        }

        ui_set_source_state(ui, source, true, g_runtime ? g_running_config_record.revision : 0,
                            adapter_status_generation(source));
        uint32_t      now_ms = (uint32_t)(esp_timer_get_time() / 1000);
        clear_state_t clear;
        if (diagnostic_clear_take(source, pdTICKS_TO_MS(xTaskGetTickCount()), &clear))
        {
            uint8_t       bytes[64];
            size_t        length = sizeof(bytes);
            elm_result_t  response;
            int           result = ble_obd_read_service_status_ecu(obd, 4, 0x7e8, 1500, bytes, &length, &response);
            clear_phase_t phase  = CLEAR_UNKNOWN;
            if (result == 0)
            {
                phase              = CLEAR_VERIFYING;
                bool     verified  = true;
                unsigned counts[3] = {0};
                vTaskDelay(pdMS_TO_TICKS(500));
                for (unsigned i = 0; i < 3; i++)
                {
                    length = sizeof(bytes);
                    if (ble_obd_read_service_status_ecu(obd, dtc_modes[i], 0x7e8, 1500, bytes, &length, &response) == 0)
                    {
                        for (size_t j = 0; j + 1 < length; j += 2)
                            if (bytes[j] || bytes[j + 1])
                                counts[i]++;
                        diagnostics_state_codes_for(2, dtc_modes[i], bytes, length, pdTICKS_TO_MS(xTaskGetTickCount()));
                        if (!transmission)
                            diagnostics_state_codes_for(source, dtc_modes[i], bytes, length,
                                                        pdTICKS_TO_MS(xTaskGetTickCount()));
                    }
                    else
                    {
                        diagnostics_state_failed_for(2, dtc_modes[i],
                                                     response == ELM_UNSUPPORTED ? DIAGNOSTICS_UNSUPPORTED
                                                                                 : DIAGNOSTICS_UNAVAILABLE);
                        if (response != ELM_UNSUPPORTED)
                            verified = false;
                    }
                }
                elm_payload_t mil;
                if (ble_obd_read_pid(obd, 1, 1, 0x7e8, 700, &response, &mil) == 0 && mil.length >= 4)
                {
                    diagnostics_state_mil_payload(2, mil.bytes, pdTICKS_TO_MS(xTaskGetTickCount()));
                    if (!transmission)
                        diagnostics_state_mil_for(source, (mil.bytes[0] & 0x80) != 0, mil.bytes[0] & 0x7f,
                                                  pdTICKS_TO_MS(xTaskGetTickCount()));
                    if (mil.bytes[0] & 0x80)
                        counts[0]++;
                }
                else
                {
                    verified = false;
                    diagnostics_state_failed_for(2, 1, DIAGNOSTICS_UNAVAILABLE);
                }
                phase = !verified                ? CLEAR_UNKNOWN
                        : counts[0] || counts[1] ? CLEAR_CODES_REMAIN
                        : counts[2]              ? CLEAR_PERMANENT_REMAIN
                                                 : CLEAR_VERIFIED;
            }
            diagnostic_clear_finish(clear.operation, phase);
            poll_scheduler_init(scheduler);
            poll_scheduler_endpoints(scheduler, simulated ? 0 : endpoint_count);
            continue;
        }
        /* A Stop may have arrived during the preceding guard reads. */
        if (source == 0 && vehicle_commands_runtime_pending())
            continue;
        poll_pid_policy_t policies[POLL_SCHEDULER_MAX_PIDS];
        unsigned          selected = atomic_load(&g_selected_page);
        unsigned          next     = display_settings_snapshot().smart_cycle && g_runtime
                                         ? atomic_load(&g_upcoming_page) % page_count()
                                         : (selected + 1) % page_count();
        bool              cycling  = display_settings_snapshot().cycle_seconds != 0;
        for (uint8_t i = 0; i < count; ++i)
        {
            unsigned index   = pid_indices[i];
            bool     visible = false, upcoming = false, alert = false;
            if (g_runtime)
            {
                const runtime_page_t *page      = &g_runtime->pages[selected % page_count()];
                const runtime_page_t *following = &g_runtime->pages[next];
                for (unsigned m = 0; m < page->pid_count; ++m)
                    visible |= page->pid_indices[m] == index;
                for (unsigned m = 0; cycling && m < following->pid_count; ++m)
                    upcoming |= following->pid_indices[m] == index;
                for (unsigned a = 0; a < g_runtime->alert_count; ++a)
                    alert |= g_runtime->alerts[a].pid_index == index;
                const runtime_pid_t *pid = &g_runtime->pids[index];
                policies[i] = reading_policy_adaptive(pid->id, pid->service, pid->obd.pid, pid->poll_ms, pid->stale_ms,
                                                      visible, upcoming, alert, &reading_stability[index], now_ms,
                                                      adapter_status_generation(source));
            }
            else
                policies[i] = (poll_pid_policy_t){500, 3000, selected == index ? 2 : 0};
        }
        const poll_scheduler_budget_t budget = {.pid_timeout_ms = timeout_ms,
                                                .mil_timeout_ms = 700,
                                                .dtc_timeout_ms = 1500,
                                                .recovery_ms    = period_ms + 50,
                                                .foreground_ms  = vehicle_commands_runtime_poll_reserve(
                                                    source, 1500 + period_ms, pdTICKS_TO_MS(xTaskGetTickCount()))};
        poll_job_t                    job = poll_scheduler_next_budgeted(scheduler, now_ms, policies, count, &budget);
        if (job.kind == POLL_JOB_NONE)
            continue;
        if (simulated && (job.kind == POLL_JOB_MIL || job.kind == POLL_JOB_DTC))
            continue;
        if (job.kind == POLL_JOB_MIL || job.kind == POLL_JOB_DTC)
        {
            if (!endpoint_count)
                continue;
            unsigned     endpoint   = endpoints[job.endpoint];
            uint32_t     ecu        = endpoint == 1 ? 0x7e9 : 0x7e8;
            unsigned     slots[2]   = {2 + endpoint, source};
            unsigned     slot_count = (transmission == (endpoint == 1)) ? 2 : 1;
            elm_result_t response   = ELM_ADAPTER_ERROR;
            uint8_t      previous[248];
            bool         had_previous =
                diagnostics_endpoint_status(endpoint, pdTICKS_TO_MS(xTaskGetTickCount()), previous) != 0 &&
                (previous[2] & 12) == 4 && previous[23];
            uint32_t      generation = adapter_status_generation(source);
            uint8_t       mode       = job.kind == POLL_JOB_MIL ? 1 : dtc_modes[job.index];
            elm_payload_t payload    = {0};
            uint8_t       codes[64];
            size_t        length     = sizeof(codes);
            uint32_t      started_at = (uint32_t)(esp_timer_get_time() / 1000);
            int result = mode == 1 ? ble_obd_read_pid(obd, 1, 1, ecu, 700, &response, &payload)
                                   : ble_obd_read_service_status_ecu(obd, mode, ecu, 1500, codes, &length, &response);
            uint32_t duration = (uint32_t)(esp_timer_get_time() / 1000) - started_at;
            if (duration > maximum_request_ms)
                maximum_request_ms = duration;
            if (transactions != UINT32_MAX)
                ++transactions;
            if (generation != adapter_status_generation(source) || !adapter_status_ready_for(source))
                continue;
            poll_scheduler_diagnostic_complete(scheduler, job, (uint32_t)(esp_timer_get_time() / 1000),
                                               result == 0 && (mode != 1 || payload.length >= 4) ? POLL_DIAGNOSTIC_OK
                                               : response == ELM_UNSUPPORTED ? POLL_DIAGNOSTIC_UNSUPPORTED
                                                                             : POLL_DIAGNOSTIC_RETRY);
            if (mode == 1 && result == 0 && payload.length >= 4 && had_previous &&
                ((((previous[2] & 2) != 0) != ((payload.bytes[0] & 0x80) != 0)) ||
                 previous[22] != (payload.bytes[0] & 0x7f)))
                poll_scheduler_mil_changed(scheduler, job.endpoint);
            for (unsigned slot = 0; slot < slot_count; ++slot)
            {
                if (result == 0 && mode == 1 && payload.length >= 4)
                    diagnostics_state_mil_payload(slots[slot], payload.bytes, pdTICKS_TO_MS(xTaskGetTickCount()));
                else if (result == 0 && mode != 1)
                    diagnostics_state_codes_for(slots[slot], mode, codes, length, pdTICKS_TO_MS(xTaskGetTickCount()));
                else
                    diagnostics_state_failed_for(slots[slot], mode,
                                                 response == ELM_UNSUPPORTED ? DIAGNOSTICS_UNSUPPORTED
                                                                             : DIAGNOSTICS_UNAVAILABLE);
            }
            continue;
        }

        const obd_pid_cfg_t *requested = poll_cfg(pid_indices[job.index]);
        const uint8_t        obd_mode  = g_runtime ? g_runtime->pids[pid_indices[job.index]].service : 0x01;

        uint32_t     poll_generation = adapter_status_generation(source);
        elm_result_t response        = ELM_PENDING;
        worker->decoded_sample       = false;
        uint32_t started_at          = (uint32_t)(esp_timer_get_time() / 1000);
        int      status              = ble_obd_rxtx_status_ecu(
            obd, obd_mode, requested->pid, g_runtime ? g_runtime->pids[pid_indices[job.index]].responder : ELM_ECU_ANY,
            timeout_ms, &response);
        uint32_t duration = (uint32_t)(esp_timer_get_time() / 1000) - started_at;
        if (duration > maximum_request_ms)
            maximum_request_ms = duration;
        if (transactions != UINT32_MAX)
            ++transactions;
        poll_scheduler_complete(scheduler, job, (uint32_t)(esp_timer_get_time() / 1000),
                                status == 0 && worker->decoded_sample);
        if (status != 0 || !worker->decoded_sample)
            reading_stability_reset(&reading_stability[pid_indices[job.index]]);
        if (status != 0)
        {
            queue_alert_sample(source, requested->pid, poll_generation, NAN);
            /* No completed reply is a transient gap, not a new observation.
             * Strict alert/guard consumers still fail; display expires naturally. */
            if (response != ELM_PENDING)
                publish_ui_sample(ui, source, poll_generation, requested->pid, NULL);
            ESP_LOGW(TAG, "Reading request incomplete: result=%d response=%u", status, response);
        }
    }
}

static bool adapter_mac_valid(const char *mac)
{
    if (strlen(mac) != 17)
        return false;
    for (size_t i = 0; i < 17; i++)
    {
        if (i % 3 == 2)
        {
            if (mac[i] != ':')
                return false;
        }
        else if (!isxdigit((unsigned char)mac[i]))
            return false;
    }
    return true;
}

static void init_obd_task(ui_t *ui)
{
    for (unsigned source = 0; source < runtime_source_count(); ++source)
    {
        source_workers[source] = (source_worker_t){.ui = ui, .source = source};
        BaseType_t started =
            xTaskCreate(obd_task, source ? "obd_child" : "obd_primary", 4096, &source_workers[source], 5, NULL);
        ESP_CHECK(started == pdPASS, TAG, "Failed to create source worker");
    }
}

static void ui_touch_callback(ui_t *ui, lv_event_code_t event_code)
{
#if CONFIG_EGAUGE_DISPLAY_CALIBRATION
    if (event_code == LV_EVENT_CLICKED)
        ui_next_display_calibration(ui);
    return;
#endif
    /* A press that starts in demo cannot become a pairing/reset gesture when
     * demo exits on its long-press event. Consume the entire press sequence. */
    static bool press_started_showcase;
    if (event_code == LV_EVENT_PRESSED)
        press_started_showcase = ui_showcase_active(ui);
    if (ui_showcase_active(ui) || press_started_showcase)
    {
        if (event_code == LV_EVENT_CLICKED)
            ui_showcase_next(ui);
        else if (event_code == LV_EVENT_LONG_PRESSED)
            ui_showcase_stop(ui);
        if (event_code == LV_EVENT_RELEASED || event_code == LV_EVENT_CLICKED)
            press_started_showcase = false;
        return;
    }
    switch (event_code)
    {
    case LV_EVENT_PRESSED:
        atomic_store(&g_page_activity_ms, pdTICKS_TO_MS(xTaskGetTickCount()));
        break;
    case UI_EVENT_VEHICLE_COMMAND:
    {
        if (!transfer_gate_vehicle_allowed() || !ble_companion_has_owner())
            break;
        uint8_t  kind, preset;
        bool     stop;
        uint16_t seconds;
        ui_vehicle_command_request(ui, &kind, &stop, &preset, &seconds);
        ui_vehicle_command_queued(ui, vehicle_commands_runtime_intent(kind, stop, preset, seconds));
        break;
    }
    case UI_EVENT_PAGE_ACTION:
        if (!transfer_gate_vehicle_allowed() || !ble_companion_has_owner())
            break;
        if (ui_action_opens_vehicle_menu(ui))
        {
            ui_show_vehicle_actions(ui);
            break;
        }
        /* Fall through to the same local selection/persistence path. */
        __attribute__((fallthrough));
    case LV_EVENT_CLICKED:
    {
        /* Already on the LVGL task with its lock held. Browsing must not wait
         * for app_main, a flash commit, or another UI mailbox interval. */
        uint8_t selected = event_code == UI_EVENT_PAGE_ACTION ? ui_action_target(ui)
                                                              : (atomic_load(&g_selected_page) + 1) % page_count();
        if (selected >= page_count())
            break;
        ui_page_t page;
        page_ui(selected, &page);
        ui_set_page(ui, &page);
        ui_set_page_position(ui, selected, page_count());
        atomic_store(&g_selected_page, selected);
        atomic_store(&g_page_activity_ms, pdTICKS_TO_MS(xTaskGetTickCount()));
        ble_companion_selection_applied(selected);
        atomic_fetch_add(&g_local_page_generation, 1);
        if (event_code == UI_EVENT_PAGE_ACTION)
            ui_action_applied(ui);
        break;
    }
    case LV_EVENT_LONG_PRESSED:
        ble_companion_open_pairing_window();
        break;
    case LV_EVENT_RELEASED:
    {
        if (ble_companion_has_owner())
            ui_show_pairing_code(ui, UI_PAIRING_RESET_CONFIRM);
        break;
    }
    case LV_EVENT_VALUE_CHANGED:
    {
        companion_command_t command = {.opcode = 3};
        if (g_phone_command_queue)
            xQueueSend(g_phone_command_queue, &command, 0);
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
    ESP_ERROR_CHECK(display_settings_init(0, 80, 0));
    gauge_board_initialize();
    gauge_display_set_rotation((lv_display_rotation_t)display_settings_snapshot().rotation);
    if (lvgl_port_lock(portMAX_DELAY))
    {
        ui_boot_logo_early();
        lvgl_port_unlock();
    }
    gauge_display_set_power(true);
    ESP_ERROR_CHECK(gauge_display_set_brightness(display_settings_snapshot().brightness));
    ESP_ERROR_CHECK(config_store_init());
    ESP_ERROR_CHECK(config_trial_init());
    ESP_ERROR_CHECK(ota_transfer_init());
    ESP_ERROR_CHECK(diagnostics_state_init());

    g_runtime = heap_caps_malloc(sizeof(*g_runtime), MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (g_runtime && config_runtime_load(g_runtime, &g_running_config_record, &g_running_previous_config) == ESP_OK)
    {
        ESP_LOGI(TAG, "Running configuration revision %" PRIu32 "%s with %u PIDs, %u pages, %u alerts",
                 g_running_config_record.revision, g_running_previous_config ? " (previous compatible generation)" : "",
                 g_runtime->pid_count, g_runtime->page_count, g_runtime->alert_count);
    }
    else
    {
        if (g_runtime)
            heap_caps_free(g_runtime);
        g_runtime = NULL;
    }
    ESP_ERROR_CHECK(config_transfer_init());
#if CONFIG_EGAUGE_WIFI_BULK_ENABLED
    if (wifi_bulk_init() != ESP_OK)
        ESP_LOGE(TAG, "Wi-Fi maintenance unavailable; preserving gauge and App/BLE operation");
#endif

    esp_err_t err = config_load(&g_config);

    if (err == ESP_OK && (g_config.cfg_idx >= page_count() || g_config.disp_rot > LV_DISPLAY_ROTATION_270))
    {
        ESP_LOGE(TAG, "Saved legacy configuration is invalid");
        g_config = (config_t){.cfg_idx = 0, .disp_rot = LV_DISPLAY_ROTATION_0};
        err      = ESP_ERR_INVALID_ARG;
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
    uint8_t default_rotation   = g_runtime ? g_runtime->rotation : (uint8_t)g_config.disp_rot;
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

    memory_health_init();

    const gauge_board_t *board = gauge_board_description();
    if (board == NULL || !gauge_presentation_matches(board->surface))
    {
        ESP_LOGE(TAG, "No matching UI profile for this display surface");
        return;
    }

    config_document_init();
    init_config();
    ESP_LOGI(TAG, "Main startup stack_free=%u", (unsigned)uxTaskGetStackHighWaterMark(NULL));
    for (unsigned source = 0; source < runtime_source_count(); ++source)
        diagnostics_state_configure_for(source, g_runtime && g_runtime->sources[source].transmission,
                                        g_runtime ? g_running_config_record.revision : 0,
                                        g_runtime && g_runtime->sources[source].simulated);
    bool pairing_required = !ble_companion_load_owner();
    alert_engine_init(g_runtime);
    ESP_ERROR_CHECK(alert_runtime_init(g_runtime ? g_running_config_record.revision : 0));
    ESP_ERROR_CHECK(diagnostic_clear_init());
    ESP_ERROR_CHECK(vehicle_commands_runtime_init(g_runtime));
    alert_engine_set_sink(alert_runtime_observe);
    if (g_runtime)
    {
        for (unsigned i = 0; i < g_runtime->alert_count; i++)
            alert_runtime_priority(i, g_runtime->alerts[i].priority);
        for (unsigned i = 0; i < g_runtime->pid_count; i++)
            alert_runtime_stale(i, g_runtime->pids[i].stale_ms);
    }
    if (g_runtime)
        for (unsigned i = 0; i < g_runtime->alert_count; i++)
            alert_runtime_simulated(
                i, g_runtime->sources[g_runtime->pids[g_runtime->alerts[i].pid_index].source_index].simulated);

    hardware_probe_set_ready(HARDWARE_FEATURE_DISPLAY | HARDWARE_FEATURE_TOUCH | HARDWARE_FEATURE_BACKLIGHT, true);

    apply_display_rotation(g_config.disp_rot);

    const uint32_t ui_interval_ms = 50;
    ui_page_t      initial_page;
    page_ui(g_config.cfg_idx, &initial_page);
    ui_t *ui = ui_init(&initial_page, ui_interval_ms, ui_touch_callback, pairing_required,
                       display_settings_snapshot().units == 1);
    ESP_NULL_CHECK(ui, TAG, "Failed to initialize UI");
    if (lvgl_port_lock(portMAX_DELAY))
    {
        ui_set_page_position(ui, g_config.cfg_idx, page_count());
        if (g_runtime)
        {
            ui_configure_page_action(ui, g_runtime->page_action);
            double idle_limit;
            ui_set_idle_protection(ui, vehicle_idle_coolant_limit(g_runtime, &idle_limit));
        }
        lvgl_port_unlock();
    }
    ESP_ERROR_CHECK(gauge_display_set_brightness(display_settings_snapshot().brightness));
    orientation_runtime_init(display_settings_snapshot().rotation);

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
    if (CONFIG_EGAUGE_ECM_ADAPTER_MAC[0] && !adapter_mac_valid(CONFIG_EGAUGE_ECM_ADAPTER_MAC))
    {
        ESP_LOGE(TAG, "Invalid ECM adapter MAC in firmware configuration");
        return;
    }
    adapter_status_init(g_runtime);
    if (runtime_simulated() && lvgl_port_lock(portMAX_DELAY))
    {
        ui_set_simulated(ui);
        lvgl_port_unlock();
    }
    ESP_ERROR_CHECK(adapter_registry_init());
    obd_trace_init();
    for (unsigned source = 0; source < runtime_source_count(); ++source)
        if (g_runtime && g_runtime->sources[source].simulated)
            obd_trace_emit(source, 0, "source_simulated", NULL, 0, 1);
    for (unsigned e = 0; e < 2; ++e)
    {
        int transport = -1;
        for (unsigned i = 0; i < poll_count(); ++i)
        {
            unsigned role = g_runtime && g_runtime->pids[i].responder == 0x7e9 ? 1 : 0;
            if (role == e)
                transport = g_runtime ? g_runtime->pids[i].source_index : 0;
        }
        if (e == 0 && transport < 0 && (!g_runtime || !g_runtime->sources[0].transmission))
            transport = 0;
        if (g_runtime)
            for (unsigned i = 0; i < g_runtime->source_count; ++i)
                if (g_runtime->sources[i].transmission == (e == 1))
                    transport = (int)i;
        if (transport >= 0)
        {
            alert_runtime_simulated(32 + e, g_runtime && g_runtime->sources[transport].simulated);
            alert_runtime_simulated(34 + e, g_runtime && g_runtime->sources[transport].simulated);
        }
        if (transport >= 0)
            diagnostics_endpoint_configure(e, transport, g_running_config_record.revision,
                                           g_runtime && g_runtime->sources[transport].simulated);
    }
    smart_configure();
    atomic_store(&g_upcoming_page, (atomic_load(&g_selected_page) + 1U) % page_count());
    init_obd_task(ui);
    BaseType_t tick_started = xTaskCreate(app_tick_task, "app_tick", 4096, ui, 5, NULL);
    ESP_CHECK(tick_started == pdPASS, TAG, "Application tick task creation failed");
    bool config_trial = g_runtime && config_trial_needs_confirmation(g_running_config_record.revision);
    if (ota_trial_pending() || config_trial)
    {
        BaseType_t started = xTaskCreate(boot_health_task, "boot_health", 3072, NULL, 5, NULL);
        ESP_CHECK(started == pdPASS, TAG, "Boot health task creation failed");
    }

    page_save_policy_t  page_save = {0};
    page_cycle_policy_t cycling   = {0};
    atomic_store(&g_page_activity_ms, pdTICKS_TO_MS(xTaskGetTickCount()));
    while (true)
    {
        companion_command_t command;
        bool                has_command = xQueueReceive(g_phone_command_queue, &command, pdMS_TO_TICKS(20)) == pdTRUE;
        if (has_command && command.opcode == 1 && command.value < page_count() && !ui_action_pending(ui))
        {
            config_t updated = g_config;
            updated.cfg_idx  = command.value;
            if (config_save(&updated) == ESP_OK)
            {
                g_config = updated;
                if (lvgl_port_lock(portMAX_DELAY))
                {
                    ui_page_t page;
                    page_ui(command.value, &page);
                    ui_set_page(ui, &page);
                    ui_set_page_position(ui, command.value, page_count());
                    atomic_store(&g_selected_page, command.value);
                    atomic_store(&g_page_activity_ms, pdTICKS_TO_MS(xTaskGetTickCount()));
                    lvgl_port_unlock();
                    ble_companion_selection_applied(command.value);
                }
            }
        }
        else if (has_command && command.opcode == 2 && command.value <= LV_DISPLAY_ROTATION_270)
        {
            config_t saved;
            uint32_t revision;
            if (config_read_snapshot(&saved, &revision) != ESP_OK)
                continue;
            saved.disp_rot = (lv_display_rotation_t)command.value;
            if (config_save_if_revision(&saved, command.base_revision, NULL) != ESP_OK)
                continue;
            display_settings_t display = display_settings_snapshot();
            if (display.rotation != command.value &&
                display_settings_save(command.value, display.brightness, display.units, display.cycle_seconds,
                                      display.revision, NULL) != ESP_OK)
                ESP_LOGW(TAG, "Legacy rotation saved but display preference update failed");
            g_config = saved;
            if (lvgl_port_lock(portMAX_DELAY))
            {
                ui_reset_action_sequence(ui);
                apply_display_rotation(saved.disp_rot);
                lvgl_port_unlock();
            }
            ESP_LOGI(TAG, "Display rotation saved: %u", (unsigned)command.value * 90);
        }
        else if (has_command && command.opcode == 0x36)
        {
            display_settings_t saved;
            display_settings_t before = display_settings_snapshot();
            if (display_settings_save(command.value, command.brightness, before.units, before.cycle_seconds,
                                      command.base_revision, &saved) != ESP_OK)
                continue;
            g_config.disp_rot = (lv_display_rotation_t)saved.rotation;
            if (lvgl_port_lock(portMAX_DELAY))
            {
                ui_reset_action_sequence(ui);
                apply_display_rotation(g_config.disp_rot);
                lvgl_port_unlock();
            }
            ESP_ERROR_CHECK(gauge_display_set_brightness(saved.brightness));
            ESP_LOGI(TAG, "Display settings saved: rotation=%u brightness=%u%%", (unsigned)saved.rotation * 90,
                     (unsigned)saved.brightness);
        }
        else if (has_command && command.opcode == 0x37)
        {
            display_settings_t saved;
            display_settings_t before = display_settings_snapshot();
            if (display_settings_save(command.value, command.brightness, command.units, before.cycle_seconds,
                                      command.base_revision, &saved) != ESP_OK)
                continue;
            g_config.disp_rot = (lv_display_rotation_t)saved.rotation;
            if (lvgl_port_lock(portMAX_DELAY))
            {
                ui_reset_action_sequence(ui);
                apply_display_rotation(g_config.disp_rot);
                ui_set_units(ui, saved.units == 1);
                lvgl_port_unlock();
            }
            ESP_ERROR_CHECK(gauge_display_set_brightness(saved.brightness));
            ESP_LOGI(TAG, "Display settings saved: rotation=%u brightness=%u%% units=%u", (unsigned)saved.rotation * 90,
                     (unsigned)saved.brightness, (unsigned)saved.units);
        }
        else if (has_command && (command.opcode == 0x38 || command.opcode == 0x69))
        {
            display_settings_t saved;
            esp_err_t result = command.opcode == 0x69
                                   ? display_settings_save_smart(command.value, command.brightness, command.units,
                                                                 command.cycle_seconds, command.smart_cycle,
                                                                 command.base_revision, &saved)
                                   : display_settings_save(command.value, command.brightness, command.units,
                                                           command.cycle_seconds, command.base_revision, &saved);
            if (result != ESP_OK)
                continue;
            if (!saved.auto_orientation)
                g_config.disp_rot = (lv_display_rotation_t)saved.rotation;
            if (lvgl_port_lock(portMAX_DELAY))
            {
                ui_reset_action_sequence(ui);
                if (!saved.auto_orientation)
                {
                    apply_display_rotation(g_config.disp_rot);
                }
                ui_set_units(ui, saved.units == 1);
                lvgl_port_unlock();
            }
            ESP_ERROR_CHECK(gauge_display_set_brightness(saved.brightness));
            atomic_store(&g_page_activity_ms, pdTICKS_TO_MS(xTaskGetTickCount()));
            ESP_LOGI(TAG, "Display settings saved: rotation=%u brightness=%u%% units=%u cycle=%us",
                     (unsigned)saved.rotation * 90, (unsigned)saved.brightness, (unsigned)saved.units,
                     (unsigned)saved.cycle_seconds);
        }
        else if (has_command && command.opcode == 0x6b)
        {
            display_settings_t saved;
            if (command.auto_orientation && !orientation_runtime_snapshot().usable)
                continue;
            if (display_settings_save_orientation(command.auto_orientation, command.value, command.base_revision,
                                                  &saved) != ESP_OK)
                continue;
            if (!saved.auto_orientation && lvgl_port_lock(portMAX_DELAY))
            {
                ui_reset_action_sequence(ui);
                apply_display_rotation((lv_display_rotation_t)saved.rotation);
                g_config.disp_rot = (lv_display_rotation_t)saved.rotation;
                lvgl_port_unlock();
            }
            atomic_store(&g_page_activity_ms, pdTICKS_TO_MS(xTaskGetTickCount()));
        }
        else if (has_command && command.opcode == 0x64)
        {
            ui_request_showcase(ui, command.value != 0, command.brightness, command.base_revision,
                                adapter_status_ready_for(0) || adapter_status_ready_for(1),
                                transfer_gate_current() != 0);
            atomic_store(&g_page_activity_ms, pdTICKS_TO_MS(xTaskGetTickCount()));
        }
        else if (has_command && command.opcode == 3)
            ble_companion_forget_owner();

        uint32_t           now_ms      = (uint32_t)(esp_timer_get_time() / 1000);
        display_settings_t cycle       = display_settings_snapshot();
        bool               owner_ready = ble_companion_has_owner();
        bool               maintenance = !transfer_gate_vehicle_allowed();
        uint8_t            auto_angle;
        if (orientation_runtime_poll(cycle.auto_orientation, cycle.rotation,
                                     maintenance || ble_mgr_is_paused() ||
                                         transfer_gate_current() == TRANSFER_GATE_VEHICLE_COMMAND ||
                                         vehicle_commands_runtime_pending() || ui_action_pending(ui),
                                     &auto_angle) &&
            orientation_runtime_retry_ready(now_ms) && lvgl_port_lock(0))
        {
            ui_reset_action_sequence(ui);
            if (apply_display_rotation((lv_display_rotation_t)auto_angle))
            {
                g_config.disp_rot = (lv_display_rotation_t)auto_angle;
            }
            lvgl_port_unlock();
        }
#if CONFIG_EGAUGE_AUTO_ORIENTATION_CANDIDATE
        static uint32_t orientation_logged_at;
        static uint64_t orientation_config_logged = UINT64_MAX;
        if ((uint32_t)(now_ms - orientation_logged_at) >= 2000)
        {
            orientation_logged_at                 = now_ms;
            orientation_diagnostics_t sensor      = orientation_runtime_diagnostics();
            orientation_status_t      orientation = orientation_runtime_snapshot();
            uint64_t config_key = sensor.control_verified | (sensor.have_revision ? 8 : 0) |
                                  (sensor.have_direct_x ? 16 : 0) | ((uint64_t)sensor.ctrl[0] << 8) |
                                  ((uint64_t)sensor.ctrl[1] << 16) | ((uint64_t)sensor.ctrl[2] << 24) |
                                  ((uint64_t)sensor.revision << 32) | ((uint64_t)(uint16_t)sensor.direct_x << 40);
            if (config_key != orientation_config_logged)
            {
                orientation_config_logged = config_key;
                ESP_LOGI(TAG,
                         "orientation config ctrl=%02x/%02x/%02x verified=%u revision=%02x revision_valid=%u "
                         "direct_x=%d direct_valid=%u",
                         sensor.ctrl[0], sensor.ctrl[1], sensor.ctrl[2], sensor.control_verified, sensor.revision,
                         sensor.have_revision, sensor.direct_x, sensor.have_direct_x);
            }
            ESP_LOGI(TAG,
                     "orientation sample=%u xyz_mg=%ld,%ld,%ld proposed=%u applied=%u health=%u phase=%u failures=%lu "
                     "stack=%u main_stack=%u age_ms=%lu max_tick_us=%lu",
                     sensor.have_observation, (long)sensor.x_mg, (long)sensor.y_mg, (long)sensor.z_mg,
                     sensor.proposed_angle, orientation.applied_angle, sensor.healthy, sensor.phase,
                     (unsigned long)sensor.failures, orientation_runtime_stack_margin(),
                     (unsigned)uxTaskGetStackHighWaterMark(NULL),
                     (unsigned long)(pdTICKS_TO_MS(xTaskGetTickCount()) - sensor.observed_at_ms),
                     (unsigned long)sensor.maximum_tick_us);
        }
#endif
        ui_set_action_context(ui, owner_ready && !maintenance);
        uint8_t             selected_before = atomic_load(&g_selected_page);
        uint32_t            activity_before = atomic_load(&g_page_activity_ms);
        page_cycle_reason_t before_reason   = cycling.reason;
        bool cycle_due = page_cycle_step(&cycling, cycle.cycle_seconds, page_count(), owner_ready, maintenance,
                                         ui_cycle_pause(ui), activity_before, now_ms);
        if (before_reason != cycling.reason)
            ESP_LOGI(TAG, "page cycle reason=%u interval_s=%u", cycling.reason, cycle.cycle_seconds);
        smart_page_decision_t choice = {0};
        bool                  smart  = cycle.smart_cycle && g_runtime;
        if (smart)
            cycle_due = smart_select(ui, cycle, selected_before, activity_before, cycling.reason, now_ms, &choice);
        else
        {
            smart_page_suspend(&smart_pages);
            atomic_store(&g_upcoming_page, (atomic_load(&g_selected_page) + 1U) % page_count());
        }
        if (cycle_due && lvgl_port_lock(0))
        {
            bool eligible = atomic_load(&g_selected_page) == selected_before &&
                            atomic_load(&g_page_activity_ms) == activity_before &&
                            ui_cycle_pause(ui) == PAGE_CYCLE_RUNNING && transfer_gate_vehicle_allowed() &&
                            ble_companion_has_owner();
            if (smart && choice.priority)
            {
                double   value;
                uint32_t at, generation;
                eligible = eligible && smart_sample(ui, choice.pid, now_ms, &value, &at, &generation) &&
                           generation == smart_pages.pids[choice.pid].generation;
            }
            if (eligible)
            {
                uint8_t   selected = smart ? choice.page : (atomic_load(&g_selected_page) + 1) % page_count();
                ui_page_t page;
                page_ui(selected, &page);
                ui_set_page(ui, &page);
                ui_set_page_position(ui, selected, page_count());
                atomic_store(&g_selected_page, selected);
                ble_companion_selection_applied(selected);
                atomic_store(&g_page_activity_ms, now_ms);
                if (smart)
                {
                    smart_page_commit(&smart_pages, &choice, now_ms);
                    ESP_LOGI(TAG, "smart_page page=%u reason=%u pid=%u early=%u cursor=%u max_us=%" PRIu32, selected,
                             choice.reason, choice.pid, choice.early, smart_pages.cursor, smart_maximum_us);
                }
            }
            lvgl_port_unlock();
        }
        page_save_observe(&page_save, atomic_load(&g_local_page_generation), now_ms);
        if (page_save_due(&page_save, now_ms) && transfer_gate_current() == 0)
        {
            config_t updated = g_config;
            uint32_t generation;
            /* Snapshot the page and generation together, then release LVGL
             * before any flash access. A tap during the save stays pending. */
            if (!lvgl_port_lock(0))
                continue;
            generation      = atomic_load(&g_local_page_generation);
            updated.cfg_idx = atomic_load(&g_selected_page);
            lvgl_port_unlock();
            if (generation != page_save.generation)
            {
                page_save_observe(&page_save, generation, pdTICKS_TO_MS(xTaskGetTickCount()));
                continue;
            }
            bool saved = updated.cfg_idx == g_config.cfg_idx || config_save(&updated) == ESP_OK;
            if (saved)
                g_config = updated;
            page_save_finished(&page_save, generation, saved, now_ms);
        }
    }
}
