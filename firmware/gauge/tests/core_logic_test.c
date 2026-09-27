#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <string.h>

#include "alert_engine.h"
#include "config_trial_policy.h"
#include "json_guard.h"
#include "pid_decoder.h"
#include "poll_scheduler.h"
#include "wifi_bulk_policy.h"

static void test_json_guard(void)
{
    const char *quoted = "{\"text\":\"[{\\\"x\\\":1}]\"}";
    const char *deep = "[[[[[]]]]]";
    const char *open = "{\"x\":1";
    const char *unterminated = "{\"x\":\"unterminated}";
    assert(json_guard_shape(quoted, strlen(quoted), 4));
    assert(!json_guard_shape(deep, strlen(deep), 4));
    assert(!json_guard_shape(open, strlen(open), 4));
    assert(!json_guard_shape(unterminated, strlen(unterminated), 4));
    assert(json_guard_shape("[[[[]]]]", 8, 4));
    assert(!json_guard_shape("[[[[[]]]]]", 10, 4));
    const char *escaped = "{\"slash\":\"\\\\\",\"brace\":\"}\"}";
    assert(json_guard_shape(escaped, strlen(escaped), 4));
    assert(!json_guard_shape("{]", 2, 4));
}

static void test_decoder(void)
{
    pid_numeric_decoder_t decoder = {
        .byte_length = 2, .signed_value = true, .numerator = 1,
        .denominator = 2, .minimum = -100, .maximum = 100,
    };
    const uint8_t payload[] = {0xff, 0xf6};
    double value = 0;
    assert(pid_decoder_eval(&decoder, payload, sizeof(payload), &value));
    assert(fabs(value + 5.0) < 0.0001);
}

static config_runtime_t one_alert_runtime(void)
{
    config_runtime_t runtime = {0};
    runtime.pid_count = 1;
    runtime.alert_count = 1;
    strcpy(runtime.pids[0].name, "Coolant");
    runtime.pids[0].stale_ms = 500;
    runtime.alerts[0] = (runtime_alert_t){
        .pid_index = 0, .above = true, .has_warning = true,
        .warning = 100, .hysteresis = 5,
    };
    return runtime;
}

static void test_alert_edges(void)
{
    config_runtime_t runtime = one_alert_runtime();
    alert_engine_init(&runtime);
    alert_engine_sample(0, 101, 10);
    alert_summary_t summary = alert_engine_tick(10);
    assert(summary.severity == 1 && !summary.unavailable);
    summary = alert_engine_tick(600);
    assert(summary.severity == 1 && summary.unavailable);
    alert_engine_sample(0, 94, 610);
    summary = alert_engine_tick(610);
    assert(summary.severity == 0);

    runtime.alerts[0].trigger_dwell_ms = 100;
    alert_engine_init(&runtime);
    alert_engine_sample(0, 101, 0);
    assert(alert_engine_tick(0).severity == 0);
    alert_engine_sample(0, 101, 99);
    assert(alert_engine_tick(99).severity == 0);
    alert_engine_sample(0, 101, 100);
    assert(alert_engine_tick(100).severity == 1);

    runtime.alerts[0].has_critical = true;
    runtime.alerts[0].critical = 110;
    runtime.alerts[0].trigger_dwell_ms = 0;
    runtime.alerts[0].clear_dwell_ms = 100;
    alert_engine_init(&runtime);
    alert_engine_sample(0, 111, UINT32_MAX - 20);
    assert(alert_engine_tick(UINT32_MAX - 20).severity == 2);
    alert_engine_sample(0, 104, UINT32_MAX - 10);
    assert(alert_engine_tick(UINT32_MAX - 10).severity == 2);
    alert_engine_sample(0, 104, 89);
    assert(alert_engine_tick(89).severity == 1);

    config_runtime_t priorities = {0};
    priorities.pid_count = 2;
    priorities.alert_count = 2;
    strcpy(priorities.pids[0].name, "Low priority");
    strcpy(priorities.pids[1].name, "High priority");
    priorities.pids[0].stale_ms = priorities.pids[1].stale_ms = 1000;
    priorities.alerts[0] = (runtime_alert_t){
        .pid_index = 0, .above = true, .has_warning = true, .warning = 1, .priority = 1,
    };
    priorities.alerts[1] = (runtime_alert_t){
        .pid_index = 1, .above = true, .has_warning = true, .warning = 1, .priority = 9,
    };
    alert_engine_init(&priorities);
    alert_engine_sample(0, 2, 1);
    alert_engine_sample(1, 2, 1);
    summary = alert_engine_tick(1);
    assert(summary.severity == 1 && strcmp(summary.label, "High priority") == 0);
}

