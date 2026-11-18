#pragma once

/* The board build sets the limit for new dashboards. Keep enough slots to
 * read and run existing dashboards written before that limit was introduced. */
#ifndef EGAUGE_BOARD_PAGE_LIMIT
#define EGAUGE_BOARD_PAGE_LIMIT 5
#endif

#define EGAUGE_LEGACY_PAGE_LIMIT 10
#define EGAUGE_RUNTIME_PAGES                                                                                           \
    (EGAUGE_BOARD_PAGE_LIMIT > EGAUGE_LEGACY_PAGE_LIMIT ? EGAUGE_BOARD_PAGE_LIMIT : EGAUGE_LEGACY_PAGE_LIMIT)
