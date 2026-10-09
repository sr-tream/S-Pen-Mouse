package dev.spenmouse;

/** An in-memory override that lasts for one foreground visit to an app. */
final class LaunchSession {
    private String pkg="";
    private int uid=-1;
    private boolean entered;
    private long deadline;

    synchronized void begin(String target,int targetUid,long now) {
        if(target==null || target.isEmpty() || targetUid<10000)throw new IllegalArgumentException("Invalid launch target");
        pkg=target;uid=targetUid;entered=false;deadline=now+30000;
    }
    synchronized String packageName(){return pkg;}
    synchronized boolean active(){return !pkg.isEmpty();}
    synchronized int uidFor(String target){return entered && pkg.equals(target)?uid:-1;}
    synchronized void clear(){pkg="";uid=-1;entered=false;}

    /** Returns true when this observation ends the override. */
    synchronized boolean observe(String top,String activity,boolean screenUsable,long now) {
        if(pkg.isEmpty())return false;
        boolean target=pkg.equals(top) && (!pkg.equals("dev.spenmouse") || activity.endsWith("TestActivity"));
        if(!entered) {
            if(screenUsable && target){entered=true;return false;}
            if(now<deadline)return false;
        } else if(!screenUsable || top.isEmpty() || temporarySystemActivity(top,activity) || target) return false;
        clear();return true;
    }
    private static boolean temporarySystemActivity(String pkg,String activity) {
        if(activity.toLowerCase(java.util.Locale.ROOT).contains("recents"))return false;
        return pkg.equals("com.android.systemui") || pkg.equals("android") ||
            pkg.equals("com.android.permissioncontroller") || pkg.equals("com.google.android.permissioncontroller");
    }
}
