#pragma once
#include <stdbool.h>
#include <stddef.h>
#include "gauge_surface.h"

#define GAUGE_CONTENT_RADIUS (GAUGE_REFERENCE_WIDTH / 2 - GAUGE_REFERENCE_CONTENT_INSET)
#define GAUGE_DECORATION_RADIUS (GAUGE_REFERENCE_WIDTH / 2 - GAUGE_REFERENCE_DECORATION_INSET)

/* Native 240 px coordinates. Bounds describe actual glyph ink, not label boxes. */
bool        gauge_layout_fits(int left, int top, int right, int bottom);
const char *gauge_layout_label(const char *name, const char *unit);
const char *gauge_layout_unit(const char *label, const char *unit);
void        gauge_layout_alert_alias(char *out, size_t size, const char *label);
