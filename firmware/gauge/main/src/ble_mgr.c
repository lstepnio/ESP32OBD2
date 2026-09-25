// ---------------------------------------------------------------------------------------------------------------------
// Includes
// ---------------------------------------------------------------------------------------------------------------------

#include <inttypes.h>
#include <stdbool.h>
#include <stdatomic.h>
#include <stdio.h>
#include <string.h>

#include "util.h"

#include "esp_log.h"
#include "esp_log_color.h"

#include "freertos/FreeRTOS.h"  // IWYU pragma: keep
#include "freertos/projdefs.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include "portmacro.h"

#include "host/ble_gap.h"
#include "host/ble_gatt.h"
#include "host/ble_hs.h"
#include "host/ble_hs_adv.h"
#include "host/ble_uuid.h"
#include "nimble/ble.h"
#include "os/os_mbuf.h"

#include "ble_init.h"
#include "ble_mgr.h"
#include "ble_util.h"

// ---------------------------------------------------------------------------------------------------------------------
// Private Types
// ---------------------------------------------------------------------------------------------------------------------

struct ble_mgr_ctx
{
    uint16_t conn_handle;
    atomic_bool is_connected;
    atomic_bool connecting;
    bool scanning;

    ble_mgr_disc_cfg_t const *disc_cfg;

    void *usr_ctx;

    struct
    {
        bool svc_disc_completed;
        bool chr_disc_completed;
        bool chr_disc_started;
    } svc_disc_ctx;

    struct
    {
        QueueHandle_t     result_que;
        SemaphoreHandle_t lock_mtx;
    } api;

};  // ble_mgr_ctx_t

// ---------------------------------------------------------------------------------------------------------------------
// Forward Declarations
// ---------------------------------------------------------------------------------------------------------------------

static void ble_mgr_gap_stack_reset_cb(int reason);
static void ble_mgr_gap_stack_sync_cb(void);

static void ble_mgr_gap_notification_cb(ble_mgr_ctx_t  *mgr_ctx,
                                        struct os_mbuf *om,
                                        uint16_t        attr_handle,
                                        uint16_t        conn_handle,
                                        bool            indication);

static void ble_mgr_gap_connected_cb(ble_mgr_ctx_t *mgr_ctx, uint16_t conn_handle, int status);
static void ble_mgr_connect_complete(ble_mgr_ctx_t *mgr_ctx, ble_mgr_status_t status);

// ---------------------------------------------------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------------------------------------------------

#define BLE_DISCOVERY_TIMEOUT_MS (5000U)

static const char *TAG = "BLE_MGR";

static ble_init_config_t ble_init_cfg = {
    .reset_cb = ble_mgr_gap_stack_reset_cb,
    .sync_cb  = ble_mgr_gap_stack_sync_cb,
};

static const struct ble_gap_disc_params disc_params = {
    .passive           = 1,
    .itvl              = 0x0010,
    .window            = 0x0010,
    .filter_duplicates = 1,
};

static const struct ble_gap_conn_params conn_params = {
    .scan_itvl           = 0x0010,
    .scan_window         = 0x0010,
    .itvl_min            = 0x0010,
    .itvl_max            = 0x0020,
    .latency             = 0,
    .supervision_timeout = 0x0100,
    .min_ce_len          = 0x0010,
    .max_ce_len          = 0x0300,
};

static const uint8_t cccd_notify_enable_cfg[] = {0x01, 0x00};

// ---------------------------------------------------------------------------------------------------------------------
// Global Variables
// ---------------------------------------------------------------------------------------------------------------------

/* One host, two stable central connection contexts. Initialize before tasks start. */
static ble_mgr_ctx_t BLE_MGR_CTX[2] = {
    {.conn_handle = BLE_HS_CONN_HANDLE_NONE},
    {.conn_handle = BLE_HS_CONN_HANDLE_NONE},
};
static SemaphoreHandle_t scan_lock;
static SemaphoreHandle_t stack_ready;
static bool stack_started;

// ---------------------------------------------------------------------------------------------------------------------
// Macros
// ---------------------------------------------------------------------------------------------------------------------

