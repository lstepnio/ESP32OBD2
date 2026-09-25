#include <stdint.h>
#include <string.h>
#include <stdatomic.h>

#include "esp_random.h"
#include "esp_log.h"
#include "esp_system.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "nvs.h"
#include "host/ble_att.h"
#include "host/ble_gap.h"
#include "host/ble_gatt.h"
#include "host/ble_hs.h"
#include "host/ble_hs_adv.h"
#include "host/ble_store.h"
#include "host/ble_uuid.h"
#include "host/util/util.h"
#include "os/os_mbuf.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"
#include "ble_companion.h"
#include "ui.h"

static const char *TAG = "COMPANION";
static const ble_uuid128_t service_uuid = BLE_UUID128_INIT(
    0x00, 0x6c, 0x3b, 0xd2, 0xc9, 0x69, 0x14, 0xa7,
    0x45, 0x4f, 0x3b, 0x9e, 0x00, 0x00, 0x1a, 0x6f);
static const ble_uuid128_t capabilities_uuid = BLE_UUID128_INIT(
    0x00, 0x6c, 0x3b, 0xd2, 0xc9, 0x69, 0x14, 0xa7,
    0x45, 0x4f, 0x3b, 0x9e, 0x01, 0x00, 0x1a, 0x6f);
static const ble_uuid128_t control_uuid = BLE_UUID128_INIT(
    0x00, 0x6c, 0x3b, 0xd2, 0xc9, 0x69, 0x14, 0xa7,
    0x45, 0x4f, 0x3b, 0x9e, 0x02, 0x00, 0x1a, 0x6f);
static const ble_uuid128_t state_uuid = BLE_UUID128_INIT(
    0x00, 0x6c, 0x3b, 0xd2, 0xc9, 0x69, 0x14, 0xa7,
    0x45, 0x4f, 0x3b, 0x9e, 0x03, 0x00, 0x1a, 0x6f);

static ui_t *g_ui;
static QueueHandle_t g_selection_queue;
static atomic_uchar g_selected_index;
static atomic_uint g_revision;
static ble_addr_t g_owner;
static atomic_bool g_has_owner;
static atomic_uint g_pairing_until;

static bool address_equal(const ble_addr_t *a, const ble_addr_t *b)
{
    return a->type == b->type && memcmp(a->val, b->val, sizeof(a->val)) == 0;
}

static bool pairing_open(void)
{
    unsigned until = atomic_load(&g_pairing_until);
    return until != 0 && (int32_t)(until - xTaskGetTickCount()) > 0;
}

static bool authorized(uint16_t conn_handle)
{
    struct ble_gap_conn_desc desc;
    return ble_gap_conn_find(conn_handle, &desc) == 0 && atomic_load(&g_has_owner) &&
           desc.sec_state.encrypted && desc.sec_state.authenticated &&
           desc.sec_state.bonded && address_equal(&desc.peer_id_addr, &g_owner);
}

static void load_owner(void)
{
    nvs_handle_t handle;
    if (nvs_open("eg_owner", NVS_READONLY, &handle) != ESP_OK) return;
    size_t size = sizeof(g_owner);
    atomic_store(&g_has_owner,
                 nvs_get_blob(handle, "peer", &g_owner, &size) == ESP_OK && size == sizeof(g_owner));
    nvs_close(handle);
}

static bool save_owner(const ble_addr_t *owner)
{
    nvs_handle_t handle;
    if (nvs_open("eg_owner", NVS_READWRITE, &handle) != ESP_OK) return false;
    esp_err_t err = nvs_set_blob(handle, "peer", owner, sizeof(*owner));
    if (err == ESP_OK) err = nvs_commit(handle);
    nvs_close(handle);
    if (err != ESP_OK) return false;
    g_owner = *owner;
    atomic_store(&g_has_owner, true);
    return true;
}

/* Public protocol-0 capabilities advertise one bounded, protected selection
 * operation. Full configuration, diagnostics, and updates remain disabled. */
static const char capabilities[] =
    "{\"protocolMajor\":0,\"board\":\"ESP32-S3-Touch-LCD-1.28\","
    "\"maxAdapterLinks\":2,\"simultaneousAdapterLinksVerified\":false,"
    "\"configWrite\":false,\"quickSelect\":true,\"ota\":false}";

/* Protocol 0 quick-select request: byte 0 = 1, byte 1 = built-in PID index 0..4.
 * A successful ATT write queues a request; the authenticated state read confirms apply. */
