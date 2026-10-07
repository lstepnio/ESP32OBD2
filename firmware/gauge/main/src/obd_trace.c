#include "obd_trace.h"
#include "sdkconfig.h"

#if CONFIG_EGAUGE_OBD_TRACE
#include <stdbool.h>
#include <stdatomic.h>
#include <stdio.h>
#include <string.h>
#include "esp_timer.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"

static QueueHandle_t trace_queue;
static atomic_uint trace_sequence;
static atomic_uint trace_drops;

static void trace_task(void *arg)
{
    (void)arg;
    obd_trace_record_t record;
    char line[OBD_TRACE_LINE];
    unsigned reported_drops = 0;
    while (true) {
        if (xQueueReceive(trace_queue, &record, pdMS_TO_TICKS(500)) == pdTRUE &&
            obd_trace_format(&record, line, sizeof(line)))
            printf("EGAUGE_OBD %s\n", line);
        unsigned drops = atomic_load(&trace_drops);
        if (drops != reported_drops) {
            /* This record deliberately bypasses the full queue so loss is
             * visible. Replay must reject a capture containing trace loss. */
            record = (obd_trace_record_t){.time_us = esp_timer_get_time(),
                .sequence = atomic_fetch_add(&trace_sequence, 1),
                .status = (int32_t)drops};
            strcpy(record.event, "trace_loss");
            if (obd_trace_format(&record, line, sizeof(line)))
                printf("EGAUGE_OBD %s\n", line);
            reported_drops = drops;
        }
    }
}

void obd_trace_init(void)
{
    if (trace_queue) return;
    trace_queue = xQueueCreate(32, sizeof(obd_trace_record_t));
    if (!trace_queue || xTaskCreate(trace_task, "obd_trace", 3072, NULL, 2, NULL) != pdPASS) {
        if (trace_queue) vQueueDelete(trace_queue);
        trace_queue = NULL;
        ESP_LOGE("OBD_TRACE", "Capture unavailable: allocation failed");
        return;
    }
    obd_trace_emit(0, 0, "trace_start", NULL, 0, 0);
}

void obd_trace_emit(unsigned source, uint32_t generation, const char *event,
                    const void *data, size_t length, int32_t status)
{
    if (!trace_queue || source >= 2 || !event || strlen(event) >= 20 ||
        (length && !data) || length > UINT32_MAX) return;
    uint64_t now = esp_timer_get_time();
    for (size_t offset = 0; offset < length || offset == 0; offset += OBD_TRACE_CHUNK) {
        size_t chunk = length - offset;
        if (chunk > OBD_TRACE_CHUNK) chunk = OBD_TRACE_CHUNK;
        obd_trace_record_t record = {.time_us = now, .source = source,
            .sequence = atomic_fetch_add(&trace_sequence, 1), .generation = generation,
            .total = length, .offset = offset, .length = chunk, .status = status};
        strcpy(record.event, event);
        if (chunk) memcpy(record.data, (const uint8_t *)data + offset, chunk);
        /* Never block the BLE host or polling task on serial output. */
        if (xQueueSend(trace_queue, &record, 0) != pdTRUE) atomic_fetch_add(&trace_drops, 1);
        if (!length) break;
    }
}
#else
void obd_trace_init(void) {}
void obd_trace_emit(unsigned source, uint32_t generation, const char *event,
                    const void *data, size_t length, int32_t status)
{
    (void)source; (void)generation; (void)event; (void)data; (void)length; (void)status;
}
#endif
