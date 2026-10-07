#include <stdlib.h>
#include <stdatomic.h>
#include <string.h>
#include <strings.h>
#include "cJSON.h"
#include "esp_heap_caps.h"
#include "config_runtime.h"
#include "config_store.h"
#include "config_trial.h"
#include "config_document.h"
#include "json_guard.h"
#include "sdkconfig.h"

typedef struct {
    bool valid;
    bool previous;
    uint32_t stored_revision;
    config_store_record_t record;
} running_identity_t;
static running_identity_t running_identity;
static atomic_bool running_trial;

static void put_u32(uint8_t *p, uint32_t value)
{
    for (unsigned i = 0; i < 4; ++i) p[i] = value >> (8 * i);
}

static const cJSON *field(const cJSON *item, const char *name)
{
    return cJSON_GetObjectItemCaseSensitive(item, name);
}



static bool copy_text(char *dest, size_t capacity, const cJSON *item)
{
    if (!cJSON_IsString(item) || strlen(item->valuestring) >= capacity) return false;
    strcpy(dest, item->valuestring);
    return true;
}

static int pid_index(const config_runtime_t *runtime, const char *id)
{
    for (unsigned i = 0; i < runtime->pid_count; ++i)
        if (strcmp(runtime->pids[i].id, id) == 0) return i;
    return -1;
}

static bool renderer_value(const char *name, runtime_renderer_t *out)
{
    static const struct {
        const char *name;
        runtime_renderer_t value;
    } values[] = {
        {"numeric", RUNTIME_RENDERER_NUMERIC},
        {"arc", RUNTIME_RENDERER_ARC},
        {"bar", RUNTIME_RENDERER_BAR},
        {"trend", RUNTIME_RENDERER_TREND},
        {"dual", RUNTIME_RENDERER_DUAL},
    };
    for (size_t i = 0; i < sizeof(values) / sizeof(values[0]); ++i) {
        if (strcmp(name, values[i].name) == 0) {
            *out = values[i].value;
            return true;
        }
    }
    return false;
}

