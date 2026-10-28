#include "diagnostic_filter.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
static uint8_t packet[248];
static void    fixture(void)
{
    memset(packet, 0, sizeof(packet));
    packet[0]  = 15;
    packet[2]  = 7;
    packet[3]  = 1;
    packet[22] = 1;
    packet[23] = 1;
    for (unsigned i = 0; i < 3; i++)
    {
        packet[32 + 72 * i] = 1;
        packet[33 + 72 * i] = 3;
    }
    packet[34] = 1;
    packet[40] = 0x04;
    packet[41] = 0x20;
}
int main(int argc, char **argv)
{
    uint16_t code;
    assert(diagnostic_filter_parse("P0420", &code) && code == 0x0420);
    assert(diagnostic_filter_parse("U0121", &code) && code == 0xc121);
    const char *bad[] = {"P0000", "p0420", "P042*", "P4420", "P04200", " P0420", "X0420", "P0G20", ""};
    for (unsigned i = 0; i < sizeof(bad) / sizeof(bad[0]); i++)
        assert(!diagnostic_filter_parse(bad[i], &code));
    diagnostic_filter_t rules[] = {{0x0420, 0}};
    fixture();
    uint8_t original[248];
    memcpy(original, packet, 248);
    diagnostic_filter_result_t r = diagnostic_filter_project(packet, 0, rules, 1);
    assert(r.suppress_mil && !r.show_mil && r.hidden_count == 1 && !r.visible_count && r.complete);
    assert(!memcmp(packet, original, 248));
    r = diagnostic_filter_project(packet, 0, NULL, 0);
    assert(!r.suppress_mil && r.show_mil && r.visible_count == 1);
    rules[0].endpoint = 1;
    r                 = diagnostic_filter_project(packet, 0, rules, 1);
    assert(!r.suppress_mil && r.show_mil && r.visible_count == 1);
    rules[0].endpoint = 0;
    packet[34]        = 2;
    packet[22]        = 2;
    packet[42]        = 0xc1;
    packet[43]        = 0x21;
    r                 = diagnostic_filter_project(packet, 0, rules, 1);
    assert(!r.suppress_mil && r.visible_count == 1 && r.hidden_count == 1);
    fixture();
    packet[106] = 1;
    packet[112] = 0x04;
    packet[113] = 0x20;
    r           = diagnostic_filter_project(packet, 0, rules, 1);
    assert(r.suppress_mil && r.hidden_count == 1); /* Across-category dedup. */
    fixture();
    packet[105] = 1;
    r           = diagnostic_filter_project(packet, 0, rules, 1);
    assert(!r.suppress_mil && !r.complete);
    fixture();
    packet[104] = 3;
    r           = diagnostic_filter_project(packet, 0, rules, 1);
    assert(r.suppress_mil); /* Unsupported pending, confirmed known. */
    fixture();
    packet[32] = 3;
    r          = diagnostic_filter_project(packet, 0, rules, 1);
    assert(!r.suppress_mil);
    fixture();
    packet[35] = 1;
    r          = diagnostic_filter_project(packet, 0, rules, 1);
    assert(!r.suppress_mil && !r.complete);
    fixture();
    packet[22] = 2;
    r          = diagnostic_filter_project(packet, 0, rules, 1);
    assert(!r.suppress_mil);
    fixture();
    packet[22] = 0;
    r          = diagnostic_filter_project(packet, 0, rules, 1);
    assert(!r.suppress_mil);
    fixture();
    packet[2] &= ~1;
    r = diagnostic_filter_project(packet, 0, rules, 1);
    assert(!r.suppress_mil);
    fixture();
    packet[2] &= ~4;
    r = diagnostic_filter_project(packet, 0, rules, 1);
    assert(!r.suppress_mil && !r.count_known);
    fixture();
    packet[2] |= 8;
    r = diagnostic_filter_project(packet, 0, rules, 1);
    assert(!r.suppress_mil && !r.count_known);
    fixture();
    r = diagnostic_filter_project(packet, 0, rules, 17);
    assert(!r.suppress_mil);
    packet[1]         = 1;
    rules[0].endpoint = 1;
    r                 = diagnostic_filter_project(packet, 1, rules, 1);
    assert(r.suppress_mil);
    /* Initial MIL On with unresolved lists is not a gauge warning. Partial
     * decoded unfiltered evidence is sufficient, without waiting for slow modes. */
    fixture();
    memset(packet + 32, 0, 216);
    r = diagnostic_filter_project(packet, 0, NULL, 0);
    assert(!r.complete && !r.suppress_mil && !r.show_mil);
    packet[32] = 1;
    packet[33] = 3;
    packet[34] = 1;
    packet[40] = 0x04;
    packet[41] = 0x20;
    r          = diagnostic_filter_project(packet, 0, NULL, 0);
    assert(!r.complete && r.show_mil && r.visible_count == 1);
    rules[0].endpoint = 0;
    r                 = diagnostic_filter_project(packet, 0, rules, 1);
    assert(!r.complete && !r.show_mil && !r.suppress_mil && r.hidden_count == 1);
    packet[2] &= ~1U;
    r = diagnostic_filter_project(packet, 0, NULL, 0);
    assert(!r.show_mil && r.visible_count == 1); /* Fault presence is not MIL truth. */
    if (argc == 2)
    {
        FILE *f = fopen(argv[1], "r");
        assert(f);
        char hexbytes[497];
        assert(fscanf(f, "%496s", hexbytes) == 1);
        fclose(f);
        for (unsigned i = 0; i < 248; i++)
        {
            unsigned value;
            assert(sscanf(hexbytes + 2 * i, "%2x", &value) == 1);
            packet[i] = value;
        }
        diagnostic_filter_t shared[4];
        for (unsigned i = 0; i < 4; i++)
            shared[i] = (diagnostic_filter_t){((uint16_t)packet[40 + 2 * i] << 8) | packet[41 + 2 * i], 1};
        r = diagnostic_filter_project(packet, 1, shared, 4);
        assert(r.suppress_mil && r.hidden_count == 4 && r.visible_count == 0);
    }
    puts("PASS: exact scoped CEL filtering, raw evidence preservation and incomplete/unknown failure cases");
}
