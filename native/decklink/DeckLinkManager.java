package org.churchpresenter.app.churchpresenter.composables;
public class DeckLinkManager {
    // Output
    private static native String[] nativeListDevices();
    private static native boolean nativeOpen(int deviceIndex, int width, int height);
    private static native void nativeSendFrame(int deviceIndex, int[] pixels, int width, int height);
    private static native boolean nativeStartScheduledPlayback(int deviceIndex, double fps);
    private static native void nativeScheduleFrame(int deviceIndex, int[] pixels, int width, int height);
    private static native void nativeStopPlayback(int deviceIndex);
    private static native void nativeClose(int deviceIndex);
    private static native int[] nativeGetOutputInfo(int deviceIndex);

    // Input capture
    private static native String[] nativeListInputModes(int deviceIndex);
    private static native String[] nativeListVideoConnections(int deviceIndex);
    private static native boolean nativeOpenInput(int deviceIndex, String mode, int connection);
    private static native int[] nativeGetInputFrame(int deviceIndex);
    private static native void nativeCloseInput(int deviceIndex);
}
