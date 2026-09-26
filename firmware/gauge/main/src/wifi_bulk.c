#include <stdbool.h>
#include <stdint.h>
#include <stdatomic.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include "esp_event.h"
#include "esp_heap_caps.h"
#include "esp_log.h"
#include "esp_mac.h"
#include "esp_netif.h"
#include "esp_random.h"
#include "esp_wifi.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "lwip/inet.h"
#include "lwip/sockets.h"
#include "mbedtls/gcm.h"
#include "config_transfer.h"
#include "ota_transfer.h"
#include "wifi_bulk.h"

#define OP_OPEN 0x40
#define OP_CLOSE 0x42
#define PORT 7331
#define SESSION_MS 600000U
#define FRAME_HEADER 16U
#define FRAME_TAG 16U
#define OTA_BATCH_MAX_COMMANDS 8U
#define FRAME_MAX_PLAINTEXT 8448U
#define COMMAND_STATUS_POLL_TICKS 1U
#define COMMAND_STATUS_TIMEOUT_TICKS pdMS_TO_TICKS(60000)

enum { PHASE_OFF, PHASE_STARTING, PHASE_READY, PHASE_FAILED };
enum { RESULT_OK, RESULT_PENDING, RESULT_INVALID, RESULT_NETWORK };

typedef struct {
    uint8_t op;
    uint32_t sequence;
} command_t;

static const char *TAG = "wifi_bulk";
static QueueHandle_t command_queue;
static SemaphoreHandle_t state_lock;
static esp_netif_t *ap_netif;
static bool wifi_initialized;
static bool server_started;
static atomic_bool wifi_active;
static struct {
    uint8_t phase;
    uint8_t result;
    uint8_t last_op;
    uint32_t sequence;
    uint8_t ip[4];
    uint32_t session_id;
    uint8_t key[32];
    uint32_t expires_at;
    uint32_t last_frame_sequence;
    char ssid[33];
    char password[17];
} state;
static uint8_t status_cache[WIFI_BULK_STATUS_SIZE];

static uint32_t read_u32(const uint8_t *p)
{
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) |
           ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

static void write_u16(uint8_t *p, uint16_t value)
{
    p[0] = value;
    p[1] = value >> 8;
}

static uint16_t read_u16(const uint8_t *p)
{
    return (uint16_t)p[0] | ((uint16_t)p[1] << 8);
}

static void write_u32(uint8_t *p, uint32_t value)
{
    for (unsigned i = 0; i < 4; ++i) p[i] = value >> (8 * i);
}

static void publish_locked(void)
{
    memset(status_cache, 0, sizeof(status_cache));
    status_cache[0] = 9;
    status_cache[1] = state.phase;
    status_cache[2] = state.result;
    status_cache[3] = state.last_op;
    write_u32(status_cache + 4, state.sequence);
    memcpy(status_cache + 8, state.ip, sizeof(state.ip));
    write_u16(status_cache + 12, PORT);
    write_u32(status_cache + 16, state.session_id);
    if (state.phase == PHASE_READY &&
        (int32_t)(state.expires_at - xTaskGetTickCount()) > 0) {
        memcpy(status_cache + 20, state.key, sizeof(state.key));
        uint32_t remaining = (state.expires_at - xTaskGetTickCount()) / configTICK_RATE_HZ;
        write_u32(status_cache + 52, remaining);
        size_t ssid_length = strlen(state.ssid);
        size_t password_length = strlen(state.password);
        status_cache[56] = ssid_length;
        status_cache[57] = password_length;
        memcpy(status_cache + 58, state.ssid, ssid_length);
        memcpy(status_cache + 90, state.password, password_length);
    }
}

static void rotate_session_locked(void)
{
    static const char alphabet[] = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    do state.session_id = esp_random(); while (state.session_id == 0);
    esp_fill_random(state.key, sizeof(state.key));
    for (size_t i = 0; i < sizeof(state.password) - 1; ++i)
        state.password[i] = alphabet[esp_random() % (sizeof(alphabet) - 1)];
    state.password[sizeof(state.password) - 1] = '\0';
    state.expires_at = xTaskGetTickCount() + pdMS_TO_TICKS(SESSION_MS);
    state.last_frame_sequence = 0;
}

