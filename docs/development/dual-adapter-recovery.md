# Dual-adapter and hardware recovery qualification

Prepared 2026-10-07 for source candidate `0.2.0-dev.42`. No physical dual-adapter
result is claimed: the owner still has one Vgate. Installed dev.40 remains the
last physically checked firmware. Do not start BLE/vehicle work unattended.

## Software boundary and offline evidence

- Schema 2 carries the primary ECM and optional TCM child in one atomic revision.
  Distinct explicit bindings are mandatory. The compiler permits only bounded ECM
  Mode 01 and captured TCM 2204FE/225503 definitions, with no TCM alerts. Duplicate
  numeric identifiers and mixed-source display pages are rejected.
- Two independent source workers own poll scheduling, parser/transaction state,
  response generation, reconnect delay and diagnostics. Shared discovery has one
  timeout budget, including admission and result wait. Workers do not catch up
  in a burst after a long operation.
- Source loss clears only that source's displayed values and alert sample validity;
  active severity remains unavailable rather than being resolved by missing data.
- Android debug Expert explicitly enables both adapters for the selected gauge.
  Editors remain separate; review/send include both. Each source has separate read
  evidence and backoff, and the universal pill aggregates both required links.
- Development capability `da:1` adds source-index reads; packet layouts remain v14/v15.
  Public `maxAdapterLinks=1`, `configWrite:false` and `ota:false` remain unchanged.
- Production C sanitizer fixtures cover independent adapter/diagnostic state,
  disconnect/reconnect, stale generations, pause, duplicate/unbound radio rejection,
  and source-specific alert invalidation. Android unit tests cover combined payload,
  bindings, child edits, bounds, per-gauge mode, source indices and safe import refusal.
  84 Android unit tests and 73 offline host tests passed, alongside production
  config/compiler and core C sanitizer fixtures, the ESP-IDF 5.4.1 build, Android
  debug build/lint and instrumentation compilation. Native UI tests and the dual
  hardware harness were compiled but not executed this session. No Android device
  was attached. These checks are software evidence only.

## Prepare once, without driving

1. Obtain a second supported adapter with a distinct address. On the bench, two
   independent BLE peripheral simulators may exercise transport, but cannot validate
   Jeep signal meaning. Do not connect one radio to both sources or infer identity
   from matching names. Keep the working single-source setup backed up privately.
2. Publish/install the exact signed candidate only with release authorization and
   through App/Wi-Fi. Record image/ELF identity and config revision/hash. Confirm
   normal physical display and touch separately from app/serial evidence.
3. Attach the TCM child to the engine vehicle in Expert, select both bindings, enable
   “Use both adapters on this gauge”, review all pages/primary alerts and send once.
   Protected running revision/hash must match the entire reviewed document.
4. Keep the vehicle parked. Verify engine RPM/coolant and existing TCM Gear/Temperature
   pages against the known references. Selector/temperature semantics remain bounded
   by the previous TCM evidence; this work adds no newly validated vehicle value.

## Fast matrix, then soak

| Case | Action | Required observation |
| --- | --- | --- |
| Single-source regression | Move one adapter ECM to TCM with one unchanged whole dashboard | Existing pages stay visible; inaccessible readings become unavailable; available readings and automatic retry work |
| Both present + phone | Start both, open app | Both source statuses and independent fault categories appear; correct role, ECU, revision and session |
| TCM absent at startup | Power only ECM | ECM reaches live readings despite repeated TCM discovery failure; child is unavailable; app remains usable |
| ECM absent at startup | Power only TCM | TCM reaches live readings independently; engine/alerts unavailable; no parent-radio dependency |
| TCM loss/recovery | Unplug TCM 15 seconds, reconnect | TCM values become unavailable; ECM stays current and session unchanged; only TCM session advances |
| ECM loss/recovery | Unplug ECM 15 seconds, reconnect | Reverse isolation; stale engine alerts never clear as healthy |
| Phone off/on | Disable phone Bluetooth 15 seconds, enable | Gauge sources continue; app reconnects and rereads settings/sources with no tap or uncertain write replay |
| Background/resume | Background app 30 seconds, return | Phone polling stops/resumes; gauge polling continues; new protected checks precede current status |
| Gauge reboot | Reboot with one adapter absent, then restore it | Settings/config/owner retained, healthy boot identity, bounded independent reconnection |
| No adapter / timeout / flood | Use read-only simulator no reply, delayed old reply, malformed reply, overflow | Unavailable data, bounded queues/drain, old generations discarded, other source and touch responsive |
| Maintenance update | Start approved App/Wi-Fi update | Both adapters explicitly paused/unavailable; transfer responsive; exact boot identity and both reconnect after close |
| Config interruption | Interrupt a reviewed config transfer or trial | Previous exact committed setup or confirmed new whole setup, never half ECM/TCM bindings; reconcile before retry |
| OTA reset/power loss | Follow existing OTA matrix | Exact running identity, stored/running config and owner readback; unknown outcome never success |
| Two hours under load | Both sources + phone, pages/alerts active | No cross-source attribution; bounded memory/queues, rates and deadline misses recorded, touch responsive |

Use the [OTA recovery matrix](ota-recovery-matrix.md) for existing reset helpers and
separate physical rail-power cases. Do not clear codes, erase bonds, write calibrations
or change eFuses during this matrix. Public capability promotion is a separate review.

## Read-only opt-in Android harness

Install the debug/test APKs without clearing the owner's data, and start/stop the
awake helper for the session. The already-configured gauge must have both real
adapters connected. The test does not configure adapters or send updates:

```sh
adb shell am instrument -w \
  -e class com.lstepnio.egauge.DualAdapterJourneyTest \
  -e allowDualGaugeRead true \
  com.lstepnio.egauge.test/androidx.test.runner.AndroidJUnitRunner
```

For an isolation case, also pass `-e interruptSource TCM` (or ECM). Within the
bounded two-minute wait unplug only that adapter, leave it out until the other
source's fresh read is recorded, then reconnect during the recovery wait. The
harness checks unchanged sibling session and whole configuration revision/hash.
It writes a private result under the app's external `dual-adapter-recovery` folder.
No opt-in means the test skips. Its physical execution is pending.

## Record the outcome

Retain private timestamped logs and captures under ignored `artifacts/`. Record exact
firmware/image identity, adapter models/drivers/address types, phone model/build,
config revision/hash, per-source achieved rate/P95 latency, stale intervals, reconnect
time, timeout/flood/drop counts, minimum internal/PSRAM memory and worker stack headroom.
Record physical LCD/touch, Android, serial and simulated observations separately.
Update [current state](../current-state.md) only after observed gates pass. Do not
call two-central-plus-phone coexistence qualified from configuration capacity or builds.
