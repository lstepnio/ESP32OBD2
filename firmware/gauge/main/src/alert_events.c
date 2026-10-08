#include "alert_events.h"
#include <math.h>
#include <string.h>

static void put(uint8_t *p, uint32_t v) { for (unsigned i=0;i<4;i++) p[i]=(uint8_t)(v>>(i*8)); }
static uint32_t get(const uint8_t *p) { return p[0]|((uint32_t)p[1]<<8)|((uint32_t)p[2]<<16)|((uint32_t)p[3]<<24); }
static void text(char *out, size_t size, const char *value) {
    memset(out,0,size); if (value) { size_t n=strlen(value); if(n>=size)n=size-1; while(n && ((unsigned char)value[n]&0xc0)==0x80)n--; memcpy(out,value,n); }
}
void alert_event_encode(const alert_event_t *e, uint8_t out[84]) {
    memset(out,0,84); put(out,e->sequence); put(out+4,e->boot); put(out+8,e->episode); put(out+12,e->at);
    out[16]=e->key; out[17]=e->key>>8; out[18]=e->severity; out[19]=e->unavailable;
    out[20]=e->lifecycle; out[21]=e->kind; out[22]=e->acknowledged; out[23]=e->reserved;
    uint32_t f; memcpy(&f,&e->value,4); put(out+24,f); memcpy(&f,&e->limit,4); put(out+28,f);
    memcpy(out+32,e->label,32); memcpy(out+64,e->unit,12); put(out+76,e->observed_at); put(out+80,e->revision);
}
bool alert_event_decode(const uint8_t in[84], alert_event_t *e) {
    if (!in || !e || in[18]>4 || in[19]>1 || in[20]>2 || in[21]>5 || in[22]>1 || (in[23]&~3U) ||
        in[63] || in[75] || (in[16]|((unsigned)in[17]<<8))>=ALERT_EVENT_KEYS) return false;
    memset(e,0,sizeof(*e)); e->sequence=get(in); e->boot=get(in+4); e->episode=get(in+8); e->at=get(in+12);
    e->key=in[16]|((unsigned)in[17]<<8); e->severity=in[18]; e->unavailable=in[19];
    e->lifecycle=in[20]; e->kind=in[21]; e->acknowledged=in[22]; e->reserved=in[23];
    uint32_t f=get(in+24); memcpy(&e->value,&f,4); f=get(in+28); memcpy(&e->limit,&f,4);
    memcpy(e->label,in+32,32); memcpy(e->unit,in+64,12); e->observed_at=get(in+76); e->revision=get(in+80);
    return e->sequence && e->boot && e->episode;
}
static void append(alert_events_t *s, alert_event_t *e, uint8_t kind, uint32_t now) {
    if(s->sequence==UINT32_MAX) {
        alert_event_t renewed=*e;unsigned key=e->key;renewed.episode=0;
        memset(s->episodes,0,sizeof(s->episodes));s->episodes[key].current=renewed;
        s->boot=s->boot==UINT32_MAX?1:s->boot+1;s->sequence=0;s->count=s->head=0;s->drops++;
    }
    e->reserved=(e->reserved&2U)|((s->simulated_keys>>e->key)&1U); e->revision=s->revision; e->boot=s->boot; e->sequence=++s->sequence; e->at=now; e->kind=kind;
    if (!e->episode) e->episode=e->sequence;
    s->ring[s->head]=*e; s->head=(s->head+1)%ALERT_EVENT_RING;
    if (s->count<ALERT_EVENT_RING) s->count++; else s->drops++;
}
void alert_events_init(alert_events_t *s, uint32_t boot) { memset(s,0,sizeof(*s)); s->boot=boot?boot:1; }
void alert_events_observe(alert_events_t *s, unsigned key, uint8_t severity, bool unavailable,
    const char *label, const char *unit, float value, float limit, uint32_t observed_at, uint32_t now) {
    if (!s || key>=ALERT_EVENT_KEYS || severity>4) return;
    alert_episode_t *p=&s->episodes[key]; alert_event_t *e=&p->current;
    if (!e->episode && (!severity || unavailable)) return;
    if (unavailable && e->lifecycle==1) { severity=e->severity;value=e->value;limit=e->limit;observed_at=e->observed_at; }
    bool entry=severity && e->lifecycle!=1;
    bool changed=entry || severity!=e->severity || unavailable!=e->unavailable;
    if (entry) { memset(p,0,sizeof(*p)); e=&p->current; }
    uint8_t previous=e->severity; e->key=key; e->severity=severity; e->unavailable=unavailable;
    e->lifecycle=severity?1:0; e->value=value; e->limit=limit; e->observed_at=observed_at;
    text(e->label,sizeof(e->label),label); text(e->unit,sizeof(e->unit),unit);
    if (severity>previous) { p->acknowledged_severity=0; p->snooze_ms=0; e->reserved&=~2U; }
    e->acknowledged=p->acknowledged_severity>=severity && severity>0;
    if (changed) append(s,e,entry?1:!severity?3:severity!=previous?2:4,now);
}
bool alert_events_ack(alert_events_t *s, unsigned key, uint32_t episode, uint32_t boot,
    uint32_t snooze_ms, uint32_t now) {
    if (!s || key>=ALERT_EVENT_KEYS || boot!=s->boot || snooze_ms>300000) return false;
    alert_episode_t *p=&s->episodes[key];
    if (p->current.lifecycle!=1 || p->current.episode!=episode || p->current.boot!=boot) return false;
    if (snooze_ms) { if(p->snooze_ms && now-p->snooze_at<p->snooze_ms)return true; p->snooze_at=now; p->snooze_ms=snooze_ms;p->current.reserved|=2U; }
    else { if(p->current.acknowledged)return true; p->snooze_ms=0;p->current.reserved&=~2U;p->acknowledged_severity=p->current.severity; p->current.acknowledged=1; }
    append(s,&p->current,5,now); return true;
}
bool alert_events_attention(const alert_episode_t *p, uint32_t now) {
    return p && p->current.lifecycle==1 && !p->current.unavailable &&
        p->current.severity>p->acknowledged_severity &&
        (!p->snooze_ms || now-p->snooze_at>=p->snooze_ms);
}
void alert_events_tick(alert_events_t *s, uint32_t now) {
    for(unsigned k=0;k<ALERT_EVENT_KEYS;k++) {
        alert_episode_t *p=&s->episodes[k];
        if(p->snooze_ms && now-p->snooze_at>=p->snooze_ms) { p->snooze_ms=0;p->current.reserved&=~2U;append(s,&p->current,5,now); }
        if(k>=36 && p->current.lifecycle==1 && p->expires_at && (int32_t)(now-p->expires_at)>=0) {
            p->current.lifecycle=2; append(s,&p->current,3,now); p->expires_at=0;
        }
    }
}
size_t alert_events_packet(const alert_events_t *s, uint32_t boot, uint32_t cursor,
    bool active, uint8_t out[ALERT_EVENT_PACKET]) {
    memset(out,0,ALERT_EVENT_PACKET); out[0]=16; out[1]=active?1:0;
    put(out+4,s->boot); put(out+12,s->sequence); put(out+16,s->drops);
    uint32_t oldest=s->count?s->ring[(s->head+ALERT_EVENT_RING-s->count)%ALERT_EVENT_RING].sequence:s->sequence+1;
    put(out+8,oldest);
    if (!active && (boot!=s->boot || (cursor && cursor<oldest-1) || cursor>s->sequence)) { out[2]=1; cursor=0; }
    unsigned n=0; uint32_t next=cursor;
    if(active) {
        for(unsigned i=cursor;i<ALERT_EVENT_KEYS;i++) {
            next=i+1;
            if(s->episodes[i].current.lifecycle==1) {
                alert_event_encode(&s->episodes[i].current,out+24+n*84); if(++n==4)break;
            }
        }
        if(next>=ALERT_EVENT_KEYS) out[2]|=2;
    } else {
        for(unsigned i=0;i<s->count;i++) {
            const alert_event_t *e=&s->ring[(s->head+ALERT_EVENT_RING-s->count+i)%ALERT_EVENT_RING];
            if(e->sequence<=cursor)continue;
            alert_event_encode(e,out+24+n*84); next=e->sequence; if(++n==4)break;
        }
        if(next>=s->sequence)out[2]|=2;
    }
    out[3]=n; put(out+20,next); return 24+n*84;
}

