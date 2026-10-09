#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <poll.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/wait.h>
#include <unistd.h>

typedef struct {
    int pen, mouse, slot, grabbed, dropped;
    int slot_supported, slot_sent;
    int x, y, range, tip, button;
    struct input_absinfo ax, ay;
    char path[64], name[256];
} Input;

#define BITS_PER_LONG (8 * sizeof(unsigned long))
#define SWITCH_WORDS ((SW_MAX + BITS_PER_LONG) / BITS_PER_LONG)
static int pen_inserted(Input *p) {
    if (!p->slot_supported) return 0;
    unsigned long switches[SWITCH_WORDS] = {0};
    if (ioctl(p->pen, EVIOCGSW(sizeof(switches)), switches) < 0) return -1;
    // Samsung's switch is active-low: 0 means stored, 1 means ejected.
    return !((switches[SW_PEN_INSERTED / BITS_PER_LONG] >> (SW_PEN_INSERTED % BITS_PER_LONG)) & 1);
}

static int relay_slot(Input *p, int inserted) {
    if (!p->slot_supported || p->slot_sent == inserted) return 0;
    // EVIOCGRAB also hides the physical slot switch from Android. Relay only
    // that switch through uinput, keeping Samsung's charging/attach state real.
    struct input_event events[2] = {
        {.type = EV_SW, .code = SW_PEN_INSERTED, .value = !inserted},
        {.type = EV_SYN, .code = SYN_REPORT}
    };
    if (write(p->slot, events, sizeof(events)) != sizeof(events)) return -1;
    p->slot_sent = inserted;
    return 0;
}

static void fail(JNIEnv *env, const char *label) {
    char message[256];
    snprintf(message, sizeof(message), "%s: %s", label, strerror(errno));
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/IOException"), message);
}

static void snapshot(Input *p) {
    unsigned long keys[(KEY_MAX + 8 * sizeof(long)) / (8 * sizeof(long))] = {0};
    ioctl(p->pen, EVIOCGKEY(sizeof(keys)), keys);
    #define KEY(code) ((keys[(code) / (8 * sizeof(long))] >> ((code) % (8 * sizeof(long)))) & 1)
    p->range = KEY(BTN_TOOL_PEN) || KEY(BTN_TOOL_RUBBER);
    p->tip = KEY(BTN_TOUCH);
    p->button = KEY(BTN_STYLUS);
    struct input_absinfo a;
    if (!ioctl(p->pen, EVIOCGABS(ABS_X), &a)) p->x = a.value;
    if (!ioctl(p->pen, EVIOCGABS(ABS_Y), &a)) p->y = a.value;
}

