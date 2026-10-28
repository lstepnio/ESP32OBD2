#include "jeep_idle_driver.h"
#include "jeep_idle_fixture.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>

static const idle_context_t owner = {.revision = 7, .link = 9, .connected = true, .guards_fresh_and_met = true};
static uint32_t             now;
static jeep_idle_request_t  request(jeep_idle_kind_t kind, uint32_t operation)
{
    return (jeep_idle_request_t){.kind = kind, .operation = operation, .revision = 7, .link = 9, .source = 0};
}
static void begin(jeep_idle_driver_t *d, jeep_idle_kind_t kind)
{
    jeep_idle_driver_init(d);
    now = 100;
    assert(jeep_idle_driver_begin(d, request(kind, 1), owner, now));
}
static void reply(jeep_idle_driver_t *d, jeep_idle_command_t command, const char *text, idle_context_t context)
{
    for (size_t i = 0; i < strlen(text); ++i)
        assert(jeep_idle_driver_feed(d, command.ticket, (const uint8_t *)text + i, 1, context, now));
}
static jeep_idle_command_t issue(jeep_idle_driver_t *d, const char *text)
{
    jeep_idle_command_t c = jeep_idle_driver_next(d, owner, now++);
    assert(c.text && !strcmp(c.text, text) && c.ticket);
    assert(!jeep_idle_driver_next(d, owner, now).text);
    return c;
}
static void exchange(jeep_idle_driver_t *d, unsigned index)
{
    jeep_idle_command_t c = issue(d, capture[index].command);
    reply(d, c, capture[index].response, owner);
    ++now;
}
static void route(jeep_idle_driver_t *d)
{
    for (unsigned i = 0; i < 3; ++i)
        exchange(d, i);
}
static void before_start(jeep_idle_driver_t *d)
{
    route(d);
    exchange(d, 3);
    exchange(d, 4);
}
static void activate(jeep_idle_driver_t *d)
{
    begin(d, JEEP_IDLE_START_CAPTURED);
    before_start(d);
    exchange(d, 5);
    exchange(d, 6);
    assert(d->phase == JEEP_IDLE_CONFIRMED && d->result == JEEP_IDLE_START_CONFIRMED);
}
static void return_control(jeep_idle_driver_t *d)
{
    route(d);
    exchange(d, 7);
    /* A positive Return is not even protocol confirmation until the report
     * is inactive. This sequence is synthetic; that post-Stop read is untested. */
    assert(d->phase == JEEP_IDLE_WORKING && d->result == JEEP_IDLE_NO_RESULT);
    jeep_idle_command_t c = issue(d, "33 05 1");
    reply(d, c, capture[4].response, owner);
    assert(d->phase == JEEP_IDLE_CONFIRMED && d->result == JEEP_IDLE_RETURN_CONFIRMED);
}
static void happy_path_and_scope(void)
{
    jeep_idle_driver_t d;
    activate(&d);
    assert(!jeep_idle_driver_begin(&d, request(JEEP_IDLE_START_CAPTURED, 1), owner, now));
    assert(!jeep_idle_driver_begin(&d, request(JEEP_IDLE_START_CAPTURED, 2), owner, now));
    assert(!jeep_idle_driver_begin(&d, request(JEEP_IDLE_RETURN, 2), owner, now));
    idle_context_t other = owner;
    other.link++;
    jeep_idle_request_t r = request(JEEP_IDLE_RETURN, 1);
    r.link++;
    assert(!jeep_idle_driver_begin(&d, r, other, now));
    assert(jeep_idle_driver_begin(&d, request(JEEP_IDLE_REPORT, 1), owner, now));
    route(&d);
    exchange(&d, 6);
    assert(d.result == JEEP_IDLE_ACTIVE_REPORT);
    assert(jeep_idle_driver_begin(&d, request(JEEP_IDLE_RETURN, 1), owner, now));
    return_control(&d);
    assert(!jeep_idle_driver_begin(&d, request(JEEP_IDLE_RETURN, 1), owner, now));
    assert(!jeep_idle_driver_begin(&d, request(JEEP_IDLE_START_CAPTURED, 1), owner, now));
    assert(!jeep_idle_driver_begin(&d, request(JEEP_IDLE_START_CAPTURED, 2), owner, now));
    assert(!jeep_idle_driver_reconcile(&d, owner, false));
    assert(jeep_idle_driver_reconcile(&d, owner, true));
    assert(jeep_idle_driver_begin(&d, request(JEEP_IDLE_START_CAPTURED, 2), owner, now));
    before_start(&d);
    exchange(&d, 9);
    exchange(&d, 10);
    assert(d.result == JEEP_IDLE_START_CONFIRMED);
    assert(jeep_idle_driver_begin(&d, request(JEEP_IDLE_RETURN, 2), owner, now));
    route(&d);
    exchange(&d, 11);
    jeep_idle_command_t c = issue(&d, "33 05 1");
    reply(&d, c, capture[8].response, owner);
    assert(d.result == JEEP_IDLE_RETURN_CONFIRMED);
    assert(sizeof(d) < 768);
}
static void recovery_reports_never_write(void)
{
    for (unsigned step = 0; step < 4; ++step)
    {
        jeep_idle_driver_t d;
        begin(&d, JEEP_IDLE_RECOVERY_REPORT);
        d.control_possible = true; /* Even a discovered active controller cannot authorize Return. */
        for (unsigned i = 0; i < step; ++i)
            exchange(&d, i);
        jeep_idle_driver_cancel(&d);
        assert(!jeep_idle_driver_next(&d, owner, now).text && d.phase == JEEP_IDLE_CANCELLED);
    }
    for (unsigned active = 0; active < 2; ++active)
    {
        jeep_idle_driver_t d;
        begin(&d, JEEP_IDLE_RECOVERY_REPORT);
        route(&d);
        jeep_idle_command_t c             = issue(&d, "33 05 1");
        idle_context_t      failed_guard  = owner;
        failed_guard.guards_fresh_and_met = false;
        reply(&d, c, active ? "7E8 03 73 05 02\r>" : "7E8 03 73 05 01\r>", failed_guard);
        assert(d.phase == JEEP_IDLE_CONFIRMED);
        assert(!jeep_idle_driver_next(&d, owner, now).text);
        assert(d.request.kind == JEEP_IDLE_RECOVERY_REPORT);
    }
}
static void admission_and_cancellation(void)
{
    jeep_idle_driver_t d;
    jeep_idle_driver_init(&d);
    now                   = 1;
    jeep_idle_request_t r = request(JEEP_IDLE_START_CAPTURED, 1);
    r.source              = 1;
    assert(!jeep_idle_driver_begin(&d, r, owner, now));
    r.source = 0;
    r.kind   = (jeep_idle_kind_t)-1;
    assert(!jeep_idle_driver_begin(&d, r, owner, now));
    r.kind      = JEEP_IDLE_START_CAPTURED;
    r.operation = 0;
    assert(!jeep_idle_driver_begin(&d, r, owner, now));
    r.operation            = 1;
    idle_context_t c       = owner;
    c.guards_fresh_and_met = false;
    assert(!jeep_idle_driver_begin(&d, r, c, now));
    begin(&d, JEEP_IDLE_START_CAPTURED);
    jeep_idle_driver_cancel(&d);
    assert(!jeep_idle_driver_next(&d, owner, now).text && d.phase == JEEP_IDLE_CANCELLED);
    begin(&d, JEEP_IDLE_START_CAPTURED);
    jeep_idle_command_t command = issue(&d, capture[0].command);
    jeep_idle_driver_cancel(&d);
    reply(&d, command, capture[0].response, owner);
    assert(d.phase == JEEP_IDLE_CANCELLED);
    begin(&d, JEEP_IDLE_START_CAPTURED);
    before_start(&d);
    command = issue(&d, capture[5].command);
    jeep_idle_driver_cancel(&d);
    assert(!jeep_idle_driver_next(&d, owner, now).text);
    reply(&d, command, capture[5].response, owner);
    return_control(&d);
    begin(&d, JEEP_IDLE_START_CAPTURED);
    before_start(&d);
    exchange(&d, 5);
    command = issue(&d, capture[6].command);
    reply(&d, command, capture[6].response, c);
    assert(d.phase == JEEP_IDLE_WORKING && d.request.kind == JEEP_IDLE_RETURN);
    /* Return must still execute after a guard fails, on the original link. */
    for (unsigned i = 0; i < 3; ++i)
    {
        command = jeep_idle_driver_next(&d, c, now++);
        assert(!strcmp(command.text, capture[i].command));
        reply(&d, command, capture[i].response, c);
    }
    command = jeep_idle_driver_next(&d, c, now++);
    assert(!strcmp(command.text, "32 05 1"));
    reply(&d, command, capture[7].response, c);
    command = jeep_idle_driver_next(&d, c, now++);
    reply(&d, command, capture[4].response, c);
    assert(d.result == JEEP_IDLE_RETURN_CONFIRMED);
}
static void failures_and_deadlines(void)
{
    jeep_idle_driver_t d;
    begin(&d, JEEP_IDLE_START_CAPTURED);
    jeep_idle_command_t c = issue(&d, capture[0].command);
    assert(jeep_idle_driver_feed(&d, c.ticket, (const uint8_t *)"O", 1, owner, d.sent_at + 1199));
    assert(!jeep_idle_driver_next(&d, owner, d.sent_at + 1200).text);
    assert(d.phase == JEEP_IDLE_REJECTED && d.error == JEEP_IDLE_ERROR_TIMEOUT);
    assert(!jeep_idle_driver_feed(&d, c.ticket, (const uint8_t *)"K\r>", 3, owner, now));
    begin(&d, JEEP_IDLE_START_CAPTURED);
    before_start(&d);
    c = issue(&d, capture[5].command);
    jeep_idle_driver_next(&d, owner, d.sent_at + 1200);
    assert(d.phase == JEEP_IDLE_UNKNOWN && d.uncertain);
    assert(!jeep_idle_driver_begin(&d, request(JEEP_IDLE_START_CAPTURED, 2), owner, now));
    assert(!jeep_idle_driver_begin(&d, request(JEEP_IDLE_RETURN, 1), owner, now));
    assert(jeep_idle_driver_begin(&d, request(JEEP_IDLE_REPORT, 1), owner, now));
    route(&d);
    exchange(&d, 4);
    assert(d.result == JEEP_IDLE_INACTIVE_REPORT && d.uncertain);
    assert(!jeep_idle_driver_begin(&d, request(JEEP_IDLE_START_CAPTURED, 2), owner, now));
    assert(!jeep_idle_driver_reconcile(&d, owner, false));
    idle_context_t wrong = owner;
    wrong.revision++;
    assert(!jeep_idle_driver_reconcile(&d, wrong, true));
    assert(jeep_idle_driver_reconcile(&d, owner, true));
    assert(d.result == JEEP_IDLE_RECONCILED && !d.uncertain);
    assert(!jeep_idle_driver_begin(&d, request(JEEP_IDLE_START_CAPTURED, 1), owner, now));
    assert(jeep_idle_driver_begin(&d, request(JEEP_IDLE_START_CAPTURED, 2), owner, now));
    begin(&d, JEEP_IDLE_START_CAPTURED);
    before_start(&d);
    c                    = issue(&d, capture[5].command);
    idle_context_t other = owner;
    other.link++;
    assert(!jeep_idle_driver_feed(&d, c.ticket, (const uint8_t *)capture[5].response, strlen(capture[5].response),
                                  other, now));
    assert(d.phase == JEEP_IDLE_UNKNOWN);
    assert(!jeep_idle_driver_next(&d, owner, now).text);
    jeep_idle_request_t moved = request(JEEP_IDLE_REPORT, 1);
    moved.link                = other.link;
    assert(!jeep_idle_driver_begin(&d, moved, other, now));
    begin(&d, JEEP_IDLE_START_CAPTURED);
    before_start(&d);
    exchange(&d, 5);
    jeep_idle_driver_cancel(&d);
    now = d.started_at + JEEP_IDLE_JOB_TIMEOUT_MS;
    assert(!jeep_idle_driver_next(&d, owner, now).text && d.phase == JEEP_IDLE_UNKNOWN);
    begin(&d, JEEP_IDLE_START_CAPTURED);
    c = issue(&d, capture[0].command);
    assert(!jeep_idle_driver_feed(&d, c.ticket + 1, (const uint8_t *)"OK\r>", 4, owner, now));
    reply(&d, c, capture[0].response, owner);
    assert(!jeep_idle_driver_feed(&d, c.ticket, (const uint8_t *)"OK\r>", 4, owner, now));
    d.ticket = UINT32_MAX;
    assert(!jeep_idle_driver_next(&d, owner, now).text);
    assert(d.error == JEEP_IDLE_ERROR_BUDGET);
    jeep_idle_driver_init(&d);
    d.ticket = UINT32_MAX - 1;
    assert(!jeep_idle_driver_begin(&d, request(JEEP_IDLE_START_CAPTURED, 1), owner, now));
    begin(&d, JEEP_IDLE_START_CAPTURED);
    d.started_at = now = UINT32_MAX - 10;
    route(&d);
    exchange(&d, 3);
    exchange(&d, 4);
    exchange(&d, 5);
    exchange(&d, 6);
    assert(d.result == JEEP_IDLE_START_CONFIRMED);
}
static void malformed_and_negative(void)
{
    const char *bad[] = {"7E9 02 71 05\r>",
                         "7105\r>",
                         "7E8 03 71 05\r>",
                         "7E8 02 71 06\r>",
                         "7E8 02 71 05\r7E8 02 71 05\r>",
                         "7E8 02 71 05\rNO DATA\r>",
                         "7E8 02 71 05\rCAN ERROR\r>",
                         "NO DATA\r>",
                         "BUFFER FULL\r>",
                         "7E8 02 71 05\r>junk",
                         "7E8 03 7F 31 31\r>",
                         "7E8 03 7F 31 78\r>",
                         "7E8 10 08 71 05 00 00 00\r>"};
    for (size_t i = 0; i < sizeof(bad) / sizeof(*bad); ++i)
    {
        jeep_idle_driver_t d;
        begin(&d, JEEP_IDLE_START_CAPTURED);
        before_start(&d);
        jeep_idle_command_t c = issue(&d, capture[5].command);
        jeep_idle_driver_feed(&d, c.ticket, (const uint8_t *)bad[i], strlen(bad[i]), owner, now);
        assert(d.phase == JEEP_IDLE_UNKNOWN && !jeep_idle_driver_next(&d, owner, now).text);
        if (i == 10)
            assert(d.error == JEEP_IDLE_ERROR_NEGATIVE && d.negative_code == 0x31);
        if (i == 11)
            assert(d.error == JEEP_IDLE_ERROR_PENDING && d.negative_code == 0x78);
    }
    jeep_idle_driver_t d;
    begin(&d, JEEP_IDLE_START_CAPTURED);
    route(&d);
    jeep_idle_command_t c = issue(&d, "10 92 1");
    reply(&d, c, "7E8 03 7F 10 22\r>", owner);
    assert(d.phase == JEEP_IDLE_REJECTED && d.error == JEEP_IDLE_ERROR_NEGATIVE);
    begin(&d, JEEP_IDLE_START_CAPTURED);
    route(&d);
    exchange(&d, 3);
    c = issue(&d, "33 05 1");
    reply(&d, c, capture[6].response, owner);
    assert(d.phase == JEEP_IDLE_UNKNOWN);
    begin(&d, JEEP_IDLE_START_CAPTURED);
    before_start(&d);
    exchange(&d, 5);
    c = issue(&d, "33 05 1");
    reply(&d, c, capture[4].response, owner);
    assert(d.phase == JEEP_IDLE_UNKNOWN);
    begin(&d, JEEP_IDLE_RETURN);
    route(&d);
    exchange(&d, 7);
    c = issue(&d, "33 05 1");
    reply(&d, c, capture[6].response, owner);
    assert(d.phase == JEEP_IDLE_UNKNOWN);
    begin(&d, JEEP_IDLE_START_CAPTURED);
    c = issue(&d, capture[0].command);
    uint8_t huge[600];
    memset(huge, 'A', sizeof(huge));
    assert(!jeep_idle_driver_feed(&d, c.ticket, huge, sizeof(huge), owner, now));
    assert(d.phase == JEEP_IDLE_REJECTED);
    begin(&d, JEEP_IDLE_START_CAPTURED);
    c                   = issue(&d, capture[0].command);
    const uint8_t nul[] = {'O', 0, 'K', '>'};
    assert(!jeep_idle_driver_feed(&d, c.ticket, nul, sizeof(nul), owner, now));
    assert(d.phase == JEEP_IDLE_REJECTED);
}
static void physical_evidence_is_separate(void)
{
    jeep_idle_driver_t d;
    begin(&d, JEEP_IDLE_RETURN);
    return_control(&d);
    temporary_idle_t lifecycle;
    temporary_idle_init(&lifecycle);
    lifecycle.phase                = IDLE_UNKNOWN;
    lifecycle.definition.qualified = true;
    lifecycle.request.revision     = owner.revision;
    assert(!temporary_idle_reconcile(&lifecycle, owner, false));
    assert(lifecycle.phase == IDLE_UNKNOWN);
    /* No production bridge equates RETURN_CONFIRMED with settled RPM. Only a
     * separate qualified evidence owner could supply release_verified=true. */
    assert(d.result == JEEP_IDLE_RETURN_CONFIRMED);
}
static void lifecycle_fixture(void)
{
    /* Test-only mapping. A nominal 1700 fixture does not qualify the captured
     * 06 80 word as a production numeric-RPM conversion. */
    const idle_definition_t definition = {.qualified        = true,
                                          .minimum_rpm      = 1700,
                                          .maximum_rpm      = 1700,
                                          .rpm_step         = 1,
                                          .maximum_seconds  = 10,
                                          .refresh_ms       = 500,
                                          .io_timeout_ms    = 9000,
                                          .lease_timeout_ms = 15000};
    const idle_request_t    configured =
        {.operation = 1, .revision = 7, .link = 9, .rpm = 1700, .seconds = 10, .preset = 1};
    temporary_idle_t lifecycle;
    temporary_idle_init(&lifecycle);
    jeep_idle_driver_t d;
    jeep_idle_driver_init(&d);
    now = 100;
    assert(temporary_idle_start(&lifecycle, configured, definition, owner));
    idle_job_t job = temporary_idle_next(&lifecycle, owner, now);
    assert(job.kind == IDLE_START && job.rpm == 1700);
    assert(jeep_idle_driver_begin(&d, request(JEEP_IDLE_START_CAPTURED, job.operation), owner, now));
    before_start(&d);
    exchange(&d, 5);
    exchange(&d, 6);
    assert(d.result == JEEP_IDLE_START_CONFIRMED);
    assert(temporary_idle_complete(&lifecycle, job, true, owner, now));
    assert(lifecycle.phase == IDLE_ACTIVE);
    now += 500;
    job = temporary_idle_next(&lifecycle, owner, now);
    assert(job.kind == IDLE_REFRESH);
    assert(jeep_idle_driver_begin(&d, request(JEEP_IDLE_REPORT, job.operation), owner, now));
    route(&d);
    exchange(&d, 6);
    assert(d.result == JEEP_IDLE_ACTIVE_REPORT);
    assert(temporary_idle_complete(&lifecycle, job, true, owner, now));
    now = lifecycle.active_at + 10000;
    job = temporary_idle_next(&lifecycle, owner, now);
    assert(job.kind == IDLE_RELEASE && lifecycle.phase == IDLE_STOPPING);
    assert(jeep_idle_driver_begin(&d, request(JEEP_IDLE_RETURN, job.operation), owner, now));
    return_control(&d);
    assert(!temporary_idle_next(&lifecycle, owner, now).kind && lifecycle.phase == IDLE_STOPPING);
    assert(!jeep_idle_driver_reconcile(&d, owner, false));
    /* Only the independently simulated release evidence closes both owners. */
    assert(jeep_idle_driver_reconcile(&d, owner, true));
    assert(temporary_idle_complete(&lifecycle, job, true, owner, now));
    assert(lifecycle.phase == IDLE_RESTORED);
}
int main(void)
{
    recovery_reports_never_write();
    assert(!strcmp(capture[3].command, "10 92 1"));
    assert(!strcmp(capture[5].command, "31 05 06 80 1"));
    assert(!strcmp(capture[7].command, "32 05 1"));
    happy_path_and_scope();
    admission_and_cancellation();
    failures_and_deadlines();
    malformed_and_negative();
    physical_evidence_is_separate();
    lifecycle_fixture();
    printf("PASS: captured idle driver (%zu bytes), Return/report, cancellation, deadlines, scoped tickets, "
           "negative/malformed replies and verified reconciliation; no live IO\n",
           sizeof(jeep_idle_driver_t));
}
