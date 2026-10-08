#include "alert_events.h"
#include "alert_engine.h"
#include "diagnostic_clear.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include <limits.h>

static alert_events_t threshold_events;
static void threshold_sink(unsigned key,uint8_t severity,bool unavailable,const char *label,const char *unit,float value,float limit,uint32_t observed,uint32_t now) {
    alert_events_observe(&threshold_events,key,severity,unavailable,label,unit,value,limit,observed,now);
}
static void threshold_integration(void) {
    config_runtime_t runtime={0};runtime.pid_count=1;runtime.alert_count=1;
    strcpy(runtime.pids[0].name,"Transmission temp");strcpy(runtime.pids[0].unit,"degC");runtime.pids[0].stale_ms=500;
    runtime.alerts[0]=(runtime_alert_t){.above=true,.has_warning=true,.has_critical=true,.warning=100,.critical=110,.hysteresis=5,.trigger_dwell_ms=200,.clear_dwell_ms=300};
    alert_events_init(&threshold_events,9);alert_engine_init(&runtime);alert_engine_set_sink(threshold_sink);
    alert_engine_sample(0,112,0);alert_engine_tick(0);assert(!threshold_events.count);
    alert_engine_sample(0,112,200);alert_engine_tick(200);assert(threshold_events.episodes[0].current.severity==4);
    alert_engine_invalidate_pid(0);alert_engine_tick(201);assert(threshold_events.episodes[0].current.unavailable);
    alert_engine_sample(0,90,202);alert_engine_tick(202);assert(threshold_events.episodes[0].current.lifecycle==1);
    alert_engine_sample(0,90,502);alert_engine_tick(502);assert(threshold_events.episodes[0].current.lifecycle==0);
    alert_engine_set_sink(NULL);
}
int main(int argc,char **argv) {
    (void)argv;
    alert_events_t events;alert_events_init(&events,7);events.revision=50;
    alert_events_observe(&events,0,4,false,"Coolant","C",118,115,900,1000);
    uint8_t packet[ALERT_EVENT_PACKET];size_t n=alert_events_packet(&events,7,0,false,packet);
    assert(n==108 && packet[3]==1 && packet[2]==2);
    if(argc>1) { for(size_t i=0;i<n;i++)printf("%02x",packet[i]);puts("");return 0; }
    alert_event_t decoded;assert(alert_event_decode(packet+24,&decoded));assert(decoded.value==118 && decoded.revision==50);
    uint32_t episode=events.episodes[0].current.episode;
    assert(alert_events_attention(&events.episodes[0],1000));
    alert_events_observe(&events,0,4,false,"Coolant","C",119,115,1000,1100);assert(events.sequence==1);
    assert(!alert_events_ack(&events,0,episode,8,0,1100));
    assert(alert_events_ack(&events,0,episode,7,0,1100));
    uint32_t ack_sequence=events.sequence;assert(alert_events_ack(&events,0,episode,7,0,1101));assert(events.sequence==ack_sequence);
    assert(events.episodes[0].current.lifecycle==1&&!alert_events_attention(&events.episodes[0],1200));
    alert_events_observe(&events,0,0,true,"Coolant","C",0,115,1100,1200);
    assert(events.episodes[0].current.lifecycle==1&&events.episodes[0].current.severity==4);
    alert_events_observe(&events,0,0,false,"Coolant","C",90,115,1200,1300);
    assert(events.episodes[0].current.lifecycle==0);
    alert_events_observe(&events,0,3,false,"Coolant","C",110,100,1300,1400);
    assert(events.episodes[0].current.episode!=episode);
    assert(!alert_events_ack(&events,0,episode,7,0,1400));
    episode=events.episodes[0].current.episode;
    assert(alert_events_ack(&events,0,episode,7,300000,UINT32_MAX-100));
    assert(!alert_events_attention(&events.episodes[0],1000));
    assert(alert_events_attention(&events.episodes[0],300000));
    alert_events_observe(&events,0,4,false,"Coolant","C",120,115,1500,1500);
    assert(alert_events_attention(&events.episodes[0],1500));
    alert_events_observe(&events,36,2,false,"Road hazard","",0,0,1500,1500);
    events.episodes[36].expires_at=2000;alert_events_tick(&events,2000);
    assert(events.episodes[36].current.lifecycle==2);
    for(unsigned i=0;i<100;i++)alert_events_observe(&events,1,i%2?3:0,false,"Oil","C",1,2,i,i);
    assert(events.count==64&&events.drops>0);
    n=alert_events_packet(&events,7,1,false,packet);assert(n==360&&packet[2]&1);
    n=alert_events_packet(&events,7,0,true,packet);assert(n>=24&&packet[1]==1);
    packet[24+23]=255;assert(!alert_event_decode(packet+24,&decoded));
    events.sequence=UINT32_MAX;alert_events_observe(&events,2,4,false,"Wrap","",1,2,100,100);
    assert(events.boot==8 && events.sequence==1 && events.count==1 && events.episodes[2].current.episode==1);
    uint8_t priorities[44]={0};priorities[2]=10;alert_event_t chosen;bool attention;
    alert_events_observe(&events,2,4,true,"Wrap","",0,0,100,101);
    alert_events_observe(&events,3,4,false,"Fresh critical","",1,2,101,101);
    assert(alert_events_summary(&events,priorities,101,&chosen,&attention) && chosen.key==3 && attention);
    threshold_integration();
    uint8_t external[64]={0x3e,0,2,1,7,0,0,0,1,0,0,0,0xe8,3};memcpy(external+16,"Road hazard",11);
    assert(alert_external_validate(external));external[2]=4;assert(!alert_external_validate(external));external[2]=2;
    external[16]=0xc0;assert(!alert_external_validate(external));external[16]=10;assert(!alert_external_validate(external));
    clear_state_t clear={0};
    assert(!clear_prepare(&clear,1,2,50,3,0,false,true));
    assert(clear_prepare(&clear,1,2,50,3,0,true,true));
    assert(!clear_confirm(&clear,1,2,50,3,30000,true,false,false));
    assert(!clear_confirm(&clear,1,2,50,3,100,true,true,false));
    assert(!clear_confirm(&clear,1,2,50,4,100,true,false,false));
    assert(!clear_confirm(&clear,1,2,50,3,100,false,false,false));
    assert(clear_confirm(&clear,1,2,50,3,100,true,false,false));
    assert(!clear_confirm(&clear,1,2,50,3,101,true,false,false));
    assert(!clear_prepare(&clear,4,5,50,3,101,true,true));
    clear_restore(&clear);assert(clear.phase==CLEAR_UNKNOWN);
    assert(!clear_confirm(&clear,1,2,50,3,102,true,false,false));
    assert(clear_prepare(&clear,4,5,50,3,103,true,true));
    assert(clear_confirm(&clear,4,5,50,3,104,true,false,false));
    puts("Alert lifecycle, bounded wire, expiry and at-most-once clear fixtures passed");
}
