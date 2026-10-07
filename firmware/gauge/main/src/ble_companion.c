#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <stdatomic.h>
#include "sdkconfig.h"

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
#include "config.h"
#include "config_store.h"
#include "config_transfer.h"
#include "ota_transfer.h"
#include "wifi_bulk.h"
#include "adapter_registry.h"
#include "adapter_status.h"
#include "diagnostics_state.h"
#include "config_runtime.h"
#include "display_settings.h"
#include "hardware_probe.h"
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
static const ble_uuid128_t pairing_status_uuid = BLE_UUID128_INIT(
    0x00, 0x6c, 0x3b, 0xd2, 0xc9, 0x69, 0x14, 0xa7,
    0x45, 0x4f, 0x3b, 0x9e, 0x04, 0x00, 0x1a, 0x6f);

static ui_t *g_ui;
static QueueHandle_t g_command_queue;
static atomic_uchar g_selected_index;
static ble_addr_t g_owner;
static atomic_bool g_has_owner;
static atomic_uint g_pairing_until;
static atomic_uint pairing_conn;
static atomic_bool g_ready;
static uint8_t extended_status_mode;
static bool document_active;
typedef struct {
    ble_addr_t owner;
    uint16_t conn_handle;
} owner_save_request_t;
static QueueHandle_t owner_save_queue;
#define ACTIVE_DOCUMENT_HEADER_SIZE 52U
#define ACTIVE_DOCUMENT_CHUNK_SIZE 128U
#define BLE_OWNER_MAX_REQUEST 173U
static uint8_t status_snapshot[ACTIVE_DOCUMENT_HEADER_SIZE + ACTIVE_DOCUMENT_CHUNK_SIZE];
static size_t status_snapshot_length;
static uint32_t active_document_offset;

static uint32_t read_u32(const uint8_t *data)
{
    return (uint32_t)data[0] | ((uint32_t)data[1] << 8) |
           ((uint32_t)data[2] << 16) | ((uint32_t)data[3] << 24);
}

static void write_u32(uint8_t *data, uint32_t value)
{
    for (unsigned i = 0; i < 4; ++i) data[i] = (uint8_t)(value >> (8 * i));
}

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

