#include <string.h>
#include "obd_adapter_profile.h"
static const obd_adapter_profile_t profiles[] = {
    {"elm-18f0-v1", "0x18f0", "0x2af1", "0x2af0", 1},
    {"elm-bench-v1", OBD_BENCH_SERVICE, "0x2af1", "0x2af0", 2},
};
const obd_adapter_profile_t *obd_adapter_profile_find(const char *id)
{
    if (!id) return 0;
    for (unsigned i=0; i<sizeof(profiles)/sizeof(profiles[0]); ++i)
        if (!strcmp(profiles[i].id,id)) return &profiles[i];
    return 0;
}
