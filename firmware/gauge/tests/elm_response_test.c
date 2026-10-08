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

static elm_result_t faults(const char *wire, uint8_t mode, uint32_t ecu, size_t fragment,
                           elm_payload_t *payload)
{
    elm_response_t response;
    elm_response_reset(&response);
    for (size_t offset = 0; offset < strlen(wire); offset += fragment) {
        size_t end = offset + fragment < strlen(wire) ? offset + fragment : strlen(wire);
        for (size_t i = offset; i < end; ++i) elm_response_push(&response, wire[i]);
    }
    return elm_response_decode_dtcs_for_ecu(&response, mode, ecu, payload);
}

static elm_result_t identifier(const char *wire, size_t fragment, elm_payload_t *payload)
{
    elm_response_t response;
    elm_response_reset(&response);
    for (size_t offset = 0; offset < strlen(wire); offset += fragment) {
        size_t end = offset + fragment < strlen(wire) ? offset + fragment : strlen(wire);
        for (size_t i = offset; i < end; ++i) elm_response_push(&response, wire[i]);
    }
    return elm_response_decode_identifier(&response, 0x04fe, 0x7e9, payload);
}

int main(void)
{
    elm_payload_t payload;
    elm_response_t clear;elm_response_reset(&clear);
    const char *ack="7E80144\r>";for(const char *p=ack;*p;p++)elm_response_push(&clear,*p);
    assert(elm_response_decode_for_ecu(&clear,4,0,0x7e8,&payload)==ELM_OK && payload.length==0);
    assert(elm_response_decode_for_ecu(&clear,4,0,0x7e9,&payload)!=ELM_OK);
    elm_response_reset(&clear);ack="7E80144\r7E80144\r>";for(const char *p=ack;*p;p++)elm_response_push(&clear,*p);
    assert(elm_response_decode_for_ecu(&clear,4,0,0x7e8,&payload)==ELM_AMBIGUOUS);
    for (unsigned gear = 0; gear < 4; ++gear) {
        static const char *const wires[] = {"7E9046255030D\r>", "7E9046255030B\r>", "7E90462550300\r>", "7E90462550301\r>"};
        static const uint8_t values[] = {13, 11, 0, 1};
        elm_response_t response;
        elm_response_reset(&response);
        for (const char *c = wires[gear]; *c; ++c) elm_response_push(&response, *c);
        assert(elm_response_decode_identifier(&response, 0x5503, 0x7e9, &payload) == ELM_OK);
        assert(payload.length == 1 && payload.bytes[0] == values[gear]);
        assert(elm_response_decode_identifier(&response, 0x5504, 0x7e9, &payload) != ELM_OK);
    }
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
    /* Actual TCM fault payloads, excluding adapter/vehicle identity. */
    for (size_t split = 1; split < 32; ++split) {
        assert(faults("03\r7E9100A4304C121C140\r7E9211DCA1DF3AAAAAA\r>",
                      3, 0x7e9, split, &payload) == ELM_OK);
        assert(payload.length == 8 && payload.bytes[0] == 0xc1 && payload.bytes[1] == 0x21 &&
               payload.bytes[6] == 0x1d && payload.bytes[7] == 0xf3 && payload.responder == 0x7e9);
        assert(faults("7E910084703C121C140\r7E9211DCAAAAAAAAAAA\r>",
                      7, 0x7e9, split, &payload) == ELM_OK);
        assert(payload.length == 6 && payload.bytes[4] == 0x1d && payload.bytes[5] == 0xca);
        assert(faults("7E910084A03C121C140\r7E9211DCAAAAAAAAAAA\r>",
                      10, 0x7e9, split, &payload) == ELM_OK);
    }
    assert(faults("7E9024300AAAAAAAAAA\r>", 3, 0x7e9, 1, &payload) == ELM_OK);
    assert(payload.length == 0 && payload.has_responder);
    assert(faults("7E80443010133AAAAAA\r>", 3, 0x7e8, 1, &payload) == ELM_OK);
    assert(payload.length == 2 && payload.bytes[0] == 1 && payload.bytes[1] == 0x33);
    assert(faults("7E9100A4304C121C140\r7E9211DCA1DF3AAAAAA\r>", 3, 0x7e8, 1, &payload) != ELM_OK);
    assert(payload.length == 0);
    const char *bad[] = {
        "7E9100A4304C121C140\r>", /* missing continuation */
        "7E9100A4304C121C140\r7E9221DCA1DF3AAAAAA\r>",
        "7E9100A4304C121C140\r7E8211DCA1DF3AAAAAA\r>",
        "7E9100A4303C121C140\r7E9211DCA1DF3AAAAAA\r>",
        "7E9100A4704C121C140\r7E9211DCA1DF3AAAAAA\r>",
        "7E9100A4304C121C140\r7E9211DCA1DF3AAAAAA\r7E9211DCA1DF3AAAAAA\r>",
        "7E9211DCA1DF3AAAAAA\r>", "7E910414304C121C140\r>",
        "7E9 0 2 43 00\r>", "4300\r>", "7E9024300\rNO DATA\r>",
        "7E9024300\rBUFFER FULL\r>", "7E9037F0311\r>"
    };
    for (size_t i = 0; i < sizeof(bad)/sizeof(bad[0]); ++i) {
        assert(faults(bad[i], 3, 0x7e9, 1, &payload) != ELM_OK);
        assert(payload.length == 0);
    }
    assert(faults("7E9024300\r>", 4, 0x7e9, 1, &payload) == ELM_MALFORMED);
    assert(faults("NO DATA\r>", 3, 0x7e9, 1, &payload) == ELM_NO_DATA);
    /* Actual TCM temperature candidate, engine idling. Sensor meaning unqualified. */
    for (size_t split = 1; split < 32; ++split) {
        assert(identifier("2204FE\r7E9066204FE555455\r\r>", split, &payload) == ELM_OK);
        assert(payload.length == 3 && payload.bytes[0] == 85 && payload.bytes[1] == 84 &&
               payload.bytes[2] == 85 && payload.has_responder && payload.responder == 0x7e9);
        assert(identifier("7E9 06 62 04 FE 55 54 55 AA\r>", split, &payload) == ELM_OK);
    }
    const char *bad_identifier[] = {
        "7E8066204FE555455\r>", "6204FE555455\r>",
        "7E906625043555455\r>", "7E9066204FE5554\r>",
        "7E9066204FE555455\r7E9066204FE555455\r>",
        "7E910086204FE5554\r>", "7E9037F2231\r>",
        "7E9066204FE555455\rNO DATA\r>",
        "7E9 0 6 62 04 FE 55 54 55\r>", "7E9066204FE555455\rBUFFER FULL\r>"
    };
    for (size_t i = 0; i < sizeof(bad_identifier)/sizeof(bad_identifier[0]); ++i) {
        assert(identifier(bad_identifier[i], 1, &payload) != ELM_OK);
        assert(payload.length == 0);
    }
    assert(identifier("NO DATA\r>", 1, &payload) == ELM_NO_DATA);
    assert(identifier(huge, 1, &payload) == ELM_OVERFLOW);
    assert(faults("7E9037F0311\r>", 3, 0x7e9, 1, &payload) == ELM_UNSUPPORTED);
    assert(faults("7E9037F0731\r>", 7, 0x7e9, 1, &payload) == ELM_UNSUPPORTED);
    assert(faults("7E9037F0322\r>", 3, 0x7e9, 1, &payload) != ELM_UNSUPPORTED);
    assert(faults("7E8037F0311\r>", 3, 0x7e9, 1, &payload) != ELM_UNSUPPORTED);
    puts("ELM response fixtures passed");
}