#define API_LOCK_OR_RETURN(mgr_ctx, retval)                                                                            \
    do                                                                                                                 \
    {                                                                                                                  \
        ESP_NULL_CHECK(mgr_ctx, TAG, "context is NULL");                                                               \
        ESP_NULL_CHECK(mgr_ctx->api.lock_mtx, TAG, "lock mutex is NULL");                                              \
        if (xSemaphoreTake((mgr_ctx->api.lock_mtx), 0) != pdTRUE)                                                      \
        {                                                                                                              \
            ESP_LOGE((TAG), "Failed to take API lock mutex");                                                          \
            return (retval);                                                                                           \
        }                                                                                                              \
    } while (0)

static inline __attribute__((always_inline)) ble_mgr_status_t API_UNLOCK(ble_mgr_ctx_t const *mgr_ctx,
                                                                         ble_mgr_status_t     retval)
{
    ESP_NULL_CHECK(mgr_ctx, TAG, "context is NULL");
    ESP_NULL_CHECK(mgr_ctx->api.lock_mtx, TAG, "lock mutex is NULL");
    if (xSemaphoreGive(mgr_ctx->api.lock_mtx) != pdTRUE)
    {
        ESP_LOGE(TAG, "Failed to give API lock mutex");
        return BLE_MGR_E_API_LOCK_ERROR;
    }
    return retval;
}

