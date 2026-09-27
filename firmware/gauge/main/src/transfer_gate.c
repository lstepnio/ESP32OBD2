#include <stdatomic.h>
#include "transfer_gate.h"

static atomic_uchar operation;

bool transfer_gate_claim(uint8_t kind)
{
    unsigned char expected = 0;
    return atomic_compare_exchange_strong(&operation, &expected, kind);
}

void transfer_gate_release(uint8_t kind)
{
    unsigned char expected = kind;
    atomic_compare_exchange_strong(&operation, &expected, 0);
}

uint8_t transfer_gate_current(void)
{
    return atomic_load(&operation);
}