bool ble_companion_load_owner(void)
{
    atomic_store(&g_has_owner, false);
    nvs_handle_t handle;
    esp_err_t opened = nvs_open("eg_owner", NVS_READONLY, &handle);
    if (opened != ESP_OK) {
        ESP_LOGI(TAG, "Owner association unavailable at boot: %s", esp_err_to_name(opened));
        return false;
    }
    size_t size = sizeof(g_owner);
    esp_err_t loaded = nvs_get_blob(handle, "peer", &g_owner, &size);
    atomic_store(&g_has_owner, loaded == ESP_OK && size == sizeof(g_owner));
    nvs_close(handle);
    ESP_LOGI(TAG, "Owner association at boot: %s (read %s)",
             atomic_load(&g_has_owner) ? "present" : "absent", esp_err_to_name(loaded));
    return atomic_load(&g_has_owner);
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

static void owner_save_worker(void *arg)
{
    (void)arg;
    owner_save_request_t request;
    for (;;) {
        if (xQueueReceive(owner_save_queue, &request, portMAX_DELAY) != pdTRUE) continue;
        if (!save_owner(&request.owner)) {
            ESP_LOGE(TAG, "Owner identity persistence failed");
            ble_gap_terminate(request.conn_handle, BLE_ERR_REM_USER_CONN_TERM);
            continue;
        }
        atomic_store(&g_pairing_until, 0);
        atomic_store(&pairing_conn, BLE_HS_CONN_HANDLE_NONE);
        ui_show_pairing_code(g_ui, UI_PAIRING_OWNER_SAVED);
        ESP_LOGI(TAG, "Owner identity persisted");
    }
}

/* Public protocol-0 capabilities advertise one bounded, protected selection
 * operation. Full configuration, diagnostics, and updates remain disabled. */
#if CONFIG_EGAUGE_WIFI_BULK_ENABLED
#define WIFI_BULK_CAPABILITY ",\"wifiBulk\":\"experimental-softap-aead-v2\""
#else
#define WIFI_BULK_CAPABILITY ""
#endif

#if CONFIG_EGAUGE_DISPLAY_SETTINGS_ENABLED
#define DISPLAY_SETTINGS_CAPABILITY ",\"ds\":3"
#else
#define DISPLAY_SETTINGS_CAPABILITY ""
#endif

static const char capabilities[] =
    "{\"protocolMajor\":0,\"board\":\"ESP32-S3-Touch-LCD-1.28\","
    "\"maxAdapterLinks\":2,"
    "\"savedStateRead\":true,\"displayRotationWrite\":true,"
    "\"configWrite\":false,\"cfg\":3,\"ad\":1,"
    "\"quickSelect\":true,\"ota\":false,\"hw\":1"
    DISPLAY_SETTINGS_CAPABILITY WIFI_BULK_CAPABILITY "}";
static const char document_capabilities[] =
    "{\"protocolMajor\":0,\"board\":\"ESP32-S3-Touch-LCD-1.28\","
    "\"maxAdapterLinks\":2,"
    "\"savedStateRead\":false,\"displayRotationWrite\":false,"
    "\"configWrite\":false,\"cfg\":3,\"ad\":1,"
    "\"ota\":false,\"hw\":1"
    DISPLAY_SETTINGS_CAPABILITY WIFI_BULK_CAPABILITY "}";
_Static_assert(sizeof(capabilities) - 1U <= 255U,
               "Public capability JSON exceeds the qualified Android read boundary");
_Static_assert(sizeof(document_capabilities) - 1U <= 255U,
               "Document capability JSON exceeds the qualified Android read boundary");

/* Protocol 0 quick-select request: byte 0 = 1, byte 1 = built-in PID index 0..4.
 * A successful ATT write queues a request; the authenticated state read confirms apply. */
static int control_access(uint16_t conn_handle, uint16_t attr_handle,
                          struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    (void)attr_handle;
    (void)arg;
    if (ctxt->op != BLE_GATT_ACCESS_OP_WRITE_CHR) return BLE_ATT_ERR_REQ_NOT_SUPPORTED;
    if (!authorized(conn_handle)) return BLE_ATT_ERR_INSUFFICIENT_AUTHEN;
    size_t length = OS_MBUF_PKTLEN(ctxt->om);
    if (length < 2 || length > BLE_OWNER_MAX_REQUEST)
        return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
    uint8_t request[BLE_OWNER_MAX_REQUEST] = {0};
    if (os_mbuf_copydata(ctxt->om, 0, length, request) != 0)
        return BLE_ATT_ERR_UNLIKELY;
    if (request[0] >= 0x10 && request[0] <= 0x17) {
        if (!config_transfer_command(request, length)) return BLE_ATT_ERR_UNLIKELY;
        extended_status_mode = 1;
        status_snapshot_length = 0;
        return 0;
    }
    if (request[0] >= 0x20 && request[0] <= 0x28) {
        if (!ota_transfer_command(request, length)) return BLE_ATT_ERR_UNLIKELY;
        extended_status_mode = 2;
        status_snapshot_length = 0;
        return 0;
    }
    if (request[0] == 0x30 && length == 5) {
        extended_status_mode = 3;
        status_snapshot_length = 0;
        return 0;
    }
    if (request[0] == 0x31 && length == 5) {
        extended_status_mode = 4;
        status_snapshot_length = 0;
        return 0;
    }
    if (request[0] == 0x32 && length == 9) {
        active_document_offset = read_u32(request + 5);
        extended_status_mode = 5;
        status_snapshot_length = 0;
        return 0;
    }
    if (request[0] == 0x33 && length == 5) {
        extended_status_mode = 6;
        status_snapshot_length = 0;
        return 0;
    }
    if (request[0] == 0x34 && length == 5) {
        extended_status_mode = 8;
        status_snapshot_length = 0;
        return 0;
    }
    if (request[0] == 0x35 && length == 5) {
        extended_status_mode = 9;
        status_snapshot_length = 0;
        return 0;
    }
    if (request[0] == 0x36 && length == 7) {
        companion_command_t command = {
            .opcode = 0x36, .value = request[1], .brightness = request[2],
            .base_revision = read_u32(request + 3),
        };
        if (command.value > 3 || command.brightness < 5 || command.brightness > 100)
            return BLE_ATT_ERR_VALUE_NOT_ALLOWED;
        if (!g_command_queue || xQueueSend(g_command_queue, &command, 0) != pdTRUE)
            return BLE_ATT_ERR_UNLIKELY;
        extended_status_mode = 9;
        status_snapshot_length = 0;
        return 0;
    }
    if (request[0] == 0x37 && length == 8) {
        companion_command_t command = {
            .opcode = 0x37, .value = request[1], .brightness = request[2],
            .units = request[3], .base_revision = read_u32(request + 4),
        };
        if (command.value > 3 || command.brightness < 5 || command.brightness > 100 ||
            command.units > 1) return BLE_ATT_ERR_VALUE_NOT_ALLOWED;
        if (!g_command_queue || xQueueSend(g_command_queue, &command, 0) != pdTRUE)
            return BLE_ATT_ERR_UNLIKELY;
        extended_status_mode = 9;
        status_snapshot_length = 0;
        return 0;
    }
    if (request[0] == 0x38 && length == 10) {
        companion_command_t command = {
            .opcode = 0x38, .value = request[1], .brightness = request[2],
            .units = request[3], .cycle_seconds = request[4] | ((uint16_t)request[5] << 8),
            .base_revision = read_u32(request + 6),
        };
        uint16_t interval = command.cycle_seconds;
        if (command.value > 3 || command.brightness < 5 || command.brightness > 100 ||
            command.units > 1 || (interval != 0 && interval != 5 && interval != 10 &&
            interval != 15 && interval != 30 && interval != 60)) return BLE_ATT_ERR_VALUE_NOT_ALLOWED;
        if (!g_command_queue || xQueueSend(g_command_queue, &command, 0) != pdTRUE)
            return BLE_ATT_ERR_UNLIKELY;
        extended_status_mode = 9;
        status_snapshot_length = 0;
        return 0;
    }
    if (request[0] == 0x40 || request[0] == 0x42) {
#if CONFIG_EGAUGE_WIFI_BULK_ENABLED
        if (!wifi_bulk_command(request, length)) return BLE_ATT_ERR_UNLIKELY;
        extended_status_mode = 7;
        status_snapshot_length = 0;
        return 0;
#else
        return BLE_ATT_ERR_REQ_NOT_SUPPORTED;
#endif
    }
    if (request[0] == 0x52 && length == 5) {
        extended_status_mode = 11;
        status_snapshot_length = 0;
        return 0;
    }
    if (request[0] == 0x50 || request[0] == 0x51) {
        if (!adapter_registry_command(request, length)) return BLE_ATT_ERR_UNLIKELY;
        extended_status_mode = 10;
        status_snapshot_length = 0;
        return 0;
    }
    if (document_active) return BLE_ATT_ERR_REQ_NOT_SUPPORTED;
    if (length != 2 && length != 6) return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
    companion_command_t command = {.opcode = request[0], .value = request[1]};
    if (command.opcode == 1 && (length != 2 || command.value > 4))
        return BLE_ATT_ERR_VALUE_NOT_ALLOWED;
    if (command.opcode == 2 && (length != 6 || command.value > 3))
        return BLE_ATT_ERR_VALUE_NOT_ALLOWED;
    if (command.opcode != 1 && command.opcode != 2)
        return BLE_ATT_ERR_VALUE_NOT_ALLOWED;
    if (command.opcode == 2) {
        command.base_revision = (uint32_t)request[2] | ((uint32_t)request[3] << 8) |
                                ((uint32_t)request[4] << 16) | ((uint32_t)request[5] << 24);
    }
    if (g_command_queue == NULL || xQueueSend(g_command_queue, &command, 0) != pdTRUE)
        return BLE_ATT_ERR_UNLIKELY;
    extended_status_mode = 0;
    status_snapshot_length = 0;
    return 0;
}

static int state_access(uint16_t conn_handle, uint16_t attr_handle,
                        struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    (void)attr_handle;
    (void)arg;
    if (ctxt->op != BLE_GATT_ACCESS_OP_READ_CHR) return BLE_ATT_ERR_REQ_NOT_SUPPORTED;
    if (!authorized(conn_handle)) return BLE_ATT_ERR_INSUFFICIENT_AUTHEN;
    if (extended_status_mode == 11) {
        if (ctxt->offset == 0 || status_snapshot_length == 0)
            status_snapshot_length = adapter_status_snapshot(status_snapshot);
        if (ctxt->offset > status_snapshot_length) return BLE_ATT_ERR_INVALID_OFFSET;
        return os_mbuf_append(ctxt->om, status_snapshot + ctxt->offset,
                              status_snapshot_length - ctxt->offset) == 0
            ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
    }
    if (extended_status_mode == 10) {
        if (ctxt->offset == 0 || status_snapshot_length == 0)
            status_snapshot_length = adapter_registry_status(status_snapshot);
        if (ctxt->offset > status_snapshot_length) return BLE_ATT_ERR_INVALID_OFFSET;
        return os_mbuf_append(ctxt->om, status_snapshot + ctxt->offset,
                              status_snapshot_length - ctxt->offset) == 0
            ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
    }
    if (extended_status_mode == 1) {
        if (ctxt->offset == 0 || status_snapshot_length == 0)
            status_snapshot_length = config_transfer_status(status_snapshot);
        if (ctxt->offset > status_snapshot_length) return BLE_ATT_ERR_INVALID_OFFSET;
        return os_mbuf_append(ctxt->om, status_snapshot + ctxt->offset,
                              status_snapshot_length - ctxt->offset) == 0
            ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
    }
    if (extended_status_mode == 2) {
        if (ctxt->offset == 0 || status_snapshot_length == 0)
            status_snapshot_length = ota_transfer_status(status_snapshot);
        if (ctxt->offset > status_snapshot_length) return BLE_ATT_ERR_INVALID_OFFSET;
        return os_mbuf_append(ctxt->om, status_snapshot + ctxt->offset,
                              status_snapshot_length - ctxt->offset) == 0
            ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
    }
    if (extended_status_mode == 3) {
        if (ctxt->offset == 0 || status_snapshot_length == 0)
            status_snapshot_length = diagnostics_state_status(status_snapshot);
        if (ctxt->offset > status_snapshot_length) return BLE_ATT_ERR_INVALID_OFFSET;
        return os_mbuf_append(ctxt->om, status_snapshot + ctxt->offset,
                              status_snapshot_length - ctxt->offset) == 0
            ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
    }
    if (extended_status_mode == 4) {
        if (ctxt->offset == 0 || status_snapshot_length == 0)
            status_snapshot_length = ota_transfer_boot_identity(status_snapshot);
        if (ctxt->offset > status_snapshot_length) return BLE_ATT_ERR_INVALID_OFFSET;
        return os_mbuf_append(ctxt->om, status_snapshot + ctxt->offset,
                              status_snapshot_length - ctxt->offset) == 0
            ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
    }
    if (extended_status_mode == 5) {
        if (ctxt->offset == 0 || status_snapshot_length == 0) {
            memset(status_snapshot, 0, ACTIVE_DOCUMENT_HEADER_SIZE);
            status_snapshot[0] = 7;
            config_store_record_t record;
            esp_err_t err = config_store_active(&record);
            if (err == ESP_OK) {
                if (active_document_offset > record.length) return BLE_ATT_ERR_INVALID_OFFSET;
                size_t count = record.length - active_document_offset;
                if (count > ACTIVE_DOCUMENT_CHUNK_SIZE) count = ACTIVE_DOCUMENT_CHUNK_SIZE;
                status_snapshot[1] = 1;
                write_u32(status_snapshot + 4, record.revision);
                write_u32(status_snapshot + 8, record.length);
                write_u32(status_snapshot + 12, active_document_offset);
                status_snapshot[16] = (uint8_t)count;
                memcpy(status_snapshot + 20, record.sha256, sizeof(record.sha256));
                if (count > 0 && config_store_read_active(active_document_offset,
                        status_snapshot + ACTIVE_DOCUMENT_HEADER_SIZE, count) != ESP_OK)
                    return BLE_ATT_ERR_UNLIKELY;
                config_store_record_t after;
                if (config_store_active(&after) != ESP_OK ||
                    after.revision != record.revision ||
                    memcmp(after.sha256, record.sha256, sizeof(record.sha256)) != 0)
                    return BLE_ATT_ERR_UNLIKELY;
                status_snapshot_length = ACTIVE_DOCUMENT_HEADER_SIZE + count;
            } else if (err == ESP_ERR_NOT_FOUND && active_document_offset == 0) {
                status_snapshot_length = ACTIVE_DOCUMENT_HEADER_SIZE;
            } else return BLE_ATT_ERR_UNLIKELY;
        }
        if (ctxt->offset > status_snapshot_length) return BLE_ATT_ERR_INVALID_OFFSET;
        return os_mbuf_append(ctxt->om, status_snapshot + ctxt->offset,
                              status_snapshot_length - ctxt->offset) == 0
            ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
    }
    if (extended_status_mode == 6) {
        if (ctxt->offset == 0 || status_snapshot_length == 0)
            status_snapshot_length = config_runtime_status(status_snapshot);
        if (ctxt->offset > status_snapshot_length) return BLE_ATT_ERR_INVALID_OFFSET;
        return os_mbuf_append(ctxt->om, status_snapshot + ctxt->offset,
                              status_snapshot_length - ctxt->offset) == 0
            ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
    }
    if (extended_status_mode == 7) {
#if CONFIG_EGAUGE_WIFI_BULK_ENABLED
        if (ctxt->offset == 0 || status_snapshot_length == 0)
            status_snapshot_length = wifi_bulk_status(status_snapshot);
        if (ctxt->offset > status_snapshot_length) return BLE_ATT_ERR_INVALID_OFFSET;
        return os_mbuf_append(ctxt->om, status_snapshot + ctxt->offset,
                              status_snapshot_length - ctxt->offset) == 0
            ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
#else
        return BLE_ATT_ERR_REQ_NOT_SUPPORTED;
#endif
    }
    if (extended_status_mode == 8) {
        hardware_probe_set_ready(HARDWARE_FEATURE_BLE, ble_companion_ready());
        if (ctxt->offset == 0 || status_snapshot_length == 0)
            status_snapshot_length = hardware_probe_status(status_snapshot);
        if (ctxt->offset > status_snapshot_length) return BLE_ATT_ERR_INVALID_OFFSET;
        return os_mbuf_append(ctxt->om, status_snapshot + ctxt->offset,
                              status_snapshot_length - ctxt->offset) == 0
            ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
    }
    if (extended_status_mode == 9) {
        display_settings_t settings = display_settings_snapshot();
        uint8_t state[] = {12, settings.rotation, settings.brightness, settings.units,
                           (uint8_t)settings.revision, (uint8_t)(settings.revision >> 8),
                           (uint8_t)(settings.revision >> 16), (uint8_t)(settings.revision >> 24),
                           (uint8_t)settings.cycle_seconds, (uint8_t)(settings.cycle_seconds >> 8)};
        if (ctxt->offset > sizeof(state)) return BLE_ATT_ERR_INVALID_OFFSET;
        return os_mbuf_append(ctxt->om, state + ctxt->offset,
                              sizeof(state) - ctxt->offset) == 0
            ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
    }
    if (document_active) {
        /* Keep a protected state read available after custom activation so
         * Android can restore link encryption before its first owner write. */
        if (ctxt->offset == 0 || status_snapshot_length == 0)
            status_snapshot_length = config_transfer_status(status_snapshot);
        if (ctxt->offset > status_snapshot_length) return BLE_ATT_ERR_INVALID_OFFSET;
        return os_mbuf_append(ctxt->om, status_snapshot + ctxt->offset,
                              status_snapshot_length - ctxt->offset) == 0
            ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
    }
    config_t saved;
    uint32_t revision;
    if (config_read_snapshot(&saved, &revision) != ESP_OK || saved.cfg_idx > 4 ||
        saved.disp_rot > LV_DISPLAY_ROTATION_270) return BLE_ATT_ERR_UNLIKELY;
    /* Version 2 extends the original state value on the same GATT handle.
     * Applied and saved indices may briefly differ during UI activation. */
    uint8_t state[] = {2, atomic_load(&g_selected_index), saved.cfg_idx,
                       (uint8_t)saved.disp_rot, (uint8_t)revision,
                       (uint8_t)(revision >> 8), (uint8_t)(revision >> 16),
                       (uint8_t)(revision >> 24)};
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
    const char *value = document_active ? document_capabilities : capabilities;
    size_t length = strlen(value);
    if (ctxt->offset > length) return BLE_ATT_ERR_INVALID_OFFSET;
    return os_mbuf_append(ctxt->om, value + ctxt->offset,
                          length - ctxt->offset) == 0 ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
}

static int pairing_status_read(uint16_t conn_handle, uint16_t attr_handle,
                               struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    (void)conn_handle;
    (void)attr_handle;
    (void)arg;
    if (ctxt->op != BLE_GATT_ACCESS_OP_READ_CHR) return BLE_ATT_ERR_REQ_NOT_SUPPORTED;

    unsigned until = atomic_load(&g_pairing_until);
    int32_t ticks_left = until ? (int32_t)(until - xTaskGetTickCount()) : 0;
    uint16_t seconds_left = ticks_left > 0
        ? (uint16_t)((pdTICKS_TO_MS((uint32_t)ticks_left) + 999U) / 1000U) : 0;
    uint8_t state = 0;
    if (atomic_load(&g_has_owner)) state = 3;
    else if (seconds_left > 0)
        state = atomic_load(&pairing_conn) == BLE_HS_CONN_HANDLE_NONE ? 1 : 2;
    uint8_t status[] = {1, state, (uint8_t)seconds_left, (uint8_t)(seconds_left >> 8)};
    if (ctxt->offset > sizeof(status)) return BLE_ATT_ERR_INVALID_OFFSET;
    return os_mbuf_append(ctxt->om, status + ctxt->offset,
                          sizeof(status) - ctxt->offset) == 0 ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
}

static const struct ble_gatt_chr_def chars[] = {
    {.uuid = &capabilities_uuid.u, .access_cb = capabilities_read,
     .flags = BLE_GATT_CHR_F_READ},
    {.uuid = &control_uuid.u, .access_cb = control_access,
     .flags = BLE_GATT_CHR_F_WRITE | BLE_GATT_CHR_F_WRITE_AUTHEN},
    {.uuid = &state_uuid.u, .access_cb = state_access,
     .flags = BLE_GATT_CHR_F_READ | BLE_GATT_CHR_F_READ_AUTHEN},
    {.uuid = &pairing_status_uuid.u, .access_cb = pairing_status_read,
     .flags = BLE_GATT_CHR_F_READ},
    {0},
};
static const struct ble_gatt_svc_def services[] = {
    {.type = BLE_GATT_SVC_TYPE_PRIMARY, .uuid = &service_uuid.u,
     .characteristics = chars},
    {0},
};

static uint8_t own_addr_type;
static bool database_announced;
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
    else atomic_store(&g_ready, true);
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
            extended_status_mode = 0;
            status_snapshot_length = 0;
            active_document_offset = 0;
            config_transfer_disconnect();
            atomic_store(&pairing_conn, BLE_HS_CONN_HANDLE_NONE);
            ESP_LOGI(TAG, "Phone discovery link disconnected");
            ui_show_pairing_code(g_ui, atomic_load(&g_has_owner) ? UI_PAIRING_HIDDEN :
                                pairing_open() ? UI_PAIRING_READY : UI_PAIRING_WAITING);
            advertise();
        }
        break;
    case BLE_GAP_EVENT_ADV_COMPLETE:
        advertise();
        break;
    case BLE_GAP_EVENT_REPEAT_PAIRING: {
        /* Android may have forgotten a bond that NimBLE still stores. Only an
         * open pairing window with no owner may replace that stale bond. */
        struct ble_gap_conn_desc desc;
        if (atomic_load(&g_has_owner) || !pairing_open() ||
            ble_gap_conn_find(event->repeat_pairing.conn_handle, &desc) != 0)
            return BLE_GAP_REPEAT_PAIRING_IGNORE;
        int rc = ble_store_util_delete_peer(&desc.peer_id_addr);
        ESP_LOGI(TAG, "Replacing stale unowned bond during physical pairing: %d", rc);
        return rc == 0 ? BLE_GAP_REPEAT_PAIRING_RETRY : BLE_GAP_REPEAT_PAIRING_IGNORE;
    }
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
            ESP_LOGI(TAG, "Passkey displayed for active owner pairing");
        }
        return rc;
    }
    case BLE_GAP_EVENT_ENC_CHANGE:
        if (event->enc_change.status == 0) {
            struct ble_gap_conn_desc desc;
            if (ble_gap_conn_find(event->enc_change.conn_handle, &desc) == 0 &&
                desc.sec_state.encrypted && desc.sec_state.authenticated && desc.sec_state.bonded) {
                if (!atomic_load(&g_has_owner)) {
                    owner_save_request_t request = {
                        .owner = desc.peer_id_addr,
                        .conn_handle = event->enc_change.conn_handle,
                    };
                    if (!pairing_open() ||
                        event->enc_change.conn_handle != atomic_load(&pairing_conn) ||
                        !owner_save_queue ||
                        xQueueSend(owner_save_queue, &request, 0) != pdTRUE)
                        ble_gap_terminate(event->enc_change.conn_handle, BLE_ERR_REM_USER_CONN_TERM);
                } else if (atomic_load(&g_has_owner) && !address_equal(&desc.peer_id_addr, &g_owner)) {
                    ble_gap_terminate(event->enc_change.conn_handle, BLE_ERR_REM_USER_CONN_TERM);
                }
                if (atomic_load(&g_has_owner)) atomic_store(&g_pairing_until, 0);
            }
        }
        if (atomic_load(&g_has_owner)) ui_show_pairing_code(g_ui, UI_PAIRING_HIDDEN);
        break;
    default:
        break;
    }
    return 0;
}

