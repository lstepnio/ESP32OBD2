// ---------------------------------------------------------------------------------------------------------------------
// Includes
// ---------------------------------------------------------------------------------------------------------------------

#include <inttypes.h>
#include <stdbool.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
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
#include "ble_companion.h"
#include "ble_mgr.h"
#include "ble_util.h"
#include "obd_adapter_profile.h"
#include "obd_wait_policy.h"
#include "ble_recovery_policy.h"
#include "sdkconfig.h"

// ---------------------------------------------------------------------------------------------------------------------
// Private Types
// ---------------------------------------------------------------------------------------------------------------------

struct ble_mgr_ctx
{
    atomic_uint_least16_t conn_handle;
    atomic_bool           is_connected;
    atomic_bool           connecting;
    atomic_bool           scanning;
    atomic_bool           write_inflight;
    atomic_uint           write_generation;
    atomic_uintptr_t      attempt_token;
    atomic_uint           callback_readers;
    atomic_bool           callback_retiring;
    QueueHandle_t         write_result;

    ble_mgr_disc_cfg_t const *disc_cfg;

    void *usr_ctx;

    struct
    {
        bool     svc_disc_completed;
        bool     chr_disc_completed;
        bool     chr_disc_started;
        bool     dsc_disc_started;
        bool     subscription_started;
        uint16_t service_end_handle;
        size_t   notify_index;
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

static void           ble_mgr_gap_connected_cb(ble_mgr_ctx_t *mgr_ctx, uint16_t conn_handle, int status, void *arg);
static void           ble_mgr_connect_complete(ble_mgr_ctx_t *mgr_ctx, ble_mgr_status_t status);
static int            ble_mgr_gatt_dsc_discovered_cb(uint16_t                     conn_handle,
                                                     const struct ble_gatt_error *error,
                                                     uint16_t                     chr_val_handle,
                                                     const struct ble_gatt_dsc   *dsc,
                                                     void                        *arg);
static void           observe(ble_mgr_ctx_t *ctx, const char *event, const void *data, size_t length, int status);
static ble_mgr_ctx_t *attempt_context(void *arg);
static ble_mgr_ctx_t *callback_enter(void *arg);
static void           callback_leave(ble_mgr_ctx_t *ctx);
static void           retire_write(ble_mgr_ctx_t *ctx);
static void          *attempt_arg(ble_mgr_ctx_t *ctx);

static int cccd_readback_leased(uint16_t                     conn_handle,
                                const struct ble_gatt_error *error,
                                struct ble_gatt_attr        *attr,
                                void                        *arg);

static int cccd_readback(uint16_t                     conn_handle,
                         const struct ble_gatt_error *error,
                         struct ble_gatt_attr        *attr,
                         void                        *arg)
{
    ble_mgr_ctx_t *lease = callback_enter(arg);
    if (!lease)
    {
        return 0;
    }
    int result = cccd_readback_leased(conn_handle, error, attr, arg);
    callback_leave(lease);
    return result;
}

static int cccd_readback_leased(uint16_t                     conn_handle,
                                const struct ble_gatt_error *error,
                                struct ble_gatt_attr        *attr,
                                void                        *arg)
{
    ble_mgr_ctx_t *ctx = attempt_context(arg);
    if (!ctx || conn_handle != ctx->conn_handle)
        return 0;
    uint8_t bytes[2];
    size_t  length = 0;
    if (!error->status && attr && attr->om && OS_MBUF_PKTLEN(attr->om) == 2 &&
        os_mbuf_copydata(attr->om, 0, 2, bytes) == 0)
        length = 2;
    observe(ctx, "cccd_readback", bytes, length, error->status);
    return 0;
}

static int ble_mgr_gatt_subscription_cb(uint16_t                     conn_handle,
                                        const struct ble_gatt_error *error,
                                        struct ble_gatt_attr        *attr,
                                        void                        *arg);

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
static bool              stack_started;
static atomic_bool       central_paused;
static atomic_bool       user_scan_active;

/* Callback arguments carry an immutable attempt identity, never a mutable slot.
 * This also fences old GATT callbacks if NimBLE reuses a connection handle. */
static void *attempt_arg(ble_mgr_ctx_t *ctx)
{
    return (void *)atomic_load(&ctx->attempt_token);
}

static ble_mgr_ctx_t *attempt_context(void *arg)
{
    uintptr_t      token = (uintptr_t)arg;
    ble_mgr_ctx_t *ctx   = &BLE_MGR_CTX[token & 1U];
    return token && token == atomic_load(&ctx->attempt_token) ? ctx : NULL;
}

/* Admission uses a reader lease. The worker closes admission before waiting
 * for readers, so a callback that passed lookup pins the entire mutable slot. */
static ble_mgr_ctx_t *callback_enter(void *arg)
{
    ble_mgr_ctx_t *ctx = attempt_context(arg);
    if (!ctx)
        return NULL;
    atomic_fetch_add(&ctx->callback_readers, 1);
    if (atomic_load(&ctx->callback_retiring) || attempt_context(arg) != ctx)
    {
        atomic_fetch_sub(&ctx->callback_readers, 1);
        return NULL;
    }
    return ctx;
}

static void callback_leave(ble_mgr_ctx_t *ctx)
{
    atomic_fetch_sub(&ctx->callback_readers, 1);
}

static bool drain_retired_callbacks(uint32_t started_ms, uint32_t timeout_ms)
{
    uint32_t drain_started_ms = pdTICKS_TO_MS(xTaskGetTickCount());
    for (size_t i = 0; i < ARRAY_SIZE(BLE_MGR_CTX); ++i)
        if (!atomic_load(&BLE_MGR_CTX[i].is_connected))
            atomic_store(&BLE_MGR_CTX[i].callback_retiring, true);
    for (;;)
    {
        bool busy = false;
        for (size_t i = 0; i < ARRAY_SIZE(BLE_MGR_CTX); ++i)
            if (atomic_load(&BLE_MGR_CTX[i].callback_retiring) && atomic_load(&BLE_MGR_CTX[i].callback_readers))
                busy = true;
        if (!busy)
        {
            for (size_t i = 0; i < ARRAY_SIZE(BLE_MGR_CTX); ++i)
                if (atomic_load(&BLE_MGR_CTX[i].is_connected))
                    atomic_store(&BLE_MGR_CTX[i].callback_retiring, false);
            return true;
        }
        if (!obd_wait_remaining(drain_started_ms, pdTICKS_TO_MS(xTaskGetTickCount()), BLE_RECOVERY_CLEANUP_MS) ||
            !obd_wait_remaining(started_ms, pdTICKS_TO_MS(xTaskGetTickCount()), timeout_ms))
            return false;
        vTaskDelay(pdMS_TO_TICKS(10));
    }
}

/* A disconnect arriving while admission is closed is intentionally rejected.
 * After readers drain, reconcile the retired handle against host truth. */
static bool reap_retired_links(void)
{
    bool clean = true;
    for (size_t i = 0; i < ARRAY_SIZE(BLE_MGR_CTX); ++i)
    {
        ble_mgr_ctx_t *ctx    = &BLE_MGR_CTX[i];
        uint16_t       handle = atomic_load(&ctx->conn_handle);
        if (!atomic_load(&ctx->callback_retiring) || handle == BLE_HS_CONN_HANDLE_NONE)
            continue;
        struct ble_gap_conn_desc desc;
        if (ble_gap_conn_find(handle, &desc) == BLE_HS_ENOTCONN)
        {
            ctx->conn_handle = BLE_HS_CONN_HANDLE_NONE;
            retire_write(ctx);
            if (ctx->disc_cfg && ctx->disc_cfg->disconnected_cb)
                ctx->disc_cfg->disconnected_cb(ctx, ctx->usr_ctx);
        }
        else
        {
            ble_gap_terminate(handle, BLE_ERR_REM_USER_CONN_TERM);
            clean = false;
        }
    }
    return clean;
}

/* A retired CONNECT may be delivered after the host has reused its handle.
 * Preserve any currently owned source session before cleaning up an orphan. */
static ble_mgr_ctx_t *gap_attempt_context(struct ble_gap_event *event, void *arg)
{
    ble_mgr_ctx_t *ctx = attempt_context(arg);
    if (!ctx && event->type == BLE_GAP_EVENT_CONNECT && event->connect.status == 0)
    {
        uint16_t handle = event->connect.conn_handle;
        for (size_t i = 0; i < ARRAY_SIZE(BLE_MGR_CTX); ++i)
            if (handle == atomic_load(&BLE_MGR_CTX[i].conn_handle))
                return NULL;
        ble_gap_terminate(handle, BLE_ERR_REM_USER_CONN_TERM);
    }
    return ctx;
}

static void observe(ble_mgr_ctx_t *ctx, const char *event, const void *data, size_t length, int status)
{
    if (ctx->disc_cfg && ctx->disc_cfg->observe)
        ctx->disc_cfg->observe(event, data, length, status, ctx->usr_ctx);
}

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

static inline
    __attribute__((always_inline)) ble_mgr_status_t API_UNLOCK(ble_mgr_ctx_t const *mgr_ctx, ble_mgr_status_t retval)
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

static void retire_write(ble_mgr_ctx_t *ctx)
{
    atomic_fetch_add(&ctx->write_generation, 1);
    if (atomic_exchange(&ctx->write_inflight, false))
    {
        ble_mgr_status_t status = BLE_MGR_E_NOT_CONNECTED;
        xQueueSend(ctx->write_result, &status, 0);
    }
}

static void ble_mgr_gap_stack_reset_cb(int reason)
{
    ESP_LOGW(TAG, "NimBLE stack reset, reset reason: %d", reason);
    ble_companion_reset();
    for (size_t i = 0; i < ARRAY_SIZE(BLE_MGR_CTX); i++)
    {
        ble_mgr_ctx_t *ctx = &BLE_MGR_CTX[i];
        if (atomic_exchange(&ctx->connecting, false))
            API_QUEUE_SEND(ctx, BLE_MGR_E_NOT_CONNECTED);
        bool was_connected = atomic_exchange(&ctx->is_connected, false);
        retire_write(ctx);
        ctx->conn_handle = BLE_HS_CONN_HANDLE_NONE;
        ctx->scanning    = false;
        if (was_connected && ctx->disc_cfg && ctx->disc_cfg->disconnected_cb)
            ctx->disc_cfg->disconnected_cb(ctx, ctx->usr_ctx);
    }
}

static void ble_mgr_gap_stack_sync_cb(void)
{
    ESP_LOGD(TAG, "NimBLE stack synced");
    ble_companion_start();
    if (stack_ready)
        xSemaphoreGive(stack_ready);
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

static int ble_mgr_gap_event_cb_leased(struct ble_gap_event *event, void *arg);

static int ble_mgr_gap_event_cb(struct ble_gap_event *event, void *arg)
{
    ble_mgr_ctx_t *lease = callback_enter(arg);
    if (!lease)
    {
        gap_attempt_context(event, NULL);
        return 0;
    }
    int result = ble_mgr_gap_event_cb_leased(event, arg);
    callback_leave(lease);
    return result;
}

static int ble_mgr_gap_event_cb_leased(struct ble_gap_event *event, void *arg)
{
    char                     addr_str[BLE_ADDR_STR_LEN];
    struct ble_hs_adv_fields adv_fields;

    ble_mgr_ctx_t *mgr_ctx = gap_attempt_context(event, arg);
    if (!mgr_ctx)
        return 0;
    ble_mgr_disc_cfg_t const *disc_cfg = mgr_ctx->disc_cfg;
    ESP_NULL_CHECK(disc_cfg, TAG, "discovery config is NULL");

    switch (event->type)
    {
    case BLE_GAP_EVENT_DISC:
        if (!mgr_ctx->scanning || !atomic_load(&mgr_ctx->connecting))
            break;
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
                int connect_rc = ble_gap_connect(BLE_OWN_ADDR_PUBLIC, &event->disc.addr, 8000, &conn_params,
                                                 ble_mgr_gap_event_cb, arg);
                if (connect_rc != 0)
                    ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_NOT_CONNECTED);
            }
        }
        break;

