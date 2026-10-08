# Vehicle dashboard and adapter routing review

> Historical evidence. Versions, measurements and unfinished steps below describe that session.
> Current support is in [current state](../current-state.md); remaining work is in
> [the backlog](../backlog.md). These notes are not standing implementation instructions.

The partial App dev.42/43 editor fixes below are historical. The current execution
model and qualification plan are in [whole vehicle review](logical-vehicle-review.md).

Updated 2026-10-07. App dev.42, unchanged signed firmware dev.44.

## Product model and finding

One vehicle owns the readings, pages, alerts and local page actions. The primary
connection normally supplies all supported vehicle data; a swap can configure an
optional transmission child in Expert. Adapter identity is routing information,
not a separate vehicle or an ordinary editor filter.

The previous `editorDraft = transmittedDraft` exposed only TCM readings whenever a
single moved adapter was assigned to TCM. Combining the editor additionally
required two distinct adapter bindings. That confused local editing with radio
execution even though the saved vehicle already contained both drafts.

## Reviewed paths

| Path | Result |
| --- | --- |
| Profile storage and legacy recovery | Retain schema 8, both bindings, draft ownership, stable page IDs/order and explicit legacy attachment. No parent inference or data deletion. |
| Gauge preview and Customize | Use `dashboardDraft()` for the full vehicle, independent of `bothAdapters` and current ECM/TCM context. |
| Add/change reading and page reorder | Use the vehicle reading list; save changes into the owning internal draft, retaining sibling pages and bindings. |
| Car gesture targets | Use that same vehicle page list and action ownership, including single-adapter operation. |
| Alerts | Offer implemented engine alerts. Remove TCM entries from All alerts rather than offering edits that silently fail. Transmission alert support remains unimplemented. |
| Review, send and confirmation | Show outgoing pages, alerts and actions. Single-adapter sends remain source-specific; explain that other vehicle pages stay saved. Counts and running confirmation refer to sent pages. |
| Multiple vehicles/gauges | Preserve selected gauge context and vehicle identity; no global adapter reassignment or automatic sends. |
| Status and faults | Retain one shared status widget/vehicle Car page; controller attribution and independent freshness remain internal evidence and status detail. |
| Firmware config compiler | Source/adapter/request identities remain explicit. Reject ambiguous two-adapter bindings. Same-source Dual restriction remains. |
| Firmware poll/parser/recovery | Independent source generations, request/response filters and unavailable state remain necessary. Making the UI uniform must not relabel a TCM response as ECM evidence. |
| Firmware display | Already renders page/metric names and values independently of ordinary setup tabs. No firmware or wire change needed to fix the reported picker defect. |

## Compatibility limits

The validated Jeep enhanced requests are calibration-specific. Moving their labels
into one catalog does not prove that every common vehicle supports those bytes
through its primary connector. Ordinary single-source setup stays unchanged; the
optional child is specific routing for the swap. One adapter moved between separate
ports cannot supply both ports simultaneously. Two-adapter radio operation remains
unqualified until the owner has a second adapter.

Mixed ECM/TCM readings on one Dual page remain rejected by the compiler. The UI
therefore limits that secondary picker to compatible readings. Removing that limit
requires coordinated runtime identity/display sample changes and separate tests,
not just removing a filter. This increment fixes vehicle-wide page editing without
claiming that additional firmware path.

## Verification

- 110 Android JVM tests passed, including same-physical-adapter editing, unfinished
  child binding, round-trip page storage and preserving execution rejection.
- Debug and instrumentation builds and lint passed.
- Three native Pixel tests passed: shared single-adapter reading picker, compatible
  secondary picker and the actual stored Jeep AppModel projection. The stored-state
  test disables automatic Bluetooth discovery and performs no edits or sends.
- Phone profile, gauge-association and presentation preferences matched before/after
  those tests byte for byte. UI fixture screenshot is private synthetic evidence.
- Firmware dev.44 was not changed or flashed for this fix. Its earlier physical
  evidence is distinct from the new App UI tests.
- Final App dev.42 protected readback/resume passed in 12.862 s: opening 7.940 s,
  resume 2.795 s, exact signed dev.44 ELF/healthy boot and unchanged configuration
  revision 42. No configuration or firmware send was performed. An earlier run
  was obscured by Android's notification panel and failed the foreground assertion;
  it was retained as a failed attempt, not counted as a pass. The UI capture now
  asserts the foreground package and was repeated successfully.
- Final profile, association and presentation preferences still matched the baseline
  byte for byte. Pixel wake settings restored; App dev.42 remains installed.


## App dev.43 deletion follow-up

- Page deletion was hidden in the options menu and vehicle deletion was missing.
  Customize now has Delete page; Manage pages and Car > Your car have visible
  Delete actions with confirmation and cancellation.
- Last-source page deletion now works. Bindings and alerts remain; target gestures
  are cleared. Keep one vehicle page overall and at least one vehicle profile.
- Phone profile schema 9 round-trips an empty internal ECM/TCM draft; schemas 1..8
  remain readable. Previous App versions cannot read newly saved schema-9 profiles.
  Empty executable source drafts cannot be sent; review shows zero pages honestly.
  Combined firmware execution still requires pages for each configured source.
- Vehicle deletion commits phone storage first, preserves sibling profiles and
  leaves affected remembered-gauge contexts unresolved instead of retargeting.
  Installed configuration and bonds remain unchanged. Pending setup/update outcomes
  block vehicle deletion.
- 115 JVM tests and six native Pixel UI/stored-profile tests passed. Fixtures tested
  delete/cancel callbacks without deleting real phone data. Debug/test builds and
  lint passed. App dev.43 installed with data-preserving replacement; phone profile,
  association and presentation preferences remained byte-for-byte unchanged.
  Firmware and wire formats unchanged.
- App dev.43 protected gauge readback/resume passed in 14.615 s: opening
  9.251 s, resume 3.245 s, exact signed dev.44 image, healthy boot and unchanged
  configuration revision 42. No configuration or firmware send was performed.
- The final installed build passed the stored-profile native check again after the
  reassignment fix. Phone profile, association and presentation preferences still
  matched the pre-install baseline. Pixel wake settings were restored.
