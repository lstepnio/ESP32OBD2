#pragma once
#include "FreeRTOS.h"
#include <stdlib.h>
#include <string.h>
typedef struct
{
    unsigned      size, capacity, count, head;
    unsigned char bytes[];
}                          *QueueHandle_t;
static inline QueueHandle_t xQueueCreate(unsigned n, unsigned size)
{
    QueueHandle_t q = calloc(1, sizeof(*q) + n * size);
    if (q)
    {
        q->size     = size;
        q->capacity = n;
    }
    return q;
}
static inline int xQueueSend(QueueHandle_t q, const void *data, unsigned wait)
{
    (void)wait;
    if (q->count == q->capacity)
        return pdFALSE;
    memcpy(q->bytes + ((q->head + q->count) % q->capacity) * q->size, data, q->size);
    q->count++;
    return pdTRUE;
}
static inline int xQueueReceive(QueueHandle_t q, void *data, unsigned wait)
{
    (void)wait;
    if (!q->count)
        return pdFALSE;
    memcpy(data, q->bytes + q->head * q->size, q->size);
    q->head = (q->head + 1) % q->capacity;
    q->count--;
    return pdTRUE;
}
static inline int xQueueOverwrite(QueueHandle_t q, const void *data)
{
    q->count = q->head = 0;
    return xQueueSend(q, data, 0);
}
static inline void xQueueReset(QueueHandle_t q)
{
    q->count = q->head = 0;
}
static inline void vQueueDelete(QueueHandle_t q)
{
    free(q);
}
