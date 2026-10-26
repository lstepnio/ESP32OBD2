#pragma once
#include <stdio.h>
#define ARRAY_SIZE(a) (sizeof(a) / sizeof((a)[0]))
#define ESP_LOGI(tag, ...)                                                                                             \
    do                                                                                                                 \
    {                                                                                                                  \
        (void)(tag);                                                                                                   \
        if (0)                                                                                                         \
            printf(__VA_ARGS__);                                                                                       \
    } while (0)
