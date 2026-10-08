#include "worker_health.h"
#include <stdatomic.h>

typedef struct {
    atomic_bool observed;
    atomic_uint_least32_t count, last_ms, maximum_gap_ms, drops;
} worker_record_t;
static worker_record_t workers[WORKER_COUNT];

static bool valid(worker_id_t worker) { return (unsigned)worker < WORKER_COUNT; }

void worker_health_progress(worker_id_t worker, uint32_t now_ms)
{
    if (!valid(worker)) return;
    worker_record_t *record = &workers[worker];
    bool observed = atomic_load(&record->observed);
    uint32_t previous = atomic_exchange(&record->last_ms, now_ms);
    if (observed) {
        uint32_t gap = now_ms - previous; /* Monotonic uint32 wrap is intentional. */
        uint_least32_t maximum = atomic_load(&record->maximum_gap_ms);
        while (gap > maximum && !atomic_compare_exchange_weak(&record->maximum_gap_ms, &maximum, gap)) {}
    }
    atomic_fetch_add(&record->count, 1);
    atomic_store(&record->observed, true);
}

void worker_health_queue_drop(worker_id_t worker)
{
    if (valid(worker)) atomic_fetch_add(&workers[worker].drops, 1);
}

worker_health_t worker_health_snapshot(worker_id_t worker, uint32_t now_ms)
{
    worker_health_t snapshot = {0};
    if (!valid(worker)) return snapshot;
    const worker_record_t *record = &workers[worker];
    snapshot.observed = atomic_load(&record->observed);
    if (snapshot.observed) {
        uint32_t age = now_ms - atomic_load(&record->last_ms);
        // A concurrent writer can publish a timestamp newer than the caller's clock sample.
        snapshot.age_ms = age <= INT32_MAX ? age : 0;
    }
    snapshot.progress_count = atomic_load(&record->count);
    snapshot.maximum_gap_ms = atomic_load(&record->maximum_gap_ms);
    snapshot.queue_drops = atomic_load(&record->drops);
    return snapshot;
}

const char *worker_health_name(worker_id_t worker)
{
    static const char *const names[WORKER_COUNT] = {
        "app", "ui", "primary_adapter", "child_adapter", "config", "ota", "wifi_server", "wifi_command"
    };
    return valid(worker) ? names[worker] : "unknown";
}
