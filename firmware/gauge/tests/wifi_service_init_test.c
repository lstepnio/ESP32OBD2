#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <stddef.h>

typedef int esp_err_t;
typedef void *TaskHandle_t;
#define ESP_OK 0
#define ESP_ERR_NO_MEM 1
#define pdPASS 1
#define portMAX_DELAY 0
#define FRAME_MAX_PLAINTEXT 8448
#define MALLOC_CAP_SPIRAM 1
#define MALLOC_CAP_8BIT 2
#define PHASE_OFF 0
#define RESULT_OK 0
typedef struct { uint8_t op; uint32_t sequence; } command_t;
static void *state_lock, *command_queue;
static uint8_t *frame_ciphertext, *frame_plaintext;
static bool server_started;
static unsigned allocations, failure, live;
static void *live_tasks[2];
static void *allocate(void) {
    if (++allocations == failure) return NULL;
    void *p = malloc(16);
    assert(p); ++live; return p;
}
static void release(void *p) { if (p) { assert(live); --live; free(p); } }
static void *xSemaphoreCreateMutex(void) { return allocate(); }
static void *xQueueCreate(unsigned count, size_t size) { assert(count == 2 && size == sizeof(command_t)); return allocate(); }
static void *heap_caps_malloc(size_t size, unsigned caps) {
    assert(size == FRAME_MAX_PLAINTEXT && caps == (MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
    return allocate();
}
static void heap_caps_free(void *p) { release(p); }
static void vSemaphoreDelete(void *p) { release(p); }
static void vQueueDelete(void *p) { release(p); }
static void vTaskDelete(void *p) {
    for (unsigned i=0;i<2;++i) if (live_tasks[i] == p) live_tasks[i] = NULL;
    release(p);
}
static void xSemaphoreTake(void *p, unsigned wait) { assert(p); (void)wait; }
static void xSemaphoreGive(void *p) { assert(p); }
static void clear_session_locked(unsigned phase, unsigned result) { assert(!phase && !result); }
static void server_task(void *arg) { (void)arg; }
static void worker_task(void *arg) { (void)arg; }
static int xTaskCreate(void (*task)(void *), const char *name, unsigned stack, void *arg,
                       unsigned priority, TaskHandle_t *out) {
    (void)task; (void)name; (void)arg;
    assert(priority == 4 && (stack == 8192 || stack == 5120));
    *out = allocate();
    live_tasks[task == server_task ? 0 : 1] = *out;
    return *out ? pdPASS : 0;
}
#include "wifi_service_init.inc"

int main(void) {
    for (failure = 1; failure <= 6; ++failure) {
        allocations = 0;
        assert(wifi_bulk_init() == ESP_ERR_NO_MEM);
        assert(!wifi_bulk_available() && live == 0);
        assert(!state_lock && !command_queue && !frame_ciphertext && !frame_plaintext);
    }
    failure = allocations = 0;
    assert(wifi_bulk_init() == ESP_OK && wifi_bulk_available());
    assert(live == 6 && allocations == 6);
    assert(wifi_bulk_init() == ESP_OK && allocations == 6);
    vTaskDelete(live_tasks[0]); vTaskDelete(live_tasks[1]);
    release(state_lock); release(command_queue);
    release(frame_ciphertext); release(frame_plaintext);
    assert(live == 0);
    return 0;
}
