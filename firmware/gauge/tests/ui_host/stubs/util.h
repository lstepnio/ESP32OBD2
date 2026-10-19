#pragma once
#include <assert.h>
#define ESP_NULL_CHECK(value, tag, message) assert(value)
#define ESP_CHECK(value, tag, message) assert(value)
