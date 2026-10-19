#include "gauge_layout.h"
#include "display_showcase.h"
#include <assert.h>
#include <string.h>
#include <stdio.h>
#include <math.h>

int main(void)
{
    assert(gauge_layout_fits(80, 180, 160, 202));
    assert(gauge_layout_fits(8, 120, 232, 120));
    assert(!gauge_layout_fits(7, 120, 233, 120));
    assert(gauge_layout_fits(24, 70, 216, 170));
    assert(!gauge_layout_fits(24, 60, 216, 180));
    assert(!gauge_layout_fits(20, 180, 220, 220));
    assert(!gauge_layout_fits(120, 120, 119, 121));
    assert(!strcmp(gauge_layout_label("Engine RPM", "rpm"), "RPM"));
    assert(!strcmp(gauge_layout_unit("RPM", "rpm"), ""));
    assert(!strcmp(gauge_layout_unit("Input speed", "rpm"), "rpm"));
    char alias[32];
    gauge_layout_alert_alias(alias, sizeof(alias), "Transmission temperature");
    assert(!strcmp(alias, "Trans temp"));
    gauge_layout_alert_alias(alias, sizeof(alias), "TCM temperature");
    assert(!strcmp(alias, "TCM temp"));
    gauge_layout_alert_alias(alias, sizeof(alias), "Transmission gear");
    assert(!strcmp(alias, "Transmission gear"));
    assert(!strcmp(gauge_layout_label("TCM temperature", "°C"), "TCM temp"));
    assert(!strcmp(gauge_layout_label("Target gear", "gear"), "Target gear"));
    display_showcase_t s = {0};
    assert(!display_showcase_apply(&s, true, 4, 1, 0, false, false, false));
    assert(display_showcase_apply(&s, true, 8, 2, 0, true, false, false));
    assert(!s.active && s.result == SHOWCASE_REFUSED);
    assert(display_showcase_apply(&s, true, 8, 3, UINT32_MAX - 1000, false, false, false));
    assert(s.active);
    assert(!display_showcase_tick(&s, 2000, false));
    assert(display_showcase_tick(&s, 8000, false));
    assert(s.index == 1);
    uint32_t start = s.started;
    assert(display_showcase_apply(&s, true, 8, 3, 9000, false, false, false));
    assert(s.started == start);
    assert(display_showcase_tick(&s, 9000, true));
    assert(!s.active);
    assert(display_showcase_apply(&s, true, 8, 4, 10000, false, false, false));
    assert(display_showcase_tick(&s, 610000, false));
    assert(!s.active);
    assert(display_showcase_apply(&s, false, 0, 5, 620000, true, true, true));
    assert(!s.active);
    for (unsigned i = 0; i < display_showcase_count(); i++)
    {
        const display_showcase_frame_t *f = display_showcase_frame(i);
        assert(f && f->renderer <= 4 && f->metrics >= 1 && f->metrics <= 2);
        for (unsigned j = 0; j < f->metrics; j++)
            assert(f->labels[j] && f->units[j] && isfinite(f->values[j]));
    }
    assert(!display_showcase_frame(display_showcase_count()));
    puts("PASS: circular layout, unit semantics and isolated showcase admission/expiry/replay");
}
