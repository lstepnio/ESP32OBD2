#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "diagnostics_state.h"
#include "diagnostic_filter.h"
#include "poll_scheduler.h"
#include "diagnostic_alerts.h"
#include "alert_events.h"
int                   test_lock_busy;
int                   test_critical_depth;
static uint8_t        packet[DIAGNOSTICS_FULL_SIZE];
static alert_events_t continuity_events;
static void           alert_sink(unsigned    key,
                                 uint8_t     severity,
                                 bool        unavailable,
                                 const char *label,
                                 const char *unit,
                                 float       value,
                                 float       limit,
                                 uint32_t    observed,
                                 uint32_t    now)
{
    alert_events_observe(&continuity_events, key, severity, unavailable, label, unit, value, limit, observed, now);
}
static void read_at(uint32_t now)
{
    assert(diagnostics_state_full_status(now, packet) == 248);
}
static void continuity_tests(void)
{
    const uint8_t             filtered[]  = {0x04, 0x20};
    const uint8_t             new_fault[] = {0x04, 0x20, 0x01, 0x23};
    const uint8_t             empty[]     = {0, 0};
    const diagnostic_filter_t rules[]     = {{0x0420, 0}};
    diagnostics_snapshot_t    snapshot;
    diagnostics_endpoint_configure(0, 0, 40, false);
    diagnostics_state_connected_for(2);
    diagnostics_state_snapshot_for(2, 1000, &snapshot);
    assert(!snapshot.valid && !snapshot.mil_known && !snapshot.count_known);
    assert(diagnostics_endpoint_presentation_status(0, 1000, packet) == 248);
    assert(!diagnostic_filter_project(packet, 0, rules, 1).suppress_mil);
    diagnostics_state_mil_for(2, true, 1, 1000);
    diagnostics_state_codes_for(2, 3, filtered, sizeof(filtered), 1000);
    diagnostics_state_codes_for(2, 7, empty, sizeof(empty), 1000);
    diagnostics_state_codes_for(2, 10, empty, sizeof(empty), 1000);
    assert(diagnostics_endpoint_presentation_status(0, 1001, packet) == 248);
    assert(diagnostic_filter_project(packet, 0, rules, 1).suppress_mil);
    uint8_t original[248];
    memcpy(original, packet, sizeof(original));
    diagnostics_state_failed_for(2, 1, DIAGNOSTICS_UNAVAILABLE);
    diagnostics_state_failed_for(2, 3, DIAGNOSTICS_UNAVAILABLE);
    diagnostics_state_failed_for(2, 7, DIAGNOSTICS_UNAVAILABLE);
    assert(diagnostics_endpoint_status(0, 1002, packet) == 248);
    assert(packet[3] == DIAGNOSTICS_UNAVAILABLE && !(packet[2] & 1));
    assert(packet[32] == DIAGNOSTICS_UNAVAILABLE && packet[33] == 1);
    assert(packet[34] == original[34] && !memcmp(packet + 36, original + 36, 68));
    assert(!diagnostic_filter_project(packet, 0, rules, 1).suppress_mil);
    assert(diagnostics_endpoint_presentation_status(0, 1002, packet) == 248);
    assert(diagnostic_filter_project(packet, 0, rules, 1).suppress_mil);
    diagnostics_state_snapshot_for(2, 1002, &snapshot);
    assert(snapshot.valid && snapshot.mil_on && snapshot.mil_known && snapshot.distinct_count == 1 &&
           !snapshot.count_partial);
    diagnostics_state_snapshot_for(2, 61000, &snapshot);
    assert(snapshot.mil_known);
    diagnostics_state_snapshot_for(2, 61001, &snapshot);
    assert(snapshot.mil_known && snapshot.count_known);
    assert(diagnostics_endpoint_status(0, 61001, packet) == 248 && !(packet[2] & 1));
    diagnostics_state_snapshot_for(2, 601000, &snapshot);
    assert(snapshot.mil_known); /* Local MIL bridges a five-minute refresh/retry. */
    diagnostics_state_snapshot_for(2, 601001, &snapshot);
    assert(!snapshot.mil_known && snapshot.count_known);
    diagnostics_state_snapshot_for(2, 121000, &snapshot);
    assert(snapshot.count_known);
    diagnostics_state_snapshot_for(2, 121001, &snapshot);
    assert(snapshot.count_known);
    assert(diagnostics_endpoint_status(0, 121001, packet) == 248 && !(packet[33] & 2));
    diagnostics_state_snapshot_for(2, 901000, &snapshot);
    assert(snapshot.count_known); /* Original successful sample, never attempt time. */
    diagnostics_state_snapshot_for(2, 901001, &snapshot);
    assert(!snapshot.valid && !snapshot.count_known);
    assert(diagnostics_endpoint_presentation_status(0, 901001, packet) == 248);
    assert(!diagnostic_filter_project(packet, 0, rules, 1).suppress_mil);

    /* Same-count MIL toggles invalidate proof, including the payload path. */
    diagnostics_state_mil_for(2, false, 1, 2000);
    diagnostics_state_mil_for(2, true, 1, 2001);
    assert(diagnostics_endpoint_presentation_status(0, 2001, packet) == 248);
    assert(!diagnostic_filter_project(packet, 0, rules, 1).suppress_mil);
    assert(diagnostics_endpoint_status(0, 2001, packet) == 248);
    assert(packet[34] == 1 && packet[40] == 0x04 && packet[41] == 0x20 && !(packet[33] & 2));
    diagnostics_state_codes_for(2, 3, filtered, sizeof(filtered), 2002);
    assert(diagnostics_endpoint_presentation_status(0, 2002, packet) == 248);
    assert(!diagnostic_filter_project(packet, 0, rules, 1).suppress_mil); /* Other categories still incompatible. */
    diagnostics_state_codes_for(2, 7, empty, sizeof(empty), 2002);
    diagnostics_state_codes_for(2, 10, empty, sizeof(empty), 2002);
    assert(diagnostics_endpoint_presentation_status(0, 2002, packet) == 248);
    assert(diagnostic_filter_project(packet, 0, rules, 1).suppress_mil);
    const uint8_t changed_mil[] = {0x82, 0, 0, 0};
    diagnostics_state_mil_payload(2, changed_mil, 2003);
    assert(diagnostics_endpoint_presentation_status(0, 2003, packet) == 248);
    assert(!diagnostic_filter_project(packet, 0, rules, 1).suppress_mil);
    diagnostics_state_codes_for(2, 3, new_fault, sizeof(new_fault), 2004);
    diagnostics_state_codes_for(2, 7, empty, sizeof(empty), 2004);
    diagnostics_state_codes_for(2, 10, empty, sizeof(empty), 2004);
    assert(diagnostics_endpoint_presentation_status(0, 2004, packet) == 248);
    diagnostic_filter_result_t projected = diagnostic_filter_project(packet, 0, rules, 1);
    assert(projected.complete && projected.visible_count == 1 && projected.hidden_count == 1 &&
           !projected.suppress_mil);
    /* A rejected partial category never replaces its last successful list. */
    diagnostics_state_codes_for(2, 3, filtered, 1, 2005);
    diagnostics_state_failed_for(2, 3, DIAGNOSTICS_UNAVAILABLE);
    assert(diagnostics_endpoint_status(0, 2005, packet) == 248);
    assert(packet[34] == 2 && packet[42] == 0x01 && packet[43] == 0x23 && packet[36] == (2004 & 255));
    assert(diagnostics_endpoint_presentation_status(0, 2005, packet) == 248);
    assert(diagnostic_filter_project(packet, 0, rules, 1).visible_count == 1);
    diagnostics_state_failed_for(2, 3, DIAGNOSTICS_UNSUPPORTED);
    assert(diagnostics_endpoint_presentation_status(0, 2006, packet) == 248);
    assert(!diagnostic_filter_project(packet, 0, rules, 1).complete);
    assert(diagnostics_endpoint_status(0, 2006, packet) == 248);
    assert(packet[32] == DIAGNOSTICS_UNSUPPORTED && packet[34] == 2);
    diagnostics_state_disconnected_for(2);
    diagnostics_state_snapshot_for(2, 2007, &snapshot);
    assert(!snapshot.valid);
    diagnostics_state_connected_for(2);
    assert(diagnostics_endpoint_presentation_status(0, 2008, packet) == 248);
    assert(packet[8] == 2 && packet[34] == 0 && !diagnostic_filter_project(packet, 0, rules, 1).suppress_mil);
    /* Explicit unsupported categories without contrary evidence permit known coverage. */
    diagnostics_state_mil_for(2, true, 1, 3000);
    diagnostics_state_codes_for(2, 3, filtered, sizeof(filtered), 3000);
    diagnostics_state_failed_for(2, 7, DIAGNOSTICS_UNSUPPORTED);
    diagnostics_state_codes_for(2, 10, empty, sizeof(empty), 3000);
    assert(diagnostics_endpoint_presentation_status(0, 3000, packet) == 248);
    assert(diagnostic_filter_project(packet, 0, rules, 1).suppress_mil);
    /* Unsupported cannot erase a previously observed unfiltered pending fault. */
    diagnostics_state_codes_for(2, 7, new_fault, sizeof(new_fault), 3001);
    diagnostics_state_failed_for(2, 7, DIAGNOSTICS_UNSUPPORTED);
    assert(diagnostics_endpoint_presentation_status(0, 3002, packet) == 248);
    projected = diagnostic_filter_project(packet, 0, rules, 1);
    assert(!projected.suppress_mil && !projected.complete);
    assert(diagnostics_endpoint_status(0, 3002, packet) == 248);
    assert(packet[104] == DIAGNOSTICS_UNSUPPORTED && packet[106] == 2 && packet[114] == 0x01);
    diagnostics_endpoint_configure(0, 1, 41, false);
    assert(diagnostics_endpoint_presentation_status(0, 3001, packet) == 248);
    assert(packet[4] == 41 && packet[8] == 0 && packet[34] == 0);
    assert(!diagnostic_filter_project(packet, 0, rules, 1).suppress_mil);
}
static void filtered_startup_retry_test(void)
{
    const uint8_t             filtered[] = {0xc1, 0x21, 0xc1, 0x40, 0x1d, 0xca, 0x1d, 0xf3};
    const uint8_t             empty[]    = {0, 0};
    const uint8_t             added[]    = {0xc1, 0x21, 0xc1, 0x40, 0x1d, 0xca, 0x1d, 0xf3, 0x01, 0x23};
    const diagnostic_filter_t rules[]    = {{0xc121, 1}, {0xc140, 1}, {0x1dca, 1}, {0x1df3, 1}};
    diagnostics_snapshot_t    snapshot;
    diagnostics_endpoint_configure(1, 1, 60, false);
    diagnostics_state_connected_for(3);
    diagnostics_state_snapshot_for(3, 1000, &snapshot);
    assert(!snapshot.valid && !snapshot.mil_known);
    diagnostics_state_mil_for(3, true, 4, 1000);
    diagnostics_state_failed_for(3, 3, DIAGNOSTICS_UNAVAILABLE);
    diagnostics_state_codes_for(3, 7, empty, sizeof(empty), 1000);
    diagnostics_state_failed_for(3, 10, DIAGNOSTICS_UNSUPPORTED);
    assert(diagnostics_endpoint_presentation_status(1, 1000, packet) == 248);
    diagnostic_filter_result_t loading = diagnostic_filter_project(packet, 1, rules, 4);
    assert(!loading.suppress_mil && !loading.show_mil && !loading.visible_count);
    assert(!diagnostic_filter_project(packet, 1, NULL, 0).show_mil);
    /* Raw ECU On remains intact while the gauge silently acquires code identity. */
    assert(diagnostics_endpoint_status(1, 1000, packet) == 248 && (packet[2] & 3) == 3);

    poll_scheduler_t scheduler;
    poll_scheduler_init(&scheduler);
    scheduler.seen_background    = 15;
    scheduler.settled_background = 13;
    for (unsigned i = 0; i < 4; ++i)
        scheduler.last_background[i] = 1000;
    poll_scheduler_diagnostic_complete(&scheduler, (poll_job_t){.kind = POLL_JOB_DTC}, 1000, POLL_DIAGNOSTIC_RETRY);
    const poll_scheduler_budget_t budget = {0};
    assert(poll_scheduler_next_budgeted(&scheduler, 2999, NULL, 0, &budget).kind == POLL_JOB_NONE);
    poll_job_t job = poll_scheduler_next_budgeted(&scheduler, 3000, NULL, 0, &budget);
    assert(job.kind == POLL_JOB_DTC && job.index == 0);
    diagnostics_state_codes_for(3, 3, filtered, sizeof(filtered), 3000);
    poll_scheduler_diagnostic_complete(&scheduler, job, 3000, POLL_DIAGNOSTIC_OK);
    assert(diagnostics_endpoint_presentation_status(1, 3000, packet) == 248);
    diagnostic_filter_result_t projected = diagnostic_filter_project(packet, 1, rules, 4);
    assert(projected.suppress_mil && !projected.show_mil && projected.hidden_count == 4 &&
           projected.visible_count == 0);

    diagnostics_state_mil_for(3, true, 5, 3100);
    diagnostics_state_codes_for(3, 3, added, sizeof(added), 3100);
    assert(diagnostics_endpoint_presentation_status(1, 3100, packet) == 248);
    projected = diagnostic_filter_project(packet, 1, rules, 4);
    assert(!projected.suppress_mil && projected.show_mil && projected.visible_count == 1);
}

