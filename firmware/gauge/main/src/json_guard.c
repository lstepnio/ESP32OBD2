#include "json_guard.h"

bool json_guard_shape(const char *bytes, size_t length, unsigned maximum_depth)
{
    enum { HARD_DEPTH_LIMIT = 64 };
    if (!bytes || length == 0 || maximum_depth == 0 ||
        maximum_depth > HARD_DEPTH_LIMIT) return false;
    unsigned depth = 0;
    char delimiters[HARD_DEPTH_LIMIT];
    bool quoted = false, escaped = false;
    for (size_t i = 0; i < length; ++i) {
        char c = bytes[i];
        if (quoted) {
            if (escaped) escaped = false;
            else if (c == '\\') escaped = true;
            else if (c == '"') quoted = false;
            continue;
        }
        if (c == '"') quoted = true;
        else if (c == '{' || c == '[') {
            if (depth >= maximum_depth) return false;
            delimiters[depth++] = c;
        } else if (c == '}' || c == ']') {
            if (depth == 0) return false;
            if ((c == '}' && delimiters[depth - 1] != '{') ||
                (c == ']' && delimiters[depth - 1] != '[')) return false;
            --depth;
        }
    }
    return !quoted && !escaped && depth == 0;
}
