package com.stremio.mobile.server;

import android.content.Context;

/** JNI surface exported by the MIT-licensed stremio-native/stream-server. */
public final class JniStreamingServerController {
    static {
        System.loadLibrary("stream_server");
    }

    private JniStreamingServerController() {}

    // The official Android binary performs class-loader setup through Context
    // before starting the embedded server. This signature is covered by a
    // reflection test because a mismatch fails only at runtime inside JNI.
    public static native String startServerNative(Context context, String configDir, String cacheDir, int port);
    public static native void stopServerNative();
    public static native String getServerUrlNative();
}
