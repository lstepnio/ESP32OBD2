#include <assert.h>
#include "platform_config.h"

int main(void)
{
    config_runtime_t runtime = {.source_count = 2,
                                .sources      = {{.transmission = true}, {.transmission = false}},
                                .pid_count    = 2,
                                .page_count   = 2,
                                .pids         = {{.source_index = 0}, {.source_index = 1}},
                                .page_action  = {.enabled = true, .vehicle_menu = true}};
    assert(platform_config_accepts(&runtime) == (EGAUGE_FEATURE_DUAL_ADAPTER && EGAUGE_FEATURE_VEHICLE_ACTIONS));
    platform_config_apply(&runtime);
    assert(platform_config_accepts(&runtime));
    assert(runtime.source_count == (EGAUGE_FEATURE_DUAL_ADAPTER ? 2 : 1));
    assert(runtime.sources[0].transmission == EGAUGE_FEATURE_DUAL_ADAPTER);
    assert(runtime.pids[0].source_index == (EGAUGE_FEATURE_DUAL_ADAPTER ? 0 : 1));
    assert(runtime.pids[1].source_index == (EGAUGE_FEATURE_DUAL_ADAPTER ? 1 : 0));
    assert(runtime.page_count == 2 && runtime.pid_count == 2);
    assert(runtime.page_action.enabled == EGAUGE_FEATURE_VEHICLE_ACTIONS);
    runtime.page_action = (page_action_config_t){.enabled = true, .target_page = 1};
    platform_config_apply(&runtime);
    assert(runtime.page_action.enabled && runtime.page_action.target_page == 1);
    return 0;
}
