#pragma once
#include <stdlib.h>
#include <stdbool.h>
extern bool host_psram_failure;
#define MALLOC_CAP_SPIRAM 1
#define MALLOC_CAP_8BIT 2
static inline void *heap_caps_calloc(size_t count, size_t size, unsigned caps)
{
    (void)caps;
    return host_psram_failure ? NULL : calloc(count, size);
}
