# Multi-adapter ECM/TCM support

Owner use case added 2026-09-25: a swapped vehicle has separate OBD-II interfaces for ECM and TCM, each with its own BLE adapter. Support both data sources in one gauge and one vehicle profile.

## Preferred design

Gauge maintains **two central-role adapter links plus one peripheral-role phone link**, three total simultaneous BLE connections. Each adapter has independent identity/profile, GATT handles, ELM transaction queue, protocol/ECU context, parser, timeout, reconnect, poll budget, and discovery job. This differs from multiple ECUs behind one adapter, which share that adapter's serial transaction budget.

Keys are `(vehicleProfileId, sourceId, ECU identity, service, identifier, definition revision)`. ECM and TCM may both reply as `7E8`; address alone is never a unique vehicle-wide key. Stable source aliases such as `ecm` and `tcm` belong to the configuration. Adapter identity/bond data stays on the gauge and is bound to those aliases during setup, not copied as credentials in profile exports.

UI labels all ambiguous signals with source/ECU. Garage shows ECM adapter and TCM adapter as separate rows with connection, protocol, age, and signal quality. Discovery can target one source or both. A dual-value page can bind ECM coolant and a documented TCM temperature definition. A threshold binds one definition/source; losing TCM must not interrupt ECM polling or silently disable a TCM alert.

## Feasibility TODO: MULTI-001

**Unresolved hardware feasibility.** M1 now has two independently owned central connection contexts and serialized discovery, with TCM link activation gated by an explicit MAC. No simultaneous two-adapter operation has been measured. Baseline `sdkconfig` enables three NimBLE connections and both roles, but configuration capacity is not evidence of working multiple links or three-link coexistence. A read-only phone discovery endpoint is implemented and was read from macOS while ECM discovery ran; it does not implement authenticated companion control.

1. Independent central contexts are implemented. Validate controller and host connection counts for ESP-IDF 5.4.1, buffer pools, heap/stack and session cleanup on two powered adapters.
2. Bench scenario: two independently powered adapters/emulated radios, continuous ECM + TCM polling, phone connected/subscribed, LVGL rendering and local alerts. Record achieved per-source rates, P95 response latency, missed deadlines, reconnect behavior, radio parameters and minimum free memory.
3. Interrupt each adapter independently; verify the other continues, phone configuration remains available, and no cross-source responses appear. Test duplicate names and overlapping `7E8`/PID identities.
4. Run two hours under combined load with zero cross-source attribution and bounded queues/memory. Compare against single-adapter baseline. Raise phone telemetry interval before sacrificing warning freshness. Maintenance OTA may explicitly disconnect both adapters.
5. Report capability `maxAdapterLinks` and `simultaneousAdapterLinks` from actual implementation/validation. App must gate dual live pages and explain degraded operation on firmware that supports only one link.

## Fallback decision TODO: MULTI-002

If two adapter links are reliable but adding the phone is not, evaluate a deliberate configuration/maintenance mode that temporarily pauses one source while the phone is connected. Indicate that source's data/alerts are unavailable. Never silently sacrifice a source to admit a phone.

If only one adapter can be held reliably, investigate scheduled switching with explicit per-source slots, connection/ELM initialization overhead, a minimum useful dwell, poll priorities, and reconnect failure bounds. Test whether the adapters tolerate repeated sessions. Expect lower rates and gaps. Time slicing must be opt-in with an achievable freshness budget; it cannot be presented as simultaneous sampling. Active alerts retain source-lost status while disconnected, and users must see that new threshold crossings cannot be detected on the inactive source. If this cannot meet required freshness, use a second gauge or dedicated gateway rather than promise continuous protection.

Do not implement a fixed switching interval before measuring connection times. Preserve independent parser/session generations and discard late responses after switching. No cross-source derived values unless timestamp skew and validity meet the calculation's explicit bounds.

## Acceptance and schemas

Configuration has up to two source descriptors. Every PID definition requires `sourceId`. An alert references a unique PID definition that already includes source and responder; imports cannot omit this binding. Semantic validation rejects undefined sources and duplicate physical queries with conflicting identifiers. Prototype examples illustrate source labels only; they do not demonstrate actual adapter concurrency.
