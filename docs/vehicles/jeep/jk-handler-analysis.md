# Wrangler JK action-handler inventory

Status: static analysis of the managed diagnostic handlers, recorded on 2026-09-29. The complete [method inventory](../../../data/vehicle-definitions/vehicles/jeep/wrangler-jk-handler-inventory.json) preserves method names, unique called methods, string literals, and instruction counts for five relevant action classes. Its assembly SHA-256 matches the source-package input in the [archive manifest](../../../data/vehicle-definitions/manifest.json). These are code observations, not vehicle observations or complete wire procedures.

| Candidate action path | Static findings | Validation still required |
| --- | --- | --- |
| Petrol high idle | The JK adaptation selects `set_rpm_pb_kwp` at `7E0`/`7E8`. Its handler contains `31 05 {0} {1}` and `33 05` literals. It starts a recurring work task; navigation away calls `StopAdaptation`. | Determine the exact byte encoding, response checks, refresh interval, stop traffic, and physical return to normal idle for the target PCM. |
| 2.8 L diesel high idle | A separate beta handler uses the same `7E0`/`7E8` route. Its literals include `30 10 07 {0} {1}` and a recurring `30 10 01`. | Confirm market and ECU applicability, value encoding, stop behavior, and physical response. |
| ABS/ESC shutoff through the front controller | The shared settings file referenced by JK includes an `ABS + ESC Kill` entry addressed to `620`/`504`. A matching candidate handler has disable and enable options and contains `31 A4` and `32 A4` literals. | Confirm that this handler is the one selected for the target JK, its preconditions, reply interpretation, persistence, and restoration. |
| Another ABS/traction-control handler | A distinct handler uses `747`/`4C7` and offers one-key-cycle, five-key-cycle, and restore choices. Its literals include `31 01 30 46 01`, `31 01 30 46 05`, and `31 02 30 46 00`. | Establish vehicle applicability. This path must not be assumed to apply to the JK merely because it exists in the package. |
| Z test routine | The JK adaptation entry is inactive. A handler exists and describes routine steps. | No claim of JK support or safe operation follows from the inactive entry. |

The strings above identify command fragments in executable methods. Branch conditions, adapter setup, diagnostic session, security access, timing, error handling, and readback can change the actual sequence. No action is enabled in eGauge. The [JK research notes](wrangler-jk.md) define the physical validation needed before implementation.

## Primary vendor cross-check, 2026-10-07

The [JScan FAQ](https://jscan.net/faq/) describes raised idle as a temporary
adaptation that returns to normal when the app disconnects or leaves the adaptation
screen. This supports modelling high idle as an active operation with Stop and
verified refresh/loss-of-contact behavior, rather than a persistent RPM preference.
The FAQ does not provide encoding, refresh timing, a stop packet or a compatibility
identity for this Jeep's swapped PCM. It does not establish eGauge support.

The [official JK feature page](https://jscan.net/jeep-wrangler-jk/) lists module
identification (VIN, part number and version) and vehicle-specific actuator testing.
Its general JK coverage is not evidence that the swapped Hemi PCM matches the
petrol handler above, nor a complete ABS/ESC disable/restore procedure. Next controller
qualification needs exact identities and complete command/result/restore definitions;
retain these as research candidates until that evidence is available.
