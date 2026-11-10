#pragma once

/* Standalone consumers fail closed. Product builds define each switch explicitly. */
#ifndef EGAUGE_FEATURE_DUAL_ADAPTER
#define EGAUGE_FEATURE_DUAL_ADAPTER 0
#endif
#ifndef EGAUGE_FEATURE_SMART_CYCLE
#define EGAUGE_FEATURE_SMART_CYCLE 0
#endif
#ifndef EGAUGE_FEATURE_VEHICLE_ACTIONS
#define EGAUGE_FEATURE_VEHICLE_ACTIONS 0
#endif

#ifndef EGAUGE_FEATURE_CONNECTION_FOOTER
#define EGAUGE_FEATURE_CONNECTION_FOOTER 0
#endif

#define EGAUGE_FEATURES_RESTRICTED                                                                                     \
    (!EGAUGE_FEATURE_DUAL_ADAPTER || !EGAUGE_FEATURE_SMART_CYCLE || !EGAUGE_FEATURE_VEHICLE_ACTIONS)
