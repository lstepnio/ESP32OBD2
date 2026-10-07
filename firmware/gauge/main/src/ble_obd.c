// ---------------------------------------------------------------------------------------------------------------------
// Includes
// ---------------------------------------------------------------------------------------------------------------------

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include "sdkconfig.h"
#include <stdatomic.h>
#include "elm_response.h"


#include "util.h"

#include "esp_log.h"
#include "esp_log_color.h"

#include "freertos/FreeRTOS.h"  // IWYU pragma: keep
#include "freertos/queue.h"
#include "freertos/task.h"
#include "freertos/projdefs.h"
#include "freertos/semphr.h"

#include "nimble/ble.h"

#include "ble_mgr.h"
#include "ble_obd.h"
#include "obd_trace.h"
#include "adapter_status.h"
#include "obd_adapter_profile.h"
#include "ble_util.h"

// ---------------------------------------------------------------------------------------------------------------------
// Forward Declarations
// ---------------------------------------------------------------------------------------------------------------------

static void ble_obd_notify_cb(const uint8_t *data, size_t len, uint16_t attr_handle, void *usr_ctx);

// ---------------------------------------------------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------------------------------------------------

static const char *TAG = "OBD";

// ---------------------------------------------------------------------------------------------------------------------
// Global Variables
// ---------------------------------------------------------------------------------------------------------------------

typedef struct {
    uint32_t generation;
    uint8_t byte;
} rx_byte_t;

struct ble_obd_ctx
{
    ble_mgr_ctx_t *mgr_ctx;
    unsigned source_id;
    uint32_t discovery_ecu;
    char peer_mac[18];
    uint8_t peer_address_type;
    ble_gatt_char_def_t chars[2];
    ble_mgr_svc_def_t svc_def;
    ble_mgr_disc_cfg_t disc_cfg;
    ble_obd_response_cb_t response_cb;
    SemaphoreHandle_t mutex;
    QueueHandle_t rx;
    atomic_uint generation;
    atomic_bool rx_overflow;
    atomic_bool ready;
    uint32_t active_generation;
    bool awaiting_prompt;
    unsigned consecutive_timeouts;
    elm_response_t response;
    void *usr_ctx;
};

/* Keep callback targets alive for the lifetime of the host, including
 * discovery timeouts and background reconnects. Each source has independent
 * service definitions, RX state, transaction lock, and connection generation. */
static ble_obd_ctx_t sources[2];

static ble_mgr_status_t send_command(ble_obd_ctx_t *obd, const char *command)
{
    uint32_t generation = atomic_load(&obd->generation);
    obd_trace_emit(obd->source_id, generation, "tx", command, strlen(command), 0);
    ble_mgr_status_t status = ble_mgr_send(obd->mgr_ctx, obd->chars[0].handle,
                                          command, strlen(command));
    if (status != BLE_MGR_E_OK)
        obd_trace_emit(obd->source_id, generation, "tx_failed", NULL, 0, status);
    return status;
}

static void ble_obd_notify_cb(const uint8_t *data, size_t len, uint16_t attr_handle, void *usr_ctx)
{
    (void)attr_handle;
    ble_obd_ctx_t *obd = usr_ctx;
    if (!obd || !obd->rx) return;
    rx_byte_t item = {.generation = atomic_load(&obd->generation)};
    obd_trace_emit(obd->source_id, item.generation, "rx", data, len, attr_handle);
    for (size_t i = 0; i < len; i++) {
        item.byte = data[i];
        if (xQueueSend(obd->rx, &item, 0) != pdTRUE) {
            atomic_store(&obd->rx_overflow, true);
            obd_trace_emit(obd->source_id, item.generation, "rx_overflow", NULL, 0, 1);
            break;
        }
    }
}

/* Only the polling task owns the assembler. A timeout leaves it waiting for
 * the old command's prompt; no subsequent request may consume that reply. */
static bool wait_for_prompt(ble_obd_ctx_t *obd, TickType_t budget)
{
    TickType_t start = xTaskGetTickCount();
    rx_byte_t item;
    while (xTaskGetTickCount() - start < budget) {
        TickType_t remaining = budget - (xTaskGetTickCount() - start);
        if (atomic_load(&obd->generation) != obd->active_generation) return false;
        if (xQueueReceive(obd->rx, &item, remaining) != pdTRUE) break;
        if (item.generation != obd->active_generation) continue;
        if (elm_response_push(&obd->response, item.byte)) {
            obd->awaiting_prompt = false;
            obd_trace_emit(obd->source_id, obd->active_generation, "prompt", NULL, 0, 0);
            return true;
        }
    }
    obd_trace_emit(obd->source_id, obd->active_generation, "timeout", NULL, 0, 0);
    return false;
}

