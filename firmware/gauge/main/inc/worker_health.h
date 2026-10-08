#pragma once
#include <stdbool.h>
#include <stdint.h>

typedef enum {
    WORKER_APP, WORKER_UI, WORKER_ECM, WORKER_TCM,
    WORKER_CONFIG, WORKER_OTA, WORKER_WIFI_SERVER, WORKER_WIFI_COMMAND,
    WORKER_COUNT
} worker_id_t;

typedef struct {
    bool observed;
    uint32_t progress_count;
    uint32_t age_ms;
    uint32_t maximum_gap_ms;
    uint32_t queue_drops;
} worker_health_t;

/* Nonblocking diagnostics, not an automatic reboot policy. One progress writer per slot. */
void worker_health_progress(worker_id_t worker, uint32_t now_ms);
void worker_health_queue_drop(worker_id_t worker);
worker_health_t worker_health_snapshot(worker_id_t worker, uint32_t now_ms);
const char *worker_health_name(worker_id_t worker);
