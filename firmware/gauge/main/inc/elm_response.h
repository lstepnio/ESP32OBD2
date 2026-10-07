#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define ELM_RESPONSE_CAPACITY 512
#define ELM_PAYLOAD_CAPACITY 64
#define ELM_ECU_ANY UINT32_MAX

typedef enum {
    ELM_PENDING, ELM_OK, ELM_NO_DATA, ELM_ADAPTER_ERROR,
    ELM_MALFORMED, ELM_OVERFLOW, ELM_AMBIGUOUS
} elm_result_t;

/* One instance per adapter. Owned by its polling task, never by the BLE callback. */
typedef struct {
    char text[ELM_RESPONSE_CAPACITY];
    size_t length;
    bool overflow;
} elm_response_t;

typedef struct {
    uint8_t bytes[ELM_PAYLOAD_CAPACITY];
    size_t length;
    bool has_responder;
    uint32_t responder;
} elm_payload_t;

void elm_response_reset(elm_response_t *response);
/* True only at the ELM prompt. Caller consumes the completed frame then resets. */
bool elm_response_push(elm_response_t *response, uint8_t byte);
/* Headerless or CAN single-frame Mode 01. Multiple responders fail closed. */
elm_result_t elm_response_decode(const elm_response_t *response, uint8_t mode,
                                 uint8_t pid, elm_payload_t *payload);
/* Headerless single-responder emissions service, for Mode 03/07/0A reads.
 * Ambiguous or malformed multi-ECU replies fail closed. */
elm_result_t elm_response_decode_service(const elm_response_t *response, uint8_t mode,
                                         elm_payload_t *payload);

/* Explicit routing requires headers and accepts exactly one reply from this ECU. */
elm_result_t elm_response_decode_for_ecu(const elm_response_t *response, uint8_t mode,
                                         uint8_t pid, uint32_t ecu, elm_payload_t *payload);

/* Headered CAN DTC reads only. Assemble one bounded ISO-TP message for this ECU,
 * validate its reported DTC count, and return normalized two-byte code pairs. */
elm_result_t elm_response_decode_dtcs_for_ecu(const elm_response_t *response, uint8_t mode,
                                            uint32_t ecu, elm_payload_t *payload);

/* Headered 11-bit CAN single-frame Mode 22 read. Verify the full 16-bit DID;
 * callers must also check the exact expected data length and sensor validity. */
elm_result_t elm_response_decode_identifier(const elm_response_t *response, uint16_t identifier,
                                             uint32_t ecu, elm_payload_t *payload);
