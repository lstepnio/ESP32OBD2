#pragma once

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

/* Values and alert thresholds stay in their canonical metric units. */
double display_units_decimal(double canonical, const char *unit, bool imperial);
void display_units_format(char *out, size_t length, double canonical, const char *unit, bool imperial);
const char *display_units_label(const char *unit, bool imperial);
