#pragma once
#include <stdbool.h>
#include <stdint.h>

#define SMART_PAGE_PIDS 32U
#define SMART_PAGE_PAGES 8U
#define SMART_PAGE_MIN_VIEW_MS 5000U
#define SMART_PAGE_EARLY_MS 30000U
#define SMART_PAGE_PERSIST_MS 3000U
#define SMART_PAGE_EVALUATE_MS 200U
#define SMART_PAGE_TREND_WINDOW_MS 10000U
#define SMART_PAGE_COLD_ENTRY_C 60.0
#define SMART_PAGE_WARM_EXIT_C 75.0
#define SMART_PAGE_WARM_DWELL_MS 30000U
#define SMART_PAGE_COLD_MAX_MS 600000U

typedef enum
{
    SMART_METRIC_NONE,
    SMART_METRIC_COOLANT,
    SMART_METRIC_THERMAL,
    SMART_METRIC_RPM,
    SMART_METRIC_RUNTIME
} smart_metric_kind_t;
typedef enum
{
    SMART_REASON_ORDINARY,
    SMART_REASON_WARMUP,
    SMART_REASON_TREND,
    SMART_REASON_APPROACH,
    SMART_REASON_ALERT
} smart_page_reason_t;
typedef struct
{
    float   approach, rise_rate, rise_delta;
    uint8_t kind;
} smart_metric_rule_t;
typedef struct
{
    smart_metric_rule_t rule;
    float               low, high;
    uint32_t            stale_ms;
    bool                has_low, has_high;
} smart_pid_config_t;
typedef struct
{
    double   value, anchor;
    float    approach_origin;
    uint32_t at, generation, first_at, anchor_at, approach_since, trend_since;
    uint32_t boosted_at, episode, boosted_episode, advancing_at;
    uint8_t  severity, boosted_severity, boosted_rank, rank, reason, approach_direction;
    bool     present, multiple, suppressed, trend, boosted, advanced;
} smart_pid_state_t;
typedef struct
{
    uint8_t count, pids[2];
} smart_page_definition_t;
typedef struct
{
    smart_pid_config_t      config[SMART_PAGE_PIDS];
    smart_pid_state_t       pids[SMART_PAGE_PIDS];
    smart_page_definition_t pages[SMART_PAGE_PAGES];
    uint32_t                visited_at[SMART_PAGE_PAGES];
    uint8_t                 pid_count, page_count, cursor, selected, phase;
    uint16_t                interval;
    uint32_t                activity, switched_at, protected_at, early_at, evaluated_at;
    uint32_t                cold_at, warm_at;
    bool                    initialized, paused, insertion_used, early_used, evaluated, context_decided, warm_pending;
} smart_page_policy_t;
typedef struct
{
    uint8_t page, pid, reason;
    bool    priority, early;
} smart_page_decision_t;

/* Configuration is fixed for the running document. Runtime state is volatile. */
void smart_page_suspend(smart_page_policy_t *s);
bool smart_page_prepare(smart_page_policy_t *s,
                        uint16_t             interval,
                        uint8_t              selected,
                        uint32_t             activity,
                        bool                 paused,
                        uint32_t             now);
void smart_page_observe(smart_page_policy_t *s,
                        unsigned             pid,
                        bool                 valid,
                        double               value,
                        uint32_t             observed_at,
                        uint32_t             generation,
                        uint8_t              severity,
                        bool                 suppressed,
                        uint32_t             episode);
/* Peek never consumes a visit; caller commits only after successful UI apply. */
bool smart_page_choose(smart_page_policy_t *s, uint32_t now, smart_page_decision_t *out);
/* Preserve ordinary cycling if an optional interest projection is temporarily busy. */
bool    smart_page_fallback(const smart_page_policy_t *s, uint32_t now, smart_page_decision_t *out);
uint8_t smart_page_upcoming(const smart_page_policy_t *s, uint32_t now);
void    smart_page_commit(smart_page_policy_t *s, const smart_page_decision_t *choice, uint32_t now);