JNIEXPORT jlong JNICALL Java_dev_spenmouse_NativeInput_open(JNIEnv *env, jclass clazz) {
    (void)clazz;
    Input *p = calloc(1, sizeof(Input));
    if (!p) { fail(env, "allocate"); return 0; }
    p->pen = p->mouse = p->slot = -1;
    p->slot_sent = -1;
    for (int n = 0; n < 64; n++) {
        char path[64], name[256] = {0};
        snprintf(path, sizeof(path), "/dev/input/event%d", n);
        int fd = open(path, O_RDONLY | O_NONBLOCK | O_CLOEXEC);
        if (fd < 0) continue;
        ioctl(fd, EVIOCGNAME(sizeof(name)), name);
        if (strstr(name, "sec_e-pen") || strstr(name, "sec_epen")) {
            // A switch-only relay shares the Samsung configuration name.
            // Only the physical digitizer has valid pen axes.
            if (ioctl(fd, EVIOCGABS(ABS_X), &p->ax) < 0 || ioctl(fd, EVIOCGABS(ABS_Y), &p->ay) < 0 ||
                p->ax.maximum <= p->ax.minimum || p->ay.maximum <= p->ay.minimum) { close(fd); continue; }
            p->pen = fd;
            snprintf(p->path, sizeof(p->path), "%s", path);
            snprintf(p->name, sizeof(p->name), "%s", name);
            break;
        }
        close(fd);
    }
    if (p->pen < 0) { errno = ENODEV; fail(env, "S Pen input device"); free(p); return 0; }
    unsigned long switches[SWITCH_WORDS] = {0};
    if (ioctl(p->pen, EVIOCGBIT(EV_SW, sizeof(switches)), switches) < 0) {
        fail(env, "S Pen switch capabilities"); close(p->pen); free(p); return 0;
    }
    p->slot_supported = (switches[SW_PEN_INSERTED / BITS_PER_LONG] >> (SW_PEN_INSERTED % BITS_PER_LONG)) & 1;
    if (ioctl(p->pen, EVIOCGABS(ABS_X), &p->ax) < 0 || ioctl(p->pen, EVIOCGABS(ABS_Y), &p->ay) < 0 ||
        p->ax.maximum <= p->ax.minimum || p->ay.maximum <= p->ay.minimum) {
        fail(env, "S Pen axes"); close(p->pen); free(p); return 0;
    }
    // Registers a real mouse identity. All motion is injected with absolute coordinates in Java.
    p->mouse = open("/dev/uinput", O_WRONLY | O_NONBLOCK | O_CLOEXEC);
    if (p->mouse < 0) { fail(env, "open uinput"); close(p->pen); free(p); return 0; }
    int rc = ioctl(p->mouse, UI_SET_EVBIT, EV_KEY);
    if (rc >= 0) rc = ioctl(p->mouse, UI_SET_KEYBIT, BTN_LEFT);
    if (rc >= 0) rc = ioctl(p->mouse, UI_SET_KEYBIT, BTN_RIGHT);
    if (rc >= 0) rc = ioctl(p->mouse, UI_SET_EVBIT, EV_REL);
    if (rc >= 0) rc = ioctl(p->mouse, UI_SET_RELBIT, REL_X);
    if (rc >= 0) rc = ioctl(p->mouse, UI_SET_RELBIT, REL_Y);
    struct uinput_setup setup = {0};
    setup.id.bustype = BUS_VIRTUAL;
    setup.id.vendor = 0x5350; setup.id.product = 0x0001; setup.id.version = 1;
    snprintf(setup.name, sizeof(setup.name), "S Pen Mouse");
    if (rc >= 0) rc = ioctl(p->mouse, UI_DEV_SETUP, &setup);
    if (rc >= 0) rc = ioctl(p->mouse, UI_DEV_CREATE);
    if (rc < 0) { fail(env, "create virtual mouse"); close(p->mouse); close(p->pen); free(p); return 0; }
    if (p->slot_supported) {
        p->slot = open("/dev/uinput", O_WRONLY | O_NONBLOCK | O_CLOEXEC);
        struct uinput_setup slot_setup = {0};
        slot_setup.id.bustype = BUS_VIRTUAL;
        slot_setup.id.vendor = 0x5350; slot_setup.id.product = 0x0004; slot_setup.id.version = 1;
        // Samsung's sec_e-pen.idc maps raw SW_PEN_INSERTED (0xf) to
        // Android's Samsung pen-insert switch (0x13). A generic name does not.
        snprintf(slot_setup.name, sizeof(slot_setup.name), "%s", p->name);
        if (p->slot < 0 || ioctl(p->slot, UI_SET_EVBIT, EV_SW) < 0 ||
            ioctl(p->slot, UI_SET_SWBIT, SW_PEN_INSERTED) < 0 ||
            ioctl(p->slot, UI_DEV_SETUP, &slot_setup) < 0 || ioctl(p->slot, UI_DEV_CREATE) < 0) {
            fail(env, "create S Pen slot relay");
            if (p->slot >= 0) close(p->slot);
            ioctl(p->mouse, UI_DEV_DESTROY); close(p->mouse); close(p->pen); free(p); return 0;
        }
    }
    int inserted = pen_inserted(p);
    if (inserted < 0 || relay_slot(p, inserted) < 0) {
        fail(env, "initialize S Pen slot");
        if (p->slot >= 0) { ioctl(p->slot, UI_DEV_DESTROY); close(p->slot); }
        ioctl(p->mouse, UI_DEV_DESTROY); close(p->mouse); close(p->pen); free(p); return 0;
    }
    snapshot(p);
    return (jlong)(intptr_t)p;
}

