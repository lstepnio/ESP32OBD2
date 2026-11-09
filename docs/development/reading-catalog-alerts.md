# Reading catalog and local alerts

## Scope and authority

The source catalog offers 63 executable readings. These include 53 scalar SAE
Mode 01 readings, eight narrowband oxygen voltage components and two
calibration-specific JSS transmission readings. The catalog covers fuel trims,
pressures, intake/ambient/oil/catalyst
temperatures, voltage, air flow, throttle/pedal positions, EGR/purge, run time,
distance/time since diagnostic events, ethanol, fuel rate and torque.

`contracts/reading-catalog.json` is the single editable catalog. It contains editor
identity, source/category, gauge label, example, minimum configuration capability
and the complete wire definition, provenance and synthetic decode vectors.
`tools/generate_reading_catalog.py` generates Android `ReadingCatalog.kt` and the
wire template plus firmware `reading_policy_catalog.inc`. `tools/validate.py` rejects drift and validates every definition
and a selected one-reading configuration. The template stores the full library.
The gauge receives only definitions used by the saved pages and enabled alerts.

Protocol formulas were checked against the primary
[python-OBD command tables](https://python-obd.readthedocs.io/en/latest/Command%20Tables/)
and [decoder implementation](https://github.com/brendan-w/python-OBD/blob/master/obd/decoders.py).
New definitions and vectors are project-authored protocol facts. Existing TCM
provenance and CC-BY-SA attribution are retained. No vendor research archive was
imported or promoted into executable support.

Catalog availability is not vehicle support evidence. Mode 01 entries use the
existing functional CAN/7E8 path. A missing response is unavailable and does not
prove permanent lack of support. Automatic recovery/backoff remains per reading
and per source. Compound status bytes, oxygen-sensor trim and wideband components, arbitrary
enhanced identifiers and alternate transport/responders require separate work. Legacy synthetic transmission input speed remains readable in
local profiles but cannot be selected for execution or alerts.

## Starter profiles

In **Car > Your cars**, the default Generic OBD-II starter includes RPM, coolant
and speed through functional request `7DF` and engine responder `7E8`, matching the
archive’s generic 11-bit CAN route. Valid replies determine which readings become
available. The archive also records the distinct 29-bit `18DB33F1` / `18DAF110`
route for future transport qualification. The starter uses one primary adapter
and standard emissions requests.

Development builds also offer **JK / Hemi + JSS 8HP70**, which adds temperature and
gear pages using the two captured JSS reads. Add the optional transmission child
only for a separate port. The starter creates editable pages in the existing
profile format. Adapter binding, sending a setup, vehicle actions and code
filters follow their own explicit setup paths.
The [JK coverage](../vehicles/jeep/reading-coverage.md) separates supported software
from advertised, observed and archive-only candidates.

## Narrowband oxygen voltage

Mode 01 `14` through `1B` each return two data bytes. Voltage is `A / 200` V with a
range of 0 to 1.275 V. The runtime requires both data bytes and decodes byte A.
Byte B carries a separate trim component and can contain `FF` when trim is
unavailable. Slot numbers avoid assuming a bank or position before interpreting
`01 13` or `01 1D`. The current Hemi fixture advertises slots 1, 2, 5 and 6.
The other slots are standard definitions available on vehicles that report them.

The App requires `ov:1` for an oxygen voltage page or enabled alert. Older gauges
can keep the draft locally until updated. The existing Mode 01 admission guard
allows this response/decoder length difference only for the documented voltage
shape. Standard responder, timeout, stale age and configured-reading limits still
apply. Missing or truncated replies stay unavailable. The [owner protocol](../protocol/experimental-firmware-transfers.md)
defines the additive marker.

## Editing, routing and runtime

- Reading and alert pickers search the catalog. Saved alerts sort before inactive
  readings. Every selectable reading can have one saved alert, with or without a
  page. Opening/cancelling an editor never creates a rule.
- Numeric alerts accept finite decimal boundaries and reset distances. Above/Below
  retain ordered warning/critical levels, range and release-boundary validation,
  trigger/clear dwell and stale-data qualification. Invalid values, unknown gear codes and failed polls
  break pending dwell immediately through the application-owned alert queue. Queue
  overflow conservatively invalidates the affected reading using a bounded bit mask;
  alert queue draining is bounded per application tick.
- Gear alerts use named P/R/N/1..8 conditions with `direction: "equals"` and zero
  reset distance. Warning and critical targets are chosen explicitly; critical
  wins if identical. A fresh nonmatching position begins the clearing dwell.
  P/R/N/1 were compared on the JSS vehicle; 2..8 remain published mappings without
  independent physical validation. This does not establish selector/actual gear
  semantics for another calibration.
- Vehicle alerts merge across the primary and optional TCM child. Editing and
  review use one logical dashboard. Child removal retains pages and alerts.
  Only Mode 22 definitions route to the optional TCM child; an absent sibling does
  not invalidate healthy-source samples. A single primary transport can carry both
  kinds of requests without adding a child in Expert.
- Only definitions referenced by pages or alerts are transmitted/polled. Limits
  remain 32 different active readings, 32 alerts, ten pages and 64 KiB (subject to negotiated `mp` capacity). Asking for
  short intervals does not guarantee that rate on a slow or missing adapter.
- Configuration and alerts retain canonical metric values. Imperial display and
  editing convert temperature, speed/distance, pressure, flow and torque. Saving an
  unchanged existing threshold preserves its canonical value. The gauge keeps
  doubles through its bounded UI queue/cache; fractional units render with up to
  three decimals. Familiar temperature/speed/RPM pages retain whole-number display.
  Arc and Bar use the canonical range for ordinary readings. The [RPM display scale and trend window](../architecture/display-experience.md#rpm-ring-and-trend-history) use presentation-only bounds.

## Display and polling classes

Each catalog row has explicit `samplingClass` metadata, separate from the wire
PID definition. The generator emits a flash-resident firmware table keyed by
reading ID, service and PID, with the original polling/stale defaults. Class
policies apply only to unchanged catalog timings. Unknown/custom definitions or
explicit timing overrides keep their configured values; labels/units never infer
policy. Adding a class requires rate/age and native-policy tests.

| Class | Polling target | Display age limit |
| --- | --- | --- |
| Fast | Visible 250 ms; upcoming cycling page 500 ms; hidden 1,000 ms | 3 seconds |
| Thermal | 1,500 ms | 8 seconds |
| Slow | Visible 2,000 ms; upcoming 2,500 ms; hidden 5,000 ms | 20 seconds |

Only reviewed catalog rows have `samplingStability` metadata. Coolant, controller,
intake, ambient and oil temperature allow 0.5 °C variation; fuel level and ethanol
allow 0.5 percentage points. After thirty seconds within that distance of a fixed
anchor, a hidden thermal reading can back off to 3,000 ms and a hidden slow reading
to 10,000 ms. Visible/upcoming readings immediately regain their normal class
interval. RPM, gear and other fast signals never slow down just because they are
steady. Gradual cumulative drift resets the window, as do failed/nonfinite reads,
a stale gap or a source generation change. Stability state is bounded per reading
in PSRAM and introduces no additional queries. Custom/overridden definitions retain
explicit timing; new adaptive rows need semantic review and native tests.

Configured alerts cap the interval at their original polling requirement. Their
`staleAfterMs`, dwell and control freshness are not increased. A display age limit
never changes with visibility and page navigation never renews the timestamp.
The table schedules only configured page/alert readings; it does not start querying
the entire catalog. Rates remain subject to measured adapter capacity.
Metadata does not add wire fields, change saved configuration digests or require an
App migration. Existing installed definitions acquire the firmware presentation
policy when their identities and timings match the catalog.

## Gauge presentation smoothing

The raw cache remains the authority for alerts, controls, Smart selection and trend
history. A separate fixed state for each of the two visible metrics handles display
motion on the existing 50 ms UI timer. It adds no task, queue, framebuffer or history.

- Unchanged catalog continuous readings interpolate arc/bar position over 150 ms
  with monotone ease-out, bounded by the previous displayed position and latest
  measured target. New samples retarget from the current position, without prediction,
  overshoot or manufactured samples. Original observation age pays down the window,
  so delayed consumption does not add a second full delay. Equal targets do not
  restart convergence. Their numeric text retains measured precision.
- RPM numeric text uses 25-RPM bins, a 17.5-RPM change deadband and at least 250 ms
  between ordinary numeric changes. Page entry/recovery starts at the actual cached
  value; engine-off zero and shared severity transitions bypass these filters.
- Gear and unknown/custom definitions, including explicit timing overrides, stay
  direct. Policy is assigned from catalog identity/service/PID/timings, never labels.
- Missing/expired/invalid samples clear immediately. Source-generation changes,
  revision/session changes, invalidation between UI frames, missed stale intervals,
  page/showcase changes and unit changes start fresh, with no animation from zero or
  across sources. Trend history records only raw samples; its RPM label can use the
  numeric policy. Animation never renews a timestamp or widens any age limit.

During ordinary scheduling, a never-seen PID has no retention deadline and cannot
claim infinite urgency ahead of a due visible reading. Measured readings near expiry
still take precedence; visible/upcoming priority orders other eligible work. Every
ninth ordinary slot remains round-robin. Diagnostic preflight refreshes higher-priority
at-risk metrics first, interleaving at most one due visible read per pair of
preflight reads. A diagnostic walk stays bounded to 32 preflight plus at most 16
extra visible requests; it retains full timeout reserves and retry backoff. A saturated adapter cannot guarantee nominal rates or eliminate
every gap; physical rate/legibility acceptance is in the
[continuity plan](gauge-continuity-plan.md#one-parked-jeep-session).

## Compatibility

Development capability `cfg:5` includes `cfg:4` and signals expanded decimal
rendering plus numeric Mode 22 and gear-equality alerts. Public `configWrite`/`ota`
remain false, and link capacity stays one. No new BLE opcode or protocol-major
change is introduced. Config schema remains 2 with the additive `equals`
comparator. Current phone storage compatibility is in [current state](../current-state.md)
and [Android storage](../architecture/android-runtime.md#storage-and-recovery).
Android negotiates capabilities and blocks unsupported writes without dropping pages.
Install a compatible App before updating firmware; saved drafts remain editable.
Release publication is separately authorized under `AGENTS.md`.

## Verification and physical follow-up

- Android JVM tests cover every catalog row, both numeric comparators, projection,
  alert-only definitions, exact saved readback, profile persistence, child routing
  and removal, unsupported versions, invalid/nonfinite thresholds, unknown/fractional
  gear codes, bounds and the union-of-readings limit.
- Production C configuration fixtures compile all 63 definitions with alerts and
  evaluate their synthetic vectors through the actual decoder. Rejection cases
  include short payloads, gear reset distance and non-gear equality conditions.
- Sanitized core tests cover fractional threshold hysteresis, named-position dwell,
  stale recovery and source-specific invalidation, plus decimal/Imperial formatting.
- Native Pixel editor fixtures cover searching common/TCM alerts, fractional saves,
  gear saves and a transmission-page alert entry. They perform no vehicle requests
  or gauge writes. Separate dashboard editor regression checks are recorded with
  the final result below.

Physical acceptance is tracked as **QUAL-06**, **QUAL-07** and **QUAL-04** in
[the backlog](../backlog.md). Host vectors and phone fixtures do not establish vehicle
support. Dated build/editor/install results are in [catalog rollout evidence](../evidence/reading-catalog-rollout.md).

## Extending the catalog

1. Add a project-authored definition to the catalog with authoritative formula,
   responder, minimum payload, range, units, an explicit sampling class, reasonable poll/stale intervals and
   min/mid/max vectors. Keep query identities unique.
2. Choose numeric or named-state alert semantics, include provenance and license,
   and avoid claiming compatibility from an example.
3. Run the generator, repository validator, Android tests and production C catalog
   fixtures. A definition that the firmware compiler cannot execute must not appear
   as selectable. Add mappings for genuinely new units in both App and firmware.
4. Record physical qualification separately; do not change public flags implicitly.

## Smart presentation metadata

Optional `smartInterest` metadata and its generated flash table provide semantic
context/thermal hints. The [Smart cycling contract](../architecture/smart-page-cycling.md)
owns defaults, exact-definition matching, tuning validation, limits and qualification.
This is independent of `samplingClass`: it never creates queries, widens freshness
or changes actual alert thresholds. Unknown/custom readings keep active-alert priority.

## Per-vehicle coverage audit

Compare captured per-responder support maps with executable definitions using
`tools/research/audit_vehicle_readings.py`, then separately inventory enhanced
modules. The [Hemi-swapped JK audit](../vehicles/jeep/reading-coverage.md) records
standard gaps, conflicting transfer-case variants and promotion checks. Imported
definitions or a responding PID do not establish a selectable reading or scale.
