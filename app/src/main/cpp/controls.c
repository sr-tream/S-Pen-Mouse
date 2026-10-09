#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

#define SLOTS 10
typedef struct {
    int touch, relay, keyboard, grabbed, slot, dropped;
    int id[SLOTS], x[SLOTS], y[SLOTS], major[SLOTS], minor[SLOTS], sent[SLOTS];
    struct input_absinfo ax, ay;
} Controls;

static void error(JNIEnv *env, const char *label) {
    char message[256];snprintf(message,sizeof(message),"%s: %s",label,strerror(errno));
    (*env)->ThrowNew(env,(*env)->FindClass(env,"java/io/IOException"),message);
}
static int event(int fd,int type,int code,int value) {
    struct input_event e={.type=type,.code=code,.value=value};
    return write(fd,&e,sizeof(e))==sizeof(e)?0:-1;
}
static void clear_relay(Controls *c) {
    if(c->relay<0)return;
    for(int n=0;n<SLOTS;n++)if(c->sent[n]>=0) {
        event(c->relay,EV_ABS,ABS_MT_SLOT,n);event(c->relay,EV_ABS,ABS_MT_TRACKING_ID,-1);c->sent[n]=-1;
    }
    event(c->relay,EV_KEY,BTN_TOUCH,0);event(c->relay,EV_SYN,SYN_REPORT,0);
}
static void dispose(Controls *c) {
    if(!c)return;
    clear_relay(c);
    if(c->touch>=0){if(c->grabbed)ioctl(c->touch,EVIOCGRAB,0);close(c->touch);}
    if(c->relay>=0){ioctl(c->relay,UI_DEV_DESTROY);close(c->relay);}
    if(c->keyboard>=0){ioctl(c->keyboard,UI_DEV_DESTROY);close(c->keyboard);}
    free(c);
}
static int abs_axis(int fd,int code,int min,int max) {
    if(ioctl(fd,UI_SET_ABSBIT,code)<0)return -1;
    struct uinput_abs_setup a={.code=code};a.absinfo.minimum=min;a.absinfo.maximum=max;
    return ioctl(fd,UI_ABS_SETUP,&a);
}
static int create_device(int fd,const char *name,int product) {
    struct uinput_setup s={0};s.id.bustype=BUS_VIRTUAL;s.id.vendor=0x5350;s.id.product=product;s.id.version=1;
    snprintf(s.name,sizeof(s.name),"%s",name);
    if(ioctl(fd,UI_DEV_SETUP,&s)<0)return -1;
    return ioctl(fd,UI_DEV_CREATE);
}
static int snapshot(Controls *c) {
    struct input_absinfo slot;
    if(ioctl(c->touch,EVIOCGABS(ABS_MT_SLOT),&slot)<0)return -1;
    c->slot=slot.value;
    int values[SLOTS+1];values[0]=ABS_MT_TRACKING_ID;
    if(ioctl(c->touch,EVIOCGMTSLOTS(sizeof(values)),values)<0)return -1;
    for(int n=0;n<SLOTS;n++)c->id[n]=values[n+1];
    values[0]=ABS_MT_POSITION_X;
    if(ioctl(c->touch,EVIOCGMTSLOTS(sizeof(values)),values)<0)return -1;
    for(int n=0;n<SLOTS;n++)c->x[n]=values[n+1];
    values[0]=ABS_MT_POSITION_Y;
    if(ioctl(c->touch,EVIOCGMTSLOTS(sizeof(values)),values)<0)return -1;
    for(int n=0;n<SLOTS;n++)c->y[n]=values[n+1];
    values[0]=ABS_MT_TOUCH_MAJOR;
    if(ioctl(c->touch,EVIOCGMTSLOTS(sizeof(values)),values)==0)for(int n=0;n<SLOTS;n++)c->major[n]=values[n+1];
    values[0]=ABS_MT_TOUCH_MINOR;
    if(ioctl(c->touch,EVIOCGMTSLOTS(sizeof(values)),values)==0)for(int n=0;n<SLOTS;n++)c->minor[n]=values[n+1];
    return 0;
}
JNIEXPORT jlong JNICALL Java_dev_spenmouse_NativeControls_open(JNIEnv *env,jclass cls) {
    (void)cls;Controls *c=calloc(1,sizeof(*c));
    if(!c){error(env,"allocate camera input");return 0;}
    c->touch=c->relay=c->keyboard=-1;
    for(int n=0;n<SLOTS;n++)c->id[n]=c->sent[n]=-1;
    for(int n=0;n<64;n++) {
        char path[64],name[256]={0};snprintf(path,sizeof(path),"/dev/input/event%d",n);
        int fd=open(path,O_RDONLY|O_NONBLOCK|O_CLOEXEC);if(fd<0)continue;
        ioctl(fd,EVIOCGNAME(sizeof(name)),name);
        if(strcmp(name,"sec_touchscreen")==0){c->touch=fd;break;}close(fd);
    }
    if(c->touch<0){errno=ENODEV;error(env,"Samsung touchscreen");dispose(c);return 0;}
    struct input_absinfo slots;
    if(ioctl(c->touch,EVIOCGABS(ABS_MT_POSITION_X),&c->ax)<0 || ioctl(c->touch,EVIOCGABS(ABS_MT_POSITION_Y),&c->ay)<0 ||
       ioctl(c->touch,EVIOCGABS(ABS_MT_SLOT),&slots)<0 || slots.maximum!=SLOTS-1 || slots.minimum!=0 ||
       c->ax.maximum<=c->ax.minimum || c->ay.maximum<=c->ay.minimum) {
        errno=ENOTSUP;error(env,"touchscreen multitouch format");dispose(c);return 0;
    }
    c->relay=open("/dev/uinput",O_WRONLY|O_NONBLOCK|O_CLOEXEC);
    if(c->relay<0 || ioctl(c->relay,UI_SET_EVBIT,EV_KEY)<0 || ioctl(c->relay,UI_SET_KEYBIT,BTN_TOUCH)<0 ||
       ioctl(c->relay,UI_SET_EVBIT,EV_ABS)<0 || ioctl(c->relay,UI_SET_PROPBIT,INPUT_PROP_DIRECT)<0 ||
       abs_axis(c->relay,ABS_X,c->ax.minimum,c->ax.maximum)<0 || abs_axis(c->relay,ABS_Y,c->ay.minimum,c->ay.maximum)<0 ||
       abs_axis(c->relay,ABS_MT_SLOT,0,SLOTS-1)<0 || abs_axis(c->relay,ABS_MT_TRACKING_ID,0,65535)<0 ||
       abs_axis(c->relay,ABS_MT_POSITION_X,c->ax.minimum,c->ax.maximum)<0 ||
       abs_axis(c->relay,ABS_MT_POSITION_Y,c->ay.minimum,c->ay.maximum)<0 ||
       abs_axis(c->relay,ABS_MT_TOUCH_MAJOR,0,255)<0 || abs_axis(c->relay,ABS_MT_TOUCH_MINOR,0,255)<0 ||
       create_device(c->relay,"S Pen Touch Relay",3)<0) {
        error(env,"create touch relay");dispose(c);return 0;
    }
    c->keyboard=open("/dev/uinput",O_WRONLY|O_NONBLOCK|O_CLOEXEC);
    if(c->keyboard<0 || ioctl(c->keyboard,UI_SET_EVBIT,EV_KEY)<0 ||
       ioctl(c->keyboard,UI_SET_KEYBIT,KEY_UP)<0 || ioctl(c->keyboard,UI_SET_KEYBIT,KEY_RIGHT)<0 ||
       ioctl(c->keyboard,UI_SET_KEYBIT,KEY_DOWN)<0 || ioctl(c->keyboard,UI_SET_KEYBIT,KEY_LEFT)<0 ||
       create_device(c->keyboard,"S Pen Camera Keys",2)<0) {
        error(env,"create camera keyboard");dispose(c);return 0;
    }
    return (jlong)(intptr_t)c;
}
JNIEXPORT jboolean JNICALL Java_dev_spenmouse_NativeControls_grab(JNIEnv *env,jclass cls,jlong handle,jboolean active) {
    (void)cls;Controls *c=(Controls*)(intptr_t)handle;
    if(c->grabbed==(int)active)return JNI_TRUE;
    if(active) {
        struct input_event e;while(read(c->touch,&e,sizeof(e))==sizeof(e)){}
        if(snapshot(c)<0){error(env,"touch snapshot");return JNI_FALSE;}
        for(int n=0;n<SLOTS;n++)if(c->id[n]>=0)return JNI_FALSE;
    } else clear_relay(c);
    if(ioctl(c->touch,EVIOCGRAB,active?1:0)<0){error(env,"exclusive touch capture");return JNI_FALSE;}
    c->grabbed=active;c->dropped=0;return JNI_TRUE;
}
JNIEXPORT jint JNICALL Java_dev_spenmouse_NativeControls_read(JNIEnv *env,jclass cls,jlong handle,jintArray out) {
    (void)cls;Controls *c=(Controls*)(intptr_t)handle;struct input_event e;
    while(read(c->touch,&e,sizeof(e))==sizeof(e)) {
        if(e.type==EV_SYN && e.code==SYN_DROPPED){c->dropped=1;continue;}
        if(c->dropped && !(e.type==EV_SYN && e.code==SYN_REPORT))continue;
        if(e.type==EV_ABS) {
            if(e.code==ABS_MT_SLOT){if(e.value>=0 && e.value<SLOTS)c->slot=e.value;}
            else if(e.code==ABS_MT_TRACKING_ID)c->id[c->slot]=e.value;
            else if(e.code==ABS_MT_POSITION_X)c->x[c->slot]=e.value;
            else if(e.code==ABS_MT_POSITION_Y)c->y[c->slot]=e.value;
            else if(e.code==ABS_MT_TOUCH_MAJOR)c->major[c->slot]=e.value;
            else if(e.code==ABS_MT_TOUCH_MINOR)c->minor[c->slot]=e.value;
        } else if(e.type==EV_SYN && e.code==SYN_REPORT) {
            int dropped=c->dropped;if(dropped){snapshot(c);c->dropped=0;}
            jint frame[35]={c->ax.minimum,c->ax.maximum,c->ay.minimum,c->ay.maximum,dropped};
            for(int n=0;n<SLOTS;n++){frame[5+n*3]=c->id[n];frame[6+n*3]=c->x[n];frame[7+n*3]=c->y[n];}
            (*env)->SetIntArrayRegion(env,out,0,35,frame);return 1;
        }
    }
    if(errno!=EAGAIN && errno!=EWOULDBLOCK){error(env,"touch input stream");return -1;}return 0;
}
JNIEXPORT void JNICALL Java_dev_spenmouse_NativeControls_relay(JNIEnv *env,jclass cls,jlong handle,jintArray allowed) {
    (void)cls;Controls *c=(Controls*)(intptr_t)handle;jint pass[SLOTS];
    (*env)->GetIntArrayRegion(env,allowed,0,SLOTS,pass);int count=0,first=-1,rc=0;
    for(int n=0;n<SLOTS;n++)if(pass[n] && c->id[n]>=0){count++;if(first<0)first=n;}
    rc|=event(c->relay,EV_KEY,BTN_TOUCH,count!=0);
    for(int n=0;n<SLOTS;n++) {
        int id=pass[n]?c->id[n]:-1;
        if(id<0 && c->sent[n]<0)continue;
        rc|=event(c->relay,EV_ABS,ABS_MT_SLOT,n);
        if(id!=c->sent[n]) {
            if(c->sent[n]>=0)rc|=event(c->relay,EV_ABS,ABS_MT_TRACKING_ID,-1);
            if(id>=0)rc|=event(c->relay,EV_ABS,ABS_MT_TRACKING_ID,id);
        }
        if(id>=0) {
            rc|=event(c->relay,EV_ABS,ABS_MT_POSITION_X,c->x[n]);rc|=event(c->relay,EV_ABS,ABS_MT_POSITION_Y,c->y[n]);
            rc|=event(c->relay,EV_ABS,ABS_MT_TOUCH_MAJOR,c->major[n]);rc|=event(c->relay,EV_ABS,ABS_MT_TOUCH_MINOR,c->minor[n]);
        }
        c->sent[n]=id;
    }
    if(first>=0){rc|=event(c->relay,EV_ABS,ABS_X,c->x[first]);rc|=event(c->relay,EV_ABS,ABS_Y,c->y[first]);}
    rc|=event(c->relay,EV_SYN,SYN_REPORT,0);if(rc<0)error(env,"forward system touch");
}
JNIEXPORT void JNICALL Java_dev_spenmouse_NativeControls_close(JNIEnv *env,jclass cls,jlong handle) {
    (void)env;(void)cls;dispose((Controls*)(intptr_t)handle);
}
