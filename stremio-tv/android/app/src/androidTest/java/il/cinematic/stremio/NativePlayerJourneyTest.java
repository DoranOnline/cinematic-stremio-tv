package il.cinematic.stremio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Human-observable QA entry point for the real native player.
 *
 * The host serves a deterministic legal MP4 and maps port 8766 with adb
 * reverse. While this test is alive, the external QA driver sends the same
 * remote keys a viewer uses and records the emulator screen plus logcat.
 */
@RunWith(AndroidJUnit4.class)
public final class NativePlayerJourneyTest {
    @Test
    public void firstLaunchWaitsForLanguageBeforePlaybackStarts() throws Exception {
        ApplicationProvider.getApplicationContext()
            .getSharedPreferences("cinematic_player_settings", 0)
            .edit()
            .remove("language")
            .commit();

        try (ActivityScenario<NativePlayerActivity> scenario = launchPlayer()) {
            Thread.sleep(4_000L);
            scenario.onActivity(activity -> assertEquals(
                "Playback engine must not start until the first-run language choice is complete",
                null,
                field(activity, "exoPlayer", ExoPlayer.class)
            ));
        }
    }

    @Test
    public void hidesBufferingStatusAfterSeekRecoversToReady() throws Exception {
        ApplicationProvider.getApplicationContext()
            .getSharedPreferences("cinematic_player_settings", 0)
            .edit()
            .putString("language", "en")
            .commit();
        clearPlaybackProgress();

        try (ActivityScenario<NativePlayerActivity> scenario = launchPlayer()) {
            waitUntilPlaying(scenario, 20_000L);
            scenario.onActivity(activity -> activity.dispatchKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            ));
            waitUntilPlaying(scenario, 20_000L);
            Thread.sleep(1_000L);
            scenario.onActivity(activity -> assertEquals(
                "The loading overlay must disappear once playback is ready again",
                View.GONE,
                field(activity, "statusPanel", LinearLayout.class).getVisibility()
            ));
        }
    }

    @Test
    public void keepsRealPlayerOpenForRecordedRemoteJourney() throws Exception {
        ApplicationProvider.getApplicationContext()
            .getSharedPreferences("cinematic_player_settings", 0)
            .edit()
            .putString("language", "en")
            .commit();
        clearPlaybackProgress();

        try (ActivityScenario<NativePlayerActivity> scenario = launchPlayer()) {
            Thread.sleep(60_000L);
            assertFalse(scenario.getState() == androidx.lifecycle.Lifecycle.State.DESTROYED);
        }
    }

    private ActivityScenario<NativePlayerActivity> launchPlayer() {
        final Intent intent = new Intent(
            ApplicationProvider.getApplicationContext(),
            NativePlayerActivity.class
        );
        intent.putExtra(
            NativePlayerActivity.EXTRA_STREAM_URL,
            "http://127.0.0.1:8766/nuvyro-legal-qa-90s.mp4"
        );
        intent.putExtra(NativePlayerActivity.EXTRA_VIDEO_ID, "qa_s1_e1");
        intent.putExtra(NativePlayerActivity.EXTRA_META_ID, "qa-series");
        intent.putExtra(NativePlayerActivity.EXTRA_TITLE, "NUVYRO QA Episode 1");
        return ActivityScenario.launch(intent);
    }

    private void clearPlaybackProgress() {
        ApplicationProvider.getApplicationContext()
            .getSharedPreferences("cinematic_playback_progress", 0)
            .edit()
            .clear()
            .commit();
    }

    private void waitUntilPlaying(
        ActivityScenario<NativePlayerActivity> scenario,
        long timeoutMs
    ) throws Exception {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        final boolean[] playing = {false};
        while (System.currentTimeMillis() < deadline) {
            scenario.onActivity(activity -> {
                final ExoPlayer player = field(activity, "exoPlayer", ExoPlayer.class);
                playing[0] = player != null &&
                    player.getPlaybackState() == Player.STATE_READY &&
                    player.isPlaying();
            });
            if (playing[0]) return;
            Thread.sleep(250L);
        }
        assertTrue("Player did not reach READY + playing", playing[0]);
    }

    private <T> T field(Object target, String name, Class<T> type) {
        try {
            final java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (Exception error) {
            throw new AssertionError("Unable to inspect " + name, error);
        }
    }
}
