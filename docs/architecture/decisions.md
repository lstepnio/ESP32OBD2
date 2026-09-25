# Architecture decisions

Status is **proposed**, except the existing firmware toolchain baseline. Each decision can be revised with measured evidence.

| ID | Decision | Reason and tradeoff | Revisit when |
| --- | --- | --- | --- |
| ADR-001 | Gauge owns adapter, phone connects to gauge | Autonomous operation, single source of samples; two adapter links plus phone add radio/memory pressure | Dual-link spike fails on board |
| ADR-002 | Native Kotlin/Compose companion | Android accessibility, lifecycle, BLE and foreground-service support; Android-only initially | A funded iOS requirement appears |
| ADR-003 | ESP-IDF 5.4.1 + NimBLE + LVGL 9.2.2 baseline | Matches working firmware; pin display port 2.7.2; old pin requires upgrade review | Before first production release or upstream security fix |
| ADR-004 | Explicit templates and shared tokens | Clear 240 × 240 UI, bounded renderer memory; no arbitrary layout scripting | Hardware/UI measurement supports more complexity |
| ADR-005 | Capability-driven PID discovery + curated manufacturer profiles | Broad coverage with honest support states; no exhaustive manufacturer scan | A documented protocol offers safe enumeration |
| ADR-006 | Typed bounded numeric decoder | Cross-language parity and resource bounds; limited complex payload support | Real profiles require additional operators |
| ADR-007 | BLE bootstrap/control, shared BLE/Wi-Fi bulk protocol; A/B rollback | BLE works without network setup; Wi-Fi can speed maintenance transfers; requires routing/trust/coexistence tests and one USB migration | WIFI-001 and RADIO-001 results |
| ADR-008 | JSON documents + versioned CBOR command envelope | Reviewable imports and schemas with compact radio transport; two representations need parity checks | Transport measurements favor a simpler encoding |
| ADR-009 | Offline local data, no mandatory backend | Vehicle setup works in garage/trail without connectivity; catalogs/releases downloaded separately | Signed community distribution needs hosted indexing |
| ADR-010 | Freeze contracts after a two-device vertical slice | Avoid premature protocol permanence; draft versions can change | Bond/read/apply/reboot slice passes |

[System architecture](system.md) records module boundaries. [Roadmap](../roadmap.md) defines the evidence needed to accept these decisions.

ADR-011 (proposed): source-aware multi-adapter data model from v1. Prefer simultaneous ECM + TCM links; measure three-link feasibility and retain explicit fallback TODOs. See [multi-adapter design](multi-adapter.md).
