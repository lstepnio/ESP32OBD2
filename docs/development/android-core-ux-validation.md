# Android companion redesign validation

## Customize workspace review, 2026-09-28

The [review and before/after images](../design/redesign/customize-review.md) cover the replacement of the nested page editor with one preview-led workspace, focused pickers, compact page management and a locally saved alert form.

| Check | Result and boundary |
| --- | --- |
| Required Android build, unit tests, Android-test compilation and lint | Passed; 42 app unit tests, including seven form/range/legacy-profile checks |
| `tools/validate.py` | Passed: schema examples, rejection cases, signed vector, document links and design-token parity |
| USB Pixel 10 Pro, Android instrumented suite | 15 passed; two opt-in bench tests skipped. Includes nine stateful Customize examples, two read-only activity journeys, three accessibility checks and the image comparison |
| Shared screenshots | All 72 baselines matched after visual review: 16 component/theme examples and 56 screen/theme/width examples. Compact 390 dp and expanded 1000 dp, light and dark |
| Physical Pixel font scale 2.0 | All 11 Customize and activity journeys passed. Fields scroll into reach; the fixed action stays above the keyboard. Original font scale restored |
| TalkBack | Service bound and touch exploration enabled; focus outlines observed on Back and the sheet handle, and the reading picker operated. Original accessibility services restored. Spoken output and touch-exploration accuracy were not independently qualified |
| Real gauge/vehicle | No gauge configuration write, firmware update, live OBD request or DTC clear was run for this review. No new gauge-side behavior is claimed |

Physical phone captures are under `docs/design/redesign/customize-review/physical/`. `pixel` and `font200` editing images use visibly labelled in-memory examples; the activity captures show the installed app with Preview values and automatic connection disabled. The TalkBack check briefly selected Vehicle speed on the first page and restored its original Engine speed reading; no Send action was used. The pre-existing bottom-navigation Settings label wraps at 200% text, outside the Customize content; its destination remains reachable.

The screenshot suite never overwrites baselines during comparison. `PrimaryJourneyTest` now asserts that viewing Customize leaves the complete saved configuration unchanged. All editing mutations use `CustomizeJourneyTest`'s in-memory state, preventing the previous test from deleting a saved user page. The physical bench journey was adapted to the new controls but intentionally not run.

Older evidence below predates this scoped review and retains its original verification boundaries.

Date: 2026-09-27. Review stack: design foundation, presentation core, feature journeys, verification, automatic connection. The [prior A01+ review](android-core-ux-review-plan.md) remains a historical trust checklist; its original navigation/visual guidance is superseded by the [new design system](../design/design-system.md).

## Software checks

