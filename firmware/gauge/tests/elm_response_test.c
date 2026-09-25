#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "elm_response.h"

static elm_result_t decode(const char *wire, size_t fragment, elm_payload_t *payload)
{
    elm_response_t response;
    elm_response_reset(&response);
    size_t length = strlen(wire);
    bool complete = false;
    for (size_t start = 0; start < length; start += fragment) {
        size_t end = start + fragment < length ? start + fragment : length;
        for (size_t i = start; i < end; i++) {
            if (elm_response_push(&response, (uint8_t)wire[i])) complete = true;
        }
    }
    assert(complete);
    return elm_response_decode(&response, 1, 0x0C, payload);
}

int main(void)
{
    elm_payload_t payload;
    for (size_t split = 1; split < 32; split++) {
        assert(decode("010C\rSEARCHING...\r41 0C 1A F8\r>", split, &payload) == ELM_OK);
        assert(payload.length == 2 && payload.bytes[0] == 0x1A && payload.bytes[1] == 0xF8);
    }
    assert(decode("NO DATA\r>", 1, &payload) == ELM_NO_DATA);
    assert(decode("?\r>", 1, &payload) == ELM_ADAPTER_ERROR);
    assert(decode("41 0C 1A ZZ\r>", 1, &payload) == ELM_MALFORMED);
    assert(decode("41 0C 1A F8\r41 0C 11 22\r>", 1, &payload) == ELM_AMBIGUOUS);
    char huge[ELM_RESPONSE_CAPACITY + 5];
    memset(huge, 'A', sizeof(huge));
    huge[sizeof(huge) - 2] = '>';
    huge[sizeof(huge) - 1] = '\0';
    assert(decode(huge, 7, &payload) == ELM_OVERFLOW);
    puts("ELM response fixtures passed");
}
