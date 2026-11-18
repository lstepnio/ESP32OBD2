#include "sunset_policy.h"

#include <assert.h>
#include <stdio.h>

static bool night_at(uint32_t utc, int16_t latitude, int16_t longitude)
{
    bool night = false;
    assert(sunset_is_night(utc, latitude, longitude, &night));
    return night;
}

int main(void)
{
    /* Denver: June 21 18:00 UTC is midday, June 22 04:00 UTC is after sunset. */
    assert(!night_at(1782064800, 3974, -10499));
    assert(night_at(1782100800, 3974, -10499));
    /* At the same UTC hour, seasonal sunset can put the gauge in either mode. */
    assert(!night_at(1782086400, 3974, -10499));
    assert(night_at(1797897600, 3974, -10499));
    assert(!night_at(1782064800, 8500, 0));
    assert(night_at(1797897600, 8500, 0));
    assert(!sunset_is_night(0, 0, 0, NULL));
    assert(!sunset_location_valid(-9001, 0));
    assert(!sunset_location_valid(0, 18001));

    sunset_policy_t policy = {0};
    sunset_policy_observe(&policy, true);
    assert(policy.night);
    sunset_policy_toggle(&policy);
    sunset_policy_observe(&policy, true);
    assert(!policy.night && policy.override);
    sunset_policy_observe(&policy, false);
    assert(!policy.night && !policy.override);
    sunset_policy_observe(&policy, true);
    assert(policy.night);
    assert(!sunset_policy_reconfigure(policy, true, false).night);
    assert(sunset_policy_reconfigure(policy, false, false).night);
    sunset_policy_t no_clock = {0};
    sunset_policy_toggle(&no_clock);
    sunset_policy_observe(&no_clock, false);
    assert(no_clock.night && no_clock.override);
    sunset_policy_observe(&no_clock, true);
    assert(no_clock.night && !no_clock.override);
    sunset_button_t button = {0};
    assert(!sunset_button_update(&button, true, true, 5)); /* Held at boot. */
    assert(!sunset_button_update(&button, true, true, 50));
    assert(!sunset_button_update(&button, false, true, 80));
    assert(!sunset_button_update(&button, false, true, 120));
    assert(!sunset_button_update(&button, true, true, 200));
    assert(!sunset_button_update(&button, false, true, 210)); /* Bounce. */
    assert(!sunset_button_update(&button, true, true, 220));
    assert(!sunset_button_update(&button, true, true, 260));
    assert(!sunset_button_update(&button, false, true, 300));
    assert(sunset_button_update(&button, false, true, 340));
    assert(!sunset_button_update(&button, false, true, 380));
    assert(!sunset_button_update(&button, true, true, 400));
    assert(!sunset_button_update(&button, true, true, 440));
    assert(!sunset_button_update(&button, false, true, 1150)); /* Long press. */
    assert(!sunset_button_update(&button, false, true, 1200));
    /* With the main loop's 20 ms receive wait, a quick press can be visible
     * for only two polls. Maintenance still samples and coalesces releases. */
    assert(!sunset_button_update(&button, true, false, 1300));
    assert(!sunset_button_update(&button, true, false, 1320));
    assert(!sunset_button_update(&button, false, false, 1340));
    assert(!sunset_button_update(&button, false, false, 1360));
    assert(sunset_button_update(&button, false, true, 1400));
    assert(!sunset_button_update(&button, false, true, 1420));
    for (uint64_t at = 1500; at <= 1600; at += 100)
    {
        assert(!sunset_button_update(&button, true, false, at));
        assert(!sunset_button_update(&button, true, false, at + 20));
        assert(!sunset_button_update(&button, false, false, at + 40));
        assert(!sunset_button_update(&button, false, false, at + 60));
    }
    assert(!sunset_button_update(&button, false, true, 1700));
    puts("PASS: seasonal and polar daylight, input bounds, and override lifetime");
}
