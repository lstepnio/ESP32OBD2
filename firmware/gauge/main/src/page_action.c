#include "page_action.h"
#include <stdlib.h>
#include <string.h>

void page_action_init(page_action_t *s, page_action_config_t config)
{
    memset(s, 0, sizeof(*s));
    s->config = config;
    if (config.count < 2 || config.count > 5 || config.window_ms < 2000 || config.window_ms > 10000 || config.window_ms % 1000 != 0)
        s->config.enabled = false;
}
void page_action_reset(page_action_t *s)
{
    s->progress = 0;
    s->pressed = false;
    s->cancelled = false;
    /* Context loss cannot shorten the completed-action cooldown. */
}
void page_action_tick(page_action_t *s, uint32_t now)
{
    if (s->progress && now - s->first_ms > s->config.window_ms) s->progress = 0;
    if (s->cooling && now - s->executed_ms >= 5000) s->cooling = false;
}
void page_action_press(page_action_t *s, int16_t x, int16_t y, uint32_t now)
{
    page_action_tick(s, now);
    s->pressed = s->config.enabled;
    s->cancelled = false;
    s->x = x; s->y = y; s->press_ms = now;
}
void page_action_move(page_action_t *s, int16_t x, int16_t y)
{
    if (s->pressed && (abs((int)x - s->x) > 24 || (int)y - s->y > 16)) s->cancelled = true;
}
bool page_action_release(page_action_t *s, int16_t x, int16_t y, uint32_t now, bool *fired)
{
    *fired = false;
    page_action_tick(s, now);
    bool valid = s->pressed && !s->cancelled && s->config.enabled &&
        now - s->press_ms >= 80 && now - s->press_ms <= 800 &&
        (int)s->y - y >= 32 && abs((int)x - s->x) <= 24 &&
        (int)s->y - y >= 2 * abs((int)x - s->x);
    s->pressed = false;
    if (!valid) { s->progress = 0; return false; }
    if (s->cooling) return true;
    if (!s->progress) s->first_ms = now;
    if (++s->progress == s->config.count) {
        s->progress = 0;
        s->cooling = true;
        s->executed_ms = now;
        *fired = true;
    }
    return true;
}
