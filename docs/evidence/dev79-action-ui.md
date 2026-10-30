# Dev.79 approved action presentation delivery

> Historical evidence. [Current state](../current-state.md) and [backlog](../backlog.md) own current support and remaining work.

Recorded 2026-10-29. The owner approved all 14 compact action screens, then requested
implementation and App/firmware delivery for testing. This delivery operated no
vehicle controls, confirmed no reset and cleared no vehicle recovery or diagnostic record.

## Source and checks

Firmware source `88d68c8623edeac0fc54f56e049bc82bbb5146d8` passed all four lanes in
[quality run 40994195316](https://github.com/lstepnio/ESP32OBD2/actions/runs/40994195316).
Local checks passed: 185 host tests, 14 review-server tests, 342 Android JVM tests,
30 focused managed UI tests, 103 full CI emulator tests, Android build/lint, format/contract validation and
normal/fallback/parked production LVGL plus configuration fixtures. Compact and
large-text action captures were reviewed. A final regression rejects a short tap
when recovery phase/scope changes while the finger is down; navigation remains local.
The superseded initial release run was canceled before signing/publication.

Signed [dev.79](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.79)
was published by [run 40995452275](https://github.com/lstepnio/ESP32OBD2/actions/runs/40995452275),
catalog generation 60. Catalog/image/evidence signatures, source inventory, board,
layout, candidate configuration and exact bundle/evidence application parity passed.

- Image bytes: 1699184.
- Image SHA-256: `f2a2c9d1ccd5a9e83f7eb093a5944208da61f8225dcb11972c0ccd5d46bd4f49`.
- ELF: `c1c77c5967fd7e053d54761410fb43dd0532ab9ecf97b05b96c3e6b578077f68`.
- App dev.56/code56 replacement APK SHA-256: `f1bfd53963625ec98e144f05c710979d4d725a63f516275d2f9c4dc4ac2a6e03`.

The APK was built from `1563ec7de7cf1d1004de40ed7ccb152dccc1bff2`. The final firmware follow-up changes only
C touch fencing, its native regression and canonical documentation; Android product
sources are identical between those commits. The App matched the installed signing
certificate and was installed without clearing data.

## Device verification and preservation

The signed hosted App/Wi-Fi transfer used the ordinary updater. Its test process
was force-stopped by an overlapping dev.78 recheck from the main chat after the
transfer. Android records USER REQUESTED / FORCE STOP, not an App fatal exception;
the overlapping test then observed dev.79 and failed its obsolete dev.78 expectation.
The interrupted WifiFirmwareJourneyTest is retained as **not passed**. Device
ownership was coordinated before continuing; the same image was not resent.

A separate fresh reconciliation verified the exact healthy post-boot image in the
alternate OTA slot and unchanged configuration/settings. Normal App recovery cleared
the matching update journal. Two protected maintenance opens/closes, automatic
foreground/reconnect/read-chain checks and cached diagnostic readback then passed. Transport evidence is separate from physical panel or
vehicle behavior. No USB flashing was used.

Running configuration revision 66 and SHA-256
`807623e49e2995cb50bad065979ed974b627503f4314bdff3b16db971697b153` were unchanged, as were display settings, vehicle
profiles, gauge associations and retained history. Every command phase/operation
matched the fresh baseline: ABS/TCS Unknown operation 1 remains pending; idle and
Burnout remain inactive. The approved UI simplifies presentation without erasing
that history or claiming restoration. Phone notification and Android wake settings
were restored after the bounded device session.

## Remaining physical checks

The owner confirmed clean physical display/navigation, three-up action entry and
removal of the yellow two-square icon. This observation is separate from transport
and fixture results; no Start, Restore or reset-confirmation operation was requested.
Parked Jeep Restore followed by the actual controller reset/state check, uncertain
recovery and dual-adapter swapped-port/loaded behavior remain unqualified. Restart-free
ABS/TCS restoration is not implemented or implied. Burnout remains paused.

[PR #127](https://github.com/lstepnio/ESP32OBD2/pull/127) is stacked on PR #126;
neither main merge nor branch deletion was requested. Website screenshot refresh
is deferred to the normal daily window after physical UI validation; this change
introduces no new vehicle-support claim or public capability.
