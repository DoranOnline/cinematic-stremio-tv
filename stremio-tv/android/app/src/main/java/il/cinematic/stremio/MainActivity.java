package il.cinematic.stremio;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.SystemClock;
import android.content.Intent;
import android.net.Uri;
import android.view.KeyEvent;
import android.graphics.Color;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import android.util.Log;

import androidx.activity.OnBackPressedCallback;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final String REMOTE_NAVIGATION_SCRIPT =
        "(function(direction){" +
        "if(typeof window.navigate==='function'){window.navigate(direction);return true;}" +
        "return false;" +
        "})('%s');";

    private final RemoteKeyGate remoteKeyGate = new RemoteKeyGate(110L);
    private AppUpdateManager appUpdateManager;
    private boolean exitDialogVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebView webView = getBridge().getWebView();
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        webView.setBackgroundColor(Color.rgb(7, 8, 10));
        webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.getSettings().setUserAgentString(
            webView.getSettings().getUserAgentString() + " CinematicTV/2.0.0"
        );
        webView.addJavascriptInterface(new NativeStatusBridge(), "CinematicAndroid");
        webView.requestFocus();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackNavigation();
            }
        });
        appUpdateManager = new AppUpdateManager(this);
        appUpdateManager.start();
    }

    @Override
    public void onResume() {
        super.onResume();
        final WebView webView = getBridge() == null ? null : getBridge().getWebView();
        if (webView != null) {
            webView.post(() -> {
                webView.requestFocus();
                webView.evaluateJavascript(
                    "(function(){var e=document.querySelector('[data-cinematic-return-focus]');" +
                    "if(e){e.focus({preventScroll:true});e.scrollIntoView({block:'center',inline:'center'});return true;}" +
                    "return false;})()",
                    null
                );
            });
        }
        if (appUpdateManager != null) appUpdateManager.resume();
    }

    @Override
    public void onDestroy() {
        if (appUpdateManager != null) appUpdateManager.stop();
        super.onDestroy();
    }

    private void handleBackNavigation() {
        final WebView webView = getBridge().getWebView();
        final String url = webView.getUrl();
        Log.i("CinematicBack", "Back requested at " + url);
        if (url != null && url.contains("#/") && !url.endsWith("#/")) {
            // A restored WebView route may have no usable browser history.
            // Returning to the board is deterministic and never exits the TV app.
            webView.evaluateJavascript("window.location.hash='#/';", null);
            return;
        }
        showExitConfirmation();
    }

    private void showExitConfirmation() {
        if (exitDialogVisible || isFinishing()) return;
        exitDialogVisible = true;
        new AlertDialog.Builder(this)
            .setTitle("לצאת מ־Cinematic?")
            .setMessage("לחיצה בטעות לא תסגור יותר את האפליקציה.")
            .setNegativeButton("להישאר", null)
            .setPositiveButton("יציאה", (dialog, which) -> finish())
            .setOnDismissListener(dialog -> exitDialogVisible = false)
            .show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                handleBackNavigation();
            }
            return true;
        }

        final String direction = directionForKeyCode(event.getKeyCode());
        if (direction == null) {
            return super.dispatchKeyEvent(event);
        }

        // Consume both DOWN and UP so Android WebView cannot also dispatch an
        // unbounded stream of keyboard events to the JS spatial-nav polyfill.
        if (event.getAction() == KeyEvent.ACTION_DOWN &&
            remoteKeyGate.shouldDispatch(direction, event.getRepeatCount(), SystemClock.uptimeMillis())) {
            final WebView webView = getBridge().getWebView();
            webView.evaluateJavascript(String.format(REMOTE_NAVIGATION_SCRIPT, direction), null);
        }
        return true;
    }

    static String directionForKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return "left";
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return "right";
            case KeyEvent.KEYCODE_DPAD_UP:
                return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return "down";
            default:
                return null;
        }
    }

    private final class NativeStatusBridge {
        @JavascriptInterface
        public boolean openNativePlayer(String streamUrl, String videoId, String title) {
            if (streamUrl == null || streamUrl.isEmpty()) return false;
            final Uri uri = Uri.parse(streamUrl);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            runOnUiThread(() -> {
                final Intent intent = new Intent(MainActivity.this, NativePlayerActivity.class);
                intent.putExtra(NativePlayerActivity.EXTRA_STREAM_URL, streamUrl);
                intent.putExtra(NativePlayerActivity.EXTRA_VIDEO_ID, videoId);
                intent.putExtra(NativePlayerActivity.EXTRA_TITLE, title);
                startActivity(intent);
            });
            return true;
        }

        @JavascriptInterface
        public boolean isStreamingServerReady() {
            return EmbeddedStreamingServer.isReady();
        }

        @JavascriptInterface
        public String getStreamingServerUrl() {
            final String url = EmbeddedStreamingServer.getBaseUrl();
            return url == null ? "" : url;
        }
    }
}