static void clear_session_locked(uint8_t phase, uint8_t result)
{
    state.phase = phase;
    state.result = result;
    memset(state.ip, 0, sizeof(state.ip));
    state.session_id = 0;
    memset(state.key, 0, sizeof(state.key));
    memset(state.password, 0, sizeof(state.password));
    state.expires_at = 0;
    state.last_frame_sequence = 0;
    publish_locked();
}

static void wifi_event(void *arg, esp_event_base_t base, int32_t id, void *data)
{
    (void)arg;
    (void)data;
    if (base == WIFI_EVENT && id == WIFI_EVENT_AP_STACONNECTED)
        ESP_LOGI(TAG, "Owner phone joined temporary maintenance network");
    else if (base == WIFI_EVENT && id == WIFI_EVENT_AP_STADISCONNECTED)
        ESP_LOGI(TAG, "Owner phone left temporary maintenance network");
}

static bool initialize_wifi(void)
{
    if (wifi_initialized) return true;
    esp_err_t err = esp_netif_init();
    if (err != ESP_OK && err != ESP_ERR_INVALID_STATE) return false;
    err = esp_event_loop_create_default();
    if (err != ESP_OK && err != ESP_ERR_INVALID_STATE) return false;
    ap_netif = esp_netif_create_default_wifi_ap();
    if (!ap_netif) return false;
    wifi_init_config_t init = WIFI_INIT_CONFIG_DEFAULT();
    if (esp_wifi_init(&init) != ESP_OK) return false;
    if (esp_event_handler_register(WIFI_EVENT, ESP_EVENT_ANY_ID, wifi_event, NULL) != ESP_OK)
        return false;
    wifi_initialized = true;
    return true;
}

static bool start_maintenance_network(void)
{
    if (!initialize_wifi()) return false;
    uint8_t mac[6];
    if (esp_read_mac(mac, ESP_MAC_WIFI_SOFTAP) != ESP_OK) return false;
    xSemaphoreTake(state_lock, portMAX_DELAY);
    snprintf(state.ssid, sizeof(state.ssid), "eGauge-%02X%02X", mac[4], mac[5]);
    rotate_session_locked();
    wifi_config_t config = {0};
    strlcpy((char *)config.ap.ssid, state.ssid, sizeof(config.ap.ssid));
    strlcpy((char *)config.ap.password, state.password, sizeof(config.ap.password));
    config.ap.ssid_len = strlen(state.ssid);
    config.ap.channel = 1;
    config.ap.max_connection = 1;
    config.ap.authmode = WIFI_AUTH_WPA2_PSK;
    config.ap.pmf_cfg.capable = true;
    config.ap.pmf_cfg.required = false;
    xSemaphoreGive(state_lock);
    if (atomic_load(&wifi_active)) esp_wifi_stop();
    if (esp_wifi_set_mode(WIFI_MODE_AP) != ESP_OK ||
        esp_wifi_set_config(WIFI_IF_AP, &config) != ESP_OK ||
        esp_wifi_start() != ESP_OK) return false;
    esp_netif_ip_info_t info;
    if (esp_netif_get_ip_info(ap_netif, &info) != ESP_OK) {
        esp_wifi_stop();
        return false;
    }
    xSemaphoreTake(state_lock, portMAX_DELAY);
    uint32_t address = info.ip.addr;
    state.ip[0] = address & 0xff;
    state.ip[1] = (address >> 8) & 0xff;
    state.ip[2] = (address >> 16) & 0xff;
    state.ip[3] = (address >> 24) & 0xff;
    state.phase = PHASE_READY;
    state.result = RESULT_OK;
    publish_locked();
    xSemaphoreGive(state_lock);
    atomic_store(&wifi_active, true);
    ESP_LOGI(TAG, "Temporary Wi-Fi maintenance network ready at " IPSTR ":%d",
             IP2STR(&info.ip), PORT);
    return true;
}

