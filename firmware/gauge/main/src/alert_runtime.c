#include "alert_runtime.h"
#include <string.h>
#include <math.h>
#include "esp_heap_caps.h"
#include "esp_random.h"
#include "nvs.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include "freertos/task.h"

static alert_events_t events;
static SemaphoreHandle_t mutex;
static QueueHandle_t commands;
static uint32_t saved_sequence, revision;
static bool storage_gap;
static uint8_t priorities[44];
static uint32_t stale_ms[32];
static bool capture_partial;
static unsigned capture_pid_count;
static uint32_t external_sequences[8];
typedef struct { uint32_t at; float value; uint8_t pid,valid; uint16_t reserved; } context_sample_t;
typedef struct {
    uint32_t boot,episode,revision,start;
    uint16_t key,count;
    bool complete,partial;
    context_sample_t samples[480];
} context_capture_t;
typedef struct {
    context_sample_t latest[32], before[160];
    unsigned before_count,before_head;
    uint32_t sampled_at;
    context_capture_t captures[2];
} context_store_t;
static context_store_t *context_store;
static void start_capture(const alert_event_t *event,uint32_t now) {
    if(!context_store)return;
    unsigned slot=0;
    if(context_store->captures[0].episode && (!context_store->captures[1].episode ||
       context_store->captures[1].start<context_store->captures[0].start))slot=1;
    context_capture_t *capture=&context_store->captures[slot];
    memset(capture,0,sizeof(*capture)); capture->boot=event->boot;capture->episode=event->episode;
    capture->revision=event->revision;capture->key=event->key;capture->start=now;
    capture->partial=capture_partial || context_store->before_count<10*capture_pid_count;
    for(unsigned i=0;i<context_store->before_count;i++)
        capture->samples[capture->count++]=context_store->before[(context_store->before_head+10*capture_pid_count-context_store->before_count+i)%(10*capture_pid_count)];
}

typedef struct { uint8_t bytes[64]; size_t length; } command_t;
static uint32_t u32(const uint8_t *p) { return p[0]|((uint32_t)p[1]<<8)|((uint32_t)p[2]<<16)|((uint32_t)p[3]<<24); }
/* Latest 32 compact transitions use less than one NVS page of payload. NVS
 * commit is atomic; failure retains RAM state and advertises a storage gap. */
