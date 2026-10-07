/* Small host entry point that runs the production assembler/decoder. */
#include "elm_response.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int nibble(char c)
{
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

int main(int argc, char **argv)
{
    if (argc != 4 && argc != 5) return 2;
    char *end = NULL;
    long mode = strtol(argv[1], &end, 16);
    if (!end || *end || mode < 0 || mode > 255) return 2;
    long pid = strtol(argv[2], &end, 16);
    if (!end || *end || pid < 0 || pid > 255) return 2;
    size_t length = strlen(argv[3]);
    if (length % 2 || length > 16384) return 2;
    elm_response_t frame;
    elm_response_reset(&frame);
    bool prompt = false;
    for (size_t i = 0; i < length; i += 2) {
        int high = nibble(argv[3][i]), low = nibble(argv[3][i + 1]);
        if (high < 0 || low < 0 || prompt) return 2;
        prompt = elm_response_push(&frame, (uint8_t)((high << 4) | low));
    }
    uint32_t ecu = ELM_ECU_ANY;
    if (argc == 5) {
        unsigned long route = strtoul(argv[4], &end, 16);
        if (!end || *end || route > 0x1fffffffUL) return 2;
        ecu = route;
    }
    elm_payload_t payload = {0};
    elm_result_t result = !prompt ? ELM_PENDING : mode == 1
        ? elm_response_decode_for_ecu(&frame, (uint8_t)mode, (uint8_t)pid, ecu, &payload)
        : elm_response_decode_service(&frame, (uint8_t)mode, &payload);
    printf("{\"status\":%d,\"payload\":\"", result);
    for (size_t i = 0; i < payload.length; ++i) printf("%02x", payload.bytes[i]);
    puts("\"}");
    return 0;
}
