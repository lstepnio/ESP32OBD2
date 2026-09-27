#include "wifi_bulk_policy.h"

bool wifi_bulk_policy_accept(bool ready, uint32_t expected_session_id,
                             uint32_t received_session_id, uint32_t last_sequence,
                             uint32_t received_sequence, uint32_t expires_at,
                             uint32_t now)
{
    return ready && expected_session_id != 0 &&
           received_session_id == expected_session_id &&
           received_sequence > last_sequence &&
           (int32_t)(expires_at - now) > 0;
}
