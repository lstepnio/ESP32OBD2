#pragma once

#include <stdbool.h>
#include <stdint.h>

/* Configuration and OTA already use 1 and 2. These short/exclusive owners
 * share the same atomic gate so admission cannot race cached state reads. */
enum
{
    TRANSFER_GATE_VEHICLE_COMMAND = 4,
    TRANSFER_GATE_WIFI_STARTUP    = 5
};

bool    transfer_gate_claim(uint8_t kind);
void    transfer_gate_release(uint8_t kind);
uint8_t transfer_gate_current(void);

/* Includes the command owner so Stop and its menu remain available. */
bool transfer_gate_vehicle_allowed(void);
