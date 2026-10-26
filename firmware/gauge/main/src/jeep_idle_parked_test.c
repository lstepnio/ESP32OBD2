#include "jeep_idle_parked_test.h"
#include <string.h>

void jeep_idle_parked_test_scope(jeep_idle_parked_test_t *t, vehicle_command_context_t c)
{
    if (!c.connected || t->revision != c.revision || t->link != c.link)
    {
        memset(t, 0, sizeof(*t));
        t->revision = c.revision;
        t->link     = c.link;
    }
}

bool jeep_idle_parked_test_feed(jeep_idle_parked_test_t *t,
                                jeep_test_input_t        input,
                                const elm_payload_t     *p,
                                uint32_t                 now)
{
    bool valid = p && p->has_responder && p->responder == 0x7e8;
    switch (input)
    {
    case JEEP_TEST_CALIBRATION:
        t->calibration = valid && p->length == 16 && !memcmp(p->bytes, "68059434AD", 10);
        for (unsigned i = 10; t->calibration && i < 16; ++i)
            t->calibration = p->bytes[i] == 0 || p->bytes[i] == ' ';
        t->cvn = false;
        return t->calibration;
    case JEEP_TEST_CVN:
        t->cvn = t->calibration && valid && p->length == 4 && !memcmp(p->bytes, "\xca\x36\x37\x8c", 4);
        if (t->cvn)
            t->identity_at = now;
        return t->cvn;
    case JEEP_TEST_RPM:
        t->rpm_valid = valid && p->length == 2;
        if (t->rpm_valid)
        {
            t->rpm    = ((unsigned)p->bytes[0] * 256 + p->bytes[1]) / 4.0;
            t->rpm_at = now;
        }
        return t->rpm_valid;
    case JEEP_TEST_SPEED:
        t->speed_valid = valid && p->length == 1;
        if (t->speed_valid)
        {
            t->speed    = p->bytes[0];
            t->speed_at = now;
        }
        return t->speed_valid;
    }
    return false;
}

void jeep_idle_parked_test_context(jeep_idle_parked_test_t       *t,
                                   vehicle_command_context_t     *c,
                                   const vehicle_command_state_t *idle,
                                   uint32_t                       now)
{
    c->parked_idle_test = true;
    c->qualified_mask   = 0;
    uint32_t rpm_age = now - t->rpm_at, speed_age = now - t->speed_at;
    bool active = idle->phase == VEHICLE_STARTING || idle->phase == VEHICLE_ACTIVE || idle->phase == VEHICLE_STOPPING ||
                  idle->phase == VEHICLE_VERIFYING;
    c->identity = t->calibration && t->cvn && (active || now - t->identity_at <= 30000) ? 0xca36378cU : 0;
    c->identity_mask        = c->identity ? 1 : 0;
    c->guards_fresh_and_met = c->connected && c->identity && t->rpm_valid && t->speed_valid && t->rpm_at &&
                              t->speed_at && rpm_age <= JEEP_IDLE_PARKED_INPUT_AGE_MS &&
                              speed_age <= JEEP_IDLE_PARKED_INPUT_AGE_MS && !t->speed && t->rpm >= 400 &&
                              t->rpm <= (active ? 2500 : 1200);
    c->guard_at             = rpm_age > speed_age ? t->rpm_at : t->speed_at;
    c->normal_idle_verified = false;
    c->proof_at[0] = c->verified_operation[0] = 0;
    if (idle->phase != VEHICLE_VERIFYING || t->proof_operation != idle->request.operation ||
        t->proof_transition != idle->changed_at)
    {
        t->proof_count = 0;
        t->proof_first = t->proof_last = 0;
        t->proof_operation             = idle->request.operation;
        t->proof_transition            = idle->changed_at;
    }
    /* Only a positive Return/inactive driver result enters Verifying. Ordinary
     * RPM after an uncertain write/reboot must not clear the durable marker. */
    if (idle->phase != VEHICLE_VERIFYING || !c->guards_fresh_and_met || !t->baseline_valid ||
        t->baseline_operation != idle->request.operation || t->rpm > 1200 || t->rpm > t->baseline_rpm + 150 ||
        !t->rpm_at || (uint32_t)(t->rpm_at - idle->changed_at) >= 0x80000000U || t->rpm_at == idle->changed_at)
    {
        t->proof_count = 0;
        return;
    }
    if (t->proof_last != t->rpm_at)
    {
        if (!t->proof_count)
            t->proof_first = t->rpm_at;
        t->proof_last = t->rpm_at;
        if (t->proof_count < 3)
            ++t->proof_count;
    }
    if (t->proof_count >= 3 && t->proof_last - t->proof_first >= 500)
    {
        c->normal_idle_verified  = true;
        c->proof_at[0]           = t->proof_last;
        c->verified_operation[0] = idle->request.operation;
    }
}

void jeep_idle_parked_test_started(jeep_idle_parked_test_t *t, const vehicle_command_state_t *idle)
{
    t->baseline_operation = idle->request.operation;
    t->baseline_rpm       = t->rpm;
    t->baseline_valid     = t->rpm_valid;
    t->proof_count        = 0;
}
