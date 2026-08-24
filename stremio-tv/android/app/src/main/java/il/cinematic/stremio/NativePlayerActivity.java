package il.cinematic.stremio;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;

public final class NativePlayerActivity extends Activity {
    static final String EXTRA_STREAM_URL = "stream_url";
    private static final long MEDIA3_FALLBACK_TIMEOUT_MS = 25_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExoPlayer exoPlayer;
    private PlayerView playerView;
    private LibVLC libVlc;
    private MediaPlayer vlcPlayer;
    private VLCVideoLayout vlcView;
    private LinearLayout statusPanel;
    private TextView statusText;
    private String streamUrl;
    private boolean firstFrameRendered;
    private boolean usingVlc;
    private boolean vlcStarted;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        streamUrl = getIntent().getStringExtra(EXTRA_STREAM_URL);
        if (!isSupportedUrl(streamUrl)) {
            finish();
            return;
        }
        buildLayout();
    }

    private void buildLayout() {
        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(7, 8, 10));

        vlcView = new VLCVideoLayout(this);
        vlcView.setVisibility(View.GONE);
        root.addView(vlcView, matchParent());

        playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.TRANSPARENT);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setControllerAutoShow(true);
        playerView.setControllerHideOnTouch(true);
        playerView.setControllerShowTimeoutMs(5_000);
        root.addView(playerView, matchParent());

        statusPanel = new LinearLayout(this);
        statusPanel.setOrientation(LinearLayout.VERTICAL);
        statusPanel.setGravity(Gravity.CENTER);
        statusPanel.setPadding(48, 36, 48, 36);
        final FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        );
        statusPanel.addView(new ProgressBar(this));
        statusText = new TextView(this);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(20f);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 22, 0, 18);
        statusText.setText("מכין את הווידאו…");
        statusPanel.addView(statusText);

        final Button otherPlayer = new Button(this);
        otherPlayer.setText("פתח בנגן אחר");
        otherPlayer.setOnClickListener(view -> openExternalPlayer());
        statusPanel.addView(otherPlayer);
        root.addView(statusPanel, panelParams);
        setContentView(root);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isLocalStreamServerUrl(streamUrl)) {
            startVlcFallback("פותח במצב תאימות לטלוויזיה…");
        } else {
            startMedia3();
        }
    }

    private void startMedia3() {
        releasePlayers();
        usingVlc = false;
        firstFrameRendered = false;
        playerView.setVisibility(View.VISIBLE);
        vlcView.setVisibility(View.GONE);
        showStatus("מתחבר למקור…");

        final DefaultRenderersFactory renderers = new DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true);
        exoPlayer = new ExoPlayer.Builder(this, renderers)
            .setHandleAudioBecomingNoisy(true)
            .build();
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                startVlcFallback("הפורמט דורש נגן תאימות…");
            }

            @Override
            public void onRenderedFirstFrame() {
                firstFrameRendered = true;
                hideStatus();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) showStatus("טוען וידאו…");
                if (state == Player.STATE_READY && exoPlayer != null && exoPlayer.isPlaying()) hideStatus();
            }
        });
        playerView.setPlayer(exoPlayer);
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)));
        exoPlayer.prepare();
        exoPlayer.play();
        handler.postDelayed(() -> {
            if (!firstFrameRendered && !usingVlc && !isFinishing()) {
                startVlcFallback("מנסה מצב תאימות…");
            }
        }, MEDIA3_FALLBACK_TIMEOUT_MS);
    }

    private void startVlcFallback(String message) {
        if (usingVlc || isFinishing()) return;
        usingVlc = true;
        vlcStarted = false;
        showStatus(message);
        if (exoPlayer != null) {
            playerView.setPlayer(null);
            exoPlayer.release();
            exoPlayer = null;
        }
        playerView.setVisibility(View.GONE);
        vlcView.setVisibility(View.VISIBLE);

        final ArrayList<String> options = new ArrayList<>();
        options.add("--network-caching=3000");
        options.add("--clock-jitter=0");
        options.add("--clock-synchro=0");
        libVlc = new LibVLC(this, options);
        vlcPlayer = new MediaPlayer(libVlc);
        vlcPlayer.attachViews(vlcView, null, false, false);
        vlcPlayer.setEventListener(event -> {
            if (event.type == MediaPlayer.Event.Playing || event.type == MediaPlayer.Event.Vout) {
                vlcStarted = true;
                hideStatus();
            } else if (event.type == MediaPlayer.Event.Buffering && !vlcStarted) {
                showStatus("טוען במצב תאימות…");
            } else if (event.type == MediaPlayer.Event.EncounteredError) {
                showStatus("המקור לא הצליח להתנגן. נסה מקור אחר או נגן אחר.");
            }
        });
        final Media media = new Media(libVlc, Uri.parse(streamUrl));
        media.setHWDecoderEnabled(true, true);
        vlcPlayer.setMedia(media);
        media.release();
        vlcPlayer.play();
    }

    private void openExternalPlayer() {
        final Intent intent = new Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(streamUrl), "video/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(Intent.createChooser(intent, "בחר נגן"));
        } else {
            showStatus("לא נמצא נגן נוסף במכשיר");
        }
    }

    private void showStatus(String text) {
        runOnUiThread(() -> {
            statusText.setText(text);
            statusPanel.setVisibility(View.VISIBLE);
        });
    }

    private void hideStatus() {
        runOnUiThread(() -> statusPanel.setVisibility(View.GONE));
    }

    @Override
    protected void onStop() {
        handler.removeCallbacksAndMessages(null);
        releasePlayers();
        super.onStop();
    }

    private void releasePlayers() {
        if (exoPlayer != null) {
            playerView.setPlayer(null);
            exoPlayer.release();
            exoPlayer = null;
        }
        if (vlcPlayer != null) {
            vlcPlayer.stop();
            vlcPlayer.detachViews();
            vlcPlayer.release();
            vlcPlayer = null;
        }
        if (libVlc != null) {
            libVlc.release();
            libVlc = null;
        }
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private static boolean isSupportedUrl(String value) {
        if (value == null || value.isEmpty()) return false;
        final Uri uri = Uri.parse(value);
        return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
    }

    private static boolean isLocalStreamServerUrl(String value) {
        final String host = Uri.parse(value).getHost();
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host);
    }
}