    case BLE_GAP_EVENT_DISC_COMPLETE:
        if (mgr_ctx->scanning && !atomic_load(&central_paused))
        {
            ESP_LOGD(TAG, "Device discovery window complete. Continuing...");
            int scan_rc = ble_gap_disc(0, BLE_DISCOVERY_TIMEOUT_MS, &disc_params, ble_mgr_gap_event_cb, arg);
            if (scan_rc)
                ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_DISCOVERY_FAILED);
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
        ble_mgr_gap_connected_cb(mgr_ctx, event->connect.conn_handle, event->connect.status, arg);
        break;

    case BLE_GAP_EVENT_DISCONNECT:
        if (event->disconnect.conn.conn_handle != mgr_ctx->conn_handle)
            break;
        retire_write(mgr_ctx);
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

    case BLE_GAP_EVENT_CONN_UPDATE_REQ:
        *event->conn_update_req.self_params = *event->conn_update_req.peer_params;
        observe(mgr_ctx, "conn_update_req", NULL, 0, 0);
        break;

    case BLE_GAP_EVENT_CONN_UPDATE:
        observe(mgr_ctx, "conn_updated", NULL, 0, event->conn_update.status);
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
                    for (struct os_mbuf *part = om; part; part = SLIST_NEXT(part, om_next))
                    {
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

    if (!atomic_exchange(&mgr_ctx->connecting, false))
        return;
    mgr_ctx->is_connected = (status == BLE_MGR_E_OK);
    API_QUEUE_SEND(mgr_ctx, status);
}

static void ble_mgr_gatt_svc_chr_disc_completed_check(ble_mgr_ctx_t               *mgr_ctx,
                                                      const struct ble_gatt_error *error,
                                                      void                        *arg)
{
    ESP_NULL_CHECK(error, TAG, "error is NULL");
    ESP_NULL_CHECK(mgr_ctx, TAG, "context is NULL");

    /* Successful item callbacks precede BLE_HS_EDONE; keep collecting. */
    if (error->status == 0)
    {
        return;
    }

    // If discovery failed, call the callback with false
    if (error->status != BLE_HS_EDONE)
    {
        ESP_LOGE(TAG, "Discovery failed: %d", error->status);
        ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_DISCOVERY_FAILED);
        return;
    }

