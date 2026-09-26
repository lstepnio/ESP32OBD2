#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#include "cJSON.h"
#include "esp_heap_caps.h"
#include "esp_log.h"
#include "config_document.h"
#include "config_store.h"

#define ARRAY_COUNT(a) (sizeof(a) / sizeof((a)[0]))

static const char *TAG = "config_document";

static void *psram_malloc(size_t size)
{
    return heap_caps_malloc(size, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
}

void config_document_init(void)
{
    cJSON_Hooks hooks = {.malloc_fn = psram_malloc, .free_fn = heap_caps_free};
    cJSON_InitHooks(&hooks);
}

static bool fields(const cJSON *object, const char *const *allowed, size_t count)
{
    if (!cJSON_IsObject(object)) return false;
    for (const cJSON *field = object->child; field != NULL; field = field->next) {
        if (field->string == NULL) return false;
        bool found = false;
        for (size_t i = 0; i < count; ++i)
            if (strcmp(field->string, allowed[i]) == 0) { found = true; break; }
        if (!found) return false;
        for (const cJSON *prior = object->child; prior != field; prior = prior->next)
            if (prior->string != NULL && strcmp(prior->string, field->string) == 0)
                return false;
    }
    return true;
}

static bool integer(const cJSON *item, int64_t low, int64_t high)
{
    return cJSON_IsNumber(item) && isfinite(item->valuedouble) &&
           item->valuedouble >= (double)low && item->valuedouble <= (double)high &&
           item->valuedouble == (double)(int64_t)item->valuedouble;
}

static bool number(const cJSON *item, double low, double high)
{
    return cJSON_IsNumber(item) && isfinite(item->valuedouble) &&
           item->valuedouble >= low && item->valuedouble <= high;
}

static bool string(const cJSON *item, size_t low, size_t high)
{
    if (!cJSON_IsString(item) || item->valuestring == NULL) return false;
    size_t length = strnlen(item->valuestring, high + 1);
    return length >= low && length <= high;
}

static bool identifier(const cJSON *item, size_t high)
{
    if (!string(item, 1, high)) return false;
    const char *s = item->valuestring;
    if (*s < 'a' || *s > 'z') return false;
    for (++s; *s != 0; ++s)
        if (!((*s >= 'a' && *s <= 'z') || (*s >= '0' && *s <= '9') ||
              *s == '.' || *s == '_' || *s == '-')) return false;
    return true;
}

static bool one_of(const cJSON *item, const char *const *choices, size_t count)
{
    if (!cJSON_IsString(item)) return false;
    for (size_t i = 0; i < count; ++i)
        if (strcmp(item->valuestring, choices[i]) == 0) return true;
    return false;
}

static bool hex(const cJSON *item, size_t low, size_t high)
{
    if (!string(item, low, high) || (strlen(item->valuestring) & 1U)) return false;
    for (const char *p = item->valuestring; *p != 0; ++p)
        if (!( (*p >= '0' && *p <= '9') || (*p >= 'A' && *p <= 'F') )) return false;
    return true;
}

static bool can11_id(const cJSON *item)
{
    if (!string(item, 3, 3)) return false;
    const char *id = item->valuestring;
    if (id[0] < '0' || id[0] > '7') return false;
    for (size_t i = 1; i < 3; ++i)
        if (!((id[i] >= '0' && id[i] <= '9') ||
              (id[i] >= 'A' && id[i] <= 'F'))) return false;
    return true;
}

static bool array_size(const cJSON *item, int low, int high)
{
    if (!cJSON_IsArray(item)) return false;
    int count = cJSON_GetArraySize(item);
    return count >= low && count <= high;
}

static bool unique_ids(const cJSON *array)
{
    for (const cJSON *a = array->child; a != NULL; a = a->next) {
        const cJSON *id = cJSON_GetObjectItemCaseSensitive(a, "id");
        if (!cJSON_IsString(id)) return false;
        for (const cJSON *b = a->next; b != NULL; b = b->next) {
            const cJSON *other = cJSON_GetObjectItemCaseSensitive(b, "id");
            if (cJSON_IsString(other) && strcmp(id->valuestring, other->valuestring) == 0)
                return false;
        }
    }
    return true;
}

static bool contains_id(const cJSON *array, const char *id)
{
    for (const cJSON *item = array->child; item != NULL; item = item->next) {
        const cJSON *candidate = cJSON_GetObjectItemCaseSensitive(item, "id");
        if (cJSON_IsString(candidate) && strcmp(candidate->valuestring, id) == 0)
            return true;
    }
    return false;
}

static const cJSON *find_id(const cJSON *array, const char *id)
{
    for (const cJSON *item = array->child; item != NULL; item = item->next) {
        const cJSON *candidate = cJSON_GetObjectItemCaseSensitive(item, "id");
        if (cJSON_IsString(candidate) && strcmp(candidate->valuestring, id) == 0)
            return item;
    }
    return NULL;
}

static uint8_t hex_byte(const char *pair)
{
    uint8_t value = 0;
    for (size_t i = 0; i < 2; ++i) {
        char digit = pair[i];
        value = (uint8_t)((value << 4) | (digit <= '9' ? digit - '0' : digit - 'A' + 10));
    }
    return value;
}

static bool vector_matches(const cJSON *payload, const cJSON *expected,
                           const cJSON *decoder, const cJSON *range)
{
    const cJSON *offset = cJSON_GetObjectItemCaseSensitive(decoder, "byteOffset");
    const cJSON *length = cJSON_GetObjectItemCaseSensitive(decoder, "byteLength");
    const cJSON *endian = cJSON_GetObjectItemCaseSensitive(decoder, "endian");
    const cJSON *sign = cJSON_GetObjectItemCaseSensitive(decoder, "signed");
    const cJSON *numerator = cJSON_GetObjectItemCaseSensitive(decoder, "numerator");
    const cJSON *denominator = cJSON_GetObjectItemCaseSensitive(decoder, "denominator");
    const cJSON *adjust = cJSON_GetObjectItemCaseSensitive(decoder, "offset");
    const cJSON *minimum = cJSON_GetObjectItemCaseSensitive(range, "min");
    const cJSON *maximum = cJSON_GetObjectItemCaseSensitive(range, "max");
    uint32_t raw = 0;
    for (int i = 0; i < length->valueint; ++i) {
        int index = offset->valueint + i;
        uint8_t byte = hex_byte(payload->valuestring + 2 * index);
        if (strcmp(endian->valuestring, "big") == 0) raw = (raw << 8) | byte;
        else raw |= (uint32_t)byte << (8 * i);
    }
    int64_t sample = raw;
    if (cJSON_IsTrue(sign) && (raw & (1U << (8 * length->valueint - 1))) != 0)
        sample -= (int64_t)1 << (8 * length->valueint);
    double decoded = (double)sample * numerator->valuedouble / denominator->valuedouble +
                     adjust->valuedouble;
    return isfinite(decoded) && decoded >= minimum->valuedouble &&
           decoded <= maximum->valuedouble &&
           fabs(decoded - expected->valuedouble) <= 0.0001;
}

static bool source_valid(const cJSON *item)
{
    static const char *const keys[] = {"id", "label", "role"};
    static const char *const roles[] = {"ecm", "tcm", "other"};
    return fields(item, keys, ARRAY_COUNT(keys)) &&
           identifier(cJSON_GetObjectItemCaseSensitive(item, "id"), 32) &&
           string(cJSON_GetObjectItemCaseSensitive(item, "label"), 1, 32) &&
           one_of(cJSON_GetObjectItemCaseSensitive(item, "role"), roles, ARRAY_COUNT(roles));
}

static bool definition_valid(const cJSON *item, const cJSON *sources)
{
    static const char *const keys[] = {"schemaVersion", "id", "revision", "name", "unit",
        "request", "response", "decoder", "range", "pollIntervalMs", "staleAfterMs",
        "provenance", "vectors", "sourceId"};
    static const char *const request_keys[] = {"service", "identifier", "route", "requestId", "responseId"};
    static const char *const response_keys[] = {"prefix", "minPayloadBytes"};
    static const char *const decoder_keys[] = {"byteOffset", "byteLength", "endian", "signed",
        "numerator", "denominator", "offset"};
    static const char *const range_keys[] = {"min", "max"};
    static const char *const provenance_keys[] = {"source", "license", "vehicleScope", "evidence"};
    static const char *const vector_keys[] = {"payloadHex", "expected"};
    static const char *const services[] = {"01", "22"};
    static const char *const routes[] = {"functional", "can11_physical"};
    static const char *const endians[] = {"big", "little"};
    static const char *const evidence[] = {"synthetic", "documented", "vehicle_observed"};
    if (!fields(item, keys, ARRAY_COUNT(keys)) ||
        !integer(cJSON_GetObjectItemCaseSensitive(item, "schemaVersion"), 1, 1) ||
        !identifier(cJSON_GetObjectItemCaseSensitive(item, "id"), 64) ||
        !integer(cJSON_GetObjectItemCaseSensitive(item, "revision"), 1, 65535) ||
        !string(cJSON_GetObjectItemCaseSensitive(item, "name"), 1, 64) ||
        !string(cJSON_GetObjectItemCaseSensitive(item, "unit"), 1, 16)) return false;

    const cJSON *source_id = cJSON_GetObjectItemCaseSensitive(item, "sourceId");
    if (!identifier(source_id, 32) || !contains_id(sources, source_id->valuestring)) return false;

    const cJSON *request = cJSON_GetObjectItemCaseSensitive(item, "request");
    const cJSON *service = cJSON_GetObjectItemCaseSensitive(request, "service");
    const cJSON *pid = cJSON_GetObjectItemCaseSensitive(request, "identifier");
    const cJSON *route = cJSON_GetObjectItemCaseSensitive(request, "route");
    const cJSON *request_id = cJSON_GetObjectItemCaseSensitive(request, "requestId");
    if (!fields(request, request_keys, ARRAY_COUNT(request_keys)) ||
        !one_of(service, services, ARRAY_COUNT(services)) || !hex(pid, 2, 4) ||
        !one_of(route, routes, ARRAY_COUNT(routes)) ||
        !can11_id(cJSON_GetObjectItemCaseSensitive(request, "responseId")) ||
        (request_id && !can11_id(request_id)) ||
        (strcmp(service->valuestring, "01") == 0 && strlen(pid->valuestring) != 2) ||
        (strcmp(service->valuestring, "22") == 0 && strlen(pid->valuestring) != 4) ||
        (strcmp(route->valuestring, "can11_physical") == 0 && request_id == NULL)) return false;

    const cJSON *response = cJSON_GetObjectItemCaseSensitive(item, "response");
    const cJSON *prefix = cJSON_GetObjectItemCaseSensitive(response, "prefix");
    const cJSON *min_payload = cJSON_GetObjectItemCaseSensitive(response, "minPayloadBytes");
    if (!fields(response, response_keys, ARRAY_COUNT(response_keys)) ||
        !hex(prefix, 4, 6) || !integer(min_payload, 1, 4096) ||
        strlen(prefix->valuestring) != strlen(pid->valuestring) + 2 ||
        prefix->valuestring[0] != (service->valuestring[0] == '0' ? '4' : '6') ||
        prefix->valuestring[1] != (service->valuestring[0] == '0' ? '1' : '2') ||
        strcmp(prefix->valuestring + 2, pid->valuestring) != 0) return false;

    const cJSON *decoder = cJSON_GetObjectItemCaseSensitive(item, "decoder");
    const cJSON *byte_offset = cJSON_GetObjectItemCaseSensitive(decoder, "byteOffset");
    const cJSON *byte_length = cJSON_GetObjectItemCaseSensitive(decoder, "byteLength");
    if (!fields(decoder, decoder_keys, ARRAY_COUNT(decoder_keys)) ||
        !integer(byte_offset, 0, 4095) || !integer(byte_length, 1, 4) ||
        !one_of(cJSON_GetObjectItemCaseSensitive(decoder, "endian"), endians, ARRAY_COUNT(endians)) ||
        !cJSON_IsBool(cJSON_GetObjectItemCaseSensitive(decoder, "signed")) ||
        !integer(cJSON_GetObjectItemCaseSensitive(decoder, "numerator"), -1000000, 1000000) ||
        !integer(cJSON_GetObjectItemCaseSensitive(decoder, "denominator"), 1, 1000000) ||
        !number(cJSON_GetObjectItemCaseSensitive(decoder, "offset"), -1e9, 1e9) ||
        byte_offset->valueint + byte_length->valueint > min_payload->valueint) return false;

    const cJSON *range = cJSON_GetObjectItemCaseSensitive(item, "range");
    const cJSON *minimum = cJSON_GetObjectItemCaseSensitive(range, "min");
    const cJSON *maximum = cJSON_GetObjectItemCaseSensitive(range, "max");
    const cJSON *poll = cJSON_GetObjectItemCaseSensitive(item, "pollIntervalMs");
    const cJSON *stale = cJSON_GetObjectItemCaseSensitive(item, "staleAfterMs");
    if (!fields(range, range_keys, ARRAY_COUNT(range_keys)) ||
        !number(minimum, -1e12, 1e12) || !number(maximum, -1e12, 1e12) ||
        minimum->valuedouble >= maximum->valuedouble ||
        !integer(poll, 100, 60000) || !integer(stale, 200, 180000) ||
        stale->valueint < poll->valueint) return false;

    const cJSON *provenance = cJSON_GetObjectItemCaseSensitive(item, "provenance");
    if (!fields(provenance, provenance_keys, ARRAY_COUNT(provenance_keys)) ||
        !string(cJSON_GetObjectItemCaseSensitive(provenance, "source"), 1, 500) ||
        !string(cJSON_GetObjectItemCaseSensitive(provenance, "license"), 1, 80) ||
        !string(cJSON_GetObjectItemCaseSensitive(provenance, "vehicleScope"), 1, 300) ||
        !one_of(cJSON_GetObjectItemCaseSensitive(provenance, "evidence"),
                evidence, ARRAY_COUNT(evidence))) return false;

    const cJSON *vectors = cJSON_GetObjectItemCaseSensitive(item, "vectors");
    if (!array_size(vectors, 1, 16)) return false;
    for (const cJSON *vector = vectors->child; vector != NULL; vector = vector->next) {
        const cJSON *payload = cJSON_GetObjectItemCaseSensitive(vector, "payloadHex");
        const cJSON *expected = cJSON_GetObjectItemCaseSensitive(vector, "expected");
        if (!fields(vector, vector_keys, ARRAY_COUNT(vector_keys)) ||
            !hex(payload, 2, 8192) ||
            strlen(payload->valuestring) / 2 < (size_t)(byte_offset->valueint + byte_length->valueint) ||
            !number(expected, -1e12, 1e12) ||
            !vector_matches(payload, expected, decoder, range))
            return false;
    }
    return true;
}

static bool page_valid(const cJSON *item, const cJSON *definitions)
{
    static const char *const keys[] = {"id", "name", "renderer", "pidIds"};
    static const char *const renderers[] = {"numeric", "arc", "bar", "trend", "dual"};
    const cJSON *renderer = cJSON_GetObjectItemCaseSensitive(item, "renderer");
    const cJSON *ids = cJSON_GetObjectItemCaseSensitive(item, "pidIds");
    if (!fields(item, keys, ARRAY_COUNT(keys)) ||
        !identifier(cJSON_GetObjectItemCaseSensitive(item, "id"), 64) ||
        !string(cJSON_GetObjectItemCaseSensitive(item, "name"), 1, 32) ||
        !one_of(renderer, renderers, ARRAY_COUNT(renderers)) ||
        !array_size(ids, 1, 2) ||
        (strcmp(renderer->valuestring, "dual") == 0) != (cJSON_GetArraySize(ids) == 2)) return false;
    for (const cJSON *id = ids->child; id != NULL; id = id->next) {
        if (!identifier(id, 64) || !contains_id(definitions, id->valuestring)) return false;
        for (const cJSON *other = id->next; other != NULL; other = other->next)
            if (cJSON_IsString(other) && strcmp(id->valuestring, other->valuestring) == 0)
                return false;
    }
    return true;
}

static bool alert_valid(const cJSON *item, const cJSON *definitions)
{
    static const char *const keys[] = {"id", "pidId", "direction", "warning", "critical",
        "hysteresis", "triggerDwellMs", "clearDwellMs", "snoozeMs", "priority"};
    static const char *const directions[] = {"above", "below"};
    const cJSON *pid = cJSON_GetObjectItemCaseSensitive(item, "pidId");
    const cJSON *direction = cJSON_GetObjectItemCaseSensitive(item, "direction");
    const cJSON *warning = cJSON_GetObjectItemCaseSensitive(item, "warning");
    const cJSON *critical = cJSON_GetObjectItemCaseSensitive(item, "critical");
    const cJSON *hysteresis = cJSON_GetObjectItemCaseSensitive(item, "hysteresis");
    if (!fields(item, keys, ARRAY_COUNT(keys)) ||
        !identifier(cJSON_GetObjectItemCaseSensitive(item, "id"), 64) ||
        !identifier(pid, 64) || !contains_id(definitions, pid->valuestring) ||
        !one_of(direction, directions, ARRAY_COUNT(directions)) ||
        (warning == NULL && critical == NULL) ||
        (warning != NULL && !number(warning, -1e12, 1e12)) ||
        (critical != NULL && !number(critical, -1e12, 1e12)) ||
        !number(hysteresis, 0, 1e9) ||
        !integer(cJSON_GetObjectItemCaseSensitive(item, "triggerDwellMs"), 0, 60000) ||
        !integer(cJSON_GetObjectItemCaseSensitive(item, "clearDwellMs"), 0, 60000) ||
        !integer(cJSON_GetObjectItemCaseSensitive(item, "snoozeMs"), 0, 600000) ||
        !integer(cJSON_GetObjectItemCaseSensitive(item, "priority"), 0, 10)) return false;
    const cJSON *definition = find_id(definitions, pid->valuestring);
    if (definition == NULL) return false;
    const cJSON *range = cJSON_GetObjectItemCaseSensitive(definition, "range");
    const cJSON *minimum = cJSON_GetObjectItemCaseSensitive(range, "min");
    const cJSON *maximum = cJSON_GetObjectItemCaseSensitive(range, "max");
    if (!number(minimum, -1e12, 1e12) ||
        !number(maximum, -1e12, 1e12) ||
        (warning && (warning->valuedouble < minimum->valuedouble ||
                     warning->valuedouble > maximum->valuedouble)) ||
        (critical && (critical->valuedouble < minimum->valuedouble ||
                      critical->valuedouble > maximum->valuedouble)) ||
        hysteresis->valuedouble >= maximum->valuedouble - minimum->valuedouble) return false;
    if (strcmp(direction->valuestring, "above") == 0) {
        if ((warning && warning->valuedouble - hysteresis->valuedouble < minimum->valuedouble) ||
            (critical && critical->valuedouble - hysteresis->valuedouble < minimum->valuedouble))
            return false;
    } else if ((warning && warning->valuedouble + hysteresis->valuedouble > maximum->valuedouble) ||
               (critical && critical->valuedouble + hysteresis->valuedouble > maximum->valuedouble)) {
        return false;
    }
    if (warning != NULL && critical != NULL) {
        if (strcmp(direction->valuestring, "above") == 0)
            return warning->valuedouble + hysteresis->valuedouble < critical->valuedouble;
        return warning->valuedouble - hysteresis->valuedouble > critical->valuedouble;
    }
    return true;
}

static bool document_valid(const cJSON *root, const config_document_context_t *context)
{
    static const char *const keys[] = {"schemaVersion", "baseRevision", "vehicleProfileId",
        "units", "rotation", "brightness", "reducedMotion", "definitions", "pages",
        "alerts", "sources"};
    static const char *const units[] = {"metric", "imperial"};
    const cJSON *sources = cJSON_GetObjectItemCaseSensitive(root, "sources");
    const cJSON *definitions = cJSON_GetObjectItemCaseSensitive(root, "definitions");
    const cJSON *pages = cJSON_GetObjectItemCaseSensitive(root, "pages");
    const cJSON *alerts = cJSON_GetObjectItemCaseSensitive(root, "alerts");
    const cJSON *rotation = cJSON_GetObjectItemCaseSensitive(root, "rotation");
    if (!fields(root, keys, ARRAY_COUNT(keys)) ||
        !integer(cJSON_GetObjectItemCaseSensitive(root, "schemaVersion"), 1, 1) ||
        !integer(cJSON_GetObjectItemCaseSensitive(root, "baseRevision"),
                 context->base_revision, context->base_revision) ||
        !identifier(cJSON_GetObjectItemCaseSensitive(root, "vehicleProfileId"), 64) ||
        !one_of(cJSON_GetObjectItemCaseSensitive(root, "units"), units, ARRAY_COUNT(units)) ||
        !integer(rotation, 0, 270) || rotation->valueint % 90 != 0 ||
        !integer(cJSON_GetObjectItemCaseSensitive(root, "brightness"), 5, 100) ||
        !cJSON_IsBool(cJSON_GetObjectItemCaseSensitive(root, "reducedMotion")) ||
        !array_size(sources, 1, context->max_adapter_links) ||
        !array_size(definitions, 1, 32) || !array_size(pages, 1, 8) ||
        !array_size(alerts, 0, 32) ||
        !unique_ids(sources) || !unique_ids(definitions) ||
        !unique_ids(pages) || !unique_ids(alerts)) return false;
    for (const cJSON *item = sources->child; item != NULL; item = item->next)
        if (!source_valid(item)) return false;
    for (const cJSON *item = definitions->child; item != NULL; item = item->next)
        if (!definition_valid(item, sources)) return false;
    for (const cJSON *item = pages->child; item != NULL; item = item->next)
        if (!page_valid(item, definitions)) return false;
    for (const cJSON *item = alerts->child; item != NULL; item = item->next)
        if (!alert_valid(item, definitions)) return false;
    return true;
}

esp_err_t config_document_validate(const esp_partition_t *partition,
                                   uint32_t document_offset, uint32_t length,
                                   void *context)
{
    const config_document_context_t *limits = context;
    if (partition == NULL || limits == NULL || limits->max_adapter_links < 1 ||
        limits->max_adapter_links > 2 || length == 0 || length > EGAUGE_CONFIG_MAX_BYTES)
        return ESP_ERR_INVALID_ARG;
    char *buffer = heap_caps_malloc((size_t)length + 1, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (buffer == NULL) return ESP_ERR_NO_MEM;
    esp_err_t err = esp_partition_read(partition, document_offset, buffer, length);
    if (err != ESP_OK) { heap_caps_free(buffer); return err; }
    if (memchr(buffer, 0, length) != NULL) { heap_caps_free(buffer); return ESP_ERR_INVALID_ARG; }
    buffer[length] = 0;
    const char *end = NULL;
    cJSON *root = cJSON_ParseWithLengthOpts(buffer, length + 1, &end, 1);
    bool valid = root != NULL && end == buffer + length && document_valid(root, limits);
    cJSON_Delete(root);
    heap_caps_free(buffer);
    if (!valid) ESP_LOGW(TAG, "Rejected invalid configuration document");
    return valid ? ESP_OK : ESP_ERR_INVALID_ARG;
}
