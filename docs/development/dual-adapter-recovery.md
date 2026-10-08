# Dual-adapter and hardware recovery qualification

This maintained procedure qualifies the current development runtime. Current image,
software checks and device availability are in [current state](../current-state.md).
The owner has one adapter; physical dual operation is **QUAL-04** in
[the backlog](../backlog.md). Do not start vehicle/BLE work unattended.

## Software boundary and offline evidence

- Schema 2 carries primary/optional child bindings in one atomic revision. Distinct
  bindings are required for two workers. Per-definition routing permits common Mode
  01 and the captured Mode 22 definitions on a primary or child transport.
- One logical dashboard supports mixed-controller Dual pages and catalog TCM alerts.
  Expert changes bindings only; source loss clears only that source's current samples.
  Missing data does not resolve active alert severity as healthy.
- Workers independently own polling, parsers, generations, diagnostics and retry.
  Shared discovery has one deadline; workers never catch up in an unbounded burst.
- `da:1` source reads preserve v14/v15 layouts. Public qualified link count remains
  one, and `configWrite:false`/`ota:false` remain unchanged.
- Focused source/sanitizer fixtures are recorded in [hardening evidence](../evidence/offline-hardening.md).
  These checks do not qualify concurrent radios, display behavior or vehicle signals.

## Prepare once, without driving

1. Obtain a second supported adapter with a distinct address. On the bench, two
   independent BLE peripheral simulators may exercise transport, but cannot validate
   Jeep signal meaning. Do not connect one radio to both sources or infer identity
   from matching names. Keep the working single-source setup backed up privately.
2. Publish/install the exact signed candidate only with release authorization and
   through App/Wi-Fi. Record image/ELF identity and config revision/hash. Confirm
   normal physical display and touch separately from app/serial evidence.
3. Attach the TCM child to the engine vehicle in Expert, select both bindings, enable
   “Use both adapters on this gauge”, review all pages/alerts and send once.
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