static void journal_task(void *arg) {
    (void)arg;
    uint8_t *blob=heap_caps_malloc(16+32*84,MALLOC_CAP_8BIT);
    if(!blob) { if(xSemaphoreTake(mutex,pdMS_TO_TICKS(20))==pdTRUE) { storage_gap=true;xSemaphoreGive(mutex); } vTaskDelete(NULL);return; }
    for (;;) {
        vTaskDelay(pdMS_TO_TICKS(5000));
        if (xSemaphoreTake(mutex,pdMS_TO_TICKS(20))!=pdTRUE) continue;
        if (events.sequence==saved_sequence) { xSemaphoreGive(mutex); continue; }
        memset(blob,0,16+32*84); blob[0]=1;
        unsigned count=events.count<32?events.count:32; blob[1]=count;
        memcpy(blob+4,&revision,4);
        uint32_t sequence=events.sequence;
        for(unsigned i=0;i<count;i++)alert_event_encode(&events.ring[(events.head+64-count+i)%64],blob+16+i*84);
        xSemaphoreGive(mutex);
        nvs_handle_t handle;
        esp_err_t err=nvs_open("alerts",NVS_READWRITE,&handle);
        if(err==ESP_OK) {
            err=nvs_set_blob(handle,"events",blob,16+count*84);
            if(err==ESP_OK)err=nvs_commit(handle);
            nvs_close(handle);
        }
        if(xSemaphoreTake(mutex,pdMS_TO_TICKS(20))==pdTRUE) {
            if(err==ESP_OK)saved_sequence=sequence;
            storage_gap=err!=ESP_OK;
            xSemaphoreGive(mutex);
        }
    }
}
esp_err_t alert_runtime_init(uint32_t config_revision) {
    context_store=heap_caps_calloc(1,sizeof(*context_store),MALLOC_CAP_SPIRAM|MALLOC_CAP_8BIT);
    revision=config_revision; alert_events_init(&events,esp_random()); events.revision=revision;
    mutex=xSemaphoreCreateMutex(); commands=xQueueCreate(16,sizeof(command_t));
    if(!mutex||!commands)return ESP_ERR_NO_MEM;
    nvs_handle_t handle;
    if(nvs_open("alerts",NVS_READONLY,&handle)==ESP_OK) {
        uint8_t *blob=heap_caps_malloc(16+32*84,MALLOC_CAP_8BIT);size_t length=16+32*84;
        if(blob && nvs_get_blob(handle,"events",blob,&length)==ESP_OK && length>=16 && blob[0]==1 && blob[1]<=32 && length==16+(size_t)blob[1]*84) {
            for(unsigned i=0;i<blob[1];i++) {
                alert_event_t e;
                if(!alert_event_decode(blob+16+i*84,&e)) { storage_gap=true;events.count=events.head=0; events.sequence=0;break; }
                events.ring[events.head++]=e; events.count++;
                events.sequence=e.sequence;
            }
            /* A reboot leaves a coverage gap even when committed events survive. */
            events.drops++;
        }
        heap_caps_free(blob);nvs_close(handle);
    }
    saved_sequence=events.sequence;
    return xTaskCreate(journal_task,"alert_journal",4096,NULL,2,NULL)==pdPASS?ESP_OK:ESP_ERR_NO_MEM;
}
void alert_runtime_observe(unsigned key,uint8_t severity,bool unavailable,const char *label,
    const char *unit,float value,float limit,uint32_t observed_at,uint32_t now) {
    if(!mutex||xSemaphoreTake(mutex,0)!=pdTRUE)return;
    uint32_t previous=key<44?events.episodes[key].current.sequence:0;
    alert_events_observe(&events,key,severity,unavailable,label,unit,value,limit,observed_at,now);
    if(key<36 && events.episodes[key].current.sequence!=previous &&
        (events.episodes[key].current.kind==1||events.episodes[key].current.kind==2))
        start_capture(&events.episodes[key].current,now);
    xSemaphoreGive(mutex);
}
bool alert_runtime_command(const uint8_t *bytes,size_t length) {
    if(!commands||!bytes||!length||length>64)return false;
    if(bytes[0]==0x3d) {
        if(length!=16||bytes[1]>=44||bytes[2]||bytes[3]||!u32(bytes+4)||!u32(bytes+8)||u32(bytes+12)>300000)return false;
    } else if(bytes[0]==0x3e) {
        if(length!=64||!alert_external_validate(bytes))return false;
    } else return false;
    command_t c={.length=length}; memcpy(c.bytes,bytes,length);
    return xQueueSend(commands,&c,0)==pdTRUE;
}
void alert_runtime_tick(uint32_t now) {
    if(!mutex||xSemaphoreTake(mutex,0)!=pdTRUE)return;
    command_t c;
    for(unsigned i=0;i<16&&xQueueReceive(commands,&c,0)==pdTRUE;i++) {
        uint8_t *b=c.bytes;
        if(b[0]==0x3d)alert_events_ack(&events,b[1],u32(b+4),u32(b+8),u32(b+12),now);
        else if(u32(b+4)==events.boot && (int32_t)(u32(b+8)-external_sequences[b[1]])>0) {
            external_sequences[b[1]]=u32(b+8);
            unsigned key=36+b[1]; uint32_t ttl=u32(b+12);
            if(b[3])events.simulated_keys|=UINT64_C(1)<<key;else events.simulated_keys&=~(UINT64_C(1)<<key);
            alert_events_observe(&events,key,ttl?b[2]:0,false,(char*)b+16,(char*)b+48,0,0,now,now);
            events.episodes[key].expires_at=ttl?now+ttl:0;
            if(ttl && !events.episodes[key].expires_at)events.episodes[key].expires_at=1;
        }
    }
    alert_events_tick(&events,now);
    if(context_store && now-context_store->sampled_at>=1000) {
        context_store->sampled_at=now;
        for(unsigned i=0;i<capture_pid_count;i++) {
            context_sample_t sample=context_store->latest[i];sample.pid=i;
            if(now-sample.at>(stale_ms[i]?stale_ms[i]:2000))sample.valid=0;
            sample.at=now;
            context_store->before[context_store->before_head]=sample;
            context_store->before_head=(context_store->before_head+1)%(10*capture_pid_count);
            if(context_store->before_count<10*capture_pid_count)context_store->before_count++;
            for(unsigned capture_index=0;capture_index<2;capture_index++) {
                context_capture_t *capture=&context_store->captures[capture_index];
                if(!capture->episode||capture->complete)continue;
                if(now-capture->start>=20000) { capture->complete=true;continue; }
                if(capture->count<480)capture->samples[capture->count++]=sample;
                if(capture->count>=480)capture->complete=true;
            }
        }
        for(unsigned i=0;i<2;i++)if(context_store->captures[i].episode && now-context_store->captures[i].start>=20000)context_store->captures[i].complete=true;
    }
    xSemaphoreGive(mutex);
}
size_t alert_runtime_packet(uint32_t boot,uint32_t cursor,bool active,uint8_t out[360]) {
    if(!mutex||xSemaphoreTake(mutex,0)!=pdTRUE)return 0;
    size_t n=alert_events_packet(&events,boot,cursor,active,out);if(storage_gap)out[2]|=4;xSemaphoreGive(mutex);return n;
}
bool alert_runtime_summary(uint32_t now,alert_event_t *out,bool *attention) {
    if(!mutex||!out||!attention||xSemaphoreTake(mutex,0)!=pdTRUE)return false;
    bool found=alert_events_summary(&events,priorities,now,out,attention);
    xSemaphoreGive(mutex);return found;
}