static int control_access(uint16_t conn_handle, uint16_t attr_handle,
                          struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    (void)attr_handle;
    (void)arg;
    if (ctxt->op != BLE_GATT_ACCESS_OP_WRITE_CHR) return BLE_ATT_ERR_REQ_NOT_SUPPORTED;
    if (!authorized(conn_handle)) return BLE_ATT_ERR_INSUFFICIENT_AUTHEN;
    if (OS_MBUF_PKTLEN(ctxt->om) != 2) return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
    uint8_t request[2];
    if (os_mbuf_copydata(ctxt->om, 0, 2, request) != 0 ||
        request[0] != 1 || request[1] > 4) return BLE_ATT_ERR_VALUE_NOT_ALLOWED;
    if (g_selection_queue == NULL || xQueueSend(g_selection_queue, &request[1], 0) != pdTRUE)
        return BLE_ATT_ERR_UNLIKELY;
    return 0;
}

static int state_access(uint16_t conn_handle, uint16_t attr_handle,
                        struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    (void)attr_handle;
    (void)arg;
    if (ctxt->op != BLE_GATT_ACCESS_OP_READ_CHR) return BLE_ATT_ERR_REQ_NOT_SUPPORTED;
    if (!authorized(conn_handle)) return BLE_ATT_ERR_INSUFFICIENT_AUTHEN;
    unsigned revision = atomic_load(&g_revision);
    uint8_t state[] = {1, atomic_load(&g_selected_index), revision & 0xff, (revision >> 8) & 0xff};
    if (ctxt->offset > sizeof(state)) return BLE_ATT_ERR_INVALID_OFFSET;
    return os_mbuf_append(ctxt->om, state + ctxt->offset,
                          sizeof(state) - ctxt->offset) == 0 ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
}

static int capabilities_read(uint16_t conn_handle, uint16_t attr_handle,
                             struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    (void)conn_handle;
    (void)attr_handle;
    (void)arg;
    if (ctxt->op != BLE_GATT_ACCESS_OP_READ_CHR) return BLE_ATT_ERR_REQ_NOT_SUPPORTED;
    size_t length = sizeof(capabilities) - 1;
    if (ctxt->offset > length) return BLE_ATT_ERR_INVALID_OFFSET;
    return os_mbuf_append(ctxt->om, capabilities + ctxt->offset,
                          length - ctxt->offset) == 0 ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
}

static const struct ble_gatt_chr_def chars[] = {
    {.uuid = &capabilities_uuid.u, .access_cb = capabilities_read,
     .flags = BLE_GATT_CHR_F_READ},
    {.uuid = &control_uuid.u, .access_cb = control_access,
     .flags = BLE_GATT_CHR_F_WRITE | BLE_GATT_CHR_F_WRITE_AUTHEN},
    {.uuid = &state_uuid.u, .access_cb = state_access,
     .flags = BLE_GATT_CHR_F_READ | BLE_GATT_CHR_F_READ_AUTHEN},
    {0},
};
static const struct ble_gatt_svc_def services[] = {
    {.type = BLE_GATT_SVC_TYPE_PRIMARY, .uuid = &service_uuid.u,
     .characteristics = chars},
    {0},
};

static uint8_t own_addr_type;
static uint16_t phone_conn = BLE_HS_CONN_HANDLE_NONE;
static atomic_uint pairing_conn;

static int phone_gap_event(struct ble_gap_event *event, void *arg);

static void advertise(void)
{
    if (phone_conn != BLE_HS_CONN_HANDLE_NONE || ble_gap_adv_active()) return;
    struct ble_hs_adv_fields fields = {0};
    fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
    fields.uuids128 = (ble_uuid128_t *)&service_uuid;
    fields.num_uuids128 = 1;
    fields.uuids128_is_complete = 1;
    int rc = ble_gap_adv_set_fields(&fields);
    if (rc != 0) { ESP_LOGW(TAG, "Advertisement fields failed: %d", rc); return; }
    struct ble_hs_adv_fields response = {0};
    const char *name = ble_svc_gap_device_name();
    response.name = (uint8_t *)name;
    response.name_len = strlen(name);
    response.name_is_complete = 1;
    rc = ble_gap_adv_rsp_set_fields(&response);
    if (rc != 0) { ESP_LOGW(TAG, "Scan response failed: %d", rc); return; }
    struct ble_gap_adv_params params = {0};
    params.conn_mode = BLE_GAP_CONN_MODE_UND;
    params.disc_mode = BLE_GAP_DISC_MODE_GEN;
    rc = ble_gap_adv_start(own_addr_type, NULL, BLE_HS_FOREVER,
                           &params, phone_gap_event, NULL);
    if (rc != 0) ESP_LOGW(TAG, "Advertising failed: %d", rc);
}

