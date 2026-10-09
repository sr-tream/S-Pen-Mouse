package dev.spenmouse;

public final class CenterHoldCheck {
    public static void main(String[] args) {
        CenterHold hold=new CenterHold();
        hold.update(7,100,500);hold.update(7,599,500);
        require(!hold.ready,"Short center touch must not show settings");
        hold.update(7,600,500);long first=hold.gesture;
        require(hold.ready && first==1,"Stationary contact must show settings after the dwell");
        hold.update(7,900,500);require(hold.gesture==first,"One dwell must not repeatedly trigger");
        hold.update(-1,901,500);require(!hold.ready,"Leaving center must hide settings immediately");
        hold.update(7,902,500);require(!hold.ready,"Returning to center must start a new dwell");
        hold.update(7,1402,500);require(hold.ready && hold.gesture==first+1,"A new dwell needs a new gesture ID");
        hold.update(8,1403,500);require(!hold.ready,"A replacement finger must not inherit the dwell");
        hold.update(8,1903,500);hold.clear();require(!hold.ready,"Focus loss or editor opening must hide settings");
        System.out.println("Passed center dwell, stationary hold, exit/re-entry, finger replacement, and cancellation.");
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
