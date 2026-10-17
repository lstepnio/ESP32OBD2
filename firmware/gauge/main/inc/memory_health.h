#pragma once
#include <stddef.h>
#include <stdint.h>

/* The allocation hook updates only internal counters in a copy-only critical
 * section. Logging runs in task context, never from the allocator/callback. */
void memory_health_init(void);
void memory_health_log(const char *stage);
void memory_health_allocation_failed(size_t size, uint32_t caps, const char *function);
