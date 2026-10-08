# Multi-adapter ECM/TCM support

## Implemented development boundary

One vehicle has one primary ECM connection and an optional TCM child configured in
Expert. A normal single adapter carries all configured requests; enabling a child
routes transmission definitions to its independent worker. Editors, previews, pages,
alerts and send review always use the same whole vehicle dashboard.

Firmware has independent connection contexts, transaction/parser state, sessions,
retry schedules, polling and diagnostic snapshots. Android projects both bindings
into one schema-2 configuration revision and aggregates required connections in the
universal status widget. A parent relationship never requires the ECM radio to be
connected before the child can recover. See [vehicle routing and migration](vehicle-connections.md).

Mixed-controller Dual pages and catalog TCM alerts are supported in the development
path. Disabling/removing a child retains pages, alerts and gestures; unavailable
readings do not become zero or disappear from the editor. Alert identity remains
bound to its definition and responder after routing changes.

## Identity and independent recovery

Keys include vehicle, source, ECU, service, identifier and definition revision.
Two adapters can both report `7E8`; responder address alone is not vehicle-wide
identity. Explicit physical bindings must be distinct. Do not infer identity from
matching advertised names or copy bond credentials into profile exports.

Each adapter has one outstanding ELM transaction and its own connection generation.
Discard late replies from retired sessions. Losing either source invalidates only
its samples/freshness; healthy-source polling and touch continue. Pause both adapters
explicitly during maintenance rather than silently starving one. Source-specific
protected status reads preserve these identities even though ordinary UX shows one
logical connection.

## Qualification and fallback

The configured three BLE connections (two central links and one phone peripheral
link) are software capacity, not physical qualification. Public qualified link
capacity remains one. Current evidence/device availability is in [current state](../current-state.md);
remaining hardware work is **QUAL-04** and **QUAL-08** in the [backlog](../backlog.md).
Use the [dual recovery matrix](../development/dual-adapter-recovery.md) and
[quality gates](../development/quality.md) for rates, memory, sibling interruption,
identity overlap and soak acceptance.

**Proposed, not implemented:** if measured three-link coexistence cannot meet the
budget, evaluate explicit maintenance-only source pauses or opt-in time slicing.
Measure reconnect overhead and achievable freshness first. Time slicing cannot be
presented as simultaneous sampling. Never silently sacrifice a required source to
admit the phone; cross-source derived values need explicit timestamp-skew bounds.
A second gauge/gateway is an alternative if a measured fallback cannot meet freshness.

## Contracts

[Adapter bindings](../protocol/adapter-bindings-v1.md) owns wire identities and
capability negotiation. Imports reject undefined sources and duplicate/conflicting
physical queries. Schema examples and simulator radios do not qualify vehicle
compatibility or concurrency.
