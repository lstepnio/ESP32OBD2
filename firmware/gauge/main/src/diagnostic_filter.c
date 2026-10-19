#include "diagnostic_filter.h"
#include <string.h>
static int hex(char c)
{
    return c >= '0' && c <= '9' ? c - '0' : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
}
bool diagnostic_filter_parse(const char *text, uint16_t *code)
{
    if (!text || !code || strlen(text) != 5)
        return false;
    static const char classes[] = "PCBU";
    const char       *kind      = strchr(classes, text[0]);
    if (!kind || !text[0] || text[1] < '0' || text[1] > '3')
        return false;
    int a = hex(text[2]), b = hex(text[3]), c = hex(text[4]);
    if (a < 0 || b < 0 || c < 0)
        return false;
    *code = ((unsigned)(kind - classes) << 14) | ((text[1] - '0') << 12) | (a << 8) | (b << 4) | c;
    return *code != 0; /* P0000 is padding, not a filterable fault. */
}
bool diagnostic_filter_matches(const diagnostic_filter_t *rules, size_t count, unsigned endpoint, uint16_t code)
{
    if (!rules || count > DIAGNOSTIC_FILTER_LIMIT || endpoint > 1 || !code)
        return false;
    for (size_t i = 0; i < count; i++)
        if (rules[i].endpoint == endpoint && rules[i].code == code)
            return true;
    return false;
}
diagnostic_filter_result_t diagnostic_filter_project(const uint8_t              p[248],
                                                     unsigned                   endpoint,
                                                     const diagnostic_filter_t *rules,
                                                     size_t                     count)
{
    diagnostic_filter_result_t out = {0};
    if (!p || p[0] != 15 || endpoint > 1 || count > DIAGNOSTIC_FILTER_LIMIT || p[1] != endpoint || !(p[2] & 4) ||
        (p[2] & 8))
        return out;
    uint16_t seen[96];
    unsigned length   = 0;
    bool     complete = true;
    for (unsigned i = 0; i < 3; i++)
    {
        const uint8_t *category = p + 32 + i * 72;
        if (category[0] == 3)
            continue;
        if (category[0] != 1 || (category[1] & 3) != 3 || category[2] > 32 || category[3])
        {
            complete = false;
            continue;
        }
        out.count_known = true;
        for (unsigned j = 0; j < category[2]; j++)
        {
            uint16_t code = ((uint16_t)category[8 + j * 2] << 8) | category[9 + j * 2];
            if (!code)
                continue;
            bool duplicate = false;
            for (unsigned k = 0; k < length; k++)
                if (seen[k] == code)
                    duplicate = true;
            if (duplicate)
                continue;
            seen[length++] = code;
            if (diagnostic_filter_matches(rules, count, endpoint, code))
                out.hidden_count++;
            else
                out.visible_count++;
        }
    }
    if ((p[2] & 3) == 3 && (p[32] != 1 || (p[33] & 3) != 3 || !p[22] || p[34] != p[22]))
        complete = false;
    out.complete = complete;
    /* Only reconciled confirmed faults can explain the real ECU MIL. */
    out.suppress_mil = count && complete && (p[2] & 3) == 3 && p[23] == 1 && p[3] == 1 && p[22] > 0 && p[32] == 1 &&
                       (p[33] & 3) == 3 && p[34] == p[22] && out.hidden_count > 0 && !out.visible_count;
    return out;
}
