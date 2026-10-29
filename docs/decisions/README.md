# Architecture decision records

Accepted development decisions and their tradeoffs. Implementation details are linked
below; [current state](../current-state.md) owns qualification, not this ledger.

| ID | Decision | Reason and tradeoff | Revisit when |
| --- | --- | --- | --- |
| ADR-001 | Gauge owns adapter, phone connects to gauge | Autonomous operation, single source of samples; two adapter links plus phone add radio/memory pressure | Dual-link spike fails on board |
| ADR-002 | Native Kotlin/Compose companion | Android accessibility, lifecycle, BLE and foreground-service support; Android-only initially | A funded iOS requirement appears |
| ADR-003 | ESP-IDF 5.4.1 + NimBLE + LVGL 9.2.2 baseline | Matches working firmware; pin display port 2.7.2; old pin requires upgrade review | Before first production release or upstream security fix |
| ADR-004 | Explicit templates and shared tokens | Clear 240 × 240 UI, bounded renderer memory; no arbitrary layout scripting | Hardware/UI measurement supports more complexity |
| ADR-005 | Capability-driven PID discovery + curated manufacturer profiles | Broad coverage with honest support states; no exhaustive manufacturer scan | A documented protocol offers safe enumeration |
| ADR-006 | Typed bounded numeric decoder | Cross-language parity and resource bounds; limited complex payload support | Real profiles require additional operators |
| ADR-007 | BLE bootstrap/control, shared BLE/Wi-Fi bulk protocol; A/B rollback | BLE works without network setup; Wi-Fi can speed maintenance transfers; requires routing/trust/coexistence tests and one USB migration | QUAL-02/QUAL-04/QUAL-08 measurements |
| ADR-008 | JSON configuration documents + bounded versioned binary commands | Matches implemented config and protected command codecs; maintain Kotlin/C vectors and compatibility notes | Transport measurements favor a simpler encoding |
| ADR-009 | Offline local data, no mandatory backend | Vehicle setup works in garage/trail without connectivity; catalogs/releases downloaded separately | Signed community distribution needs hosted indexing |
| ADR-010 | Freeze contracts after a two-device vertical slice | Avoid premature protocol permanence; draft versions can change | Bond/read/apply/reboot slice passes |
| ADR-011 | Source-aware multi-adapter data model | Prefer simultaneous ECM and TCM links with explicit fallback if three-link measurements fail | Three-link hardware spike completes |
| ADR-012 | Dedicated dual config slots before full writes | Existing 24 KiB NVS cannot stage and retain a 64 KiB document; inactive-slot validation protects active config | Flash geometry, image growth, and USB migration are measured |
| ADR-013 | Curated off-road profile actions with bounded gauge gestures | Simple autonomous controls with explicit controller support, source routing and uncertain-outcome handling | Action contract and controller qualification complete |
| ADR-014 | Coupled vehicle/gauge setup in one SharedPreferences commit | Existing bounded offline storage and backward reads; publish only acknowledged state via serialized off-main writes, not a second independently saved association document | Storage scale or a concrete transaction requirement warrants a migration |
| ADR-015 | Shared alert lifecycle and history across threshold, MIL/fault and external producers; diagnostic endpoint independent of adapter | Autonomous gauge truth, consistent UX, bounded context and recoverable sync; event storage separate from setup; clearing remains explicit and qualified | Phased implementation, resource measurements and controller/Android qualification |

| ADR-016 | Shared reference core with private hardware adapters, target descriptors and per-target RC promotion | Small ports, common behavior and controlled distribution; each hardware target still needs physical qualification | Second-board bring-up or virtual display demonstrates a missing seam |
| ADR-017 | [Deterministic opt-in Smart cycling](../architecture/smart-page-cycling.md) over existing scoped samples and alert episodes | One setting; bounded insertion/fairness and phase-aware hints preserve predictable navigation. Additive protected settings retain older App/FW compatibility; no learned model or extra polling owner | Labelled loaded evidence shows distracting choices, additional contexts are needed, or another surface requires different dwell rules |

| ADR-018 | [Typed phone companion content and isolated synthetic harness](../architecture/phone-companion-content.md) | Preserve autonomous instrument truth, typed bounded content, lexical owner sessions and explicit source feasibility gates; no live provider or background activation | Scoped wire, supported navigation/media acquisition and separate physical/provider approval justify activation |

| ADR-019 | [Display-only bounded interpolation](../development/reading-catalog-alerts.md#gauge-presentation-smoothing), keeping raw samples authoritative | Improve motion/text legibility without predicted measurements or changed control/alert freshness; two visible-metric states on the existing UI timer. Saturated transport still needs honest expiry | Loaded comparison shows distracting lag, misleading bins or a different surface needs its own presentation policy |

## Implementation details

- [System boundaries](../architecture/system.md): ADR-001 through ADR-010.
- [Vehicle routing](../architecture/multi-adapter.md): ADR-011.
- [Configuration storage](../architecture/config-storage.md): ADR-012.
- [Profile actions](../architecture/vehicle-actions.md): ADR-013.
- [Phone storage](../architecture/android-runtime.md#storage-and-recovery): ADR-014.
- [Shared alerts and diagnostics](../architecture/alerts-and-diagnostics.md): ADR-015.
- [Platforms and release boundaries](../architecture/platforms-and-releases.md): ADR-016.
- [Smart page cycling](../architecture/smart-page-cycling.md): ADR-017.
- [Phone companion proposal](../architecture/phone-companion-content.md): ADR-018.

Add a short numbered record here for a major choice, explaining the reason, tradeoff
and revisit condition. Use a separate file only when the decision needs substantial
context. Keep task status in the [backlog](../backlog.md).
