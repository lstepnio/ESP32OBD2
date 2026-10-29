#include <assert.h>
#include <stdio.h>
#include <math.h>
#include "reading_policy.h"
static void thermal_observe(reading_stability_t *state, float value, uint32_t start, uint32_t generation)
{
    for (uint32_t t = 0; t <= 30000; t += 1500)
        reading_stability_observe(state, "engine.coolant", 1, 5, 500, 2000, value, start + t, generation);
}
static poll_pid_policy_t thermal(const reading_stability_t *state,
                                 uint32_t                   now,
                                 bool                       visible,
                                 bool                       next,
                                 bool                       alert,
                                 uint32_t                   generation)
{
    return reading_policy_adaptive("engine.coolant", 1, 5, 500, 2000, visible, next, alert, state, now, generation);
}
int main(void)
{
    assert(reading_presentation_kind("engine.rpm", 1, 12, 500, 2000) == READING_PRESENT_RPM);
    assert(reading_presentation_kind("engine.coolant", 1, 5, 500, 2000) == READING_PRESENT_CONTINUOUS);
    assert(reading_presentation_kind("transmission.gear", 0x22, 0x5503, 500, 2000) == READING_PRESENT_DIRECT);
    assert(reading_presentation_kind("custom", 1, 12, 500, 2000) == READING_PRESENT_DIRECT);
    assert(reading_presentation_kind("engine.rpm", 1, 12, 1000, 2000) == READING_PRESENT_DIRECT);
    poll_pid_policy_t rpm = reading_policy("engine.rpm", 1, 12, 500, 2000, true, false, false);
    assert(rpm.interval_ms == 250 && rpm.display_age_ms == 3000 && rpm.priority == 2);
    poll_pid_policy_t hidden = reading_policy("engine.rpm", 1, 12, 500, 2000, false, false, false);
    assert(hidden.interval_ms == 1000 && hidden.display_age_ms == rpm.display_age_ms);
    poll_pid_policy_t next = reading_policy("engine.rpm", 1, 12, 500, 2000, false, true, false);
    assert(next.interval_ms == 500 && next.priority == 1);
    poll_pid_policy_t temp = reading_policy("engine.coolant", 1, 5, 500, 2000, true, false, false);
    assert(temp.interval_ms == 1500 && temp.display_age_ms == 8000);
    temp = reading_policy("engine.coolant", 1, 5, 500, 2000, true, false, true);
    assert(temp.interval_ms == 500 && temp.display_age_ms == 8000);
    poll_pid_policy_t custom = reading_policy("custom.pressure", 1, 5, 200, 700, true, false, false);
    assert(custom.interval_ms == 200 && custom.display_age_ms == 700);
    custom = reading_policy("engine.coolant", 1, 5, 200, 700, true, false, false);
    assert(custom.interval_ms == 200 && custom.display_age_ms == 700);
    custom = reading_policy("engine.coolant", 0x22, 5, 500, 2000, true, false, false);
    assert(custom.interval_ms == 500 && custom.display_age_ms == 2000);
    reading_stability_t state = {0};
    thermal_observe(&state, 90, 0, 1);
    assert(state.initialized);
    assert(thermal(&state, 30000, false, false, false, 1).interval_ms == 3000);
    assert(thermal(&state, 30000, true, false, false, 1).interval_ms == 1500);
    assert(thermal(&state, 30000, false, true, false, 1).interval_ms == 1500);
    assert(thermal(&state, 30000, false, false, true, 1).interval_ms == 500);
    assert(thermal(&state, 30000, false, false, false, 2).interval_ms == 1500);
    assert(thermal(&state, 38001, false, false, false, 1).interval_ms == 1500);
    /* Cumulative drift is compared to the window anchor, not just adjacent reads. */
    reading_stability_observe(&state, "engine.coolant", 1, 5, 500, 2000, 90.3f, 31500, 1);
    assert(thermal(&state, 31500, false, false, false, 1).interval_ms == 3000);
    reading_stability_observe(&state, "engine.coolant", 1, 5, 500, 2000, 90.6f, 33000, 1);
    assert(thermal(&state, 33000, false, false, false, 1).interval_ms == 1500);
    thermal_observe(&state, 90.6f, 33000, 1);
    reading_stability_reset(&state); /* Worker failure/reset, never synthesized zero. */
    assert(thermal(&state, 63000, false, false, false, 1).interval_ms == 1500);
    thermal_observe(&state, 90, 64000, 1);
    reading_stability_observe(&state, "engine.coolant", 1, 5, 500, 2000, 90, 102001, 1);
    assert(thermal(&state, 102001, false, false, false, 1).interval_ms == 1500);
    thermal_observe(&state, 90, 103000, 1);
    reading_stability_observe(&state, "engine.coolant", 1, 5, 500, 2000, 90, 134000, 2);
    assert(thermal(&state, 134000, false, false, false, 2).interval_ms == 1500);
    thermal_observe(&state, 90, UINT32_MAX - 15000, 3);
    assert(thermal(&state, 14999, false, false, false, 3).interval_ms == 3000);

    /* Fuel is reviewed slow metadata; fast RPM/gear/voltage never adapt merely
     * because a value is constant. Unknown identity or custom timings also hold. */
    for (uint32_t t = 0; t <= 30000; t += 5000)
        reading_stability_observe(&state, "vehicle.fuel", 1, 0x2f, 500, 2000, 50, t, 4);
    poll_pid_policy_t fuel =
        reading_policy_adaptive("vehicle.fuel", 1, 0x2f, 500, 2000, false, false, false, &state, 30000, 4);
    assert(fuel.interval_ms == 10000 && fuel.display_age_ms == 20000);
    fuel = reading_policy_adaptive("vehicle.fuel", 1, 0x2f, 500, 2000, true, false, false, &state, 30000, 4);
    assert(fuel.interval_ms == 2000 && fuel.priority == 2);
    fuel = reading_policy_adaptive("vehicle.fuel", 1, 0x2f, 500, 2000, false, true, false, &state, 30000, 4);
    assert(fuel.interval_ms == 2500 && fuel.priority == 1);
    fuel = reading_policy_adaptive("vehicle.fuel", 1, 0x2f, 500, 2000, false, false, true, &state, 30000, 4);
    assert(fuel.interval_ms == 500);
    for (uint32_t t = 0; t <= 60000; t += 1000)
        reading_stability_observe(&state, "engine.rpm", 1, 12, 500, 2000, 1500, t, 4);
    assert(!state.initialized);
    assert(reading_policy_adaptive("engine.rpm", 1, 12, 500, 2000, true, false, false, &state, 60000, 4).interval_ms ==
           250);
    assert(reading_policy_adaptive("engine.rpm", 1, 12, 500, 2000, false, true, false, &state, 60000, 4).interval_ms ==
           500);
    assert(reading_policy_adaptive("engine.rpm", 1, 12, 500, 2000, false, false, false, &state, 60000, 4).interval_ms ==
           1000);
    reading_stability_observe(&state, "engine.coolant", 1, 5, 200, 700, 90, 0, 1);
    assert(!state.initialized);
    assert(
        reading_policy_adaptive("engine.coolant", 1, 5, 200, 700, false, false, false, &state, 60000, 4).interval_ms ==
        200);
    assert(
        reading_policy_adaptive("custom.coolant", 1, 5, 500, 2000, false, false, false, &state, 60000, 4).interval_ms ==
        500);
    assert(reading_policy_adaptive("transmission.gear", 0x22, 0x5503, 500, 2000, false, false, false, &state, 60000, 4)
               .interval_ms <= 1000);
    reading_stability_observe(&state, "transmission.gear", 0x22, 0x5503, 500, 2000, 3, 0, 4);
    assert(!state.initialized);
    assert(reading_policy_adaptive("transmission.gear", 0x22, 0x5503, 500, 2000, false, false, false, &state, 60000, 4)
               .interval_ms == 1000);
    reading_stability_observe(&state, "obd.voltage", 1, 0x42, 1000, 4000, 14, 0, 4);
    assert(!state.initialized);
    assert(
        reading_policy_adaptive("obd.voltage", 1, 0x42, 1000, 4000, true, false, false, &state, 60000, 4).interval_ms ==
        250);
    thermal_observe(&state, 90, 0, 4);
    reading_stability_observe(&state, "engine.coolant", 1, 5, 500, 2000, NAN, 31500, 4);
    assert(!state.initialized);
    assert(sizeof(reading_stability_t) <= 24);
    puts(
        "Reading stability: reviewed metadata, window/drift, visibility, alert cap, reset, generation and wrap passed");
}