static int phone_gap_event(struct ble_gap_event *event, void *arg)
{
    (void)arg;
    switch (event->type) {
    case BLE_GAP_EVENT_CONNECT:
        if (event->connect.status == 0) {
            phone_conn = event->connect.conn_handle;
            ESP_LOGI(TAG, "Phone link connected");
        } else advertise();
        break;
    case BLE_GAP_EVENT_DISCONNECT:
        if (event->disconnect.conn.conn_handle == phone_conn) {
            phone_conn = BLE_HS_CONN_HANDLE_NONE;
            atomic_store(&pairing_conn, BLE_HS_CONN_HANDLE_NONE);
            ESP_LOGI(TAG, "Phone discovery link disconnected");
            ui_show_pairing_code(g_ui, pairing_open() ? UINT32_MAX : 0);
            advertise();
        }
        break;
    case BLE_GAP_EVENT_ADV_COMPLETE:
        advertise();
        break;
    case BLE_GAP_EVENT_PASSKEY_ACTION: {
        struct ble_sm_io io = {0};
        if (event->passkey.params.action != BLE_SM_IOACT_DISP ||
            atomic_load(&g_has_owner) || !pairing_open()) return BLE_HS_EAUTHEN;
        io.action = BLE_SM_IOACT_DISP;
        io.passkey = 100000 + esp_random() % 900000;
        int rc = ble_sm_inject_io(event->passkey.conn_handle, &io);
        if (rc == 0) {
            atomic_store(&pairing_conn, event->passkey.conn_handle);
            ui_show_pairing_code(g_ui, io.passkey);
        }
        return rc;
    }
    case BLE_GAP_EVENT_ENC_CHANGE:
        if (event->enc_change.status == 0) {
            struct ble_gap_conn_desc desc;
            if (ble_gap_conn_find(event->enc_change.conn_handle, &desc) == 0 &&
                desc.sec_state.encrypted && desc.sec_state.authenticated && desc.sec_state.bonded) {
                if (!atomic_load(&g_has_owner)) {
                    if (!pairing_open() || event->enc_change.conn_handle != atomic_load(&pairing_conn) ||
                        !save_owner(&desc.peer_id_addr))
                        ble_gap_terminate(event->enc_change.conn_handle, BLE_ERR_REM_USER_CONN_TERM);
                } else if (atomic_load(&g_has_owner) && !address_equal(&desc.peer_id_addr, &g_owner)) {
                    ble_gap_terminate(event->enc_change.conn_handle, BLE_ERR_REM_USER_CONN_TERM);
                }
                if (atomic_load(&g_has_owner)) atomic_store(&g_pairing_until, 0);
            }
        }
        ui_show_pairing_code(g_ui, 0);
        break;
    default:
        break;
    }
    return 0;
}

int ble_companion_register(void)
{
    load_owner();
    ble_svc_gap_init();
    ble_svc_gatt_init();
    int rc = ble_svc_gap_device_name_set("eGauge");
    if (rc == 0) rc = ble_gatts_count_cfg(services);
    if (rc == 0) rc = ble_gatts_add_svcs(services);
    if (rc != 0) ESP_LOGE(TAG, "GATT registration failed: %d", rc);
    return rc;
}

void ble_companion_reset(void)
{
    phone_conn = BLE_HS_CONN_HANDLE_NONE;
    atomic_store(&pairing_conn, BLE_HS_CONN_HANDLE_NONE);
}

void ble_companion_set_control(ui_t *ui, QueueHandle_t selection_queue, uint8_t selected_index)
{
    g_ui = ui;
    g_selection_queue = selection_queue;
    atomic_store(&g_selected_index, selected_index);
}

void ble_companion_open_pairing_window(void)
{
    if (atomic_load(&g_has_owner)) return;
    atomic_store(&pairing_conn, BLE_HS_CONN_HANDLE_NONE);
    atomic_store(&g_pairing_until, xTaskGetTickCount() + pdMS_TO_TICKS(120000));
    ui_show_pairing_code(g_ui, UINT32_MAX);
}

void ble_companion_selection_applied(uint8_t selected_index)
{
    atomic_store(&g_selected_index, selected_index);
    atomic_fetch_add(&g_revision, 1);
}

void ble_companion_tick(void)
{
    if (atomic_load(&g_pairing_until) && !pairing_open()) {
        atomic_store(&g_pairing_until, 0);
        ui_show_pairing_code(g_ui, 0);
    }
}

void ble_companion_forget_owner(void)
{
    if (!atomic_load(&g_has_owner)) return;
    int rc = ble_store_util_delete_peer(&g_owner);
    if (rc != 0) {
        ESP_LOGW(TAG, "Owner bond deletion returned %d; clearing owner association", rc);
    }
    nvs_handle_t handle;
    if (nvs_open("eg_owner", NVS_READWRITE, &handle) != ESP_OK) return;
    esp_err_t err = nvs_erase_key(handle, "peer");
    if (err == ESP_OK) err = nvs_commit(handle);
    nvs_close(handle);
    if (err != ESP_OK) return;
    esp_restart();
}

void ble_companion_start(void)
{
    int rc = ble_hs_util_ensure_addr(0);
    if (rc == 0) rc = ble_hs_id_infer_auto(0, &own_addr_type);
    if (rc != 0) { ESP_LOGE(TAG, "BLE address setup failed: %d", rc); return; }
    advertise();
}
