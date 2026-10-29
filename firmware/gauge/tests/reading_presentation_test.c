#include "reading_presentation.h"
#include <assert.h>
#include <math.h>
#include <stdint.h>

int main(void)
{
    reading_presentation_t s = {0};
    double                 number;
    assert(reading_presentation_step(&s, READING_PRESENT_RPM, true, 675, 1, 0, false, &number) == 675);
    assert(number == 675); /* Cached page entry never rises from zero. */
    assert(reading_presentation_step(&s, READING_PRESENT_RPM, true, 1700, 1, 50, false, &number) == 675);
    double v = reading_presentation_step(&s, READING_PRESENT_RPM, true, 1700, 1, 100, false, &number);
    assert(v > 675 && v < 1700 && number == 675);
    double next = reading_presentation_step(&s, READING_PRESENT_RPM, true, 1000, 1, 100, false, &number);
    assert(next == v); /* Mid-animation retarget starts at current position. */
    next = reading_presentation_step(&s, READING_PRESENT_RPM, true, 1000, 1, 250, false, &number);
    assert(next == 1000 && number == 1000);
    reading_presentation_step(&s, READING_PRESENT_RPM, true, 1013, 1, 500, false, &number);
    assert(number == 1000); /* Deadband around a displayed bin. */
    reading_presentation_step(&s, READING_PRESENT_RPM, true, 1019, 1, 750, false, &number);
    assert(number == 1025);
    assert(reading_presentation_step(&s, READING_PRESENT_RPM, true, 2500, 1, 751, true, &number) == 2500);
    assert(number == 2500); /* Threshold transition bypasses all smoothing. */
    assert(reading_presentation_step(&s, READING_PRESENT_RPM, true, 0, 1, 752, false, &number) == 0);
    assert(number == 0);
    reading_presentation_step(&s, READING_PRESENT_RPM, false, 999, 1, 753, false, &number);
    assert(!s.initialized && number == 0);
    assert(reading_presentation_step(&s, READING_PRESENT_RPM, true, 700, 2, 754, false, &number) == 700);
    assert(reading_presentation_step(&s, READING_PRESENT_RPM, true, 900, 3, 755, false, &number) == 900);
    assert(reading_presentation_step(&s, READING_PRESENT_DIRECT, true, 4, 3, 756, false, &number) == 4);
    assert(number == 4); /* Gear/custom values are never rounded or interpolated. */
    reading_presentation_step(&s, READING_PRESENT_CONTINUOUS, false, NAN, 3, 0, false, &number);
    reading_presentation_step(&s, READING_PRESENT_CONTINUOUS, true, 10, 3, UINT32_MAX - 50, false, &number);
    reading_presentation_step(&s, READING_PRESENT_CONTINUOUS, true, 20, 3, UINT32_MAX - 25, false, &number);
    v = reading_presentation_step(&s, READING_PRESENT_CONTINUOUS, true, 20, 3, 25, false, &number);
    assert(v > 10 && v < 20 && number == 20);
    assert(reading_presentation_step(&s, READING_PRESENT_CONTINUOUS, true, 20, 3, 125, false, &number) == 20);
    reading_presentation_step(&s, READING_PRESENT_CONTINUOUS, true, INFINITY, 3, 126, false, &number);
    assert(!s.initialized);
}
