# One vehicle dashboard and optional second adapter

Updated 2026-10-07. Source review and offline evidence are separate from vehicle qualification.

## What the previous fixes missed

1. `dashboardDraft()` merged pages only if a transmission child existed. A legacy
   TCM-only profile still offered only TCM readings; an ordinary profile could not
   choose supported transmission readings without creating a second connection.
2. `AppModel.transmittedDraft` and `configurationBytes` still sent only the selected
   controller draft in single-adapter mode. The preview could contain five pages
   while the installed gauge received only the TCM subset.
3. Expert source selection changed the active draft and persisted that choice per
   gauge. Reconnection could restore the controller subset. Car adapter selection
   also inherited the last Expert selection.
4. Firmware inferred service 01 or 22 from the adapter's role. It rejected captured
   transmission definitions on the primary connection. `ble_obd` additionally
   required enhanced replies from the connection's discovery ECU and retained one
   CAN request header for that entire connection.
5. Both firmware and Android rejected mixed-controller Dual pages. Removing the
   second connection deleted its pages. Missing PIDs repeatedly consumed normal
   polling slots without independent failure backoff.

These are source-level causes consistent with the owner's report. The exact
installed phone state at the reported vehicle incident was not captured.

## Implemented design

- One vehicle owns one dashboard, one page order and its gestures/alerts. ECM is the
  primary logical connection. Supported transmission readings are selectable in
  that dashboard even without a second adapter and for legacy TCM-only profiles.
- Expert selects a physical binding to edit. It never selects a dashboard subset.
  Car edits the primary binding. Reconnection restores the vehicle's primary draft;
  ordinary editing and review/send include the same whole vehicle page list.
- With one adapter, all definitions reference the primary transport. Their request
  service, CAN header and expected ECU remain distinct. Moving the adapter between
  separate ports does not change the dashboard; inaccessible definitions expire.
- Explicitly enabling the second adapter routes captured transmission definitions
  through the child and engine definitions through the primary. Binding identities
  must be distinct. Pages, order, alerts and gesture targets do not change when the
  transport mode changes. Existing internal child drafts remain storage details.
- Cross-controller Dual pages are allowed. Each value retains independent age and
  availability. Removing the second adapter retains all pages and gestures and
  prepares a primary-only route after reviewed send.
- Firmware selects request service per definition and serializes CAN header changes
  within the existing adapter worker/mutex. Header selection and reply share one
  operation budget; uncertain header changes invalidate the cache. A physical
  enhanced request remains restricted to the captured 7E1/7E9 identifiers, not an
  arbitrary vehicle control path.
- Each adapter retains its own worker, parser, generation and reconnect state.
  Missing reading retries back off independently to five seconds. A worker with
  no selected display definitions still polls its diagnostic background jobs.
  A failed child never requires the parent radio to become available first.
- Existing installed source roles drive status polling until a new setup is sent.
  Desired routing does not masquerade as verified installed state.

## Compatibility and evidence limits

Android source dev.44 uses phone profile schema 10; schemas 1..9 remain readable.
Saving a mixed dashboard preserves profile IDs, bindings and legacy page identities.
Older Apps reject schema 10 rather than execute a silently filtered setup.

Firmware source dev.45 adds development capability `va:1` for per-definition
routing and mixed pages. Android blocks the new payload on older firmware.
Configuration wire schema stays 2. Compact `qs` aliases legacy `quickSelect` so
capability reads stay within 255 bytes. Public config/OTA/link qualification flags
remain disabled/one.

Only existing engine PIDs and captured JSS temperature/gear definitions are
executable here. A common diagnostic connector does not mean every vehicle exposes
these calibration-specific enhanced requests. Source/ECU attribution stays inside
parsing and diagnostics. TCM alerts and transmission-fault polling through a single
shared primary transport remain unimplemented; the optional child retains its
independent TCM fault path. No new PID meanings are inferred.

## Fast physical check

1. Install the signed dev.45 candidate through App/Wi-Fi after release authorization
   and successful CI; confirm exact running image and healthy boot before sending.
2. Using the existing Jeep vehicle and one Vgate, leave second-adapter mode off.
   Add engine RPM and coolant alongside Gear/Temperature, review the complete list,
   send once, and confirm all pages remain swipeable.
3. In the TCM connector with ignition on, verify temperature/gear update while
   missing engine readings become unavailable. Engine off may not produce gear.
4. Move the same adapter to ECM with the Jeep parked/idling. Without another setup
   send, verify engine RPM/coolant return while missing TCM values expire. Moving
   ports might not physically reboot the adapter; capture reconnect and no-data
   behavior separately.
5. Unplug/replug the adapter. All values expire and return as their ECU permits;
   settings, page order, phone pairing and touch remain intact.
6. With two distinct adapters, enable the child in Expert and send the same dashboard.
   Test each absent at startup, each lost/restored, then phone background/resume and
   gauge restart. The remaining adapter must keep its readings current. Record
   per-value freshness, sessions and time to recovery. One adapter cannot qualify
   simultaneous operation.

## Verification

Results are recorded below after each completed check. Builds and synthetic/native
UI fixtures do not establish physical port switching or dual-radio liveness.

- 123 Android JVM tests passed, including single/dual projection, legacy TCM-only
  editing, route removal, whole-dashboard import and capability compatibility.
  Debug APK, instrumentation APK and lint passed.
- ESP-IDF 5.4.1 built source dev.45. Production configuration compiler/partition
  fixtures and core scheduler fixtures passed AddressSanitizer/UBSan. Mixed pages,
  one transport with both request services, duplicate binding rejection, unavailable
  reading backoff and diagnostic-only workers are covered offline.
- Ten final native Pixel UI/stored-state tests passed: ordinary and legacy reading
  pickers, mixed secondary choices, Expert route selection, primary-only Car adapter
  editing and gauge picker copy. Three deletion fixtures also passed earlier in this
  increment. Fixtures issued no vehicle/configuration writes or real deletions.
- App dev.44 installed with data-preserving replacement. Profile, association and
  presentation preferences matched the baseline byte-for-byte after UI checks.
  Protected readback/resume passed in 11.305 s: opening 6.432 s, resume 2.789 s,
  exact installed signed dev.44 identity/OTA health 2 and unchanged configuration
  revision 45/digest and settings. No firmware/configuration send was performed.
- Signed dev.45 publication, App/Wi-Fi installation, physical ECM/TCM port switching
  and independent two-adapter absence/recovery are still pending. Do not describe
  those source/offline results as physical qualification.
