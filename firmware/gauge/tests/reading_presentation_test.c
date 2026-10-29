#include <assert.h>
#include <math.h>
#include <stdio.h>
#include "reading_presentation.h"

int main(void)
{
    reading_presentation_t state = {0};
    assert(reading_presentation_value(&state, true, 100, 10, 1, 10) == 100);
    assert(reading_presentation_value(&state, true, 200, 20, 1, 20) == 100);
    double previous = 100;
    for (uint32_t t = 21; t <= 140; t++)
    {
        double value = reading_presentation_value(&state, true, 200, 20, 1, t);
        assert(value >= previous && value <= 200);
        if (t == 20 + READING_PRESENTATION_SETTLE_MS)
            assert(value == 200);
        previous = value;
    }
    assert(previous == 200);
    state = (reading_presentation_t){0};
    reading_presentation_value(&state, true, 100, 0, 1, 0);
    for (uint32_t t = 1; t <= 121; t++)
        previous = reading_presentation_value(&state, true, 200, t, 1, t);
    assert(previous == 200); /* Repeated equal targets never restart settling. */
    /* No drift, extrapolation or fresh-window restart on unchanged samples. */
    assert(reading_presentation_value(&state, true, 200, 20, 1, 3000) == 200);
    assert(reading_presentation_value(&state, true, 0, 3001, 1, 3001) == 200);
    double falling = reading_presentation_value(&state, true, 0, 3001, 1, 3051);
    assert(falling > 0 && falling < 200);
    assert(reading_presentation_value(&state, true, 300, 3051, 1, 3051) == falling);
    for (uint32_t t = 3052; t <= 3171; t++)
    {
        double value = reading_presentation_value(&state, true, 300, 3051, 1, t);
        assert(value >= falling && value <= 300);
        falling = value;
    }
    assert(falling == 300);
    /* Delayed consumption pays observation age down; no additional 100 ms. */
    double delayed = reading_presentation_value(&state, true, 400, 3200, 1, 3260);
    assert(delayed > 380 && delayed < 400);
    assert(reading_presentation_value(&state, true, 500, 3300, 1, 3420) == 500);
    /* A source session/dropout resets immediately, including skipped UI frames. */
    assert(reading_presentation_value(&state, true, 9, 3421, 2, 3421) == 9);
    reading_presentation_value(&state, false, 0, 3422, 2, 3422);
    assert(!state.initialized);
    assert(reading_presentation_value(&state, true, 99, 3423, 2, 3423) == 99);
    reading_presentation_value(&state, true, NAN, 3424, 2, 3424);
    assert(!state.initialized);
    /* Millisecond wrap uses the same unsigned monotonic age as validity. */
    state = (reading_presentation_t){0};
    assert(reading_presentation_value(&state, true, 0, UINT32_MAX - 50, 3, UINT32_MAX - 50) == 0);
    assert(reading_presentation_value(&state, true, 120, UINT32_MAX - 20, 3, UINT32_MAX - 20) == 0);
    assert(reading_presentation_value(&state, true, 120, UINT32_MAX - 20, 3, 99) == 120);
    puts("reading presentation: bounds, reversal, convergence, age, dropout and wrap passed");
}
