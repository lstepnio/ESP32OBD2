# Configuration reconciliation and write precondition

> Historical evidence. Versions, measurements and unfinished steps below describe that session.
> Current support is in [current state](../current-state.md); remaining work is in
> [the backlog](../backlog.md). These notes are not standing implementation instructions.

> Reference record: implementation/evidence from its recorded session. Current status and next work are maintained in [current state](../current-state.md) and [roadmap](../roadmap.md). Do not treat old pending steps or tool instructions as the current plan.

Observed on 2026-09-26 with the paired Pixel 10 Pro and USB-powered ESP32-S3-Touch-LCD-1.28. No OBD adapter was attached, and no configuration transfer was started during this check.

The updated debug APK was installed with `adb install -r`. After public gauge discovery, **Send experimental numeric profile** was disabled because the app had no verified write base. **Refresh saved configuration** completed through the existing owner bond and loaded revision 2 plus its SHA-256, after which the experimental sender became available.

The verified document showed eight matching fields and one difference because the local renderer was Dual while the saved renderer was Numeric. **Use saved settings in phone draft** copied every safely mapped field into the active local profile. The comparison changed to nine matches and zero differences, and Android persisted the local renderer as Numeric. This action did not invoke the transfer client. The renderer was then restored to Dual; the comparison returned to eight matches and one difference. A fresh owner read still reported saved revision 2, confirming that these local reconciliation actions did not advance the gauge configuration.

Before staging a future experimental transfer, Android now compares both the verified base revision and full saved SHA-256 with the gauge's current protected status. A mismatch stops before document generation or BEGIN and directs the user to refresh and review. The matching precondition and disabled-before-refresh UI were observed. An actual concurrent-writer conflict was not induced on hardware in this session, so rejection of a deliberately stale revision remains source-level behavior rather than physical evidence.
