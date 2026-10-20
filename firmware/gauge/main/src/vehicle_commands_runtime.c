#include "vehicle_commands_runtime.h"
#include "esp_random.h"
#include "esp_attr.h"
#include "nvs.h"
#include "transfer_gate.h"
#include "ble_mgr.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include <string.h>

/* CPU-only driver buffers and history stay out of scarce internal Wi-Fi RAM. */
static EXT_RAM_BSS_ATTR vehicle_commands_t commands;
static EXT_RAM_BSS_ATTR uint8_t            control_response[ELM_RESPONSE_CAPACITY + 1];
static uint8_t                             cached_status[2][32];
static portMUX_TYPE                        cache_lock = portMUX_INITIALIZER_UNLOCKED;
static StaticQueue_t                       queue_storage;
static uint8_t                             queue_bytes[4 * sizeof(vehicle_command_request_t)];
static QueueHandle_t                       requests;
static uint32_t                            u32(const uint8_t *p)
{
    return p[0] | (uint32_t)p[1] << 8 | (uint32_t)p[2] << 16 | (uint32_t)p[3] << 24;
}
static void put32(uint8_t *p, uint32_t n)
{
    for (unsigned i = 0; i < 4; ++i)
        p[i] = n >> (8 * i);
}
static bool save(const vehicle_command_marker_t *marker, void *unused)
{
    (void)unused;
    uint8_t bytes[28] = {1, marker->mask};
    for (unsigned i = 0; i < 2; ++i)
    {
        put32(bytes + 4 + i * 12, marker->operation[i]);
        put32(bytes + 8 + i * 12, marker->revision[i]);
        put32(bytes + 12 + i * 12, marker->identity[i]);
    }
    nvs_handle_t handle;
    if (nvs_open("veh_commands", NVS_READWRITE, &handle) != ESP_OK)
        return false;
    esp_err_t err = nvs_set_blob(handle, "intent_v1", bytes, sizeof(bytes));
    if (err == ESP_OK)
        err = nvs_commit(handle);
    nvs_close(handle);
    return err == ESP_OK;
}
static void publish(vehicle_command_context_t context, uint32_t now)
{
    uint8_t snapshot[2][32];
    for (unsigned i = 0; i < 2; ++i)
        vehicle_commands_status(&commands, i + 1, context, now, snapshot[i]);
    portENTER_CRITICAL(&cache_lock);
    memcpy(cached_status, snapshot, sizeof(snapshot));
    portEXIT_CRITICAL(&cache_lock);
}
esp_err_t vehicle_commands_runtime_init(void)
{
    vehicle_command_marker_t marker = {0};
    bool                     ready  = true;
    nvs_handle_t             handle;
    esp_err_t                opened = nvs_open("veh_commands", NVS_READONLY, &handle);
    if (opened == ESP_OK)
    {
        uint8_t   bytes[28];
        size_t    n      = sizeof(bytes);
        esp_err_t loaded = nvs_get_blob(handle, "intent_v1", bytes, &n);
        if (loaded == ESP_OK)
        {
            ready = n == sizeof(bytes) && bytes[0] == 1 && !(bytes[1] & ~3U) && !bytes[2] && !bytes[3];
            if (ready)
            {
                marker.mask = bytes[1];
                for (unsigned i = 0; i < 2; ++i)
                {
                    marker.operation[i] = u32(bytes + 4 + i * 12);
                    marker.revision[i]  = u32(bytes + 8 + i * 12);
                    marker.identity[i]  = u32(bytes + 12 + i * 12);
                    if ((marker.mask & (1U << i)) &&
                        (!marker.operation[i] || !marker.revision[i] || !marker.identity[i]))
                        ready = false;
                }
            }
        }
        else if (loaded != ESP_ERR_NVS_NOT_FOUND)
            ready = false;
        nvs_close(handle);
    }
    else if (opened != ESP_ERR_NVS_NOT_FOUND)
        ready = false;
    uint32_t boot = esp_random();
    if (!boot)
        boot = 1;
    vehicle_commands_init(&commands, boot, save, NULL, &marker, ready);
    requests = xQueueCreateStatic(4, sizeof(vehicle_command_request_t), queue_bytes, &queue_storage);
    publish((vehicle_command_context_t){0}, 0);
    return requests ? ESP_OK : ESP_ERR_NO_MEM;
}
bool vehicle_commands_runtime_submit(const uint8_t *bytes, size_t length)
{
    vehicle_command_request_t request;
    if (!requests || !transfer_gate_vehicle_allowed() || ble_mgr_is_paused() ||
        !vehicle_command_decode(bytes, length, &request))
        return false;
    /* A readable interface is not an enabled controller. Fail closed at
     * admission so old/unqualified profiles never acquire a worker lease. */
    uint8_t status[32];
    vehicle_commands_runtime_status(request.kind, status);
    if (!(status[27] & 1U))
        return false;
    return xQueueSend(requests, &request, 0) == pdTRUE;
}
size_t vehicle_commands_runtime_status(uint8_t kind, uint8_t out[32])
{
    if (kind < 1 || kind > 2 || !out)
        return 0;
    portENTER_CRITICAL(&cache_lock);
    memcpy(out, cached_status[kind - 1], 32);
    portEXIT_CRITICAL(&cache_lock);
    return 32;
}
bool vehicle_commands_runtime_worker(unsigned                  source,
                                     ble_obd_ctx_t            *adapter,
                                     vehicle_command_context_t context,
                                     uint32_t                  now)
{
    if (source != 0 || !requests)
        return false;
    /* Share the existing atomic transfer gate. A cached phase check alone has a
     * check/claim race with configuration, OTA and Wi-Fi startup. Keep ownership
     * through active control, including gaps between adapter jobs. */
    if (transfer_gate_current() != TRANSFER_GATE_VEHICLE_COMMAND && !transfer_gate_claim(TRANSFER_GATE_VEHICLE_COMMAND))
        return false;
    vehicle_command_request_t request;
    /* Bounded drain: Stop is resolved before any next control step. */
    for (unsigned i = 0; i < 4 && xQueueReceive(requests, &request, 0) == pdTRUE; ++i)
        vehicle_commands_submit(&commands, request, context, now);
    bool                had_lease = commands.worker_kind != 0;
    jeep_idle_command_t command   = vehicle_commands_next(&commands, context, now);
    if (command.text)
    {
        uint8_t *response  = control_response;
        size_t   length    = sizeof(control_response);
        uint32_t before_io = (uint32_t)(xTaskGetTickCount() * portTICK_PERIOD_MS);
        uint32_t spent     = before_io - now;
        if (!adapter || spent >= JEEP_IDLE_IO_TIMEOUT_MS ||
            ble_obd_control_exchange(adapter, command.text, JEEP_IDLE_IO_TIMEOUT_MS - spent, response, &length) != 0)
            vehicle_commands_transport_failed(&commands, now);
        else
        {
            /* Use elapsed worker time, never the pre-IO timestamp. */
            uint32_t completed = (uint32_t)(xTaskGetTickCount() * portTICK_PERIOD_MS);
            vehicle_commands_feed(&commands, command.ticket, response, length, context, completed);
        }
    }
    if ((command.text || had_lease) && !commands.worker_kind && adapter &&
        ble_obd_control_restore(adapter, JEEP_IDLE_IO_TIMEOUT_MS, control_response, sizeof(control_response)) != 0)
    {
        context.connected = false;
        vehicle_commands_observe(&commands, context, (uint32_t)(xTaskGetTickCount() * portTICK_PERIOD_MS));
    }
    publish(context, (uint32_t)(xTaskGetTickCount() * portTICK_PERIOD_MS));
    bool reserved = commands.worker_kind != 0 || command.text != NULL;
    if (!commands.worker_kind && vehicle_commands_maintenance_allowed(commands.states, false))
        transfer_gate_release(TRANSFER_GATE_VEHICLE_COMMAND);
    return reserved;
}

