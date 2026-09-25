# PID discovery and definition model, draft 0.1

The app makes discovery approachable while retaining evidence for advanced users. The gauge performs requests using the same serialized engine as live polling. “Discover” never implies every manufacturer identifier can be enumerated.

## Standard discovery

1. Select adapter and confirm its GATT capabilities and ELM handshake. An advertised name or `ATI` banner alone is not proof of compatibility. Record tested profile, protocol, prompt/echo behavior and observed transaction latency.
2. Request Mode 01 PID 00 support bitmap. Keep headers/ECU routing information and separate each responder. Decode the four bitmap bytes in network bit order: bit 31 corresponds to base+1, bit 0 to base+32, which also signals the next supported range. Follow 20/40/60 etc only when advertised and within implemented catalog bounds.
3. Store support evidence per ECU, mode, PID and vehicle session. Do not OR all ECUs and then assume any ECU can answer a PID.
4. Query enabled/suggested data with bounded pacing. A valid matching response establishes “responding”; a bitmap alone establishes only “advertised support.” Store raw sample, decoded result, age, response time, decoder revision and ECU.
5. On cancel, preserve partial evidence and stop before the next request. After disconnect, resume only against the same selected vehicle/ECU context; never carry support claims to a different vehicle without user confirmation.

[ELM327 datasheet](https://www.elmelectronics.com/wp-content/uploads/2016/07/ELM327DS.pdf) describes supported-PID requests and the command/response interface. Our per-ECU evidence model, scheduling and UI below are project design choices.

## Manufacturer and custom discovery

Provide three tabs: **Standard**, **Vehicle profiles**, **Custom lab**. Manufacturer Mode 22 identifiers lack a universal portable support bitmap. Curated packs provide exact make/model/year/engine/transmission/ECU scope, documented reads and example responses. Preview requests before activation. VIN may suggest a profile but does not prove ECU/transmission compatibility.

No unbounded address/identifier brute-force scan. A scoped “check profile” job probes only reviewed read definitions at the discovery rate, supports pause/cancel and records no-response without inferring absence. Definitions needing diagnostic-session changes or security access remain unsupported until a protocol-specific implementation is reviewed.

Imports can support an explicit mapped subset of Torque CSV and other documented formats later. Show a conversion report for fields/operators that cannot be represented. An unsupported formula cannot silently become zero or a guessed decoder. Catalog provenance and license are mandatory; candidate research includes [OBDb](https://github.com/OBDb), with each repository/definition checked before adoption.

## Evidence states

| State | Meaning | UI action |
| --- | --- | --- |
| unknown | No conclusive observation | Discover / inspect |
| advertised | ECU support bitmap says yes | Request sample |
| responding | Matching valid response observed | Add to dashboard |
| no_response | Timeout or ELM NO DATA | Retry, check vehicle state |
| unsupported | Valid support bitmap excludes PID, or a specific documented negative response | Explain source; allow profile review |
| decode_error | Bytes arrived but length/sign/unit/range validation failed | Open raw sample and definition |
| unavailable | Previously working value cannot currently be sampled | Show age/status; reconnect |

Report partial scans as “12 responding, 4 advertised, 2 no response,” with per-ECU filters, not an invented global completion percentage. Progress denominator is the scheduled query set, and it can expand as support ranges are discovered.

## Definition contract

See [schema](../../contracts/pid-definition.schema.json) and [RPM example](../../contracts/examples/pid-rpm.json). Fields include stable ID/version, request service/identifier, addressing route, ECU responder selection, expected positive-response prefix, data length and decoder, base unit, plausible range, requested interval, and source/evidence metadata.

v1 decoder extracts a contiguous 1-4 byte integer at an explicit byte offset from the payload **after** service/identifier prefix, big/little endian, optional two's complement, rational scale (`numerator/denominator`) and offset. Use 64-bit intermediates, check overflow/nonfinite outputs, zero divisor, length, and plausible range. Example RPM payload `1A F8` -> 6904 / 4 = 1726 rpm. Coolant byte `7B` -> 123 - 40 = 83 °C. A user-facing formula editor can compile supported arithmetic into this representation, but neither device nor app executes arbitrary expression strings.

Custom service 22 uses a two-byte identifier and positive prefix `62` plus that identifier. Standard service 01 uses a one-byte PID and prefix `41` plus PID. A definition cannot substitute service 04, raw AT text, CR/LF or arbitrary CAN commands. Transport addressing is a typed protocol/route, not a user-provided serial string. CAN 11/29-bit and legacy header formats need separate validated adapters; v1 editable physical routes cover CAN 11-bit only. Unsupported routes fail explicitly.

## Response handling and sampling

Keep complete ELM transactions through the prompt; support split/coalesced BLE notifications, echoes, SEARCHING, NO DATA, STOPPED, CAN ERROR, BUFFER FULL, malformed hex and multiple ECU lines. Prefer ELM-managed ISO-TP assembly where verified; handle header/length variations with recorded fixtures. Never mix a service 01 response with service 22 or infer ECU identity after dropping headers. Negative responses get typed reason and bounded retry policy; response-pending receives a bounded extended timeout.

Publish achieved sample interval and stale timeout separately. Standard start point is stale at max(3 × requested interval, 2 seconds); adjust by profile and measured capacity. Alerts evaluate only fresh, valid values. Discovery must not starve active warning channels. Show requested vs achievable rates in the editor and offer to reduce low-priority channels.