static inline __attribute__((always_inline)) bool API_QUEUE_WAIT(ble_mgr_ctx_t const *mgr_ctx,
                                                                 ble_mgr_status_t    *status,
                                                                 uint32_t             timeout_ms)
{
    ESP_NULL_CHECK(mgr_ctx, TAG, "context is NULL");
    ESP_NULL_CHECK(mgr_ctx->api.result_que, TAG, "queue is NULL");
    ble_mgr_status_t  dummy;
    ble_mgr_status_t *ptr  = status ? status : &dummy;
    BaseType_t        _res = xQueueReceive(mgr_ctx->api.result_que, ptr, pdMS_TO_TICKS(timeout_ms));
    if (_res != pdTRUE)
    {
        ESP_LOGD(TAG, "API result queue receive timeout (%" PRIu32 " ms)", timeout_ms);
        return false;
    }
    return true;
}
static inline __attribute__((always_inline)) void API_QUEUE_SEND(ble_mgr_ctx_t const *mgr_ctx, ble_mgr_status_t status)
{
    ESP_NULL_CHECK(mgr_ctx, TAG, "context is NULL");
    ble_mgr_status_t value = status;
    if (xQueueSend(mgr_ctx->api.result_que, &value, 0) != pdTRUE)
    {
        ESP_LOGE(TAG, "Failed to send to API result queue ");
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Private Function Definitions
// ---------------------------------------------------------------------------------------------------------------------

static void ble_mgr_gap_stack_reset_cb(int reason)
{
    ESP_LOGW(TAG, "NimBLE stack reset, reset reason: %d", reason);
    for (size_t i = 0; i < ARRAY_SIZE(BLE_MGR_CTX); i++) {
        ble_mgr_ctx_t *ctx = &BLE_MGR_CTX[i];
        if (atomic_exchange(&ctx->connecting, false))
            API_QUEUE_SEND(ctx, BLE_MGR_E_NOT_CONNECTED);
        bool was_connected = atomic_exchange(&ctx->is_connected, false);
        ctx->conn_handle = BLE_HS_CONN_HANDLE_NONE;
        ctx->scanning = false;
        if (was_connected && ctx->disc_cfg && ctx->disc_cfg->disconnected_cb)
            ctx->disc_cfg->disconnected_cb(ctx, ctx->usr_ctx);
    }
}

static void ble_mgr_gap_stack_sync_cb(void)
{
    ESP_LOGD(TAG, "NimBLE stack synced");
    if (stack_ready) xSemaphoreGive(stack_ready);
}

static bool ble_mgr_adv_contains_service(const struct ble_hs_adv_fields *adv_fields, const char *target_uuid)
{
    char uuid_str[BLE_UUID_STR_LEN];

    // 16-bit UUIDs
    for (int i = 0; i < adv_fields->num_uuids16; i++)
    {
        ble_uuid_to_str(&adv_fields->uuids16[i].u, uuid_str);
        ESP_LOGD(TAG, "  service UUID[16]: %s", uuid_str);
        if (strncmp(uuid_str, target_uuid, BLE_UUID_STR_LEN) == 0)
        {
            return true;
        }
    }
    // 32-bit UUIDs
    for (int i = 0; i < adv_fields->num_uuids32; i++)
    {
        ble_uuid_to_str(&adv_fields->uuids32[i].u, uuid_str);
        ESP_LOGD(TAG, "  service UUID[32]: %s", uuid_str);
        if (strncmp(uuid_str, target_uuid, BLE_UUID_STR_LEN) == 0)
        {
            return true;
        }
    }
    // 128-bit UUIDs
    for (int i = 0; i < adv_fields->num_uuids128; i++)
    {
        ble_uuid_to_str(&adv_fields->uuids128[i].u, uuid_str);
        ESP_LOGD(TAG, "  service UUID[128]: %s", uuid_str);
        if (strncmp(uuid_str, target_uuid, BLE_UUID_STR_LEN) == 0)
        {
            return true;
        }
    }
    return false;
}

static int ble_mgr_gap_event_cb(struct ble_gap_event *event, void *arg)
{
    char                     addr_str[BLE_ADDR_STR_LEN];
    struct ble_hs_adv_fields adv_fields;

    ble_mgr_ctx_t *mgr_ctx = (ble_mgr_ctx_t *)arg;
    ESP_NULL_CHECK(mgr_ctx, TAG, "scan context is NULL");
    ble_mgr_disc_cfg_t const *disc_cfg = mgr_ctx->disc_cfg;
    ESP_NULL_CHECK(disc_cfg, TAG, "discovery config is NULL");

    switch (event->type)
    {
    case BLE_GAP_EVENT_DISC:
        if (!mgr_ctx->scanning || !atomic_load(&mgr_ctx->connecting)) break;
        ble_addr_to_str(&event->disc.addr, addr_str);

        ESP_LOGD(TAG, "Discovered device: addr=%s", addr_str);
        ESP_LOGD(TAG, "  RSSI: %d", event->disc.rssi);

        int rc = ble_hs_adv_parse_fields(&adv_fields, event->disc.data, event->disc.length_data);

        if (rc != 0)
        {
            ESP_LOGE(TAG, "Failed to parse advertisement data: %d", rc);
            break;
        }

        if (ble_mgr_adv_contains_service(&adv_fields, disc_cfg->svc_def->service_uuid))
        {
            bool connect = disc_cfg->dev_filter_cb(mgr_ctx, &event->disc.addr, mgr_ctx->usr_ctx);
            if (connect)
            {
                ESP_LOGD(TAG, "Connecting to %s...", addr_str);
                mgr_ctx->scanning = false;
                ble_gap_disc_cancel();
                int connect_rc = ble_gap_connect(BLE_OWN_ADDR_PUBLIC, &event->disc.addr, 8000,
                                                 &conn_params, ble_mgr_gap_event_cb, mgr_ctx);
                if (connect_rc != 0) ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_NOT_CONNECTED);
            }
        }
        break;

    case BLE_GAP_EVENT_DISC_COMPLETE:
        if (mgr_ctx->scanning) {
            ESP_LOGD(TAG, "Device discovery window complete. Continuing...");
            ble_gap_disc(0, BLE_DISCOVERY_TIMEOUT_MS, &disc_params, ble_mgr_gap_event_cb, mgr_ctx);
        }
        break;

    case BLE_GAP_EVENT_CONNECT:
        if (event->connect.status == 0)
        {
            ESP_LOGD(TAG, "Connected to device. Handle: 0x%04x", event->connect.conn_handle);
        }
        else
        {
            ESP_LOGW(TAG, "Connection attempt failed: %d", event->connect.status);
        }
        ble_mgr_gap_connected_cb(mgr_ctx, event->connect.conn_handle, event->connect.status);
        break;

    case BLE_GAP_EVENT_DISCONNECT:
        if (event->disconnect.conn.conn_handle != mgr_ctx->conn_handle) break;
        mgr_ctx->conn_handle  = BLE_HS_CONN_HANDLE_NONE;
        mgr_ctx->is_connected = false;
        ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_NOT_CONNECTED);
        if (mgr_ctx->disc_cfg && mgr_ctx->disc_cfg->disconnected_cb)
            mgr_ctx->disc_cfg->disconnected_cb(mgr_ctx, mgr_ctx->usr_ctx);
        break;

    case BLE_GAP_EVENT_NOTIFY_RX:
        ESP_LOGD(TAG, "Rx notification received on handle 0x%04x", event->notify_rx.attr_handle);

        ble_mgr_gap_notification_cb(mgr_ctx, event->notify_rx.om, event->notify_rx.attr_handle,
                                    event->notify_rx.conn_handle, (bool)event->notify_rx.indication);
        break;

    case BLE_GAP_EVENT_MTU:
        ESP_LOGD(TAG, "MTU exchange complete. MTU size: %d", event->mtu.value);
        break;

    case BLE_GAP_EVENT_LINK_ESTAB:
        // ignore
        break;

    default:
        ESP_LOGW(TAG, "Unhandled event type: %d", event->type);
        break;
    }
    return 0;
}

