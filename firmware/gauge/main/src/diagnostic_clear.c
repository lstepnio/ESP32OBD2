#include "diagnostic_clear.h"
#include "diagnostics_state.h"
#include "transfer_gate.h"
#include "sdkconfig.h"
#include "esp_random.h"
#include "nvs.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include <string.h>

static clear_state_t state;
static SemaphoreHandle_t mutex;
static QueueHandle_t queue;
static bool pending;
typedef struct { uint8_t bytes[14]; } request_t;
static uint32_t u32(const uint8_t *p) { return p[0]|((uint32_t)p[1]<<8)|((uint32_t)p[2]<<16)|((uint32_t)p[3]<<24); }
static bool save(const clear_state_t *value) {
    nvs_handle_t handle;if(nvs_open("diag_clear",NVS_READWRITE,&handle)!=ESP_OK)return false;
    esp_err_t err=nvs_set_blob(handle,"intent_v1",value,sizeof(*value));
    if(err==ESP_OK)err=nvs_commit(handle);
    nvs_close(handle);return err==ESP_OK;
}
esp_err_t diagnostic_clear_init(void) {
    mutex=xSemaphoreCreateMutex();queue=xQueueCreate(4,sizeof(request_t));
    if(!mutex||!queue)return ESP_ERR_NO_MEM;
    bool journal_available=true;
    nvs_handle_t handle;esp_err_t opened=nvs_open("diag_clear",NVS_READONLY,&handle);
    if(opened==ESP_OK) {
        size_t n=sizeof(state);
        esp_err_t loaded=nvs_get_blob(handle,"intent_v1",&state,&n);
        if(loaded!=ESP_OK && loaded!=ESP_ERR_NVS_NOT_FOUND)journal_available=false;
        if(loaded==ESP_OK && (n!=sizeof(state)||state.phase>CLEAR_FAILED||state.reserved))journal_available=false;
        if(!journal_available)state=(clear_state_t){.phase=CLEAR_UNKNOWN};
        nvs_close(handle);clear_restore(&state);
    } else if(opened!=ESP_ERR_NVS_NOT_FOUND)journal_available=false;
#ifdef CONFIG_EGAUGE_DTC_CLEAR_ENABLED
    state.supported=journal_available;if(journal_available && !state.operation)state.phase=CLEAR_IDLE;
#else
    state.supported=0;if(!state.operation)state.phase=journal_available?CLEAR_DISABLED:CLEAR_UNKNOWN;
#endif
    return ESP_OK;
}
bool diagnostic_clear_command(const uint8_t *bytes,size_t length) {
    if(!queue||!bytes)return false;
    if((bytes[0]==0x60 && length!=10)||(bytes[0]==0x61 && length!=14)||
       (bytes[0]!=0x60&&bytes[0]!=0x61)||bytes[1]>1)return false;
    request_t r={0};memcpy(r.bytes,bytes,length);return xQueueSend(queue,&r,0)==pdTRUE;
}
void diagnostic_clear_tick(uint32_t now,bool running,bool moving) {
    if(!mutex||xSemaphoreTake(mutex,0)!=pdTRUE)return;
    if(pending && now-state.prepared_at>=30000) { pending=false;state.phase=CLEAR_FAILED;transfer_gate_release(3); }
    request_t request;
    if(xQueueReceive(queue,&request,0)==pdTRUE) {
        uint8_t snapshot[248];uint8_t *b=request.bytes;
        bool checked=diagnostics_endpoint_status(0,now,snapshot)!=0;
        bool connected=checked && (snapshot[2]&4) && !(snapshot[2]&8);
        uint32_t revision=checked?u32(snapshot+4):0, session=checked?u32(snapshot+8):0;
        if(b[0]==0x60) {
            if(b[1]==0 && u32(b+6)==revision && transfer_gate_current()==0)
                clear_prepare(&state,u32(b+2),esp_random()|1U,revision,session,now,state.supported,connected);
        } else if(!pending && state.supported && connected && transfer_gate_claim(3)) {
            if(clear_confirm(&state,u32(b+2),u32(b+6),u32(b+10),session,now,b[1]==1,running,moving))pending=true;
            else transfer_gate_release(3);
        }
    }
    xSemaphoreGive(mutex);
}
bool diagnostic_clear_take(unsigned transport,uint32_t now,clear_state_t *out) {
    if(transport!=diagnostics_endpoint_transport(0)||!mutex||xSemaphoreTake(mutex,0)!=pdTRUE)return false;
    bool ready=pending;pending=false;if(ready)*out=state;xSemaphoreGive(mutex);
    if(!ready)return false;
    uint8_t snapshot[248];
    if(!diagnostics_endpoint_status(0,now,snapshot)||!(snapshot[2]&4)||u32(snapshot+8)!=out->session||u32(snapshot+4)!=out->revision) {
        diagnostic_clear_finish(out->operation,CLEAR_FAILED);return false;
    }
    // Intent is durable before any ECU side effect. Failure to journal forbids send.
    if(!save(out)) { diagnostic_clear_finish(out->operation,CLEAR_FAILED);return false; }
    return true;
}
void diagnostic_clear_finish(uint32_t operation,clear_phase_t phase) {
    clear_state_t copy={0};
    if(mutex&&xSemaphoreTake(mutex,portMAX_DELAY)==pdTRUE) {
        if(state.operation==operation)state.phase=phase;
        copy=state;xSemaphoreGive(mutex);
    }
    save(&copy);transfer_gate_release(3);
}
size_t diagnostic_clear_status(uint8_t out[32]) {
    if(!mutex||xSemaphoreTake(mutex,0)!=pdTRUE)return 0;
    memset(out,0,32);out[0]=18;out[1]=state.phase;out[2]=state.supported;out[3]=state.endpoint;
    memcpy(out+4,&state.operation,4);memcpy(out+8,&state.token,4);memcpy(out+12,&state.revision,4);
    memcpy(out+16,&state.session,4);memcpy(out+20,&state.prepared_at,4);
    xSemaphoreGive(mutex);return 32;
}