int ble_companion_register(void)
{
    if (!owner_save_queue) {
        owner_save_queue = xQueueCreate(1, sizeof(owner_save_request_t));
        if (!owner_save_queue ||
            xTaskCreate(owner_save_worker, "owner_store", 3072, NULL, 4, NULL) != pdPASS) {
            ESP_LOGE(TAG, "Owner persistence worker creation failed");
            return BLE_HS_ENOMEM;
        }
    }
    ble_svc_gap_init();
    ble_svc_gatt_init();
    int rc = ble_svc_gap_device_name_set("eGauge");
    if (rc == 0) rc = ble_gatts_count_cfg(services);
    if (rc == 0) rc = ble_gatts_add_svcs(services);
    if (rc != 0) ESP_LOGE(TAG, "GATT registration failed: %d", rc);
    else ble_companion_open_pairing_window();
    return rc;
}

void ble_companion_reset(void)
{
    database_announced = false;
    atomic_store(&g_ready, false);
    phone_conn = BLE_HS_CONN_HANDLE_NONE;
    extended_status_mode = 0;
    status_snapshot_length = 0;
    atomic_store(&pairing_conn, BLE_HS_CONN_HANDLE_NONE);
}

bool ble_companion_ready(void)
{
    return atomic_load(&g_ready);
}

