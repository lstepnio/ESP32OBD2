#pragma once
#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>
#include "esp_err.h"
typedef enum { CLEAR_DISABLED, CLEAR_IDLE, CLEAR_PREPARED, CLEAR_SENT,
    CLEAR_VERIFYING, CLEAR_VERIFIED, CLEAR_CODES_REMAIN, CLEAR_PERMANENT_REMAIN,
    CLEAR_UNKNOWN, CLEAR_FAILED } clear_phase_t;
typedef struct {
    uint32_t operation,token,revision,session,prepared_at;
    uint8_t phase, endpoint, supported, reserved;
} clear_state_t;
/* Pure policy: no IO and no implicit resubmission. */
bool clear_prepare(clear_state_t *state,uint32_t operation,uint32_t token,uint32_t revision,
    uint32_t session,uint32_t now,bool supported,bool connected);
bool clear_confirm(clear_state_t *state,uint32_t operation,uint32_t token,uint32_t revision,
    uint32_t session,uint32_t now,bool parked,bool running,bool moving);
void clear_restore(clear_state_t *state);
esp_err_t diagnostic_clear_init(void);
bool diagnostic_clear_command(const uint8_t *bytes,size_t length);
void diagnostic_clear_tick(uint32_t now,bool running,bool moving);
bool diagnostic_clear_take(unsigned transport,uint32_t now,clear_state_t *out);
void diagnostic_clear_finish(uint32_t operation,clear_phase_t phase);
size_t diagnostic_clear_status(uint8_t out[32]);
