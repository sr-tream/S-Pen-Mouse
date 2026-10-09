package dev.spenmouse;

/** One physical press can arrive through both the digitizer and Samsung BLE. */
final class PenButtonSources {
    private static final int NONE=0,DIGITIZER=1,REMOTE=2;
    private boolean range,tip,digitizer,remote,blockedRemote;
    private int owner;
    boolean tip(){return tip;}
    boolean button(){return owner!=NONE;}
    void frame(boolean inRange,boolean contact,boolean barrel) {
        boolean previous=digitizer;
        range=inRange;tip=inRange && contact;digitizer=inRange && barrel;
        if(owner==DIGITIZER && !digitizer) {
            if(remote)owner=REMOTE;
            else {owner=NONE;if(!inRange && previous)blockedRemote=true;}
        }else if(digitizer && !previous && owner==NONE)owner=DIGITIZER;
    }
    void remote(boolean down) {
        if(down && !remote) {
            remote=true;
            // BLE may duplicate or lag a digitizer press. Only start a new press outside hover.
            if(owner==NONE && !range && !blockedRemote)owner=REMOTE;
        }else if(!down) {
            remote=false;blockedRemote=false;
            if(owner==REMOTE)owner=digitizer?DIGITIZER:NONE;
        }
    }
    void reset(){range=tip=digitizer=remote=blockedRemote=false;owner=NONE;}
}
