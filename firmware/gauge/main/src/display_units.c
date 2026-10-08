#include "display_units.h"
#include <string.h>
#include <math.h>
#include <stdio.h>

const char *display_units_label(const char *unit, bool imperial)
{
    if (!unit) return "";
    if (strcmp(unit, "gear") == 0) return "";
    if (strcmp(unit, "deg") == 0) return "°";
    if (imperial) {
        if (strcmp(unit, "°C") == 0 || strcmp(unit, "degC") == 0) return "°F";
        if (strcmp(unit, "km/h") == 0 || strcmp(unit, "kph") == 0) return "mph";
        if (!strcmp(unit, "km")) return "mi";
        if (!strcmp(unit, "kPa") || !strcmp(unit, "Pa")) return "psi";
        if (!strcmp(unit, "g/s")) return "lb/min";
        if (!strcmp(unit, "L/h")) return "US gal/h";
        if (!strcmp(unit, "Nm")) return "lb·ft";
    }
    return unit;
}

double display_units_decimal(double canonical, const char *unit, bool imperial)
{
    if (!unit || !imperial) return canonical;
    if (!strcmp(unit, "degC") || !strcmp(unit, "°C")) return canonical * 1.8 + 32;
    if (!strcmp(unit, "kph") || !strcmp(unit, "km/h") || !strcmp(unit, "km")) return canonical * 0.621371;
    if (!strcmp(unit, "kPa")) return canonical * 0.1450377377;
    if (!strcmp(unit, "Pa")) return canonical * 0.0001450377377;
    if (!strcmp(unit, "g/s")) return canonical * 0.1322773573;
    if (!strcmp(unit, "L/h")) return canonical * 0.2641720524;
    if (!strcmp(unit, "Nm")) return canonical * 0.7375621493;
    return canonical;
}

void display_units_format(char *out, size_t length, double canonical, const char *unit, bool imperial)
{
    if (!out || length == 0) return;
    double shown = display_units_decimal(canonical, unit, imperial);
    if (!isfinite(shown)) { snprintf(out, length, "..."); return; }
    // Existing whole-number pages keep their legibility; new fractional units retain precision.
    int precision = !unit || !strcmp(unit, "rpm") || !strcmp(unit, "degC") || !strcmp(unit, "°C") ||
        !strcmp(unit, "kph") || !strcmp(unit, "km/h") || !strcmp(unit, "count") ||
        !strcmp(unit, "s") || !strcmp(unit, "min") || !strcmp(unit, "km") || !strcmp(unit, "Nm") ? 0 : 3;
    if (precision == 0) shown = floor(shown + .5); // Match Android whole-number display rounding.
    snprintf(out, length, "%.*f", precision, shown);
    if (precision && strchr(out, '.')) {
        size_t end = strlen(out);
        while (end && out[end-1] == '0') out[--end] = 0;
        if (end && out[end-1] == '.') out[--end] = 0;
    }
    if (!strcmp(out, "-0")) snprintf(out, length, "0");
}
