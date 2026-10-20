# Action-page indoor rollout

> Historical evidence. Measurements and unfinished checks describe this session.
> Current support is in [current state](../current-state.md); remaining work is in
> [the backlog](../backlog.md). This record is not a standing implementation instruction.

## 2026-10-20 signed dev.61

PR #89 merged as `0e3bd12`; all four Foundation quality gates passed for
`68d44a8`. Version-only PR #90 merged as `b5a8dc1`. Its exact reviewed source
`a6dd0291780722b0e0e2725491c67c2e4b478a8d` passed all four gates and the protected
[development publication](https://github.com/lstepnio/ESP32OBD2/actions/runs/39472981311).
The owner authorized merge, signed publication, App/Wi-Fi installation and indoor checks.

### Installed artifacts

- Pixel 10 Pro: updated debug App built from `a6dd029`; the App version label remains
  `0.2.0-dev.51`. Installed with replacement, without clearing App data or bonds.
- Original Waveshare ESP32-S3-Touch-LCD-1.28: signed
  [dev.61](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.61),
  catalog generation 42, image 1,643,008 bytes.
- ELF SHA-256: `4ee4474128f2ee25b8f924a2fe63b5578c75945ab6e45b01253a8253ba40fbc6`.
- Bundle SHA-256: `78ace5661cc251a1bcd4e3a1c3b042f0aba611f37cd61b001d33cdf4c033b5ad`.
- Downloaded catalog and application signatures, bundle length/hash, board,
  partition layout, transfer protocol and ESP descriptor verified before installation.
  Private signing material stayed in the protected workflow.

### Observed indoor checks

| Check | Result |
| --- | --- |
| App migration | Profiles 13 to 17; gauge associations 1 to 2. Prior profile fields preserved; only schema/default empty idle presets added. |
| Automatic connection before update | Protected opening/settings, foreground resume and hardware-to-firmware read chain passed against dev.60; revision 52 retained. |
| Hosted signed App/Wi-Fi update | Passed in 114.429 seconds including hosted preparation; update journey 74.153 seconds. Sending began at 18.314 seconds, verification at 58.005 seconds, exact healthy image confirmed at 74.151 seconds. |
| OTA acceptance | Alternate slot, health state 2, exact version/ELF and cleared phone update journal. Revision 52, digest, settings and profiles preserved by the firmware installation. |
| Five maintenance cycles | Protected open, authenticated Wi-Fi configuration/OTA status, close and unchanged boot nonce passed; open times 1,599 to 1,629 ms. |
| Bluetooth recovery | Opening 7,867 ms; foreground resume 2,796 ms; Bluetooth off/on recovery 12,252 ms. No reconnect tap or bond reset. |
| Maintenance teardown | Protected hardware snapshot reported Wi-Fi mode 255 (deinitialized), followed by fresh exact firmware read. |
| Pixel action setup | Real Gauge > Action pages opened and dismissed; profiles, assignments, settings and running revision/digest unchanged. No vehicle command invoked. |

The first update fixture stopped before any transfer because phone monitoring was
active. Monitoring was disabled through Settings to isolate the test and restored
at session end. No Bluetooth/USB firmware recovery was used.

### Action binding and preservation correction

The firmware update correctly retained the old `jumpPage` configuration. After the
owner reported that swipes still jumped to a reading, the gauge-specific override
was changed to `vehicleActions`: three upward swipes within five seconds, ordered
Air up (5:00), Winch (1:00), ABS/TCS. This is configuration, not a firmware fallback.

The initial configuration check caught an unrelated draft difference: the phone's
**pre-install backup** contained three TCM filters while the gauge had four. Sending
that draft temporarily omitted `U0140` at revision 53. Revision 54 restored it in
both phone and gauge. Comparing the complete initial/final documents showed only
the approved action binding, base revision and filter ordering changed. All four
filter codes (`U0140`, `U0121`, `P1DCA`, `P1DF3`), pages, limits and adapter bindings
were retained. The cause of the older phone/gauge filter divergence is unknown;
it was present before this migration.

The opt-in `ActionPagesConfigurationJourneyTest` now rejects unrelated draft
changes **before** sending. It can reconcile one explicitly supplied TCM filter,
uses the ordinary per-gauge save/review/send owners, and verifies exact healthy
readback and independently read unqualified/inactive command states. A prior
post-send fixture wait timed out on background command polling; the final assertion
uses isolated protected reads after draining the App operation owner. The corrected
fixture passed in 55.705 seconds at revision 55, preserving dashboard/settings and
confirming both command kinds remain unqualified and inactive. The final document
retains the same four filter codes; ordering differs without changing filter semantics.

### Memory evidence

Serial samples during upload and the five maintenance cycles recorded zero
allocation failures and zero worker queue drops. The upload's recorded internal
minimum was 93,979 bytes. Dev.61 maintenance minimum was 96,707 bytes; final three
Wi-Fi-release samples each showed 122,327 internal bytes free and a 31,744-byte
largest block. Minimum sampled application stack margin was 1,568 bytes.
Adapter absence produced bounded retries. These samples do not establish OOM
immunity, long-soak stability or loaded simultaneous-radio qualification.

## Physical observation and limits

The owner confirmed dev.61 returned to a normal page with clean text and working
swipes. The later report of old shortcut behavior was traced to the saved jump
binding and corrected as described above.

Action-branch entry, browse/return, edge/orientation/cycling and extended touch
qualification are recorded separately from ordinary-page confirmation. Native
fixtures cover deliberate hit regions, confirmation drift, holds/drags, direct
Stop, attention return and stable widget count, but do not replace physical checks.
Both vehicle-command qualification masks and public configuration/OTA flags remain
disabled. No vehicle command was issued. Parked Jeep identity/guards, actual
Start/Stop/restoration, thermal/fallback and child-source continuity remain open.

Raw preference/configuration backups, screenshots, addresses and serial logs remain
under ignored `artifacts/device-rollout-dev61/`. Website impact: existing command
marketing does not gain a new live-control claim; no extra website publication is
needed for this indoor qualification record.
