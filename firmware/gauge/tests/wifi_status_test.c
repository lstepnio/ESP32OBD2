#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>

#define WIFI_BULK_STATUS_SIZE 112
#define PHASE_READY 2
#define configTICK_RATE_HZ 100
static bool     server_started;
static unsigned status_mux, critical_depth;
static uint8_t  status_cache[WIFI_BULK_STATUS_SIZE];
static uint32_t status_expires_at, tick;
#define portENTER_CRITICAL(lock)                                                                                       \
    do                                                                                                                 \
    {                                                                                                                  \
        (void)(lock);                                                                                                  \
        assert(!critical_depth++);                                                                                     \
    } while (0)
#define portEXIT_CRITICAL(lock)                                                                                        \
    do                                                                                                                 \
    {                                                                                                                  \
        (void)(lock);                                                                                                  \
        assert(critical_depth-- == 1);                                                                                 \
    } while (0)
static uint32_t xTaskGetTickCount(void)
{
    assert(!critical_depth);
    return tick;
}
static void write_u32(uint8_t *p, uint32_t n)
{
    for (unsigned i = 0; i < 4; ++i)
        p[i] = n >> (8 * i);
}
static uint32_t read_u32(const uint8_t *p)
{
    return p[0] | (uint32_t)p[1] << 8 | (uint32_t)p[2] << 16 | (uint32_t)p[3] << 24;
}
#include "wifi_status.inc"

int main(void)
{
    uint8_t out[112];
    assert(wifi_bulk_status(out) == 0);
    server_started = true;
    assert(wifi_bulk_status(NULL) == 0);
    memset(status_cache, 0x5a, sizeof(status_cache));
    status_cache[0]   = 9;
    status_cache[1]   = PHASE_READY;
    tick              = 1000;
    status_expires_at = 1500;
    assert(wifi_bulk_status(out) == 112 && read_u32(out + 52) == 5);
    assert(out[20] == 0x5a && out[90] == 0x5a);
    tick = 1500;
    assert(wifi_bulk_status(out) == 112);
    for (unsigned i = 20; i < 112; ++i)
        assert(out[i] == 0);
    // The public caller's copy is scrubbed; the published cache is not mutated.
    assert(status_cache[20] == 0x5a);
    tick              = UINT32_MAX - 199;
    status_expires_at = 300;
    assert(wifi_bulk_status(out) == 112 && read_u32(out + 52) == 5);
    tick = 301;
    assert(wifi_bulk_status(out) == 112 && out[20] == 0);
    return 0;
}
