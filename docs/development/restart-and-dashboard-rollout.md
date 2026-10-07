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

## Display persistence follow-up

The next opt-in bench test enabled five-second cycling and resent the same setup.
Serial boot evidence showed dev.42 loading revision 36, but warm-App confirmation
failed to complete. The owner reported that the display still responded. This is
not evidence of a frozen display or successful protected settings confirmation.
The first test's cleanup opened an independent GATT session and masked the original
failure; the revised test uses the ordinary serialized settings poll, records errors
before cleanup, and retains the original failure if restoration also fails.

A separate restoration attempt exposed duplicate Android MTU/service callbacks and
a null transfer-service lookup. App dev.39 guards discovery and service handling once
per session across the protected/capability clients, and passes the validated control
and state characteristics with the Ready event. A Session no longer re-queries a
service cache that a late discovery callback can change. Disconnection, service
change and missing service still fail the operation; there is no uncertain write replay.
This addresses the observed discovery race, not every possible cause of the original
post-restart timeout. Protocol bytes and installed firmware are unchanged.

The duplicate-discovery guard allowed the isolated settings restoration (4.875 s),
but the next warm restart still timed out. Logs showed a service-change event,
then a connected link with no MTU callback. The revised test retained this original
failure and successfully restored cycling Off through normal App polling/writes.
The shared `GattDiscovery` helper now starts service discovery after two seconds
if MTU negotiation gives no callback, guards duplicate starts and cancels pending
fallback work on close. Transfers keep the conservative 23-byte MTU when an agreed
size is unavailable. Protected authentication and reply validation are unchanged.


The fallback and a read-without-MTU experiment did not qualify warm restart recovery.
The latter was removed. MTU negotiation remains in the connection path. Diagnostic
logging then showed protected reads timing out following the boot service-change
announcement. Closing the connection during this handshake did not recover in the
bounded attempts, although later ordinary polling restored settings.

Final App dev.39 keeps that connection alive and rediscovers its service database
when invalidation arrives before handshake completion. A generation token prevents
old discovery results becoming Ready; a 750 ms settling window catches late cache
invalidation before protected commands start. Validated characteristic references
travel with Ready. Service changes after handshake completion still fail the active
operation, preserving uncertain-write semantics. The connection deadline and close
cleanup remain bounded; no setup or update is automatically replayed.

The physical `DisplayPersistenceJourneyTest` passed in **58.049 seconds** overall.
It confirmed five-second cycling, resent the same setup, confirmed healthy running
revision **42** with cleared trial, then obtained a fresh ordinary serialized settings
poll. Configuration confirmation plus fresh settings took **22.065 seconds** from
send start. Brightness 100%, rotation 270°, Imperial units and five-second interval
survived. The exact setup document and shortcut survived apart from base revision.
The test restored cycling **Off**. Running revision 42 digest:
`69b3f7171cc6f97ae3f840e12b73056fdca369df3d0d11c5c4b880da12497ba9`.

Final verification: 101 Android unit tests, debug APK, instrumentation build and
lint passed; the physical persistence/recovery test passed. The earlier isolated
restoration test passed too. This qualifies protected persistence and same-App
configuration restart recovery in this session, not visual automatic cycling,
physical cooldown, broader recovery immunity, fresh firmware OTA or two adapters.
The owner separately reported a responsive display during the initial failure.
Phone wake preferences were restored and firmware remains dev.42.


## Physical cycling check

On 2026-10-07, the App Settings path confirmed five-second cycling. The owner
watched the physical gauge for about 20 seconds without touching it and confirmed
TRANSMISSION and GEAR alternate approximately every five seconds. The App then
confirmed cycling Off again, retaining 100% brightness, 270° orientation and
Imperial units. The owner then confirmed the physical cooldown sequence: three upward swipes
from GEAR jump to TRANSMISSION; an immediate return/repeat stays on GEAR during
cooldown; after six seconds, three upward swipes jump again. This also establishes
responsive physical navigation/touch after the revision-42 restart. The phone
readback and the owner observations are separate evidence.

An initial read-only phone test was interrupted by the Pixel charging screensaver
at its screenshot assertion. After the owner unlocked the phone, the opening/resume
test passed in 9.492 seconds, checking exact dev.42 firmware/ELF identity, OTA health 2,
confirmed running revision 42, automatic settings refresh and unchanged saved profiles,
configuration and display settings. Bluetooth toggling and picker cancellation were
not enabled in this run. The owner observation above, rather than this read-only phone test, establishes
physical gesture cooldown for this session.
