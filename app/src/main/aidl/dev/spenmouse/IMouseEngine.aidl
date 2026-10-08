package dev.spenmouse;
import android.os.Bundle;
interface IMouseEngine {
    void configure(in String[] packages, in int[] uids, boolean enabled, boolean hoverBridgeReady) = 0;
    Bundle getState() = 1;
    void stop() = 2;
    Bundle selfTest() = 3;
    void updateBridgeHeartbeat(long lastSeen) = 4;
    void destroy() = 16777114;
}
