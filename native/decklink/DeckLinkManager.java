package org.churchpresenter.app.churchpresenter.composables;
public class DeckLinkManager {
    private static native String[] nativeListDevices();
    private static native boolean nativeOpen(int deviceIndex, int width, int height);
    private static native void nativeSendFrame(int[] pixels, int width, int height);
    private static native boolean nativeStartScheduledPlayback(int deviceIndex, double fps);
    private static native void nativeScheduleFrame(int[] pixels, int width, int height);
    private static native void nativeStopPlayback();
    private static native void nativeClose();
}