JNIEXPORT jboolean JNICALL Java_dev_spenmouse_NativeInput_penInserted(JNIEnv *env, jclass clazz, jlong handle) {
    (void)clazz;
    Input *p = (Input *)(intptr_t)handle;
    int inserted = pen_inserted(p);
    if (inserted < 0 || relay_slot(p, inserted) < 0) { fail(env, "S Pen slot state"); return JNI_FALSE; }
    return inserted ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL Java_dev_spenmouse_NativeInput_read(JNIEnv *env, jclass clazz, jlong handle, jintArray out) {
    (void)clazz;
    Input *p = (Input *)(intptr_t)handle;
    struct pollfd fd = {.fd = p->pen, .events = POLLIN};
    int rc = poll(&fd, 1, 20);
    if (rc < 0 && errno == EINTR) return 0;
    if (rc < 0 || (fd.revents & (POLLHUP | POLLERR | POLLNVAL))) { errno = ENODEV; fail(env, "read S Pen"); return -1; }
    if (!rc) return 0;
    struct input_event event;
    while (read(p->pen, &event, sizeof(event)) == sizeof(event)) {
        if (event.type == EV_SYN && event.code == SYN_DROPPED) { p->dropped = 1; continue; }
        if (p->dropped && !(event.type == EV_SYN && event.code == SYN_REPORT)) continue;
        if (event.type == EV_ABS) {
            if (event.code == ABS_X) p->x = event.value;
            if (event.code == ABS_Y) p->y = event.value;
        } else if (event.type == EV_KEY) {
            if (event.code == BTN_TOOL_PEN || event.code == BTN_TOOL_RUBBER) p->range = event.value != 0;
            if (event.code == BTN_TOUCH) p->tip = event.value != 0;
            if (event.code == BTN_STYLUS) p->button = event.value != 0;
        } else if (event.type == EV_SYN && event.code == SYN_REPORT) {
            int wasDropped = p->dropped;
            if (p->dropped) { snapshot(p); p->dropped = 0; }
            jint frame[10] = {p->x, p->y, p->range, p->tip, p->button,
                p->ax.minimum, p->ax.maximum, p->ay.minimum, p->ay.maximum, wasDropped};
            (*env)->SetIntArrayRegion(env, out, 0, 10, frame);
            return 1;
        }
    }
    if (errno != EAGAIN && errno != EWOULDBLOCK) { fail(env, "S Pen stream"); return -1; }
    return 0;
}

JNIEXPORT jboolean JNICALL Java_dev_spenmouse_NativeInput_grab(JNIEnv *env, jclass clazz, jlong handle, jboolean value) {
    (void)clazz;
    Input *p = (Input *)(intptr_t)handle;
    if (p->grabbed == (int)value) return JNI_TRUE;
    if(value){
        struct input_event discard;
        while(read(p->pen,&discard,sizeof(discard))==sizeof(discard)){}
        snapshot(p);
        // Android must see the physical HOVER_EXIT before we take this stream.
        // Capturing an existing hover leaves a native stylus session behind;
        // after docking it can still suppress fingers and conflict with mouse input.
        if(p->range || p->tip || p->button)return JNI_FALSE;
        int inserted = pen_inserted(p);
        if (inserted < 0) { fail(env, "S Pen slot before capture"); return JNI_FALSE; }
        if (inserted) return JNI_FALSE;
    }
    if (ioctl(p->pen, EVIOCGRAB, value ? 1 : 0) < 0) { fail(env, "exclusive S Pen grab"); return JNI_FALSE; }
    p->grabbed = value;
    snapshot(p);
    if (value) {
        int inserted = pen_inserted(p);
        if (inserted != 0) {
            int saved_errno = errno;
            if (ioctl(p->pen, EVIOCGRAB, 0) < 0) { fail(env, "release stored S Pen"); return JNI_FALSE; }
            p->grabbed = 0;
            if (inserted < 0) { errno = saved_errno; fail(env, "S Pen slot after capture"); }
            return JNI_FALSE;
        }
    }
    return JNI_TRUE;
}

typedef struct {int socket;pid_t pid;} Guard;
static int guard_ack(int fd) {
    struct pollfd pollfd={.fd=fd,.events=POLLIN};
    int ready;do{ready=poll(&pollfd,1,15000);}while(ready<0 && errno==EINTR);
    if(ready<=0){errno=ETIMEDOUT;return -1;}
    char reply;if(recv(fd,&reply,1,0)!=1 || reply!='O'){errno=EIO;return -1;}return 0;
}
JNIEXPORT jlong JNICALL Java_dev_spenmouse_NativeInput_guardOpen(JNIEnv *env,jclass clazz,jstring executable,jint user) {
    (void)clazz;
    const char *utf=(*env)->GetStringUTFChars(env,executable,NULL);char *path=strdup(utf);
    (*env)->ReleaseStringUTFChars(env,executable,utf);
    char userstr[16];snprintf(userstr,sizeof(userstr),"%d",user);
    int sockets[2];if(socketpair(AF_UNIX,SOCK_SEQPACKET|SOCK_CLOEXEC,0,sockets)<0){free(path);fail(env,"settings guard socket");return 0;}
    pid_t child=fork();
    if(child==0) {
        close(sockets[0]);
        if(sockets[1]!=3){dup2(sockets[1],3);close(sockets[1]);}else fcntl(3,F_SETFD,0);
        char *argv[]={path,userstr,NULL};execv(path,argv);_exit(127);
    }
    free(path);close(sockets[1]);
    if(child<0){close(sockets[0]);fail(env,"start settings guard");return 0;}
    if(guard_ack(sockets[0])<0){close(sockets[0]);fail(env,"settings guard initialization");return 0;}
    Guard *g=malloc(sizeof(Guard));if(!g){close(sockets[0]);fail(env,"allocate guard");return 0;}
    g->socket=sockets[0];g->pid=child;return (jlong)(intptr_t)g;
}
JNIEXPORT void JNICALL Java_dev_spenmouse_NativeInput_guardSetActive(JNIEnv *env,jclass clazz,jlong handle,jboolean active) {
    (void)clazz;Guard *g=(Guard*)(intptr_t)handle;
    char command=active?'A':'R';
    if(send(g->socket,&command,1,MSG_NOSIGNAL)!=1 || guard_ack(g->socket)<0)fail(env,"Samsung gesture settings");
}
JNIEXPORT void JNICALL Java_dev_spenmouse_NativeInput_guardClose(JNIEnv *env,jclass clazz,jlong handle) {
    (void)env;(void)clazz;Guard *g=(Guard*)(intptr_t)handle;if(!g)return;
    if(send(g->socket,"X",1,MSG_NOSIGNAL)==1)guard_ack(g->socket);
    close(g->socket);waitpid(g->pid,NULL,WNOHANG);free(g);
}

JNIEXPORT void JNICALL Java_dev_spenmouse_NativeInput_close(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env; (void)clazz;
    Input *p = (Input *)(intptr_t)handle;
    if (!p) return;
    int inserted = pen_inserted(p);
    if (inserted >= 0) relay_slot(p, inserted);
    if (p->grabbed) ioctl(p->pen, EVIOCGRAB, 0);
    close(p->pen);
    ioctl(p->mouse, UI_DEV_DESTROY);
    close(p->mouse);
    if (p->slot >= 0) { ioctl(p->slot, UI_DEV_DESTROY); close(p->slot); }
    free(p);
}

JNIEXPORT jstring JNICALL Java_dev_spenmouse_NativeInput_describe(JNIEnv *env, jclass clazz, jlong handle) {
    (void)clazz;
    Input *p = (Input *)(intptr_t)handle;
    char info[512];
    snprintf(info, sizeof(info), "%s (%s), X %d..%d, Y %d..%d, slot switch %s", p->name, p->path,
        p->ax.minimum, p->ax.maximum, p->ay.minimum, p->ay.maximum, p->slot_supported ? "available" : "unavailable");
    return (*env)->NewStringUTF(env, info);
}
