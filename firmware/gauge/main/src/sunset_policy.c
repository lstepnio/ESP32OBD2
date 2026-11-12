#define _POSIX_C_SOURCE 200809L
#include "sunset_policy.h"

#include <math.h>
#include <time.h>

bool sunset_location_valid(int16_t latitude, int16_t longitude)
{
    return latitude >= -9000 && latitude <= 9000 && longitude >= -18000 && longitude <= 18000;
}

bool sunset_utc_valid(uint32_t utc)
{
    return utc >= SUNSET_MIN_UTC && utc <= SUNSET_MAX_UTC;
}

bool sunset_is_night(uint32_t utc, int16_t latitude, int16_t longitude, bool *night)
{
    if (!night || !sunset_utc_valid(utc) || !sunset_location_valid(latitude, longitude))
        return false;

    time_t    instant = utc;
    struct tm civil;
    if (!gmtime_r(&instant, &civil))
        return false;

    /* NOAA general solar position equations in UTC. The horizon test also
     * handles polar days and nights without trying to find a crossing. */
    const double radians = 0.017453292519943295;
    int          year    = civil.tm_year + 1900;
    int          days    = year % 4 == 0 ? 366 : 365; /* Valid UTC range ends before 2100. */
    double       minute  = civil.tm_hour * 60 + civil.tm_min + civil.tm_sec / 60.0;
    double       gamma   = 360 * radians / days * (civil.tm_yday + (minute - 720) / 1440);
    double       eq   = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) - 0.014615 * cos(2 * gamma) -
                                  0.040849 * sin(2 * gamma));
    double       decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) - 0.006758 * cos(2 * gamma) +
                        0.000907 * sin(2 * gamma) - 0.002697 * cos(3 * gamma) + 0.001480 * sin(3 * gamma);
    double       angle      = ((minute + eq + 0.04 * longitude) / 4 - 180) * radians;
    double       lat        = latitude * 0.01 * radians;
    double       cos_zenith = sin(lat) * sin(decl) + cos(lat) * cos(decl) * cos(angle);
    *night                  = cos_zenith < cos(90.833 * radians);
    return true;
}

sunset_policy_t sunset_policy_reconfigure(sunset_policy_t previous, bool was_automatic, bool automatic)
{
    /* Leaving the schedule restores Day even on boards without a button.
     * Editing a manual level preserves the current manual selection. */
    return (sunset_policy_t){.night = !was_automatic && !automatic && previous.night};
}

void sunset_policy_observe(sunset_policy_t *policy, bool solar_night)
{
    if (policy->solar_known && solar_night != policy->solar_night)
        policy->override = false;
    policy->solar_known = true;
    policy->solar_night = solar_night;
    if (!policy->override)
        policy->night = solar_night;
}

void sunset_policy_toggle(sunset_policy_t *policy)
{
    policy->night    = !policy->night;
    policy->override = true;
}

static bool button_released(sunset_button_t *button, bool pressed, uint64_t now_ms)
{
    if (button->candidate != pressed)
    {
        button->candidate = pressed;
        button->edge_ms   = now_ms;
    }
    if (now_ms - button->edge_ms < 20)
        return false;
    if (!button->armed)
    {
        button->armed = !pressed;
        return false;
    }
    if (pressed == button->stable)
        return false;
    button->stable = pressed;
    if (pressed)
    {
        button->pressed_ms = button->edge_ms;
        button->tracking   = true;
        return false;
    }
    bool tap         = button->tracking && button->edge_ms - button->pressed_ms < 700;
    button->tracking = false;
    return tap;
}

bool sunset_button_update(sunset_button_t *button, bool pressed, bool can_apply, uint64_t now_ms)
{
    if (button_released(button, pressed, now_ms))
        button->pending = !button->pending;
    if (!can_apply || !button->pending)
        return false;
    button->pending = false;
    return true;
}
