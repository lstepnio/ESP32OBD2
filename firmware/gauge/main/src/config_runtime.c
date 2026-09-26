#include <stdlib.h>
#include <string.h>
#include "cJSON.h"
#include "esp_heap_caps.h"
#include "config_runtime.h"
#include "config_store.h"

static const cJSON *field(const cJSON *item, const char *name)
{
    return cJSON_GetObjectItemCaseSensitive(item, name);
}

static uint8_t hex_byte(const char *s)
{
    uint8_t result = 0;
    for (unsigned i = 0; i < 2; ++i) {
        char c = s[i];
        result = (uint8_t)((result << 4) | (c <= '9' ? c - '0' : c - 'A' + 10));
    }
    return result;
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

static esp_err_t compile(const esp_partition_t *partition, uint32_t offset,
                         uint32_t length, config_runtime_t *out)
{
    char *bytes = heap_caps_malloc((size_t)length + 1, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (!bytes) return ESP_ERR_NO_MEM;
    esp_err_t err = esp_partition_read(partition, offset, bytes, length);
    if (err != ESP_OK) { heap_caps_free(bytes); return err; }
    bytes[length] = 0;
    cJSON *root = cJSON_ParseWithLength(bytes, length);
    heap_caps_free(bytes);
    if (!root) return ESP_ERR_INVALID_ARG;
    memset(out, 0, sizeof(*out));
    const cJSON *sources = field(root, "sources");
    const cJSON *definitions = field(root, "definitions");
    const cJSON *pages = field(root, "pages");
    const cJSON *alerts = field(root, "alerts");
    const cJSON *rotation = field(root, "rotation");
    err = ESP_ERR_NOT_SUPPORTED;
    if (cJSON_GetArraySize(sources) != 1 ||
        strcmp(field(cJSON_GetArrayItem(sources, 0), "role")->valuestring, "ecm") != 0 ||
        cJSON_GetArraySize(definitions) > EGAUGE_RUNTIME_PIDS ||
        cJSON_GetArraySize(pages) > 5 ||
        cJSON_GetArraySize(alerts) > EGAUGE_RUNTIME_ALERTS) goto done;
    if (strcmp(field(root, "units")->valuestring, "metric") != 0 ||
        field(root, "brightness")->valueint != 80 ||
        cJSON_IsTrue(field(root, "reducedMotion"))) goto done;
    const char *source_id = field(cJSON_GetArrayItem(sources, 0), "id")->valuestring;
    out->rotation = rotation->valueint / 90;
    for (const cJSON *item = definitions->child; item; item = item->next) {
        const cJSON *request = field(item, "request");
        const cJSON *decoder = field(item, "decoder");
        const cJSON *range = field(item, "range");
        const cJSON *response = field(item, "response");
        const char *identifier = field(request, "identifier")->valuestring;
        if (strcmp(field(item, "sourceId")->valuestring, source_id) != 0 ||
            strcmp(field(request, "service")->valuestring, "01") != 0 ||
            strcmp(field(request, "route")->valuestring, "functional") != 0 ||
            strcmp(field(request, "responseId")->valuestring, "7E8") != 0 ||
            strlen(identifier) != 2 ||
            field(decoder, "byteOffset")->valueint != 0 ||
            field(response, "minPayloadBytes")->valueint != field(decoder, "byteLength")->valueint)
            goto done;
        runtime_pid_t *pid = &out->pids[out->pid_count++];
        if (!copy_text(pid->id, sizeof(pid->id), field(item, "id")) ||
            !copy_text(pid->name, sizeof(pid->name), field(item, "name")) ||
            !copy_text(pid->unit, sizeof(pid->unit), field(item, "unit"))) goto done;
        pid->obd.pid = hex_byte(identifier);
        for (unsigned previous = 0; previous + 1 < out->pid_count; ++previous)
            if (out->pids[previous].obd.pid == pid->obd.pid) goto done;
        pid->obd.len = field(decoder, "byteLength")->valueint;
        pid->obd.name = pid->name;
        pid->obd.unit = pid->unit;
        pid->obd.decoder = (pid_numeric_decoder_t){
            .byte_length = pid->obd.len,
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
    for (const cJSON *item = pages->child; item; item = item->next) {
        const cJSON *ids = field(item, "pidIds");
        const char *renderer = field(item, "renderer")->valuestring;
        if (cJSON_GetArraySize(ids) != 1 || strcmp(renderer, "numeric") != 0) goto done;
        int index = pid_index(out, cJSON_GetArrayItem(ids, 0)->valuestring);
        if (index < 0) goto done;
        out->page_pids[out->page_count++] = index;
    }
    for (const cJSON *item = alerts->child; item; item = item->next) {
        int index = pid_index(out, field(item, "pidId")->valuestring);
        if (index < 0) goto done;
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
    err = ESP_OK;
done:
    cJSON_Delete(root);
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
    esp_err_t err = compile(partition, document_offset, length, candidate);
    heap_caps_free(candidate);
    return err;
}

esp_err_t config_runtime_load(config_runtime_t *out)
{
    if (!out) return ESP_ERR_INVALID_ARG;
    config_store_record_t active;
    esp_err_t err = config_store_active(&active);
    if (err != ESP_OK) return err;
    const esp_partition_t *partition = esp_partition_find_first(ESP_PARTITION_TYPE_DATA,
        (esp_partition_subtype_t)0x40, "config_a");
    const esp_partition_t *other = esp_partition_find_first(ESP_PARTITION_TYPE_DATA,
        (esp_partition_subtype_t)0x41, "config_b");
    if (!partition || !other) return ESP_ERR_NOT_FOUND;
    /* The store keeps active slot private. Read its header revision to select
     * the exact committed generation that config_store_active reported. */
    uint32_t revision_a = 0, revision_b = 0;
    uint8_t hash_a[32] = {0}, hash_b[32] = {0};
    esp_partition_read(partition, 8, &revision_a, sizeof(revision_a));
    esp_partition_read(other, 8, &revision_b, sizeof(revision_b));
    esp_partition_read(partition, 16, hash_a, sizeof(hash_a));
    esp_partition_read(other, 16, hash_b, sizeof(hash_b));
    bool a = revision_a == active.revision && memcmp(hash_a, active.sha256, 32) == 0;
    bool b = revision_b == active.revision && memcmp(hash_b, active.sha256, 32) == 0;
    if (!a && !b) return ESP_ERR_INVALID_STATE;
    return compile(a ? partition : other, 0x1000,
                   active.length, out);
}
