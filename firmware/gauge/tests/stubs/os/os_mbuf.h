#pragma once
#include <stdint.h>
struct os_mbuf { uint8_t data[768]; uint16_t length; int fail; };
int os_mbuf_append(struct os_mbuf *, const void *, uint16_t);
