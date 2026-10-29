#include "reading_presentation.h"
#include <assert.h>
#include <math.h>
#include <stdint.h>

static void age_and_continuity(void)
{
    reading_presentation_t s = {0};
    double                 number;
    assert(reading_presentation_step(&s, READING_PRESENT_RPM, true, 1000, 1, 0, 1, 0, false, &number) == 1000);
    double analog = reading_presentation_step(&s, READING_PRESENT_RPM, true, 4000, 1, 100, 1, 160, false, &number);
    assert(fabs(analog - 2920) < .001 && number == 1000); /* 60 ms observation age already paid. */
    analog = reading_presentation_step(&s, READING_PRESENT_RPM, true, 4000, 1, 100, 1, 250, false, &number);
    assert(analog == 4000 && number == 4000);
    /* A sample consumed after its analog window never starts a second window. */
    assert(reading_presentation_step(&s, READING_PRESENT_RPM, true, 5000, 1, 300, 1, 500, false, &number) == 5000);
    assert(number == 5000);
    assert(reading_presentation_step(&s, READING_PRESENT_RPM, true, 1500, 1, 501, 2, 501, false, &number) == 1500);
    assert(number == 1500); /* Invalid/recovered between renders changes continuity. */
    /* Retargeting remains bounded under jumps/reversals. Equal new observations
     * do not restart the target's 150 ms settling window. */
    analog = reading_presentation_step(&s, READING_PRESENT_CONTINUOUS, true, 4500, 1, 502, 2, 502, false, &number);
    assert(analog == 1500);
    double previous = analog;
    for (uint32_t now = 503; now <= 652; now++)
    {
        analog = reading_presentation_step(&s, READING_PRESENT_CONTINUOUS, true, 4500, 1, now, 2, now, false, &number);
        assert(analog >= previous && analog <= 4500 && number == 4500);
        previous = analog;
    }
    assert(analog == 4500);
    analog = reading_presentation_step(&s, READING_PRESENT_CONTINUOUS, true, 500, 1, 653, 2, 653, false, &number);
    assert(analog == 4500);
    analog = reading_presentation_step(&s, READING_PRESENT_CONTINUOUS, true, 500, 1, 653, 2, 703, false, &number);
    assert(analog > 500 && analog < 4500);
    double reverse =
        reading_presentation_step(&s, READING_PRESENT_CONTINUOUS, true, 5000, 1, 703, 2, 703, false, &number);
    assert(reverse == analog);
    assert(reading_presentation_step(&s, READING_PRESENT_CONTINUOUS, true, 5000, 1, 703, 2, 853, false, &number) ==
           5000);
}

int main(void)
{
    age_and_continuity();
    reading_presentation_t s = {0};
    double                 number;
    assert(reading_presentation_step(&s, READING_PRESENT_RPM, true, 675, 1, 0, 1, 0, false, &number) == 675);
    assert(number == 675); /* Cached page entry never rises from zero. */
    assert(reading_presentation_step(&s, READING_PRESENT_RPM, true, 1700, 1, 50, 1, 50, false, &number) == 675);
    double v = reading_presentation_step(&s, READING_PRESENT_RPM, true, 1700, 1, 100, 1, 100, false, &number);
    assert(v > 675 && v < 1700 && number == 675);
    double next = reading_presentation_step(&s, READING_PRESENT_RPM, true, 1000, 1, 100, 1, 100, false, &number);
    assert(next == v); /* Mid-animation retarget starts at current position. */
    next = reading_presentation_step(&s, READING_PRESENT_RPM, true, 1000, 1, 250, 1, 250, false, &number);
    assert(next == 1000 && number == 1000);
    reading_presentation_step(&s, READING_PRESENT_RPM, true, 1013, 1, 500, 1, 500, false, &number);
    assert(number == 1000); /* Deadband around a displayed bin. */
    reading_presentation_step(&s, READING_PRESENT_RPM, true, 1019, 1, 750, 1, 750, false, &number);
    assert(number == 1025);
    assert(reading_presentation_step(&s, READING_PRESENT_RPM, true, 2500, 1, 751, 1, 751, true, &number) == 2500);
    assert(number == 2500); /* Threshold transition bypasses all smoothing. */
    assert(reading_presentation_step(&s, READING_PRESENT_RPM, true, 0, 1, 752, 1, 752, false, &number) == 0);
    assert(number == 0);
    reading_presentation_step(&s, READING_PRESENT_RPM, false, 999, 1, 753, 1, 753, false, &number);
    assert(!s.initialized && number == 0);
    assert(reading_presentation_step(&s, READING_PRESENT_RPM, true, 700, 2, 754, 1, 754, false, &number) == 700);
    assert(reading_presentation_step(&s, READING_PRESENT_RPM, true, 900, 3, 755, 1, 755, false, &number) == 900);
    assert(reading_presentation_step(&s, READING_PRESENT_DIRECT, true, 4, 3, 756, 1, 756, false, &number) == 4);
    assert(number == 4); /* Gear/custom values are never rounded or interpolated. */
    reading_presentation_step(&s, READING_PRESENT_CONTINUOUS, false, NAN, 3, 0, 1, 0, false, &number);
    reading_presentation_step(&s, READING_PRESENT_CONTINUOUS, true, 10, 3, UINT32_MAX - 50, 1, UINT32_MAX - 50, false,
                              &number);
    reading_presentation_step(&s, READING_PRESENT_CONTINUOUS, true, 20, 3, UINT32_MAX - 25, 1, UINT32_MAX - 25, false,
                              &number);
    v = reading_presentation_step(&s, READING_PRESENT_CONTINUOUS, true, 20, 3, 25, 1, 25, false, &number);
    assert(v > 10 && v < 20 && number == 20);
    assert(reading_presentation_step(&s, READING_PRESENT_CONTINUOUS, true, 20, 3, 125, 1, 125, false, &number) == 20);
    reading_presentation_step(&s, READING_PRESENT_CONTINUOUS, true, INFINITY, 3, 126, 1, 126, false, &number);
    assert(!s.initialized);
}
