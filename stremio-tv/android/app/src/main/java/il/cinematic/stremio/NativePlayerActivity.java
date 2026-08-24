package il.cinematic.stremio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.C;
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
import java.util.Locale;

public final class NativePlayerActivity extends Activity {
    static final String EXTRA_STREAM_URL = "stream_url";
    static final String EXTRA_VIDEO_ID = "video_id";
    static final String EXTRA_TITLE = "title";

    private static final long FALLBACK_TIMEOUT_MS = 25_000L;
    private static final long SAVE_INTERVAL_MS = 10_000L;
    private static final long CONTROLS_TIMEOUT_MS = 6_000L;
    private static final long SEEK_STEP_MS = 10_000L;
    private static final String PREFS_NAME = "cinematic_playback_progress";
    private static final String SETTINGS_NAME = "cinematic_player_settings";
    private static final String LANGUAGE_KEY = "language";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExoPlayer exoPlayer;
    private PlayerView playerView;
    private LibVLC libVlc;
    private MediaPlayer vlcPlayer;
    private VLCVideoLayout vlcView;
    private LinearLayout statusPanel;
    private LinearLayout controlsPanel;
    private TextView statusText;
    private TextView titleText;
    private TextView timeText;
    private SeekBar seekBar;
    private Button playPauseButton;
    private Button rewindButton;
    private Button forwardButton;
    private Button subtitleButton;
    private Button audioButton;
    private Button speedButton;
    private Button sourceButton;
    private Button languageButton;
    private View lastFocusedControl;
    private String streamUrl;
    private String videoId;
    private String title;
    private long pendingResumeMs;
    private boolean firstFrameRendered;
    private boolean usingVlc;
    private boolean vlcStarted;
    private boolean userSeeking;
    private boolean initialTrackPreferencesApplied;
    private long lastSavedAt;
    private String selectedLanguage;

    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            updateProgressUi();
            final long now = System.currentTimeMillis();
            if (now - lastSavedAt >= SAVE_INTERVAL_MS) {
                saveProgress();
                lastSavedAt = now;
            }
            handler.postDelayed(this, 500L);
        }
    };

    private final Runnable hideControlsRunnable = () -> {
        if (isPlaying()) controlsPanel.setVisibility(View.GONE);
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        streamUrl = getIntent().getStringExtra(EXTRA_STREAM_URL);
        videoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        title = getIntent().getStringExtra(EXTRA_TITLE);
        if (!isSupportedUrl(streamUrl)) {
            finish();
            return;
        }
        if (videoId == null || videoId.isEmpty()) videoId = streamUrl;
        if (title == null || title.isEmpty()) title = "Cinematic";
        selectedLanguage = settings().getString(LANGUAGE_KEY, "");
        buildLayout();
        if (selectedLanguage.isEmpty()) handler.post(this::showLanguageMenu);
    }

    private void buildLayout() {
        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(7, 8, 10));

        vlcView = new VLCVideoLayout(this);
        vlcView.setVisibility(View.GONE);
        root.addView(vlcView, matchParent());

        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setBackgroundColor(Color.TRANSPARENT);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);
        playerView.setClickable(true);
        playerView.setOnClickListener(view -> showControls(false));
        root.addView(playerView, matchParent());

        vlcView.setClickable(true);
        vlcView.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) showControls(false);
            return true;
        });

        buildStatusPanel(root);
        buildControlsPanel(root);
        setContentView(root);
    }

    private void buildStatusPanel(FrameLayout root) {
        statusPanel = new LinearLayout(this);
        statusPanel.setOrientation(LinearLayout.VERTICAL);
        statusPanel.setGravity(Gravity.CENTER);
        statusPanel.setPadding(52, 38, 52, 38);
        statusPanel.addView(new ProgressBar(this));
        statusText = new TextView(this);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(20f);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 22, 0, 18);
        statusText.setText("מכין את הווידאו…");
        statusPanel.addView(statusText);
        final Button otherPlayer = button("פתח בנגן אחר", view -> openExternalPlayer());
        statusPanel.addView(otherPlayer);
        final FrameLayout.LayoutParams params = wrapContent(Gravity.CENTER);
        root.addView(statusPanel, params);
    }

    private void buildControlsPanel(FrameLayout root) {
        controlsPanel = new LinearLayout(this);
        controlsPanel.setOrientation(LinearLayout.VERTICAL);
        controlsPanel.setPadding(48, 30, 48, 34);
        final GradientDrawable background = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0x22000000, 0xE6000000}
        );
        controlsPanel.setBackground(background);

        titleText = new TextView(this);
        titleText.setText(title);
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(22f);
        titleText.setSingleLine(true);
        controlsPanel.addView(titleText, matchWidthWrapHeight());

        final LinearLayout timeline = new LinearLayout(this);
        timeline.setOrientation(LinearLayout.HORIZONTAL);
        timeline.setGravity(Gravity.CENTER_VERTICAL);
        seekBar = new SeekBar(this);
        seekBar.setMax(1);
        seekBar.setKeyProgressIncrement((int) SEEK_STEP_MS);
        timeline.addView(seekBar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        timeText = new TextView(this);
        timeText.setTextColor(Color.WHITE);
        timeText.setTextSize(16f);
        timeText.setPadding(18, 0, 0, 0);
        timeText.setText("00:00 / 00:00");
        timeline.addView(timeText);
        controlsPanel.addView(timeline, matchWidthWrapHeight());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                userSeeking = false;
                seekTo(bar.getProgress());
                scheduleControlsHide();
            }
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    timeText.setText(formatTime(progress) + " / " + formatTime(getDuration()));
                    // A TV remote changes SeekBar progress without invoking touch start/stop.
                    // Seek immediately for D-pad input; touch dragging still commits on release.
                    if (!userSeeking) seekTo(progress);
                }
            }
        });

        final HorizontalScrollView actionScroller = new HorizontalScrollView(this);
        actionScroller.setFillViewport(true);
        actionScroller.setHorizontalScrollBarEnabled(false);
        final LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        rewindButton = button("↶ 10", view -> seekBy(-SEEK_STEP_MS));
        actions.addView(rewindButton);
        playPauseButton = button("Pause", view -> togglePlayPause());
        actions.addView(playPauseButton);
        forwardButton = button("10 ↷", view -> seekBy(SEEK_STEP_MS));
        actions.addView(forwardButton);
        subtitleButton = button("כתוביות", view -> showSubtitleMenu());
        actions.addView(subtitleButton);
        audioButton = button("שמע", view -> showAudioMenu());
        actions.addView(audioButton);
        speedButton = button("מהירות", view -> showSpeedMenu());
        actions.addView(speedButton);
        languageButton = button("שפה", view -> showLanguageMenu());
        actions.addView(languageButton);
        sourceButton = button("מקור אחר", view -> finish());
        actions.addView(sourceButton);
        actionScroller.addView(actions, matchWidthWrapHeight());
        controlsPanel.addView(actionScroller, matchWidthWrapHeight());
        applyControlLabels();

        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        );
        root.addView(controlsPanel, params);
    }

    @Override
    protected void onStart() {
        super.onStart();
        final long saved = preferences().getLong(progressKey(), 0L);
        if (saved >= 30_000L) {
            new AlertDialog.Builder(this)
                .setTitle("להמשיך לצפות?")
                .setMessage("עצרנו ב־" + formatTime(saved))
                .setPositiveButton("המשך", (dialog, which) -> {
                    pendingResumeMs = saved;
                    startSelectedEngine();
                })
                .setNegativeButton("מההתחלה", (dialog, which) -> {
                    preferences().edit().remove(progressKey()).apply();
                    startSelectedEngine();
                })
                .setOnCancelListener(dialog -> startSelectedEngine())
                .show();
        } else {
            startSelectedEngine();
        }
        handler.post(progressTicker);
    }

    private void startSelectedEngine() {
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
        exoPlayer.setTrackSelectionParameters(
            exoPlayer.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        );
        exoPlayer.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) {
                startVlcFallback("הפורמט דורש נגן תאימות…");
            }
            @Override public void onRenderedFirstFrame() {
                firstFrameRendered = true;
                hideStatus();
                applyPendingResume();
                scheduleControlsHide();
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) showStatus("טוען וידאו…");
                if (state == Player.STATE_READY) applyPendingResume();
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
        }, FALLBACK_TIMEOUT_MS);
    }

    private void startVlcFallback(String message) {
        if (usingVlc || isFinishing()) return;
        usingVlc = true;
        vlcStarted = false;
        initialTrackPreferencesApplied = false;
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
                applyPendingResume();
                if (!initialTrackPreferencesApplied) {
                    initialTrackPreferencesApplied = true;
                    applyPreferredTracks();
                }
                scheduleControlsHide();
            } else if (event.type == MediaPlayer.Event.Buffering && !vlcStarted) {
                showStatus("טוען במצב תאימות…");
            } else if (event.type == MediaPlayer.Event.EncounteredError) {
                showStatus("המקור לא הצליח להתנגן. נסה מקור אחר או נגן אחר.");
            } else if (event.type == MediaPlayer.Event.EndReached) {
                clearProgress();
            }
        });
        final Media media = new Media(libVlc, Uri.parse(streamUrl));
        media.setHWDecoderEnabled(true, true);
        vlcPlayer.setMedia(media);
        media.release();
        vlcPlayer.play();
    }

    private void applyPendingResume() {
        if (pendingResumeMs <= 0) return;
        final long resume = pendingResumeMs;
        pendingResumeMs = 0;
        handler.postDelayed(() -> seekTo(resume), 250L);
    }

    private void togglePlayPause() {
        if (exoPlayer != null) {
            if (exoPlayer.isPlaying()) exoPlayer.pause(); else exoPlayer.play();
        } else if (vlcPlayer != null) {
            if (vlcPlayer.isPlaying()) vlcPlayer.pause(); else vlcPlayer.play();
        }
        updatePlayPauseLabel();
        showControls(false);
    }

    private void seekBy(long deltaMs) {
        final long duration = getDuration();
        final long target = duration > 0L
            ? Math.max(0L, Math.min(duration, getPosition() + deltaMs))
            : Math.max(0L, getPosition() + deltaMs);
        seekTo(target);
        showControls(false);
    }

    private void seekTo(long positionMs) {
        if (exoPlayer != null) exoPlayer.seekTo(positionMs);
        if (vlcPlayer != null) vlcPlayer.setTime(positionMs, true);
        updateProgressUi();
    }

    private void showSubtitleMenu() {
        if (vlcPlayer == null) {
            showStatusTemporarily("בחירת כתוביות זמינה במקורות Stremio");
            return;
        }
        final MediaPlayer.TrackDescription[] tracks = vlcPlayer.getSpuTracks();
        if (tracks == null || tracks.length == 0) {
            showStatusTemporarily("אין כתוביות במקור הזה");
            return;
        }
        final String[] labels = new String[tracks.length + 1];
        labels[0] = "ללא כתוביות";
        for (int i = 0; i < tracks.length; i++) labels[i + 1] = tracks[i].name;
        new AlertDialog.Builder(this)
            .setTitle("שפת כתוביות")
            .setItems(labels, (dialog, which) -> vlcPlayer.setSpuTrack(which == 0 ? -1 : tracks[which - 1].id))
            .show();
    }

    private void showAudioMenu() {
        if (vlcPlayer == null) {
            showStatusTemporarily("בחירת שמע זמינה במקורות Stremio");
            return;
        }
        final MediaPlayer.TrackDescription[] tracks = vlcPlayer.getAudioTracks();
        if (tracks == null || tracks.length == 0) {
            showStatusTemporarily("אין רצועות שמע נוספות");
            return;
        }
        final String[] labels = new String[tracks.length];
        for (int i = 0; i < tracks.length; i++) labels[i] = tracks[i].name;
        new AlertDialog.Builder(this)
            .setTitle("שפת שמע")
            .setItems(labels, (dialog, which) -> vlcPlayer.setAudioTrack(tracks[which].id))
            .show();
    }

    private void showSpeedMenu() {
        final String[] labels = {"0.75×", "1.0×", "1.25×", "1.5×", "2.0×"};
        final float[] rates = {0.75f, 1f, 1.25f, 1.5f, 2f};
        new AlertDialog.Builder(this)
            .setTitle("מהירות ניגון")
            .setItems(labels, (dialog, which) -> {
                if (exoPlayer != null) exoPlayer.setPlaybackSpeed(rates[which]);
                if (vlcPlayer != null) vlcPlayer.setRate(rates[which]);
            })
            .show();
    }

    private void showLanguageMenu() {
        final String[] labels = {"עברית", "English"};
        final String[] codes = {"he", "en"};
        final int checked = "en".equals(selectedLanguage) ? 1 : 0;
        new AlertDialog.Builder(this)
            .setTitle(selectedLanguage.isEmpty() ? "בחר שפה / Choose language" : text("בחירת שפה", "Language"))
            .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                selectedLanguage = codes[which];
                settings().edit().putString(LANGUAGE_KEY, selectedLanguage).apply();
                applyControlLabels();
                applyPreferredTracks();
                dialog.dismiss();
                showControls(false);
            })
            .setCancelable(!selectedLanguage.isEmpty())
            .show();
    }

    private void applyControlLabels() {
        if (playPauseButton == null) return;
        rewindButton.setText(text("↶ 10", "↶ 10"));
        forwardButton.setText(text("10 ↷", "10 ↷"));
        subtitleButton.setText(text("כתוביות", "Subtitles"));
        audioButton.setText(text("שמע", "Audio"));
        speedButton.setText(text("מהירות", "Speed"));
        languageButton.setText(text("שפה", "Language"));
        sourceButton.setText(text("מקור אחר", "Other source"));
        updatePlayPauseLabel();
    }

    private void applyPreferredTracks() {
        if (vlcPlayer == null || selectedLanguage == null || selectedLanguage.isEmpty()) return;
        final String[] hints = "he".equals(selectedLanguage)
            ? new String[]{"he", "heb", "hebrew", "עברית"}
            : new String[]{"en", "eng", "english"};
        // Subtitles are opt-in. Never surprise the viewer with text on screen.
        vlcPlayer.setSpuTrack(-1);
        selectMatchingTrack(vlcPlayer.getAudioTracks(), hints, false);
    }

    private void selectMatchingTrack(MediaPlayer.TrackDescription[] tracks, String[] hints, boolean subtitle) {
        if (tracks == null) return;
        for (MediaPlayer.TrackDescription track : tracks) {
            final String name = track.name == null ? "" : track.name.toLowerCase(Locale.ROOT);
            for (String hint : hints) {
                if (name.contains(hint)) {
                    if (subtitle) vlcPlayer.setSpuTrack(track.id);
                    else vlcPlayer.setAudioTrack(track.id);
                    return;
                }
            }
        }
    }

    private String text(String hebrew, String english) {
        return "en".equals(selectedLanguage) ? english : hebrew;
    }

    private void updateProgressUi() {
        final long duration = getDuration();
        final long position = getPosition();
        if (!userSeeking) {
            seekBar.setMax((int) Math.max(1L, Math.min(Integer.MAX_VALUE, duration)));
            seekBar.setProgress((int) Math.max(0L, Math.min(Integer.MAX_VALUE, position)));
        }
        timeText.setText(formatTime(position) + " / " + formatTime(duration));
        updatePlayPauseLabel();
    }

    private void updatePlayPauseLabel() {
        playPauseButton.setText(isPlaying() ? text("עצור", "Pause") : text("נגן", "Play"));
    }

    private long getPosition() {
        if (exoPlayer != null) return Math.max(0L, exoPlayer.getCurrentPosition());
        if (vlcPlayer != null) return Math.max(0L, vlcPlayer.getTime());
        return 0L;
    }

    private long getDuration() {
        if (exoPlayer != null) return Math.max(0L, exoPlayer.getDuration());
        if (vlcPlayer != null) return Math.max(0L, vlcPlayer.getLength());
        return 0L;
    }

    private boolean isPlaying() {
        return exoPlayer != null ? exoPlayer.isPlaying() : vlcPlayer != null && vlcPlayer.isPlaying();
    }

    private void saveProgress() {
        final long position = getPosition();
        final long duration = getDuration();
        if (position < 5_000L) return;
        if (duration > 0L && position >= duration * 0.90) {
            clearProgress();
        } else {
            preferences().edit().putLong(progressKey(), position).apply();
        }
    }

    private void clearProgress() {
        preferences().edit().remove(progressKey()).apply();
    }

    private SharedPreferences preferences() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private SharedPreferences settings() {
        return getSharedPreferences(SETTINGS_NAME, MODE_PRIVATE);
    }

    private String progressKey() {
        return "progress:" + videoId;
    }

    private void showControls() {
        showControls(true);
    }

    private void showControls(boolean focusControl) {
        final boolean wasHidden = controlsPanel.getVisibility() != View.VISIBLE;
        controlsPanel.setVisibility(View.VISIBLE);
        if (focusControl && (wasHidden || controlsPanel.findFocus() == null)) {
            final View preferred = lastFocusedControl != null ? lastFocusedControl : playPauseButton;
            preferred.requestFocus();
        }
        scheduleControlsHide();
    }

    private void scheduleControlsHide() {
        handler.removeCallbacks(hideControlsRunnable);
        handler.postDelayed(hideControlsRunnable, CONTROLS_TIMEOUT_MS);
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

    private void showStatusTemporarily(String text) {
        showStatus(text);
        handler.postDelayed(this::hideStatus, 2_500L);
    }

    private void hideStatus() {
        runOnUiThread(() -> statusPanel.setVisibility(View.GONE));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_SPACE:
                    togglePlayPause();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    seekBy(-SEEK_STEP_MS);
                    return true;
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    seekBy(SEEK_STEP_MS);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (controlsPanel.getVisibility() != View.VISIBLE) {
                        showControls();
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (controlsPanel.getVisibility() != View.VISIBLE || controlsPanel.findFocus() == null) {
                        showControls(true);
                        return true;
                    }
                    scheduleControlsHide();
                    break;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (controlsPanel.getVisibility() == View.VISIBLE) {
            controlsPanel.setVisibility(View.GONE);
        } else {
            saveProgress();
            super.onBackPressed();
        }
    }

    @Override
    protected void onStop() {
        saveProgress();
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

    private Button button(String label, View.OnClickListener listener) {
        final Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16f);
        button.setTextColor(new ColorStateList(
            new int[][]{new int[]{android.R.attr.state_focused}, new int[]{}},
            new int[]{Color.WHITE, 0xFFE8E8EA}
        ));
        final GradientDrawable focused = new GradientDrawable();
        focused.setColor(0xFFE50914);
        focused.setCornerRadius(18f);
        focused.setStroke(5, Color.WHITE);
        final GradientDrawable normal = new GradientDrawable();
        normal.setColor(0xCC34363D);
        normal.setCornerRadius(18f);
        normal.setStroke(2, 0x66FFFFFF);
        final StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        button.setBackground(states);
        button.setFocusable(true);
        button.setMinWidth(128);
        button.setOnClickListener(listener);
        button.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                lastFocusedControl = view;
                scheduleControlsHide();
            }
        });
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(7, 4, 7, 4);
        button.setLayoutParams(params);
        return button;
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static FrameLayout.LayoutParams wrapContent(int gravity) {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
    }

    private static LinearLayout.LayoutParams matchWidthWrapHeight() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static String formatTime(long valueMs) {
        final long seconds = Math.max(0L, valueMs / 1000L);
        final long hours = seconds / 3600L;
        final long minutes = (seconds % 3600L) / 60L;
        final long remainder = seconds % 60L;
        return hours > 0
            ? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainder)
            : String.format(Locale.US, "%02d:%02d", minutes, remainder);
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
