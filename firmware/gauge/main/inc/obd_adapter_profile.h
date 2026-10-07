#pragma once
#include <stdint.h>
typedef struct { const char *id; const char *service; const char *tx; const char *rx; uint8_t wire_id; } obd_adapter_profile_t;
const obd_adapter_profile_t *obd_adapter_profile_find(const char *id);
#define OBD_BENCH_SERVICE "6f1a1000-9e3b-4f45-a714-69c9d23b6c00"
