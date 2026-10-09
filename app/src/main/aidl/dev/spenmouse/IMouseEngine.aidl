package dev.spenmouse;
import android.os.Bundle;
import dev.spenmouse.ICameraUi;
interface IMouseEngine {
    void configure(in String[] packages, in int[] uids, boolean enabled, boolean hoverBridgeReady) = 0;
    Bundle getState() = 1;
    void stop() = 2;
    Bundle selfTest() = 3;
    void updateBridgeHeartbeat(long lastSeen) = 4;
    void configureCamera(in Bundle settings) = 5;
    Bundle cameraSelfTest() = 6;
    void setCameraEditing(boolean editing) = 7;
    void setCameraUi(in Bundle bounds, ICameraUi callback) = 8;
    void beginLaunch(String packageName, int uid) = 9;
    void cancelLaunch() = 10;
    void destroy() = 16777114;
}
