# Dual-adapter and hardware recovery qualification

This maintained procedure qualifies the current development runtime. Current image,
software checks and device availability are in [current state](../current-state.md).
Physical dual operation is **QUAL-04** in [the backlog](../backlog.md).
Check current device availability in [current state](../current-state.md); do not
start vehicle/BLE work unattended.

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

## Source recovery ownership and offline regression

The shared NimBLE scanner/initiator has one mutex owner. Each source keeps its own
link, parser, retry backoff and generation. A failed source must not reset the host,
disconnect its healthy sibling or change saved bindings. Worker backoff remains
500 ms, doubling to an 8 s cap after unsuccessful attempts and resetting on readiness.

The source admission budget is now separate from its actual discovery/connect
allowance, within one 26 s monotonic deadline: at most 13 s for queued scanner
admission, 12 s for that source's operation and 1 s for cancellation cleanup.
An uncontended absent source still spends at most 12 s discovering/connecting,
plus bounded cleanup. Previously waiting behind a 12 s sibling attempt could
consume the entire 12 s deadline before the queued source began scanning.
That starvation path is a source-level defect consistent with the owner report
that unplugging TCM allowed ECM to connect. It does not establish the physical
cause without a capture of the failing startup.

GAP and discovery/subscription callbacks carry an immutable per-attempt token.
Callbacks arriving with a retired token are rejected before applying discovered
handles or completing the new attempt. GAP/GATT callbacks hold a reader lease
for their entire body. Before rollover, the worker closes callback admission on
retired sources and drains existing readers for at most 1 s inside the total
budget. If a reader cannot drain, retry returns timeout with its token/context
unchanged; the next retry drains again. Healthy sibling callbacks remain admitted.
Rejected retired disconnects are reconciled against host handle existence after
readers drain. Follow-up registrations preserve the received immutable token.
A late successful connection for a retired attempt is terminated only when neither
source currently owns that handle. This preserves a sibling or newer session if
the host has reused the connection handle.
Timeout cleanup retains the scanner lease while the initiator/disconnecting link
settles, up to its allowance. If a global initiation remains active on the next
admission, cancellation is retried under the lease before a new scan is admitted.
A permanently unresponsive host still cannot be recovered by these local retries;
no host reset is introduced because that would tear down a healthy sibling.
Scan restart and characteristic-discovery start errors now complete the failed
attempt immediately instead of silently spending its entire operation timeout.

`tools/tests/test_ble_recovery.py` executes the production admission/cleanup body
and token helpers with a deterministic host, under address/undefined sanitizers.
It covers a queued source behind a 12 s sibling, cancellation acknowledgement,
ignored cancellation with bounded retry, token retirement/wrap, reused-handle
ownership, a worker retry during an admitted callback, bounded reader-drain failure
with unchanged token/context, and unchanged healthy sibling state. Existing source snapshot and deadline tests cover independent
source retirement and tick rollover. This is offline transport evidence; actual
ECM/TCM startup, radio coexistence and recovery latency remain physical gates.
`ble_obd_connect_role_probe` shares subscription and bounded ELM setup while
preserving the logical ECU. It skips role-dependent Mode 01 support discovery;
normal connections retain their prior support-map initialization. Probe-connected
workers can recover availability through `ble_obd_read_support_map`, one fixed
Mode 01 page per phase, against the logical ECU with its physical header. Support
is published only after an exact four-byte reply; missing/wrong-ECU evidence does
not become unsupported or erase a prior map. Probe-only setup
does not itself identify the controller or establish protocol-search completion.
The first vehicle request after ATSP0 may need the existing 15 s search allowance;
subsequent identity framing and role-policy scheduling have separate budgets.

The worker-only `ble_obd_read_role_identity` transport API admits only Mode 09
PID 04/06 against exact responders 7E8/7E9, with physical request headers 7E0/7E1.
It uses the existing serialized request/header owner; later polling selects its
normal route. Identity parsing requires one exact responder and one complete
record; missing headers, mixed responders and malformed ISO-TP fail closed.
The legacy identity decoder remains ECM-only. This API performs no ECU session,
control or actuation request. `ble_obd_disconnect` retires source readiness,
generation and adapter status synchronously; physical teardown remains asynchronous
and the next connect still drains/reconciles callbacks before slot reuse.

Worker health reports observed progress age/gaps without an adapter-stall reset
threshold. The configured 5 s task watchdog monitors idle tasks; adapter workers
are not explicitly subscribed, and admission, response and drain waits yield to
the scheduler. The 26 s transport bound therefore requires no watchdog-policy
relaxation. Adapter initialization and the existing worker backoff are additional
bounded phases; a transport deadline is not a complete readiness deadline.
Website impact: corrective recovery behavior adds no public capability or marketing claim.

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
| Bindings in swapped physical ports | With power off, swap the two adapters between ECM/TCM ports, then restart | Adapter IDs retain their saved roles; wrong-controller data stays unavailable or identity validation fails. No automatic rebinding or new role-verification probe is implemented; sibling retries remain independent |
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

For an isolation case, also pass `-e interruptSource TCM` (or ECM). Wait for the
`READY_FOR_LOSS_TCM`/`READY_FOR_LOSS_ECM` instrumentation checkpoint before unplugging
that adapter. Leave it out until `READY_FOR_RESTORE_TCM`/`READY_FOR_RESTORE_ECM`, then
reconnect during the bounded recovery wait. The
harness checks unchanged sibling session and whole configuration revision/hash.
It writes a private result under the app's external `dual-adapter-recovery` folder.
For the phone radio case, pass `-e allowPhoneBluetoothToggle true`. This explicitly
opts into a platform Bluetooth off/on cycle with a 15-second off interval. The harness
drains each shell command to completion, verifies the platform state separately from
the App phase, then requires fresh reads with unchanged source sessions. Other services such as
Android Auto can re-enable Bluetooth; an interrupted off interval fails as test setup,
not proof of a gauge recovery failure. Record any temporary service isolation.
Optional exact `expectedFirmwareVersion` / `expectedFirmwareElf` arguments verify
the installed candidate. No opt-in means the test skips. Record physical execution
separately; stage signals are operator coordination, not automatic adapter actions.

## Record the outcome

Retain private timestamped logs and captures under ignored `artifacts/`. Record exact
firmware/image identity, adapter models/drivers/address types, phone model/build,
config revision/hash, per-source achieved rate/P95 latency, stale intervals, reconnect
time, timeout/flood/drop counts, minimum internal/PSRAM memory and worker stack headroom.
Record physical LCD/touch, Android, serial and simulated observations separately.
Update [current state](../current-state.md) only after observed gates pass. Do not
call two-central-plus-phone coexistence qualified from configuration capacity or builds.


### Swapped-port check

With controls inactive, exchange the two adapters physically while retaining their
saved Bluetooth bindings. Record exact identities read on both links, old/new
source epochs and one runtime map exchange. Expect old values to clear before
fresh ECM and TCM values resume on their original logical pages. No configuration
hash, profile, bond or automatic vehicle command should change. Reconnect each
adapter and verify the inverse mapping stays stable; reboot and verify it is
rediscovered without intervention. Restore the physical arrangement and repeat.

One missing adapter, absent identity replies and ports exposing both modules must
retain the working map and continue independent recovery. Positive ECM proof on a
shared port must not disable valid ECM actions. A timeout after a good identity
must not revoke that same-session proof. A new link epoch or a positively different
identity must invalidate it. Follow the [role contract](../architecture/vehicle-connections.md#role-verification-for-accidentally-swapped-adapters)
and keep offline test evidence separate from actual Jeep qualification.
