#pragma once
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#define ALERT_EVENT_KEYS 44
#define ALERT_EVENT_RING 64
#define ALERT_EVENT_WIRE 84
#define ALERT_EVENT_PACKET 360
/* Severity: 0 inactive, 1 information, 2 advisory, 3 warning, 4 critical.
 * Availability: 0 fresh, 1 unavailable. Lifecycle: 0 resolved, 1 active, 2 expired. */
typedef struct {
    uint32_t sequence, boot, episode, at;
    uint16_t key;
    uint8_t severity, unavailable, lifecycle, kind, acknowledged, reserved;
    float value, limit;
    char label[32], unit[12];
    uint32_t observed_at, revision;
} alert_event_t;
typedef struct {
    alert_event_t current;
    uint32_t snooze_at, snooze_ms, expires_at;
    uint8_t acknowledged_severity;
} alert_episode_t;
typedef struct {
    uint32_t boot, sequence, drops, revision;
    uint64_t simulated_keys;
    uint8_t count, head;
    alert_event_t ring[ALERT_EVENT_RING];
    alert_episode_t episodes[ALERT_EVENT_KEYS];
} alert_events_t;
void alert_events_init(alert_events_t *events, uint32_t boot);
void alert_events_observe(alert_events_t *events, unsigned key, uint8_t severity,
    bool unavailable, const char *label, const char *unit, float value, float limit,
    uint32_t observed_at, uint32_t now);
bool alert_events_ack(alert_events_t *events, unsigned key, uint32_t episode,
    uint32_t boot, uint32_t snooze_ms, uint32_t now);
void alert_events_tick(alert_events_t *events, uint32_t now);
bool alert_events_attention(const alert_episode_t *episode, uint32_t now);
size_t alert_events_packet(const alert_events_t *events, uint32_t requested_boot,
    uint32_t cursor, bool active, uint8_t out[ALERT_EVENT_PACKET]);
void alert_event_encode(const alert_event_t *event, uint8_t out[ALERT_EVENT_WIRE]);
bool alert_event_decode(const uint8_t in[ALERT_EVENT_WIRE], alert_event_t *out);

bool alert_external_validate(const uint8_t request[64]);

bool alert_events_summary(const alert_events_t *events,const uint8_t priorities[44],uint32_t now,alert_event_t *out,bool *attention);
