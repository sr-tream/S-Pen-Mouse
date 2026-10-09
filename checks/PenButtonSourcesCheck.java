package dev.spenmouse;

public final class PenButtonSourcesCheck {
    public static void main(String[] args) {
        PenButtonSources sources=new PenButtonSources();
        MouseButtons mapping=new MouseButtons();mapping.configure(MouseButtons.SWAP,1500);
        sources.remote(true);apply(sources,mapping,10);
        require(mapping.touchButton()==2 && mapping.state().swapHeld(),"Outside-hover BLE press must swap and hold popup");
        sources.remote(true);apply(sources,mapping,11);
        sources.frame(false,false,false);apply(sources,mapping,12);
        require(mapping.state().sequence()==1 && mapping.state().swapHeld(),"Repeated BLE down or out-of-range frame must not reswap/release");
        sources.frame(true,false,true);apply(sources,mapping,20);
        require(mapping.state().sequence()==1,"Entering hover while held must not swap twice");
        sources.remote(false);apply(sources,mapping,21);
        require(mapping.state().swapHeld(),"Digitizer must retain a held press if BLE up arrives first");
        sources.frame(true,false,false);apply(sources,mapping,22);
        require(!mapping.state().swapHeld() && mapping.state().releasedAt()==22,"Both sources must release the popup");
        sources.remote(true);apply(sources,mapping,30);
        require(mapping.state().sequence()==1,"A late duplicate BLE down in hover must not swap");
        sources.frame(true,false,true);apply(sources,mapping,31);
        require(mapping.state().sequence()==2,"Digitizer must still work when BLE arrives first in hover");
        sources.frame(false,false,false);apply(sources,mapping,32);
        require(mapping.state().sequence()==2 && mapping.state().swapHeld(),"Leaving hover with both sources held must retain one press");
        sources.remote(false);apply(sources,mapping,33);
        require(!mapping.state().swapHeld(),"Outside-hover release must end the hold");
        sources.frame(true,true,false);apply(sources,mapping,40);
        sources.remote(true);apply(sources,mapping,41);
        sources.frame(true,true,true);
        require(apply(sources,mapping,42)==3 && mapping.state().sequence()==2,"Contact chord must remain a chord with BLE duplicates");
        sources.remote(false);sources.frame(true,false,false);apply(sources,mapping,43);
        sources.frame(true,false,true);apply(sources,mapping,50);
        sources.frame(false,false,false);apply(sources,mapping,51);
        sources.remote(true);apply(sources,mapping,52);
        require(mapping.state().sequence()==3,"Late BLE for a press exiting hover must not swap again");
        sources.remote(false);apply(sources,mapping,53);
        sources.remote(true);apply(sources,mapping,54);sources.remote(false);apply(sources,mapping,55);
        require(mapping.state().sequence()==4 && mapping.state().popupVisible(100),"Next short remote press must be handled");
        sources.remote(true);apply(sources,mapping,60);sources.reset();mapping.suspend(61);
        require(!sources.button() && !sources.tip() && !mapping.state().swapHeld(),"Session pause must clear all sources and holds");
        System.out.println("Passed remote presses, short presses, duplicate edges, hover entry/exit, source handoff, contact chords, and reset.");
    }
    private static int apply(PenButtonSources s,MouseButtons m,long now){return m.frame(s.tip(),s.button(),now);}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