static void stop_maintenance_network(void)
{
    atomic_store(&wifi_active, false);
    if (wifi_initialized) esp_wifi_stop();
    xSemaphoreTake(state_lock, portMAX_DELAY);
    clear_session_locked(PHASE_OFF, RESULT_OK);
    xSemaphoreGive(state_lock);
}

static int receive_all(int socket_fd, uint8_t *data, size_t length)
{
    size_t received = 0;
    while (received < length) {
        int count = recv(socket_fd, data + received, length - received, 0);
        if (count <= 0) return -1;
        received += count;
    }
    return 0;
}

static int send_all(int socket_fd, const uint8_t *data, size_t length)
{
    size_t sent = 0;
    while (sent < length) {
        int count = send(socket_fd, data + sent, length - sent, 0);
        if (count <= 0) return -1;
        sent += count;
    }
    return 0;
}

static void make_nonce(uint8_t nonce[12], uint32_t session_id,
                       uint32_t sequence, uint8_t direction)
{
    memset(nonce, 0, 12);
    write_u32(nonce, session_id);
    write_u32(nonce + 4, sequence);
    nonce[8] = direction;
}

static bool wait_status(uint8_t kind, const uint8_t *command, size_t command_length,
                        uint8_t *out, size_t *out_length)
{
    bool admitted = kind == 1
        ? config_transfer_command(command, command_length)
        : ota_transfer_command(command, command_length);
    if (!admitted) return false;
    uint32_t sequence = read_u32(command + 1);
    TickType_t started = xTaskGetTickCount();
    while (xTaskGetTickCount() - started < COMMAND_STATUS_TIMEOUT_TICKS) {
        *out_length = kind == 1 ? config_transfer_status(out) : ota_transfer_status(out);
        if (*out_length >= 8 && out[3] == command[0] && read_u32(out + 4) == sequence)
            return true;
        vTaskDelay(COMMAND_STATUS_POLL_TICKS);
    }
    return false;
}

static bool validate_ota_batch(const uint8_t *batch, size_t length)
{
    if (length < 1 || batch[0] == 0 || batch[0] > OTA_BATCH_MAX_COMMANDS) return false;
    size_t offset = 1;
    for (uint8_t i = 0; i < batch[0]; ++i) {
        if (offset + 2 > length) return false;
        size_t command_length = read_u16(batch + offset);
        offset += 2;
        if (command_length < 13 || command_length > OTA_TRANSFER_MAX_REQUEST ||
            offset + command_length > length || batch[offset] != 0x23) return false;
        offset += command_length;
    }
    return offset == length;
}

static bool run_ota_batch(const uint8_t *batch, uint8_t *response, size_t *response_length)
{
    size_t offset = 1;
    uint8_t status[OTA_TRANSFER_STATUS_SIZE];
    size_t status_length = 0;
    for (uint8_t i = 0; i < batch[0]; ++i) {
        size_t command_length = read_u16(batch + offset);
        offset += 2;
        if (!wait_status(2, batch + offset, command_length, status, &status_length)) return false;
        if (status_length != OTA_TRANSFER_STATUS_SIZE || status[2] != RESULT_OK) return false;
        offset += command_length;
    }
    response[0] = batch[0];
    memcpy(response + 1, status, status_length);
    *response_length = status_length + 1;
    return true;
}

