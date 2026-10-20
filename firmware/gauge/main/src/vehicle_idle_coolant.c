#include "vehicle_idle_coolant.h"
#include <math.h>
#include <string.h>

bool vehicle_idle_coolant_limit(const config_runtime_t *r, double *out)
{
    if (!r || !out || !r->source_count || r->source_count > 2 || r->pid_count > EGAUGE_RUNTIME_PIDS ||
        r->alert_count > EGAUGE_RUNTIME_ALERTS || r->sources[0].transmission || r->sources[0].simulated)
        return false;
    double limit = INFINITY;
    for (unsigned i = 0; i < r->alert_count; ++i)
    {
        const runtime_alert_t *a = &r->alerts[i];
        if (a->pid_index >= r->pid_count || !a->above || a->equals)
            continue;
        const runtime_pid_t *p = &r->pids[a->pid_index];
        if (p->source_index || p->service != 1 || p->obd.pid != 5 || p->responder != 0x7e8 ||
            (strcmp(p->unit, "°C") && strcmp(p->unit, "degC")))
            continue;
        if (a->has_warning && isfinite(a->warning) && a->warning > -40 && a->warning <= 215)
            limit = fmin(limit, a->warning);
        if (a->has_critical && isfinite(a->critical) && a->critical > -40 && a->critical <= 215)
            limit = fmin(limit, a->critical);
    }
    if (!isfinite(limit))
        return false;
    *out = limit;
    return true;
}

bool vehicle_idle_coolant_allows(bool configured, bool valid, double value, double limit, uint32_t at, uint32_t now)
{
    return configured && valid && now - at <= VEHICLE_IDLE_COOLANT_MAX_AGE_MS && isfinite(value) && value >= -40 &&
           value <= 215 && isfinite(limit) && limit > -40 && limit <= 215 && value < limit;
}
