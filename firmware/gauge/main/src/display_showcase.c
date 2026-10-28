#include "display_showcase.h"
#include <stddef.h>

/* Presentation fixtures only. Never feed adapter, alert history or vehicle state. */
#define ONE(n, r, l, u, v, lo, hi)                                                                                     \
    .name = n, .renderer = r, .metrics = 1, .labels = {l}, .units = {u}, .values = {v}, .minimum = lo, .maximum = hi,  \
    .present = {true}, .count_known = true
#define DUAL                                                                                                           \
    .name = "Mixed readings", .renderer = 4, .metrics = 2, .labels = {"Gear", "RPM"}, .units = {"gear", "rpm"},        \
    .values = {13, 675}, .minimum = 0, .maximum = 8000, .present = {true, true}, .count_known = true
static const display_showcase_frame_t frames[] = {
    {ONE("RPM", 0, "RPM", "rpm", 2840, 0, 8000)},
    {ONE("Coolant", 1, "Coolant", "°C", 92, 40, 130)},
    {ONE("Coolant", 2, "Coolant", "°C", 92, 40, 130)},
    {ONE("Coolant", 3, "Coolant", "°C", 92, 40, 130)},
    {DUAL},
    {.name             = "Gear and RPM arc",
     .renderer         = 5,
     .metrics          = 2,
     .arc_metric_index = 1,
     .labels           = {"Gear", "RPM"},
     .units            = {"gear", "rpm"},
     .values           = {13, 675},
     .minimum          = 0,
     .maximum          = 8000,
     .present          = {true, true},
     .count_known      = true},
    {.name             = "Reverse and RPM arc",
     .renderer         = 5,
     .metrics          = 2,
     .arc_metric_index = 1,
     .labels           = {"Gear", "RPM"},
     .units            = {"gear", "rpm"},
     .values           = {11, 1700},
     .minimum          = 0,
     .maximum          = 8000,
     .present          = {true, true},
     .count_known      = true},
    {.name             = "Coolant arc and gear",
     .renderer         = 5,
     .metrics          = 2,
     .arc_metric_index = 0,
     .labels           = {"Coolant", "Gear"},
     .units            = {"°C", "gear"},
     .values           = {92, 0},
     .minimum          = 40,
     .maximum          = 130,
     .present          = {true, true},
     .count_known      = true},
    {.name             = "Arc source unavailable",
     .renderer         = 5,
     .metrics          = 2,
     .arc_metric_index = 1,
     .labels           = {"Gear", "RPM"},
     .units            = {"gear", "rpm"},
     .values           = {1, 675},
     .minimum          = 0,
     .maximum          = 8000,
     .present          = {true, false},
     .count_known      = true},
    {.name        = "Mixed units",
     .renderer    = 4,
     .metrics     = 2,
     .labels      = {"Coolant", "Gear"},
     .units       = {"°C", "gear"},
     .values      = {92, 1},
     .minimum     = 0,
     .maximum     = 130,
     .present     = {true, true},
     .count_known = true},
    {ONE("Fuel rail pressure", 0, "Fuel rail pressure", "kPa", 10330.175, 0, 20000)},
    {ONE("Ignition timing", 0, "Ignition timing", "deg", -12.5, -30, 50)},
    {ONE("Input speed", 0, "Input speed", "rpm", 16383, 0, 20000)},
    {ONE("Battery", 2, "Battery", "V", 13.8, 8, 16)},
    {DUAL, .mil = true, .faults = 4},
    {ONE("Coolant", 0, "Coolant", "°C", 112, 40, 130), .severity = 1, .condition = "Coolant"},
    {ONE("Coolant", 1, "Coolant", "°C", 112, 40, 130), .mil = true, .faults = 4, .severity = 1, .condition = "Coolant"},
    {ONE("Coolant", 2, "Coolant", "°C", 112, 40, 130), .mil = true, .faults = 4, .severity = 1, .condition = "Coolant"},
    {ONE("Coolant", 3, "Coolant", "°C", 112, 40, 130), .mil = true, .faults = 4, .severity = 1, .condition = "Coolant"},
    {DUAL, .mil = true, .faults = 4, .severity = 1, .condition = "Coolant"},
    {ONE("Coolant", 0, "Coolant", "°C", 118, 40, 130), .severity = 2, .condition = "Coolant", .attention = true},
    {ONE("Coolant", 0, "Coolant", "°C", 118, 40, 130), .mil = true, .faults = 4, .severity = 2, .condition = "Coolant"},
    {.name      = "Lost child",
     .renderer  = 4,
     .metrics   = 2,
     .labels    = {"Gear", "RPM"},
     .units     = {"gear", "rpm"},
     .values    = {13, 675},
     .maximum   = 8000,
     .present   = {false, true},
     .mil       = true,
     .severity  = 1,
     .last_seen = true,
     .condition = "ATF"},
    {.name      = "Both absent",
     .renderer  = 4,
     .metrics   = 2,
     .labels    = {"Gear", "RPM"},
     .units     = {"gear", "rpm"},
     .maximum   = 8000,
     .present   = {false, false},
     .mil       = true,
     .severity  = 1,
     .last_seen = true,
     .condition = "ATF"},
    {ONE("RPM", 0, "RPM", "rpm", 675, 0, 8000), .faults = 2},

};
_Static_assert(sizeof(frames) / sizeof(frames[0]) <= 64, "Showcase wire frame limit");
#undef ONE
#undef DUAL
unsigned display_showcase_count(void)
{
    return sizeof(frames) / sizeof(frames[0]);
}
const display_showcase_frame_t *display_showcase_frame(unsigned index)
{
    return index < display_showcase_count() ? &frames[index] : NULL;
}
bool display_showcase_apply(display_showcase_t *s,
                            bool                enabled,
                            uint8_t             seconds,
                            uint32_t            sequence,
                            uint32_t            now,
                            bool                vehicle_ready,
                            bool                maintenance,
                            bool                attention)
{
    if (!s || !sequence || (seconds && (seconds < 5 || seconds > 30)) || (enabled && !seconds))
        return false;
    if (s->sequence == sequence)
        return true; /* Duplicate does not restart its deadline. */
    s->sequence = sequence;
    s->result   = SHOWCASE_APPLIED;
    if (enabled && (vehicle_ready || maintenance || attention))
    {
        s->result = SHOWCASE_REFUSED;
        return true;
    }
    s->active  = enabled;
    s->index   = 0;
    s->seconds = seconds;
    s->started = s->changed = now;
    return true;
}
void display_showcase_next(display_showcase_t *s, uint32_t now)
{
    if (!s || !s->active)
        return;
    s->index   = (s->index + 1) % display_showcase_count();
    s->changed = now;
}
bool display_showcase_tick(display_showcase_t *s, uint32_t now, bool interrupt)
{
    if (!s || !s->active)
        return false;
    if (interrupt || now - s->started >= DISPLAY_SHOWCASE_DURATION_MS)
    {
        s->active = false;
        return true;
    }
    if (now - s->changed >= (uint32_t)s->seconds * 1000)
    {
        display_showcase_next(s, now);
        return true;
    }
    return false;
}
