# Temporary gauge display showcase

Development firmware dev.54 introduces an owner-authorized presentation showcase.
The debug App exposes its start/stop control in Expert when the gauge reports `sc:1`.
This is a UI review tool, not a vehicle-data source. Implementation and host renders
are prepared; installation and physical observation remain separate gates.

## Behavior and boundaries

- Twenty-one generated frames cover five renderers, mixed readings/units, long and
  negative values, CEL, warnings, critical entry/acknowledgment, lost sources and codes.
- Default eight seconds per frame; accepted intervals are five to thirty seconds.
  Playback expires after ten minutes and is never persisted across restart.
- Every frame has a quiet Demo footer. Tap advances; hold exits. The whole hold
  sequence is consumed, so a longer demo hold cannot become pairing or owner reset.
- Saved dashboard, selected page, source bindings, real samples, settings, bonds,
  shared event history and notification state are unchanged. Generated attention
  acknowledgment advances a frame and never acknowledges a real event.
- Refuse start while a vehicle source is ready, maintenance is active, pairing is
  visible or a real critical event needs attention. Stop immediately if these begin.
  Stop remains permitted regardless of the source/maintenance state.
- Phone disconnect does not fabricate a stop. Normal page/config mutation stops the
  showcase; reconnect uses protected automatic reads to obtain actual playback state.
- Units use the owner's current setting. Example sensor names preserve their role;
  a TCM temperature is not renamed fluid temperature without validated provenance.

## Verification

Pure lifecycle policy: `python3 -m unittest discover -s tools/tests -p 'test_obd_gauge_presentation.py'`.
Production LVGL, fonts and software rendering (after the normal IDF build resolves LVGL):

```sh
cmake -S firmware/gauge/tests/ui_host -B artifacts/gauge-ui-lvgl-build
cmake --build artifacts/gauge-ui-lvgl-build
artifacts/gauge-ui-lvgl-build/gauge_ui_render artifacts/gauge-ui-lvgl-renders
```

The native fixture checks complete circular glyph bounds, page/font stability,
metric/imperial cases, generated acknowledgment isolation, preemption and pairing
restore. Its captures are software renders, not physical display observations.
`DisplayShowcaseJourneyTest` is opt-in with `allowDisplayShowcase=true`; it verifies
playback advancement, stop and unchanged saved dashboard/settings/profiles. Optional
`keepShowcaseRunning=true` restarts playback for owner viewing. Only run with owner
permission to show generated data and no connected vehicle sources.

Wire ownership: [development firmware transfers](../protocol/experimental-firmware-transfers.md#temporary-display-showcase).
Design and remaining screens: [gauge UI proposal](../design/gauge-ui-proposal.md).
