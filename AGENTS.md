# Repository agent notes

For live Android debugging, run `python3 tools/adb_debug_awake.py start` before the session and `python3 tools/adb_debug_awake.py stop` when finished. The script saves and restores the phone's timeout and charging wake settings. The debug APK keeps its window awake while eGauge is visible. A manual device lock still requires the user to unlock it.

Record physical gauge and phone observations separately from simulated or source-level behavior. Keep public BLE capability flags disabled until the corresponding full path is implemented and verified.
