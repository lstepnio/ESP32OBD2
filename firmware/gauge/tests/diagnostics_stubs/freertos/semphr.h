#pragma once
#include <assert.h>
typedef void *SemaphoreHandle_t;
extern int test_lock_busy;
static inline void *xSemaphoreCreateMutex(void) { return (void*)1; }
static inline int xSemaphoreTake(void *s, unsigned t) {
    (void)s;
    if (test_lock_busy) { assert(t != UINT32_MAX); return 0; }
    return 1;
}
static inline int xSemaphoreGive(void *s) { (void)s; return 1; }
