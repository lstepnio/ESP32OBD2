#pragma once
#include <stdbool.h>
#include <stdint.h>

/* Session loss only abandons an unactivated Wi-Fi transfer. Once activation is
 * durable, only boot identity/health can resolve the outcome. BLE is independent. */
static inline bool ota_recovery_abandon(uint8_t phase, bool wifi_owned, bool session_current)
{
    return wifi_owned && !session_current && phase >= 1 && phase <= 3;
}