static void filtered_refresh_gap_tests(void)
{
    const uint8_t filtered[] = {0xc1, 0x21, 0xc1, 0x40, 0x1d, 0xca, 0x1d, 0xf3};
    const uint8_t changed[]  = {0xc1, 0x21, 0xc1, 0x40, 0x1d, 0xca, 0x01, 0x23};
    const uint8_t empty[]    = {0, 0};
    for (unsigned endpoint = 0; endpoint < 2; ++endpoint)
    {
        unsigned                  source  = endpoint + 2;
        const diagnostic_filter_t rules[] = {{0xc121, endpoint},
                                             {0xc140, endpoint},
                                             {0x1dca, endpoint},
                                             {0x1df3, endpoint}};
        diagnostics_endpoint_configure(endpoint, endpoint, 61, false);
        diagnostics_state_connected_for(source);
        diagnostics_state_mil_for(source, true, 4, 1000);
        diagnostics_state_codes_for(source, 3, filtered, sizeof(filtered), 1000);
        diagnostics_state_codes_for(source, 7, empty, sizeof(empty), 1000);
        diagnostics_state_codes_for(source, 10, empty, sizeof(empty), 1000);
        /* A permanent refresh due at ten minutes can fail and back off. A fresh
         * identical MIL must not turn the four filtered faults into a new CEL. */
        diagnostics_state_failed_for(source, 10, DIAGNOSTICS_UNAVAILABLE);
        for (uint32_t now = 61000; now <= 901000; now += 60000)
        {
            diagnostics_state_mil_for(source, true, 4, now);
            assert(diagnostics_endpoint_status(endpoint, now + 1, packet) == 248);
            assert(!diagnostic_filter_project(packet, endpoint, rules, 4).suppress_mil);
            assert(diagnostics_endpoint_presentation_status(endpoint, now, packet) == 248);
            diagnostic_filter_result_t view = diagnostic_filter_project(packet, endpoint, rules, 4);
            assert(view.suppress_mil && view.hidden_count == 4 && !view.visible_count);
            assert(packet[36] == (1000 & 255)); /* Failure/refresh never re-dates lists. */
        }
        assert(diagnostics_endpoint_presentation_status(endpoint, 901001, packet) == 248);
        assert(!diagnostic_filter_project(packet, endpoint, rules, 4).suppress_mil);
        /* A same-count replacement by an unfiltered fault is visible immediately. */
        diagnostics_state_codes_for(source, 3, changed, sizeof(changed), 902000);
        assert(diagnostics_endpoint_presentation_status(endpoint, 902000, packet) == 248);
        diagnostic_filter_result_t view = diagnostic_filter_project(packet, endpoint, rules, 4);
        assert(!view.suppress_mil && view.visible_count == 1);
        diagnostics_state_codes_for(source, 3, filtered, sizeof(filtered), 903000);
        diagnostics_state_codes_for(source, 7, empty, sizeof(empty), 903000);
        diagnostics_state_codes_for(source, 10, empty, sizeof(empty), 903000);
        assert(diagnostics_endpoint_presentation_status(endpoint, 903000, packet) == 248);
        assert(diagnostic_filter_project(packet, endpoint, rules, 4).suppress_mil);
        diagnostics_state_mil_for(source, true, 5, 903001);
        assert(diagnostics_endpoint_presentation_status(endpoint, 903001, packet) == 248);
        assert(!diagnostic_filter_project(packet, endpoint, rules, 4).suppress_mil);
        diagnostics_state_disconnected_for(source);
        assert(diagnostics_endpoint_presentation_status(endpoint, 903002, packet) == 248);
        assert(!diagnostic_filter_project(packet, endpoint, rules, 4).suppress_mil);
        diagnostics_state_connected_for(source);
        uint32_t sample = UINT32_MAX - 1000;
        diagnostics_state_mil_for(source, true, 4, sample);
        diagnostics_state_codes_for(source, 3, filtered, sizeof(filtered), sample);
        diagnostics_state_codes_for(source, 7, empty, sizeof(empty), sample);
        diagnostics_state_codes_for(source, 10, empty, sizeof(empty), sample);
        uint32_t boundary = sample + 900000U;
        diagnostics_state_mil_for(source, true, 4, boundary);
        assert(diagnostics_endpoint_presentation_status(endpoint, boundary, packet) == 248);
        assert(diagnostic_filter_project(packet, endpoint, rules, 4).suppress_mil);
        assert(!diagnostic_filter_project(packet, endpoint, NULL, 0).suppress_mil);
        assert(diagnostics_endpoint_presentation_status(endpoint, boundary + 1, packet) == 248);
        assert(!diagnostic_filter_project(packet, endpoint, rules, 4).suppress_mil);
    }
}

