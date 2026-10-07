# Adapter and vehicle expansion

Updated 2026-10-06. Initial sequential Jeep captures and single-source Engine/
Transmission slices are implemented. Read [current state](../current-state.md)
for evidence and [TCM resume](tcm-session-resume.md) for the next parked session.

## Next expansion

1. Qualify full TCM faults and advertised readings on the existing one-adapter setup.
2. Obtain a second physically identified adapter and measure simultaneous ECM, TCM
   and phone links on this board. Record polling age, radio contention, disconnect
   recovery, heap/stack limits and update preemption.
3. Implement a measured fallback if concurrency cannot satisfy freshness needs.
   Preserve source attribution and explain unavailable sources in the consumer UI.
4. Add other adapter profiles and vehicles only with captured GATT/protocol identity,
   decoder vectors and field evidence. Catalog entries alone are not compatibility.

## Implementation rules

Reuse existing profile/schema-2 adapter bindings, transaction workers and freshness
model. The normal runtime selects one source; legacy second-slot scaffolding is not
a supported concurrent path. Never fabricate a live adapter or shared ECU identity.
Public capacity stays one until the complete second-adapter path is qualified.

Keep recordings private and replay them offline. Do not require a vehicle connection
for decoder, protocol or App state development. Vehicle writes/clearing need their
own explicit protected implementation and user authorization.
