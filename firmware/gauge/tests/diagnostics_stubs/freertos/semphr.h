#pragma once
typedef void *SemaphoreHandle_t;
static inline void *xSemaphoreCreateMutex(void) { return (void*)1; }
static inline int xSemaphoreTake(void *s, unsigned t) { (void)s; (void)t; return 1; }
static inline int xSemaphoreGive(void *s) { (void)s; return 1; }
