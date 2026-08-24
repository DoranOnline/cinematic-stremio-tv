package il.cinematic.stremio;

import android.os.Bundle;
import android.os.SystemClock;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.view.KeyEvent;
import android.graphics.Color;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;

import java.util.List;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final String REMOTE_NAVIGATION_SCRIPT =
        "(function(direction){" +
        "if(typeof window.navigate==='function'){window.navigate(direction);return true;}" +
        "return false;" +
        "})('%s');";

    private final RemoteKeyGate remoteKeyGate = new RemoteKeyGate(110L);
    private AppUpdateManager appUpdateManager;

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
            webView.getSettings().getUserAgentString() + " CinematicTV/0.7"
        );
        webView.addJavascriptInterface(new PlaybackBridge(), "CinematicAndroid");
        webView.requestFocus();
        appUpdateManager = new AppUpdateManager(this);
        appUpdateManager.start();
    }

    @Override
    public void onResume() {
        super.onResume();
        final WebView webView = getBridge() == null ? null : getBridge().getWebView();
        if (webView != null) {
            webView.post(webView::requestFocus);
        }
        if (appUpdateManager != null) appUpdateManager.resume();
    }

    @Override
    public void onDestroy() {
        if (appUpdateManager != null) appUpdateManager.stop();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        final WebView webView = getBridge().getWebView();
        final String url = webView.getUrl();
        if (url != null && url.contains("#/") && !url.endsWith("#/")) {
            webView.evaluateJavascript("window.history.back();", null);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
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

    private final class PlaybackBridge {
        @JavascriptInterface
        public boolean openInStremio(String deepLink) {
            if (deepLink == null) {
                return false;
            }

            if (!PlaybackLinkPolicy.isSupported(deepLink)) {
                return false;
            }

            final Uri uri = Uri.parse(deepLink);
            final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            // Android TV boxes often register browsers and vendor launchers for
            // custom schemes. Route only to the real Stremio package; a generic
            // resolver was the source of the black-screen handoff in v0.5.
            final List<ResolveInfo> handlers = getPackageManager().queryIntentActivities(intent, 0);
            ResolveInfo stremioHandler = null;
            for (ResolveInfo handler : handlers) {
                final String packageName = handler.activityInfo.packageName;
                if (packageName != null && packageName.toLowerCase().contains("stremio")) {
                    stremioHandler = handler;
                    break;
                }
            }
            if (stremioHandler == null) {
                return false;
            }
            intent.setPackage(stremioHandler.activityInfo.packageName);
            runOnUiThread(() -> {
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException | SecurityException ignored) {
                    // Keep this shell alive if the target disappears or refuses
                    // the deep link after package discovery.
                }
            });
            return true;
        }
    }
}
