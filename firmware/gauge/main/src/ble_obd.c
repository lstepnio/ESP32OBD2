// ---------------------------------------------------------------------------------------------------------------------
// Includes
// ---------------------------------------------------------------------------------------------------------------------

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
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
#include "ble_util.h"

// ---------------------------------------------------------------------------------------------------------------------
// Forward Declarations
// ---------------------------------------------------------------------------------------------------------------------

static void ble_obd_notify_cb(const uint8_t *data, size_t len, uint16_t attr_handle, void *usr_ctx);

// ---------------------------------------------------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------------------------------------------------

static const char *TAG = "OBD";

static ble_gatt_char_def_t obd_svc_chars1[] __attribute__((unused)) = {
    /* TX */ {.uuid = "0x2af1", .notify_cb = NULL},
    /* RX */ {.uuid = "0x2af0", .notify_cb = ble_obd_notify_cb},
};

static const ble_mgr_svc_def_t obd_svc_def1 __attribute__((unused)) = {
    .service_uuid = "0x18f0",
    .chars        = obd_svc_chars1,
    .num_chars    = ARRAY_SIZE(obd_svc_chars1),
};

static const ble_mgr_svc_def_t *const   obd_svc_def = &obd_svc_def1;
static const ble_gatt_char_def_t *const obd_tx_char = &obd_svc_chars1[0];

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
    ble_obd_response_cb_t response_cb;
    SemaphoreHandle_t mutex;
    QueueHandle_t rx;
    atomic_uint generation;
    atomic_bool rx_overflow;
    uint32_t active_generation;
    bool awaiting_prompt;
    unsigned consecutive_timeouts;
    elm_response_t response;
    void *usr_ctx;
};

/* The baseline manager is a singleton. Keep its callback target alive for the
 * lifetime of the host, including discovery timeouts and background reconnects. */
static ble_obd_ctx_t singleton;

static void ble_obd_notify_cb(const uint8_t *data, size_t len, uint16_t attr_handle, void *usr_ctx)
{
    (void)attr_handle;
    ble_obd_ctx_t *obd = usr_ctx;
    if (!obd || !obd->rx) return;
    rx_byte_t item = {.generation = atomic_load(&obd->generation)};
    for (size_t i = 0; i < len; i++) {
        item.byte = data[i];
        if (xQueueSend(obd->rx, &item, 0) != pdTRUE) {
            atomic_store(&obd->rx_overflow, true);
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
            return true;
        }
    }
    return false;
}

static bool ble_obd_dev_filter_cb(ble_mgr_ctx_t *mgr_ctx, const ble_addr_t *addr, void *ctx)
{
    ble_obd_ctx_t *obd = (ble_obd_ctx_t *)ctx;
    ESP_NULL_CHECK(obd, TAG, "context is NULL");

    char addr_str[BLE_ADDR_STR_LEN];
    ble_addr_to_str(addr, addr_str);
    ESP_LOGI(TAG, "Found device: %s", addr_str);

    return true;  // connect to this device
}

static bool ble_obd_dev_disconnected_cb_t(ble_mgr_ctx_t *mgr_ctx, void *usr_ctx)
{
    (void)mgr_ctx;
    ble_obd_ctx_t *obd = usr_ctx;
    atomic_store(&obd->rx_overflow, false);
    atomic_fetch_add(&obd->generation, 1);
    ESP_LOGW(TAG, "Disconnected from device. Restarting discovery.");
    return true;
}

// ---------------------------------------------------------------------------------------------------------------------
// Public Function Definitions
// ---------------------------------------------------------------------------------------------------------------------

ble_obd_ctx_t *ble_obd_connect(ble_obd_response_cb_t response_cb, void *usr_ctx)
{
    ESP_LOGD(TAG, "Connecting to BLE RX/TX service...");

    ble_obd_ctx_t *obd = &singleton;
    if (!obd->rx) {
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
    if (!obd->mgr_ctx) obd->mgr_ctx = ble_mgr_init(1000U);
    if (!obd->mgr_ctx) return NULL;
    if (ble_mgr_is_connected(obd->mgr_ctx)) return obd;

    static const ble_mgr_disc_cfg_t disc_cfg = {
        .svc_def         = obd_svc_def,
        .dev_filter_cb   = ble_obd_dev_filter_cb,
        .disconnected_cb = ble_obd_dev_disconnected_cb_t,
    };

    ESP_LOGD(TAG, "Connecting to RX/TX service...");
    ble_mgr_status_t status = ble_mgr_connect_service(obd->mgr_ctx, &disc_cfg, 10000U, obd);

    if (status != BLE_MGR_E_OK)
    {
        ESP_LOGE(TAG, "Failed to connect to RX/TX service: %s", BLE_MGR_STATUS_STR(status));
        /* Callbacks may still arrive after the discovery timeout. */
        return NULL;
    }
    ESP_LOGD(TAG, "Connected to RX/TX service");

    return obd;
}

int ble_obd_rxtx(ble_obd_ctx_t *obd, uint8_t mode, uint8_t pid, uint32_t timeout_ms)
{
    if (!obd || mode != 1 || !timeout_ms) return -1;
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
    char command[6];
    snprintf(command, sizeof(command), "%02X%02X\r", mode, pid);
    obd->awaiting_prompt = true;
    if (ble_mgr_send(obd->mgr_ctx, obd_tx_char->handle, command, strlen(command)) != BLE_MGR_E_OK) goto done;
    if (!wait_for_prompt(obd, budget)) {
        obd->consecutive_timeouts = 1;
        goto done;
    }
    obd->consecutive_timeouts = 0;
    elm_payload_t payload;
    elm_result_t decoded = elm_response_decode(&obd->response, mode, pid, &payload);
    if (decoded == ELM_OK && !atomic_load(&obd->rx_overflow) &&
        atomic_load(&obd->generation) == obd->active_generation) {
        obd->response_cb(pid, payload.bytes, payload.length, obd->usr_ctx);
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
    return ble_mgr_is_connected(obd->mgr_ctx);
}
