#pragma once

#include "esp_partition.h"
#include "ota_transfer.h"

static inline void ota_target_preflight_encode(uint8_t                out[OTA_TARGET_PREFLIGHT_SIZE],
                                               bool                   rollback,
                                               uint32_t               board_tag,
                                               const esp_partition_t *running,
                                               const esp_partition_t *alternate,
                                               const esp_partition_t *otadata)
{
    if (alternate && running && alternate->address == running->address)
        alternate = NULL;

    out[0]                 = 1;
    out[1]                 = rollback ? 1 : 0;
    out[2]                 = 0;
    out[3]                 = 0;
    const uint32_t words[] = {
        board_tag,
        running ? running->address : 0,
        alternate ? alternate->address : 0,
        running ? running->size : 0,
        alternate ? alternate->size : 0,
        otadata ? otadata->address : 0,
        otadata ? otadata->size : 0,
    };
    for (size_t i = 0; i < sizeof(words) / sizeof(words[0]); ++i)
        for (unsigned byte = 0; byte < 4; ++byte)
            out[4 + 4 * i + byte] = (uint8_t)(words[i] >> (8 * byte));
}