static void ble_mgr_gap_notification_cb(ble_mgr_ctx_t  *mgr_ctx,
                                        struct os_mbuf *om,
                                        uint16_t        attr_handle,
                                        uint16_t        conn_handle,
                                        bool            indication)
{
    ESP_NULL_CHECK(mgr_ctx, TAG, "context is NULL");

    if (om != NULL && conn_handle == mgr_ctx->conn_handle)
    {
        ESP_LOGD(TAG, "Notify Rx on handle 0x%04x: %.*s", attr_handle, om->om_len, (char const *)om->om_data);

        if (mgr_ctx->disc_cfg != NULL)
        {
            ESP_NULL_CHECK(mgr_ctx->disc_cfg->svc_def, TAG, "service context is NULL");

            ble_mgr_svc_def_t const *svc_def = mgr_ctx->disc_cfg->svc_def;

            for (size_t i = 0; i < svc_def->num_chars; i++)
            {
                if ((svc_def->chars[i].handle == attr_handle) && (svc_def->chars[i].notify_cb != NULL))
                {
                    for (struct os_mbuf *part = om; part; part = SLIST_NEXT(part, om_next)) {
                        svc_def->chars[i].notify_cb(part->om_data, part->om_len, attr_handle, mgr_ctx->usr_ctx);
                    }
                    break;
                }
            }
        }
        else
        {
            ESP_LOGW(TAG, "Service context is NULL");
        }
    }
    else
    {
        ESP_LOGW(TAG, "Notification received on handle 0x%04" PRIx16 ": NULL", attr_handle);
    }
}

static void ble_mgr_connect_complete(ble_mgr_ctx_t *mgr_ctx, ble_mgr_status_t status)
{
    ESP_NULL_CHECK(mgr_ctx, TAG, "context is NULL");

    if (!atomic_exchange(&mgr_ctx->connecting, false)) return;
    mgr_ctx->is_connected = (status == BLE_MGR_E_OK);
    API_QUEUE_SEND(mgr_ctx, status);
}

static void ble_mgr_gatt_svc_chr_disc_completed_check(ble_mgr_ctx_t *mgr_ctx, const struct ble_gatt_error *error)
{
    ESP_NULL_CHECK(error, TAG, "error is NULL");
    ESP_NULL_CHECK(mgr_ctx, TAG, "context is NULL");

    // If the error status is 0, it means discovery is not completed yet
    if (error->status == 0)
    {
        // FIXME: Check if this is true
        ESP_LOGW(TAG, "Discovery in progress ???????????????????????????????");
        return;
    }

    // If discovery failed, call the callback with false
    if (error->status != BLE_HS_EDONE)
    {
        ESP_LOGE(TAG, "Discovery failed: %d", error->status);
        ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_DISCOVERY_FAILED);
        return;
    }

    // If characteristic discovery has started but not completed, do not call the callback yet
    if (mgr_ctx->svc_disc_ctx.chr_disc_started && !mgr_ctx->svc_disc_ctx.chr_disc_completed)
    {
        return;
    }

    // If both characteristic and service discovery have completed, call the callback
    if (mgr_ctx->svc_disc_ctx.svc_disc_completed && mgr_ctx->svc_disc_ctx.chr_disc_completed)
    {
        for (size_t i = 0; i < mgr_ctx->disc_cfg->svc_def->num_chars; i++) {
            if (!mgr_ctx->disc_cfg->svc_def->chars[i].handle) {
                ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_DISCOVERY_FAILED);
                return;
            }
        }
        ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_OK);
        return;
    }

    // If characteristic discovery has not started, but service discovery has completed, call the callback
    if (!mgr_ctx->svc_disc_ctx.chr_disc_started && mgr_ctx->svc_disc_ctx.svc_disc_completed)
    {
        ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_DISCOVERY_FAILED);
        return;
    }
}

