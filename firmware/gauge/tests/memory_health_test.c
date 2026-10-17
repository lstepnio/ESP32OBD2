#include <assert.h>
#include <limits.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include "memory_health.h"
#include "esp_heap_caps.h"

static esp_alloc_failed_hook_t hook;
static unsigned critical_depth, heap_reads, log_calls;
static char last_log[512];
void test_memory_enter(void *lock) { (void)lock; assert(!critical_depth++); }
void test_memory_exit(void *lock) { (void)lock; assert(critical_depth-- == 1); }
esp_err_t heap_caps_register_failed_alloc_callback(esp_alloc_failed_hook_t callback) { hook=callback; return ESP_OK; }
size_t heap_caps_get_free_size(unsigned caps) { assert(!critical_depth); ++heap_reads; return 100000 + caps; }
size_t heap_caps_get_largest_free_block(unsigned caps) { assert(!critical_depth); ++heap_reads; return 50000 + caps; }
size_t heap_caps_get_minimum_free_size(unsigned caps) { assert(!critical_depth); ++heap_reads; return 40000 + caps; }
void test_memory_log(const char *tag, const char *format, ...) {
    (void)tag; assert(!critical_depth); ++log_calls;
    va_list args; va_start(args,format); vsnprintf(last_log,sizeof(last_log),format,args); va_end(args);
}
int main(void) {
    memory_health_init();
    assert(hook && log_calls==1 && strstr(last_log,"alloc_failures=0"));
    unsigned reads=heap_reads;
    hook(1600, MALLOC_CAP_INTERNAL|MALLOC_CAP_DMA, "ignored");
    hook(752, MALLOC_CAP_INTERNAL, "ignored");
    assert(log_calls==1 && heap_reads==reads); // No logging or heap queries inside the hook.
    memory_health_log("test");
    assert(strstr(last_log,"stage=test") && strstr(last_log,"alloc_failures=2"));
    assert(strstr(last_log,"last_size=752") && strstr(last_log,"last_caps=0x1"));
#if SIZE_MAX > UINT_MAX
    hook((size_t)UINT_MAX+1, MALLOC_CAP_SPIRAM, "ignored");
    memory_health_log("large");
    assert(strstr(last_log,"last_size=4294967295"));
#endif
    return 0;
}
