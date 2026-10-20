#pragma once

#include <stdbool.h>
#include <stdint.h>

/* Reference template constants remain native pixels until templates are adapted. */
#define GAUGE_REFERENCE_WIDTH 240
#define GAUGE_REFERENCE_HEIGHT 240
#define GAUGE_REFERENCE_CONTENT_INSET 8
#define GAUGE_REFERENCE_DECORATION_INSET 4

typedef enum
{
    GAUGE_SURFACE_ROUND = 0,
    GAUGE_SURFACE_RECTANGLE,
} gauge_surface_shape_t;

typedef struct
{
    uint16_t              width;
    uint16_t              height;
    gauge_surface_shape_t shape;
    uint16_t              content_inset;
    uint16_t              decoration_inset;
} gauge_surface_t;

extern const gauge_surface_t gauge_reference_surface;

bool gauge_surface_uses_reference_templates(const gauge_surface_t *surface);
bool gauge_surface_valid(const gauge_surface_t *surface);
bool gauge_surface_fits(const gauge_surface_t *surface, int left, int top, int right, int bottom);
