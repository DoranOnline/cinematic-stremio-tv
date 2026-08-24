package il.cinematic.stremio;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public final class NativePlayerActivity extends Activity {
    static final String EXTRA_STREAM_URL = "stream_url";

    private ExoPlayer player;
    private PlayerView playerView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setControllerAutoShow(true);
        playerView.setControllerHideOnTouch(true);
        playerView.setControllerShowTimeoutMs(5_000);
        setContentView(playerView, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    @Override
    protected void onStart() {
        super.onStart();
        final String streamUrl = getIntent().getStringExtra(EXTRA_STREAM_URL);
        if (!isSupportedUrl(streamUrl)) {
            Toast.makeText(this, "לא נמצא קישור וידאו תקין", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        player = new ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                Toast.makeText(NativePlayerActivity.this, "הניגון נכשל — נסה מקור אחר", Toast.LENGTH_LONG).show();
            }
        });
        playerView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)));
        player.prepare();
        player.play();
    }

    @Override
    protected void onStop() {
        if (playerView != null) playerView.setPlayer(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onStop();
    }

    private static boolean isSupportedUrl(String value) {
        if (value == null || value.isEmpty()) return false;
        final Uri uri = Uri.parse(value);
        return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
    }
}
