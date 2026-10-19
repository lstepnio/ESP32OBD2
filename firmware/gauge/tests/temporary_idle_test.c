#include "temporary_idle.h"
#include "page_action.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
static const idle_definition_t fixture = {.qualified          = true,
                                          .release_idempotent = true,
                                          .minimum_rpm        = 1000,
                                          .maximum_rpm        = 1600,
                                          .rpm_step           = 50,
                                          .maximum_seconds    = 600,
                                          .refresh_ms         = 500,
                                          .io_timeout_ms      = 200,
                                          .lease_timeout_ms   = 2000};
static const idle_context_t    context = {.revision = 7, .link = 9, .connected = true, .guards_fresh_and_met = true};
static idle_request_t          request(uint32_t op)
{
    return (idle_request_t){.operation = op, .revision = 7, .link = 9, .rpm = 1200, .seconds = 10, .preset = 1};
}
static idle_job_t start(temporary_idle_t *s, uint32_t at)
{
    temporary_idle_init(s);
    assert(temporary_idle_start(s, request(1), fixture, context));
    idle_job_t j = temporary_idle_next(s, context, at);
    assert(j.kind == IDLE_START);
    assert(temporary_idle_next(s, context, at + 1).kind == IDLE_NO_JOB);
    assert(temporary_idle_complete(s, j, true, context, at + 10));
    assert(s->phase == IDLE_ACTIVE);
    return j;
}
int main(void)
{
    temporary_idle_t s;
    temporary_idle_init(&s);
    assert(!temporary_idle_start(&s, request(1), (idle_definition_t){0}, context));
    assert(s.reason == IDLE_UNSUPPORTED && temporary_idle_next(&s, context, 0).kind == IDLE_NO_JOB);
    assert(!temporary_idle_start(&s, request(1), fixture, context));
    idle_request_t invalid = request(2);
    invalid.rpm            = 1201;
    assert(!temporary_idle_start(&s, invalid, fixture, context));
    idle_context_t bad       = context;
    bad.guards_fresh_and_met = false;
    assert(!temporary_idle_start(&s, request(3), fixture, bad));
    assert(s.reason == IDLE_GUARD);
    bad = context;
    bad.link++;
    assert(!temporary_idle_start(&s, request(4), fixture, bad));
    assert(s.reason == IDLE_CONTEXT);
    idle_job_t old = start(&s, 100);
    assert(!temporary_idle_start(&s, request(2), fixture, context));
    assert(temporary_idle_remaining(&s, 110) == 10);
    idle_job_t refresh = temporary_idle_next(&s, context, 610);
    assert(refresh.kind == IDLE_REFRESH);
    assert(!temporary_idle_complete(&s, old, true, context, 611));
    assert(temporary_idle_stop(&s, 1));
    assert(temporary_idle_complete(&s, refresh, true, context, 620));
    idle_job_t stop = temporary_idle_next(&s, context, 621);
    assert(stop.kind == IDLE_RELEASE);
    assert(temporary_idle_complete(&s, stop, true, context, 630));
    assert(s.phase == IDLE_RESTORED);
    assert(!temporary_idle_start(&s, request(1), fixture, context));
    assert(temporary_idle_start(&s, request(2), fixture, context));
    assert(temporary_idle_stop(&s, 2));
    assert(temporary_idle_next(&s, context, 640).kind == IDLE_NO_JOB && s.phase == IDLE_RESTORED);
    start(&s, 100);
    bad                      = context;
    bad.guards_fresh_and_met = false;
    stop                     = temporary_idle_next(&s, bad, 120);
    assert(stop.kind == IDLE_RELEASE);
    assert(temporary_idle_complete(&s, stop, false, context, 130));
    assert(s.phase == IDLE_UNKNOWN);
    assert(temporary_idle_next(&s, context, 140).kind == IDLE_NO_JOB);
    assert(!temporary_idle_start(&s, request(2), fixture, context));
    assert(!temporary_idle_reconcile(&s, context, false));
    assert(temporary_idle_reconcile(&s, context, true));
    start(&s, 100);
    assert(!temporary_idle_next(&s, context, 900).kind && s.phase == IDLE_UNKNOWN);
    assert(temporary_idle_stop(&s, 1));
    stop = temporary_idle_next(&s, context, 901);
    assert(stop.kind == IDLE_RELEASE);
    assert(temporary_idle_complete(&s, stop, true, context, 902));
    assert(s.phase == IDLE_RESTORED);
    start(&s, 100);
    bad = context;
    bad.link++;
    assert(!temporary_idle_next(&s, bad, 120).kind && s.phase == IDLE_UNKNOWN);
    start(&s, 100);
    refresh = temporary_idle_next(&s, context, 610);
    assert(!temporary_idle_next(&s, context, 810).kind && s.phase == IDLE_UNKNOWN);
    assert(!temporary_idle_complete(&s, refresh, true, context, 811));
    temporary_idle_init(&s);
    assert(temporary_idle_start(&s, request(1), fixture, context));
    old = temporary_idle_next(&s, context, 0);
    assert(!temporary_idle_complete(&s, old, true, context, 200) && s.phase == IDLE_UNKNOWN);
    start(&s, UINT32_MAX - 100);
    assert(temporary_idle_remaining(&s, 20) == 10);
    refresh = temporary_idle_next(&s, context, 420);
    assert(refresh.kind == IDLE_REFRESH);
    assert(temporary_idle_complete(&s, refresh, true, context, 430));
    start(&s, 0);
    for (uint32_t t = 510; t < 10010; t += 500)
    {
        refresh = temporary_idle_next(&s, context, t);
        assert(refresh.kind == IDLE_REFRESH);
        assert(temporary_idle_complete(&s, refresh, true, context, t));
    }
    stop = temporary_idle_next(&s, context, 10010);
    assert(stop.kind == IDLE_RELEASE);
    assert(temporary_idle_complete(&s, stop, true, context, 10011));
    assert(s.phase == IDLE_RESTORED);
    /* The existing recognizer emits an intent; no gesture code sends IO. */
    temporary_idle_init(&s);
    page_action_t gesture;
    page_action_init(&gesture, (page_action_config_t){.enabled = true, .count = 3, .window_ms = 5000});
    for (unsigned i = 0; i < 3; i++)
    {
        bool fired = false;
        page_action_press(&gesture, 120, 180, i * 300);
        assert(page_action_release(&gesture, 120, 100, i * 300 + 100, &fired));
        if (fired)
            assert(temporary_idle_start(&s, request(1), fixture, context));
        assert(fired == (i == 2));
    }
    assert(temporary_idle_next(&s, context, 800).kind == IDLE_START);
    bad = context;
    bad.link++;
    assert(!temporary_idle_complete(&s, s.in_flight, true, bad, 810));
    assert(s.phase == IDLE_UNKNOWN);
    uint8_t wire[24], cmd = 0;
    FILE   *vector = fopen("contracts/examples/temporary-idle-start.hex", "r");
    assert(vector);
    for (size_t i = 0; i < sizeof(wire); i++)
    {
        unsigned byte;
        assert(fscanf(vector, "%2x", &byte) == 1 && byte <= 255);
        wire[i] = (uint8_t)byte;
    }
    fclose(vector);
    idle_request_t r;
    assert(temporary_idle_decode(wire, 24, &cmd, &r) && cmd == 1 && r.rpm == 1200);
    for (size_t n = 0; n < 24; n++)
        assert(!temporary_idle_decode(wire, n, &cmd, &r));
    wire[3] = 1;
    assert(!temporary_idle_decode(wire, 24, &cmd, &r));
    wire[3]  = 0;
    wire[20] = 1;
    assert(!temporary_idle_decode(wire, 24, &cmd, &r));
    wire[20] = 0;
    wire[1]  = 2;
    assert(!temporary_idle_decode(wire, 24, &cmd, &r));
    memset(wire + 16, 0, 4);
    wire[2] = 0;
    assert(temporary_idle_decode(wire, 24, &cmd, &r));
    puts("PASS: idle worker lifecycle, duplicate and stale-context isolation, cancellation, timeouts, expiry, clock "
         "wrap and inner codec");
}
