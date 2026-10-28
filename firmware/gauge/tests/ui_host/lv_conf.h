#ifndef LV_CONF_H
#define LV_CONF_H
#define LV_COLOR_DEPTH 32
#define LV_USE_OS LV_OS_NONE
/* Match the device's fixed LVGL arena so new screens cannot hide allocation
 * regressions behind an unbounded host heap. Draw buffers remain host-owned. */
#define LV_USE_STDLIB_MALLOC LV_STDLIB_BUILTIN
#define LV_MEM_SIZE (64U * 1024U)
#define LV_USE_STDLIB_STRING LV_STDLIB_CLIB
#define LV_USE_STDLIB_SPRINTF LV_STDLIB_CLIB
#define LV_USE_LOG 0
#define LV_USE_FONT_PLACEHOLDER 0
#define LV_CONF_BUILD_DISABLE_EXAMPLES 1
#define LV_CONF_BUILD_DISABLE_DEMOS 1
#define LV_CONF_BUILD_DISABLE_THORVG_INTERNAL 1
#endif