static bool ble_obd_dev_filter_cb(ble_mgr_ctx_t *mgr_ctx, const ble_addr_t *addr, void *ctx)
{
    ble_obd_ctx_t *obd = (ble_obd_ctx_t *)ctx;
    ESP_NULL_CHECK(obd, TAG, "context is NULL");

    char addr_str[BLE_ADDR_STR_LEN];
    ble_addr_to_str(addr, addr_str);
    ESP_LOGI(TAG, "Found device: %s", addr_str);
    obd_trace_emit(obd->source_id, atomic_load(&obd->generation), "adapter_seen",
                    addr_str, strlen(addr_str), 0);

    if (obd->peer_mac[0]) return addr->type == obd->peer_address_type && strcasecmp(addr_str, obd->peer_mac) == 0;
    if (obd->source_id == 0 && CONFIG_EGAUGE_TCM_ADAPTER_MAC[0] &&
        strcasecmp(addr_str, CONFIG_EGAUGE_TCM_ADAPTER_MAC) == 0) return false;
    return true;
}

static bool ble_obd_dev_disconnected_cb_t(ble_mgr_ctx_t *mgr_ctx, void *usr_ctx)
{
    (void)mgr_ctx;
    ble_obd_ctx_t *obd = usr_ctx;
    atomic_store(&obd->rx_overflow, false);
    atomic_store(&obd->ready, false);
    atomic_fetch_add(&obd->generation, 1);
    adapter_status_event(obd->source_id, atomic_load(&obd->generation), 1, 0);
    obd_trace_emit(obd->source_id, atomic_load(&obd->generation), "disconnected", NULL, 0, 0);
    ESP_LOGW(TAG, "Source %u disconnected", obd->source_id);
    return false;
}

static bool adapter_command(ble_obd_ctx_t *obd, const char *command, uint32_t timeout_ms)
{
    TickType_t budget = pdMS_TO_TICKS(timeout_ms);
    if (!budget) budget = 1;
    if (xSemaphoreTake(obd->mutex, budget) != pdTRUE) return false;
    bool ok = false;
    uint32_t generation = atomic_load(&obd->generation);
    obd->active_generation = generation;
    obd->awaiting_prompt = false;
    rx_byte_t ignored;
    while (xQueueReceive(obd->rx, &ignored, 0) == pdTRUE) {}
    elm_response_reset(&obd->response);
    if (send_command(obd, command) != BLE_MGR_E_OK) goto done;
    obd->awaiting_prompt = true;
    if (!wait_for_prompt(obd, budget) || atomic_load(&obd->rx_overflow) ||
        atomic_load(&obd->generation) != generation) goto done;
    if (strstr(obd->response.text, "?") || strstr(obd->response.text, "ERROR") ||
        strstr(obd->response.text, "UNABLE TO CONNECT") ||
        !strstr(obd->response.text, "OK")) goto done;
    ok = true;
done:
    xSemaphoreGive(obd->mutex);
    return ok;
}

static void transport_observe(const char *event, const void *data, size_t length, int status, void *context)
{
    ble_obd_ctx_t *obd = context;
    obd_trace_emit(obd->source_id, atomic_load(&obd->generation), event, data, length, status);
}

static bool initialize_adapter(ble_obd_ctx_t *obd)
{
    adapter_status_event(obd->source_id, atomic_load(&obd->generation), 3, 0);
    static const char *const commands[] = {"ATE0\r", "ATL0\r", "ATS0\r", "ATH1\r", "ATCAF1\r", "ATSH7DF\r", "ATSP0\r"};
    for (size_t i = 0; i < ARRAY_SIZE(commands); ++i) {
        if (!adapter_command(obd, commands[i], 2000)) {
            adapter_status_event(obd->source_id, atomic_load(&obd->generation), 5, i+1);
            ESP_LOGW(TAG, "Adapter initialization failed at command %u", (unsigned)i);
            return false;
        }
    }
    /* Bounded standard support-map discovery, attributed to the engine ECU.
     * A missing reply remains unknown; it is not evidence of unsupported PIDs. */
    for (unsigned base = 0; base <= 224; base += 32) {
        /* ATSP0 starts protocol discovery at the first vehicle request.
         * Do not let normal poll timeouts interrupt its SEARCHING reply. */
        if (ble_obd_rxtx_ecu(obd, 1, base, obd->discovery_ecu, base == 0 ? 15000 : 1500) != 0) {
            if (obd->awaiting_prompt) {
                adapter_status_event(obd->source_id, atomic_load(&obd->generation), 5, 6);
                return false;
            }
            if (obd->discovery_ecu == 0x7e9 && base == 0) return false;
            break;
        }
        if (obd->response.overflow) break;
        elm_payload_t map;
        if (elm_response_decode_for_ecu(&obd->response, 1, base, obd->discovery_ecu, &map) != ELM_OK || map.length != 4) break;
        adapter_status_support(obd->source_id, atomic_load(&obd->generation), obd->discovery_ecu, base, map.bytes, map.length);
        if (!(map.bytes[3] & 1)) break;
    }
    if (obd->discovery_ecu == 0x7e9 && !adapter_command(obd, "ATSH7E1\r", 2000)) return false;
    atomic_store(&obd->ready, true);
    adapter_status_event(obd->source_id, atomic_load(&obd->generation), 4, 0);
    return true;
}

