#include <assert.h>
#include <stdio.h>
#include "reading_policy.h"
int main(void)
{
    poll_pid_policy_t rpm = reading_policy("engine.rpm", 1, 12, 500, 2000, true, false, false);
    assert(rpm.interval_ms == 250 && rpm.display_age_ms == 3000 && rpm.priority == 2);
    poll_pid_policy_t hidden = reading_policy("engine.rpm", 1, 12, 500, 2000, false, false, false);
    assert(hidden.interval_ms == 1000 && hidden.display_age_ms == rpm.display_age_ms);
    poll_pid_policy_t next = reading_policy("engine.rpm", 1, 12, 500, 2000, false, true, false);
    assert(next.interval_ms == 500 && next.priority == 1);
    poll_pid_policy_t temp = reading_policy("engine.coolant", 1, 5, 500, 2000, true, false, false);
    assert(temp.interval_ms == 1500 && temp.display_age_ms == 8000);
    temp = reading_policy("engine.coolant", 1, 5, 500, 2000, true, false, true);
    assert(temp.interval_ms == 500 && temp.display_age_ms == 8000);
    poll_pid_policy_t custom = reading_policy("custom.pressure", 1, 5, 200, 700, true, false, false);
    assert(custom.interval_ms == 200 && custom.display_age_ms == 700);
    custom = reading_policy("engine.coolant", 1, 5, 200, 700, true, false, false);
    assert(custom.interval_ms == 200 && custom.display_age_ms == 700);
    custom = reading_policy("engine.coolant", 0x22, 5, 500, 2000, true, false, false);
    assert(custom.interval_ms == 500 && custom.display_age_ms == 2000);
    puts("Reading classes preserve original display age across visibility and strict alert/custom policy");
}
