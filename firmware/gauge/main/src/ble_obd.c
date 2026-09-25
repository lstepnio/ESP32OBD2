// ---------------------------------------------------------------------------------------------------------------------
// Includes
// ---------------------------------------------------------------------------------------------------------------------

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "util.h"

#include "esp_log.h"
#include "esp_log_color.h"

#include "freertos/FreeRTOS.h"  // IWYU pragma: keep
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

struct ble_obd_ctx
{
    ble_mgr_ctx_t        *mgr_ctx;
    ble_obd_response_cb_t response_cb;

    struct
    {
        SemaphoreHandle_t mutex;
        SemaphoreHandle_t response_sem;
    } api;

    struct
    {
        char     buf[BLE_OBD_MAX_DATA_LEN];
        uint16_t mode;
        uint16_t pid;
    } tx_data;
    void *usr_ctx;
};

// ---------------------------------------------------------------------------------------------------------------------
// Private Function Definitions
// ---------------------------------------------------------------------------------------------------------------------

static void ble_obd_process_obd_data(ble_obd_ctx_t *obd, char *data, size_t len)
{
    ESP_NULL_CHECK(obd, TAG, "context is NULL");
    ESP_NULL_CHECK(data, TAG, "data is NULL");
    ESP_NULL_CHECK(obd->response_cb, TAG, "response callback is NULL");

    // Parse into hex values
    char   *saveptr;
    char   *tok = strtok_r(data, " \r", &saveptr);
    uint8_t values[BLE_OBD_MAX_DATA_LEN];
    int     count = 0;

    while (tok && count < BLE_OBD_MAX_DATA_LEN)
    {
        char *cur_tok = tok;  // Save current token for logging
        long  val     = strtol(tok, NULL, 16);
        tok           = strtok_r(NULL, " \r", &saveptr);
        if (val < 0 || val > 255)
        {
            ESP_LOGW(TAG, "Invalid hex value: %s", cur_tok);
            continue;
        }
        values[count++] = (uint8_t)val;
    }

    if (count < 2)
    {
        ESP_LOGW(TAG, "Received invalid OBD response: %.*s", (int)len, data);
        return;
    }

    if (values[0] == (obd->tx_data.mode + 0x40) && values[1] == obd->tx_data.pid)
    {
        obd->response_cb(obd->tx_data.pid, values + 2, count - 2, obd->usr_ctx);
    }
    else
    {
        ESP_LOGW(TAG, "Received unexpected OBD response: mode=%02X, pid=%02X, data=%.*s", values[0], values[1],
                 (int)(len - 6), data + 6);
        obd->response_cb(-1, NULL, 0, obd->usr_ctx);
    }
}

static void ble_obd_notify_cb(const uint8_t *data, size_t len, uint16_t attr_handle, void *usr_ctx)
{
    ble_obd_ctx_t *obd = (ble_obd_ctx_t *)usr_ctx;
    ESP_NULL_CHECK(obd, TAG, "context is NULL");
    ESP_NULL_CHECK(obd->response_cb, TAG, "response callback is NULL");
    ESP_NULL_CHECK(obd->api.response_sem, TAG, "response semaphore is NULL");

    ESP_LOGD(TAG, "Received notification on handle 0x%04x (len=%zu): %.*s", attr_handle, len, (int)len,
             (char const *)data);

    if (len == 0)
    {
        ESP_LOGW(TAG, "Received empty notification, ignoring.");
        return;
    }

    char   copy[BLE_OBD_MAX_DATA_LEN];
    size_t copy_len = len < sizeof(copy) - 1 ? len : sizeof(copy) - 1;
    memcpy(copy, data, copy_len);
    copy[copy_len] = '\0';

    if (strncmp(copy, ">\r", sizeof(">\r")) == 0 || strncmp(copy, "\r", sizeof("\r")) == 0)
    {
        ESP_LOGD(TAG, "Received prompt, releasing API caller.");
        if (xSemaphoreGive(obd->api.response_sem) != pdTRUE)
        {
            ESP_LOGW(TAG, "Failed to give response semaphore");
        }
        return;
    }

    if (strncmp(copy, "?\r", sizeof("?\r")) == 0)
    {
        ESP_LOGW(TAG, "Received error response: %.*s", (int)len, copy);
        obd->response_cb(-1, NULL, 0, obd->usr_ctx);
        return;
    }

    if (strcmp(copy, obd->tx_data.buf) == 0 || strcmp(copy, obd->tx_data.buf) == 0)
    {
        ESP_LOGD(TAG, "Received echo of sent command, ignoring.");
        return;
    }

    ESP_LOGD(TAG, "Processing received data: %.*s", (int)len, copy);
    ble_obd_process_obd_data(obd, copy, len);
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
    ESP_LOGW(TAG, "Disconnected from device. Restarting discovery.");
    return true;
}