    if (!mgr_ctx->svc_disc_ctx.chr_disc_started && mgr_ctx->svc_disc_ctx.svc_disc_completed)
    {
        ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_DISCOVERY_FAILED);
        return;
    }
    if (!mgr_ctx->svc_disc_ctx.chr_disc_completed || mgr_ctx->svc_disc_ctx.dsc_disc_started ||
        mgr_ctx->svc_disc_ctx.subscription_started)
        return;

    ble_mgr_svc_def_t *service = (ble_mgr_svc_def_t *)mgr_ctx->disc_cfg->svc_def;
    for (size_t i = mgr_ctx->svc_disc_ctx.notify_index; i < service->num_chars; ++i)
    {
        ble_gatt_char_def_t *chr = &service->chars[i];
        if (!chr->notify_cb)
            continue;
        if (!chr->handle || !(chr->properties & BLE_GATT_CHR_PROP_NOTIFY))
        {
            ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_DISCOVERY_FAILED);
            return;
        }
        uint16_t end_handle = mgr_ctx->svc_disc_ctx.service_end_handle;
        for (size_t next = 0; next < service->num_chars; ++next)
            if (service->chars[next].def_handle > chr->def_handle && service->chars[next].def_handle - 1 < end_handle)
                end_handle = service->chars[next].def_handle - 1;
        mgr_ctx->svc_disc_ctx.notify_index     = i;
        mgr_ctx->svc_disc_ctx.dsc_disc_started = true;
        int rc =
            ble_gattc_disc_all_dscs(mgr_ctx->conn_handle, chr->handle, end_handle, ble_mgr_gatt_dsc_discovered_cb, arg);
        if (rc != 0)
        {
            mgr_ctx->svc_disc_ctx.dsc_disc_started = false;
            ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_DISCOVERY_FAILED);
        }
        return;
    }

    for (size_t i = 0; i < service->num_chars; ++i)
    {
        ble_gatt_char_def_t *chr = &service->chars[i];
        if (!chr->handle ||
            (!chr->notify_cb && !(chr->properties & (BLE_GATT_CHR_PROP_WRITE | BLE_GATT_CHR_PROP_WRITE_NO_RSP))) ||
            (chr->notify_cb && !chr->cccd_handle))
        {
            ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_DISCOVERY_FAILED);
            return;
        }
    }
    mgr_ctx->svc_disc_ctx.notify_index = 0;
    for (size_t i = 0; i < service->num_chars; ++i)
    {
        ble_gatt_char_def_t *chr = &service->chars[i];
        if (!chr->notify_cb)
            continue;
        mgr_ctx->svc_disc_ctx.notify_index         = i;
        mgr_ctx->svc_disc_ctx.subscription_started = true;
        int rc = ble_gattc_write_flat(mgr_ctx->conn_handle, chr->cccd_handle, cccd_notify_enable_cfg,
                                      sizeof(cccd_notify_enable_cfg), ble_mgr_gatt_subscription_cb, arg);
        if (rc != 0)
        {
            mgr_ctx->svc_disc_ctx.subscription_started = false;
            ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_DISCOVERY_FAILED);
        }
        return;
    }
    ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_OK);
}

