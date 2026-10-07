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

/* CAN headers include the ISO-TP single-frame length. Multi-frame replies
 * require a separate bounded assembler and are deliberately not accepted here. */
static elm_result_t decode_pid(const elm_response_t *response, uint8_t mode,
                              uint8_t pid, uint32_t ecu, elm_payload_t *payload)
{
    memset(payload, 0, sizeof(*payload));
    if (response->overflow) return ELM_OVERFLOW;
    bool service = mode != 1;
    if (service && mode != 3 && mode != 7 && mode != 10) return ELM_MALFORMED;
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
        char compact[2 * (ELM_PAYLOAD_CAPACITY + 8) + 1];
        size_t digits = 0;
        size_t space_prefix = 0;
        bool saw_space = false;
        for (size_t i = 0; i < len; ++i) {
            if (line[i] == ' ' || line[i] == '\t') {
                /* A space may follow a CAN ID or a whole byte, never split a byte. */
                if (!saw_space) {
                    bool response_prefix = digits >= 2 && hex(compact[0]) == ((mode+0x40)>>4) &&
                                           hex(compact[1]) == ((mode+0x40)&15);
                    bool echo_prefix = digits >= 2 && hex(compact[0]) == (mode>>4) && hex(compact[1]) == (mode&15);
                    if (!response_prefix && !echo_prefix) space_prefix = (digits & 1) ? 3 : 8;
                    saw_space = true;
                }
                if (digits < space_prefix || ((digits - space_prefix) & 1)) return ELM_MALFORMED;
                continue;
            }
            if (hex(line[i]) < 0 || digits == sizeof(compact) - 1) {
                payload->length = 0;
                return ELM_MALFORMED;
            }
            compact[digits++] = line[i];
        }
        compact[digits] = 0;
        if (digits == (service ? 2U : 4U) && hex(compact[0]) == (mode >> 4) &&
            hex(compact[1]) == (mode & 15) && (service ||
            (hex(compact[2]) == (pid >> 4) && hex(compact[3]) == (pid & 15)))) continue;
        size_t prefix = 0;
        bool header = !(digits >= 2 && hex(compact[0]) == ((mode + 0x40) >> 4) &&
                        hex(compact[1]) == ((mode + 0x40) & 15));
        uint32_t responder = 0;
        if (header) {
            prefix = (digits & 1) ? 3 : 8;
            if (digits < prefix + (service ? 4U : 8U)) return ELM_MALFORMED;
            for (size_t i = 0; i < prefix; ++i)
                responder = (responder << 4) | (unsigned)hex(compact[i]);
            if (responder > (prefix == 3 ? 0x7ffU : 0x1fffffffU)) return ELM_MALFORMED;
        } else if (ecu != ELM_ECU_ANY) {
            /* Headerless data cannot prove which ECU answered. */
            return ELM_MALFORMED;
        }
        if ((digits - prefix) & 1) return ELM_MALFORMED;
        uint8_t bytes[ELM_PAYLOAD_CAPACITY + 2];
        size_t count = (digits - prefix) / 2;
        if (count > sizeof(bytes)) return ELM_OVERFLOW;
        for (size_t i = 0; i < count; ++i)
            bytes[i] = (hex(compact[prefix + 2*i]) << 4) | hex(compact[prefix + 2*i + 1]);
        size_t start = header ? 1 : 0;
        if (header) {
            if (!bytes[0] || bytes[0] > 7 || count - 1 < bytes[0]) return ELM_MALFORMED;
            /* CAN padding is outside the declared ISO-TP payload. */
            count = 1 + bytes[0];
            if (ecu != ELM_ECU_ANY && responder != ecu) continue;
        }
        if (count < start + (service ? 1U : 3U) || bytes[start] != (uint8_t)(mode + 0x40) ||
            (!service && bytes[start + 1] != pid)) {
            failed = true; result = ELM_MALFORMED; continue;
        }
        if (++matches > 1) { payload->length = 0; return ELM_AMBIGUOUS; }
        size_t payload_start = start + (service ? 1 : 2);
        memcpy(payload->bytes, bytes + payload_start, count - payload_start);
        payload->length = count - payload_start;
        payload->has_responder = header;
        payload->responder = responder;
    }
    if (failed || matches != 1) { payload->length = 0; return result; }
    return ELM_OK;
}

elm_result_t elm_response_decode(const elm_response_t *response, uint8_t mode,
                                 uint8_t pid, elm_payload_t *payload)
{
    return elm_response_decode_for_ecu(response, mode, pid, ELM_ECU_ANY, payload);
}

elm_result_t elm_response_decode_for_ecu(const elm_response_t *response, uint8_t mode,
                                         uint8_t pid, uint32_t ecu, elm_payload_t *payload)
{
    elm_result_t result = decode_pid(response, mode, pid, ecu, payload);
    if (result != ELM_OK) memset(payload, 0, sizeof(*payload));
    return result;
}

elm_result_t elm_response_decode_service(const elm_response_t *response, uint8_t mode,
                                         elm_payload_t *payload)
{
    if (mode != 3 && mode != 7 && mode != 10) { memset(payload, 0, sizeof(*payload)); return ELM_MALFORMED; }
    return elm_response_decode_for_ecu(response, mode, 0, ELM_ECU_ANY, payload);
}