void alert_runtime_sample(unsigned pid,float value,uint32_t at) {
    if(!context_store||pid>=32||!mutex||xSemaphoreTake(mutex,0)!=pdTRUE)return;
    context_store->latest[pid]=(context_sample_t){.at=at,.value=value,.pid=pid,.valid=isfinite(value)};
    xSemaphoreGive(mutex);
}
void alert_runtime_simulated(unsigned key,bool value) {
    if(key>=44)return;
    if(value)events.simulated_keys|=UINT64_C(1)<<key;
}
size_t alert_runtime_context(unsigned key,uint32_t boot,uint32_t episode,uint32_t offset,uint8_t out[216]) {
    if(!mutex||xSemaphoreTake(mutex,0)!=pdTRUE)return 0;
    memset(out,0,216);out[0]=17;out[2]=key;
    memcpy(out+4,&boot,4);memcpy(out+8,&episode,4);memcpy(out+20,&offset,4);
    size_t length=24;
    if(context_store)for(unsigned i=0;i<2;i++) {
        context_capture_t *c=&context_store->captures[i];
        if(c->key!=key||c->boot!=boot||c->episode!=episode)continue;
        if(offset>c->count)break;
        out[1]=1|(c->complete?2:0)|(c->partial?4:0);
        memcpy(out+12,&c->revision,4);uint32_t count=c->count;memcpy(out+16,&count,4);
        unsigned n=c->count-offset;if(n>16)n=16;
        for(unsigned j=0;j<n;j++) {
            const context_sample_t *sample=&c->samples[offset+j];uint8_t *p=out+24+j*12;
            memcpy(p,&sample->at,4);memcpy(p+4,&sample->value,4);p[8]=sample->pid;p[9]=sample->valid;
        }
        length+=n*12;break;
    }
    xSemaphoreGive(mutex);return length;
}

void alert_runtime_priority(unsigned key,uint8_t priority) { if(key<44)priorities[key]=priority; }
void alert_runtime_stale(unsigned pid,uint32_t milliseconds) { if(pid<32)stale_ms[pid]=milliseconds; if(pid>=16)capture_partial=true;else if(capture_pid_count<pid+1)capture_pid_count=pid+1; }