static int ble_mgr_gatt_dsc_discovered_cb_leased(uint16_t                     conn_handle,
                                                 const struct ble_gatt_error *error,
                                                 uint16_t                     chr_val_handle,
                                                 const struct ble_gatt_dsc   *dsc,
                                                 void                        *arg);

static int ble_mgr_gatt_dsc_discovered_cb(uint16_t                     conn_handle,
                                          const struct ble_gatt_error *error,
                                          uint16_t                     chr_val_handle,
                                          const struct ble_gatt_dsc   *dsc,
                                          void                        *arg)
{
    ble_mgr_ctx_t *lease = callback_enter(arg);
    if (!lease)
    {
        return 0;
    }
    int result = ble_mgr_gatt_dsc_discovered_cb_leased(conn_handle, error, chr_val_handle, dsc, arg);
    callback_leave(lease);
    return result;
}

static int ble_mgr_gatt_dsc_discovered_cb_leased(uint16_t                     conn_handle,
                                                 const struct ble_gatt_error *error,
                                                 uint16_t                     chr_val_handle,
                                                 const struct ble_gatt_dsc   *dsc,
                                                 void                        *arg)
{
    ble_mgr_ctx_t *mgr_ctx = attempt_context(arg);
    if (!mgr_ctx || !atomic_load(&mgr_ctx->connecting) || conn_handle != mgr_ctx->conn_handle)
        return 0;
    ble_mgr_svc_def_t   *service = (ble_mgr_svc_def_t *)mgr_ctx->disc_cfg->svc_def;
    ble_gatt_char_def_t *chr     = &service->chars[mgr_ctx->svc_disc_ctx.notify_index];
    if (chr->handle != chr_val_handle)
    {
        ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_DISCOVERY_FAILED);
        return 0;
    }
    if (error->status == 0 && dsc)
    {
        if (ble_uuid_u16(&dsc->uuid.u) == BLE_GATT_DSC_CLT_CFG_UUID16)
            chr->cccd_handle = dsc->handle;
        return 0;
    }
    mgr_ctx->svc_disc_ctx.dsc_disc_started = false;
    if (error->status != BLE_HS_EDONE || !chr->cccd_handle)
    {
        ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_DISCOVERY_FAILED);
        return 0;
    }
    mgr_ctx->svc_disc_ctx.notify_index++;
    ble_mgr_gatt_svc_chr_disc_completed_check(mgr_ctx, error, arg);
    return 0;
}

static int ble_mgr_gatt_subscription_cb_leased(uint16_t                     conn_handle,
                                               const struct ble_gatt_error *error,
                                               struct ble_gatt_attr        *attr,
                                               void                        *arg);

static int ble_mgr_gatt_subscription_cb(uint16_t                     conn_handle,
                                        const struct ble_gatt_error *error,
                                        struct ble_gatt_attr        *attr,
                                        void                        *arg)
{
    ble_mgr_ctx_t *lease = callback_enter(arg);
    if (!lease)
    {
        return 0;
    }
    int result = ble_mgr_gatt_subscription_cb_leased(conn_handle, error, attr, arg);
    callback_leave(lease);
    return result;
}

static int ble_mgr_gatt_subscription_cb_leased(uint16_t                     conn_handle,
                                               const struct ble_gatt_error *error,
                                               struct ble_gatt_attr        *attr,
                                               void                        *arg)
{
    (void)attr;
    ble_mgr_ctx_t *mgr_ctx = attempt_context(arg);
    if (!mgr_ctx || !atomic_load(&mgr_ctx->connecting) || conn_handle != mgr_ctx->conn_handle)
        return 0;
    if (error->status != 0)
    {
        ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_DISCOVERY_FAILED);
        return 0;
    }
    ble_mgr_svc_def_t   *service    = (ble_mgr_svc_def_t *)mgr_ctx->disc_cfg->svc_def;
    ble_gatt_char_def_t *subscribed = &service->chars[mgr_ctx->svc_disc_ctx.notify_index];
    observe(mgr_ctx, "cccd_written", cccd_notify_enable_cfg, sizeof(cccd_notify_enable_cfg), 0);
    int read_rc = ble_gattc_read(conn_handle, subscribed->cccd_handle, cccd_readback, arg);
    if (read_rc)
        observe(mgr_ctx, "cccd_readback", NULL, 0, read_rc);
    for (size_t i = mgr_ctx->svc_disc_ctx.notify_index + 1; i < service->num_chars; ++i)
    {
        ble_gatt_char_def_t *chr = &service->chars[i];
        if (!chr->notify_cb)
            continue;
        mgr_ctx->svc_disc_ctx.notify_index = i;
        int rc = ble_gattc_write_flat(conn_handle, chr->cccd_handle, cccd_notify_enable_cfg,
                                      sizeof(cccd_notify_enable_cfg), ble_mgr_gatt_subscription_cb, arg);
        if (rc != 0)
            ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_DISCOVERY_FAILED);
        return 0;
    }
    mgr_ctx->svc_disc_ctx.subscription_started = false;
    ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_OK);
    return 0;
}

static int ble_mgr_gatt_chr_discovered_cb_leased(uint16_t                     conn_handle,
                                                 const struct ble_gatt_error *error,
                                                 const struct ble_gatt_chr   *chr,
                                                 void                        *arg);

static int ble_mgr_gatt_chr_discovered_cb(uint16_t                     conn_handle,
                                          const struct ble_gatt_error *error,
                                          const struct ble_gatt_chr   *chr,
                                          void                        *arg)
{
    ble_mgr_ctx_t *lease = callback_enter(arg);
    if (!lease)
    {
        return 0;
    }
    int result = ble_mgr_gatt_chr_discovered_cb_leased(conn_handle, error, chr, arg);
    callback_leave(lease);
    return result;
}

