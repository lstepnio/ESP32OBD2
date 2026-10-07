# Reading catalog and local alerts

## Scope and authority

Source candidate `0.2.0-dev.46` offers 55 executable readings: 53 scalar SAE
Mode 01 readings and the two existing calibration-specific JSS transmission
readings. The list covers fuel trims, pressures, intake/ambient/oil/catalyst
temperatures, voltage, air flow, throttle/pedal positions, EGR/purge, run time,
distance/time since diagnostic events, ethanol, fuel rate and torque.

`contracts/reading-catalog.json` is the single editable catalog. It contains editor
identity, source/category, gauge label, example, minimum configuration capability
and the complete wire definition, provenance and synthetic decode vectors.
`tools/generate_reading_catalog.py` generates Android `ReadingCatalog.kt` and the
wire template. `tools/validate.py` rejects drift and validates every definition
and a selected one-reading configuration. The template is a definition library;
it is not itself an executable 55-reading configuration.

Protocol formulas were checked against the primary
[python-OBD command tables](https://python-obd.readthedocs.io/en/latest/Command%20Tables/)
and [decoder implementation](https://github.com/brendan-w/python-OBD/blob/master/obd/decoders.py).
New definitions and vectors are project-authored protocol facts. Existing TCM
provenance and CC-BY-SA attribution are retained. No vendor research archive was
imported or promoted into executable support.

Catalog availability is not vehicle support evidence. Mode 01 entries use the
existing functional CAN/7E8 path. A missing response is unavailable and does not
prove permanent lack of support. Automatic recovery/backoff remains per reading
and per source. Compound status bytes, oxygen-sensor multi-signal responses,
arbitrary enhanced identifiers and alternate transport/responders remain outside
this increment. Legacy synthetic transmission input speed remains readable in
local profiles but cannot be selected for execution or alerts.

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
  remain 32 different active readings, 32 alerts, eight pages and 64 KiB. Asking for
  short intervals does not guarantee that rate on a slow or missing adapter.
- Configuration and alerts retain canonical metric values. Imperial display and
  editing convert temperature, speed/distance, pressure, flow and torque. Saving an
  unchanged existing threshold preserves its canonical value. The gauge keeps
  doubles through its bounded UI queue/cache; fractional units render with up to
  three decimals. Familiar temperature/speed/RPM pages retain whole-number display.
  Arc/Bar/Trend normalize using the full canonical range, including negative values
  and sub-unit ranges.

## Compatibility

Development capability `cfg:5` includes `cfg:4` and signals expanded decimal
rendering plus numeric Mode 22 and gear-equality alerts. Public `configWrite`/`ota`
remain false, and link capacity stays one. No new BLE opcode or protocol-major
change is introduced. Config schema remains 2 with the additive `equals`
comparator. Local profile schema becomes 11, preventing older Android versions
from silently truncating fractional limits. This App reads profile schemas 1..11
and capability versions 0..5.

Install App dev.46 before gauge dev.46: older Apps deliberately reject unknown
capability versions. The new App can read dev.45 and keep existing dashboards, but
blocks sending new readings/TCM alerts until `cfg:5` is confirmed. Saved drafts
can be edited before firmware installation. Existing published releases are
unchanged; publication requires owner authorization under `AGENTS.md`.

## Verification and physical follow-up

- Android JVM tests cover every catalog row, both numeric comparators, projection,
  alert-only definitions, exact saved readback, profile persistence, child routing
  and removal, unsupported versions, invalid/nonfinite thresholds, unknown/fractional
  gear codes, bounds and the union-of-readings limit.
- Production C configuration fixtures compile all 55 definitions with alerts and
  evaluate their synthetic vectors through the actual decoder. Rejection cases
  include short payloads, gear reset distance and non-gear equality conditions.
- Sanitized core tests cover fractional threshold hysteresis, named-position dwell,
  stale recovery and source-specific invalidation, plus decimal/Imperial formatting.
- Native Pixel editor fixtures cover searching common/TCM alerts, fractional saves,
  gear saves and a transmission-page alert entry. They perform no vehicle requests
  or gauge writes. Separate dashboard editor regression checks are recorded with
  the final result below.

Remaining real-gauge checks after an authorized App/Wi-Fi install: verify voltage
or air-flow fractional rendering, fractional Arc/Bar range, a hidden-page TCM
alert, entry/clear dwell, stale badge after unplugging, return after reconnect and
settings/profile retention. Use both physical adapters for independent two-source
loss/recovery qualification. Temperature meaning and gears 2..8 need an independent
reference. Host vectors and phone fixtures do not provide vehicle evidence.

## Extending the catalog

1. Add a project-authored definition to the catalog with authoritative formula,
   responder, minimum payload, range, units, reasonable poll/stale intervals and
   min/mid/max vectors. Keep query identities unique.
2. Choose numeric or named-state alert semantics, include provenance and license,
   and avoid claiming compatibility from an example.
3. Run the generator, repository validator, Android tests and production C catalog
   fixtures. A definition that the firmware compiler cannot execute must not appear
   as selectable. Add mappings for genuinely new units in both App and firmware.
4. Record physical qualification separately; do not change public flags implicitly.

### Session result, 2026-10-07

- Android debug APK build, instrumentation APK build, lint and 145 JVM tests pass.
- Pixel native fixtures: 18 tests pass, comprising four new reading/alert tests,
  ten dashboard editor journeys and four unified dashboard tests. Three older
  assertions were corrected to compare canonical doubles; the editor behavior
  remained verified. App dev.46 was installed with replacement install preserving
  data, and the original phone wake settings were restored after testing.
- ESP-IDF 5.4.1 candidate dev.46 build passes. Production configuration fixtures
  compile all 55 definitions/alerts and evaluate all catalog vectors. Core logic
  fixtures pass with AddressSanitizer and UndefinedBehaviorSanitizer. The host
  compile suppresses macOS deprecation warnings in the pinned third-party cJSON
  implementation; project warnings remain errors.
- Repository contract/examples/rejection cases, generated catalog parity, document
  links and design token parity pass.
- Gauge stays on dev.45. No firmware publication, App/Wi-Fi installation or vehicle
  qualification is claimed for this candidate.
