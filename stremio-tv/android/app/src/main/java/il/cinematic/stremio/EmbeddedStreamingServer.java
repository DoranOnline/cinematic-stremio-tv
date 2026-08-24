package il.cinematic.stremio;

import android.content.Context;
import android.util.Log;

import com.stremio.mobile.server.JniStreamingServerController;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class EmbeddedStreamingServer {
    private static final String TAG = "CinematicStreamServer";
    private static final int PORT = 11470;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private static volatile boolean ready;
    private static volatile String baseUrl;

    private EmbeddedStreamingServer() {}

    static void start(Context context) {
        final Context applicationContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                final File configDir = new File(applicationContext.getFilesDir(), "stream-server");
                final File cacheDir = new File(applicationContext.getCacheDir(), "stream-server");
                if (!configDir.exists() && !configDir.mkdirs()) {
                    throw new IllegalStateException("Cannot create stream-server config directory");
                }
                if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                    throw new IllegalStateException("Cannot create stream-server cache directory");
                }

                baseUrl = JniStreamingServerController.startServerNative(
                    applicationContext,
                    configDir.getAbsolutePath(),
                    cacheDir.getAbsolutePath(),
                    PORT
                );
                ready = waitUntilReachable(baseUrl, 8_000L);
                Log.i(TAG, ready ? "READY " + baseUrl : "FAILED health check " + baseUrl);
            } catch (Throwable error) {
                ready = false;
                Log.e(TAG, "Unable to start embedded streaming server", error);
            }
        });
    }

    static boolean isReady() {
        return ready;
    }

    static String getBaseUrl() {
        return baseUrl;
    }

    private static boolean waitUntilReachable(String url, long timeoutMs) {
        if (url == null || url.isEmpty()) return false;
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url + "/settings").openConnection();
                connection.setConnectTimeout(500);
                connection.setReadTimeout(500);
                connection.setRequestProperty("Accept", "application/json");
                final int status = connection.getResponseCode();
                if (status >= 200 && status < 500) return true;
            } catch (Exception ignored) {
                try {
                    Thread.sleep(150L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        return false;
    }
}