// ---------------------------------------------------------------------------------------------------------------------
// Public Function Definitions
// ---------------------------------------------------------------------------------------------------------------------

ble_obd_ctx_t *ble_obd_connect_profile(unsigned source_id, const char *peer_mac, uint8_t address_type,
    const char *driver, ble_obd_response_cb_t response_cb, void *usr_ctx)
{
    return ble_obd_connect_profile_ecu(source_id, peer_mac, address_type, driver, 0x7e8, response_cb, usr_ctx);
}

ble_obd_ctx_t *ble_obd_connect_profile_ecu(unsigned source_id, const char *peer_mac, uint8_t address_type,
    const char *driver, uint32_t ecu, ble_obd_response_cb_t response_cb, void *usr_ctx)
{
    ESP_LOGD(TAG, "Connecting to BLE RX/TX service...");

    if ((ecu != 0x7e8 && ecu != 0x7e9) || source_id >= 2 || address_type > 1 || !peer_mac || strlen(peer_mac) >= sizeof(sources[0].peer_mac)) return NULL;
    const obd_adapter_profile_t *profile = obd_adapter_profile_find(driver);
    if (!profile) return NULL;
#if !CONFIG_EGAUGE_OBD_TRACE
    if (profile->wire_id == 2) return NULL;
#endif
    ble_obd_ctx_t *obd = &sources[source_id];
    if (!obd->rx) {
        obd->source_id = source_id;
        strcpy(obd->peer_mac, peer_mac);
        obd->peer_address_type = address_type;
        obd->chars[0] = (ble_gatt_char_def_t){.uuid = profile->tx};
        obd->chars[1] = (ble_gatt_char_def_t){.uuid = profile->rx, .notify_cb = ble_obd_notify_cb};
        obd->svc_def = (ble_mgr_svc_def_t){.service_uuid = profile->service, .chars = obd->chars, .num_chars = 2};
        obd->disc_cfg = (ble_mgr_disc_cfg_t){.svc_def = &obd->svc_def,
            .dev_filter_cb = ble_obd_dev_filter_cb,
            .disconnected_cb = ble_obd_dev_disconnected_cb_t, .observe = transport_observe};
        obd->rx = xQueueCreate(ELM_RESPONSE_CAPACITY, sizeof(rx_byte_t));
        obd->mutex = xSemaphoreCreateMutex();
        if (!obd->rx || !obd->mutex) {
            if (obd->rx) vQueueDelete(obd->rx);
            if (obd->mutex) vSemaphoreDelete(obd->mutex);
            obd->rx = NULL;
            obd->mutex = NULL;
            return NULL;
        }
        obd->response_cb = response_cb;
        obd->usr_ctx = usr_ctx;
        elm_response_reset(&obd->response);
    }
    if (obd->discovery_ecu != ecu || strcmp(obd->peer_mac, peer_mac) || obd->peer_address_type != address_type ||
        strcmp(obd->svc_def.service_uuid, profile->service)) {
        ble_mgr_disconnect(obd->mgr_ctx);
        atomic_store(&obd->ready, false);
        atomic_fetch_add(&obd->generation, 1);
        strcpy(obd->peer_mac, peer_mac);
        obd->peer_address_type = address_type;
        obd->svc_def.service_uuid = profile->service;
        obd->chars[0].uuid = profile->tx;
        obd->chars[1].uuid = profile->rx;
    }
    obd->discovery_ecu = ecu;
    if (!obd->mgr_ctx) obd->mgr_ctx = ble_mgr_init(source_id, 1000U);
    if (!obd->mgr_ctx) return NULL;
    if (ble_mgr_is_connected(obd->mgr_ctx)) {
        if (atomic_load(&obd->ready) || initialize_adapter(obd)) return obd;
        ble_mgr_disconnect(obd->mgr_ctx);
        return NULL;
    }

    ESP_LOGD(TAG, "Connecting source %u to RX/TX service...", source_id);
    atomic_fetch_add(&obd->generation, 1);
    adapter_status_event(obd->source_id, atomic_load(&obd->generation), 2, 0);
    ble_mgr_status_t status = ble_mgr_connect_service(obd->mgr_ctx, &obd->disc_cfg, 12000U, obd);

    if (status != BLE_MGR_E_OK)
    {
        adapter_status_event(obd->source_id, atomic_load(&obd->generation), 5, status);
        obd_trace_emit(obd->source_id, atomic_load(&obd->generation), "connect_failed", NULL, 0, status);
        ESP_LOGE(TAG, "Failed to connect to RX/TX service: %s", BLE_MGR_STATUS_STR(status));
        /* Callbacks may still arrive after the discovery timeout. */
        return NULL;
    }
    char gatt[128];
    int gatt_length = snprintf(gatt, sizeof(gatt), "service=%s tx=%s:%u props=%u rx=%s:%u props=%u cccd=%u",
        obd->svc_def.service_uuid, obd->chars[0].uuid, obd->chars[0].handle,
        obd->chars[0].properties, obd->chars[1].uuid, obd->chars[1].handle,
        obd->chars[1].properties, obd->chars[1].cccd_handle);
    if (gatt_length > 0 && (size_t)gatt_length < sizeof(gatt))
        obd_trace_emit(obd->source_id, atomic_load(&obd->generation), "gatt", gatt, gatt_length, 0);
    if (!initialize_adapter(obd)) {
        ble_mgr_disconnect(obd->mgr_ctx);
        return NULL;
    }
    ESP_LOGD(TAG, "Connected and initialized RX/TX service");
    obd_trace_emit(obd->source_id, atomic_load(&obd->generation), "link_ready", NULL, 0, 0);

    return obd;
}

