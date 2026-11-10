# Display experience and bounded resources

This owns page capacity, the optional Dual + arc layout, gear presentation and the
icon-only waiting surface. [Sampling](../development/reading-catalog-alerts.md#display-and-polling-classes),
[Smart selection](smart-page-cycling.md) and [orientation](hardware-and-transports.md#automatic-display-orientation)
have separate owners. [Current state](../current-state.md) distinguishes source
from installed and physically qualified behavior.
[Gauge presentation rules](../development/reading-catalog-alerts.md#gauge-presentation-smoothing)
own bounded motion, RPM text stabilization and immediate raw-data invalidation.

## Ten saved pages

The App can save and reorder up to ten pages per gauge. Firmware compiles at most
ten pages and 32 distinct active readings, still within the 64 KiB document limit.
Local profile schema 18 adds the selected arc index, migrates schema 17 with an
index-zero default, and retains newer/malformed records rather than overwriting
them. Gauge association schema remains 2. More pages reuse the same rendering objects and sample cache; they do not each
allocate a screen or duplicate polling. More distinct readings can reduce achieved
refresh rates because OBD transport capacity is finite. Page visibility and the next
cycling page influence scheduling, not cache identity or observation timestamps.

The negotiated `mp` limit controls deployment. Legacy document gauges default to
eight; older fallback configurations retain their existing limits. Editing ten
pages offline is allowed, but deployment to an older gauge is rejected before
transfer. Never silently drop pages. Up to eight pages retain the existing dots;
nine or ten use a compact page counter without allocating additional dots.

## Dual + arc

The App offers this beside Dual in the layout chooser. Two readings remain explicit;
the user chooses which numeric reading drives the perimeter arc. Gear can occupy
the other slot. The arc uses that reading's complete canonical range and availability,
including unit conversion at presentation. A missing selected reading removes its
arc without hiding the healthy sibling. Ordinary Dual keeps its previous geometry.

The document uses `renderer: "dualArc"` and required `arcMetricIndex: 0|1`.
Two distinct readings are required and the selected arc reading cannot be gear.
The field is invalid on other renderers. The App requires `dx:1` before transfer.
The existing single arc object is reused; text is narrowed to fit within its ring.
Custom definitions follow the same measured glyph fitting, units, range and
unavailable rules as catalog readings. No label-based guessing of enum semantics.

## Foreground reading motion

The [sampling/presentation contract](../development/reading-catalog-alerts.md#gauge-presentation-smoothing)
owns the single 150 ms analog and bounded RPM text policy, catalog matching,
foreground preference, original observation age and continuity/reset rules.
Presentation values never enter raw cache, trend history, alerts or vehicle guards.
Page entry uses the latest valid cached value immediately. The existing LVGL owner
uses two fixed states, without a new task, animation buffer or allocation.
Loaded cadence, comfort and memory qualification remain separate from fixture proof.

## Gear color

`design/tokens.json` owns the shared palette, generated into firmware and Android.
P is white, N green, R amber, and D/1..8 cyan. Unknown remains neutral gray.
These are project design choices, not a claim of a universal mandated gear palette.
The letter or number always carries meaning independently of color. An actual
numbered gear is never relabeled D, and unknown values remain unavailable.
Red stays reserved for critical attention. Shared alert presentation keeps priority.

## Icon-only boot and no-data surface

After NVS display preferences load, the board initializes and presents the logo
before heavier configuration/radio setup. It uses four retained LVGL objects
(container, arc, line and dot), with no bitmap, additional framebuffer or animation
task. Saved brightness and the manual angle (or last checkpointed applied angle in Auto) apply at startup. The logo has no text.
The reference mark spans 168 pixels on the 240-pixel LCD. Presentation profiles
can tune the mark's proportions on the shared 240-pixel design grid. The OLED
Balanced profile uses a 148-pixel diameter and a 9-pixel ring stroke on that grid,
with a shorter needle that clears the ring. Both use muted neutral gray
(`#555555`) and the same retained objects and point buffer.

With no valid vehicle observation, it stays until touch or data. After observations
have arrived, sixty seconds without a valid scoped vehicle observation returns to
the logo. A touch wakes readings for thirty seconds; the waking stroke is consumed
so it cannot also navigate or execute an action. Data wakes immediately, with the
page populated from the existing cache before uncovering it. Original monotonic
sample time, source, session and revision remain authoritative. Invalid or retired
samples cannot postpone the logo. One healthy vehicle source keeps the combined
view awake if its sibling disappears.

Pairing, active actions, calibration, showcase, active alerts and confirmed diagnostic
attention take precedence. The logo pauses automatic page cycling; wake resumes a
complete viewing interval. Waiting never creates a false reading or changes alert,
control or source-loss freshness. Dashes still represent unavailable values after
wake. Quiet recovery continues behind the logo.

## Connection footer

`EGAUGE_FEATURE_CONNECTION_FOOTER` is off by default. It is an opt-in OLED
experiment, independent of feature licensing, and uses the shared presentation
profile. Wi-Fi (silver) means a station has joined the active maintenance
network. Blue marks the phone BLE link. The green and violet plug icons indicate
ready OBD adapter sessions with one and two pins. A ready link can still have
unavailable readings. The muted icons appear only while their connections are
live, and the visible icons are centered. A child icon requires both a saved
adapter binding and a ready link. When every link is down, the footer is empty.
On supported gauges, **Settings → Connection icons** in the App turns the
footer on or off. It defaults to On, including for existing gauges on their
first update. The owner preference is saved on the gauge, applies to both
readings and the waiting logo, and survives restart. The App reads it
automatically and shows a switch only after supported firmware confirms it.
See the [protected settings protocol](../protocol/experimental-firmware-transfers.md#connection-icons).
Transport, polling, and alert behavior are independent of the preference.

The same symbols sit below the waiting logo or a reading with a free footer.
On multipage readings the icons sit above the ordinary page dots,
with clearance from both the reading and the dots. A tight layout drops the
icons and keeps its reading and page indicator in place. Page counters, pairing,
calibration, showcase, action controls, and vehicle warnings take priority.
The compositor uses one packed status snapshot, fixed stroke geometry, and
bounded footer invalidation. No persistent image buffer or transport task is
added. The first boot-logo frame can render before connection state is known.

Run the production LVGL host test with `--connection-review` to capture the
24-state matrix. Verify on each physical panel at 10% Night brightness before
enabling the flag in a field build. Native frames establish geometry and color
selection, while physical panels establish legibility and bezel clearance.

## Sunset brightness

**Day brightness** defaults to 90% on a fresh gauge or its first current-format
dashboard. The owner can set it from 5 to 100%. **Night brightness** defaults to
10% and cannot drive the panel above the Day level. Existing saved levels take
precedence. **Sunset to sunrise** is opt-in. Turning it off restores Day brightness.
The owner can supply approximate phone location on demand. The App rounds it to a tenth of a degree before sending
it to the gauge and asks for no background location permission.

The gauge estimates solar daylight from UTC and the saved location using
[NOAA's solar-position equations](https://gml.noaa.gov/grad/solcalc/solareqns.PDF).
The schedule tracks seasonal daylight, including polar day or night. It runs
locally once the App has synced the clock. On a cold boot without a valid
retained clock, the gauge uses the last saved Day/Night mode until the App
reconnects. A board with a supported battery-backed RTC can resume the
schedule at boot. Update the saved location in Settings after travel.

On the OLED adapter, a short release of the right-side BOOT key with USB-C
at the bottom switches Day/Night. The override stays active through the next
automatic phase evaluation and expires when the solar phase changes.
Holding the key at startup, long presses and bouncing contacts do not
toggle brightness. The reference LCD has no bound button shortcut. The
display owner applies the hardware level, and scheduled state does not write
flash between Day/Night changes. Settings migrations preserve the legacy
brightness, rotation and cycling records.

Validate both panels' low-end readability, daylight return, boot without the
phone, clock-loss recovery, and the physical OLED button before qualifying.
The [owner protocol](../protocol/experimental-firmware-transfers.md#sunset-brightness)
defines the version gate and confirmation.

## Touch motion and shortcut arbitration

A configured upward shortcut accepts 40 through 800 ms strokes with at least
32 design pixels upward travel, at most 24 horizontal pixels and upward travel
at least twice horizontal travel. The path still rejects excessive sideways or
reverse motion. This includes deliberate 60 ms flicks while rejecting short
noise. Motion over 12 design pixels is a stroke and cannot become a pairing hold
or owner-reset release. A failed bound upward/diagonal stroke cannot become a page
tap. Horizontal browsing, stationary holds and unconfigured swipe browsing retain
their existing purpose. Lost input cancels progress and consumes the trailing click. Pairing/reset
confirmation consumes all moved strokes, including horizontal browsing motion and
inputs with no configured shortcut; only a stationary deliberate tap confirms.

Repeated pairing HIDDEN status while already hidden preserves partial progress;
actual pairing/context changes cancel it. Pending recognition is published from
the input callback so cycling observes it immediately. Completed actions retain
the five-second cooldown across context resets; another recognized stroke displays
Wait without executing again. Existing action-entry arrows and critical-attention
cancellation retain priority. The production LVGL pointer regression covers these
paths; hardware timing, vibration and loaded transport remain separate qualification.
[Historical browsing review](../evidence/gauge-browsing-reliability.md) retains
original evidence and physical acceptance inputs.

## Resource and qualification gates

Fixed bulk sampling/Smart state stays in PSRAM. Ten pages add two bounded page
records, not tasks or per-page UI trees. Stability tracking is 20 bytes per active
reading, at most 640 bytes. The optional orientation worker has a separately gated
4 KiB internal static stack and is disabled in default images.

Tests exercise real production LVGL with the same fixed 64 KiB arena size, native
configuration compilation/rejection, cache/wake timing and ten-page fairness.
A 100-redraw waiting-logo fixture checks its four-object tree and unchanged LVGL
free memory after warm-up.
They do not prove radio coexistence or physical comfort. Before rollout, verify
saved-page migration/readback, cold boot and brightness, first-touch consumption,
source loss/recovery, all ten pages, mixed gear/arc layouts, and Wi-Fi/BLE memory
under loaded ECM/TCM polling. Orientation has its own qualification gate.
