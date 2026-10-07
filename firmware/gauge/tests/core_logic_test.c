#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <string.h>

#include "alert_engine.h"
#include "config_trial_policy.h"
#include "display_units.h"
#include "transmission_gear.h"
#include "json_guard.h"
#include "pid_decoder.h"
#include "poll_scheduler.h"
#include "page_save_policy.h"
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

static void test_display_units(void)
{
    assert(strcmp(transmission_gear_label(13), "P") == 0);
    assert(strcmp(transmission_gear_label(11), "R") == 0);
    assert(strcmp(transmission_gear_label(0), "N") == 0);
    assert(strcmp(transmission_gear_label(1), "1") == 0);
    assert(strcmp(transmission_gear_label(8), "8") == 0);
    for (int value = -1; value < 256; ++value)
        assert((transmission_gear_label(value) != NULL) ==
               (value >= 0 && (value <= 8 || value == 11 || value == 13)));
    assert(lround(display_units_decimal(13, "gear", true)) == 13);
    assert(strcmp(display_units_label("gear", true), "") == 0);
    assert(lround(display_units_decimal(92, "°C", true)) == 198);
    assert(lround(display_units_decimal(-40, "degC", true)) == -40);
    assert(lround(display_units_decimal(64, "kph", true)) == 40);
    assert(lround(display_units_decimal(2840, "rpm", true)) == 2840);
    assert(lround(display_units_decimal(92, "°C", false)) == 92);
    assert(strcmp(display_units_label("°C", true), "°F") == 0);
    assert(strcmp(display_units_label("km/h", true), "mph") == 0);
}

static void test_decimal_display(void)
{
    char text[32];
    display_units_format(text,sizeof(text),14.2,"V",false); assert(!strcmp(text,"14.2"));
    display_units_format(text,sizeof(text),.825,"g/s",false); assert(!strcmp(text,"0.825"));
    display_units_format(text,sizeof(text),1.001,"ratio",false); assert(!strcmp(text,"1.001"));
    display_units_format(text,sizeof(text),100,"kPa",true); assert(!strcmp(text,"14.504"));
    display_units_format(text,sizeof(text),92,"degC",true); assert(!strcmp(text,"198"));
    display_units_format(text,sizeof(text),83.5,"degC",false); assert(!strcmp(text,"84"));
    display_units_format(text,sizeof(text),-39.5,"degC",false); assert(!strcmp(text,"-39"));
    display_units_format(text,sizeof(text),NAN,"V",true); assert(!strcmp(text,"..."));
    assert(fabs(display_units_decimal(2.5,"L/h",true)-.660430131)<1e-9);
    assert(!strcmp(display_units_label("L/h",true),"US gal/h"));
}

