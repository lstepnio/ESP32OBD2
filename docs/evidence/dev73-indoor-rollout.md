# Dev.73 idle recovery delivery

> Historical evidence. This dated rollout records its own scope; [current state](../current-state.md) and [backlog](../backlog.md) own current support and remaining work.

Recorded 2026-10-28. Live ECM idle recovery is not yet qualified.

## Source and publication

PR #117 passed all four checks on `96e61fa17b881808cc541509cd238d9d8cf80fe3`
(run `40767956865`) and merged as `ce8c381c04749463c40a5a0884e9c9da1ceae87c`.
The checked and merged trees match. It adds the captured session prerequisite to
uncertain idle recovery; the [contract](../architecture/vehicle-actions.md#read-only-recovery-of-an-uncertain-idle-operation)
owns the unchanged inactive-status, exact-identity and settled-RPM gates.

The first publication run `40769806926` compiled firmware but failed before signing
or publication: Git rejected the container checkout's UID during evidence packaging.
No assets or release generation were published by that attempt. PR #118 scopes Git
trust to the exact checkout and preserves dirty-source rejection. A real Git fixture
reproduces the rejection, verifies command-scoped trust and checks dirty-source denial.
All four quality lanes passed on its source `6d04d43bac2ac324a7b66e60cb23e6c07a8c26f6`
(run `40771143532`). This source has no firmware-directory changes relative to the
approved PR #117 source. PR #118 remains open for separate main-merge approval.

Signed [dev.73](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.73)
was published by run `40772741580` from that checked candidate, consuming generation 54.
Catalog expiry/signature, bundle/image signature and hashes, board/layout/protocol,
image descriptor, build-evidence signature/source/inventory and application parity
were independently verified. The image is 1,680,224 bytes, SHA-256
`d53f739f77eb2d5a84c8ec7ffb8dea33074f3f785e9c70f725313b92074b6bb8`;
its full ELF hash is
`69efa45eb603f747a9d7f06beba4bf2b83ecb81a8a4356ff63df1c8357d5efe0`.
Parked-idle and owner-supervised orientation candidate options are retained;
ordinary qualification/public flags and ABS/TCS execution remain off.

## Authorized USB installation and preservation

The owner initially authorized App/Wi-Fi, then explicitly changed this delivery to
USB while taking the Pixel away. No Wi-Fi or Bluetooth firmware transfer was attempted.
Before departure, a protected read-only journey passed in 24.174 seconds on dev.72:
configuration revision 65/digest
`5fe79e9b78e95211fc12d7459140b3a63ecd4426573ed3a967c59b35e8fdcec8`,
five pages, saved actions/filters, brightness 100, rotation 270 degrees, Imperial
and ten-second Smart cycling. Idle operation 1 remained Unknown indoors.
Original phone alert preferences were restored byte-for-byte with the App stopped;
the debug awake helper restored the original timeout/charging settings. No APK was
installed; the PR #115 App remains installed.

The installed partition map matched the reference. Private backups retain the
bootloader/partition table/NVS/PHY/OTA metadata/both configuration slots and the
complete current 3 MiB application slot. Its application bytes matched the published
dev.72 image. An initial high-baud read failed before writing; stable 115200-baud
reads were used thereafter. The unused full-flash read was replaced with scoped
backups of the preserved prefix and complete active slot.

Only the verified application was written at `0x360000`. The tool's target flash-hash
verification passed. Before booting, the entire `0x00000..0x51fff` prefix compared
byte-identical to the final pre-write copy, preserving pairing/configuration and
OTA selection. No erase-all, partition/bootloader update, bond reset, configuration
write, uncertainty-marker reset or actuator command was performed. Boot logs then
confirmed slot `0x360000`, version dev.73, matching ELF prefix `69efa45eb`, revision 65
with five pages, and retained idle Unknown/operation 1.

## USB-connected runtime observation

A bounded 150-second serial capture observed one boot and no reboot, panic, heap
corruption, stack-canary or boot-health timeout marker. Two periodic samples retained
125,343 bytes of free internal memory, 31,744-byte largest internal block and
1,958,464 bytes of free PSRAM, with zero allocation failures. UI progress advanced
from 1,003 to 2,003 with zero queue drops and 3,596-byte observed UI stack margin.
Observed application stack margins were 1,688 then 1,576 bytes. Orientation samples
continued and both absent vehicle adapters retried without stopping UI progress.
These are short, offline baseline observations, not a loaded or Wi-Fi memory soak
and not proof of panel scanout or touch behavior. The serial observer closed cleanly
and left dev.73 powered/running.

## Remaining qualification

The Pixel was absent for post-installation protected BLE identity/setup readback,
App recovery and authenticated Wi-Fi lifecycle checks. Those were not counted as
passed on dev.73. Physical display/touch/navigation and the parked session/inactive
report/settled-RPM recovery sequence remain to be confirmed when the owner returns.
The prior dev.72 radio checks remain historical evidence, not dev.73 results.

Raw phone backups, device addresses, flash contents and serial logs remain private
under ignored `artifacts/device-rollout-dev73/`. PR #117's local/remote feature branch
and the failed publication ref were removed; the open PR #118 branch is retained.
Website impact: this corrective recovery change needs no new marketing claim or image.
