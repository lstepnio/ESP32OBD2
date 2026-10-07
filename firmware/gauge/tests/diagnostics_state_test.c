#include <assert.h>
#include <stdio.h>
#include "diagnostics_state.h"
int test_lock_busy;
int test_critical_depth;
static uint8_t packet[DIAGNOSTICS_FULL_SIZE];
static void read_at(uint32_t now) { assert(diagnostics_state_full_status(now, packet) == 248); }
int main(int argc, char **argv) {
 assert(diagnostics_state_init() == ESP_OK);
 diagnostics_state_configure(true, 28, false); diagnostics_state_connected();
 read_at(1000); assert(packet[0]==15 && packet[1]==1 && packet[20]==0xe9 && packet[4]==28 && packet[8]==1);
 assert(packet[32]==0 && packet[33]==0);
 uint8_t codes[]={0xc1,0x21,0xc1,0x40,0x1d,0xca,0x1d,0xf3,0xc1,0x21,0,0};
 diagnostics_state_codes(3,codes,sizeof(codes),900);
 diagnostics_state_codes(7,codes,6,900); diagnostics_state_codes(10,codes,6,900);
 diagnostics_state_mil(true,4,900); read_at(1000);
 assert(packet[2]==7 && packet[34]==4 && packet[106]==3 && packet[178]==3);
 assert(packet[40]==0xc1 && packet[47]==0xf3 && packet[48]==0);
 if(argc==2) { FILE *f=fopen(argv[1],"w"); assert(f); for(unsigned i=0;i<sizeof(packet);i++) fprintf(f,"%02x",packet[i]); fputs("\n",f); fclose(f); }
 diagnostics_state_codes(3,codes,3,1000); read_at(1000); assert(packet[34]==4);
 uint8_t legacy[32]; diagnostics_state_status(legacy); assert(legacy[1]==0 && legacy[3]==0);
 diagnostics_state_failed(3,DIAGNOSTICS_UNSUPPORTED); read_at(1100);
 assert(packet[32]==3 && packet[33]==1 && packet[34]==4);
 diagnostics_state_disconnected(); read_at(1200); assert(!(packet[2]&5) && packet[33]==1 && packet[34]==4);
 diagnostics_state_connected(); read_at(1300); assert(packet[8]==2 && packet[34]==0 && packet[33]==0);
 uint8_t zero[2]={0,0}; diagnostics_state_codes(3,zero,2,1300); read_at(1300);
 assert(packet[32]==1 && packet[33]==3 && packet[34]==0);
 read_at(121301); assert(packet[33]==1);
 diagnostics_state_configure(false,29,false); diagnostics_state_connected();
 diagnostics_state_codes(3,codes,8,UINT32_MAX-50); read_at(25); assert(packet[33]==3 && packet[1]==0 && packet[20]==0xe8);
 diagnostics_state_configure(true,30,true); diagnostics_state_connected(); diagnostics_state_codes(3,codes,8,900);
 read_at(1000); assert(packet[2]==12 && packet[33]==1);
 test_lock_busy = 1;
 assert(diagnostics_state_full_status(1000, packet) == 0);
 assert(diagnostics_state_status(legacy) == 0);
 diagnostics_snapshot_t unavailable;
 diagnostics_state_snapshot(1000, &unavailable); assert(!unavailable.valid);
 test_lock_busy = 0;
 read_at(1000); assert(packet[2]==12);
 /* Independent controller evidence survives loss and reconnect of the other. */
 diagnostics_state_configure_for(0,false,31,false);
 diagnostics_state_configure_for(1,true,31,false);
 diagnostics_state_connected_for(0); diagnostics_state_connected_for(1);
 diagnostics_state_codes_for(0,3,codes,8,900);
 diagnostics_state_codes_for(1,3,codes,4,900);
 diagnostics_state_disconnected_for(1);
 assert(diagnostics_state_full_status_for(0,1000,packet)==248);
 assert(packet[2]==4 && packet[33]==3 && packet[34]==4 && packet[1]==0);
 assert(diagnostics_state_full_status_for(1,1000,packet)==248);
 assert(packet[2]==0 && packet[33]==1 && packet[34]==2 && packet[1]==1);
 diagnostics_state_connected_for(1);
 assert(diagnostics_state_full_status_for(1,1000,packet)==248 && packet[8]==2 && packet[34]==0);
 assert(diagnostics_state_full_status_for(0,1000,packet)==248 && packet[8]==1 && packet[34]==4);
 puts("Diagnostics source, full lists, expiry, reconnect and simulation passed");
}
