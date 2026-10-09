package dev.spenmouse;

public final class LaunchSessionCheck {
    public static void main(String[] args) {
        LaunchSession session=new LaunchSession();
        session.begin("game",10042,100);
        require(!session.observe("dev.spenmouse","MainActivity",true,200),"Preparing the launch must not end it");
        require(session.uidFor("game")==-1,"A pending launch must not capture an app before it opens");
        session.observe("game","LoadingActivity",true,300);
        require(session.uidFor("game")==10042 && session.uidFor("other")==-1,"Only the launched app may receive forced input");
        require(!session.observe("game","GameActivity",true,400),"Navigation inside the app must keep the session");
        require(!session.observe("","",true,500),"A missing task observation must only pause capture");
        require(!session.observe("com.android.systemui","NotificationShade",true,600),"System panels must preserve the session");
        require(!session.observe("com.google.android.permissioncontroller","GrantPermissionsActivity",true,700),"Permission dialogs must preserve the session");
        require(!session.observe("launcher","HomeActivity",false,800),"The lock screen must only pause the session");
        require(session.observe("launcher","HomeActivity",true,900) && !session.active(),"Home must end the override");

        session.begin("game",10042,1000);session.observe("game","GameActivity",true,1100);
        require(session.observe("other","MainActivity",true,1200),"Switching to another app must end the override");
        require(session.uidFor("game")==-1,"Returning through Recents must not restore an expired override");
        session.begin("game",10042,2000);session.observe("game","GameActivity",true,2100);
        require(session.observe("com.android.systemui","RecentsActivity",true,2200),"Recents is navigation, not a temporary panel");

        session.begin("dev.spenmouse",10027,3000);
        require(!session.observe("dev.spenmouse","MainActivity",true,3100) && session.uidFor("dev.spenmouse")==-1,"Our launcher must never be captured as the test");
        session.observe("dev.spenmouse","dev.spenmouse.TestActivity",true,3200);
        require(session.uidFor("dev.spenmouse")==10027,"The built-in test must support one-time launch");
        require(session.observe("dev.spenmouse","MainActivity",true,3300),"Back from the test must end its session");

        session.begin("game",10042,4000);
        require(!session.observe("launcher","HomeActivity",true,33999),"Allow time for a cold app launch");
        require(session.observe("launcher","HomeActivity",true,34000) && !session.active(),"A failed launch must time out");
        session.begin("game",10042,35000);session.observe("game","GameActivity",true,35001);
        session.begin("other",10043,35002);
        require(session.uidFor("game")==-1,"A new launch must replace the former target");
        session.observe("other","MainActivity",true,35003);session.clear();
        require(!session.active() && session.uidFor("other")==-1,"Explicit Stop must clear the override");
        System.out.println("Passed launch preparation, target isolation, system/lock pauses, Home/Back/app exits, timeout, replacement, and Stop.");
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