static int ble_mgr_gatt_chr_discovered_cb_leased(uint16_t                     conn_handle,
                                                 const struct ble_gatt_error *error,
                                                 const struct ble_gatt_chr   *chr,
                                                 void                        *arg)
{
    ESP_NULL_CHECK(error, TAG, "error is NULL");
    ble_mgr_ctx_t *mgr_ctx = attempt_context(arg);
    if (!mgr_ctx)
        return 0;
    if (!atomic_load(&mgr_ctx->connecting) || conn_handle != mgr_ctx->conn_handle)
        return 0;

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
                mgr_ctx->disc_cfg->svc_def->chars[i].def_handle = chr->def_handle;
                mgr_ctx->disc_cfg->svc_def->chars[i].handle     = chr->val_handle;
                mgr_ctx->disc_cfg->svc_def->chars[i].properties = chr->properties;
                break;
            }
        }
    }
    else
    {
        ESP_LOGD(TAG, "Characteristic discovery complete");
        mgr_ctx->svc_disc_ctx.chr_disc_completed = true;
        ble_mgr_gatt_svc_chr_disc_completed_check(mgr_ctx, error, arg);
    }

    return 0;
}

static int ble_mgr_gatt_svc_discovered_cb_leased(uint16_t                     conn_handle,
                                                 const struct ble_gatt_error *error,
                                                 const struct ble_gatt_svc   *service,
                                                 void                        *arg);

static int ble_mgr_gatt_svc_discovered_cb(uint16_t                     conn_handle,
                                          const struct ble_gatt_error *error,
                                          const struct ble_gatt_svc   *service,
                                          void                        *arg)
{
    ble_mgr_ctx_t *lease = callback_enter(arg);
    if (!lease)
    {
        return 0;
    }
    int result = ble_mgr_gatt_svc_discovered_cb_leased(conn_handle, error, service, arg);
    callback_leave(lease);
    return result;
}

static int ble_mgr_gatt_svc_discovered_cb_leased(uint16_t                     conn_handle,
                                                 const struct ble_gatt_error *error,
                                                 const struct ble_gatt_svc   *service,
                                                 void                        *arg)
{
    ESP_NULL_CHECK(error, TAG, "error is NULL");
    ble_mgr_ctx_t *mgr_ctx = attempt_context(arg);
    if (!mgr_ctx)
        return 0;
    if (!atomic_load(&mgr_ctx->connecting) || conn_handle != mgr_ctx->conn_handle)
        return 0;

    char uuid_str[BLE_UUID_STR_LEN];

    if (error->status == 0 && service)
    {
        ble_uuid_to_str(&service->uuid.u, uuid_str);
        ESP_LOGD(TAG, "Service found: UUID = %s", uuid_str);

        if (strcmp(uuid_str, mgr_ctx->disc_cfg->svc_def->service_uuid) == 0)
        {
            ESP_LOGD(TAG, "Target service found: %s", uuid_str);
            ESP_LOGD(TAG, "Starting characteristic discovery...");

            mgr_ctx->svc_disc_ctx.chr_disc_started   = true;
            mgr_ctx->svc_disc_ctx.service_end_handle = service->end_handle;

            int rc = ble_gattc_disc_all_chrs(conn_handle, service->start_handle, service->end_handle,
                                             ble_mgr_gatt_chr_discovered_cb, arg);
            if (rc != 0)
            {
                ESP_LOGE(TAG, "Failed to start characteristic discovery: %d", rc);
                ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_DISCOVERY_FAILED);
            }
        }
    }
    else
    {
        ESP_LOGD(TAG, "Service discovery complete");
        mgr_ctx->svc_disc_ctx.svc_disc_completed = true;
        ble_mgr_gatt_svc_chr_disc_completed_check(mgr_ctx, error, arg);
    }

    return 0;
}

