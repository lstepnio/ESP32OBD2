# Architecture decisions

Decisions describe chosen architecture, not proof every proposed extension is shipped.
ADR-001..004, 006..009 and 012 are implemented development choices. ADR-005 has
bounded implemented slices; broader discovery is proposed. ADR-010 public contract
freezing and ADR-011 simultaneous radio operation remain qualification gates.
ADR-013 implements local page jumps only; controller writes are proposed. ADR-014
records current durable phone setup. [Current state](../current-state.md) owns support.

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

[System architecture](system.md) records module boundaries. [Backlog](../backlog.md) records outstanding acceptance; [roadmap](../roadmap.md) orders outcomes.

[Multi-adapter design](multi-adapter.md) details ADR-011. [Configuration storage](config-storage.md) details ADR-012 and its migration gate.

[Profile actions and gauge gestures](vehicle-actions.md) records ADR-013 and its local foundation; no vehicle controls are enabled.
