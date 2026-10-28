# Indoor display qualification

This owns the bench sequence for ten pages, Dual + arc and sensor Auto orientation.
[Display experience](../architecture/display-experience.md) owns UX/defaults;
[orientation](../architecture/hardware-and-transports.md#automatic-display-orientation)
owns the sensor policy. Results belong in dated evidence, with installed identity,
configuration, settings and physical observations recorded separately.

## Preserve and isolate

Reserve one Pixel/gauge owner. Keep vehicle adapters absent and take private App
prefs/files plus authenticated configuration/settings/boot backups. Use the debug
awake helper, pause phone alert monitoring for the isolated session, and restore
both afterward. Never clear bonds, erase configuration or invoke vehicle commands.
Record exact version/ELF and healthy OTA state before and after every restart.

## Ten-page round trip on the installed image

Run only `TenPageDisplayJourneyTest` with explicit `allowGaugeWrite=true`. It requires
`mp:10`, `dx:1`, 1..7 original pages and an original primary-PID/layout selection
that can be restored without renaming pages. A pure storage reconstruction and an
actual local append/remove dry run must preserve the complete profile/assignment
state before any gauge send. Unsent setup differences are a preflight failure.

The fixture appends to ten without editing originals, including numeric arc slots
0 and 1, gear in the non-arc slot when supported and ordinary layouts. Send through
the ordinary App coordinator; require exact running revision/hash, cleared trial,
complete readback and unchanged sources/alerts/filters/actions/settings. The optional
`visualHoldMs` is bounded to three minutes and emits `TEN_PAGE_LAYOUT_READY` only
after running proof. During that window the owner taps through all ten positions,
checks 10/10 and navigation/label fit. No example values enter vehicle state.

Finally remove only appended pages, prove exact local restoration, read a fresh
protected base and send once. Require the original functional document (excluding
expected base revision), settings and assignments. An uncertain send is not replayed;
stop, retain backups and recover by fresh running proof. Configuration revisions
advance twice for a successful temporary setup/restoration, not for navigation.
Legacy built-in quick-select is deliberately limited to its original five readings;
it is not a ten-page test API and must not be widened incidentally.

## Populated Dual + arc visual coverage

The existing temporary screen preview includes both arc slots, P/R/N/numbered gear,
and a missing arc source with a healthy sibling. It uses production rendering but
never feeds adapter samples, alert history or saved pages. The fixture reads the
advertised frame count (bounded to 64), observes requested frames, stops playback
and checks unchanged configuration/settings/profiles. Owner review checks circle
clearance, legibility, gear colors, arc range/absence and hold-to-exit. Remove no
current vehicle data to manufacture a preview.

## Auto qualification build boundary

Ordinary images keep probe, candidate and board-qualified flags off. A separate
owner-supervised candidate uses `--orientation-candidate` and its own build directory:

```sh
python tools/dev.py build --target firmware --parked-idle-test --orientation-candidate
```

It enables the sensor worker and the test-only candidate permission while explicitly
keeping `CONFIG_EGAUGE_AUTO_ORIENTATION_QUALIFIED` off. It permits protected Auto
only after live IMU health is established. No public capability flag or default
board qualification is promoted. This distinction permits testing the real feature
without claiming it qualified beforehand. Probe-only builds can report passive
raw health without granting Auto. Normal/parked/candidate builds and placement gates
are compiled in CI. Signed publication/App-Wi-Fi installation still require explicit
owner authorization under the [release runbook](firmware-release-runbook.md).

The bounded raw/health snapshot records original observation time, gravity axes,
probe phase, proposed/applied angle, failures and worker stack margin. Candidate
logs are low-rate and emitted by the main owner, never from the small I2C worker
stack or a BLE callback. Hardware inventory marks the IMU initialized only while
its live stream is healthy; that bit does not mean board orientation is qualified.

## Guided physical matrix

1. Verify WHO identity, changing sensor timestamps and plausible approximately 1 g
   at rest, plus adequate worker stack margin and no allocation/bus errors.
2. Hold the face vertically at each of four cardinal poses for at least two seconds.
   Correlate raw axes, proposed/applied angles, upright text and actual touch hit
   regions. If mapping is wrong, correct/version it from evidence before acceptance.
3. Exercise diagonal boundaries, brief tilt/vibration and face-up/down poses. No
   flapping; ambiguous gravity holds the last angle. Manual selection exits Auto.
4. Save unrelated brightness/units/cycling, then restart: Auto/manual fallback remain
   distinct and preserved. Restore the owner's settings after the test.
5. Repeat authenticated Wi-Fi open/status/close and ordinary connection recovery
   while the sensor worker is enabled. It must suspend through maintenance, become
   stale without moving, then recover with fresh observations/dwell. Native tests
   cover actual absent/frozen/bus-failure inputs; label those separately from the
   physical suspension test, which does not prove an unplugged sensor.
6. Record memory/stack/worker progress and final exact healthy image/setup. Stop
   previews, restore the original layout/settings/monitoring/wake state.

Only observed cells pass. Indoor static/motion checks do not establish vehicle
acceleration/vibration behavior, full electrical fault tolerance or loaded dual-radio
performance. Keep normal defaults and board qualification off until their recorded
acceptance is complete; do not infer these from board model or one sensor reply.
