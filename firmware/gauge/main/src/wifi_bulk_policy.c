#include "wifi_bulk_policy.h"

bool wifi_bulk_policy_accept(bool     ready,
                             uint32_t expected_session_id,
                             uint32_t received_session_id,
                             uint32_t last_sequence,
                             uint32_t received_sequence,
                             uint32_t expires_at,
                             uint32_t now)
{
    return ready && expected_session_id != 0 && received_session_id == expected_session_id &&
           received_sequence > last_sequence && (int32_t)(expires_at - now) > 0;
}

bool wifi_bulk_policy_memory_ready(size_t free_internal, size_t largest_internal)
{
    return free_internal >= 32U * 1024U && largest_internal >= 8U * 1024U;
}

bool wifi_bulk_policy_init_memory_ready(size_t free_internal, size_t largest_internal)
{
    return free_internal >= 64U * 1024U && largest_internal >= 16U * 1024U;
}
