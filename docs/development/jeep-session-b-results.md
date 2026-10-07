# Jeep engine Session B

## Confirmed setup

Owner confirmed the Jeep was idling, the identified Vgate was in the engine port, and other OBD apps were closed. Pixel and physical gauge were connected to the Mac. Public capability claims remain disabled.

## Initial dev34lab observations

Android found the previously identified Vgate, saved its engine binding in the Jeep profile, and sent configuration revision 25. USB boot logs confirmed that configuration healthy. The gauge successfully subscribed, wrote commands, and received adapter replies. This verifies the notification security-floor correction on the actual Vgate.

The first Mode 01 request emitted SEARCHING bytes. The gauge's short support-map timeout and subsequent short polling retries disconnected the link before that search could finish. Reconnect initialization interrupted the old request, producing STOPPED. The pre-fix capture replayed 100 completed transactions with zero accepted Mode 01 replies. No physical LCD reading was confirmed at this stage.

## Bounded search correction

Development firmware 0.2.0-dev.35lab gives the first support-map request 15 seconds for automatic protocol discovery. Subsequent support queries and ordinary polls retain their short budgets. The source remains in its preparation phase until initialization and this bounded discovery finish; a missing prompt fails initialization instead of immediately starting normal polling against an unfinished search. Initialization commands require an OK reply, so STOPPED alone is not treated as successful setup.

The image is delivered through Android/Wi-Fi, preserving the saved Jeep profile and binding. The first Wi-Fi attempt was interrupted when the phone left eGauge. Android Auto also requested the head-unit Wi-Fi connection during the retry. The foreground eGauge retry obtained Wi-Fi after 23,141 ms, connected its socket in 20 ms, and received the first gauge response 24,185 ms after preflight. Radio association and competing network requests are distinct from image-transfer throughput.

## Remaining observations

Post-update live decoding, visible LCD values, independent comparison, reboot persistence, and unplug/reconnect results are recorded separately as the session proceeds. Enhanced transmission definitions and simultaneous dual-adapter operation remain outside this engine-port test.

## Post-update observations so far

- Pixel: update recovery journal cleared after the App/Wi-Fi update, and Check adapter reports the actual selected Vgate ready.
- Gauge USB: dev35lab booted with the saved binding; the first Mode 01 support query completed in 5,474 ms. Engine ECU 7E8 replies are accepted, including varying RPM payloads around 670 to 690 RPM, coolant payload 7F (87 C), and speed 00. The first 285 accepted payloads had zero timeouts and no source_simulated event.
- Physical LCD: owner reported apparent live RPM. Exact visible values, coolant, swipe behavior, and dashboard comparison are still awaiting confirmation.
- Restart: opening the USB recorder caused a fresh physical gauge start; the Vgate binding was retained, initialized, and resumed live replies without resending setup.

Android Auto was temporarily force-stopped to prevent repeated foreground/network takeovers during the firmware update. Its stopped state is restored after the session.

Owner confirmed unplugging the Vgate from the engine port. USB recorded failed command delivery followed by a disconnect and a new connection generation. Owner confirmed the physical RPM number disappeared while the adapter was unplugged. Owner confirmed reconnecting the Vgate restored the physical RPM reading. USB traces resumed accepted ECU 7E8 replies on connection generation 8.

Installed dev35lab image SHA-256: `d61a81605a5c9cf9aa68859a879c4590edb9d4d9a87f3b944aebd9fb1aad1ff8`, 1,504,848 bytes. Lab and normal firmware builds pass; contract/document checks pass.

The engine vertical slice now has physical evidence for a visible RPM reading, saved binding across restart, unavailable display after unplugging, and recovery after reconnecting. Coolant/speed decoding is confirmed in USB logs; visible coolant values, swipe behavior, and dashboard comparison have not yet been confirmed. This does not qualify other adapter models, vehicles, enhanced transmission reads, or simultaneous links.

Final hardware recording replay: 1,499 completed transactions, with 1,462 accepted Mode 01 responses. The remaining completed transactions include adapter setup and other service categories; this count is not a packet-success rate. Raw identifiers and serial recordings remain in ignored local artifacts.

## Transmission-port follow-on, same vehicle session

Owner moved the same Vgate to the transmission diagnostic port with the gauge powered and confirmed swiping works. Gauge USB capture restarted dev35lab with the saved engine profile. The recording contains 331 completed transactions and no timeout events. The support-map response and standard RPM, coolant, and speed replies came from responder 7E9. All 326 firmware decoded events rejected the responses under the active engine route (status 4); owner separately confirmed the physical gauge showed dashes. This verifies that transmission-port replies are not displayed as live engine-profile values.

The standalone replay summary reports 320 accepted Mode 01 replies because it uses the parser without a specific ECU route. That is structural parser acceptance, not acceptance by the running engine profile or proof of transmission-specific measurement meaning. Sanitized responses now cover 0100, 010C, 0105, and 010D in the transmission fixture; the bench test verifies acceptance for 7E9 and rejection for 7E8. Replaying the fixture remains simulated evidence.

The owner identifies the transmission as ZFHP70. Its controller supplier/software and documented enhanced request definitions remain unknown. No new enhanced vehicle queries, configuration writes, or fault-clear requests were sent. Return-to-engine display comparison remains pending.
