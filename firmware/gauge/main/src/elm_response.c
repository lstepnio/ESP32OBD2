#include "elm_response.h"
#include <string.h>

void elm_response_reset(elm_response_t *response)
{
    response->length = 0;
    response->overflow = false;
    response->text[0] = '\0';
}

bool elm_response_push(elm_response_t *response, uint8_t byte)
{
    if (byte == '>') return true;
    if (response->length + 1 >= sizeof(response->text)) {
        response->overflow = true;
    } else {
        response->text[response->length++] = (char)byte;
        response->text[response->length] = '\0';
    }
    return false;
}

static int hex(char c)
{
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    return -1;
}

static bool equals(const char *line, size_t len, const char *text)
{
    return len == strlen(text) && memcmp(line, text, len) == 0;
}

elm_result_t elm_response_decode(const elm_response_t *response, uint8_t mode,
                                 uint8_t pid, elm_payload_t *payload)
{
    payload->length = 0;
    if (response->overflow) return ELM_OVERFLOW;
    if (mode != 1) return ELM_MALFORMED;
    unsigned matches = 0;
    elm_result_t result = ELM_MALFORMED;
    bool failed = false;
    for (size_t offset = 0; offset < response->length;) {
        const char *line = response->text + offset;
        size_t len = 0;
        while (offset + len < response->length && line[len] != '\r' && line[len] != '\n') len++;
        offset += len;
        while (offset < response->length && (response->text[offset] == '\r' || response->text[offset] == '\n')) offset++;
        while (len && (*line == ' ' || *line == '\t')) { line++; len--; }
        while (len && (line[len - 1] == ' ' || line[len - 1] == '\t')) len--;
        if (!len || equals(line, len, "SEARCHING...")) continue;
        if (equals(line, len, "NO DATA")) { result = ELM_NO_DATA; failed = true; continue; }
        if (equals(line, len, "?") || equals(line, len, "STOPPED") ||
            equals(line, len, "UNABLE TO CONNECT") || equals(line, len, "CAN ERROR") ||
            equals(line, len, "BUS ERROR") || equals(line, len, "BUFFER FULL")) {
            result = ELM_ADAPTER_ERROR; failed = true; continue;
        }
        uint8_t bytes[ELM_PAYLOAD_CAPACITY + 2];
        size_t count = 0;
        int high = -1;
        bool malformed = false;
        for (size_t i = 0; i < len; i++) {
            if (line[i] == ' ' || line[i] == '\t') {
                if (high >= 0) malformed = true;
                continue;
            }
            int digit = hex(line[i]);
            if (digit < 0) { malformed = true; break; }
            if (high < 0) high = digit;
            else {
                if (count == sizeof(bytes)) { malformed = true; break; }
                bytes[count++] = (uint8_t)((high << 4) | digit);
                high = -1;
            }
        }
        if (malformed || high >= 0) { failed = true; result = ELM_MALFORMED; continue; }
        if (count == 2 && bytes[0] == mode && bytes[1] == pid) continue; /* command echo */
        if (count < 3 || bytes[0] != (uint8_t)(mode + 0x40) || bytes[1] != pid) {
            failed = true; result = ELM_MALFORMED; continue;
        }
        matches++;
        memcpy(payload->bytes, bytes + 2, count - 2);
        payload->length = count - 2;
    }
    if (matches > 1) { payload->length = 0; return ELM_AMBIGUOUS; }
    if (failed || matches != 1) { payload->length = 0; return result; }
    return ELM_OK;
}
