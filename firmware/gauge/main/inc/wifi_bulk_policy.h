#ifndef WIFI_BULK_POLICY_H
#define WIFI_BULK_POLICY_H

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

/* Pure authorization policy kept separate from sockets and cryptography so
 * expiry, session binding and replay rejection can run in host fixtures. */
bool wifi_bulk_policy_accept(bool     ready,
                             uint32_t expected_session_id,
                             uint32_t received_session_id,
                             uint32_t last_sequence,
                             uint32_t received_sequence,
                             uint32_t expires_at,
                             uint32_t now);

/* Internal 8-bit free/largest floors: 64/16 KiB before driver initialization,
 * 32/8 KiB before AP start. Admission samples are not a reserved heap or OOM guarantee. */
bool wifi_bulk_policy_memory_ready(size_t free_internal, size_t largest_internal);
bool wifi_bulk_policy_init_memory_ready(size_t free_internal, size_t largest_internal);

#endif
