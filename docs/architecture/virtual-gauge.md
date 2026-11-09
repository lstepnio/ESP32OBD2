# Android virtual gauge

Open **Virtual gauge** from the Gauge tab or from initial gauge setup. Choose the
adapter plugged into the car's main OBD-II port. Turn on the ignition and release
the adapter from any other app or physical gauge before connecting. The phone is
the display. A BLE OBD-II adapter is still required.

The active vehicle's saved pages drive the swipeable dashboard. **Customize
pages** edits that profile using the existing local setup transaction. Physical
gauge assignments and transfers are separate. The virtual screen remembers its
adapter per vehicle and its units on the phone. The initial units follow the
phone's measurement preference. Each visit reconnects to the saved adapter and
keeps the screen awake while the dashboard is visible.

## Ownership and limits

- Android scans for the existing `elm-18f0-v1` service profile (18F0, write 2AF1,
  notify 2AF0). Selection is explicit, even with only one candidate. Discovery
  alone does not prove compatibility. It must find the expected characteristics,
  subscribe to notifications and receive valid interpreter responses.
- The first implementation uses one adapter and 11-bit, 500 kbps OBD CAN, with
  the primary ECU replying at 7E8. A direct session has a single in-flight
  request, bounded replies and timeouts, disconnect cleanup, and bounded retry.
  It releases the adapter when leaving the screen or app foreground.
- The supported Mode 01 readings are RPM, speed, coolant, intake, ambient and oil
  temperatures, load, throttle, fuel level, manifold pressure, air flow and
  module voltage. Vehicle support varies. Other readings on existing pages stay
  unavailable. The dashboard does not request vehicle actions, clear codes, run
  fault or threshold notifications, or connect to a separate TCM.
- Visible RPM and speed target 500 ms between polls when the link can keep up.
  Older due readings still get service. Hidden page readings warm the cache
  and unsupported readings retry more slowly. The renderer uses the latest
  measured sample and a bounded session-local trend, with 5- or 10-second
  expiry by reading class. Unavailable values show dashes. Connection loss
  retires all samples, and dual faces expire their readings independently.
- Existing hardware previews keep their example treatment. The live dashboard
  shares face geometry and unit conversion, and uses its own connection pill.
  There are no firmware or wire protocol changes.

The [ELM327 data sheet](https://www.elmelectronics.com/wp-content/uploads/2017/01/ELM327DS.pdf)
describes interpreter commands and header-enabled CAN output.
[Android BluetoothGatt](https://developer.android.com/reference/android/bluetooth/BluetoothGatt)
defines the write and subscription callbacks used by the transport.

## Qualification

JVM fixtures cover fragmented replies, wrong source/PID, malformed or conflicting
frames, polling fairness, and independent stale and unit handling. Managed UI
checks cover entry, the local editor and rendered compact/200% text faces. All
three focused UI tests passed and both captures were reviewed. Bench
acceptance still requires the owner's supported Vgate on the ECM port: select the
correct unit with both adapters nearby, compare RPM/speed/coolant with an
independent reference, observe stale/return after ignition or adapter loss, and
check that leaving the screen frees the adapter for another client. Try another
stock vehicle before expanding the support claim.
