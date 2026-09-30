# Arc visual refresh validation

Recorded 2026-09-29 for the Waveshare ESP32-S3-Touch-LCD-1.28.

## Change and source-level bounds

Firmware `0.2.0-dev.29` changes the arc from 198 px diameter and 7 px stroke to 218 px diameter and 11 px stroke, with rounded ends retained. Its 109 px outer radius stays within the physically calibrated 112 px decorative band. Essential reading and alert text retain the 104 px limit. Track, normal, warning, and critical colors are now `#29343A`, `#00D6A0`, `#FFB000`, and `#FF4D5A`. The background and text colors are unchanged.

## Build and release evidence

- Local ESP-IDF 5.4.1 build passed; the image fits the 3 MiB OTA slot.
- PR #49 quality CI passed contracts and docs, Android build and tests, and firmware build for commit `ac2b6c48c27cfcd90c57f83f7e3549f82cd0cbee`.
- The protected workflow published signed prerelease `dev-v0.2.0-dev.29`, catalog generation 19, targeting that commit.
- Independently downloaded release assets passed catalog and image signature verification against the pinned development public key. The bundle hash is `6dc27cb9bd351ee680cf935b2fa3d5b30133fbc7167c6647992e2c89f62617a4`; the image hash is `3eb6407269237f914da8f98133d5e9033ae8d8da1f2d678fc8b023c383cd5c96`. Bundle size, board, partition layout, ZIP members, and embedded app version matched the signed catalog and metadata.

## Pixel and gauge protocol evidence

The paired Pixel 10 Pro downloaded the hosted update and sent it through the app's Wi-Fi path. The app showed transfer progress, waited for restart, and reported that the gauge confirmed the update. Expert read back installed version `0.2.0-dev.29`. Device data reported retained stored and running configuration revision 20 and operation response `The new image is healthy and running`. Settings read back 100% brightness and 90° rotation.

## Physical display review

The enlarged arc and stronger palette still need a direct visual check on the physical LCD, including clearance at the user's viewing angle. The normal arc can be judged on a live page. Warning and critical colors require their respective alert states and have not been visually qualified by the app or source-level checks above.
