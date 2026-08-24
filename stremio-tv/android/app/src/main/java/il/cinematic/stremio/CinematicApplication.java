package il.cinematic.stremio;

import android.app.Application;

public final class CinematicApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        EmbeddedStreamingServer.start(this);
    }
}
