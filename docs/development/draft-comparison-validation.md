# Phone draft and saved gauge comparison

Observed on 2026-09-26 with the paired Pixel 10 Pro and USB-powered ESP32-S3-Touch-LCD-1.28. No OBD adapter was attached.

The final Android debug APK from commit `42081ad` was installed with `adb install -r`. The app discovered the gauge through its public capability read. On Design, **Refresh saved configuration** completed through the existing owner bond without a new passkey prompt. The screen reported saved revision 2, eight matching fields, one difference, and zero unavailable fields. The difference was the phone draft's `dual` renderer versus the gauge document's `numeric` renderer. The profile ID and primary PID rows visibly matched.

Changing only the local renderer chip to Numeric updated the comparison to nine matches and zero differences without a gauge transfer. Restoring the chip to Dual returned the comparison to eight matches and one difference. A fresh authenticated document read still reported revision 2 with the same difference. This confirms the displayed comparison reacts to the local draft and that this specific edit did not advance the saved document revision. It does not prove that no other phone or physical action could change the gauge later; the UI calls this the last verified readback and offers refresh.

The comparison checks profile ID, primary PID, renderer, primary source, coolant warning and critical values, hysteresis, and trigger and clear dwell. A field that cannot be mapped from a saved document is labeled unavailable. No vehicle data, adapter behavior, or general Apply operation was validated in this session.