bool ble_companion_has_owner(void)
{
    return atomic_load(&g_has_owner);
}

void ble_companion_set_control(ui_t *ui, QueueHandle_t command_queue,
                               uint8_t selected_index, bool custom)
{
    g_ui = ui;
    g_command_queue = command_queue;
    atomic_store(&g_selected_index, selected_index);
    document_active = custom;
}

void ble_companion_open_pairing_window(void)
{
    if (atomic_load(&g_has_owner)) return;
    atomic_store(&pairing_conn, BLE_HS_CONN_HANDLE_NONE);
    atomic_store(&g_pairing_until, xTaskGetTickCount() + pdMS_TO_TICKS(120000));
    ESP_LOGI(TAG, "Owner pairing window opened");
    ui_show_pairing_code(g_ui, UI_PAIRING_READY);
}

void ble_companion_selection_applied(uint8_t selected_index)
{
    atomic_store(&g_selected_index, selected_index);
}

void ble_companion_tick(void)
{
    if (atomic_load(&g_pairing_until) && !pairing_open()) {
        atomic_store(&g_pairing_until, 0);
        ui_show_pairing_code(g_ui, atomic_load(&g_has_owner) ?
                            UI_PAIRING_HIDDEN : UI_PAIRING_WAITING);
    }
}

void ble_companion_forget_owner(void)
{
    if (!atomic_load(&g_has_owner)) return;
    ESP_LOGW(TAG, "Physical 12-second hold requested owner reset");
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
    uint8_t address[6];
    if (ble_hs_id_copy_addr(own_addr_type, address, NULL) == 0) {
        char identifier[7];
        snprintf(identifier, sizeof(identifier), "%02X%02X%02X",
                 address[2], address[1], address[0]);
        ui_set_pairing_identifier(g_ui, identifier);
        char device_name[16];
        snprintf(device_name, sizeof(device_name), "eGauge-%s", identifier);
        int name_rc = ble_svc_gap_device_name_set(device_name);
        if (name_rc != 0) ESP_LOGW(TAG, "Unique gauge name setup failed: %d", name_rc);
    } else {
        ESP_LOGW(TAG, "BLE identity unavailable for short pairing ID");
    }
    if (!database_announced) {
        database_announced = true;
        ble_svc_gatt_changed(1, 0xffff);
    }
    advertise();
}
