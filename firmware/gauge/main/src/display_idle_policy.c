#include "display_idle_policy.h"
void display_idle_touch(display_idle_policy_t *s, uint32_t now)
{
    s->last_touch = now;
    s->touched    = true;
}
bool display_idle_waiting(display_idle_policy_t *s, bool available, uint32_t at, bool priority_ui, uint32_t now)
{
    if (available && (!s->saw_data || now - at < now - s->last_data))
    {
        s->saw_data  = true;
        s->last_data = at;
    }
    return !priority_ui && (!s->saw_data || now - s->last_data >= DISPLAY_IDLE_DATA_MS) &&
           (!s->touched || now - s->last_touch >= DISPLAY_IDLE_TOUCH_MS);
}