// ---------------------------------------------------------------------------------------------------------------------
// Public Function Definitions
// ---------------------------------------------------------------------------------------------------------------------

ble_obd_ctx_t *ble_obd_connect(ble_obd_response_cb_t response_cb, void *usr_ctx)
{
    ESP_LOGD(TAG, "Connecting to BLE RX/TX service...");

    ble_obd_ctx_t *obd = malloc(sizeof(ble_obd_ctx_t));
    ESP_NULL_CHECK(obd, TAG, "Failed to allocate memory for OBD context");
    memset(obd, 0, sizeof(ble_obd_ctx_t));

    obd->mgr_ctx = ble_mgr_init(1000U);
    ESP_NULL_CHECK(obd->mgr_ctx, TAG, "Failed to initialize BLE manager");
    ESP_LOGD(TAG, "BLE manager initialized successfully");

    obd->api.mutex        = xSemaphoreCreateMutex();
    obd->api.response_sem = xSemaphoreCreateBinary();
    ESP_NULL_CHECK(obd->api.mutex, TAG, "Failed to create API mutex");
    ESP_NULL_CHECK(obd->api.response_sem, TAG, "Failed to create API response semaphore");

    obd->response_cb = response_cb;
    obd->usr_ctx     = usr_ctx;

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
        vSemaphoreDelete(obd->api.mutex);
        vSemaphoreDelete(obd->api.response_sem);
        free(obd);
        return NULL;
    }
    ESP_LOGD(TAG, "Connected to RX/TX service");

    return obd;
}

int ble_obd_rxtx(ble_obd_ctx_t *obd, uint8_t mode, uint8_t pid, uint32_t timeout_ms)
{
    ESP_NULL_CHECK(obd, TAG, "context is NULL");
    ESP_NULL_CHECK(obd->mgr_ctx, TAG, "manager context is NULL");
    ESP_NULL_CHECK(obd->response_cb, TAG, "response callback is NULL");
    ESP_NULL_CHECK(obd->api.mutex, TAG, "mutex is NULL");
    ESP_NULL_CHECK(obd->api.response_sem, TAG, "response semaphore is NULL");

    // take API mutex
    if (xSemaphoreTake(obd->api.mutex, pdMS_TO_TICKS(timeout_ms)) != pdTRUE)
    {
        ESP_LOGW(TAG, "OBD busy, try again later");
        return -1;
    }

    while (xSemaphoreTake(obd->api.response_sem, 0) == pdTRUE)
    {
        // Drain semaphore before sending (in case of late response)
    }

    obd->tx_data.mode = mode;
    obd->tx_data.pid  = pid;
    snprintf(obd->tx_data.buf, sizeof(obd->tx_data.buf), "%02X%02X\r", mode, pid);
    ESP_LOGD(TAG, "TX: %s", obd->tx_data.buf);
    ble_mgr_status_t status =
        ble_mgr_send(obd->mgr_ctx, obd_tx_char->handle, obd->tx_data.buf, strlen(obd->tx_data.buf));

    if (status != BLE_MGR_E_OK)
    {
        ESP_LOGE(TAG, "Failed to send command: %s", BLE_MGR_STATUS_STR(status));
        xSemaphoreGive(obd->api.mutex);
        return -1;
    }

    // Wait for response or timeout
    int result = 0;
    if (xSemaphoreTake(obd->api.response_sem, pdMS_TO_TICKS(timeout_ms)) != pdTRUE)
    {
        ESP_LOGW(TAG, "OBD response timeout");
        result = -1;
    }

    // release API mutex
    xSemaphoreGive(obd->api.mutex);

    return result;
}

bool ble_obd_is_connected(ble_obd_ctx_t *obd)
{
    ESP_NULL_CHECK(obd, TAG, "context is NULL");
    return ble_mgr_is_connected(obd->mgr_ctx);
}
