#pragma once

#include <stdbool.h>
#include <stdint.h>

bool transfer_gate_claim(uint8_t kind);
void transfer_gate_release(uint8_t kind);
uint8_t transfer_gate_current(void);
