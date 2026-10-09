package dev.spenmouse;

/** A dwell belongs to one physical contact; leaving the center requires a new dwell. */
final class CenterHold {
    private int contact=-1;
    private long since;
    volatile boolean ready;
    volatile long gesture;
    void update(int id,long now,int timeout) {
        if(id<0){clear();return;}
        if(contact!=id){contact=id;since=now;ready=false;}
        if(!ready && now-since>=timeout){gesture++;ready=true;}
    }
    void clear(){contact=-1;ready=false;}
}
