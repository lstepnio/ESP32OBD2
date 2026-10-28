# Display experience and bounded resources

This owns page capacity, the optional Dual + arc layout, gear presentation and the
icon-only waiting surface. [Sampling](../development/reading-catalog-alerts.md#display-and-polling-classes),
[Smart selection](smart-page-cycling.md) and [orientation](hardware-and-transports.md#automatic-display-orientation)
have separate owners. [Current state](../current-state.md) distinguishes source
from installed and physically qualified behavior.

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
task. Saved brightness and manual angle apply at startup. The logo has no text.

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

## Resource and qualification gates

Fixed bulk sampling/Smart state stays in PSRAM. Ten pages add two bounded page
records, not tasks or per-page UI trees. Stability tracking is 20 bytes per active
reading, at most 640 bytes. The optional orientation worker has a separately gated
2 KiB internal static stack and is disabled in default images.

Tests exercise real production LVGL with the same fixed 64 KiB arena size, native
configuration compilation/rejection, cache/wake timing and ten-page fairness.
They do not prove radio coexistence or physical comfort. Before rollout, verify
saved-page migration/readback, cold boot and brightness, first-touch consumption,
source loss/recovery, all ten pages, mixed gear/arc layouts, and Wi-Fi/BLE memory
under loaded ECM/TCM polling. Orientation has its own qualification gate.
