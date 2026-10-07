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
} snapshots[2];

static int category(uint8_t mode) { return mode == 3 ? 0 : mode == 7 ? 1 : mode == 10 ? 2 : -1; }
static void put_u32(uint8_t *p, uint32_t v) { for (unsigned i=0; i<4; ++i) p[i] = v >> (8*i); }
static bool mil_fresh(unsigned source, uint32_t now) {
    return snapshots[source].connected && !snapshots[source].simulated && snapshots[source].mil_known &&
        snapshots[source].mil_result == DIAGNOSTICS_OK && now - snapshots[source].mil_at <= 60000;
}
static bool codes_fresh(unsigned source, unsigned i, uint32_t now) {
    return snapshots[source].connected && !snapshots[source].simulated && snapshots[source].categories[i].known &&
        snapshots[source].categories[i].result == DIAGNOSTICS_OK && now - snapshots[source].categories[i].at <= 120000;
}
static void format_code(uint16_t code, char out[6]) {
    static const char classes[] = "PCBU", digits[] = "0123456789ABCDEF";
    if (!code) { out[0] = 0; return; }
    out[0] = classes[code >> 14]; out[1] = digits[(code >> 12) & 3];
    out[2] = digits[(code >> 8) & 15]; out[3] = digits[(code >> 4) & 15];
    out[4] = digits[code & 15]; out[5] = 0;
}
esp_err_t diagnostics_state_init(void) {
    memset(snapshots, 0, sizeof(snapshots));
    lock = xSemaphoreCreateMutex();
    return lock ? ESP_OK : ESP_ERR_NO_MEM;
}
void diagnostics_state_configure_for(unsigned source, bool transmission, uint32_t revision, bool simulated) {
    if (source >= 2 || !lock) return;
    xSemaphoreTake(lock, portMAX_DELAY);
    memset(&snapshots[source], 0, sizeof(snapshots[source]));
    snapshots[source].transmission = transmission; snapshots[source].revision = revision; snapshots[source].simulated = simulated;
    xSemaphoreGive(lock);
}
void diagnostics_state_connected_for(unsigned source) {
    if (source >= 2 || !lock) return;
    xSemaphoreTake(lock, portMAX_DELAY);
    snapshots[source].session++;
    snapshots[source].connected = true;
    snapshots[source].mil_known = false; snapshots[source].mil_on = false; snapshots[source].reported_count = 0;
    snapshots[source].mil_result = DIAGNOSTICS_NOT_CHECKED; snapshots[source].mil_at = 0;
    memset(snapshots[source].categories, 0, sizeof(snapshots[source].categories));
    xSemaphoreGive(lock);
}
void diagnostics_state_mil_for(unsigned source, bool on, uint8_t count, uint32_t now) {
    if (source >= 2 || !lock) return;
    xSemaphoreTake(lock, portMAX_DELAY);
    snapshots[source].mil_known = true; snapshots[source].mil_result = DIAGNOSTICS_OK;
    snapshots[source].mil_on = on; snapshots[source].reported_count = count; snapshots[source].mil_at = now;
    xSemaphoreGive(lock);
}
void diagnostics_state_codes_for(unsigned source, uint8_t mode, const uint8_t *bytes, size_t length, uint32_t now) {
    int i = category(mode);
    if (source >= 2 || !lock || i < 0 || !bytes || (length & 1U) || length > 2*DIAGNOSTICS_MAX_CODES) return;
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
    snapshots[source].categories[i].known = true; snapshots[source].categories[i].result = DIAGNOSTICS_OK;
    snapshots[source].categories[i].count = count; snapshots[source].categories[i].at = now;
    memcpy(snapshots[source].categories[i].codes, codes, sizeof(codes));
    xSemaphoreGive(lock);
}
void diagnostics_state_failed_for(unsigned source, uint8_t mode, diagnostics_result_t result) {
    int i = category(mode);
    if (source >= 2 || !lock || (mode != 1 && i < 0) ||
        (result != DIAGNOSTICS_UNAVAILABLE && result != DIAGNOSTICS_UNSUPPORTED)) return;
    xSemaphoreTake(lock, portMAX_DELAY);
    if (mode == 1) snapshots[source].mil_result = result;
    else snapshots[source].categories[i].result = result;
    xSemaphoreGive(lock);
}
void diagnostics_state_disconnected_for(unsigned source) {
    if (source >= 2 || !lock) return;
    xSemaphoreTake(lock, portMAX_DELAY);
    snapshots[source].connected = false;
    if (snapshots[source].mil_known) snapshots[source].mil_result = DIAGNOSTICS_UNAVAILABLE;
    for (unsigned i=0; i<3; ++i)
        if (snapshots[source].categories[i].known) snapshots[source].categories[i].result = DIAGNOSTICS_UNAVAILABLE;
    xSemaphoreGive(lock);
}
void diagnostics_state_snapshot_for(unsigned source, uint32_t now, diagnostics_snapshot_t *out) {
    if (!out) return;
    memset(out, 0, sizeof(*out));
    if (source >= 2 || !lock) return;
    if (xSemaphoreTake(lock, 0) != pdTRUE) return;
    out->transmission = snapshots[source].transmission;
    if (mil_fresh(source, now)) { out->mil_on = snapshots[source].mil_on; out->reported_count = snapshots[source].reported_count; out->valid = true; }
    if (codes_fresh(source, 0, now)) {
        out->stored_count = snapshots[source].categories[0].count;
        if (!mil_fresh(source, now)) out->reported_count = out->stored_count;
        format_code(snapshots[source].categories[0].codes[0], out->first_code);
        out->valid = true;
    }
    xSemaphoreGive(lock);
}
size_t diagnostics_state_status(uint8_t out[DIAGNOSTICS_STATUS_SIZE]) {
    unsigned source = 0;
    memset(out, 0, DIAGNOSTICS_STATUS_SIZE); out[0] = 5;
    if (source >= 2 || !lock) return DIAGNOSTICS_STATUS_SIZE;
    if (xSemaphoreTake(lock, 0) != pdTRUE) return 0;
    uint32_t now = pdTICKS_TO_MS(xTaskGetTickCount());
    /* Legacy clients cannot distinguish TCM from ECM. Never mislabel TCM evidence. */
    if (!snapshots[source].transmission && !snapshots[source].simulated) {
        if (mil_fresh(source, now)) out[1] |= 1;
        if (snapshots[source].mil_on) out[1] |= 2;
        out[2] = snapshots[source].reported_count;
        for (unsigned i=0; i<3; ++i) {
            if (codes_fresh(source, i, now)) out[1] |= (uint8_t)(4U << i);
            out[3+i] = snapshots[source].categories[i].count;
            out[6+2*i] = snapshots[source].categories[i].codes[0] >> 8;
            out[7+2*i] = snapshots[source].categories[i].codes[0];
            put_u32(out+12+4*i, snapshots[source].categories[i].at);
        }
        put_u32(out+24, snapshots[source].mil_at);
    }
    put_u32(out+28, now);
    xSemaphoreGive(lock);
    return DIAGNOSTICS_STATUS_SIZE;
}
size_t diagnostics_state_full_status_for(unsigned source, uint32_t now, uint8_t out[DIAGNOSTICS_FULL_SIZE]) {
    memset(out, 0, DIAGNOSTICS_FULL_SIZE); out[0] = 15;
    if (source >= 2 || !lock) return DIAGNOSTICS_FULL_SIZE;
    if (xSemaphoreTake(lock, 0) != pdTRUE) return 0;
    out[1] = snapshots[source].transmission ? 1 : 0;
    out[2] = (mil_fresh(source, now) ? 1 : 0) | (snapshots[source].mil_on ? 2 : 0) |
             (snapshots[source].connected ? 4 : 0) | (snapshots[source].simulated ? 8 : 0);
    out[3] = snapshots[source].mil_result;
    put_u32(out+4, snapshots[source].revision); put_u32(out+8, snapshots[source].session);
    put_u32(out+12, now); put_u32(out+16, snapshots[source].mil_at);
    uint16_t ecu = snapshots[source].transmission ? 0x7e9 : 0x7e8;
    out[20] = ecu; out[21] = ecu >> 8; out[22] = snapshots[source].reported_count;
    out[23] = snapshots[source].mil_known ? 1 : 0;
    for (unsigned i=0; i<3; ++i) {
        uint8_t *p = out+32+72*i;
        p[0] = snapshots[source].categories[i].result;
        p[1] = (snapshots[source].categories[i].known ? 1 : 0) | (codes_fresh(source, i, now) ? 2 : 0);
        p[2] = snapshots[source].categories[i].count;
        put_u32(p+4, snapshots[source].categories[i].at);
        for (unsigned j=0; j<snapshots[source].categories[i].count; ++j) {
            p[8+2*j] = snapshots[source].categories[i].codes[j] >> 8;
            p[9+2*j] = snapshots[source].categories[i].codes[j];
        }
    }
    xSemaphoreGive(lock);
    return DIAGNOSTICS_FULL_SIZE;
}

void diagnostics_state_configure(bool t, uint32_t r, bool s) { diagnostics_state_configure_for(0, t, r, s); }
void diagnostics_state_connected(void) { diagnostics_state_connected_for(0); }
void diagnostics_state_disconnected(void) { diagnostics_state_disconnected_for(0); }
void diagnostics_state_mil(bool on, uint8_t count, uint32_t now) { diagnostics_state_mil_for(0, on, count, now); }
void diagnostics_state_codes(uint8_t mode, const uint8_t *bytes, size_t length, uint32_t now) { diagnostics_state_codes_for(0, mode, bytes, length, now); }
void diagnostics_state_failed(uint8_t mode, diagnostics_result_t result) { diagnostics_state_failed_for(0, mode, result); }
void diagnostics_state_snapshot(uint32_t now, diagnostics_snapshot_t *out) { diagnostics_state_snapshot_for(0, now, out); }
size_t diagnostics_state_full_status(uint32_t now, uint8_t out[DIAGNOSTICS_FULL_SIZE]) { return diagnostics_state_full_status_for(0, now, out); }
