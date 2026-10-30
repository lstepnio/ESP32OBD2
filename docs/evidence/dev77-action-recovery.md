# Dev.77 action-recovery delivery

> Historical evidence. [Current state](../current-state.md) and [backlog](../backlog.md) own current support and remaining work.

Recorded 2026-10-29. This corrects misleading action presentation after the owner
reported a persistent Burnout “Action in progress / Hold brake pedal” screen.
The owner reported normal Jeep/brake behavior after restarting; no Jeep was
connected during this delivery.

## Source and verification

PR [#126](https://github.com/lstepnio/ESP32OBD2/pull/126) remains unmerged.
Source `5898c80836a0a285faed2c218e843c88e41c2fb0` passed all four quality lanes
([run 40947556163](https://github.com/lstepnio/ESP32OBD2/actions/runs/40947556163)).
Focused production-LVGL normal/fallback, firmware fixtures and sanitizer checks
cover actual-blocker navigation, independent recovery and the brake/idle flag
collision. A supervised brake Start now needs one hold; flag 16 cannot open the
idle coolant confirmation or replace **Release to stop**.

Signed [dev.77](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.77)
was published by [run 40949642402](https://github.com/lstepnio/ESP32OBD2/actions/runs/40949642402),
catalog generation 58, from the same source. All five assets passed catalog/image
signatures, freshness/compatibility, build-evidence signature/inventory and image
parity checks. Parked-idle, owner-brake and orientation candidate flags remain
selected; public qualification is unchanged.

- Image length: 1,695,392 bytes.
- Image SHA-256: `85b51fcd044632fa3ea80a885e5edf329a11290b9b51c7ac19b5c67055d083cd`.
- ELF: `0850b6c1eeb8a8062d7f426e55a0f2657d875fbf9d551f8132c790415c4f5769`.

## Recovery evidence and limits

Protected dev.76 readback identified ABS/TCS **Unknown / Context**, operation 2,
restart required; Idle and Burnout had operation 0. This is retained ABS reset
confirmation, not proof of active Burnout. Dev.77 names that blocker and provides
**Review**, without dispatching a command or clearing the record. With no matched
ECM, recovery says **Connect Jeep**; the existing stationary confirmation remains
required. The [contract](../architecture/vehicle-actions.md#reviewing-a-different-actions-retained-state)
and [parked procedure](../development/jeep-action-qualification.md#brake-actions-follow-up)
own the behavior. Actual front-lock behavior, release/reset effects and physical
recovery remain separate vehicle qualification.

The owner authorized USB if quicker. A high-speed read failed with a serial packet
error; the lower-speed full backup was stopped because it was slower than Wi-Fi.
Only loader reads/resets occurred. No USB flash write or erase was performed.
The ordinary authenticated App/Wi-Fi path was selected instead.

The first Wi-Fi attempt ended with **Socket closed** while sending; the serial
reader then captured a power-on boot of dev.76. The source of that reset is not
established. No panic or allocation-failure evidence was captured for it. A retry
with the serial reader already attached reached the normal software OTA restart
into dev.77. Preserve this failed attempt separately from the successful delivery.

## Installed and preserved

The retry completed the ordinary signed App/Wi-Fi upload in 73.944 seconds, with
sending observed at 16.348 seconds. Exact post-boot ELF, alternate OTA slot,
health 2 and cleared update journal passed. Two maintenance opens/closes passed
(1,561 and 1,868 ms to protected open), with authenticated reads and unchanged boot.
Automatic foreground connection/resume and the hardware read chain passed.

Protected post-install readback retained configuration revision 66 and SHA-256
`807623e49e2995cb50bad065979ed974b627503f4314bdff3b16db971697b153`, display
settings and profiles. ABS/TCS remains Unknown, operation 2, restart required;
Idle and Burnout remain inactive, operation 0. Both brake kinds retain owner-local
Start context and are unqualified indoors. No Start, Stop, reset confirmation or
ECU-clear request was issued. App dev.54/code54 is unchanged and already installed.
Phone notifications and Android timeout/charging-wake policy were restored.

The short captured Wi-Fi window reported zero allocation failures, with minimum
internal free 86,555 bytes and largest block 31,744 bytes. This does not establish
loaded endurance or explain the initial power-on reset. Production-LVGL regression
captures are separate from physical touch/Jeep behavior; no new owner panel or
vehicle observation was collected after this installation. Website impact is a
corrective UI update, with no new public qualification claim or publication needed.
