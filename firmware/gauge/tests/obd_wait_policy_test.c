#include <assert.h>
#include "obd_wait_policy.h"
int main(void) {
 assert(obd_wait_remaining(100, 100, 5) == 5);
 assert(obd_wait_remaining(100, 104, 5) == 1);
 assert(obd_wait_remaining(100, 105, 5) == 0);
 assert(obd_wait_remaining(100, 106, 5) == 0);
 assert(obd_wait_remaining(UINT32_MAX-2, 1, 5) == 1);
 assert(obd_wait_remaining(UINT32_MAX-2, 2, 5) == 0);
 assert(obd_wait_remaining(100, 100, 0) == 0);
}
