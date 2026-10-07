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

static elm_result_t routed(const char *wire, uint32_t ecu, elm_payload_t *payload)
{
    elm_response_t frame;
    elm_response_reset(&frame);
    for (const char *p = wire; *p; ++p) elm_response_push(&frame, *p);
    return elm_response_decode_for_ecu(&frame, 1, 0x0c, ecu, payload);
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
    /* Sanitized Jeep engine capture: responder and exact baseline payload. */
    for (size_t split = 1; split < 32; ++split) {
        assert(decode("7E804410C0B3C\r\r>", split, &payload) == ELM_OK);
        assert(payload.has_responder && payload.responder == 0x7e8 &&
               payload.length == 2 && payload.bytes[0] == 0x0b && payload.bytes[1] == 0x3c);
    }
    assert(routed("7E8 04 41 0C 0B 3C 00 00 00\r7E9 04 41 0C 11 22\r>", 0x7e8, &payload) == ELM_OK);
    assert(payload.length == 2 && payload.bytes[0] == 0x0b);
    assert(routed("18DAF11004410C0B3C\r>", 0x18daf110, &payload) == ELM_OK);
    assert(routed("410C0B3C\r>", 0x7e8, &payload) == ELM_MALFORMED);
    assert(routed("7E904410C1122\r>", 0x7e8, &payload) == ELM_MALFORMED);
    assert(routed("7E804410C0B3C\r7E804410C0B3C\r>", 0x7e8, &payload) == ELM_AMBIGUOUS);
    assert(routed("7E81008410C0B3C\r>", 0x7e8, &payload) == ELM_MALFORMED);
    assert(routed("7E805410C0B3C\r>", 0x7e8, &payload) == ELM_MALFORMED);
    assert(routed("7E804410C0B3C\r7E904410CZZZZ\r>", 0x7e8, &payload) == ELM_MALFORMED);
    assert(payload.length == 0);
    assert(routed("7E8 0 4 41 0C 0B 3C\r>", 0x7e8, &payload) == ELM_MALFORMED);
    assert(routed("41 0 C 0B 3C\r>", ELM_ECU_ANY, &payload) == ELM_MALFORMED);
    assert(routed("18DAF110 04 41 0C 0B 3C\r>", 0x18daf110, &payload) == ELM_OK);
    puts("ELM response fixtures passed");
}