The four required Gradle tasks pass: `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, `:app:lintDebug`. The 34 unit tests include the existing protocol, projection, migration, operation and signed-release checks plus strict success copy, stepper gating, exact preview bindings, plain-language blockers and foreground connection scheduling. Six connection cases cover target selection, retry delay bounds, freshness, exact payload recognition, lifecycle cancellation and user-operation priority. `tools/validate.py` verifies 4 schemas, 7 examples, 15 rejection cases, the signed vector, 55 document link sets and generated token parity.

On the Pixel, `GoldenScreenshotTest`, the two `PrimaryJourneyTest` cases and three `PresentationAccessibilityTest` cases passed. The golden test compares 64 images against checked-in baselines. Capture checks reject a run if another app takes the foreground, so interrupted captures cannot become baselines. The accessibility cases cover one reachable primary action on all 12 fixture screens at 200% font scale, copying the last of 50 Details fields without scrolling to the copy button, and the explicitly labelled example clear-code consequence dialog. The clear callback is test-only and sends no command.

The image baselines and Compose previews share `design/fixtures/ui-states.json` and debug `ScreenFixtures`. These are simulations, never observations of a vehicle. [Native gallery](../../design/prototype/native.html), [interactive concept](../../design/prototype/index.html), [IA and flows](../design/redesign/concept.md), [audit](../design/redesign/audit.md), [baseline string inventory](../design/redesign/strings.csv).

## Physically observed phone and gauge behaviour

Device: Pixel 10 Pro, Android 17 / API 37, 1280×2856 physical pixels, existing density override 532 dpi. The app was installed over Wi-Fi ADB. The awake-session helper was used; phone settings are restored at the end of verification.

| Observation | Result | Scope |
| --- | --- | --- |
| Native navigation and Customize | Passed on the Pixel | Gauge, Car, Settings, preview-led Customize destinations and honest unavailable states |
| Powered gauge connection | Protected settings read succeeded | An initial “Offline” label was misleading because no session had been checked. Foreground discovery now starts automatically, with “Gauge ready” only after a recent protected check. |
| Three-reading send | Passed | Engine speed, coolant temperature and engine load selected through the UI, with numeric, arc and bar layouts |
| Running confirmation | Passed | Revision 7 matched digest `741d6c17b03d4b2411a1dede001b1cd06f7f94215dd65d584f8aa7c24d1d1542`, running flag set and trial cleared |
| Restore original setup | Passed | Original revision 6's five pages, bindings, layouts and limits were restored as revision 8, then read and compared again |
| Phone choices | Restored | The pre-test local profile choices were preserved separately, including any unsent changes |
| Automatic connection on launch | Passed, 3.827 seconds | Same remembered owner gauge, protected saved/runtime confirmation, no Connect tap |
| Return to the app | Passed | Activity stop/start refreshed protected confirmation without a tap |
| Bluetooth interruption | Passed | Phone Bluetooth off/on caused a visible offline state, then automatic rediscovery and owner reconnect; revision 8 and its digest remained unchanged |
| End-to-end bench test duration | 56.245 seconds | Includes discovery, protected reads, UI editing, send and restoration. This is not a first-time setup or participant timing result. |

The [automatic connection record](../design/redesign/screenshots/physical/automatic-connection/result.txt) and its real Pixel captures cover launch, resume and Bluetooth recovery. This separate read-only run took 35.937 seconds in total, including the interruption. A screenshot check caught a one-frame timestamp mismatch between the card and connection pill; state mapping now samples the current monotonic clock, and the physical test also asserts the visible **Gauge ready** label. The same gauge configuration remained intact. Both the connection pill and status card expire their ready state after 30 seconds; boundary checks cover stale and future timestamps. Fresh pairing, multi-gauge radio environments and permission denial were not re-created on the bonded bench phone; deterministic selection and scheduling are covered by unit tests.

The [physical run record](../design/redesign/screenshots/physical/result.txt) contains exact before/sent/restored identities. The [confirmed screen](../design/redesign/screenshots/physical/configuration-confirmed.png) is a real Pixel capture after protected gauge confirmation. Its displayed reading value is still visibly labelled Preview. No live RPM or other vehicle value was obtained.

These are physical phone observations and authenticated responses from the powered gauge. No camera-based observation of the physical gauge's pixels was made. First-owner code association was not repeated because the existing owner bond was retained. The hardware run used the redesign debug build before the final accessibility-only refinements; the unchanged transport/codec and confirmation checks were retained throughout.

## Accessibility and adaptive layout

- Real Pixel system font scale 2.0: both primary-journey tests passed; Customize destinations and default destinations were captured. Large content scrolls and the primary action remains reachable.
- Light and dark physical-phone journeys passed. A temporary 1100×880 dp window on the Pixel exercised rail navigation and side-by-side layouts; both journey tests passed. This is a resized phone window, not physical tablet or foldable qualification.
- TalkBack 17 was enabled on the Pixel. Navigation focus outlines and activation were observed on Gauge, Customize, Car and Settings, including a change to the dynamic-colour switch. Round-preview semantics are covered by the Compose checks; touch-exploration accuracy was not independently established. The service was confirmed bound and touch exploration enabled. Captures are in `docs/design/redesign/screenshots/after/talkback/`. Spoken audio was not monitored, so pronunciation and audio timing are not claimed as passed. Existing accessibility services were preserved and TalkBack was removed from the enabled list afterward.
- Source and fixture checks: 48 dp minimum action targets; headings and selection/disabled states; a single full round-preview announcement; a visible scalable equivalent at large text; whole-row named switches; explicit status text and icons; no colour-only critical state.
- Dynamic colour is optional. Warning/critical/success colours and the round preview use fixed semantic tokens.
- Compact navigation uses a bar, medium/expanded navigation uses a rail. Separating fold bounds are excluded from controls. Physical foldable hardware has not been tested.
- The paired Pixel uses three-button system navigation. Predictive Back is implemented through the Android API and manifest opt-in; a gesture-navigation hardware pass remains unperformed.

## A01+ regression record

| Finding | Retained or improved safeguard | Evidence / limit |
| --- | --- | --- |
| A01: success strength | Exact running revision + digest + cleared trial; stored/100%/generic read cannot claim active | Unit fixtures and physical revision 7 then restoration 8 |
| A02: operation ownership | Existing coordinator retained; shared banner across destinations; new download/install presentation uses the same operation lease | Existing overlap tests plus automatic read cancellation/cleanup before a user lease; background stops automatic work without cancelling a user transaction; no background-delivery claim |
| A03: scope and freshness | Target changes still clear target-specific observations; diagnostics expire after 30 seconds; prior check-engine-on status remains explicit when stale | Existing codec/state tests; no live OBD session |
| A04: association | Automatic discovery chooses only the remembered target; a different advertiser cannot replace it. Real owner-authenticated reads remain required; no invented pairing code or owner success | Physical protected reconnect; fresh-owner pairing not repeated |
| A05: ordinary terminology | Gauge/Car/Settings, opt-in Expert, exact technical facts in Details | Default-journey jargon assertions, audit and 601-string baseline inventory |
| A06: intent vs payload | Existing typed projector retained; every page/binding/layout/alert reviewed; unsupported values blocked | Exact-binding tests and physical send/restoration; no renderer fallback |
| A07: accessibility | Tokens, readable hierarchy, scroll reset per Customize step, one primary action, named controls and scalable preview equivalent | Compose tests, goldens and physical captures; audio qualification is separately bounded below |
| A08/A09: persistence and protocol | Existing profile migration, codecs, repository and GATT implementation unchanged | Existing tests pass; no secret enters Details or appearance preferences |
| A10/A13: updates | Existing signed package/catalog checks and durable interruption reconciliation retained; no Done before running proof | Unit tests and simulated stage/recovery screens; no new firmware installation in this task |
| A11/A12: coverage/documentation | Shared image fixtures, physical bench journey and updated docs | No participant study, startup/jank benchmark, broad device matrix or release qualification claimed |

## Screenshots and remaining gates

[Before screenshots](../design/redesign/screenshots/before/) were captured from the original engineering-oriented UI. New native captures, physical transfer captures and explicitly labelled simulated error/update screens are linked from the final verification PR. Before/after comparisons must not label a simulated failure or transfer as a hardware result.

No live OBD telemetry, actual fault-code clearing, firmware flashing, LVGL changes, public capability enablement, real power-loss injection or deliberate broken-signature installation was performed. Adapter setup, custom-definition execution, second-adapter connection, brightness control and production updates show honest unavailable states. Existing expert tools remain reachable.

The first-time setup under two minutes acceptance target cannot be established with the current missing adapter path. A fresh-owner participant run, real vehicle qualification, physical foldables, gesture Back, full RTL, update interruption/rollback, performance and full spoken-audio accessibility qualification remain separate work. This redesign does not claim those paths are implemented or verified.
