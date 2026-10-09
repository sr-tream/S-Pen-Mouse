package dev.spenmouse;

/** Maps physical contacts; a barrel press keeps the role it had at press time. */
final class MouseButtons {
    static final int DEFAULT=0, INVERTED=1, SWAP=2, LMB=1, RMB=2;
    private int mode, popupMs=1500, touchHeld, barrelClick;
    private boolean rightTouch, tipDown, barrelDown, swapHeld;
    private long swapSequence, releasedAt=-1;
    private int swapOld=LMB, swapNew=RMB;
    record State(int mode,int touchButton,int popupMs,boolean swapHeld,long sequence,long releasedAt,int oldButton,int newButton) {
        boolean popupVisible(long now){return mode==SWAP && sequence>0 && (swapHeld || releasedAt>=0 && now<releasedAt+popupMs);}
    }
    static int validMode(int value){return value>=DEFAULT && value<=SWAP?value:DEFAULT;}
    static int validPopupMs(int value){return Math.max(300,Math.min(5000,value));}
    int mode(){return mode;}
    void configure(int value,int duration) {
        value=validMode(value);
        if(mode!=value) {
            mode=value;rightTouch=false;touchHeld=barrelClick=0;
            tipDown=barrelDown=swapHeld=false;swapSequence=0;releasedAt=-1;
        }
        popupMs=validPopupMs(duration);
    }
    int touchButton(){return mode==INVERTED || mode==SWAP && rightTouch?RMB:LMB;}
    int frame(boolean tip,boolean barrel,long now) {
        if(barrel && !barrelDown) {
            if(mode==SWAP && !tip) {
                swapOld=touchButton();rightTouch=!rightTouch;swapNew=touchButton();
                swapSequence++;swapHeld=true;releasedAt=-1;barrelClick=0;
            } else {
                barrelClick=mode==DEFAULT?RMB:mode==INVERTED?LMB:opposite(tipDown?touchHeld:touchButton());
                swapHeld=false;
            }
        } else if(!barrel && barrelDown) {
            if(swapHeld)releasedAt=now;
            swapHeld=false;barrelClick=0;
        }
        if(tip && !tipDown)touchHeld=touchButton();else if(!tip)touchHeld=0;
        tipDown=tip;barrelDown=barrel;
        return touchHeld|barrelClick;
    }
    void suspend(long now) {
        if(swapHeld)releasedAt=now;
        swapHeld=tipDown=barrelDown=false;touchHeld=barrelClick=0;
    }
    State state(){return new State(mode,touchButton(),popupMs,swapHeld,swapSequence,releasedAt,swapOld,swapNew);}
    static int opposite(int button){return button==LMB?RMB:LMB;}
    static String label(int button){return button==RMB?"RMB":"LMB";}
}