static esp_err_t compile_bytes(char *bytes, uint32_t length, config_runtime_t *out)
{
    bytes[length] = 0;
    if (!json_guard_shape(bytes, length, 16)) return ESP_ERR_INVALID_ARG;
    cJSON *root = cJSON_ParseWithLength(bytes, length);
    if (!root) return ESP_ERR_INVALID_ARG;
    memset(out, 0, sizeof(*out));
    const cJSON *sources = field(root, "sources");
    const cJSON *definitions = field(root, "definitions");
    const cJSON *pages = field(root, "pages");
    const cJSON *alerts = field(root, "alerts");
    const cJSON *rotation = field(root, "rotation");
    esp_err_t err = ESP_ERR_NOT_SUPPORTED;
    if (cJSON_GetArraySize(sources) < 1 || cJSON_GetArraySize(sources) > 2 ||

        cJSON_GetArraySize(definitions) > EGAUGE_RUNTIME_PIDS ||
        cJSON_GetArraySize(pages) > EGAUGE_RUNTIME_PAGES ||
        cJSON_GetArraySize(alerts) > EGAUGE_RUNTIME_ALERTS) goto done;
    if (strcmp(field(root, "units")->valuestring, "metric") != 0 ||
        cJSON_IsTrue(field(root, "reducedMotion"))) goto done;
    out->source_count = cJSON_GetArraySize(sources);
    out->legacy_auto_discovery = field(root, "schemaVersion")->valueint == 1;
    if (!copy_text(out->vehicle_id, sizeof(out->vehicle_id), field(root, "vehicleProfileId"))) goto done;
    for (unsigned i = 0; i < out->source_count; ++i) {
        const cJSON *source = cJSON_GetArrayItem(sources, i);
        runtime_source_t *target = &out->sources[i];
        const char *role = field(source, "role")->valuestring;
        target->transmission = !strcmp(role, "tcm");
        if (strcmp(role, "ecm") && !target->transmission) goto done;
        if (!copy_text(target->id, sizeof(target->id), field(source, "id"))) goto done;
        const cJSON *binding = field(source, "adapter");
        if (binding) {
            target->simulated = !strcmp(field(binding, "driver")->valuestring, "elm-bench-v1");
#if !CONFIG_EGAUGE_OBD_TRACE
            if (target->simulated) goto done;
#endif
            if (!copy_text(target->adapter_id, sizeof(target->adapter_id), field(binding, "id")) ||
                !copy_text(target->address, sizeof(target->address), field(binding, "address"))) goto done;
            target->address_type = !strcmp(field(binding, "addressType")->valuestring, "random");
        }
        if (target->transmission && field(root, "schemaVersion")->valueint != 2) goto done;
        if (i && (!strcmp(out->sources[0].adapter_id, target->adapter_id) ||
            !strcasecmp(out->sources[0].address, target->address))) goto done;
        if (out->source_count == 2 && (!binding || out->legacy_auto_discovery ||
            (i && out->sources[0].transmission == target->transmission))) goto done;
    }
    out->rotation = rotation->valueint / 90;
    out->brightness = field(root, "brightness")->valueint;
    for (const cJSON *item = definitions->child; item; item = item->next) {
        const cJSON *request = field(item, "request");
        const cJSON *decoder = field(item, "decoder");
        const cJSON *range = field(item, "range");
        const cJSON *response = field(item, "response");
        const char *identifier = field(request, "identifier")->valuestring;
        unsigned source_index = 0;
        while (source_index < out->source_count && strcmp(field(item, "sourceId")->valuestring,
               out->sources[source_index].id)) ++source_index;
        if (source_index == out->source_count || !field(request, "responseId") ||
            field(decoder, "byteOffset")->valueint != 0) goto done;
        if (out->sources[source_index].transmission) {
            /* Captured TCM temperature/current gear. No arbitrary enhanced reads. */
            bool gear = !strcmp(identifier, "5503");
            if (strcmp(field(request, "service")->valuestring, "22") ||
                (!gear && strcmp(identifier, "04FE")) || strcmp(field(request, "route")->valuestring, "can11_physical") ||
                !field(request, "requestId") || strcmp(field(request, "requestId")->valuestring, "7E1") ||
                strcmp(field(request, "responseId")->valuestring, "7E9") ||
                strcmp(field(response, "prefix")->valuestring, gear ? "625503" : "6204FE") ||
                field(response, "minPayloadBytes")->valueint != (gear ? 1 : 3) ||
                field(decoder, "byteLength")->valueint != 1 ||
                field(decoder, "numerator")->valueint != 1 || field(decoder, "denominator")->valueint != 1 ||
                field(decoder, "offset")->valuedouble != (gear ? 0 : -40) || cJSON_IsTrue(field(decoder, "signed")) ||
                strcmp(field(item, "unit")->valuestring, gear ? "gear" : "degC") ||
                (gear && (field(range, "min")->valuedouble != 0 || field(range, "max")->valuedouble != 13))) goto done;
        } else if (strcmp(field(request, "service")->valuestring, "01") ||
                   strcmp(field(request, "route")->valuestring, "functional") || strlen(identifier) != 2 ||
                   !strcmp(identifier, "01") ||
                   field(response, "minPayloadBytes")->valueint != field(decoder, "byteLength")->valueint) goto done;
        runtime_pid_t *pid = &out->pids[out->pid_count++];
        if (!copy_text(pid->id, sizeof(pid->id), field(item, "id")) ||
            !copy_text(pid->name, sizeof(pid->name), field(item, "name")) ||
            !copy_text(pid->unit, sizeof(pid->unit), field(item, "unit"))) goto done;
        pid->source_index = source_index;
        pid->obd.pid = strtoul(identifier, NULL, 16);
        pid->responder = strtoul(field(request, "responseId")->valuestring, NULL, 16);
        for (unsigned previous = 0; previous + 1 < out->pid_count; ++previous)
            if (out->pids[previous].obd.pid == pid->obd.pid) goto done;
        pid->obd.len = field(response, "minPayloadBytes")->valueint;
        pid->obd.name = pid->name;
        pid->obd.unit = pid->unit;
        pid->obd.decoder = (pid_numeric_decoder_t){
            .byte_length = field(decoder, "byteLength")->valueint,
            .little_endian = strcmp(field(decoder, "endian")->valuestring, "little") == 0,
            .signed_value = cJSON_IsTrue(field(decoder, "signed")),
            .numerator = field(decoder, "numerator")->valueint,
            .denominator = field(decoder, "denominator")->valueint,
            .offset = field(decoder, "offset")->valuedouble,
            .minimum = field(range, "min")->valuedouble,
            .maximum = field(range, "max")->valuedouble,
        };
        pid->poll_ms = field(item, "pollIntervalMs")->valueint;
        pid->stale_ms = field(item, "staleAfterMs")->valueint;
    }
    for (unsigned source=0; source<out->source_count; ++source) {
        bool used = false;
        for (unsigned i=0; i<out->pid_count; ++i)
            if (out->pids[i].source_index == source) used = true;
        if (!used) goto done;
    }
    for (const cJSON *item = pages->child; item; item = item->next) {
        const cJSON *ids = field(item, "pidIds");
        const char *renderer = field(item, "renderer")->valuestring;
        int count = cJSON_GetArraySize(ids);
        runtime_page_t *page = &out->pages[out->page_count];
        if (!copy_text(page->id, sizeof(page->id), field(item, "id")) ||
            !copy_text(page->name, sizeof(page->name), field(item, "name")) ||
            !renderer_value(renderer, &page->renderer) || count < 1 || count > 2 ||
            (page->renderer == RUNTIME_RENDERER_DUAL) != (count == 2)) goto done;
        page->pid_count = count;
        for (int i = 0; i < count; ++i) {
            int index = pid_index(out, cJSON_GetArrayItem(ids, i)->valuestring);
            if (index < 0) goto done;
            if (out->pids[index].obd.pid == 0x5503 && page->renderer != RUNTIME_RENDERER_NUMERIC &&
                page->renderer != RUNTIME_RENDERER_DUAL) goto done;
            if (i && out->pids[page->pid_indices[0]].source_index != out->pids[index].source_index) goto done;
            page->pid_indices[i] = index;
        }
        out->page_count++;
    }
    for (const cJSON *item = alerts->child; item; item = item->next) {
        int index = pid_index(out, field(item, "pidId")->valuestring);
        if (index < 0 || out->sources[out->pids[index].source_index].transmission) goto done;
        runtime_alert_t *alert = &out->alerts[out->alert_count++];
        if (!copy_text(alert->id, sizeof(alert->id), field(item, "id"))) goto done;
        alert->pid_index = index;
        alert->above = strcmp(field(item, "direction")->valuestring, "above") == 0;
        const cJSON *warning = field(item, "warning");
        const cJSON *critical = field(item, "critical");
        alert->has_warning = warning != NULL;
        alert->has_critical = critical != NULL;
        alert->warning = warning ? warning->valuedouble : 0;
        alert->critical = critical ? critical->valuedouble : 0;
        alert->hysteresis = field(item, "hysteresis")->valuedouble;
        alert->trigger_dwell_ms = field(item, "triggerDwellMs")->valueint;
        alert->clear_dwell_ms = field(item, "clearDwellMs")->valueint;
        alert->priority = field(item, "priority")->valueint;
        if (field(item, "snoozeMs")->valueint != 0) goto done;
    }
    const cJSON *actions = field(root, "actions");
    if (actions && (!cJSON_IsArray(actions) || cJSON_GetArraySize(actions) > 1 ||
        field(root, "schemaVersion")->valueint != 2)) goto done;
    if (actions && cJSON_GetArraySize(actions)) {
        const cJSON *action = cJSON_GetArrayItem(actions, 0);
        const cJSON *kind = field(action, "type"), *gesture = field(action, "gesture"),
            *page_id = field(action, "pageId"), *count = field(action, "count"), *window = field(action, "windowMs");
        if (!cJSON_IsString(kind) || strcmp(kind->valuestring, "jumpPage") ||
            !cJSON_IsString(gesture) || strcmp(gesture->valuestring, "up") || !cJSON_IsString(page_id) ||
            !cJSON_IsNumber(count) || count->valuedouble != count->valueint || count->valueint < 2 || count->valueint > 5 ||
            !cJSON_IsNumber(window) || window->valuedouble != window->valueint || window->valueint < 2000 || window->valueint > 10000 || window->valueint % 1000 != 0) goto done;
        unsigned target = 0;
        while (target < out->page_count && strcmp(out->pages[target].id, field(action, "pageId")->valuestring)) ++target;
        if (target == out->page_count) goto done;
        out->page_action = (page_action_config_t){.enabled = true, .target_page = target,
            .count = field(action, "count")->valueint, .window_ms = field(action, "windowMs")->valueint};
    }
    err = ESP_OK;

done:
    cJSON_Delete(root);
    return err;
}

