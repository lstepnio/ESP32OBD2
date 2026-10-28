#pragma once

#include "alert_engine.h"
#include "diagnostics_state.h"

/* One retained endpoint snapshot drives alert availability and the gauge face.
 * Raw companion/history status remains independent. Zero means snapshot busy. */
size_t diagnostic_alerts_observe(unsigned                endpoint,
                                 uint32_t                now,
                                 uint8_t                 packet[DIAGNOSTICS_FULL_SIZE],
                                 alert_transition_sink_t sink);