static int ble_mgr_gatt_chr_discovered_cb(uint16_t                     conn_handle,
                                          const struct ble_gatt_error *error,
                                          const struct ble_gatt_chr   *chr,
                                          void                        *arg)
{
    ESP_NULL_CHECK(error, TAG, "error is NULL");
    ble_mgr_ctx_t *mgr_ctx = (ble_mgr_ctx_t *)arg;
    ESP_NULL_CHECK(mgr_ctx, TAG, "context is NULL");
    if (!atomic_load(&mgr_ctx->connecting) || conn_handle != mgr_ctx->conn_handle) return 0;

    if (error->status == 0 && chr != NULL)
    {
        char uuid_str[BLE_UUID_STR_LEN];
        ble_uuid_to_str(&chr->uuid.u, uuid_str);
        ESP_LOGD(TAG, "Characteristic found: UUID = %s, handle = 0x%04x", uuid_str, chr->val_handle);

        for (size_t i = 0; i < mgr_ctx->disc_cfg->svc_def->num_chars; i++)
        {
            if (strcmp(uuid_str, mgr_ctx->disc_cfg->svc_def->chars[i].uuid) == 0)
            {
                ESP_LOGD(TAG, "Found matching characteristic: %s", uuid_str);
                mgr_ctx->disc_cfg->svc_def->chars[i].handle = chr->val_handle;

                if (mgr_ctx->disc_cfg->svc_def->chars[i].notify_cb != NULL)
                {
                    ESP_LOGD(TAG, "Setting up notification callback for characteristic: %s", uuid_str);
                    int rc = ble_gattc_write_flat(conn_handle,
                                                  chr->val_handle + 1,  // CCCD handle is usually handle+1
                                                  cccd_notify_enable_cfg, sizeof(cccd_notify_enable_cfg), NULL, NULL);
                    if (rc != 0)
                    {
                        ESP_LOGE(TAG, "Failed to subscribe to TX notifications: %d", rc);
                    }
                }
                break;
            }
        }
    }
    else
    {
        ESP_LOGD(TAG, "Characteristic discovery complete");
        mgr_ctx->svc_disc_ctx.chr_disc_completed = true;
        ble_mgr_gatt_svc_chr_disc_completed_check(mgr_ctx, error);
    }

    return 0;
}

static int ble_mgr_gatt_svc_discovered_cb(uint16_t                     conn_handle,
                                          const struct ble_gatt_error *error,
                                          const struct ble_gatt_svc   *service,
                                          void                        *arg)
{
    ESP_NULL_CHECK(error, TAG, "error is NULL");
    ble_mgr_ctx_t *mgr_ctx = (ble_mgr_ctx_t *)arg;
    ESP_NULL_CHECK(mgr_ctx, TAG, "context is NULL");
    if (!atomic_load(&mgr_ctx->connecting) || conn_handle != mgr_ctx->conn_handle) return 0;

    char uuid_str[BLE_UUID_STR_LEN];

    if (error->status == 0 && service)
    {
        ble_uuid_to_str(&service->uuid.u, uuid_str);
        ESP_LOGD(TAG, "Service found: UUID = %s", uuid_str);

        if (strcmp(uuid_str, mgr_ctx->disc_cfg->svc_def->service_uuid) == 0)
        {
            ESP_LOGD(TAG, "Target service found: %s", uuid_str);
            ESP_LOGD(TAG, "Starting characteristic discovery...");

            mgr_ctx->svc_disc_ctx.chr_disc_started = true;

            int rc = ble_gattc_disc_all_chrs(conn_handle, service->start_handle, service->end_handle,
                                             ble_mgr_gatt_chr_discovered_cb, mgr_ctx);
            if (rc != 0)
            {
                ESP_LOGE(TAG, "Failed to start characteristic discovery: %d", rc);
            }
        }
    }
    else
    {
        ESP_LOGD(TAG, "Service discovery complete");
        mgr_ctx->svc_disc_ctx.svc_disc_completed = true;
        ble_mgr_gatt_svc_chr_disc_completed_check(mgr_ctx, error);
    }

    return 0;
}