int ble_obd_rxtx_ecu(ble_obd_ctx_t *obd, uint8_t mode, uint16_t pid, uint32_t ecu, uint32_t timeout_ms)
{
    if (!obd || !timeout_ms || (mode != 1 && mode != 0x22) ||
        (mode == 1 && pid > 255) || (mode == 0x22 && ecu != obd->discovery_ecu)) return -1;
    TickType_t budget = pdMS_TO_TICKS(timeout_ms);
    if (!budget) budget = 1;
    if (xSemaphoreTake(obd->mutex, budget) != pdTRUE) return -1;
    int result = -1;
    uint32_t generation = atomic_load(&obd->generation);
    if (generation != obd->active_generation) {
        obd->active_generation = generation;
        obd->awaiting_prompt = false;
        obd->consecutive_timeouts = 0;
        elm_response_reset(&obd->response);
    }
    if (!ble_mgr_is_connected(obd->mgr_ctx)) goto done;
    if (obd->awaiting_prompt && !wait_for_prompt(obd, budget)) {
        if (++obd->consecutive_timeouts >= 5) ble_mgr_disconnect(obd->mgr_ctx);
        goto done;
    }
    /* After dropped RX bytes the boundary is untrustworthy. Fail closed until
     * a reconnect; do not reinterpret the remaining bytes as a new response. */
    if (atomic_load(&obd->rx_overflow)) {
        ble_mgr_disconnect(obd->mgr_ctx);
        goto done;
    }
    rx_byte_t ignored;
    while (xQueueReceive(obd->rx, &ignored, 0) == pdTRUE) {}
    elm_response_reset(&obd->response);
    char command[8];
    if (mode == 0x22) snprintf(command, sizeof(command), "22%04X\r", pid);
    else snprintf(command, sizeof(command), "%02X%02X\r", mode, pid);
    if (send_command(obd, command) != BLE_MGR_E_OK) goto done;
    obd->awaiting_prompt = true;
    if (!wait_for_prompt(obd, budget)) {
        obd->consecutive_timeouts = 1;
        goto done;
    }
    obd->consecutive_timeouts = 0;
    elm_payload_t payload;
    elm_result_t decoded = mode == 0x22
        ? elm_response_decode_identifier(&obd->response, pid, ecu, &payload)
        : elm_response_decode_for_ecu(&obd->response, mode, pid, ecu, &payload);
    obd_trace_emit(obd->source_id, obd->active_generation, "decoded", payload.bytes, payload.length, decoded);
    if (decoded == ELM_OK && !atomic_load(&obd->rx_overflow) &&
        atomic_load(&obd->generation) == obd->active_generation) {
        if (obd->response_cb && (mode == 0x22 || pid % 32 != 0)) obd->response_cb(pid, payload.bytes, payload.length, obd->usr_ctx);
        result = 0;
    } else {
        ESP_LOGW(TAG, "Rejected PID %02X response (status=%d)", pid, decoded);
    }
done:
    xSemaphoreGive(obd->mutex);
    return result;
}

