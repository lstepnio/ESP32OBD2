#include "worker_health.h"
#include <assert.h>
#include <limits.h>
#include <stdio.h>

int main(void)
{
    assert(!worker_health_snapshot(WORKER_TCM, 1000).observed);
    worker_health_progress(WORKER_ECM, UINT32_MAX - 10U);
    worker_health_progress(WORKER_ECM, 20);
    worker_health_queue_drop(WORKER_ECM);
    worker_health_t ecm = worker_health_snapshot(WORKER_ECM, 40);
    assert(ecm.observed && ecm.progress_count == 2 && ecm.age_ms == 20);
    assert(ecm.maximum_gap_ms == 31 && ecm.queue_drops == 1);
    assert(!worker_health_snapshot(WORKER_TCM, 40).observed);
    worker_health_progress(WORKER_UI, 0);
    assert(worker_health_snapshot(WORKER_UI, 8).observed);
    assert(worker_health_snapshot(WORKER_UI, 8).age_ms == 8);
    worker_health_progress(WORKER_CONFIG, 101);
    assert(worker_health_snapshot(WORKER_CONFIG, 100).age_ms == 0);
    /* A responding sibling does not refresh a stalled worker. */
    for (unsigned i = 1; i <= 10000; ++i) worker_health_progress(WORKER_APP, i);
    assert(worker_health_snapshot(WORKER_APP, 10000).maximum_gap_ms == 1);
    assert(worker_health_snapshot(WORKER_UI, 10000).age_ms == 10000);
    worker_health_progress((worker_id_t)-1, 1);
    worker_health_queue_drop(WORKER_COUNT);
    assert(!worker_health_snapshot(WORKER_COUNT, 1).observed);
    assert(worker_health_snapshot(WORKER_ECM, 40).queue_drops == 1);
    puts("worker health fixtures passed");
}
