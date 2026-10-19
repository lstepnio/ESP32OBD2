#pragma once
#include <stdbool.h>
#include <stdint.h>

/* Pure bounded local-action recognizer. No adapter/vehicle commands. */
typedef struct { bool enabled; uint8_t target_page, count; uint32_t window_ms; } page_action_config_t;
typedef struct {
    page_action_config_t config;
    uint8_t progress;
    uint32_t first_ms, press_ms, executed_ms;
    int16_t x, y;
    bool pressed, cancelled, cooling;
} page_action_t;
void page_action_init(page_action_t *state, page_action_config_t config);
void page_action_reset(page_action_t *state);
void page_action_tick(page_action_t *state, uint32_t now);
void page_action_press(page_action_t *state, int16_t x, int16_t y, uint32_t now);
void page_action_move(page_action_t *state, int16_t x, int16_t y);
/* Returns true for a consumed upward swipe. fired is true only at completion. */
bool page_action_release(page_action_t *state, int16_t x, int16_t y, uint32_t now, bool *fired);
