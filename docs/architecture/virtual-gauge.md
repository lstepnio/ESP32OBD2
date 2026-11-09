# Android Virtual Gauge

Open **Virtual gauge** from the Gauge tab or initial setup. Tap **Adapters**, the connection
pill or **Set up adapters**, then select **Main adapter**. Turn on the ignition
and release the adapter from any other app or physical gauge before connecting.

In development builds, if the vehicle has a separate transmission diagnostic port, open **Adapter
setup** and choose **Add transmission adapter**. This option appears after a
main adapter is selected. Match the adapter suffix to the physical port. The
same adapter cannot fill both slots. The optional child has its own status and
can be removed without changing the vehicle's saved pages.

The swipeable dashboard uses the active vehicle's saved pages. **Customize
pages** edits that profile through the existing local setup transaction.
Measurement units and direct adapter selections stay on the phone. The previous
single-adapter binding is the main slot. Physical gauge assignments and
transfers are separate.

## Read-only transport and ownership

- Scan for `elm-18f0-v1` (18F0 service, 2AF1 write, 2AF0 notify), select
  explicitly, validate the characteristics and subscribe. A candidate is not
  a confirmed vehicle source.
- One foreground owner runs up to two independent GATT sessions. Each session
  has one in-flight request, bounded framing, cleanup and backoff. A lost
  source drops its own samples. The other source stays live. Replacing a
  binding restarts the dashboard's sessions and retires their samples.
- Main reads use 11-bit/500 kbps CAN, Mode 01 and responder 7E8. Supported
  readings include RPM, speed, coolant, intake, ambient and oil temperatures,
  load, throttle, fuel, manifold pressure, air flow and module voltage.
- The separate transmission profile is specific to the captured JSS swap.
  It uses physical request 7E1, responder 7E9, and the captured calibration
  and CVN as a gate for the two enhanced reads. Keep this slot in development
  builds until the dual-adapter Jeep checks pass. A reconnect or prolonged
  loss rechecks identity. Temperature and current gear follow the existing
  [JSS definitions](../development/tcm-values-research-plan.md).
- Visible RPM and speed target a 500 ms interval when the link can keep up.
  Older due readings still get service. Hidden pages warm the cache, and
  unsupported readings retry more slowly. The round face uses measured
  values, independently expired after 5 or 10 seconds. This mode performs
  no vehicle actions, clearing, fault checks or threshold notifications.

There are no firmware or App-to-gauge protocol changes. The
[ELM327 data sheet](https://www.elmelectronics.com/wp-content/uploads/2017/01/ELM327DS.pdf)
describes header-enabled CAN output and interpreter commands.
[Android BluetoothGatt](https://developer.android.com/reference/android/bluetooth/BluetoothGatt)
defines the write and subscription callbacks used by the transport.

## Subscription access

Virtual Gauge is an optional subscription feature. The free app's hardware
functions remain available independently. See [App subscriptions](app-subscriptions.md)
for Play Billing, future StoreKit support, and the introductory trial.

## Qualification

Run the virtual gauge JVM and managed UI fixtures. They cover fragmented
responses, controller identity, source loss, duplicate binding rejection,
and compact/200% dashboard states. The owner reported the
single-adapter dashboard worked on the Jeep. The revised two-adapter path
needs parked testing with both Vgates: select the correct ports, compare
RPM/gear/temperature, unplug each adapter separately, verify the other
source remains live, then confirm reconnect and session release on exit.
A second stock vehicle is needed before broadening compatibility claims.
