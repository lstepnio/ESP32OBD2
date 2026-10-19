#pragma once

/* Applied only to the managed LVGL library, without modifying vendor sources.
 * Its fixed TLSF arena is CPU data, not a DMA buffer or a flash/ISR stack. */
#include "sdkconfig.h"
#include "esp_attr.h"

#if !CONFIG_SPIRAM || !CONFIG_SPIRAM_BOOT_INIT || !CONFIG_SPIRAM_ALLOW_BSS_SEG_EXTERNAL_MEMORY
#error "The gauge requires initialized PSRAM for its LVGL arena"
#endif
#if CONFIG_SPIRAM_IGNORE_NOTFOUND
#error "Do not silently boot without the PSRAM-backed display arena"
#endif

#define LV_ATTRIBUTE_LARGE_RAM_ARRAY EXT_RAM_BSS_ATTR
