package com.stremio.mobile.server;

import android.content.Context;

/** JNI surface exported by the MIT-licensed stremio-native/stream-server. */
public final class JniStreamingServerController {
    static {
        System.loadLibrary("stream_server");
    }

    private JniStreamingServerController() {}

    public static native String startServerNative(Context context, String configDir, String cacheDir, int port);
    public static native void stopServerNative();
    public static native String getServerUrlNative();
}
