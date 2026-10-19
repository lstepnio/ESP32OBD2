#include "jeep_idle_driver.h"
#include <string.h>

enum
{
    ROUTE,
    FLOW_ROUTE,
    FILTER,
    SESSION,
    BEFORE_START,
    START,
    AFTER_START,
    RETURN,
    AFTER_RETURN,
    REPORT
};
static const char *const commands[] = {"AT SH 7E0",     "AT FC SH 7E0", "AT CRA 7E8", "10 92 1", "33 05 1",
                                       "31 05 06 80 1", "33 05 1",      "32 05 1",    "33 05 1", "33 05 1"};

void jeep_idle_driver_init(jeep_idle_driver_t *d)
{
    memset(d, 0, sizeof(*d));
}

static void fail(jeep_idle_driver_t *d, jeep_idle_error_t error)
{
    d->phase = d->control_possible || d->uncertain ? JEEP_IDLE_UNKNOWN : JEEP_IDLE_REJECTED;
    d->uncertain |= d->phase == JEEP_IDLE_UNKNOWN;
    d->error   = error;
    d->result  = JEEP_IDLE_NO_RESULT;
    d->waiting = false;
}

static bool current(jeep_idle_driver_t *d, idle_context_t c, uint32_t now)
{
    if (!c.connected || c.revision != d->request.revision || c.link != d->request.link)
    {
        /* A changed endpoint cannot prove the old controller's state. */
        d->uncertain = true;
        fail(d, JEEP_IDLE_ERROR_CONTEXT);
        return false;
    }
    if (now - d->started_at >= JEEP_IDLE_JOB_TIMEOUT_MS || (d->waiting && now - d->sent_at >= JEEP_IDLE_IO_TIMEOUT_MS))
    {
        fail(d, JEEP_IDLE_ERROR_TIMEOUT);
        return false;
    }
    if (!c.guards_fresh_and_met && d->request.kind != JEEP_IDLE_RETURN)
    {
        d->cancel_requested = true;
        d->error            = JEEP_IDLE_ERROR_GUARD;
    }
    return true;
}

bool jeep_idle_driver_begin(jeep_idle_driver_t *d, jeep_idle_request_t r, idle_context_t c, uint32_t now)
{
    if (d->phase == JEEP_IDLE_WORKING || (unsigned)r.kind > JEEP_IDLE_RETURN || !r.operation || !r.revision ||
        !r.link || r.source != 0 || !c.connected || c.revision != r.revision || c.link != r.link ||
        r.operation < d->last_operation || d->ticket > UINT32_MAX - 16U)
        return false;
    if ((d->control_possible || d->uncertain) &&
        (r.operation != d->request.operation || r.link != d->request.link || r.revision != d->request.revision))
        return false;
    if (r.kind == JEEP_IDLE_START_CAPTURED &&
        (!c.guards_fresh_and_met || d->uncertain || d->control_possible || r.operation <= d->last_start_operation))
        return false;
    /* A failed/uncertain Return is not replayed here. The lifecycle owner needs
     * separately qualified idempotence/reconciliation before another release. */
    if (r.kind == JEEP_IDLE_RETURN && (d->uncertain || r.operation <= d->last_return_operation))
        return false;
    if (r.kind == JEEP_IDLE_START_CAPTURED)
        d->last_start_operation = r.operation;
    d->last_operation = r.operation;
    d->request        = r;
    d->phase          = JEEP_IDLE_WORKING;
    d->result         = JEEP_IDLE_NO_RESULT;
    d->error          = JEEP_IDLE_ERROR_NONE;
    d->negative_code  = 0;
    d->step           = ROUTE;
    d->started_at     = now;
    d->waiting = d->cancel_requested = false;
    return true;
}

void jeep_idle_driver_cancel(jeep_idle_driver_t *d)
{
    if (d->phase == JEEP_IDLE_WORKING)
        d->cancel_requested = true;
}

static void cancellation(jeep_idle_driver_t *d)
{
    if (!d->cancel_requested || d->request.kind == JEEP_IDLE_RETURN)
        return;
    if (!d->control_possible)
    {
        d->phase = JEEP_IDLE_CANCELLED;
        return;
    }
    d->request.kind = JEEP_IDLE_RETURN;
    d->step         = ROUTE;
}

jeep_idle_command_t jeep_idle_driver_next(jeep_idle_driver_t *d, idle_context_t c, uint32_t now)
{
    jeep_idle_command_t none = {0};
    if (d->phase != JEEP_IDLE_WORKING || !current(d, c, now) || d->waiting)
        return none;
    cancellation(d);
    if (d->phase != JEEP_IDLE_WORKING)
        return none;
    if (d->ticket == UINT32_MAX)
    {
        fail(d, JEEP_IDLE_ERROR_BUDGET);
        return none;
    }
    d->waiting = true;
    d->sent_at = now;
    elm_response_reset(&d->response);
    if (d->step == START || d->step == RETURN)
        d->control_possible = true;
    if (d->step == RETURN)
        d->last_return_operation = d->request.operation;
    return (jeep_idle_command_t){commands[d->step], ++d->ticket};
}

static bool adapter_ok(const elm_response_t *r)
{
    size_t begin = 0, end = r->length;
    while (begin < end && (r->text[begin] == '\r' || r->text[begin] == '\n' || r->text[begin] == ' '))
        ++begin;
    while (end > begin && (r->text[end - 1] == '\r' || r->text[end - 1] == '\n' || r->text[end - 1] == ' '))
        --end;
    return !r->overflow && end - begin == 2 && !memcmp(r->text + begin, "OK", 2);
}

