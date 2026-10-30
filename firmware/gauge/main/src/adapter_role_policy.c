#include "adapter_role_policy.h"
#include <string.h>

void adapter_role_policy_init(adapter_role_policy_t *p)
{
    memset(p, 0, sizeof(*p));
    p->transport[1] = 1;
}

void adapter_role_policy_scope(adapter_role_policy_t *p, unsigned source, uint32_t generation)
{
    if (source > 1 || p->generation[source] == generation)
        return;
    p->generation[source] = generation;
    p->observed &= (uint8_t)~(1U << source);
    p->refresh_requested &= (uint8_t)~(1U << source);
    p->refresh_issued &= (uint8_t)~(1U << source);
    p->roles[source] = 0;
    if (!source)
        p->primary_proven = false;
    /* Retirement itself changes link epochs. Once pending, only the barrier
     * may publish a map; it does not use subsequent identity observations. */
}

bool adapter_role_identity_match(unsigned role, uint16_t pid, uint32_t responder, const uint8_t *data, size_t length)
{
    if (!data || (role != ADAPTER_ROLE_ECM && role != ADAPTER_ROLE_TCM) ||
        responder != (role == ADAPTER_ROLE_ECM ? 0x7e8U : 0x7e9U))
        return false;
    if (pid == 4)
    {
        const char *calibration = role == ADAPTER_ROLE_ECM ? "68059434AD" : "68274867AE";
        if (length != 16 || memcmp(data, calibration, 10))
            return false;
        for (unsigned i = 10; i < 16; ++i)
            if (data[i] != 0 && data[i] != ' ')
                return false;
        return true;
    }
    const uint8_t ecm_cvn[] = {0xca, 0x36, 0x37, 0x8c};
    const uint8_t tcm_cvn[] = {0x82, 0x40, 0xda, 0xe8};
    return pid == 6 && length == 4 && !memcmp(data, role == ADAPTER_ROLE_ECM ? ecm_cvn : tcm_cvn, 4);
}

void adapter_role_policy_observe(adapter_role_policy_t *p,
                                 unsigned               source,
                                 uint32_t               generation,
                                 uint8_t                roles,
                                 uint8_t                contradicted_roles,
                                 uint32_t               now)
{
    if (source > 1 || p->pending || p->generation[source] != generation)
        return;
    if (!source)
    {
        if (contradicted_roles & ADAPTER_ROLE_ECM)
            p->primary_proven = false;
        else if (roles & ADAPTER_ROLE_ECM)
            p->primary_proven = true;
    }
    p->roles[source]       = roles & (ADAPTER_ROLE_ECM | ADAPTER_ROLE_TCM);
    p->observed_at[source] = now;
    p->observed |= (uint8_t)(1U << source);
    if (p->observed != 3 || p->roles[0] != ADAPTER_ROLE_TCM || p->roles[1] != ADAPTER_ROLE_ECM)
        return;
    uint8_t stale = 0;
    for (unsigned candidate = 0; candidate < 2; ++candidate)
        if (now - p->observed_at[candidate] > ADAPTER_ROLE_EVIDENCE_WINDOW_MS)
        {
            uint8_t bit = (uint8_t)(1U << candidate);
            stale |= bit;
            if (!(p->refresh_issued & bit) || now - p->refresh_at[candidate] >= ADAPTER_ROLE_REFRESH_INTERVAL_MS)
            {
                p->refresh_requested |= bit;
                p->refresh_issued |= bit;
                p->refresh_at[candidate] = now;
            }
        }
    if (stale)
        return; /* Refresh a stale counterpart, never exchange on stale proof. */
    p->pending = true;
    p->retired = 0;
}

bool adapter_role_policy_take_refresh(adapter_role_policy_t *p, unsigned source)
{
    if (source > 1 || p->pending || !(p->refresh_requested & (1U << source)))
        return false;
    p->refresh_requested &= (uint8_t)~(1U << source);
    return true;
}

bool adapter_role_policy_retire(adapter_role_policy_t *p, unsigned source)
{
    if (source > 1 || !p->pending)
        return false;
    p->retired |= (uint8_t)(1U << source);
    if (p->retired != 3)
        return false;
    p->transport[0] ^= 1U;
    p->transport[1] ^= 1U;
    p->revision++;
    p->pending        = false;
    p->primary_proven = false;
    p->retired = p->observed = p->refresh_requested = p->refresh_issued = 0;
    p->roles[0] = p->roles[1] = 0;
    return true;
}

void adapter_role_policy_reject_primary(adapter_role_policy_t *p, uint32_t generation)
{
    if (p->generation[0] == generation)
        p->primary_proven = false;
}

bool adapter_role_policy_primary_verified(const adapter_role_policy_t *p, uint32_t generation)
{
    return !p->pending && p->generation[0] == generation && p->primary_proven;
}
