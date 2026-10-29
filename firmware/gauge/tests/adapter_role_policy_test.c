#include "adapter_role_policy.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>

static void scope(adapter_role_policy_t *p)
{
    adapter_role_policy_init(p);
    adapter_role_policy_scope(p, 0, 10);
    adapter_role_policy_scope(p, 1, 20);
}
int main(void)
{
    adapter_role_policy_t p;
    scope(&p);
    assert(!adapter_role_policy_primary_verified(&p, 10));
    adapter_role_policy_observe(&p, 0, 10, ADAPTER_ROLE_TCM, 0, 100);
    adapter_role_policy_observe(&p, 1, 20, ADAPTER_ROLE_ECM, 0, 200);
    assert(p.pending && p.transport[0] == 0);
    assert(!adapter_role_policy_retire(&p, 0));
    assert(p.transport[0] == 0 && p.pending);
    assert(adapter_role_policy_retire(&p, 1));
    assert(p.transport[0] == 1 && p.transport[1] == 0 && p.revision == 1);
    assert(!adapter_role_policy_primary_verified(&p, 10));
    assert(!adapter_role_policy_retire(&p, 1));
    adapter_role_policy_scope(&p, 0, 11);
    adapter_role_policy_scope(&p, 1, 21);
    adapter_role_policy_observe(&p, 0, 10, ADAPTER_ROLE_ECM, 0, 300);
    assert(!adapter_role_policy_primary_verified(&p, 11));
    adapter_role_policy_observe(&p, 0, 11, ADAPTER_ROLE_ECM, 0, 300);
    adapter_role_policy_observe(&p, 1, 21, ADAPTER_ROLE_TCM, 0, 300);
    assert(!p.pending && adapter_role_policy_primary_verified(&p, 11));
    for (unsigned a = 0; a < 4; ++a)
        for (unsigned b = 0; b < 4; ++b)
        {
            if (a == ADAPTER_ROLE_TCM && b == ADAPTER_ROLE_ECM)
                continue;
            scope(&p);
            adapter_role_policy_observe(&p, 0, 10, a, 0, 100);
            adapter_role_policy_observe(&p, 1, 20, b, 0, 200);
            assert(!p.pending && p.transport[0] == 0);
        }
    scope(&p);
    adapter_role_policy_observe(&p, 0, 10, ADAPTER_ROLE_TCM, 0, 100);
    adapter_role_policy_scope(&p, 0, 11);
    adapter_role_policy_observe(&p, 1, 20, ADAPTER_ROLE_ECM, 0, 200);
    assert(!p.pending); /* Retired source epoch cannot justify a swap. */
    scope(&p);
    adapter_role_policy_observe(&p, 0, 10, ADAPTER_ROLE_TCM, 0, 100);
    adapter_role_policy_observe(&p, 1, 20, ADAPTER_ROLE_ECM, 0, 15101);
    assert(!p.pending); /* No inference from a missing or stale sibling. */
    scope(&p);
    adapter_role_policy_observe(&p, 0, 10, ADAPTER_ROLE_ECM | ADAPTER_ROLE_TCM, 0, 100);
    adapter_role_policy_observe(&p, 1, 20, ADAPTER_ROLE_TCM, 0, 100);
    assert(!p.pending && adapter_role_policy_primary_verified(&p, 10));
    adapter_role_policy_observe(&p, 0, 10, 0, 0, 30100);
    assert(adapter_role_policy_primary_verified(&p, 10)); /* Timeout is not contrary identity evidence. */
    adapter_role_policy_reject_primary(&p, 9);
    assert(adapter_role_policy_primary_verified(&p, 10));
    adapter_role_policy_reject_primary(&p, 10);
    assert(!adapter_role_policy_primary_verified(&p, 10));
    adapter_role_policy_observe(&p, 0, 10, ADAPTER_ROLE_ECM, 0, 40100);
    assert(adapter_role_policy_primary_verified(&p, 10));
    adapter_role_policy_observe(&p, 0, 10, 0, ADAPTER_ROLE_ECM, 60100);
    assert(!adapter_role_policy_primary_verified(&p, 10));
    uint8_t       cal[16] = "68059434AD";
    const uint8_t ecm[] = {0xca, 0x36, 0x37, 0x8c}, tcm[] = {0x82, 0x40, 0xda, 0xe8};
    assert(adapter_role_identity_match(ADAPTER_ROLE_ECM, 4, 0x7e8, cal, 16));
    assert(adapter_role_identity_match(ADAPTER_ROLE_ECM, 6, 0x7e8, ecm, 4));
    assert(!adapter_role_identity_match(ADAPTER_ROLE_ECM, 6, 0x7e9, ecm, 4));
    assert(!adapter_role_identity_match(ADAPTER_ROLE_ECM, 6, 0x7e8, tcm, 4));
    memcpy(cal, "68274867AE", 10);
    assert(adapter_role_identity_match(ADAPTER_ROLE_TCM, 4, 0x7e9, cal, 16));
    assert(adapter_role_identity_match(ADAPTER_ROLE_TCM, 6, 0x7e9, tcm, 4));
    cal[15] = 'X';
    assert(!adapter_role_identity_match(ADAPTER_ROLE_TCM, 4, 0x7e9, cal, 16));
    assert(!adapter_role_identity_match(ADAPTER_ROLE_TCM, 4, 0x7e9, cal, 10));
    puts("adapter role policy tests passed");
}
