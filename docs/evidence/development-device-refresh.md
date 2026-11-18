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

## Refresh 2026-11-18

The owner authorized cleanup and current App/firmware delivery, then explicitly
approved LCD dev.88 publication. Sources and acceptance:

- App dev.59 runtime source `c757f2e`; the private-OTA-provisioned APK retained the
  installed certificate. Exact pulled-back APK SHA-256:
  `8fa3ee3a83bad423e880a5f7ce6870dbe74bb3be8508e2edba0cdadd9eacf1e8`.
- Reference LCD [dev.88](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.88)
  was published from `555d641102c710db38ab82b7ee05817e83307481` after all four
  [quality lanes passed](https://github.com/lstepnio/ESP32OBD2/actions/runs/44381467909).
  Protected signing used catalog generation 68. Catalog/build-evidence signatures
  verified. Running ELF:
  `1b08f538c32f5a0453c12e2748919a99462f128739d8e5c5e3926d67fa0ab4f8`.
- Private OLED `0.2.0-dev.88-amoled.2-w27` uses integration `b19f2d0` and shared
  core `d3f4a5a`. Its four private CI lanes passed; integration PR #27 merged as
  `6f00c0e`. Exact private ELF, signed bundle, build and device receipts remain
  outside public Git. Its target key and board were checked independently.

Both installations used the ordinary App/Wi-Fi owner, changed OTA slots and
confirmed exact ELF plus healthy state 2 and a cleared update journal. OLED took
81.7 seconds and LCD 72.5 seconds from install admission through confirmation.
OLED dashboard revision 8 and LCD revision 68, document hashes, display settings
and profiles survived. Read-only preflight correctly stopped when the LCD was
not powered; it succeeded after the owner swapped boards.

Targeted post-install checks passed authenticated connection, automatic settings
refresh, icons Off/On, Day 100%, Night 10%, and retained configuration. App
preferences and every logical alert-history table matched the original backup
after replacement. Final comparison retained all 64 events, 44 episodes, 28
configuration records and zero reports exactly. The two cursor boot fields advanced
for the verified firmware restarts; keys and sequence fields were retained. Only the
intentional OLED-to-LCD selection changed in setup, and the signed catalog trust
checkpoint advanced to generation 68. The notification policy matched exactly.
Original Pixel wake settings and the exact original test APK were restored; temporary
delivery files were removed. No vehicle command or recovery-record clearing occurred.

Local host/native/configuration/render/fallback tests and App unit/build gates
passed. Four idle-preset and nine dashboard-editor emulator cases passed. Review
found late-code visibility and unsupported redline editing defects; the App fixes
preserve immutable entry evidence, scope bounded live notification deferral and
keep virtual redline editing independent. The final PR carries their CI outcome.
A later CI dependency check found the Plugin Portal's alternate Kotlin marker POM;
Google/Maven Central remain the verified origins, with checksums unchanged.

Cleanup pruned six nonexistent public and one nonexistent private worktree
references, and removed one merged inactive local branch. Thirty existing public
worktrees, including six dirty ones, were preserved. Incorporated public PRs
#138/#146/#166/#167 are superseded by #172; #159's historical evidence is retained
above. Private #18 is superseded by merged #27. Unfinished commerce/website PRs
#152/#162/#171 and their source branches are preserved, not silently integrated.

Remaining physical gates: current-build panel/touch and OLED button observations,
OBD icon disappearance/reappearance on both boards, independent ECM/TCM recovery,
loaded trends/warning timing, fault injection, cold power/time behavior and soak.
Website impact: this refresh adds no approved public support claim or new imagery;
review status/visual inputs in the next normal website batch, without publishing
unfinished commerce or design changes.
