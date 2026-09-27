#include <math.h>

#include "pid_decoder.h"

bool pid_decoder_eval(const pid_numeric_decoder_t *definition,
                      const uint8_t *payload, size_t payload_length, double *value)
{
    if (definition == NULL || payload == NULL || value == NULL ||
        definition->byte_length < 1 || definition->byte_length > 4 ||
        definition->denominator == 0 ||
        definition->byte_offset > payload_length ||
        definition->byte_length > payload_length - definition->byte_offset ||
        !isfinite(definition->offset) || !isfinite(definition->minimum) ||
        !isfinite(definition->maximum) || definition->minimum >= definition->maximum)
        return false;

    uint32_t raw = 0;
    for (uint8_t i = 0; i < definition->byte_length; ++i) {
        uint8_t byte = payload[definition->byte_offset + i];
        if (definition->little_endian) raw |= (uint32_t)byte << (8 * i);
        else raw = (raw << 8) | byte;
    }
    int64_t sample = raw;
    if (definition->signed_value &&
        (raw & (1U << (8 * definition->byte_length - 1))) != 0)
        sample -= (int64_t)1 << (8 * definition->byte_length);
    double decoded = (double)sample * definition->numerator / definition->denominator +
                     definition->offset;
    if (!isfinite(decoded) || decoded < definition->minimum ||
        decoded > definition->maximum) return false;
    *value = decoded;
    return true;
}
