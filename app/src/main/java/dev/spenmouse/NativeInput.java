package dev.spenmouse;

final class NativeInput {
    static native long open();
    // x, y, in-range, tip, barrel, minX, maxX, minY, maxY, dropped
    static native int read(long handle, int[] frame);
    static native boolean grab(long handle, boolean value);
    // Reads the kernel slot switch even during capture and relays it to Android.
    static native boolean penInserted(long handle);
    static native void close(long handle);
    static native String describe(long handle);
    static native long guardOpen(String executable, int user);
    static native void guardSetActive(long guard, boolean active);
    static native void guardClose(long guard);
}
