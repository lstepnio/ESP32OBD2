#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include "esp_err.h"

#define DIAGNOSTICS_STATUS_SIZE 32
#define DIAGNOSTICS_FULL_SIZE 248
#define DIAGNOSTICS_MAX_CODES 32
typedef enum { DIAGNOSTICS_NOT_CHECKED, DIAGNOSTICS_OK,
               DIAGNOSTICS_UNAVAILABLE, DIAGNOSTICS_UNSUPPORTED } diagnostics_result_t;

typedef struct {
    bool valid;
    bool mil_on;
    uint8_t reported_count;
    uint8_t stored_count;
    char first_code[6];
    bool transmission;
} diagnostics_snapshot_t;

esp_err_t diagnostics_state_init(void);
void diagnostics_state_configure(bool transmission, uint32_t revision, bool simulated);
void diagnostics_state_connected(void);
void diagnostics_state_failed(uint8_t mode, diagnostics_result_t result);
void diagnostics_state_mil(bool on, uint8_t reported_count, uint32_t now_ms);
void diagnostics_state_codes(uint8_t mode, const uint8_t *bytes, size_t length,
                             uint32_t now_ms);
void diagnostics_state_disconnected(void);
void diagnostics_state_snapshot(uint32_t now_ms, diagnostics_snapshot_t *out);
size_t diagnostics_state_status(uint8_t out[DIAGNOSTICS_STATUS_SIZE]);
size_t diagnostics_state_full_status(uint32_t now_ms, uint8_t out[DIAGNOSTICS_FULL_SIZE]);

void diagnostics_state_configure_for(unsigned source, bool transmission, uint32_t revision, bool simulated);
void diagnostics_state_connected_for(unsigned source);
void diagnostics_state_mil_for(unsigned source, bool on, uint8_t reported_count, uint32_t now_ms);
void diagnostics_state_codes_for(unsigned source, uint8_t mode, const uint8_t *bytes, size_t length,
                             uint32_t now_ms);
void diagnostics_state_failed_for(unsigned source, uint8_t mode, diagnostics_result_t result);
void diagnostics_state_disconnected_for(unsigned source);
void diagnostics_state_snapshot_for(unsigned source, uint32_t now_ms, diagnostics_snapshot_t *out);
size_t diagnostics_state_full_status_for(unsigned source, uint32_t now_ms, uint8_t out[DIAGNOSTICS_FULL_SIZE]);

void diagnostics_endpoint_configure(unsigned endpoint, unsigned transport, uint32_t revision, bool simulated);
bool diagnostics_endpoint_enabled(unsigned endpoint);
unsigned diagnostics_endpoint_transport(unsigned endpoint);
size_t diagnostics_endpoint_status(unsigned endpoint, uint32_t now, uint8_t out[248]);
void diagnostics_vehicle_snapshot(uint32_t now, diagnostics_snapshot_t *out);

void diagnostics_state_mil_payload(unsigned source,const uint8_t payload[4],uint32_t now);
size_t diagnostics_endpoint_readiness(unsigned endpoint,uint32_t now,uint8_t out[24]);
