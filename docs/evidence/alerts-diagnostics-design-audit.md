# Alert and diagnostic design audit, 2026-10-07

> Historical evidence. Versions, measurements and unfinished steps below describe that session.
> Current support is in [current state](../current-state.md); remaining work is in
> [the backlog](../backlog.md). These notes are not standing implementation instructions.

Source baseline: 7c140d8 on codex/dual-adapter-runtime. No physical vehicle, phone or
gauge checks were performed. The maintained proposal is [ADR-015](../architecture/alerts-and-diagnostics.md).

## Source findings

- `main.c` diagnostic workers choose 7E8 or 7E9 from the configured adapter role;
  `diagnostics_state.c` retains two physical-source snapshots. This does not cover
  two diagnostic endpoints on one primary transport. `ble_obd.c` also checks the
  selected diagnostic ECU against its discovery identity.
- `main.c` selects gauge fault presentation from the current page's first reading
  source; `ui.c` shows a compact CEL/DTC/TCM badge. This is not global vehicle aggregation.
- `DiagnosticPresentation.kt` exposes category availability, while `CarScreen.kt`
  flattens faults without rendering empty-category status. An empty fault list alone
  cannot explain unsupported, unavailable or not-yet-checked categories.
- `alert_engine.c` has local threshold summary/freshness/dwell behavior, not a retained
  multi-producer episode/transition framework. Phone `VehiclePollSchedule.kt` has
  foreground polling; no continuously connected background notification path was verified.

## Synthetic production-scheduler check

Compiled a temporary fixture against `firmware/gauge/main/src/poll_scheduler.c` and
its production header. Initialized one PID with a 30,000 ms interval, advanced a
synthetic clock by 100 ms from 0 through 599,900 ms, and counted returned job kinds.
The exact fixture loop was:

```c
poll_scheduler_t scheduler;
const uint32_t intervals[1] = {30000};
poll_scheduler_init(&scheduler);
unsigned mil = 0, dtc = 0, pid = 0;
for (uint32_t now = 0; now < 600000; now += 100) {
    poll_job_t job = poll_scheduler_next(&scheduler, now, intervals, 1);
    if (job.kind == POLL_JOB_MIL) mil++;
    if (job.kind == POLL_JOB_DTC) dtc++;
    if (job.kind == POLL_JOB_PID) pid++;
}
```

Result: **MIL=3, DTC=0, PID=20**. Background admission requires ten normal reads;
MIL gets priority when due. This input starves DTC polling. The fixture did not model
serial timing or invoke a physical adapter and is not a ten-minute hardware soak.
Phase 1 must retain this input as a regression and verify fair diagnostic progress
without catch-up bursts or excessive foreground PID delay.

## Plan verification

Repository validation passed 4 schemas, 9 examples, 24 rejection cases, signed
decode vector, generated catalog/design-token parity and 105 document link sets.
All 12 documentation-policy regression tests and whitespace review passed. The
synthetic scheduler result above was reproduced after writing the plan.

No runtime change, new wire bytes, capability, release, installation or fault
clearing occurred here.