static void confirmed(jeep_idle_driver_t *d, jeep_idle_result_t result)
{
    d->phase  = JEEP_IDLE_CONFIRMED;
    d->result = result;
}

static void complete(jeep_idle_driver_t *d)
{
    d->waiting = false;
    if (d->step <= FILTER)
    {
        if (!adapter_ok(&d->response))
        {
            fail(d, JEEP_IDLE_ERROR_FRAMING);
            return;
        }
        if (d->step < FILTER)
            ++d->step;
        else
            d->step = d->request.kind == JEEP_IDLE_START_CAPTURED ? SESSION
                      : d->request.kind == JEEP_IDLE_RETURN       ? RETURN
                                                                  : REPORT;
        return;
    }
    elm_payload_t p;
    if (elm_response_decode_control_frame(&d->response, 0x7e8, &p) != ELM_OK)
    {
        fail(d, JEEP_IDLE_ERROR_FRAMING);
        return;
    }
    uint8_t service = d->step == SESSION ? 0x10 : d->step == START ? 0x31 : d->step == RETURN ? 0x32 : 0x33;
    if (p.length == 3 && p.bytes[0] == 0x7f && p.bytes[1] == service)
    {
        d->negative_code = p.bytes[2];
        fail(d, p.bytes[2] == 0x78 ? JEEP_IDLE_ERROR_PENDING : JEEP_IDLE_ERROR_NEGATIVE);
        return;
    }
    bool report = service == 0x33;
    if (p.length != (report ? 3U : 2U) || p.bytes[0] != service + 0x40 || p.bytes[1] != (service == 0x10 ? 0x92 : 5))
    {
        fail(d, JEEP_IDLE_ERROR_FRAMING);
        return;
    }
    if (report && p.bytes[2] != 1 && p.bytes[2] != 2)
    {
        d->uncertain = true;
        fail(d, JEEP_IDLE_ERROR_STATE);
        return;
    }
    switch (d->step)
    {
    case SESSION:
        d->step = BEFORE_START;
        break;
    case BEFORE_START:
        if (p.bytes[2] != 1)
        {
            d->uncertain = true;
            fail(d, JEEP_IDLE_ERROR_STATE);
            return;
        }
        d->step = START;
        break;
    case START:
        d->step = AFTER_START;
        break;
    case AFTER_START:
        if (p.bytes[2] != 2)
        {
            fail(d, JEEP_IDLE_ERROR_STATE);
            return;
        }
        confirmed(d, JEEP_IDLE_START_CONFIRMED);
        break;
    case RETURN:
        d->step = AFTER_RETURN;
        break;
    case AFTER_RETURN:
        if (p.bytes[2] != 1)
        {
            fail(d, JEEP_IDLE_ERROR_STATE);
            return;
        }
        /* Protocol release is not settled physical idle. Keep Start fenced
         * until the qualified evidence owner explicitly reconciles it. */
        confirmed(d, JEEP_IDLE_RETURN_CONFIRMED);
        break;
    case REPORT:
        if (p.bytes[2] == 2)
            d->control_possible = true;
        confirmed(d, p.bytes[2] == 2 ? JEEP_IDLE_ACTIVE_REPORT : JEEP_IDLE_INACTIVE_REPORT);
        break;
    }
}

bool jeep_idle_driver_feed(jeep_idle_driver_t *d,
                           uint32_t            ticket,
                           const uint8_t      *bytes,
                           size_t              length,
                           idle_context_t      c,
                           uint32_t            now)
{
    if (d->phase != JEEP_IDLE_WORKING || !d->waiting || ticket != d->ticket || !bytes || !length)
        return false;
    if (!current(d, c, now))
        return false;
    bool prompt = false;
    for (size_t i = 0; i < length; ++i)
    {
        if (prompt || !bytes[i] || bytes[i] > 127)
        {
            fail(d, JEEP_IDLE_ERROR_FRAMING);
            return false;
        }
        prompt = elm_response_push(&d->response, bytes[i]);
        if (d->response.overflow)
        {
            fail(d, JEEP_IDLE_ERROR_FRAMING);
            return false;
        }
    }
    if (prompt)
    {
        complete(d);
        if (d->cancel_requested && d->phase == JEEP_IDLE_CONFIRMED &&
            (d->result == JEEP_IDLE_START_CONFIRMED || d->result == JEEP_IDLE_ACTIVE_REPORT))
        {
            d->phase  = JEEP_IDLE_WORKING;
            d->result = JEEP_IDLE_NO_RESULT;
        }
        if (d->phase == JEEP_IDLE_WORKING)
            cancellation(d);
    }
    return true;
}

bool jeep_idle_driver_reconcile(jeep_idle_driver_t *d, idle_context_t c, bool verified)
{
    if ((!d->uncertain && !d->control_possible) || d->phase == JEEP_IDLE_WORKING || !verified || !c.connected ||
        !c.link || c.revision != d->request.revision)
        return false;
    d->uncertain = d->control_possible = false;
    d->phase                           = JEEP_IDLE_CONFIRMED;
    d->result                          = JEEP_IDLE_RECONCILED;
    d->error                           = JEEP_IDLE_ERROR_NONE;
    d->request.link                    = c.link;
    return true;
}
