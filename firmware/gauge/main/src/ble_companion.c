#include <stdint.h>
#include <string.h>

#include "esp_log.h"
#include "host/ble_gap.h"
#include "host/ble_gatt.h"
#include "host/ble_hs.h"
#include "host/ble_hs_adv.h"
#include "host/ble_uuid.h"
#include "host/util/util.h"
#include "os/os_mbuf.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"
#include "ble_companion.h"

static const char *TAG = "COMPANION";
static const ble_uuid128_t service_uuid = BLE_UUID128_INIT(
    0x00, 0x6c, 0x3b, 0xd2, 0xc9, 0x69, 0x14, 0xa7,
    0x45, 0x4f, 0x3b, 0x9e, 0x00, 0x00, 0x1a, 0x6f);
static const ble_uuid128_t capabilities_uuid = BLE_UUID128_INIT(
    0x00, 0x6c, 0x3b, 0xd2, 0xc9, 0x69, 0x14, 0xa7,
    0x45, 0x4f, 0x3b, 0x9e, 0x01, 0x00, 0x1a, 0x6f);

/* Protocol 0 is an explicit experimental read-only surface. It makes no
 * promise about configuration, DTC clearing, telemetry, or update operations. */
static const char capabilities[] =
    "{\"protocolMajor\":0,\"board\":\"ESP32-S3-Touch-LCD-1.28\","
    "\"maxAdapterLinks\":2,\"simultaneousAdapterLinksVerified\":false,"
    "\"configWrite\":false,\"ota\":false}";

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
    {0},
};
static const struct ble_gatt_svc_def services[] = {
    {.type = BLE_GATT_SVC_TYPE_PRIMARY, .uuid = &service_uuid.u,
     .characteristics = chars},
    {0},
};

static uint8_t own_addr_type;
static uint16_t phone_conn = BLE_HS_CONN_HANDLE_NONE;

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
            ESP_LOGI(TAG, "Phone discovery link connected");
        } else advertise();
        break;
    case BLE_GAP_EVENT_DISCONNECT:
        if (event->disconnect.conn.conn_handle == phone_conn) {
            phone_conn = BLE_HS_CONN_HANDLE_NONE;
            ESP_LOGI(TAG, "Phone discovery link disconnected");
            advertise();
        }
        break;
    case BLE_GAP_EVENT_ADV_COMPLETE:
        advertise();
        break;
    default:
        break;
    }
    return 0;
}

int ble_companion_register(void)
{
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
}

void ble_companion_start(void)
{
    int rc = ble_hs_util_ensure_addr(0);
    if (rc == 0) rc = ble_hs_id_infer_auto(0, &own_addr_type);
    if (rc != 0) { ESP_LOGE(TAG, "BLE address setup failed: %d", rc); return; }
    advertise();
}