bool vehicle_commands_runtime_intent(uint8_t kind, bool stop, uint8_t preset, uint16_t seconds)
{
    uint8_t status[32];
    if (!vehicle_commands_runtime_status(kind, status) || !(status[27] & 1U))
        return false;
    bool can = stop ? status[2] == VEHICLE_STARTING || status[2] == VEHICLE_ACTIVE
                    : status[2] == VEHICLE_READY || status[2] == VEHICLE_RESTORED || status[2] == VEHICLE_REJECTED;
    if (!can || (!stop && u32(status + 28) == UINT32_MAX))
        return false;
    uint8_t bytes[24] = {1, kind, stop ? VEHICLE_STOP : VEHICLE_START, preset};
    put32(bytes + 4, stop ? u32(status + 4) : u32(status + 28) + 1);
    put32(bytes + 8, u32(status + 8));
    put32(bytes + 12, u32(status + 12));
    put32(bytes + 16, u32(status + 16));
    bytes[20] = seconds;
    bytes[21] = seconds >> 8;
    return vehicle_commands_runtime_submit(bytes, sizeof(bytes));
}

bool vehicle_commands_runtime_maintenance_allowed(bool configuration)
{
    if (requests && uxQueueMessagesWaiting(requests))
        return false;
    vehicle_command_state_t values[2] = {0};
    portENTER_CRITICAL(&cache_lock);
    for (unsigned i = 0; i < 2; ++i)
        values[i].phase = cached_status[i][2];
    portEXIT_CRITICAL(&cache_lock);
    return vehicle_commands_maintenance_allowed(values, configuration);
}
