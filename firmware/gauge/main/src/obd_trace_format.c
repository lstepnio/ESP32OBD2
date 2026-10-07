#include "obd_trace.h"
#include <inttypes.h>
#include <stdio.h>
#include <string.h>

size_t obd_trace_format(const obd_trace_record_t *r, char *out, size_t capacity)
{
    if (!r || !out || r->length > OBD_TRACE_CHUNK ||
        r->offset > r->total || r->length > r->total - r->offset) return 0;
    size_t event_length = 0;
    while (event_length < sizeof(r->event) && r->event[event_length]) {
        char c = r->event[event_length++];
        if (!((c >= 'a' && c <= 'z') || c == '_')) return 0;
    }
    if (!event_length || event_length == sizeof(r->event)) return 0;
    char hex[2 * OBD_TRACE_CHUNK + 1];
    static const char digits[] = "0123456789abcdef";
    for (unsigned i = 0; i < r->length; ++i) {
        hex[2 * i] = digits[r->data[i] >> 4];
        hex[2 * i + 1] = digits[r->data[i] & 15];
    }
    hex[2 * r->length] = 0;
    int count = snprintf(out, capacity,
        "{\"schema\":1,\"seq\":%" PRIu32 ",\"t_us\":%" PRIu64
        ",\"source\":%u,\"generation\":%" PRIu32 ",\"event\":\"%s\""
        ",\"status\":%" PRId32 ",\"offset\":%" PRIu32
        ",\"total\":%" PRIu32 ",\"hex\":\"%s\"}",
        r->sequence, r->time_us, r->source, r->generation, r->event,
        r->status, r->offset, r->total, hex);
    return count > 0 && (size_t)count < capacity ? (size_t)count : 0;
}
