#pragma once
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#define DIAGNOSTIC_FILTER_LIMIT 16
/* Controller identity, independent of the physical adapter/transport. */
typedef struct
{
    uint16_t code;
    uint8_t  endpoint;
} diagnostic_filter_t;
typedef struct
{
    bool     suppress_mil, complete, count_known;
    bool     show_mil; /* Positive unfiltered evidence, never an unresolved initial scan. */
    uint16_t visible_count, hidden_count;
} diagnostic_filter_result_t;
bool diagnostic_filter_parse(const char *text, uint16_t *code);
bool diagnostic_filter_matches(const diagnostic_filter_t *rules, size_t count, unsigned endpoint, uint16_t code);
diagnostic_filter_result_t diagnostic_filter_project(const uint8_t              packet[248],
                                                     unsigned                   endpoint,
                                                     const diagnostic_filter_t *rules,
                                                     size_t                     count);
