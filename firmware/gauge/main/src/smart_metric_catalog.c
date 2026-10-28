#include "smart_metric_catalog.h"
#include <string.h>
static const struct
{
    const char           *id, *unit;
    uint8_t               service;
    uint16_t              pid;
    uint32_t              responder;
    pid_numeric_decoder_t decoder;
    smart_metric_rule_t   rule;
} catalog[] = {
#include "smart_metric_catalog.inc"
};
smart_metric_rule_t smart_metric_rule(const char                  *id,
                                      uint8_t                      service,
                                      uint16_t                     pid,
                                      uint32_t                     responder,
                                      const char                  *unit,
                                      const pid_numeric_decoder_t *d)
{
    for (unsigned i = 0; id && unit && d && i < sizeof(catalog) / sizeof(catalog[0]); ++i)
    {
        const pid_numeric_decoder_t *c = &catalog[i].decoder;
        if (strcmp(catalog[i].id, id) || strcmp(catalog[i].unit, unit) || catalog[i].service != service ||
            catalog[i].pid != pid || catalog[i].responder != responder || c->byte_offset != d->byte_offset ||
            c->byte_length != d->byte_length || c->little_endian != d->little_endian ||
            c->signed_value != d->signed_value || c->numerator != d->numerator || c->denominator != d->denominator ||
            c->offset != d->offset || c->minimum != d->minimum || c->maximum != d->maximum)
            continue;
        return catalog[i].rule;
    }
    return (smart_metric_rule_t){0};
}
