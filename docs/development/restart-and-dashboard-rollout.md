# Restart confirmation and unified dashboard editing

Date: 2026-10-07. Android source App dev.38; installed firmware remains signed dev.42.

## Restart confirmation

Session timeouts use `TimeoutCancellationException`, a subtype of coroutine
cancellation. The old configuration and firmware restart loops rethrew these as
caller cancellation, aborting their remaining confirmation attempts. `restartRead`
now treats a bounded session timeout as unavailable readback and allows another
protected read. Actual caller cancellation still propagates and closes transport.
No write is repeated. Configuration saved/running confirmation shares a 90-second
read budget; firmware confirmation retains its bounded deadline and exact expected
ELF, partition/trial decisions. Missing, mismatched or rolled-back images do not
become success. The App/Wi-Fi transfer protocol and firmware bytes are unchanged.

On the physical Pixel and gauge, the opt-in `RestartConfirmationJourneyTest`
resent the already confirmed setup. The same warm App confirmed revision 35 as
healthy and active in 15,282 ms, without reopening, refresh or another write.
The whole test passed in 24.793 seconds. Readback retained the same document fields
apart from base revision; configuration trial was cleared. Running revision 35 digest:
`3f6d8db9e77aa395d53014993043d96f0e57ad4370df951338672ed7d5f14bd4`.
This qualifies configuration restart confirmation in this session. The shared
firmware confirmation helper passed timeout/cancellation unit tests, but a fresh
firmware OTA installation using this App is still a separate qualification gate.

## One vehicle dashboard

When the gauge uses both adapters, Customize now edits the combined vehicle page
list and offers one engine/transmission reading picker. It does not require an
Expert source switch to select a page or add its reading. Add, edit, remove, reorder,
alerts, review and actions operate on that dashboard. Internal ownership is derived
from reading definitions and saved back into the ECM parent or TCM child.

Profile schema 8 persists cross-controller page order; schemas 1 through 7 remain
readable with their original ordering. The combined review and exact wire projection
use that same saved order. Single-source projection and existing settings remain
unchanged. A combined setup keeps at least one page per configured controller;
last-page removal is disabled. Invalid edits retain the last saved draft and show
an editor message. The current firmware requires a Dual page's two readings to use
one controller; the second-reading picker exposes compatible choices rather than
creating a mixed-source page that cannot run. No public capability was promoted.

## Verification and evidence limits

- 101 Android unit tests: restart session timeout versus caller cancellation,
  combined page storage/projection order, add/remove target ownership, action
  mapping, invalid mixed-source/empty-controller pages, and legacy profile migration.
- Debug APK, lint and instrumentation build passed.
- Two isolated native UI tests passed on the physical Pixel in 3.677 seconds.
  These use synthetic fixtures and do not open Bluetooth or prove dual radio operation.
- The [combined picker example](../design/unified-dashboard/combined-reading-picker-example.png)
  is a native Pixel fixture screenshot in landscape with example data.
- The owner previously confirmed the three-upward-swipe page jump. Cooldown,
  automatic-cycle interaction and physical restart persistence observations remain
  pending; source recognizer tests are not those physical observations.
- Two-adapter hardware coexistence/recovery still needs a second adapter.

Private phone/device identifiers, saved document and logs stay ignored under
`artifacts/profile-actions-rollout/`. Phone wake settings are restored after debugging.
