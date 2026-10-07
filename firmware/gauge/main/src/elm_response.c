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

elm_result_t elm_response_decode_identifier(const elm_response_t *response, uint16_t identifier,
                                             uint32_t ecu, elm_payload_t *payload)
{
    if (!payload) return ELM_MALFORMED;
    memset(payload, 0, sizeof(*payload));
    if (!response || ecu > 0x7ffU) return ELM_MALFORMED;
    if (response->overflow) return ELM_OVERFLOW;
    bool matched = false, no_data = false;
    elm_payload_t candidate = {0};
    for (size_t offset = 0; offset < response->length;) {
        const char *line = response->text + offset;
        size_t len = 0;
        while (offset + len < response->length && line[len] != '\r' && line[len] != '\n') ++len;
        offset += len;
        while (offset < response->length && (response->text[offset] == '\r' || response->text[offset] == '\n')) ++offset;
        while (len && (*line == ' ' || *line == '\t')) { ++line; --len; }
        while (len && (line[len-1] == ' ' || line[len-1] == '\t')) --len;
        if (!len || equals(line, len, "SEARCHING...")) continue;
        if (equals(line, len, "NO DATA")) { no_data = true; continue; }
        if (equals(line, len, "?") || equals(line, len, "BUFFER FULL") ||
            equals(line, len, "STOPPED") || equals(line, len, "CAN ERROR") ||
            equals(line, len, "BUS ERROR") || equals(line, len, "UNABLE TO CONNECT")) return ELM_ADAPTER_ERROR;
        char compact[20];
        size_t digits = 0;
        for (size_t i = 0; i < len; ++i) {
            if (line[i] == ' ' || line[i] == '\t') {
                if (digits < 3 || ((digits - 3) & 1U)) return ELM_MALFORMED;
                continue;
            }
            if (hex(line[i]) < 0 || digits >= sizeof(compact)-1) return ELM_MALFORMED;
            compact[digits++] = line[i];
        }
        if (digits == 6 && hex(compact[0]) == 2 && hex(compact[1]) == 2 &&
            hex(compact[2]) == (identifier >> 12) && hex(compact[3]) == ((identifier >> 8) & 15) &&
            hex(compact[4]) == ((identifier >> 4) & 15) && hex(compact[5]) == (identifier & 15)) continue;
        if (digits < 7 || !(digits & 1U)) return ELM_MALFORMED;
        uint32_t responder = (hex(compact[0]) << 8) | (hex(compact[1]) << 4) | hex(compact[2]);
        if (responder > 0x7ffU) return ELM_MALFORMED;
        uint8_t frame[8];
        size_t count = (digits-3)/2;
        for (size_t i = 0; i < count; ++i)
            frame[i] = (uint8_t)((hex(compact[3+2*i]) << 4) | hex(compact[4+2*i]));
        if (!frame[0] || frame[0] > 7 || count < 1U+frame[0]) return ELM_MALFORMED;
        if (responder != ecu) continue;
        if (matched) return ELM_AMBIGUOUS;
        if (frame[0] < 4 || frame[1] != 0x62 || frame[2] != (identifier >> 8) ||
            frame[3] != (identifier & 255)) return ELM_MALFORMED;
        candidate.length = frame[0]-3;
        memcpy(candidate.bytes, frame+4, candidate.length);
        candidate.has_responder = true;
        candidate.responder = responder;
        matched = true;
    }
    if (no_data) return matched ? ELM_MALFORMED : ELM_NO_DATA;
    if (!matched) return ELM_MALFORMED;
    *payload = candidate;
    return ELM_OK;
}

