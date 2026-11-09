#include <assert.h>
#include "platform_config.h"

#ifdef EGAUGE_OLED_LICENSE_REQUIRED
uint8_t platform_feature_mask(void)
{
    return EGAUGE_TEST_LICENSE_MASK & EGAUGE_COMPILED_FEATURES;
}
#define EXPECTED_DUAL ((EGAUGE_TEST_LICENSE_MASK & PLATFORM_FEATURE_DUAL_ADAPTER) && EGAUGE_FEATURE_DUAL_ADAPTER)
#define EXPECTED_ACTIONS                                                                                               \
    ((EGAUGE_TEST_LICENSE_MASK & PLATFORM_FEATURE_VEHICLE_ACTIONS) && EGAUGE_FEATURE_VEHICLE_ACTIONS)
#else
#define EXPECTED_DUAL EGAUGE_FEATURE_DUAL_ADAPTER
#define EXPECTED_ACTIONS EGAUGE_FEATURE_VEHICLE_ACTIONS
#endif

int main(void)
{
    config_runtime_t runtime = {.source_count = 2,
                                .sources      = {{.transmission = true}, {.transmission = false}},
                                .pid_count    = 2,
                                .page_count   = 2,
                                .pids         = {{.source_index = 0}, {.source_index = 1}},
                                .page_action  = {.enabled = true, .vehicle_menu = true}};
    assert(platform_config_accepts(&runtime) == (EXPECTED_DUAL && EXPECTED_ACTIONS));
    platform_config_apply(&runtime);
    assert(platform_config_accepts(&runtime));
    assert(runtime.source_count == (EXPECTED_DUAL ? 2 : 1));
    assert(runtime.sources[0].transmission == EXPECTED_DUAL);
    assert(runtime.pids[0].source_index == (EXPECTED_DUAL ? 0 : 1));
    assert(runtime.pids[1].source_index == (EXPECTED_DUAL ? 1 : 0));
    assert(runtime.page_count == 2 && runtime.pid_count == 2);
    assert(runtime.page_action.enabled == EXPECTED_ACTIONS);
    runtime.page_action = (page_action_config_t){.enabled = true, .target_page = 1};
    platform_config_apply(&runtime);
    assert(runtime.page_action.enabled && runtime.page_action.target_page == 1);
    return 0;
}
