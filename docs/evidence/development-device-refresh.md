# Development device refresh, 2026-11-10

> Historical evidence. [Current state](../current-state.md) owns installed support;
> [backlog](../backlog.md) owns remaining qualification.

The owner approved a bounded development refresh: latest merged App, signed LCD
dev.86 and private OLED Balanced w15, followed by indoor checks and safe cleanup.
Production-release planning was cancelled. No source behavior, vehicle control,
recovery record, pairing bond or unfinished feature branch was changed.

## Artifacts and installation

- App source: `4f36ef20206936754d1412135c018bf297903644`, main with successful
  [quality run 42916857814](https://github.com/lstepnio/ESP32OBD2/actions/runs/42916857814).
  The App remains dev.58/code 58; this identifies a new build by source and digest,
  not a newly published version. Existing private OLED public-key provisioning
  and the signing certificate were verified before the in-place installation.
- Installed APK SHA-256:
  `42539155d5b9643d801968b1268c60bf95b4969924ec856df3da8c673ef9e167`.
  APK pulled back from the Pixel matched the build byte-for-byte.
- [LCD dev.86](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.86)
  catalog, application and retained build-evidence signatures verified, with exact
  source `edf41b689f9f2fa8c80e883d677a385f8b2f4a0b`, target and A/B layout.
  Authenticated ELF:
  `ce35a810e85747f1544802edbd3de5c367b2999d4a1a2840188696471d7c8724`.
- The private OLED Balanced w15 bundle signature, target and full ELF matched
  authenticated running readback. Its version text is limited to 16 bytes by the
  existing boot-identity protocol; the full ELF and signed image descriptor identify
  the exact build. Detailed private artifacts and receipts remain outside Git.
- Both gauges reported OTA state 2 (healthy). Neither needed another transfer.
  No new firmware release, USB flash or full Wi-Fi upload was performed.

## Checks and preserved state

The privately provisioned debug APK build, targeted instrumentation compilation,
Android lint and 387 JVM tests passed. Temporary exact-device tests exercised
protected identity, gauge selection/reconnection, automatic settings readback and
the actual App Connection icons switch Off/On on each board. The update recovery
journal was clear. Initial test-only failures were corrected: an ambiguous Gauge
text selector and an assertion expecting more than the protocol's 16 version bytes.
The corrected cases passed without product code changes or relaxed ELF checks.

- LCD dashboard revision 68 and its hash stayed unchanged.
- OLED dashboard revision 7 and its hash stayed unchanged.
- Both Day levels are 100%, Night levels 10%, with icons On. The OLED's saved Night
  level was 20% at entry and was changed to the approved 10%. Its schedule/location
  and both boards' other display preferences were retained.
- Saved profiles, gauge assignments and alert policy survived App replacement.
  The SQLite database bytes changed, so installation work paused for investigation.
  Both databases passed integrity checks and every table's logical rows matched:
  52 events, 34 episodes, 27 configurations, two cursors and no reports.
  Database file identity was not misreported as logical data loss.

The owner separately confirmed clean layouts without page dots and working swipes
on both gauges. The OLED right-side button brightened the display, then dimmed it
after the second press. Those observations establish the requested indoor panel
and button check. They do not establish adapter icons under load, vehicle control,
interruption recovery or a long-running memory soak.

## Cleanup and follow-up

Nine merged, unused public local branches were removed after checking ancestry.
The unused public/private local main refs were fast-forwarded to their origins and
one obsolete worktree reference was pruned. Six dirty worktrees, unmerged branches,
private prototypes and saved device backups were retained. No shared history was
rewritten and no unmerged work was integrated.

The original test APK was restored and its digest rechecked; temporary device test
files were removed. Pixel wake settings and the originally selected OLED were
restored. Final setup/association, notification and virtual-gauge preferences
matched their backups byte-for-byte; every history table retained identical rows.
Private preservation/readback receipts remain outside Git. The App was left open.

Next Jeep session: test one gauge at a time. Confirm each configured OBD2 icon
appears for a ready adapter, disappears on disconnect and returns on reconnect.
On OLED, independently interrupt ECM and TCM and confirm the healthy sibling's
icon and readings continue. An unconfigured child must not show an icon.

Website impact: no new public feature or marketing capture was introduced, and no
publication is needed for this device-only refresh. Updated status inputs should
be reviewed in the next normal website batch. Development/public/private boundaries
and the existing daily publication policy remain unchanged.
