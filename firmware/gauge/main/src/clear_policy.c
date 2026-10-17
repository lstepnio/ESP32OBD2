#include "diagnostic_clear.h"
#include <string.h>
bool clear_prepare(clear_state_t *s,uint32_t operation,uint32_t token,uint32_t revision,
    uint32_t session,uint32_t now,bool supported,bool connected) {
    if(!s||!operation||!token||!supported||!connected)return false;
    if(s->operation==operation)return s->phase==CLEAR_PREPARED;
    if(s->phase==CLEAR_SENT||s->phase==CLEAR_VERIFYING)return false;
    *s=(clear_state_t){.operation=operation,.token=token,.revision=revision,.session=session,
        .prepared_at=now,.phase=CLEAR_PREPARED,.supported=1};return true;
}
bool clear_confirm(clear_state_t *s,uint32_t operation,uint32_t token,uint32_t revision,
    uint32_t session,uint32_t now,bool parked,bool running,bool moving) {
    if(!s||s->phase!=CLEAR_PREPARED||s->operation!=operation||s->token!=token||
        s->revision!=revision||s->session!=session||now-s->prepared_at>=30000||
        !parked||running||moving)return false;
    s->phase=CLEAR_SENT;return true;
}
void clear_restore(clear_state_t *s) {
    if(s&&(s->phase==CLEAR_SENT||s->phase==CLEAR_VERIFYING))s->phase=CLEAR_UNKNOWN;
}
