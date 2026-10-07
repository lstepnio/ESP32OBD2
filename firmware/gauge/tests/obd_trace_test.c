#include "obd_trace.h"
#include <assert.h>
#include <limits.h>
#include <stdio.h>
#include <string.h>

int main(void)
{
    obd_trace_record_t r = {.time_us = UINT64_MAX, .sequence = UINT32_MAX,
        .generation = UINT32_MAX, .total = UINT32_MAX,
        .offset = UINT32_MAX - 64, .status = INT32_MIN, .source = 1, .length = 64};
    strcpy(r.event, "rx");
    memset(r.data, 255, sizeof(r.data));
    char out[OBD_TRACE_LINE];
    size_t n = obd_trace_format(&r, out, sizeof(out));
    assert(n && n < sizeof(out) && strlen(out) == n);
    assert(strstr(out, "18446744073709551615"));
    assert(strstr(out, "ffffffff"));
    char short_out[8];
    assert(!obd_trace_format(&r, short_out, sizeof(short_out)));
    r.length = 65;
    assert(!obd_trace_format(&r, out, sizeof(out)));
    r.length = 64; r.offset++;
    assert(!obd_trace_format(&r, out, sizeof(out)));
    r.offset--; strcpy(r.event, "bad\"event");
    assert(!obd_trace_format(&r, out, sizeof(out)));
    memset(r.event, 'a', sizeof(r.event));
    assert(!obd_trace_format(&r, out, sizeof(out)));
    puts("OBD trace bounds and JSON encoding passed");
}