/* Reject malformed UTF-8, C0/C1 controls and nonzero padding at the trust boundary. */
static bool valid_text(const uint8_t *p,size_t capacity,bool required) {
    size_t at=0;if(required && !p[0])return false;
    while(at<capacity && p[at]) {
        uint32_t code;unsigned continuation;uint8_t first=p[at++];
        if(first<0x80) { code=first;continuation=0; }
        else if(first>=0xc2 && first<=0xdf) { code=first&0x1f;continuation=1; }
        else if(first>=0xe0 && first<=0xef) { code=first&0xf;continuation=2; }
        else if(first>=0xf0 && first<=0xf4) { code=first&7;continuation=3; }
        else return false;
        if(at+continuation>=capacity)return false;
        for(unsigned j=0;j<continuation;j++) { if((p[at]&0xc0)!=0x80)return false;code=(code<<6)|(p[at++]&0x3f); }
        if(code<32 || (code>=0x7f && code<=0x9f) || (continuation==1 && code<0x80) ||
           (continuation==2 && code<0x800) || (continuation==3 && code<0x10000) ||
           code>0x10ffff || (code>=0xd800 && code<=0xdfff))return false;
    }
    if(at==capacity)return false;
    while(at<capacity)if(p[at++])return false;
    return true;
}
bool alert_external_validate(const uint8_t request[64]) {
    return request && request[0]==0x3e && request[1]<8 && request[2]<=3 && request[3]<=1 &&
        get(request+4) && get(request+8) && get(request+8)<=INT32_MAX &&
        get(request+12)<=600000 && (!get(request+12) || request[2]>0) &&
        valid_text(request+16,32,true) && valid_text(request+48,12,false) && !get(request+60);
}

bool alert_events_summary(const alert_events_t *events,const uint8_t priorities[44],uint32_t now,alert_event_t *out,bool *attention) {
    if(!events || !out || !attention)return false;
    bool found=false;*attention=false;
    for(unsigned key=0;key<44;key++) {
        const alert_episode_t *p=&events->episodes[key];if(p->current.lifecycle!=1)continue;
        bool wants_attention=alert_events_attention(p,now);
        if(!found || p->current.severity>out->severity || (p->current.severity==out->severity &&
            ((wants_attention && !*attention) || (wants_attention==*attention && priorities[key]>priorities[out->key])))) {
            *out=p->current;*attention=wants_attention;found=true;
        }
    }
    return found;
}
