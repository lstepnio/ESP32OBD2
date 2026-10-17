#pragma once
#include <stdbool.h>
#include <stdint.h>

/* Session loss only abandons an unactivated Wi-Fi transfer. Once activation is
 * durable, only boot identity/health can resolve the outcome. BLE is independent. */
static inline bool ota_recovery_abandon(uint8_t phase, bool wifi_owned, bool session_current)
{
    return wifi_owned && !session_current && phase >= 1 && phase <= 3;
}

/* Only authenticated OTA participants provide abandoned_session. A late signal
 * from an older session cannot close a newly opened maintenance network. */
static inline bool ota_recovery_close_network(uint32_t abandoned_session,
                                              uint32_t current_session, bool ready)
{
    return ready && abandoned_session != 0 && abandoned_session == current_session;
}