static void test_scheduler(void)
{
    poll_scheduler_t scheduler;
    poll_scheduler_init(&scheduler);
    uint32_t intervals[] = {1, 1};
    poll_job_t job = poll_scheduler_next(&scheduler, 0, intervals, 2);
    assert(job.kind == POLL_JOB_MIL);
    for (uint32_t now = 1; now <= 10; ++now) {
        job = poll_scheduler_next(&scheduler, now, intervals, 2);
        assert(job.kind == POLL_JOB_PID);
        assert(job.index == (uint8_t)((now - 1) % 2));
    }
    job = poll_scheduler_next(&scheduler, 11, intervals, 2);
    assert(job.kind == POLL_JOB_DTC && job.index == 0);

    /* A due diagnostic cannot form a catch-up burst after one background job. */
    poll_scheduler_init(&scheduler);
    job = poll_scheduler_next(&scheduler, 1000, intervals, 2);
    assert(job.kind == POLL_JOB_MIL);
    job = poll_scheduler_next(&scheduler, 1000, intervals, 2);
    assert(job.kind == POLL_JOB_PID && job.index == 0);
    job = poll_scheduler_next(&scheduler, 1000, intervals, 2);
    assert(job.kind == POLL_JOB_PID && job.index == 1);
    assert(poll_scheduler_next(&scheduler, 1000, intervals, 2).kind == POLL_JOB_NONE);
    for (uint32_t now = 1001; now <= 1008; ++now)
        assert(poll_scheduler_next(&scheduler, now, intervals, 2).kind == POLL_JOB_PID);
    job = poll_scheduler_next(&scheduler, 1009, intervals, 2);
    assert(job.kind == POLL_JOB_DTC);

    assert(poll_scheduler_next(&scheduler, 1010, intervals,
                               POLL_SCHEDULER_MAX_PIDS + 1).kind == POLL_JOB_NONE);

    poll_scheduler_init(&scheduler);
    scheduler.seen_pids = 1;
    scheduler.last_pid[0] = UINT32_MAX - 5;
    intervals[0] = 10;
    scheduler.seen_background = 0x0f;
    scheduler.last_mil = UINT32_MAX - 5;
    for (unsigned i = 0; i < 3; ++i) scheduler.last_dtc[i] = UINT32_MAX - 5;
    job = poll_scheduler_next(&scheduler, 5, intervals, 1);
    assert(job.kind == POLL_JOB_PID && job.index == 0);
}

static void test_wifi_bulk_policy(void)
{
    assert(wifi_bulk_policy_accept(true, 7, 7, 0, 1, 200, 100));
    assert(!wifi_bulk_policy_accept(false, 7, 7, 0, 1, 200, 100));
    assert(!wifi_bulk_policy_accept(true, 0, 0, 0, 1, 200, 100));
    assert(!wifi_bulk_policy_accept(true, 7, 8, 0, 1, 200, 100));
    assert(!wifi_bulk_policy_accept(true, 7, 7, 1, 1, 200, 100));
    assert(!wifi_bulk_policy_accept(true, 7, 7, 2, 1, 200, 100));
    assert(!wifi_bulk_policy_accept(true, 7, 7, 0, 1, 100, 100));
    assert(!wifi_bulk_policy_accept(true, 7, 7, 0, 1, 99, 100));
    assert(wifi_bulk_policy_accept(true, 7, 7, 0, 1, 5, UINT32_MAX - 5));
}

static void test_config_trial_policy(void)
{
    config_trial_policy_t state = {0};
    config_trial_policy_decision_t decision;
    config_trial_policy_prepare(&state, 3);
    assert(!config_trial_policy_needs_confirmation(&state, 3));
    assert(config_trial_policy_decide(&state, 3, &decision));
    assert(decision == CONFIG_TRIAL_POLICY_TRIAL);
    assert(config_trial_policy_needs_confirmation(&state, 3));

    /* A reset before confirmation rejects the trial on the next boot. */
    assert(config_trial_policy_decide(&state, 3, &decision));
    assert(decision == CONFIG_TRIAL_POLICY_PREVIOUS);
    assert(state.rejected_revision == 3 && state.pending_revision == 0);
    assert(!config_trial_policy_decide(&state, 3, &decision));
    assert(decision == CONFIG_TRIAL_POLICY_PREVIOUS);

    /* A newer transfer clears rejection and can be confirmed healthy. */
    config_trial_policy_prepare(&state, 4);
    assert(config_trial_policy_decide(&state, 4, &decision));
    assert(decision == CONFIG_TRIAL_POLICY_TRIAL);
    assert(config_trial_policy_confirm(&state, 4));
    assert(!config_trial_policy_needs_confirmation(&state, 4));
    assert(!config_trial_policy_decide(&state, 4, &decision));
    assert(decision == CONFIG_TRIAL_POLICY_ACTIVE);

    /* A journal written before a commit is discarded when the slot did not advance. */
    config_trial_policy_prepare(&state, 5);
    assert(config_trial_policy_decide(&state, 4, &decision));
    assert(decision == CONFIG_TRIAL_POLICY_ACTIVE && state.pending_revision == 0);
    config_trial_policy_prepare(&state, 5);
    assert(config_trial_policy_cancel(&state, 5));
    assert(!config_trial_policy_cancel(&state, 5));
}

int main(void)
{
    test_json_guard();
    test_decoder();
    test_alert_edges();
    test_scheduler();
    test_wifi_bulk_policy();
    test_config_trial_policy();
    puts("Core firmware logic fixtures passed");
    return 0;
}
