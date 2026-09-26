#pragma once

#include <stdbool.h>
#include <stddef.h>

/* Non-recursive structural guard applied before the recursive JSON parser. */
bool json_guard_shape(const char *bytes, size_t length, unsigned maximum_depth);