static bool handle_frame(int client, uint8_t *ciphertext, uint8_t *plaintext)
{
    uint8_t header[FRAME_HEADER], tag[FRAME_TAG];
    if (receive_all(client, header, sizeof(header)) != 0 ||
        memcmp(header, "EGW1", 4) != 0) return false;
    uint32_t session_id = read_u32(header + 4);
    uint32_t sequence = read_u32(header + 8);
    uint8_t kind = header[12];
    uint16_t length = read_u16(header + 14);
    if ((kind < 1 || kind > 4) || length == 0 || length > FRAME_MAX_PLAINTEXT ||
        receive_all(client, tag, sizeof(tag)) != 0 ||
        receive_all(client, ciphertext, length) != 0) return false;

    uint8_t key[32];
    bool authorized = false;
    xSemaphoreTake(state_lock, portMAX_DELAY);
    if (state.phase == PHASE_READY && state.session_id == session_id &&
        sequence > state.last_frame_sequence &&
        (int32_t)(state.expires_at - xTaskGetTickCount()) > 0) {
        memcpy(key, state.key, sizeof(key));
        authorized = true;
    }
    xSemaphoreGive(state_lock);
    if (!authorized) return false;

    uint8_t nonce[12];
    make_nonce(nonce, session_id, sequence, 0);
    mbedtls_gcm_context gcm;
    mbedtls_gcm_init(&gcm);
    int rc = mbedtls_gcm_setkey(&gcm, MBEDTLS_CIPHER_ID_AES, key, 256);
    if (rc == 0) rc = mbedtls_gcm_auth_decrypt(&gcm, length, nonce, sizeof(nonce),
        header, sizeof(header), tag, sizeof(tag), ciphertext, plaintext);
    memset(key, 0, sizeof(key));
    if (rc != 0) {
        mbedtls_gcm_free(&gcm);
        return false;
    }
    xSemaphoreTake(state_lock, portMAX_DELAY);
    if (state.session_id != session_id || sequence <= state.last_frame_sequence ||
        (int32_t)(state.expires_at - xTaskGetTickCount()) <= 0) {
        xSemaphoreGive(state_lock);
        mbedtls_gcm_free(&gcm);
        return false;
    }
    state.last_frame_sequence = sequence;
    xSemaphoreGive(state_lock);

    uint8_t response[CONFIG_TRANSFER_STATUS_SIZE];
    size_t response_length = 0;
    bool ok;
    if (kind == 3 && length == 1 && (plaintext[0] == 1 || plaintext[0] == 2)) {
        response_length = plaintext[0] == 1
            ? config_transfer_status(response) : ota_transfer_status(response);
        ok = true;
    } else if (kind == 4 && validate_ota_batch(plaintext, length)) {
        ok = run_ota_batch(plaintext, response, &response_length);
    } else if ((kind == 1 && plaintext[0] >= 0x10 && plaintext[0] <= 0x17) ||
               (kind == 2 && plaintext[0] >= 0x20 && plaintext[0] <= 0x28)) {
        ok = wait_status(kind, plaintext, length, response, &response_length);
    } else ok = false;
    if (!ok) {
        response[0] = 1;
        response_length = 1;
    }

    memcpy(header, "EGW1", 4);
    header[12] = kind | 0x80;
    header[13] = ok ? 0 : 1;
    write_u16(header + 14, response_length);
    make_nonce(nonce, session_id, sequence, 1);
    rc = mbedtls_gcm_crypt_and_tag(&gcm, MBEDTLS_GCM_ENCRYPT, response_length,
        nonce, sizeof(nonce), header, sizeof(header), response, ciphertext,
        sizeof(tag), tag);
    mbedtls_gcm_free(&gcm);
    return rc == 0 && send_all(client, header, sizeof(header)) == 0 &&
        send_all(client, tag, sizeof(tag)) == 0 &&
        send_all(client, ciphertext, response_length) == 0;
}