static void test_gear_and_fractional_alerts(void)
{
    config_runtime_t runtime = {0};
    runtime.pid_count=2; runtime.alert_count=2;
    runtime.pids[0].source_index=0; runtime.pids[0].stale_ms=500;
    runtime.pids[1].source_index=1; runtime.pids[1].stale_ms=500;
    strcpy(runtime.pids[0].name,"Voltage"); strcpy(runtime.pids[1].name,"Gear");
    runtime.alerts[0]=(runtime_alert_t){.pid_index=0,.above=false,.has_warning=true,.has_critical=true,
        .warning=12.2,.critical=11.7,.hysteresis=.1,.priority=8};
    runtime.alerts[1]=(runtime_alert_t){.pid_index=1,.equals=true,.has_warning=true,.has_critical=true,
        .warning=11,.critical=13,.trigger_dwell_ms=100,.clear_dwell_ms=100,.priority=9};
    alert_engine_init(&runtime);
    alert_engine_sample(0,12.19,0); assert(alert_engine_tick(0).severity==1);
    alert_engine_sample(0,12.25,1); assert(alert_engine_tick(1).severity==1);
    alert_engine_sample(0,12.31,2); assert(alert_engine_tick(2).severity==0);
    alert_engine_sample(1,11,10); assert(alert_engine_tick(10).severity==0);
    alert_engine_sample(1,11,110); assert(alert_engine_tick(110).severity==1);
    alert_engine_sample(1,13,111); assert(alert_engine_tick(111).severity==1);
    alert_engine_sample(1,13,211); assert(alert_engine_tick(211).severity==2);
    alert_engine_sample(1,0,212); assert(alert_engine_tick(212).severity==2);
    alert_engine_sample(1,0,312); assert(alert_engine_tick(312).severity==0);
    alert_engine_sample(1,13,313); alert_engine_invalidate_source(1);
    alert_engine_sample(1,13,414); assert(alert_engine_tick(414).severity==0);
    alert_engine_sample(1,13,514); assert(alert_engine_tick(514).severity==2);
    assert(alert_engine_tick(1015).unavailable);
    alert_engine_sample(0,11.69,1016); assert(alert_engine_tick(1016).severity==2);
    runtime.alerts[0].priority=10; assert(!alert_engine_tick(1016).unavailable);
    alert_engine_invalidate_source(1); assert(!alert_engine_tick(1017).unavailable);
    alert_engine_sample(1,0,1018); alert_engine_sample(1,0,1118);
    assert(!alert_engine_tick(1118).unavailable && !strcmp(alert_engine_tick(1118).label,"Voltage"));
    alert_engine_sample(0,NAN,1119); assert(alert_engine_tick(1119).unavailable);
    alert_engine_sample(0,12.5,1120); assert(alert_engine_tick(1120).severity==0);
    alert_engine_sample(1,13,1121); alert_engine_sample(1,NAN,1122);
    alert_engine_sample(1,13,1221); assert(alert_engine_tick(1221).severity==0);
    alert_engine_sample(1,13,1321); assert(alert_engine_tick(1321).severity==2);
    alert_engine_invalidate_pid(1); assert(alert_engine_tick(1322).unavailable);
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
    alert_engine_invalidate();
    summary = alert_engine_tick(11);
    assert(summary.severity == 1 && summary.unavailable);
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
    priorities.pids[1].source_index = 1;
    alert_engine_invalidate_source(1);
    summary = alert_engine_tick(2);
    assert(summary.severity == 1 && !strcmp(summary.label, "High priority") && summary.unavailable);
    priorities.alerts[1].priority = 0;
    summary = alert_engine_tick(2);
    assert(!strcmp(summary.label, "Low priority") && !summary.unavailable);
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
    /* Missing transmission data cannot consume the healthy engine's cadence. */
    poll_scheduler_init(&scheduler);
    scheduler.seen_background = 0x0f;
    scheduler.last_mil = 0;
    memset(scheduler.last_dtc, 0, sizeof(scheduler.last_dtc));
    intervals[0] = intervals[1] = 100;
    job = poll_scheduler_next(&scheduler, 0, intervals, 2);
    assert(job.kind == POLL_JOB_PID && job.index == 0);
    poll_scheduler_result(&scheduler, 0, false);
    job = poll_scheduler_next(&scheduler, 0, intervals, 2);
    assert(job.kind == POLL_JOB_PID && job.index == 1);
    poll_scheduler_result(&scheduler, 1, true);
    for (uint32_t now=100; now<1000; now+=100) {
        job = poll_scheduler_next(&scheduler, now, intervals, 2);
        assert(job.kind == POLL_JOB_PID && job.index == 1);
    }
    job = poll_scheduler_next(&scheduler, 1000, intervals, 2);
    assert(job.kind == POLL_JOB_PID && job.index == 0);
    poll_scheduler_result(&scheduler, 0, true);
    assert(scheduler.failures[0] == 0);
    /* A configured adapter with no display definitions still checks faults. */
    poll_scheduler_init(&scheduler);
    assert(poll_scheduler_next(&scheduler, 0, intervals, 0).kind == POLL_JOB_MIL);
    assert(poll_scheduler_next(&scheduler, 0, intervals, 0).kind == POLL_JOB_DTC);

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

static void test_page_save_policy(void)
{
    page_save_policy_t state = {0};
    assert(!page_save_due(&state, 10000));
    /* A burst of browsing needs one save after it settles, not a write per tap. */
    for (uint32_t tap = 1; tap <= 100; ++tap) {
        page_save_observe(&state, tap, tap * 100);
        assert(!page_save_due(&state, tap * 100 + 99));
    }
    assert(!page_save_due(&state, 10749));
    assert(page_save_due(&state, 10750));
    page_save_finished(&state, 100, true, 10750);
    assert(!page_save_due(&state, 20000));

    /* A tap arriving while an older selection is saved must stay pending. */
    page_save_observe(&state, 101, 20000);
    page_save_observe(&state, 102, 20750);
    page_save_finished(&state, 101, true, 20751);
    assert(!page_save_due(&state, 21499));
    assert(page_save_due(&state, 21500));
    page_save_finished(&state, 102, false, 21500);
    assert(!page_save_due(&state, 22249));
    assert(page_save_due(&state, 22250));
    page_save_finished(&state, 102, true, 22250);
    assert(!page_save_due(&state, 30000));

    /* The millisecond counter wrapping does not lose a pending save. */
    page_save_observe(&state, 103, UINT32_MAX - 500);
    assert(!page_save_due(&state, 248));
    assert(page_save_due(&state, 249));
}

int main(void)
{
    assert(!page_cycle_due(0, 3, true, true, 1000, 61000));
    assert(!page_cycle_due(5, 1, true, true, 1000, 6000));
    assert(!page_cycle_due(5, 3, false, true, 1000, 6000));
    assert(!page_cycle_due(5, 3, true, false, 1000, 6000));
    assert(!page_cycle_due(5, 3, true, true, 1000, 5999));
    assert(page_cycle_due(5, 3, true, true, 1000, 6000));
    assert(!page_cycle_due(5, 3, true, true, 6000, 6001)); /* Manual tap restarts the timer. */
    assert(page_cycle_due(5, 3, true, true, UINT32_MAX - 1000, 3999));
    test_json_guard();
    test_decoder();
    test_display_units();
    test_decimal_display();
    test_gear_and_fractional_alerts();
    test_alert_edges();
    test_scheduler();
    test_wifi_bulk_policy();
    test_config_trial_policy();
    test_page_save_policy();
    puts("Core firmware logic fixtures passed");
    return 0;
}
