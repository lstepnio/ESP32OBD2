# Configurable pages and renderers

Status: implemented and transferred to the development gauge on 2026-09-27. Android sent a five-page dashboard through the authenticated owner path, firmware restarted into revision 3, and a fresh bounded document read reported zero differences. Physical renderer inspection and live vehicle sampling remain to be recorded.

## Executable configuration

The authenticated configuration path accepts one to eight ordered pages and up to 32 Mode 01 definitions. Android generates definitions only for readings referenced by a page or alert. The current companion editor offers the five bounded standard definitions already implemented by the firmware: RPM, coolant temperature, vehicle speed, calculated load, and fuel level.

Each page has a stable ID, name, renderer, and one or two PID references. Numeric, arc, bar, and trend pages require one PID. Dual pages require exactly two distinct PIDs. Firmware retains the full page descriptor instead of reducing a page to one PID index.

The compact public capability `cfg:2` negotiates this experimental subset while keeping the public GATT value within the observed Android 255-byte boundary. Version 1 remains the former three-page numeric subset. Public `configWrite` remains false until the complete general configuration contract is qualified.

## Gauge rendering

The LVGL presentation layer creates its bounded widget set once and switches visibility by page type:

- Numeric shows one large value, page label, and unit.
- Arc maps the decoded PID range to a 270-degree arc.
- Bar maps the same range to a compact horizontal indicator.
- Trend retains 60 selected-page points at 500 ms intervals, approximately 30 seconds, without persistent writes.
- Dual shows two independently fresh values and units.

Samples enter a bounded queue and update only metrics referenced by the selected page. Each metric uses its configured stale interval. Page changes clear prior samples, preventing values from crossing pages. Adapter loss and maintenance clear every metric on the visible page. Alert severity colors the arc, bar, or trend while the existing readable warning or critical label remains active.

## Companion workflow

The Gauge screen provides an ordered page list with add, remove, and move controls. A profile persists up to eight pages in local schema version 3. Existing schema 1 and 2 profiles migrate to the earlier three-page ordering without losing their primary reading, renderer, thresholds, or advanced adapter preference.

The editor selects a renderer per page, a primary reading, and a second reading when Dual is selected. The review shows the exact page order, renderer, and readings sent. Unsupported renderer or page limits from older firmware disable transfer with an explicit reason. Authenticated readback reconstructs the complete page list.

## Evidence boundaries

The five-page transfer, firmware activation, persistence after restart, and authenticated document readback are recorded in [configurable-pages-live-validation.md](configurable-pages-live-validation.md). That evidence does not establish circular-display readability for every renderer, renderer frame timing under live traffic, alert presentation on every renderer, or real OBD values.
