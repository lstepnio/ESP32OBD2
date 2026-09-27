#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

typedef struct {
    uint16_t byte_offset;
    uint8_t byte_length;
    bool little_endian;
    bool signed_value;
    int32_t numerator;
    uint32_t denominator;
    double offset;
    double minimum;
    double maximum;
} pid_numeric_decoder_t;

/* A bounded numeric decoder for declarative PID definitions. No allocation,
 * ELM routing, or unit conversion occurs here. */
bool pid_decoder_eval(const pid_numeric_decoder_t *definition,
                      const uint8_t *payload, size_t payload_length, double *value);
