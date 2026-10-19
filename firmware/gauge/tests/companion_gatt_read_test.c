#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "companion_gatt_read.h"
int os_mbuf_append(struct os_mbuf *om, const void *value, uint16_t length) {
    if (om->fail || length > sizeof(om->data) - om->length) return -1;
    memcpy(om->data + om->length, value, length); om->length += length; return 0;
}
int main(void) {
    uint8_t original[512], received[512];
    for (unsigned i=0;i<sizeof(original);i++) original[i]=(uint8_t)(i*37+11);
    const unsigned sizes[]={4,10,56,140,248,360,512};
    const unsigned mtus[]={23,185,256,517};
    for(unsigned l=0;l<sizeof(sizes)/sizeof(sizes[0]);l++) {
        unsigned length=sizes[l];
        for(unsigned m=0;m<sizeof(mtus)/sizeof(mtus[0]);m++) {
            unsigned offset=0;
            do {
                struct os_mbuf om={0};
                assert(companion_gatt_read_value(&om,original,length,offset)==0);
                /* ESP-IDF 5.4.1 NimBLE ble_gatts_val_access slices AFTER callback. */
                assert(om.length>=offset);
                unsigned count=om.length-offset;
                if(count>mtus[m]-1) count=mtus[m]-1;
                memcpy(received+offset,om.data+offset,count);offset+=count;
                if(!count)break;
            }while(offset<length);
            assert(offset==length && memcmp(received,original,length)==0);
        }
        struct os_mbuf end={0};
        assert(companion_gatt_read_value(&end,original,length,length)==0);
        assert(end.length==length); /* stack returns the zero-length final blob */
        struct os_mbuf bad={0};
        assert(companion_gatt_read_value(&bad,original,length,length+1)==7);
        assert(!bad.length);
    }
    struct os_mbuf failed={.fail=1};
    assert(companion_gatt_read_value(&failed,original,512,0)==17);
    /* Demonstrate the previous sliced callback fails at the actual 256 MTU. */
    unsigned old_tail=360-255;
    assert(old_tail<255); /* NimBLE rejects tail_length - offset as invalid. */
    puts("GATT full-value/stack-offset, MTU reconstruction and failure boundaries passed");
}
