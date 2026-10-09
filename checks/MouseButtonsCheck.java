package dev.spenmouse;

public final class MouseButtonsCheck {
    public static void main(String[] args) {
        MouseButtons buttons=new MouseButtons();
        require(buttons.frame(true,false,10)==1,"Default touch must hold LMB");
        require(buttons.frame(true,true,20)==3,"Default must support both buttons");
        require(buttons.frame(false,true,30)==2,"Lifting the tip must retain held RMB");
        require(buttons.frame(false,false,40)==0,"Releasing both must clear input");
        buttons.configure(MouseButtons.INVERTED,1500);
        require(buttons.frame(true,false,50)==2,"Inverted touch must hold RMB");
        require(buttons.frame(true,true,60)==3,"Inverted pen button must add LMB");
        require(buttons.frame(false,true,70)==1,"Inverted barrel must hold LMB after tip lifts");
        buttons.suspend(80);

        buttons.configure(MouseButtons.SWAP,1500);
        require(buttons.touchButton()==1 && buttons.frame(false,true,100)==0,"Hover press must swap without clicking");
        require(buttons.touchButton()==2 && buttons.state().popupVisible(100),"Hover swap must show its new RMB state");
        buttons.frame(false,true,6000);
        require(buttons.state().sequence()==1 && buttons.state().popupVisible(6000),"Holding the button must not repeatedly toggle or time out");
        buttons.frame(false,false,6010);
        require(buttons.state().popupVisible(7509) && !buttons.state().popupVisible(7510),"Popup duration must start at release");
        require(buttons.frame(true,false,8000)==2,"After swapping, touch must hold RMB");
        require(buttons.frame(true,true,8010)==3 && buttons.touchButton()==2,"A contact press must add LMB without swapping");
        require(buttons.frame(false,true,8020)==1 && buttons.state().sequence()==1,"Lifting during a chord must retain its original barrel role");
        require(buttons.frame(false,false,8030)==0,"Chord release must clear LMB");
        buttons.frame(false,true,8100);buttons.frame(false,false,8101);
        require(buttons.touchButton()==1 && buttons.state().oldButton()==2 && buttons.state().newButton()==1,"A short hover press must swap back");
        require(buttons.state().popupVisible(8200),"A press shorter than the UI polling interval must retain its popup");

        require(buttons.frame(true,true,9000)==3 && buttons.touchButton()==1,"Simultaneous tip and barrel contact must chord, not swap");
        buttons.frame(true,false,9010);buttons.frame(false,false,9020);
        buttons.frame(false,true,9030);
        require(buttons.frame(true,true,9040)==2,"Touch after a hover swap must use the new action without an extra button");
        require(buttons.frame(true,false,9050)==2,"Releasing the swap button must not release a held touch");
        buttons.suspend(9060);
        require(buttons.touchButton()==2 && buttons.frame(true,false,9100)==2,"A pause must clear physical holds while retaining this app's swap state");
        buttons.suspend(9101);

        buttons.configure(MouseButtons.SWAP,-10);
        require(buttons.state().popupMs()==300,"Popup minimum must be 300 ms");
        buttons.frame(false,true,10000);buttons.frame(false,false,10001);
        require(buttons.state().popupVisible(10300) && !buttons.state().popupVisible(10301),"Minimum popup expiry is wrong");
        buttons.configure(MouseButtons.SWAP,99999);require(buttons.state().popupMs()==5000,"Popup maximum must be 5 seconds");
        MouseButtons other=new MouseButtons();other.configure(MouseButtons.SWAP,1500);
        other.frame(false,true,11000);
        require(other.touchButton()==2 && buttons.touchButton()==1,"Different app controllers must keep independent swap states");
        buttons.configure(MouseButtons.DEFAULT,1500);
        require(buttons.touchButton()==1 && !buttons.state().popupVisible(12000),"Changing mode must reset swap state and popup");
        buttons.configure(-123,1500);require(buttons.mode()==MouseButtons.DEFAULT,"Invalid saved modes must safely default");
        System.out.println("Passed default/inverted buttons, hover toggles, contact chords, press-role latching, short/long popup timing, bounds, pauses, and app isolation.");
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
