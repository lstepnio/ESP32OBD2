#pragma once
#include <stdbool.h>
extern bool test_paused;
static inline bool ble_mgr_is_paused(void) { return test_paused; }
