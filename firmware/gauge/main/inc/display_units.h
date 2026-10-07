#pragma once

#include <stdbool.h>
#include <stdint.h>

/* Values and alert thresholds stay in their canonical metric units. */
int32_t display_units_value(int32_t canonical, const char *unit, bool imperial);
const char *display_units_label(const char *unit, bool imperial);
