#include "gauge_layout.h"
#include <stdio.h>
#include <string.h>
#include <ctype.h>

bool gauge_layout_fits(int left, int top, int right, int bottom)
{
    if (left > right || top > bottom || left < 0 || top < 0 || right > 240 || bottom > 240)
        return false;
    int xs[2] = {left - 120, right - 120}, ys[2] = {top - 120, bottom - 120};
    for (unsigned x = 0; x < 2; ++x)
        for (unsigned y = 0; y < 2; ++y)
            if (xs[x] * xs[x] + ys[y] * ys[y] > GAUGE_CONTENT_RADIUS * GAUGE_CONTENT_RADIUS)
                return false;
    return true;
}
static bool contains(const char *text, const char *word)
{
    if (!text || !word)
        return false;
    for (; *text; ++text)
    {
        size_t i = 0;
        while (word[i] && text[i] && tolower((unsigned char)text[i]) == tolower((unsigned char)word[i]))
            ++i;
        if (!word[i])
            return true;
    }
    return false;
}
const char *gauge_layout_label(const char *name, const char *unit)
{
    if (!name || !*name)
        return "Reading";
    if (unit && !strcmp(unit, "gear"))
    {
        if (contains(name, "target"))
            return "Target gear";
        if (contains(name, "command"))
            return "Commanded gear";
        if (contains(name, "select"))
            return "Selected gear";
        return "Gear";
    }
    if (contains(name, "rpm") && contains(name, "engine"))
        return "RPM";
    if (contains(name, "coolant"))
        return "Coolant";
    if (contains(name, "tcm temp"))
        return "TCM temp";
    if (contains(name, "transmission temperature"))
        return "Trans temp";
    if (contains(name, "intake air temperature"))
        return "Intake air";
    if (contains(name, "fuel rail pressure"))
        return "Fuel rail pressure";
    return name;
}
const char *gauge_layout_unit(const char *label, const char *unit)
{
    if (!unit || !strcmp(unit, "gear") || (contains(label, "rpm") && !strcmp(unit, "rpm")))
        return "";
    return unit;
}
void gauge_layout_alert_alias(char *out, size_t size, const char *label)
{
    if (!out || !size)
        return;
    const char *alias = label && *label ? label : "Alert";
    if (contains(alias, "coolant"))
        alias = "Coolant";
    else if (contains(alias, "tcm temp"))
        alias = "TCM temp";
    else if (contains(alias, "transmission temperature"))
        alias = "Trans temp";
    else if (contains(alias, "atf"))
        alias = "ATF";
    else if (contains(alias, "fuel rail"))
        alias = "Fuel rail";
    else if (contains(alias, "voltage") || contains(alias, "battery"))
        alias = "Voltage";
    else if (contains(alias, "oil pressure"))
        alias = "Oil press";
    else if (contains(alias, "oil temp"))
        alias = "Oil temp";
    snprintf(out, size, "%s", alias);
}
