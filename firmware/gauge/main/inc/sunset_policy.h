#pragma once

#include <stdbool.h>
#include <stdint.h>

#define SUNSET_NIGHT_DEFAULT 10
#define SUNSET_MIN_UTC UINT32_C(1704067200)
#define SUNSET_MAX_UTC UINT32_C(4102444799)

/* Coordinates are hundredths of a degree, longitude positive east. */
bool sunset_location_valid(int16_t latitude, int16_t longitude);
bool sunset_utc_valid(uint32_t utc);
bool sunset_is_night(uint32_t utc, int16_t latitude, int16_t longitude, bool *night);

typedef struct
{
    bool night;
    bool solar_known;
    bool solar_night;
    bool override;
} sunset_policy_t;

/* Call from the display owner. A manual choice lasts until the next solar edge. */
sunset_policy_t sunset_policy_reconfigure(sunset_policy_t previous, bool was_automatic, bool automatic);
void            sunset_policy_observe(sunset_policy_t *policy, bool solar_night);
void            sunset_policy_toggle(sunset_policy_t *policy);

typedef struct
{
    uint64_t edge_ms;
    uint64_t pressed_ms;
    bool     candidate;
    bool     stable;
    bool     armed;
    bool     tracking;
    bool     pending;
} sunset_button_t;

/* Sample even during maintenance. Apply the net toggle once the display is available. */
bool sunset_button_update(sunset_button_t *button, bool pressed, bool can_apply, uint64_t now_ms);
