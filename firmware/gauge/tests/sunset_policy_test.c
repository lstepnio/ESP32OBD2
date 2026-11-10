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
    sunset_policy_t no_clock = {0};
    sunset_policy_toggle(&no_clock);
    sunset_policy_observe(&no_clock, false);
    assert(no_clock.night && no_clock.override);
    sunset_policy_observe(&no_clock, true);
    assert(no_clock.night && !no_clock.override);
    sunset_button_t button = {0};
    assert(!sunset_button_release(&button, true, 5)); /* Held at boot. */
    assert(!sunset_button_release(&button, true, 50));
    assert(!sunset_button_release(&button, false, 80));
    assert(!sunset_button_release(&button, false, 120));
    assert(!sunset_button_release(&button, true, 200));
    assert(!sunset_button_release(&button, false, 210)); /* Bounce. */
    assert(!sunset_button_release(&button, true, 220));
    assert(!sunset_button_release(&button, true, 260));
    assert(!sunset_button_release(&button, false, 300));
    assert(sunset_button_release(&button, false, 340));
    assert(!sunset_button_release(&button, false, 380));
    assert(!sunset_button_release(&button, true, 400));
    assert(!sunset_button_release(&button, true, 440));
    assert(!sunset_button_release(&button, false, 1150)); /* Long press. */
    assert(!sunset_button_release(&button, false, 1200));
    puts("PASS: seasonal and polar daylight, input bounds, and override lifetime");
}
