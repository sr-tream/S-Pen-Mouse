// Android-hosted native check: fake ioctl/write; never opens or captures input.
#include <assert.h>
#include <stdarg.h>
#include <linux/input.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <string.h>

static int raw_slot, hover, grabs, releases, writes;
static struct input_event relayed[2];
static int check_ioctl(int fd, unsigned long request, ...) {
    (void)fd;
    va_list args;va_start(args,request);
    if(request==EVIOCGRAB) {
        int enabled=va_arg(args,int);if(enabled)grabs++;else releases++;
    } else if(request==EVIOCGSW(sizeof(unsigned long))) {
        *va_arg(args,unsigned long*)=(unsigned long)raw_slot<<SW_PEN_INSERTED;
    } else if(request==EVIOCGKEY((KEY_MAX+8*sizeof(long))/(8*sizeof(long))*sizeof(unsigned long))) {
        unsigned long *keys=va_arg(args,unsigned long*);
        memset(keys,0,(KEY_MAX+8*sizeof(long))/(8*sizeof(long))*sizeof(unsigned long));
        if(hover)keys[BTN_TOOL_PEN/(8*sizeof(long))]|=1UL<<(BTN_TOOL_PEN%(8*sizeof(long)));
    } else if(request==EVIOCGABS(ABS_X) || request==EVIOCGABS(ABS_Y)) {
        memset(va_arg(args,struct input_absinfo*),0,sizeof(struct input_absinfo));
    } else {assert(!"Unexpected ioctl");}
    va_end(args);return 0;
}
static ssize_t check_write(int fd,const void *data,size_t size) {
    (void)fd;assert(size==sizeof(relayed));memcpy(relayed,data,size);writes++;return size;
}
#define ioctl check_ioctl
#define write check_write
#include "../app/src/main/cpp/input.c"
#undef ioctl
#undef write

int main(void) {
    Input pen={.pen=-1,.mouse=-1,.slot=-1,.slot_supported=1,.slot_sent=-1};
    raw_slot=0;
    assert(pen_inserted(&pen)==1);
    assert(relay_slot(&pen,1)==0 && relayed[0].type==EV_SW && relayed[0].code==SW_PEN_INSERTED && relayed[0].value==0);
    assert(relayed[1].type==EV_SYN && relayed[1].code==SYN_REPORT);
    assert(relay_slot(&pen,1)==0 && writes==1);
    assert(!Java_dev_spenmouse_NativeInput_grab(NULL,NULL,(jlong)(intptr_t)&pen,JNI_TRUE) && grabs==0);
    raw_slot=1;hover=1;
    assert(pen_inserted(&pen)==0);
    assert(relay_slot(&pen,0)==0 && relayed[0].value==1 && writes==2);
    assert(!Java_dev_spenmouse_NativeInput_grab(NULL,NULL,(jlong)(intptr_t)&pen,JNI_TRUE) && grabs==0);
    hover=0;
    assert(Java_dev_spenmouse_NativeInput_grab(NULL,NULL,(jlong)(intptr_t)&pen,JNI_TRUE) && grabs==1);
    assert(Java_dev_spenmouse_NativeInput_grab(NULL,NULL,(jlong)(intptr_t)&pen,JNI_FALSE) && releases==1);
    pen.slot_supported=0;raw_slot=0;
    assert(pen_inserted(&pen)==0 && relay_slot(&pen,1)==0 && writes==2);
    puts("Passed Samsung slot polarity, raw switch relay, no duplicate reports, stored/hover capture refusal, ejected capture, release, and missing-switch fallback.");
    return 0;
}
