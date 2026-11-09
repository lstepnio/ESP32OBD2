#include <assert.h>
#include <string.h>
#include "ota_target_preflight.h"

int main(void)
{
    const esp_partition_t running                             = {.address = 0x00012345, .size = 0x00234567};
    const esp_partition_t alternate                           = {.address = 0x00345678, .size = 0x00456789};
    const esp_partition_t otadata                             = {.address = 0x00056789, .size = 0x00002000};
    const uint8_t         expected[OTA_TARGET_PREFLIGHT_SIZE] = {
        1,    1,    0,    0, 'T',  'E',  'S',  'T', 0x45, 0x23, 0x01, 0, 0x78, 0x56, 0x34, 0,
        0x67, 0x45, 0x23, 0, 0x89, 0x67, 0x45, 0,   0x89, 0x67, 0x05, 0, 0x00, 0x20, 0x00, 0,
    };
    uint8_t frame[OTA_TARGET_PREFLIGHT_SIZE];
    ota_target_preflight_encode(frame, true, 0x54534554U, &running, &alternate, &otadata);
    assert(memcmp(frame, expected, sizeof(frame)) == 0);

    const uint8_t missing[OTA_TARGET_PREFLIGHT_SIZE] = {1};
    ota_target_preflight_encode(frame, false, 0, NULL, NULL, NULL);
    assert(memcmp(frame, missing, sizeof(frame)) == 0);

    ota_target_preflight_encode(frame, false, 0, &running, &running, NULL);
    assert(memcmp(frame + 8, expected + 8, 4) == 0);
    assert(memcmp(frame + 16, expected + 16, 4) == 0);
    assert(memcmp(frame + 12, missing + 12, 4) == 0);
    assert(memcmp(frame + 20, missing + 20, 12) == 0);
    return 0;
}