static void ble_mgr_gap_connected_cb(ble_mgr_ctx_t *mgr_ctx, uint16_t conn_handle, int status)
{
    ESP_NULL_CHECK(mgr_ctx, TAG, "Manager context is NULL");

    if (status != 0)
    {
        ESP_LOGE(TAG, "Connection failed: %d", status);
        ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_NOT_CONNECTED);
        return;
    }

    if (!atomic_load(&mgr_ctx->connecting)) {
        ble_gap_terminate(conn_handle, BLE_ERR_REM_USER_CONN_TERM);
        return;
    }
    mgr_ctx->conn_handle = conn_handle;

    mgr_ctx->svc_disc_ctx.svc_disc_completed = false;
    mgr_ctx->svc_disc_ctx.chr_disc_completed = false;
    mgr_ctx->svc_disc_ctx.chr_disc_started   = false;

    int rc = ble_gattc_disc_all_svcs(mgr_ctx->conn_handle, ble_mgr_gatt_svc_discovered_cb, mgr_ctx);
    if (rc != 0)
    {
        ESP_LOGE(TAG, "Failed to start service discovery: %d", rc);
        ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_DISCOVERY_FAILED);
        return;
    }
    ESP_LOGD(TAG, "Service discovery started");
}

// ---------------------------------------------------------------------------------------------------------------------
// Public Function Definitions
// ---------------------------------------------------------------------------------------------------------------------

ble_mgr_ctx_t *ble_mgr_init(unsigned source_id, int timeout_ms)
{
    if (source_id >= ARRAY_SIZE(BLE_MGR_CTX)) return NULL;
    /* app_main initializes both slots before starting the polling tasks. */
    if (!stack_started) {
        stack_ready = xSemaphoreCreateBinary();
        scan_lock = xSemaphoreCreateMutex();
        if (!stack_ready || !scan_lock) return NULL;
        ble_init_stack(&ble_init_cfg);
        stack_started = true;
        if (xSemaphoreTake(stack_ready, pdMS_TO_TICKS(timeout_ms)) != pdTRUE) {
            ESP_LOGE(TAG, "NimBLE host did not synchronize");
            return NULL;
        }
    }
    ble_mgr_ctx_t *mgr_ctx = &BLE_MGR_CTX[source_id];
    if (!mgr_ctx->api.lock_mtx) {
        mgr_ctx->api.lock_mtx = xSemaphoreCreateMutex();
        mgr_ctx->api.result_que = xQueueCreate(1, sizeof(ble_mgr_status_t));
        if (!mgr_ctx->api.lock_mtx || !mgr_ctx->api.result_que) return NULL;
    }
    return mgr_ctx;
}