static void ble_mgr_gap_connected_cb(ble_mgr_ctx_t *mgr_ctx, uint16_t conn_handle, int status, void *arg)
{
    ESP_NULL_CHECK(mgr_ctx, TAG, "Manager context is NULL");

    if (status != 0)
    {
        ESP_LOGE(TAG, "Connection failed: %d", status);
        ble_mgr_connect_complete(mgr_ctx, BLE_MGR_E_NOT_CONNECTED);
        return;
    }

    if (!atomic_load(&mgr_ctx->connecting))
    {
        ble_gap_terminate(conn_handle, BLE_ERR_REM_USER_CONN_TERM);
        return;
    }
    mgr_ctx->conn_handle = conn_handle;

    mgr_ctx->svc_disc_ctx.svc_disc_completed   = false;
    mgr_ctx->svc_disc_ctx.chr_disc_completed   = false;
    mgr_ctx->svc_disc_ctx.chr_disc_started     = false;
    mgr_ctx->svc_disc_ctx.dsc_disc_started     = false;
    mgr_ctx->svc_disc_ctx.subscription_started = false;
    mgr_ctx->svc_disc_ctx.notify_index         = 0;
    mgr_ctx->svc_disc_ctx.service_end_handle   = 0;

    int rc = ble_gattc_disc_all_svcs(mgr_ctx->conn_handle, ble_mgr_gatt_svc_discovered_cb, arg);
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
    if (source_id >= ARRAY_SIZE(BLE_MGR_CTX))
        return NULL;
    /* app_main initializes both slots before starting the polling tasks. */
    if (!stack_started)
    {
        stack_ready = xSemaphoreCreateBinary();
        scan_lock   = xSemaphoreCreateMutex();
        if (!stack_ready || !scan_lock)
            return NULL;
        ble_init_stack(&ble_init_cfg);
        stack_started = true;
        if (xSemaphoreTake(stack_ready, pdMS_TO_TICKS(timeout_ms)) != pdTRUE)
        {
            ESP_LOGE(TAG, "NimBLE host did not synchronize");
            return NULL;
        }
    }
    ble_mgr_ctx_t *mgr_ctx = &BLE_MGR_CTX[source_id];
    if (!mgr_ctx->api.lock_mtx)
    {
        mgr_ctx->api.lock_mtx   = xSemaphoreCreateMutex();
        mgr_ctx->api.result_que = xQueueCreate(1, sizeof(ble_mgr_status_t));
        mgr_ctx->write_result   = xQueueCreate(1, sizeof(ble_mgr_status_t));
        if (!mgr_ctx->api.lock_mtx || !mgr_ctx->api.result_que || !mgr_ctx->write_result)
            return NULL;
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

    if (atomic_load(&mgr_ctx->is_connected))
        return BLE_MGR_E_OK;
    if (atomic_load(&central_paused) || atomic_load(&user_scan_active) || atomic_load(&mgr_ctx->write_inflight))
        return BLE_MGR_E_NOT_CONNECTED;
    uint32_t started_ms = pdTICKS_TO_MS(xTaskGetTickCount());
    if (xSemaphoreTake(scan_lock, pdMS_TO_TICKS(ble_recovery_admission_ms(timeout_ms))) != pdTRUE)
        return BLE_MGR_E_TIMEOUT;
    if (xSemaphoreTake(mgr_ctx->api.lock_mtx,
                       pdMS_TO_TICKS(obd_wait_remaining(started_ms, pdTICKS_TO_MS(xTaskGetTickCount()), timeout_ms))) !=
        pdTRUE)
    {
        xSemaphoreGive(scan_lock);
        return BLE_MGR_E_API_LOCK_ERROR;
    }
    if (atomic_load(&mgr_ctx->is_connected))
    {
        xSemaphoreGive(mgr_ctx->api.lock_mtx);
        xSemaphoreGive(scan_lock);
        return BLE_MGR_E_OK;
    }
    if (atomic_load(&central_paused) || atomic_load(&user_scan_active) ||
        !obd_wait_remaining(started_ms, pdTICKS_TO_MS(xTaskGetTickCount()), timeout_ms))
    {
        xSemaphoreGive(mgr_ctx->api.lock_mtx);
        xSemaphoreGive(scan_lock);
        return BLE_MGR_E_TIMEOUT;
    }
    if (!drain_retired_callbacks(started_ms, timeout_ms) || !reap_retired_links())
    {
        xSemaphoreGive(mgr_ctx->api.lock_mtx);
        xSemaphoreGive(scan_lock);
        return BLE_MGR_E_TIMEOUT;
    }
    if (atomic_load(&mgr_ctx->is_connected))
    {
        xSemaphoreGive(mgr_ctx->api.lock_mtx);
        xSemaphoreGive(scan_lock);
        return BLE_MGR_E_OK;
    }
    /* Admission has its own reserved allowance. A missing sibling cannot spend
     * this source's discovery budget while it waits in the mutex queue. */
    if (ble_gap_conn_active())
    {
        /* A prior cancel may have outlived its cleanup allowance. Retry only
         * the shared initiator cancellation while holding its lease. This
         * never terminates an established sibling connection. */
        ble_gap_conn_cancel();
        uint32_t cleanup_started_ms = pdTICKS_TO_MS(xTaskGetTickCount());
        while (ble_gap_conn_active() &&
               obd_wait_remaining(cleanup_started_ms, pdTICKS_TO_MS(xTaskGetTickCount()), BLE_RECOVERY_CLEANUP_MS) &&
               obd_wait_remaining(started_ms, pdTICKS_TO_MS(xTaskGetTickCount()), timeout_ms))
            vTaskDelay(pdMS_TO_TICKS(10));
    }
    uint32_t operation_started_ms = pdTICKS_TO_MS(xTaskGetTickCount());
    uint32_t operation_ms = ble_recovery_operation_ms(obd_wait_remaining(started_ms, operation_started_ms, timeout_ms));
    if (!operation_ms || ble_gap_conn_active())
    {
        xSemaphoreGive(mgr_ctx->api.lock_mtx);
        xSemaphoreGive(scan_lock);
        return BLE_MGR_E_TIMEOUT;
    }
    uintptr_t old_token = atomic_load(&mgr_ctx->attempt_token);
    atomic_store(&mgr_ctx->attempt_token, ble_recovery_next_token(old_token, (unsigned)(mgr_ctx - BLE_MGR_CTX)));
    xQueueReset(mgr_ctx->api.result_que);
    retire_write(mgr_ctx);
    mgr_ctx->conn_handle = BLE_HS_CONN_HANDLE_NONE;
    mgr_ctx->scanning    = !atomic_load(&central_paused);
    mgr_ctx->connecting  = true;
    for (size_t i = 0; i < disc_cfg->svc_def->num_chars; i++)
    {
        disc_cfg->svc_def->chars[i].def_handle  = 0;
        disc_cfg->svc_def->chars[i].handle      = 0;
        disc_cfg->svc_def->chars[i].cccd_handle = 0;
        disc_cfg->svc_def->chars[i].properties  = 0;
    }
    mgr_ctx->disc_cfg = disc_cfg;
    mgr_ctx->usr_ctx  = usr_ctx;
    atomic_store(&mgr_ctx->callback_retiring, false);
    int rc = ble_gap_disc(0, BLE_DISCOVERY_TIMEOUT_MS, &disc_params, ble_mgr_gap_event_cb, attempt_arg(mgr_ctx));
    ble_mgr_status_t status = BLE_MGR_E_DISCOVERY_FAILED;
    if (rc == 0)
    {
        if (!API_QUEUE_WAIT(mgr_ctx, &status,
                            obd_wait_remaining(operation_started_ms, pdTICKS_TO_MS(xTaskGetTickCount()), operation_ms)))
            status = BLE_MGR_E_TIMEOUT;
    }
    mgr_ctx->scanning = false;
    if (status != BLE_MGR_E_OK)
    {
        atomic_store(&mgr_ctx->is_connected, false);
        atomic_store(&mgr_ctx->connecting, false);
        ble_gap_disc_cancel();
        ble_gap_conn_cancel();
        if (mgr_ctx->conn_handle != BLE_HS_CONN_HANDLE_NONE)
            ble_gap_terminate(mgr_ctx->conn_handle, BLE_ERR_REM_USER_CONN_TERM);
        /* Cancellation completes asynchronously. Keep the scanner lease until
         * the host is idle or the reserved cleanup budget expires. Never reset
         * the host or tear down a healthy sibling to recover this attempt. */
        uint32_t cleanup_started_ms = pdTICKS_TO_MS(xTaskGetTickCount());
        while ((ble_gap_conn_active() || mgr_ctx->conn_handle != BLE_HS_CONN_HANDLE_NONE) &&
               obd_wait_remaining(cleanup_started_ms, pdTICKS_TO_MS(xTaskGetTickCount()), BLE_RECOVERY_CLEANUP_MS) &&
               obd_wait_remaining(started_ms, pdTICKS_TO_MS(xTaskGetTickCount()), timeout_ms))
            vTaskDelay(pdMS_TO_TICKS(10));
    }
    xSemaphoreGive(mgr_ctx->api.lock_mtx);
    xSemaphoreGive(scan_lock);
    ble_companion_start();
    return status;
}

typedef struct
{
    ble_mgr_ctx_t *ctx;
    unsigned       generation;
    uintptr_t      attempt_token;
} write_request_t;

static int write_completed(uint16_t                     conn_handle,
                           const struct ble_gatt_error *error,
                           struct ble_gatt_attr        *attr,
                           void                        *arg)
{
    (void)attr;
    write_request_t *request = arg;
    ble_mgr_ctx_t   *lease   = callback_enter((void *)request->attempt_token);
    if (!lease)
    {
        free(request);
        return 0;
    }
    ble_mgr_ctx_t *ctx        = request->ctx;
    unsigned       generation = request->generation;
    free(request);
    if (generation != atomic_load(&ctx->write_generation) || conn_handle != ctx->conn_handle ||
        !atomic_exchange(&ctx->write_inflight, false))
    {
        callback_leave(lease);
        return 0;
    }
    ble_mgr_status_t status = error->status ? BLE_MGR_E_GATT_SEND_FAILED : BLE_MGR_E_OK;
    observe(ctx, "write_completed", NULL, 0, error->status);
    xQueueSend(ctx->write_result, &status, 0);
    callback_leave(lease);
    return 0;
}

ble_mgr_status_t ble_mgr_send(ble_mgr_ctx_t *mgr_ctx, uint16_t chr_handle, const char *data, size_t len)
{
    return ble_mgr_send_with_timeout(mgr_ctx, chr_handle, data, len, 2000);
}

ble_mgr_status_t ble_mgr_send_with_timeout(ble_mgr_ctx_t *mgr_ctx,
                                           uint16_t       chr_handle,
                                           const char    *data,
                                           size_t         len,
                                           uint32_t       timeout_ms)
{
    if (!mgr_ctx || !data || !mgr_ctx->disc_cfg)
        return BLE_MGR_E_NULL;
    if (!timeout_ms)
        return BLE_MGR_E_TIMEOUT;
    TickType_t start  = xTaskGetTickCount();
    TickType_t budget = pdMS_TO_TICKS(timeout_ms);
    if (!budget)
        budget = 1;
    API_LOCK_OR_RETURN(mgr_ctx, BLE_MGR_E_API_LOCK_ERROR);
    if (!atomic_load(&mgr_ctx->is_connected) || atomic_load(&central_paused) || atomic_load(&mgr_ctx->write_inflight))
        return API_UNLOCK(mgr_ctx, BLE_MGR_E_NOT_CONNECTED);
    uint8_t properties = 0;
    for (size_t i = 0; i < mgr_ctx->disc_cfg->svc_def->num_chars; ++i)
        if (mgr_ctx->disc_cfg->svc_def->chars[i].handle == chr_handle)
            properties = mgr_ctx->disc_cfg->svc_def->chars[i].properties;
    if (!(properties & (BLE_GATT_CHR_PROP_WRITE | BLE_GATT_CHR_PROP_WRITE_NO_RSP)))
        return API_UNLOCK(mgr_ctx, BLE_MGR_E_GATT_SEND_FAILED);
    if (!(properties & BLE_GATT_CHR_PROP_WRITE))
    {
        int rc = ble_gattc_write_no_rsp_flat(mgr_ctx->conn_handle, chr_handle, data, len);
        return API_UNLOCK(mgr_ctx, rc ? BLE_MGR_E_GATT_SEND_FAILED : BLE_MGR_E_OK);
    }
    write_request_t *request = malloc(sizeof(*request));
    if (!request)
        return API_UNLOCK(mgr_ctx, BLE_MGR_E_GATT_SEND_FAILED);
    request->ctx           = mgr_ctx;
    request->attempt_token = atomic_load(&mgr_ctx->attempt_token);
    request->generation    = atomic_fetch_add(&mgr_ctx->write_generation, 1) + 1;
    xQueueReset(mgr_ctx->write_result);
    atomic_store(&mgr_ctx->write_inflight, true);
    int rc = ble_gattc_write_flat(mgr_ctx->conn_handle, chr_handle, data, len, write_completed, request);
    if (rc)
    {
        free(request);
        atomic_store(&mgr_ctx->write_inflight, false);
        observe(mgr_ctx, "write_completed", NULL, 0, rc);
        return API_UNLOCK(mgr_ctx, BLE_MGR_E_GATT_SEND_FAILED);
    }
    ble_mgr_status_t status;
    TickType_t       elapsed   = xTaskGetTickCount() - start;
    TickType_t       remaining = elapsed < budget ? budget - elapsed : 0;
    if (xQueueReceive(mgr_ctx->write_result, &status, remaining) != pdTRUE)
    {
        /* Never reuse the link after an uncertain ATT write completion. */
        ble_mgr_disconnect(mgr_ctx);
        status = BLE_MGR_E_TIMEOUT;
    }
    return API_UNLOCK(mgr_ctx, status);
}

bool ble_mgr_is_connected(ble_mgr_ctx_t *mgr_ctx)
{
    ESP_NULL_CHECK(mgr_ctx, TAG, "context is NULL");

    return mgr_ctx->is_connected;
}

void ble_mgr_disconnect(ble_mgr_ctx_t *mgr_ctx)
{
    if (!mgr_ctx || !atomic_load(&mgr_ctx->is_connected))
        return;
    atomic_store(&mgr_ctx->is_connected, false);
    ble_gap_terminate(mgr_ctx->conn_handle, BLE_ERR_REM_USER_CONN_TERM);
}

void ble_mgr_set_paused(bool paused)
{
    atomic_store(&central_paused, paused);
    if (!paused || !stack_started)
        return;
    ble_gap_disc_cancel();
    ble_gap_conn_cancel();
    for (size_t i = 0; i < ARRAY_SIZE(BLE_MGR_CTX); ++i)
    {
        ble_mgr_ctx_t *ctx = &BLE_MGR_CTX[i];
        ctx->scanning      = false;
        ble_mgr_connect_complete(ctx, BLE_MGR_E_NOT_CONNECTED);
        if (ctx->conn_handle != BLE_HS_CONN_HANDLE_NONE)
        {
            atomic_store(&ctx->is_connected, false);
            ble_gap_terminate(ctx->conn_handle, BLE_ERR_REM_USER_CONN_TERM);
        }
    }
}

bool ble_mgr_is_paused(void)
{
    return atomic_load(&central_paused);
}

typedef struct
{
    void (*found)(const ble_addr_t *, const char *, uint8_t, void *);
    void             *context;
    SemaphoreHandle_t done;
} scan_context_t;
static scan_context_t adapter_scan;

static int adapter_scan_event(struct ble_gap_event *event, void *arg)
{
    scan_context_t *scan = arg;
    if (event->type == BLE_GAP_EVENT_DISC_COMPLETE)
        xSemaphoreGive(scan->done);
    else if (event->type == BLE_GAP_EVENT_DISC && !atomic_load(&central_paused))
    {
        struct ble_hs_adv_fields fields;
        if (!ble_hs_adv_parse_fields(&fields, event->disc.data, event->disc.length_data))
        {
            uint8_t driver = ble_mgr_adv_contains_service(&fields, "0x18f0") ? 1 : 0;
#if CONFIG_EGAUGE_OBD_TRACE
            if (ble_mgr_adv_contains_service(&fields, OBD_BENCH_SERVICE))
                driver = 2;
#endif
            if (!driver)
                return 0;
            char name[25] = {0};
            for (size_t i = 0; i < fields.name_len && i < sizeof(name) - 1; ++i)
                name[i] = fields.name[i] >= 32 && fields.name[i] <= 126 ? fields.name[i] : '?';
            scan->found(&event->disc.addr, driver == 2 ? "Bench simulator" : name, driver, scan->context);
        }
    }
    return 0;
}

ble_mgr_status_t ble_mgr_scan_adapters(void (*found)(const ble_addr_t *, const char *, uint8_t, void *), void *context)
{
    if (!found || !stack_started || atomic_load(&central_paused))
        return BLE_MGR_E_NOT_CONNECTED;
    atomic_store(&user_scan_active, true);
    /* A user search preempts a background connection attempt. Connected
     * adapters keep their link; only the shared scanner is taken over. */
    for (unsigned i = 0; i < ARRAY_SIZE(BLE_MGR_CTX); ++i)
    {
        ble_mgr_ctx_t *ctx = &BLE_MGR_CTX[i];
        if (atomic_load(&ctx->connecting))
        {
            ctx->scanning = false;
            ble_mgr_connect_complete(ctx, BLE_MGR_E_NOT_CONNECTED);
        }
    }
    ble_gap_disc_cancel();
    ble_gap_conn_cancel();
    if (xSemaphoreTake(scan_lock, pdMS_TO_TICKS(1000)) != pdTRUE)
    {
        atomic_store(&user_scan_active, false);
        return BLE_MGR_E_API_LOCK_ERROR;
    }
    if (!adapter_scan.done)
        adapter_scan.done = xSemaphoreCreateBinary();
    if (!adapter_scan.done)
    {
        atomic_store(&user_scan_active, false);
        xSemaphoreGive(scan_lock);
        return BLE_MGR_E_NULL;
    }
    while (xSemaphoreTake(adapter_scan.done, 0) == pdTRUE)
    {
    }
    adapter_scan.found      = found;
    adapter_scan.context    = context;
    int              rc     = ble_gap_disc(0, 8000, &disc_params, adapter_scan_event, &adapter_scan);
    ble_mgr_status_t status = BLE_MGR_E_DISCOVERY_FAILED;
    if (!rc)
    {
        status = xSemaphoreTake(adapter_scan.done, pdMS_TO_TICKS(9000)) == pdTRUE ? BLE_MGR_E_OK : BLE_MGR_E_TIMEOUT;
        if (status != BLE_MGR_E_OK)
            ble_gap_disc_cancel();
        if (atomic_load(&central_paused))
            status = BLE_MGR_E_NOT_CONNECTED;
    }
    atomic_store(&user_scan_active, false);
    xSemaphoreGive(scan_lock);
    return status;
}
