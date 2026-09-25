# Offline development sprint, 2026-09-25

Window: 18:05-20:05 UTC. The Android phone is away and OBD adapters are unavailable. Work can continue in the repository and on the USB-connected gauge. Do not treat simulated responses or a BLE capability read as vehicle support evidence.

## Work sequence

| Checkpoint | Focus | Reviewable exit |
| --- | --- | --- |
| Start | Confirm both draft PRs and CI; preserve clean branch state | Record current green checks and any new failures |
| +30 min | Local vehicle profile quality | Verify migration behavior by code review; make profile selection and validation states clear; build Android |
| +60 min | Offline PID explorer and source evidence | Separate example catalog, user draft, and vehicle observation states; retain explicit ECM/TCM source; build Android |
| +90 min | Circular gauge typography | Check 240 px safe geometry across numeric, arc, bar, trend, dual, warning, and DTC concepts; adjust text bounds and document remaining physical review |
| +120 min | Integration and handoff | Update draft PRs with evidence and limits; confirm CI; summarize APK installation and physical gauge checks for the user's return |

## Boundaries and acceptance

- Keep all phone configuration writes, DTC clearing, and OTA disabled until the authenticated firmware operations exist.
- Keep physical gauge changes on firmware PR #1 and Android/prototype changes on Android PR #2.
- Build affected Android and firmware targets after changes. Preserve a clean checkout and record failed checks rather than implying success.
- The existing Pixel 10 Pro BLE capability read is verified. The updated Android profile UI is not installed on the phone yet.
- Inspect the physical display when the user returns, especially the bottom status, negative or five-digit values, long units, and daylight readability. A build and browser preview do not establish legibility on the LCD.
