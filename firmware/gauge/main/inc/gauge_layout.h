#pragma once
#include <stdbool.h>
#include <stddef.h>

/* Native 240 px coordinates. Bounds describe actual glyph ink, not label boxes. */
bool gauge_layout_fits(int left, int top, int right, int bottom);
const char *gauge_layout_label(const char *name, const char *unit);
const char *gauge_layout_unit(const char *label, const char *unit);
void gauge_layout_alert_alias(char *out, size_t size, const char *label);