elm_result_t elm_response_decode_dtcs_for_ecu(const elm_response_t *response, uint8_t mode,
                                            uint32_t ecu, elm_payload_t *payload)
{
    if (!payload) return ELM_MALFORMED;
    memset(payload, 0, sizeof(*payload));
    if (!response || ecu > 0x1fffffffU || (mode != 3 && mode != 7 && mode != 10))
        return ELM_MALFORMED;
    if (response->overflow) return ELM_OVERFLOW;
    uint8_t message[ELM_PAYLOAD_CAPACITY];
    size_t expected = 0, received = 0;
    unsigned sequence = 1;
    bool started = false, complete = false, no_data = false;
    for (size_t offset = 0; offset < response->length;) {
        const char *line = response->text + offset;
        size_t len = 0;
        while (offset + len < response->length && line[len] != '\r' && line[len] != '\n') ++len;
        offset += len;
        while (offset < response->length && (response->text[offset] == '\r' || response->text[offset] == '\n')) ++offset;
        while (len && (*line == ' ' || *line == '\t')) { ++line; --len; }
        while (len && (line[len-1] == ' ' || line[len-1] == '\t')) --len;
        if (!len || equals(line, len, "SEARCHING...")) continue;
        if (equals(line, len, "NO DATA")) { no_data = true; continue; }
        if (equals(line, len, "?") || equals(line, len, "BUFFER FULL") ||
            equals(line, len, "STOPPED") || equals(line, len, "CAN ERROR") ||
            equals(line, len, "BUS ERROR") || equals(line, len, "UNABLE TO CONNECT"))
            return ELM_ADAPTER_ERROR;
        if (len == 2 && hex(line[0]) == 0 && hex(line[1]) == mode) continue;
        char compact[25];
        size_t digits = 0;
        /* Discover header width from the first separated token or compact parity. */
        size_t first = 0;
        while (first < len && line[first] != ' ' && line[first] != '\t') ++first;
        size_t width = first < len ? first : 0;
        if (width && width != 3 && width != 8) return ELM_MALFORMED;
        for (size_t i = 0; i < len; ++i) {
            if (line[i] == ' ' || line[i] == '\t') {
                if (digits < width || ((digits - width) & 1U)) return ELM_MALFORMED;
                continue;
            }
            if (hex(line[i]) < 0 || digits >= sizeof(compact)-1) return ELM_MALFORMED;
            compact[digits++] = line[i];
        }
        if (!width) width = (digits & 1U) ? 3 : 8;
        if (digits < width + 4 || ((digits-width) & 1U)) return ELM_MALFORMED;
        uint32_t responder = 0;
        for (size_t i = 0; i < width; ++i)
            responder = (responder << 4) | (unsigned)hex(compact[i]);
        if (responder > (width == 3 ? 0x7ffU : 0x1fffffffU)) return ELM_MALFORMED;
        uint8_t frame[8];
        size_t count = (digits-width)/2;
        if (count > sizeof(frame)) return ELM_MALFORMED;
        for (size_t i = 0; i < count; ++i)
            frame[i] = (uint8_t)((hex(compact[width+2*i]) << 4) | hex(compact[width+2*i+1]));
        if (responder != ecu) continue;
        if (complete) return ELM_AMBIGUOUS;
        unsigned type = frame[0] >> 4;
        size_t start = 1;
        if (type == 0) {
            if (started || !frame[0] || frame[0] > 7 || count < 1U+frame[0]) return ELM_MALFORMED;
            expected = frame[0];
            started = true;
        } else if (type == 1) {
            if (started || count != 8) return ELM_MALFORMED;
            expected = ((frame[0] & 15U) << 8) | frame[1];
            if (expected <= 7) return ELM_MALFORMED;
            if (expected > sizeof(message)) return ELM_OVERFLOW;
            started = true;
            start = 2;
        } else if (type == 2) {
            if (!started || (frame[0] & 15U) != sequence) return ELM_MALFORMED;
            sequence = (sequence + 1) & 15U;
        } else return ELM_MALFORMED;
        size_t needed = expected-received;
        if (needed > 7 && type == 2 && count != 8) return ELM_MALFORMED;
        size_t take = needed < count-start ? needed : count-start;
        if (!take || (type == 0 && take != needed)) return ELM_MALFORMED;
        memcpy(message+received, frame+start, take);
        received += take;
        complete = received == expected;
    }
    if (no_data) return started ? ELM_MALFORMED : ELM_NO_DATA;
    if (complete && expected == 3 && message[0] == 0x7f && message[1] == mode &&
        (message[2] == 0x11 || message[2] == 0x12 || message[2] == 0x31))
        return ELM_UNSUPPORTED;
    if (!complete || expected < 2 || message[0] != (uint8_t)(mode+0x40) ||
        expected != 2U + 2U*message[1]) return ELM_MALFORMED;
    payload->length = expected-2;
    memcpy(payload->bytes, message+2, payload->length);
    payload->has_responder = true;
    payload->responder = ecu;
    return ELM_OK;
}
