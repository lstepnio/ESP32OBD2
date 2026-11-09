# App subscriptions

The eGauge app is free. Existing physical-gauge setup, firmware updates and
vehicle configuration are available without a subscription. Virtual Gauge is the
first optional paid feature. Hardware orders and their included firmware major
version are handled separately through [Stripe hardware checkout](../../website/README.md#checkout-and-fulfillment).

## Android

Google Play Billing owns product eligibility, checkout, renewal, cancellation and
restore. The first subscription product ID is `egauge_virtual_gauge`. Before
launch, create a recurring base plan and an eligible free 30-day introductory
offer in Play Console. The app reads the eligible offer phases and localized
price from Play. It shows a trial only when Play returns one. Payment confirmation
uses Google's purchase sheet, with subscription management in Google Play.

The Android build pins the official Play Billing SDK. Set
`egaugePlayBillingPublicKey` to the app's base64 Play purchase-verification
public key for the internal release build. Leave it unset for unprovisioned
builds, which show that subscriptions are coming and offer no purchase action.
Debug builds omit the payment client and keep Virtual Gauge open for vehicle
testing.

One Android gate owns store access for each premium destination. It checks
current subscriptions on entry and resume, validates the signed purchase against
the app package and product, and acknowledges a verified completed purchase.
Pending or unverified purchases confer no access. A failed ownership query
retains an already open session so a temporary connection loss does not interrupt
the display. A successful ownership query can remove access, and re-entry
rechecks. There is no device-specific license file, entitlement cache on disk
or website payment fallback.

Qualify the purchase, cancellation, acknowledgement retry, pending completion,
expiry, renewal and restore with Play license testers before launch. Verify
Play's cached ownership behavior with the phone offline and decide on a secure
server-side verification path if the client cannot provide the required
revocation and expiry evidence. Keep product pricing and console publication
separate from building the app. [Play Billing integration](https://developer.android.com/google/play/billing/integrate)
and [subscription lifecycle](https://developer.android.com/google/play/billing/lifecycle/subscriptions).

## iOS

The future iOS app uses StoreKit for the same premium-feature boundary.
Set up an auto-renewable subscription group and use Apple's **one-month**
introductory trial for eligible customers. A calendar month can be 28 to 31
days, so label it as a month. Use verified StoreKit entitlements and
transaction updates, with a user-initiated restore and App Store management.
Qualify purchase and restore in Apple's sandbox before release. There is
no iOS target in this repository today.
[App Store subscriptions](https://developer.apple.com/app-store/subscriptions/)
and [trial duration options](https://developer.apple.com/help/app-store-connect/reference/pricing-and-availability/in-app-purchase-and-subscriptions-pricing-and-availability/).
