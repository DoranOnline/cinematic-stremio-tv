package il.cinematic.stremio;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.SystemClock;
import android.content.Intent;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.graphics.Color;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import android.util.Log;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.ArrayList;

import androidx.activity.OnBackPressedCallback;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final int PLAYER_REQUEST_CODE = 2202;
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
            webView.getSettings().getUserAgentString() + " NuvyroTV/2.4.0"
        );
        webView.addJavascriptInterface(
            new NativeStatusBridge(), WebNativeBridgeContract.LEGACY_OBJECT_NAME);
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
                    "(function(){var e=document.querySelector('[" +
                    WebNativeBridgeContract.RETURN_FOCUS_ATTRIBUTE + "]');" +
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PLAYER_REQUEST_CODE || resultCode != RESULT_OK || data == null) return;
        final WebView webView = getBridge() == null ? null : getBridge().getWebView();
        if (webView != null) {
            final boolean ended = data.getBooleanExtra(NativePlayerActivity.EXTRA_PLAYBACK_ENDED, false);
            final JSONObject detail = new JSONObject();
            try {
                detail.put("videoId", data.getStringExtra(NativePlayerActivity.EXTRA_VIDEO_ID));
                detail.put("metaId", data.getStringExtra(NativePlayerActivity.EXTRA_META_ID));
                detail.put("position", data.getLongExtra(NativePlayerActivity.EXTRA_PLAYBACK_POSITION, 0L));
                detail.put("duration", data.getLongExtra(NativePlayerActivity.EXTRA_PLAYBACK_DURATION, 0L));
                detail.put("ended", ended);
            } catch (Exception ignored) {
                return;
            }
            webView.post(() -> {
                final String payload = detail.toString();
                webView.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('" +
                        WebNativeBridgeContract.EVENT_PLAYBACK_PROGRESS +
                        "',{detail:" + payload + "}));", null);
                if (ended) {
                    webView.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('" +
                            WebNativeBridgeContract.EVENT_PLAYBACK_ENDED +
                            "',{detail:" + payload + "}));", null);
                }
            });
        }
    }

    private void handleBackNavigation() {
        final WebView webView = getBridge().getWebView();
        final String url = webView.getUrl();
        Log.i("CinematicBack", "Back requested at " + url);
        if (url != null && url.contains("#/") && !url.endsWith("#/")) {
            final String script =
                "(function(){var e=new CustomEvent('" +
                WebNativeBridgeContract.EVENT_NATIVE_BACK_REQUEST +
                "',{cancelable:true});window.dispatchEvent(e);return e.defaultPrevented;})()";
            webView.evaluateJavascript(script, handled -> {
                if (!"true".equals(handled)) showExitConfirmation();
            });
            return;
        }
        showExitConfirmation();
    }

    private void showExitConfirmation() {
        if (exitDialogVisible || isFinishing()) return;
        exitDialogVisible = true;
        new AlertDialog.Builder(this)
            .setTitle("לצאת מ־NUVYRO?")
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
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP:
                return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN:
                return "down";
            default:
                return null;
        }
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL) {
            final float vertical = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (Math.abs(vertical) > 0.1f) {
                final String direction = vertical > 0f ? "up" : "down";
                getBridge().getWebView().evaluateJavascript(
                    String.format(REMOTE_NAVIGATION_SCRIPT, direction), null);
                return true;
            }
        }
        return super.dispatchGenericMotionEvent(event);
    }

    private final class NativeStatusBridge {
        @JavascriptInterface
        public boolean openNativePlayer(String streamUrl, String videoId, String title) {
            return openNativePlayerSession(streamUrl, videoId, "", title);
        }

        @JavascriptInterface
        public boolean openNativePlayerSession(String streamUrl, String videoId, String metaId, String title) {
            return openNativePlayerSessionV2(streamUrl, videoId, metaId, title, "[]");
        }

        @JavascriptInterface
        public boolean openNativePlayerSessionV2(String streamUrl, String videoId, String metaId, String title, String sourcesJson) {
            if (streamUrl == null || streamUrl.isEmpty()) return false;
            final Uri uri = Uri.parse(streamUrl);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            final ArrayList<String> sourceUrls = new ArrayList<>();
            final ArrayList<String> sourceLabels = new ArrayList<>();
            sourceUrls.add(streamUrl);
            sourceLabels.add(title == null || title.isEmpty() ? "Best match" : title);
            try {
                final JSONArray sources = new JSONArray(sourcesJson == null ? "[]" : sourcesJson);
                for (int index = 0; index < sources.length() && sourceUrls.size() < 12; index++) {
                    final JSONObject source = sources.optJSONObject(index);
                    if (source == null) continue;
                    final String candidate = source.optString("url", "");
                    final Uri candidateUri = Uri.parse(candidate);
                    final String scheme = candidateUri.getScheme();
                    if (candidate.isEmpty() || sourceUrls.contains(candidate) ||
                        (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) continue;
                    sourceUrls.add(candidate);
                    sourceLabels.add(source.optString("label", "Source " + (sourceUrls.size())));
                }
            } catch (Exception error) {
                Log.w("CinematicPlayer", "Ignoring invalid source queue", error);
            }
            final PlaybackSession session = PlaybackSession.fromLegacy(streamUrl, videoId, title);
            runOnUiThread(() -> {
                final Intent intent = new Intent(MainActivity.this, NativePlayerActivity.class);
                intent.putExtra(NativePlayerActivity.EXTRA_STREAM_URL, streamUrl);
                intent.putExtra(NativePlayerActivity.EXTRA_VIDEO_ID, session.getVideoId());
                intent.putExtra(NativePlayerActivity.EXTRA_META_ID, metaId == null ? "" : metaId);
                intent.putExtra(NativePlayerActivity.EXTRA_TITLE, session.getTitle());
                intent.putStringArrayListExtra(NativePlayerActivity.EXTRA_SOURCE_URLS, sourceUrls);
                intent.putStringArrayListExtra(NativePlayerActivity.EXTRA_SOURCE_LABELS, sourceLabels);
                startActivityForResult(intent, PLAYER_REQUEST_CODE);
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