static esp_err_t compile_partition(const esp_partition_t *partition, uint32_t offset,
                                   uint32_t length, config_runtime_t *out)
{
    char *bytes = heap_caps_malloc((size_t)length + 1, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (!bytes) return ESP_ERR_NO_MEM;
    esp_err_t err = esp_partition_read(partition, offset, bytes, length);
    if (err == ESP_OK) err = compile_bytes(bytes, length, out);
    heap_caps_free(bytes);
    return err;
}

esp_err_t config_runtime_validate(const esp_partition_t *partition,
                                  uint32_t document_offset, uint32_t length,
                                  void *context)
{
    (void)context;
    config_runtime_t *candidate = heap_caps_malloc(sizeof(config_runtime_t),
                                                   MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (!candidate) return ESP_ERR_NO_MEM;
    esp_err_t err = compile_partition(partition, document_offset, length, candidate);
    heap_caps_free(candidate);
    return err;
}

static esp_err_t compile_record(const config_store_record_t *record, config_runtime_t *out)
{
    char *bytes = heap_caps_malloc((size_t)record->length + 1,
                                   MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (!bytes) return ESP_ERR_NO_MEM;
    esp_err_t err = config_store_read_record(record, 0, bytes, record->length);
    if (err == ESP_OK) err = compile_bytes(bytes, record->length, out);
    heap_caps_free(bytes);
    return err;
}

esp_err_t config_runtime_load(config_runtime_t *out, config_store_record_t *running,
                              bool *used_previous)
{
    if (!out || !running || !used_previous) return ESP_ERR_INVALID_ARG;
    *used_previous = false;
    config_store_record_t active;
    config_store_record_t previous;
    esp_err_t err = config_store_active(&active);
    if (err != ESP_OK) return err;
    running_identity = (running_identity_t){.stored_revision = active.revision};
    atomic_store(&running_trial, false);
    config_trial_decision_t decision;
    err = config_trial_decide(active.revision, &decision);
    if (err != ESP_OK) decision = CONFIG_TRIAL_USE_PREVIOUS;
    if (decision == CONFIG_TRIAL_USE_PREVIOUS) goto previous;
    err = compile_record(&active, out);
    if (err == ESP_OK) {
        *running = active;
        running_identity = (running_identity_t){
            .valid = true,
            .stored_revision = active.revision,
            .record = active,
        };
        atomic_store(&running_trial, decision == CONFIG_TRIAL_USE_ACTIVE_TRIAL);
        return ESP_OK;
    }
    config_trial_reject(active.revision);
previous:;
    esp_err_t previous_err = config_store_previous(&previous);
    if (previous_err != ESP_OK) return err == ESP_OK ? previous_err : err;
    previous_err = compile_record(&previous, out);
    if (previous_err != ESP_OK) return err == ESP_OK ? previous_err : err;
    previous_err = config_store_select(&previous);
    if (previous_err != ESP_OK) return previous_err;
    *running = previous;
    *used_previous = true;
    running_identity = (running_identity_t){
        .valid = true, .previous = true, .stored_revision = active.revision,
        .record = previous,
    };
    return ESP_OK;
}

size_t config_runtime_status(uint8_t out[CONFIG_RUNTIME_STATUS_SIZE])
{
    if (!out) return 0;
    memset(out, 0, CONFIG_RUNTIME_STATUS_SIZE);
    out[0] = 8;
    if (running_identity.valid) out[1] |= 1;
    if (running_identity.previous) out[1] |= 2;
    if (atomic_load(&running_trial)) out[1] |= 4;
    put_u32(out + 4, running_identity.record.revision);
    put_u32(out + 8, running_identity.stored_revision);
    if (running_identity.valid) memcpy(out + 12, running_identity.record.sha256, 32);
    return CONFIG_RUNTIME_STATUS_SIZE;
}

void config_runtime_mark_confirmed(void)
{
    atomic_store(&running_trial, false);
}
