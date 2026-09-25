#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define ELM_RESPONSE_CAPACITY 512
#define ELM_PAYLOAD_CAPACITY 64

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
} elm_payload_t;

void elm_response_reset(elm_response_t *response);
/* True only at the ELM prompt. Caller consumes the completed frame then resets. */
bool elm_response_push(elm_response_t *response, uint8_t byte);
/* Headerless Mode 01 only. Multiple matching responders are deliberately rejected. */
elm_result_t elm_response_decode(const elm_response_t *response, uint8_t mode,
                                 uint8_t pid, elm_payload_t *payload);
