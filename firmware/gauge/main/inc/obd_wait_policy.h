#pragma once
#include <stdint.h>
/* One clock observation avoids underflow when the deadline expires between reads.
 * Unsigned subtraction handles the ESP32's 32-bit tick rollover. */
static inline uint32_t obd_wait_remaining(uint32_t start, uint32_t now, uint32_t budget)
{
    uint32_t elapsed = now - start;
    return elapsed < budget ? budget - elapsed : 0;
}
