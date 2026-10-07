#include <string.h>
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "diagnostics_state.h"

static SemaphoreHandle_t lock;
static struct {
    bool transmission, simulated, connected;
    uint32_t revision, session;
    bool mil_known, mil_on;
    diagnostics_result_t mil_result;
    uint8_t reported_count;
    uint32_t mil_at;
    struct {
        bool known;
        diagnostics_result_t result;
        uint8_t count;
        uint16_t codes[DIAGNOSTICS_MAX_CODES];
        uint32_t at;
    } categories[3];
} snapshot;

static int category(uint8_t mode) { return mode == 3 ? 0 : mode == 7 ? 1 : mode == 10 ? 2 : -1; }
static void put_u32(uint8_t *p, uint32_t v) { for (unsigned i=0; i<4; ++i) p[i] = v >> (8*i); }
static bool mil_fresh(uint32_t now) {
    return snapshot.connected && !snapshot.simulated && snapshot.mil_known &&
        snapshot.mil_result == DIAGNOSTICS_OK && now - snapshot.mil_at <= 60000;
}
static bool codes_fresh(unsigned i, uint32_t now) {
    return snapshot.connected && !snapshot.simulated && snapshot.categories[i].known &&
        snapshot.categories[i].result == DIAGNOSTICS_OK && now - snapshot.categories[i].at <= 120000;
}
static void format_code(uint16_t code, char out[6]) {
    static const char classes[] = "PCBU", digits[] = "0123456789ABCDEF";
    if (!code) { out[0] = 0; return; }
    out[0] = classes[code >> 14]; out[1] = digits[(code >> 12) & 3];
    out[2] = digits[(code >> 8) & 15]; out[3] = digits[(code >> 4) & 15];
    out[4] = digits[code & 15]; out[5] = 0;
}
esp_err_t diagnostics_state_init(void) {
    memset(&snapshot, 0, sizeof(snapshot));
    lock = xSemaphoreCreateMutex();
    return lock ? ESP_OK : ESP_ERR_NO_MEM;
}
void diagnostics_state_configure(bool transmission, uint32_t revision, bool simulated) {
    if (!lock) return;
    xSemaphoreTake(lock, portMAX_DELAY);
    memset(&snapshot, 0, sizeof(snapshot));
    snapshot.transmission = transmission; snapshot.revision = revision; snapshot.simulated = simulated;
    xSemaphoreGive(lock);
}
void diagnostics_state_connected(void) {
    if (!lock) return;
    xSemaphoreTake(lock, portMAX_DELAY);
    snapshot.session++;
    snapshot.connected = true;
    snapshot.mil_known = false; snapshot.mil_on = false; snapshot.reported_count = 0;
    snapshot.mil_result = DIAGNOSTICS_NOT_CHECKED; snapshot.mil_at = 0;
    memset(snapshot.categories, 0, sizeof(snapshot.categories));
    xSemaphoreGive(lock);
}
void diagnostics_state_mil(bool on, uint8_t count, uint32_t now) {
    if (!lock) return;
    xSemaphoreTake(lock, portMAX_DELAY);
    snapshot.mil_known = true; snapshot.mil_result = DIAGNOSTICS_OK;
    snapshot.mil_on = on; snapshot.reported_count = count; snapshot.mil_at = now;
    xSemaphoreGive(lock);
}
void diagnostics_state_codes(uint8_t mode, const uint8_t *bytes, size_t length, uint32_t now) {
    int i = category(mode);
    if (!lock || i < 0 || !bytes || (length & 1U) || length > 2*DIAGNOSTICS_MAX_CODES) return;
    uint16_t codes[DIAGNOSTICS_MAX_CODES] = {0};
    uint8_t count = 0;
    for (size_t j=0; j<length; j+=2) {
        uint16_t code = ((uint16_t)bytes[j] << 8) | bytes[j+1];
        if (!code) continue;
        bool duplicate = false;
        for (unsigned k=0; k<count; ++k) if (codes[k] == code) duplicate = true;
        if (!duplicate) codes[count++] = code;
    }
    xSemaphoreTake(lock, portMAX_DELAY);
    snapshot.categories[i].known = true; snapshot.categories[i].result = DIAGNOSTICS_OK;
    snapshot.categories[i].count = count; snapshot.categories[i].at = now;
    memcpy(snapshot.categories[i].codes, codes, sizeof(codes));
    xSemaphoreGive(lock);
}
void diagnostics_state_failed(uint8_t mode, diagnostics_result_t result) {
    int i = category(mode);
    if (!lock || (mode != 1 && i < 0) ||
        (result != DIAGNOSTICS_UNAVAILABLE && result != DIAGNOSTICS_UNSUPPORTED)) return;
    xSemaphoreTake(lock, portMAX_DELAY);
    if (mode == 1) snapshot.mil_result = result;
    else snapshot.categories[i].result = result;
    xSemaphoreGive(lock);
}
void diagnostics_state_disconnected(void) {
    if (!lock) return;
    xSemaphoreTake(lock, portMAX_DELAY);
    snapshot.connected = false;
    if (snapshot.mil_known) snapshot.mil_result = DIAGNOSTICS_UNAVAILABLE;
    for (unsigned i=0; i<3; ++i)
        if (snapshot.categories[i].known) snapshot.categories[i].result = DIAGNOSTICS_UNAVAILABLE;
    xSemaphoreGive(lock);
}
void diagnostics_state_snapshot(uint32_t now, diagnostics_snapshot_t *out) {
    if (!out) return;
    memset(out, 0, sizeof(*out));
    if (!lock) return;
    xSemaphoreTake(lock, portMAX_DELAY);
    out->transmission = snapshot.transmission;
    if (mil_fresh(now)) { out->mil_on = snapshot.mil_on; out->reported_count = snapshot.reported_count; out->valid = true; }
    if (codes_fresh(0, now)) {
        out->stored_count = snapshot.categories[0].count;
        if (!mil_fresh(now)) out->reported_count = out->stored_count;
        format_code(snapshot.categories[0].codes[0], out->first_code);
        out->valid = true;
    }
    xSemaphoreGive(lock);
}
size_t diagnostics_state_status(uint8_t out[DIAGNOSTICS_STATUS_SIZE]) {
    memset(out, 0, DIAGNOSTICS_STATUS_SIZE); out[0] = 5;
    if (!lock) return DIAGNOSTICS_STATUS_SIZE;
    xSemaphoreTake(lock, portMAX_DELAY);
    uint32_t now = pdTICKS_TO_MS(xTaskGetTickCount());
    /* Legacy clients cannot distinguish TCM from ECM. Never mislabel TCM evidence. */
    if (!snapshot.transmission && !snapshot.simulated) {
        if (mil_fresh(now)) out[1] |= 1;
        if (snapshot.mil_on) out[1] |= 2;
        out[2] = snapshot.reported_count;
        for (unsigned i=0; i<3; ++i) {
            if (codes_fresh(i, now)) out[1] |= (uint8_t)(4U << i);
            out[3+i] = snapshot.categories[i].count;
            out[6+2*i] = snapshot.categories[i].codes[0] >> 8;
            out[7+2*i] = snapshot.categories[i].codes[0];
            put_u32(out+12+4*i, snapshot.categories[i].at);
        }
        put_u32(out+24, snapshot.mil_at);
    }
    put_u32(out+28, now);
    xSemaphoreGive(lock);
    return DIAGNOSTICS_STATUS_SIZE;
}
size_t diagnostics_state_full_status(uint32_t now, uint8_t out[DIAGNOSTICS_FULL_SIZE]) {
    memset(out, 0, DIAGNOSTICS_FULL_SIZE); out[0] = 15;
    if (!lock) return DIAGNOSTICS_FULL_SIZE;
    xSemaphoreTake(lock, portMAX_DELAY);
    out[1] = snapshot.transmission ? 1 : 0;
    out[2] = (mil_fresh(now) ? 1 : 0) | (snapshot.mil_on ? 2 : 0) |
             (snapshot.connected ? 4 : 0) | (snapshot.simulated ? 8 : 0);
    out[3] = snapshot.mil_result;
    put_u32(out+4, snapshot.revision); put_u32(out+8, snapshot.session);
    put_u32(out+12, now); put_u32(out+16, snapshot.mil_at);
    uint16_t ecu = snapshot.transmission ? 0x7e9 : 0x7e8;
    out[20] = ecu; out[21] = ecu >> 8; out[22] = snapshot.reported_count;
    out[23] = snapshot.mil_known ? 1 : 0;
    for (unsigned i=0; i<3; ++i) {
        uint8_t *p = out+32+72*i;
        p[0] = snapshot.categories[i].result;
        p[1] = (snapshot.categories[i].known ? 1 : 0) | (codes_fresh(i, now) ? 2 : 0);
        p[2] = snapshot.categories[i].count;
        put_u32(p+4, snapshot.categories[i].at);
        for (unsigned j=0; j<snapshot.categories[i].count; ++j) {
            p[8+2*j] = snapshot.categories[i].codes[j] >> 8;
            p[9+2*j] = snapshot.categories[i].codes[j];
        }
    }
    xSemaphoreGive(lock);
    return DIAGNOSTICS_FULL_SIZE;
}
