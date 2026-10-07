#pragma once
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/* The socket owner also owns close. Short syscall slices observe session loss;
 * one monotonic budget covers all fragments, so trickle traffic cannot extend it.
 * clock_ms wraps at 32 bits; budgets must be less than half that range. */
typedef uint32_t (*wifi_bulk_clock_fn)(void *context);
typedef bool (*wifi_bulk_active_fn)(void *context);
int wifi_bulk_io(int fd, uint8_t *bytes, size_t length, bool sending,
                 uint32_t started_ms, uint32_t budget_ms,
                 wifi_bulk_clock_fn clock_ms, wifi_bulk_active_fn active, void *context);
