#include "jeep_idle_parked_test.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>

static elm_payload_t payload(const uint8_t *bytes, size_t length)
{
    elm_payload_t p = {.length = length, .has_responder = true, .responder = 0x7e8};
    memcpy(p.bytes, bytes, length);
    return p;
}
static void rpm(jeep_idle_parked_test_t *t, unsigned value, uint32_t at)
{
    uint8_t       bytes[] = {(value * 4) >> 8, (value * 4) & 255};
    elm_payload_t p       = payload(bytes, 2);
    assert(jeep_idle_parked_test_feed(t, JEEP_TEST_RPM, &p, at));
}
static void speed(jeep_idle_parked_test_t *t, uint8_t value, uint32_t at)
{
    elm_payload_t p = payload(&value, 1);
    assert(jeep_idle_parked_test_feed(t, JEEP_TEST_SPEED, &p, at));
}
static void identity(jeep_idle_parked_test_t *t)
{
    elm_payload_t p = payload((const uint8_t *)"68059434AD\0\0\0\0\0\0", 16);
    assert(jeep_idle_parked_test_feed(t, JEEP_TEST_CALIBRATION, &p, 100));
    p = payload((const uint8_t *)"\xca\x36\x37\x8c", 4);
    assert(jeep_idle_parked_test_feed(t, JEEP_TEST_CVN, &p, 110));
}
static void identity_and_guards(void)
{
    jeep_idle_parked_test_t   t    = {0};
    vehicle_command_context_t c    = {.revision = 7, .link = 9, .connected = true};
    vehicle_command_state_t   idle = {0};
    jeep_idle_parked_test_scope(&t, c);
    identity(&t);
    rpm(&t, 700, 120);
    speed(&t, 0, 130);
    jeep_idle_parked_test_context(&t, &c, &idle, 140);
    assert(c.parked_idle_test && c.identity == 0xca36378cU && c.identity_mask == 1 && !c.qualified_mask);
    assert(c.guards_fresh_and_met && c.guard_at == 120);
    jeep_idle_parked_test_context(&t, &c, &idle, 30111);
    assert(!c.identity);
    jeep_idle_parked_test_context(&t, &c, &idle, 140);
    jeep_idle_parked_test_context(&t, &c, &idle, 2121);
    assert(!c.guards_fresh_and_met);
    rpm(&t, 700, 2200);
    speed(&t, 1, 2200);
    jeep_idle_parked_test_context(&t, &c, &idle, 2200);
    assert(!c.guards_fresh_and_met);
    speed(&t, 0, 2210);
    rpm(&t, 1700, 2210);
    jeep_idle_parked_test_context(&t, &c, &idle, 2210);
    assert(!c.guards_fresh_and_met); /* Raised RPM cannot establish a baseline. */
    idle.phase = VEHICLE_ACTIVE;
    jeep_idle_parked_test_context(&t, &c, &idle, 2210);
    assert(c.guards_fresh_and_met);
    assert(!jeep_idle_parked_test_feed(&t, JEEP_TEST_SPEED, NULL, 2220));
    jeep_idle_parked_test_context(&t, &c, &idle, 2220);
    assert(!c.guards_fresh_and_met);
    c.link++;
    jeep_idle_parked_test_scope(&t, c);
    jeep_idle_parked_test_context(&t, &c, &idle, 2230);
    assert(!c.identity && !t.baseline_valid && !c.guards_fresh_and_met);
    identity(&t);
    elm_payload_t wrong = payload((const uint8_t *)"68059434AE\0\0\0\0\0\0", 16);
    assert(!jeep_idle_parked_test_feed(&t, JEEP_TEST_CALIBRATION, &wrong, 2240));
    assert(!t.cvn);
    identity(&t);
    wrong = payload((const uint8_t *)"\xca\x36\x37\x8d", 4);
    assert(!jeep_idle_parked_test_feed(&t, JEEP_TEST_CVN, &wrong, 2250));
    wrong.responder = 0x7e9;
    assert(!jeep_idle_parked_test_feed(&t, JEEP_TEST_SPEED, &wrong, 2260));
}
static void restoration_needs_post_return_samples(void)
{
    jeep_idle_parked_test_t   t    = {0};
    vehicle_command_context_t c    = {.revision = 7, .link = 9, .connected = true};
    vehicle_command_state_t   idle = {.phase = VEHICLE_STARTING, .request = {.operation = 5}};
    jeep_idle_parked_test_scope(&t, c);
    identity(&t);
    rpm(&t, 700, 120);
    speed(&t, 0, 120);
    jeep_idle_parked_test_started(&t, &idle);
    idle.phase      = VEHICLE_VERIFYING;
    idle.changed_at = 1000;
    jeep_idle_parked_test_context(&t, &c, &idle, 1001);
    assert(!c.normal_idle_verified);
    for (uint32_t at = 1100; at <= 1700; at += 300)
    {
        rpm(&t, 720, at);
        speed(&t, 0, at);
        jeep_idle_parked_test_context(&t, &c, &idle, at);
        assert(c.normal_idle_verified == (at == 1700));
    }
    assert(c.proof_at[0] == 1700 && c.verified_operation[0] == 5);
    idle.phase = VEHICLE_UNKNOWN;
    jeep_idle_parked_test_context(&t, &c, &idle, 1701);
    assert(!c.normal_idle_verified); /* Low RPM never reconciles an uncertain write. */
    idle.phase = VEHICLE_VERIFYING;
    idle.request.operation++;
    rpm(&t, 700, 1800);
    jeep_idle_parked_test_context(&t, &c, &idle, 1800);
    assert(!c.normal_idle_verified);
    c.connected = false;
    jeep_idle_parked_test_scope(&t, c);
    assert(!t.baseline_valid && !t.cvn);
}
static void reboot_recovery_requires_inactive_and_later_samples(void)
{
    for (unsigned wrap = 0; wrap < 2; ++wrap)
    {
        uint32_t                  base = wrap ? UINT32_MAX - 600U : 1000;
        jeep_idle_parked_test_t   t    = {0};
        vehicle_command_context_t c    = {.revision = 7, .link = 9, .connected = true};
        vehicle_command_state_t   idle = {.phase = VEHICLE_UNKNOWN, .request = {.operation = 5}};
        jeep_idle_parked_test_scope(&t, c);
        identity(&t);
        t.identity_at = base;
        rpm(&t, 700, base);
        speed(&t, 0, base);
        jeep_idle_parked_test_context(&t, &c, &idle, base);
        assert(!c.normal_idle_verified && !t.baseline_valid);
        idle.phase              = VEHICLE_VERIFYING;
        idle.changed_at         = base;
        idle.recovery_verifying = true;
        jeep_idle_parked_test_context(&t, &c, &idle, base);
        assert(!t.recovery_baseline && !c.normal_idle_verified);
        rpm(&t, 700, base + 100);
        speed(&t, 0, base + 100);
        jeep_idle_parked_test_context(&t, &c, &idle, base + 100);
        assert(t.recovery_baseline && !c.normal_idle_verified);
        /* Repeated context calls cannot turn one observation into proof. */
        for (unsigned i = 0; i < 4; ++i)
            jeep_idle_parked_test_context(&t, &c, &idle, base + 100);
        assert(!t.proof_count);
        for (unsigned i = 0; i < 3; ++i)
        {
            uint32_t at = base + 200 + i * 300;
            rpm(&t, 720, at);
            speed(&t, 0, at);
            jeep_idle_parked_test_context(&t, &c, &idle, at);
            assert(c.normal_idle_verified == (i == 2));
        }
        c.link++;
        jeep_idle_parked_test_scope(&t, c);
        jeep_idle_parked_test_context(&t, &c, &idle, base + 900);
        assert(!c.normal_idle_verified && !t.recovery_baseline && !t.baseline_valid);
    }
}
static void supervised_brake_vehicle_and_input_policy(void)
{
    jeep_idle_parked_test_t   t                                  = {0};
    vehicle_command_context_t c                                  = {.revision = 7, .link = 9, .connected = true};
    vehicle_command_state_t   states[VEHICLE_COMMAND_KIND_COUNT] = {0};
    jeep_idle_parked_test_scope(&t, c);
    jeep_brake_owner_test_context(&t, &c, states, 120);
    assert(c.brake_owner_test && !c.supervised_mask);
    identity(&t);
    rpm(&t, 700, 120);
    speed(&t, 0, 125);
    jeep_brake_owner_test_context(&t, &c, states, 130);
    assert(c.supervised_mask == 6 && c.supervised_start_mask == 6 && c.supervised_run_mask == 6);
    assert(!c.qualified_mask && c.identity == 0xca36378cU && c.supervised_at == 120);
    speed(&t, 20, 140);
    rpm(&t, 2300, 140);
    jeep_brake_owner_test_context(&t, &c, states, 150);
    assert(!c.supervised_start_mask && c.supervised_run_mask == 6);
    jeep_brake_owner_test_context(&t, &c, states, 2141);
    assert(!c.supervised_start_mask && !c.supervised_run_mask);
    jeep_brake_owner_test_context(&t, &c, states, 30111);
    assert(!c.supervised_mask);
    states[1].phase             = VEHICLE_ACTIVE;
    states[1].request.operation = 1;
    jeep_brake_owner_test_context(&t, &c, states, 30111);
    assert(c.supervised_mask == 6 && !c.supervised_run_mask);
    c.link++;
    jeep_idle_parked_test_scope(&t, c);
    jeep_brake_owner_test_context(&t, &c, states, 30112);
    assert(!c.supervised_mask);
    identity(&t);
    c.connected = false;
    jeep_brake_owner_test_context(&t, &c, states, 30113);
    assert(!c.supervised_mask && !c.supervised_start_mask);
}
static elm_result_t decode(const char *text, uint8_t pid, elm_payload_t *p)
{
    elm_response_t response = {0};
    for (unsigned i = 0; text[i]; ++i)
        assert(!elm_response_push(&response, text[i]));
    return elm_response_decode_identity(&response, pid, p);
}
static void attributed_multiframe_identity(void)
{
    const char   *cal = "7E8 10 13 49 04 01 36 38 30\r7E8 21 35 39 34 33 34 41 44\r7E8 22 00 00 00 00 00 00\r";
    elm_payload_t p;
    assert(decode(cal, 4, &p) == ELM_OK && p.length == 16 && !memcmp(p.bytes, "68059434AD", 10));
    assert(decode("7E8 07 49 06 01 CA 36 37 8C\r", 6, &p) == ELM_OK && p.length == 4);
    assert(decode(cal, 6, &p) != ELM_OK);
    assert(decode("7E9 07 49 06 01 CA 36 37 8C\r", 6, &p) != ELM_OK);
    assert(decode("000007E8 07 49 06 01 CA 36 37 8C\r", 6, &p) != ELM_OK);
    assert(decode("7E8 07 49 06 02 CA 36 37 8C\r", 6, &p) != ELM_OK);
    assert(decode("7E8 10 13 49 04 01 36 38 30\r7E8 22 35 39 34 33 34 41 44\r", 4, &p) != ELM_OK);
    assert(decode("7E8 07 49 06 01 CA 36 37 8C\r7E9 07 49 06 01 CA 36 37 8C\r", 6, &p) != ELM_OK);
    assert(decode("NO DATA\r", 6, &p) == ELM_NO_DATA);
}
int main(void)
{
    reboot_recovery_requires_inactive_and_later_samples();
    identity_and_guards();
    supervised_brake_vehicle_and_input_policy();
    restoration_needs_post_return_samples();
    attributed_multiframe_identity();
    printf("parked idle evidence policy passed (%zu bytes); no live IO\n", sizeof(jeep_idle_parked_test_t));
}
