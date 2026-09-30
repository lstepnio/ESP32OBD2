#include "display_units.h"
#include <limits.h>
#include <string.h>

static int32_t rounded_divide(int64_t numerator, int64_t denominator)
{
    int64_t value = (numerator >= 0 ? numerator + denominator / 2 :
                     numerator - denominator / 2) / denominator;
    if (value > INT32_MAX) return INT32_MAX;
    if (value < INT32_MIN) return INT32_MIN;
    return (int32_t)value;
}

int32_t display_units_value(int32_t canonical, const char *unit, bool imperial)
{
    if (!imperial || !unit) return canonical;
    if (strcmp(unit, "°C") == 0 || strcmp(unit, "degC") == 0)
        return rounded_divide((int64_t)canonical * 9 + 160, 5);
    if (strcmp(unit, "km/h") == 0 || strcmp(unit, "kph") == 0)
        return rounded_divide((int64_t)canonical * 621371, 1000000);
    return canonical;
}

const char *display_units_label(const char *unit, bool imperial)
{
    if (!unit) return "";
    if (imperial) {
        if (strcmp(unit, "°C") == 0 || strcmp(unit, "degC") == 0) return "°F";
        if (strcmp(unit, "km/h") == 0 || strcmp(unit, "kph") == 0) return "mph";
    }
    return unit;
}
