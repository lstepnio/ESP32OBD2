#pragma once

#include <stddef.h>
#include <stdint.h>

/* Local USB bench instrumentation. Never advertises an app capability. RX
 * records represent callback/mbuf fragments, not individual radio packets. */
#define OBD_TRACE_CHUNK 64
#define OBD_TRACE_LINE 384
typedef struct {
    uint64_t time_us;
    uint32_t sequence;
    uint32_t generation;
    uint32_t total;
    uint32_t offset;
    int32_t status;
    uint8_t source;
    uint8_t length;
    char event[20];
    uint8_t data[OBD_TRACE_CHUNK];
} obd_trace_record_t;

/* Returns 0 on invalid input or insufficient output capacity. */
size_t obd_trace_format(const obd_trace_record_t *record, char *out, size_t capacity);
void obd_trace_init(void);
void obd_trace_emit(unsigned source, uint32_t generation, const char *event,
                    const void *data, size_t length, int32_t status);
