#include <assert.h>
#include <string.h>
#include "adapter_status.h"
bool test_paused;
int test_critical_depth;
int main(void) {
 config_runtime_t runtime = {.source_count=2};
 strcpy(runtime.vehicle_id,"fixture.vehicle");
 strcpy(runtime.sources[0].id,"ecm"); strcpy(runtime.sources[1].id,"tcm");
 strcpy(runtime.sources[0].address,"C0:00:00:00:00:01");
 strcpy(runtime.sources[1].address,"C0:00:00:00:00:02");
 runtime.sources[1].transmission=true;
 adapter_status_init(&runtime);
 uint8_t engine[160], child[160], map[]={0x80,0,0,0};
 adapter_status_event(0,10,4,0); adapter_status_event(1,20,4,0);
 adapter_status_support(0,10,0x7e8,0,map,4);
 adapter_status_support(1,20,0x7e9,0,map,4);
 assert(adapter_status_ready_for(0) && adapter_status_ready_for(1));
 adapter_status_event(1,21,1,0);
 assert(adapter_status_ready_for(0) && !adapter_status_ready_for(1));
 assert(adapter_status_snapshot_for(0,engine)==160 && engine[1]==4 && engine[120]==1);
 assert(adapter_status_snapshot_for(1,child)==160 && child[1]==1 && child[120]==0);
 assert(!strcmp((char*)engine+80,"ecm") && !strcmp((char*)child+80,"tcm"));
 adapter_status_event(1,20,4,0); /* Late old session must not resurrect the child. */
 adapter_status_support(1,20,0x7e9,0,map,4);
 assert(!adapter_status_ready_for(1) && adapter_status_generation(1)==21);
 adapter_status_event(1,22,4,0);
 assert(adapter_status_ready_for(0) && adapter_status_ready_for(1));
 test_paused=true;
 assert(!adapter_status_ready_for(0) && !adapter_status_ready_for(1));
 assert(adapter_status_snapshot_for(0,engine)==160 && engine[1]==6 && engine[120]==0);
 assert(adapter_status_snapshot_for(1,child)==160 && child[1]==6 && child[120]==0);
 assert(adapter_status_snapshot_for(2,child)==0 && !adapter_status_ready_for(2));
 assert(test_critical_depth==0);
}
