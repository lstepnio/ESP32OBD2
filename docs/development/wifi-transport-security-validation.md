# Wi-Fi transport security validation, 2026-09-26

> Reference record: implementation/evidence from its recorded session. Current status and next work are maintained in [current state](../current-state.md) and [roadmap](../roadmap.md). Do not treat old pending steps or tool instructions as the current plan.

This check used the paired Pixel 10 Pro and the Waveshare gauge running confirmed `0.2.0-dev.14`. The phone first opened the temporary Wi-Fi maintenance network through the owner-authenticated BLE control service. The diagnostic did not issue a configuration or firmware mutation command and did not display or log the session key, network password, or complete session descriptor.

Within one short-lived session, Android performed these probes:

1. Sent a structurally valid frame carrying a different session ID. The gauge closed the connection without a response.
2. Sent the correct session ID and sequence with an AES-256-GCM frame produced from a deliberately modified key. The gauge closed the connection without a response.
3. Sent a valid encrypted status request at sequence one and authenticated the valid response. It then replayed the exact accepted request on a new connection. The gauge closed the replay connection without a response.

The app reported `Passed: wrong session, wrong key, and replay were rejected`. It closed the maintenance network and zeroed the in-memory session key. A subsequent protected BLE configuration read succeeded and returned running and stored revision 2 with SHA-256 `6689a89228758d4bbe43cb772ed4902db447b1f68a3980ab1502b0d11c0bfba9`. This follow-up distinguishes intentional frame rejection from a dead transport or gauge failure.

The host sanitizer fixture separately covers an unavailable session, a zero session ID, mismatched sessions, duplicate and decreasing sequences, expiration, and tick wrap. Live expiration timing and active network attack testing remain outside this check.