static void shared_alert_availability_tests(void)
{
    const uint8_t codes[] = {0x01, 0x23};
    const uint8_t empty[] = {0, 0};
    for (unsigned endpoint = 0; endpoint < 2; ++endpoint)
    {
        unsigned source = endpoint + 2;
        diagnostics_endpoint_configure(endpoint, endpoint, 62, false);
        diagnostics_state_connected_for(source);
        alert_events_init(&continuity_events, 80);
        diagnostics_state_mil_for(source, true, 1, 1000);
        diagnostics_state_codes_for(source, 3, codes, sizeof(codes), 1000);
        diagnostics_state_codes_for(source, 7, empty, sizeof(empty), 1000);
        diagnostics_state_codes_for(source, 10, empty, sizeof(empty), 1000);
        uint32_t next_reply = 301500, last_reply = 1000;
        for (uint32_t now = 1000; now <= 605000; now += 100)
        {
            if (now == next_reply)
            {
                last_reply = now;
                diagnostics_state_mil_for(source, true, 1, now);
                next_reply += 300500;
            }
            assert(diagnostic_alerts_observe(endpoint, now, packet, alert_sink) == 248);
            assert(continuity_events.sequence == 2); /* No availability/recovery chatter. */
            assert(!continuity_events.episodes[32 + endpoint].current.unavailable);
            assert(continuity_events.episodes[32 + endpoint].current.observed_at == last_reply);
            assert(continuity_events.episodes[34 + endpoint].current.observed_at == 1000);
        }
        /* Raw result freshness still expires while a healthy reply is in flight. */
        assert(diagnostics_endpoint_status(endpoint, last_reply + 60001, packet) == 248);
        assert(!(packet[2] & 1));
        assert(diagnostic_alerts_observe(endpoint, last_reply + 60001, packet, alert_sink) == 248);
        assert(continuity_events.sequence == 2);
        test_lock_busy = 1;
        assert(diagnostic_alerts_observe(endpoint, last_reply + 60002, packet, alert_sink) == 0);
        assert(continuity_events.sequence == 2);
        test_lock_busy = 0;
        diagnostics_state_codes_for(source, 3, codes, sizeof(codes), last_reply + 600000);
        diagnostics_state_codes_for(source, 7, empty, sizeof(empty), last_reply + 600000);
        diagnostics_state_codes_for(source, 10, empty, sizeof(empty), last_reply + 600000);
        /* Real prolonged absence produces one unavailable event, keeping the
         * original observation time; further ticks do not fill the journal. */
        assert(diagnostic_alerts_observe(endpoint, last_reply + 600001, packet, alert_sink) == 248);
        assert(continuity_events.sequence == 3);
        assert(continuity_events.episodes[32 + endpoint].current.unavailable);
        assert(continuity_events.episodes[32 + endpoint].current.observed_at == last_reply);
        assert(diagnostic_alerts_observe(endpoint, last_reply + 600002, packet, alert_sink) == 248);
        assert(continuity_events.sequence == 3);
        diagnostics_state_mil_for(source, false, 0, last_reply + 600003);
        assert(diagnostic_alerts_observe(endpoint, last_reply + 600003, packet, alert_sink) == 248);
        assert(continuity_events.episodes[32 + endpoint].current.lifecycle == 0);
        diagnostics_state_mil_for(source, true, 1, last_reply + 600004);
        assert(diagnostic_alerts_observe(endpoint, last_reply + 600004, packet, alert_sink) == 248);
        assert(continuity_events.episodes[32 + endpoint].current.lifecycle == 1);
        assert(!continuity_events.episodes[32 + endpoint].current.unavailable);
        diagnostics_state_disconnected_for(source);
        assert(diagnostic_alerts_observe(endpoint, last_reply + 600005, packet, alert_sink) == 248);
        assert(continuity_events.episodes[32 + endpoint].current.unavailable);
    }
}

