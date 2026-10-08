# Vehicle capture preparation evidence

> Historical evidence. Versions, measurements and unfinished steps below describe that session.
> Current support is in [current state](../current-state.md); remaining work is in
> [the backlog](../backlog.md). These notes are not standing implementation instructions.

## Preparation status, 2026-10-06

The Mac capture environment, replay checks, capture firmware build, normal firmware build, and Android build/unit tests pass. The Pixel has the development app with signed-package import. Firmware `0.2.0-dev.33cap` was sent through the Pixel app over Wi-Fi. The gauge boot log confirms its trial image healthy, and USB trace capture/replay passes with `trace_start` and adapter connection failures on this bench. With no powered adapter here, there are zero complete OBD transactions; this is recording-path evidence only. The owner confirmed a normal physical reading page and working swipes after installation.

The first Wi-Fi attempts could not associate. A later attempt joined after retry, with 34.4 seconds to the first response and 2.85 seconds for flash preparation. One attempt was interrupted when the phone left eGauge. Keep eGauge open during installation. After activation, the Pixel reported protected-read error 2 from its cached earlier service layout. A targeted development cache refresh followed by rediscovery restored authenticated access without removing the bond. The Pixel now reports Ready, AUTHENTICATED, zero retries, and an up-to-date gauge. Stored and running configuration revision 21 and its hash match the pre-update state; boot restored 100% brightness. A durable production GATT service-change migration remains a follow-up, rather than depending on this development diagnostic. See the [first vehicle capture results](../evidence/first-jeep-capture-results.md) for subsequent live evidence. Enhanced transmission definitions and simultaneous two-adapter use remain unverified.

