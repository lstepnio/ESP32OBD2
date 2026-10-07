#pragma once
#include <stdint.h>
#define portMAX_DELAY UINT32_MAX
#define pdTICKS_TO_MS(x) (x)

#define pdTRUE 1
#define pdMS_TO_TICKS(x) (x)
typedef int portMUX_TYPE;
#define portMUX_INITIALIZER_UNLOCKED 0
extern int test_critical_depth;
#define portENTER_CRITICAL(m) do { (void)(m); ++test_critical_depth; } while (0)
#define portEXIT_CRITICAL(m) do { (void)(m); --test_critical_depth; } while (0)