bool ble_obd_is_connected(ble_obd_ctx_t *obd)
{
    ESP_NULL_CHECK(obd, TAG, "context is NULL");
    return ble_mgr_is_connected(obd->mgr_ctx) && atomic_load(&obd->ready);
}

int ble_obd_read_service(ble_obd_ctx_t *obd, uint8_t mode, uint32_t timeout_ms,
                         uint8_t *data, size_t *length)
{
    return ble_obd_read_service_ecu(obd, mode, 0x7e8, timeout_ms, data, length);
}

int ble_obd_read_service_ecu(ble_obd_ctx_t *obd, uint8_t mode, uint32_t ecu,
                            uint32_t timeout_ms, uint8_t *data, size_t *length)
{
    if (!obd || !data || !length || *length == 0 || !timeout_ms ||
        (mode != 3 && mode != 7 && mode != 10)) return -1;
    TickType_t budget = pdMS_TO_TICKS(timeout_ms);
    if (!budget) budget = 1;
    if (xSemaphoreTake(obd->mutex, budget) != pdTRUE) return -1;
    int result = -1;
    uint32_t generation = atomic_load(&obd->generation);
    if (generation != obd->active_generation) {
        obd->active_generation = generation;
        obd->awaiting_prompt = false;
        obd->consecutive_timeouts = 0;
        elm_response_reset(&obd->response);
    }
    if (!ble_mgr_is_connected(obd->mgr_ctx)) goto done;
    if (obd->awaiting_prompt && !wait_for_prompt(obd, budget)) {
        if (++obd->consecutive_timeouts >= 5) ble_mgr_disconnect(obd->mgr_ctx);
        goto done;
    }
    if (atomic_load(&obd->rx_overflow)) {
        ble_mgr_disconnect(obd->mgr_ctx);
        goto done;
    }
    rx_byte_t ignored;
    while (xQueueReceive(obd->rx, &ignored, 0) == pdTRUE) {}
    elm_response_reset(&obd->response);
    char command[4];
    snprintf(command, sizeof(command), "%02X\r", mode);
    if (send_command(obd, command) != BLE_MGR_E_OK) goto done;
    obd->awaiting_prompt = true;
    if (!wait_for_prompt(obd, budget)) {
        obd->consecutive_timeouts = 1;
        goto done;
    }
    obd->consecutive_timeouts = 0;
    elm_payload_t payload;
    elm_result_t decoded = elm_response_decode_dtcs_for_ecu(&obd->response, mode, ecu, &payload);
    obd_trace_emit(obd->source_id, obd->active_generation, "decoded", payload.bytes, payload.length, decoded);
    if (decoded == ELM_OK && payload.length <= *length &&
        !atomic_load(&obd->rx_overflow) &&
        atomic_load(&obd->generation) == obd->active_generation) {
        memcpy(data, payload.bytes, payload.length);
        *length = payload.length;
        result = 0;
    } else ESP_LOGW(TAG, "Rejected service %02X response (status=%d)", mode, decoded);
done:
    xSemaphoreGive(obd->mutex);
    return result;
}

ble_obd_ctx_t *ble_obd_connect(unsigned source_id, const char *peer_mac,
                               ble_obd_response_cb_t response_cb, void *usr_ctx)
{
    return ble_obd_connect_bound(source_id, peer_mac, 0, response_cb, usr_ctx);
}

int ble_obd_rxtx(ble_obd_ctx_t *obd, uint8_t mode, uint8_t pid, uint32_t timeout_ms)
{
    return ble_obd_rxtx_ecu(obd, mode, pid, ELM_ECU_ANY, timeout_ms);
}

void ble_obd_disconnect(ble_obd_ctx_t *obd)
{
    if (!obd) return;
    atomic_store(&obd->ready, false);
    ble_mgr_disconnect(obd->mgr_ctx);
}

ble_obd_ctx_t *ble_obd_connect_bound(unsigned source_id, const char *peer_mac, uint8_t address_type,
                                     ble_obd_response_cb_t response_cb, void *usr_ctx)
{
    return ble_obd_connect_profile(source_id, peer_mac, address_type, "elm-18f0-v1", response_cb, usr_ctx);
}
