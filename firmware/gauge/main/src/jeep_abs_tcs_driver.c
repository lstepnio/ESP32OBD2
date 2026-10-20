#include "jeep_abs_tcs_driver.h"
#include <string.h>

static const char *const route[] = {"AT SH 620", "AT FC SH 620", "AT CRA 504", "10 92 1"};
void                     jeep_abs_driver_init(jeep_abs_driver_t *d)
{
    memset(d, 0, sizeof(*d));
}
static void fail(jeep_abs_driver_t *d, jeep_idle_error_t error)
{
    d->phase   = d->control_possible ? JEEP_ABS_UNKNOWN : JEEP_ABS_REJECTED;
    d->error   = error;
    d->waiting = false;
}
static bool current(jeep_abs_driver_t *d, idle_context_t c, uint32_t now)
{
    if (!c.connected || c.revision != d->request.revision || c.link != d->request.link)
    {
        fail(d, JEEP_IDLE_ERROR_CONTEXT);
        return false;
    }
    if (now - d->started_at >= JEEP_IDLE_JOB_TIMEOUT_MS || (d->waiting && now - d->sent_at >= JEEP_IDLE_IO_TIMEOUT_MS))
    {
        fail(d, JEEP_IDLE_ERROR_TIMEOUT);
        return false;
    }
    if (!c.guards_fresh_and_met && d->request.kind == JEEP_ABS_DISABLE)
        d->cancel_requested = true;
    return true;
}
bool jeep_abs_driver_begin(jeep_abs_driver_t *d, jeep_abs_request_t r, idle_context_t c, uint32_t now)
{
    if (d->phase == JEEP_ABS_WORKING || d->phase == JEEP_ABS_UNKNOWN || (unsigned)r.kind > JEEP_ABS_RETURN ||
        r.source || !r.operation || !r.revision || !r.link || !c.connected || c.revision != r.revision ||
        c.link != r.link || d->ticket > UINT32_MAX - 8U)
        return false;
    if (r.kind == JEEP_ABS_DISABLE &&
        (!c.guards_fresh_and_met || d->control_possible || r.operation <= d->last_disable))
        return false;
    if (r.kind == JEEP_ABS_RETURN && r.operation <= d->last_return)
        return false;
    if (d->control_possible &&
        (r.operation != d->request.operation || r.revision != d->request.revision || r.link != d->request.link))
        return false;
    if (r.kind == JEEP_ABS_DISABLE)
        d->last_disable = r.operation;
    else
        d->last_return = r.operation;
    d->request = r;
    d->phase   = JEEP_ABS_WORKING;
    d->error   = JEEP_IDLE_ERROR_NONE;
    d->step = d->negative_code = 0;
    d->waiting = d->cancel_requested = false;
    d->started_at                    = now;
    return true;
}
void jeep_abs_driver_cancel(jeep_abs_driver_t *d)
{
    if (d->phase == JEEP_ABS_WORKING)
        d->cancel_requested = true;
}
jeep_idle_command_t jeep_abs_driver_next(jeep_abs_driver_t *d, idle_context_t c, uint32_t now)
{
    jeep_idle_command_t none = {0};
    if (d->phase != JEEP_ABS_WORKING || !current(d, c, now) || d->waiting)
        return none;
    if (d->cancel_requested && d->request.kind == JEEP_ABS_DISABLE)
    {
        d->phase = d->control_possible ? JEEP_ABS_UNKNOWN : JEEP_ABS_CANCELLED;
        return none;
    }
    const char *text = d->step < 4 ? route[d->step] : d->request.kind == JEEP_ABS_DISABLE ? "31 A4" : "32 A4";
    d->waiting       = true;
    d->sent_at       = now;
    elm_response_reset(&d->response);
    if (d->step == 4)
        d->control_possible = true;
    return (jeep_idle_command_t){text, ++d->ticket};
}
static void complete(jeep_abs_driver_t *d)
{
    d->waiting = false;
    if (d->step < 3)
    {
        size_t begin = 0, end = d->response.length;
        while (begin < end &&
               (d->response.text[begin] == ' ' || d->response.text[begin] == '\r' || d->response.text[begin] == '\n'))
            ++begin;
        while (end > begin && (d->response.text[end - 1] == ' ' || d->response.text[end - 1] == '\r' ||
                               d->response.text[end - 1] == '\n'))
            --end;
        if (end - begin != 2 || memcmp(d->response.text + begin, "OK", 2))
            fail(d, JEEP_IDLE_ERROR_FRAMING);
        else
            ++d->step;
        return;
    }
    elm_payload_t p;
    uint8_t       service = d->step == 3 ? 0x10 : d->request.kind == JEEP_ABS_DISABLE ? 0x31 : 0x32;
    if (elm_response_decode_control_frame(&d->response, 0x504, &p) != ELM_OK)
    {
        fail(d, JEEP_IDLE_ERROR_FRAMING);
        return;
    }
    if (p.length == 3 && p.bytes[0] == 0x7f && p.bytes[1] == service)
    {
        d->negative_code = p.bytes[2];
        fail(d, p.bytes[2] == 0x78 ? JEEP_IDLE_ERROR_PENDING : JEEP_IDLE_ERROR_NEGATIVE);
        return;
    }
    if (p.length != 2 || p.bytes[0] != service + 0x40 || p.bytes[1] != (d->step == 3 ? 0x92 : 0xa4))
    {
        fail(d, JEEP_IDLE_ERROR_FRAMING);
        return;
    }
    if (d->step == 3)
        ++d->step;
    else
        d->phase = JEEP_ABS_ACKNOWLEDGED;
}
bool jeep_abs_driver_feed(jeep_abs_driver_t *d,
                          uint32_t           ticket,
                          const uint8_t     *bytes,
                          size_t             length,
                          idle_context_t     c,
                          uint32_t           now)
{
    if (d->phase != JEEP_ABS_WORKING || !d->waiting || ticket != d->ticket || !bytes || !length)
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
            return true;
        }
    }
    if (prompt)
        complete(d);
    return true;
}
