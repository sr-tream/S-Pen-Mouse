package dev.spenmouse;
final class NativeControls {
    static native long open();
    static native boolean grab(long handle, boolean active);
    // minX, maxX, minY, maxY, dropped; then ten (trackingId, x, y) slots.
    static native int read(long handle, int[] frame);
    static native void relay(long handle, int[] allowedSlots);
    static native void close(long handle);
}