ble_mgr_status_t ble_mgr_connect_service(ble_mgr_ctx_t            *mgr_ctx,
                                         ble_mgr_disc_cfg_t const *disc_cfg,
                                         uint32_t                  timeout_ms,
                                         void                     *usr_ctx)
{
    ESP_NULL_CHECK(mgr_ctx, TAG, "context is NULL");
    ESP_NULL_CHECK(disc_cfg, TAG, "discovery config is NULL");
    ESP_NULL_CHECK(disc_cfg->dev_filter_cb, TAG, "discovery config device filter callback is NULL");
    ESP_NULL_CHECK(disc_cfg->svc_def, TAG, "discovery config service is NULL");

    if (xSemaphoreTake(scan_lock, pdMS_TO_TICKS(timeout_ms)) != pdTRUE) return BLE_MGR_E_TIMEOUT;
    if (xSemaphoreTake(mgr_ctx->api.lock_mtx, pdMS_TO_TICKS(timeout_ms)) != pdTRUE) {
        xSemaphoreGive(scan_lock);
        return BLE_MGR_E_API_LOCK_ERROR;
    }
    if (atomic_load(&mgr_ctx->is_connected)) {
        xSemaphoreGive(mgr_ctx->api.lock_mtx);
        xSemaphoreGive(scan_lock);
        return BLE_MGR_E_OK;
    }
    xQueueReset(mgr_ctx->api.result_que);
    mgr_ctx->conn_handle = BLE_HS_CONN_HANDLE_NONE;
    mgr_ctx->scanning = true;
    mgr_ctx->connecting = true;
    for (size_t i = 0; i < disc_cfg->svc_def->num_chars; i++) disc_cfg->svc_def->chars[i].handle = 0;
    mgr_ctx->disc_cfg = disc_cfg;
    mgr_ctx->usr_ctx = usr_ctx;
    int rc = ble_gap_disc(0, BLE_DISCOVERY_TIMEOUT_MS, &disc_params, ble_mgr_gap_event_cb, mgr_ctx);
    ble_mgr_status_t status = BLE_MGR_E_DISCOVERY_FAILED;
    if (rc == 0) {
        if (!API_QUEUE_WAIT(mgr_ctx, &status, timeout_ms)) status = BLE_MGR_E_TIMEOUT;
    }
    mgr_ctx->scanning = false;
    if (status != BLE_MGR_E_OK) {
        atomic_store(&mgr_ctx->connecting, false);
        ble_gap_disc_cancel();
        ble_gap_conn_cancel();
        if (mgr_ctx->conn_handle != BLE_HS_CONN_HANDLE_NONE)
            ble_gap_terminate(mgr_ctx->conn_handle, BLE_ERR_REM_USER_CONN_TERM);
    }
    xSemaphoreGive(mgr_ctx->api.lock_mtx);
    xSemaphoreGive(scan_lock);
    return status;
}

ble_mgr_status_t ble_mgr_send(ble_mgr_ctx_t *mgr_ctx, uint16_t chr_handle, const char *data, size_t len)
{
    ESP_NULL_CHECK(mgr_ctx, TAG, "context is NULL");
    ESP_NULL_CHECK(data, TAG, "command is NULL");
    ESP_NULL_CHECK(mgr_ctx->disc_cfg, TAG, "discovery config is NULL");

    API_LOCK_OR_RETURN(mgr_ctx, BLE_MGR_E_API_LOCK_ERROR);

    if (!mgr_ctx->is_connected)
    {
        ESP_LOGE(TAG, "Not connected to any service");
        return API_UNLOCK(mgr_ctx, BLE_MGR_E_NOT_CONNECTED);
    }

    ESP_LOGD(TAG, "Sending to tx characteristic (conn_handle=0x%04x, chr_handle=0x%04x, len=%zu): %.*s",
             mgr_ctx->conn_handle, chr_handle, len, (int)len, data);
    int rc = ble_gattc_write_flat(mgr_ctx->conn_handle, chr_handle, data, len, NULL, NULL);
    if (rc != 0)
    {
        ESP_LOGE(TAG, "Failed to send command: %d", rc);
        return API_UNLOCK(mgr_ctx, BLE_MGR_E_GATT_SEND_FAILED);
    }

    return API_UNLOCK(mgr_ctx, BLE_MGR_E_OK);
}

bool ble_mgr_is_connected(ble_mgr_ctx_t *mgr_ctx)
{
    ESP_NULL_CHECK(mgr_ctx, TAG, "context is NULL");

    return mgr_ctx->is_connected;
}

void ble_mgr_disconnect(ble_mgr_ctx_t *mgr_ctx)
{
    if (!mgr_ctx || !atomic_load(&mgr_ctx->is_connected)) return;
    ble_gap_terminate(mgr_ctx->conn_handle, BLE_ERR_REM_USER_CONN_TERM);
}
