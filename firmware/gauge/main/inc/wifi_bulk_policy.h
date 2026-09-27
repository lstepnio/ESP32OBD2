#ifndef WIFI_BULK_POLICY_H
#define WIFI_BULK_POLICY_H

#include <stdbool.h>
#include <stdint.h>

/* Pure authorization policy kept separate from sockets and cryptography so
 * expiry, session binding and replay rejection can run in host fixtures. */
bool wifi_bulk_policy_accept(bool ready, uint32_t expected_session_id,
                             uint32_t received_session_id, uint32_t last_sequence,
                             uint32_t received_sequence, uint32_t expires_at,
                             uint32_t now);

#endif
