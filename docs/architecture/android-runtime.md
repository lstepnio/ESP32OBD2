# Android companion runtime

Updated 2026-10-06. This describes current ownership; evidence limits are in [current state](../current-state.md).

## Responsibilities and boundaries

Gauge, Car and Settings are primary destinations; Settings reveals optional Expert.
Customize uses focused reading/layout sheets and exact review/send. Setup handles
association. Settings owns brightness, orientation, units, page cycling and updates.
Car owns source-specific fault views and adapter/profile selection. Technical reports,
definition examples and maintenance details remain in Expert. Preview readings never
become live vehicle observations.

`AppViewModel` owns operation lifetime. `MainActivity` owns Android permission prompts, the system file picker, and window flags. Composables only dispatch intent and render state. `OperationCoordinator` grants one process-local BLE operation lease, so discovery, protected reads, configuration, and firmware upload cannot overlap. Every terminal operation reports Active, Recovered, Failed, or Outcome unknown instead of relying on one generic progress string.

Public discovery and owner access are separate states:

```mermaid
stateDiagram-v2
    [*] --> Unknown
    Unknown --> Discovered: public capability read
    Discovered --> Authenticated: protected read or confirmed write
    Authenticated --> Unknown: target or permission failure
```

Discovery gathers all matching advertisers for a short bounded window. A single result is selected automatically. Multiple results require a user choice. The chosen BLE identity is remembered locally. RSSI orders the list but never proves identity. Android owns the bond secret; the app stores no passkey or bond key.

## Configuration transaction

`ConfigurationProjector` is the single projection from the local draft to the supported firmware document. It validates supported page/rendering combinations. A bounded Transmission profile
uses the captured temperature/current-gear definitions and one source-specific adapter. The review and wire payload use the same projection. The transaction captures the profile, draft, base revision, and base hash before sending.

```mermaid
stateDiagram-v2
    [*] --> Preparing
    Preparing --> Sending
    Sending --> Verifying
    Verifying --> Saved
    Saved --> Restarting
    Restarting --> CheckingRunning
    CheckingRunning --> Active: expected revision and hash, trial clear
    CheckingRunning --> Recovered: previous generation active
    CheckingRunning --> OutcomeUnknown: identity unavailable
```

Stored revision and hash alone do not establish success. `GaugeConfigTransferClient` waits for runtime identity, rejects mismatches, keeps a trial in Waiting, and exposes firmware recovery. Cancellation closes the GATT connection and does not convert cancellation into a retry failure.

## Evidence lifetime

Device-specific caches are cleared when discovery fails or a different target is selected. Owner state becomes authenticated only after a protected operation succeeds. Diagnostic freshness is derived from a monotonic receipt timestamp and expires after 30 seconds. Vehicle PID examples never become vehicle observations. An observation model carries profile, session, adapter, ECU, source, PID, and observation time.

## Storage and recovery

Profile documents have an explicit schema version. Version 1 profiles migrate to version 2. A legacy TCM draft enables the advanced second-adapter preference without changing its source. Unknown newer schemas and unsupported enum values fail closed and preserve the stored source document. Saves use synchronous commit and report failure.

The selected gauge identity and the minimal firmware-update recovery journal use separate private preference files. An interrupted update records target identity, image digest, stage, and time. On relaunch, the app asks for a protected running-firmware read before retry. A successful identity read reconciles and clears the journal. BLE upload remains an explicitly foreground workflow; the debug build keeps the phone awake while visible.

## Protocol and transport rules

Pure codecs validate capability JSON, diagnostics, and runtime identity before UI state changes. Invalid versions, lengths, reserved bits, and ranges are errors. Transfer callbacks enter a bounded channel. The process-level operation coordinator serializes sessions, and each client connection owns its callback queue and deadline. A callback cannot complete another connection's deferred result.

Production support still requires a shared reusable GATT request layer if the protocol surface grows. The current bounded and serialized behavior is the compatibility-preserving foundation. `WifiBulkClient` implements the authenticated private Wi-Fi update path using the
same protected maintenance state. Configuration remains on the bounded BLE path.

## Feature evidence boundaries

- Numeric ECM configuration, protected status, display rotation, and the development update protocol have prior phone and gauge evidence.
- The GitHub catalog path is development trust only until a published release is exercised from a fresh app install.
- Live Engine RPM and combined Transmission Gear + Temperature have recorded owner observations.
- Full source-scoped fault snapshots are implemented and offline-tested; new physical checks are pending.
- DTC clearing, dual-adapter concurrency and production OTA remain unavailable or unqualified.
- Enabling the second-adapter preference changes a local profile only. It does not claim simultaneous firmware support.