int main(int argc, char **argv)
{
    assert(diagnostics_state_init() == ESP_OK);
    diagnostics_state_configure(true, 28, false);
    diagnostics_state_connected();
    read_at(1000);
    assert(packet[0] == 15 && packet[1] == 1 && packet[20] == 0xe9 && packet[4] == 28 && packet[8] == 1);
    assert(packet[32] == 0 && packet[33] == 0);
    uint8_t codes[] = {0xc1, 0x21, 0xc1, 0x40, 0x1d, 0xca, 0x1d, 0xf3, 0xc1, 0x21, 0, 0};
    diagnostics_state_codes(3, codes, sizeof(codes), 900);
    diagnostics_state_codes(7, codes, 6, 900);
    diagnostics_state_codes(10, codes, 6, 900);
    diagnostics_state_mil(true, 4, 900);
    read_at(1000);
    assert(packet[2] == 7 && packet[34] == 4 && packet[106] == 3 && packet[178] == 3);
    assert(packet[40] == 0xc1 && packet[47] == 0xf3 && packet[48] == 0);
    if (argc == 2)
    {
        FILE *f = fopen(argv[1], "w");
        assert(f);
        for (unsigned i = 0; i < sizeof(packet); i++)
            fprintf(f, "%02x", packet[i]);
        fputs("\n", f);
        fclose(f);
    }
    diagnostics_state_codes(3, codes, 3, 1000);
    read_at(1000);
    assert(packet[34] == 4);
    uint8_t legacy[32];
    diagnostics_state_status(legacy);
    assert(legacy[1] == 0 && legacy[3] == 0);
    diagnostics_state_failed(3, DIAGNOSTICS_UNSUPPORTED);
    read_at(1100);
    assert(packet[32] == 3 && packet[33] == 1 && packet[34] == 4);
    diagnostics_state_disconnected();
    read_at(1200);
    assert(!(packet[2] & 5) && packet[33] == 1 && packet[34] == 4);
    diagnostics_state_connected();
    read_at(1300);
    assert(packet[8] == 2 && packet[34] == 0 && packet[33] == 0);
    uint8_t zero[2] = {0, 0};
    diagnostics_state_codes(3, zero, 2, 1300);
    read_at(1300);
    assert(packet[32] == 1 && packet[33] == 3 && packet[34] == 0);
    read_at(121301);
    assert(packet[33] == 1);
    diagnostics_state_configure(false, 29, false);
    diagnostics_state_connected();
    diagnostics_state_codes(3, codes, 8, UINT32_MAX - 50);
    read_at(25);
    assert(packet[33] == 3 && packet[1] == 0 && packet[20] == 0xe8);
    diagnostics_state_configure(true, 30, true);
    diagnostics_state_connected();
    diagnostics_state_codes(3, codes, 8, 900);
    read_at(1000);
    assert(packet[2] == 12 && packet[33] == 1);
    test_lock_busy = 1;
    assert(diagnostics_state_full_status(1000, packet) == 0);
    assert(diagnostics_state_status(legacy) == 0);
    diagnostics_snapshot_t unavailable;
    diagnostics_state_snapshot(1000, &unavailable);
    assert(!unavailable.valid);
    test_lock_busy = 0;
    read_at(1000);
    assert(packet[2] == 12);
    /* Independent controller evidence survives loss and reconnect of the other. */
    diagnostics_state_configure_for(0, false, 31, false);
    diagnostics_state_configure_for(1, true, 31, false);
    diagnostics_state_connected_for(0);
    diagnostics_state_connected_for(1);
    diagnostics_state_codes_for(0, 3, codes, 8, 900);
    diagnostics_state_codes_for(1, 3, codes, 4, 900);
    diagnostics_state_disconnected_for(1);
    assert(diagnostics_state_full_status_for(0, 1000, packet) == 248);
    assert(packet[2] == 4 && packet[33] == 3 && packet[34] == 4 && packet[1] == 0);
    assert(diagnostics_state_full_status_for(1, 1000, packet) == 248);
    assert(packet[2] == 0 && packet[33] == 1 && packet[34] == 2 && packet[1] == 1);
    diagnostics_state_connected_for(1);
    assert(diagnostics_state_full_status_for(1, 1000, packet) == 248 && packet[8] == 2 && packet[34] == 0);
    assert(diagnostics_state_full_status_for(0, 1000, packet) == 248 && packet[8] == 1 && packet[34] == 4);
    /* One physical link can carry independent engine and transmission endpoints. */
    diagnostics_endpoint_configure(0, 0, 32, false);
    diagnostics_endpoint_configure(1, 0, 32, false);
    diagnostics_state_connected_for(2);
    diagnostics_state_connected_for(3);
    uint8_t readiness[] = {0x82, 0x07, 0xaa, 0x55};
    diagnostics_state_mil_payload(2, readiness, 1000);
    diagnostics_state_codes_for(3, 3, codes, 4, 1000);
    assert(diagnostics_endpoint_status(0, 1000, packet) == 248 && packet[2] == 7 && packet[1] == 0);
    assert(diagnostics_endpoint_status(1, 1000, packet) == 248 && packet[2] == 4 && packet[34] == 2 && packet[1] == 1);
    diagnostics_state_failed_for(3, 1, DIAGNOSTICS_UNAVAILABLE);
    assert(diagnostics_endpoint_status(0, 1001, packet) == 248 && packet[2] == 7);
    uint8_t ready[24];
    assert(diagnostics_endpoint_readiness(0, 1001, ready) == 24 && ready[2] == 7 && !memcmp(ready + 20, readiness, 4));
    diagnostics_state_disconnected_for(3);
    diagnostics_snapshot_t vehicle;
    diagnostics_vehicle_snapshot(1002, &vehicle);
    assert(vehicle.valid && vehicle.mil_on);
    /* Retrieved unique count is separate from reported MIL count. */
    diagnostics_endpoint_configure(0, 0, 33, false);
    diagnostics_endpoint_configure(1, 0, 33, false);
    diagnostics_state_connected_for(2);
    diagnostics_state_connected_for(3);
    diagnostics_state_mil(true, 99, 2000);
    diagnostics_state_codes_for(2, 3, codes, 8, 2000);
    diagnostics_state_codes_for(2, 7, codes, 4, 2000);
    diagnostics_state_codes_for(2, 10, codes, 4, 2000);
    diagnostics_snapshot_t distinct;
    diagnostics_state_snapshot_for(2, 2001, &distinct);
    assert(distinct.distinct_count == 4 && distinct.count_known && !distinct.count_partial);
    diagnostics_vehicle_snapshot(2001, &vehicle);
    assert(vehicle.distinct_count == 4 && vehicle.count_partial);
    diagnostics_state_codes_for(3, 3, codes, 4, 2000);
    diagnostics_state_codes_for(3, 7, codes, 4, 2000);
    diagnostics_state_codes_for(3, 10, codes, 4, 2000);
    diagnostics_vehicle_snapshot(2001, &vehicle);
    assert(vehicle.distinct_count == 6 && !vehicle.count_partial);
    diagnostics_state_disconnected_for(3);
    diagnostics_vehicle_snapshot(2002, &vehicle);
    assert(vehicle.distinct_count == 4 && vehicle.count_partial);
    continuity_tests();
    filtered_startup_retry_test();
    filtered_refresh_gap_tests();
    shared_alert_availability_tests();
    puts("Diagnostics source, distinct count, coverage, full lists, expiry, reconnect and simulation passed");
}