static void server_task(void *arg)
{
    (void)arg;
    uint8_t *ciphertext = heap_caps_malloc(FRAME_MAX_PLAINTEXT,
        MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    uint8_t *plaintext = heap_caps_malloc(FRAME_MAX_PLAINTEXT,
        MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (!ciphertext || !plaintext) {
        ESP_LOGE(TAG, "Could not allocate Wi-Fi frame buffers in PSRAM");
        heap_caps_free(ciphertext);
        heap_caps_free(plaintext);
        vTaskDelete(NULL);
        return;
    }
    for (;;) {
        while (!atomic_load(&wifi_active)) vTaskDelay(pdMS_TO_TICKS(250));
        int server = socket(AF_INET, SOCK_STREAM, IPPROTO_IP);
        if (server < 0) { vTaskDelay(pdMS_TO_TICKS(1000)); continue; }
        int yes = 1;
        setsockopt(server, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));
        struct sockaddr_in address = {
            .sin_family = AF_INET,
            .sin_port = htons(PORT),
            .sin_addr.s_addr = htonl(INADDR_ANY),
        };
        if (bind(server, (struct sockaddr *)&address, sizeof(address)) != 0 ||
            listen(server, 1) != 0) {
            close(server);
            vTaskDelay(pdMS_TO_TICKS(1000));
            continue;
        }
        while (atomic_load(&wifi_active)) {
            struct timeval timeout = {.tv_sec = 1};
            setsockopt(server, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
            int client = accept(server, NULL, NULL);
            if (client < 0) continue;
            struct timeval io_timeout = {.tv_sec = 65};
            setsockopt(client, SOL_SOCKET, SO_RCVTIMEO, &io_timeout, sizeof(io_timeout));
            setsockopt(client, SOL_SOCKET, SO_SNDTIMEO, &io_timeout, sizeof(io_timeout));
            while (handle_frame(client, ciphertext, plaintext)) {}
            close(client);
        }
        close(server);
    }
}

static void worker_task(void *arg)
{
    (void)arg;
    command_t command;
    for (;;) {
        if (xQueueReceive(command_queue, &command, pdMS_TO_TICKS(1000)) == pdTRUE) {
            xSemaphoreTake(state_lock, portMAX_DELAY);
            state.last_op = command.op;
            state.sequence = command.sequence;
            state.result = RESULT_PENDING;
            state.phase = command.op == OP_OPEN ? PHASE_STARTING : PHASE_OFF;
            publish_locked();
            xSemaphoreGive(state_lock);
            if (command.op == OP_OPEN) {
                if (!start_maintenance_network()) {
                    xSemaphoreTake(state_lock, portMAX_DELAY);
                    clear_session_locked(PHASE_FAILED, RESULT_NETWORK);
                    state.last_op = command.op;
                    state.sequence = command.sequence;
                    publish_locked();
                    xSemaphoreGive(state_lock);
                }
            } else if (command.op == OP_CLOSE) {
                stop_maintenance_network();
                xSemaphoreTake(state_lock, portMAX_DELAY);
                state.last_op = command.op;
                state.sequence = command.sequence;
                publish_locked();
                xSemaphoreGive(state_lock);
            }
        }
        bool expired;
        xSemaphoreTake(state_lock, portMAX_DELAY);
        expired = state.phase == PHASE_READY &&
            (int32_t)(state.expires_at - xTaskGetTickCount()) <= 0;
        xSemaphoreGive(state_lock);
        if (expired) stop_maintenance_network();
    }
}

esp_err_t wifi_bulk_init(void)
{
    state_lock = xSemaphoreCreateMutex();
    command_queue = xQueueCreate(2, sizeof(command_t));
    if (!state_lock || !command_queue) return ESP_ERR_NO_MEM;
    xSemaphoreTake(state_lock, portMAX_DELAY);
    clear_session_locked(PHASE_OFF, RESULT_OK);
    xSemaphoreGive(state_lock);
    if (xTaskCreate(worker_task, "wifi_control", 5120, NULL, 4, NULL) != pdPASS ||
        xTaskCreate(server_task, "wifi_bulk", 8192, NULL, 4, NULL) != pdPASS)
        return ESP_ERR_NO_MEM;
    server_started = true;
    return ESP_OK;
}

bool wifi_bulk_command(const uint8_t *bytes, size_t length)
{
    if (!server_started || !bytes || length != 5 ||
        (bytes[0] != OP_OPEN && bytes[0] != OP_CLOSE)) return false;
    command_t command = {.op = bytes[0], .sequence = read_u32(bytes + 1)};
    return xQueueSend(command_queue, &command, 0) == pdTRUE;
}

size_t wifi_bulk_status(uint8_t out[WIFI_BULK_STATUS_SIZE])
{
    if (!out || !state_lock) return 0;
    xSemaphoreTake(state_lock, portMAX_DELAY);
    publish_locked();
    memcpy(out, status_cache, sizeof(status_cache));
    xSemaphoreGive(state_lock);
    return sizeof(status_cache);
}
