#include "diagnostic_alerts.h"

static uint32_t timestamp(const uint8_t *p)
{
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

size_t diagnostic_alerts_observe(unsigned                endpoint,
                                 uint32_t                now,
                                 uint8_t                 packet[DIAGNOSTICS_FULL_SIZE],
                                 alert_transition_sink_t sink)
{
    if (!packet || !sink || endpoint > 1)
        return 0;
    size_t length = diagnostics_endpoint_presentation_status(endpoint, now, packet);
    if (!length)
        return 0;
    bool usable = (packet[2] & 1) != 0;
    sink(32 + endpoint, usable && (packet[2] & 2) ? 3 : 0, !usable, endpoint ? "Transmission warning" : "Check engine",
         "codes", packet[22], 0, timestamp(packet + 16), now);
    unsigned count      = 0;
    bool     complete   = true;
    uint8_t  severity   = 0;
    uint32_t oldest_age = 0;
    for (unsigned i = 0; i < 3; ++i)
    {
        const uint8_t *category = packet + 32 + i * 72;
        if (category[0] == DIAGNOSTICS_UNSUPPORTED)
            continue;
        if (!(category[1] & 2))
        {
            complete = false;
            continue;
        }
        uint32_t age = now - timestamp(category + 4);
        if (age > oldest_age)
            oldest_age = age;
        count += category[2];
        if (category[2])
            severity = i == 2 ? (severity ? severity : 1) : 2;
    }
    sink(34 + endpoint, severity, !complete && !severity, endpoint ? "Transmission faults" : "Engine faults", "codes",
         count, 0, now - oldest_age, now);
    return length;
}
