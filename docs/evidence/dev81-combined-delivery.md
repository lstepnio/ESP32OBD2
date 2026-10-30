# Combined dev.81 delivery

> Historical evidence. Delivery recorded 2026-10-30. [Current state](../current-state.md)
> owns the latest installed baseline; [backlog](../backlog.md) owns remaining work.

The owner explicitly authorized merging PRs #126, #127 and #128 after CI,
installing the App, publishing/installing firmware over Wi-Fi, indoor checks and
branch cleanup. No vehicle controls, Restore/reset confirmations, bond resets or
recovery-record clearing were invoked.

## Source and release

- #126 merged as `81185a2`; #127 as `e28d91b`; #128 as `f4fc353`.
  All four required checks passed before each merge. Updating the stacked bases
  preserved the reviewed file trees; no shared history was rewritten.
- Firmware release source `a1297982019a8d50f7d95ab6017f9180b909b4f4` passed all four lanes in
  [quality run 41074213685](https://github.com/lstepnio/ESP32OBD2/actions/runs/41074213685).
  Main merge `f4fc353` has the same tree. GitHub automatically deleted merged
  branches, so a temporary exact-source release ref was used without code changes.
- [Dev.81](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.81),
  sequence 62, was signed/published by
  [run 41076280946](https://github.com/lstepnio/ESP32OBD2/actions/runs/41076280946).
  Catalog/bundle/image/evidence signatures, freshness, source, board/layout,
  descriptor, candidate configuration and evidence/image parity verified.
- Image: 1699296 bytes; SHA-256 `5c34a2f1e94eb3aae11203acac2eac6e46e6fcbf9318669ca5064976146aa1e5`.
  ELF `165bb38e4edfdd8653148a82a26525980a77b73ce1edf12bcc6f83f799061f6e`.
- App dev.58/code58: installed APK SHA-256 `5290f2de0a4b0e8f36fd46edca4e70f8e5fbef6d2c52cc5c18671e889f4b8610`.
  Built from `1a9712e`; its Android tree matches the release source exactly.
  Package replacement preserved data, and the installed APK bytes matched the build.

The existing parked-idle, brake-owner and orientation candidate switches were
retained. Burnout stays paused in normal menus. No public capability or
qualification flag was promoted; this is not a generally qualified vehicle release.

## Installed and checked

The App update first passed protected cache/action readback and automatic
connection/resume against healthy dev.80. The ordinary hosted catalog/download
and full App/Wi-Fi update then passed against dev.81. The sampled installation
journey took 74.3 seconds: Wi-Fi sending appeared at 17.8 seconds, verification at
58.6 seconds, and exact healthy alternate-slot confirmation at 74.3 seconds.
These are sampled stage observations, not exact wire throughput.

Two subsequent maintenance cycles opened in 1,581 and 1,687 ms, used different
session identities, completed authenticated status reads and closed without a
boot-nonce change. Automatic connection/resume, settings refresh, hardware-to-firmware
read chaining and both cached diagnostic endpoints passed. Final hardware read
reported Wi-Fi uninitialized (mode255). No global Bluetooth toggle was performed.

Preservation comparisons verified:

- Configuration revision66 and SHA-256
  `807623e49e2995cb50bad065979ed974b627503f4314bdff3b16db971697b153`.
- Saved vehicle profiles, gauge assignments, display/connection preferences and
  notification policy. Temporary monitoring/wake settings were restored.
- All command operations/phases, including retained ABS/TCS Unknown operation1.
  Legacy Burnout remained unavailable with operation0.
- All 90 events, 67 episodes and 18 configuration contexts; no row loss/change.
  The update journal cleared only after exact healthy-image confirmation.

Private setup backups and detailed transport reports remain outside Git. The
website was not redeployed: its dynamic release selector can discover dev.81;
public copy and represented screens did not change in this version delivery.

## Remaining acceptance

Dev.81 physical panel/navigation confirmation is pending. The last owner panel
confirmation remains dev.80. Indoor automated passes do not qualify real rail-power
interruption, loaded dual-adapter radio/memory/timing, swapped-port correction,
actual ABS/TCS restoration, coolant/fallback protection or numeric idle targets.
The recorded single hardware snapshot is not a soak or proof of OOM immunity.
