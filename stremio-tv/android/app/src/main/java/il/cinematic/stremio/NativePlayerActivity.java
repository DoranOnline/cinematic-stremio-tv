package il.cinematic.stremio;

import android.app.Activity;
import android.app.AlertDialog;
import android.annotation.SuppressLint;
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
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;
import java.util.Locale;

@SuppressLint("UnsafeOptInUsageError")
public final class NativePlayerActivity extends Activity {
    static final String EXTRA_STREAM_URL = "stream_url";
    static final String EXTRA_VIDEO_ID = "video_id";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_PLAYBACK_ENDED = "playback_ended";

    private static final long SLOW_SOURCE_NOTICE_MS = 15_000L;
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
    private FrameLayout controlsPanel;
    private TextView statusText;
    private LinearLayout statusActions;
    private Button statusPrimaryButton;
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
    private Button tracksButton;
    private Button lockButton;
    private Button unlockButton;
    private Button closeButton;
    private LinearLayout primaryControls;
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
    private boolean playbackEndReported;
    private long lastSavedAt;
    private String selectedLanguage;
    private boolean controlsLocked;
    private int brightnessPercent = 70;
    private PlayerState playerState = PlayerState.IDLE;

    private final Runnable slowSourceNoticeRunnable = () -> {
        if (!firstFrameRendered && !usingVlc && !isFinishing()) {
            playerState = PlayerState.BUFFERING;
            showRecoveryStatus(
                text("המקור הזה לוקח יותר זמן מהרגיל", "This source is taking longer than expected"),
                text("להמשיך לחכות", "Still waiting"),
                () -> showStatus(text("ממשיך לחכות למקור…", "Still waiting for the source…"))
            );
        }
    };

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
        if (title == null || title.isEmpty()) title = "NUVYRO";
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
        statusText.setText("NUVYRO מכין את הווידאו…");
        statusPanel.addView(statusText);
        statusActions = new LinearLayout(this);
        statusActions.setOrientation(LinearLayout.HORIZONTAL);
        statusActions.setGravity(Gravity.CENTER);
        statusActions.setVisibility(View.GONE);
        statusPrimaryButton = compactButton("להמשיך לחכות", view -> {});
        statusActions.addView(statusPrimaryButton);
        final Button compatibilityButton = compactButton("מצב תאימות", view ->
            startVlcFallback(text("פותח במצב תאימות…", "Opening compatibility mode…")));
        statusActions.addView(compatibilityButton);
        final Button chooseSourceButton = compactButton("מקור אחר", view -> finishPlayer());
        statusActions.addView(chooseSourceButton);
        statusPanel.addView(statusActions);
        final FrameLayout.LayoutParams params = wrapContent(Gravity.CENTER);
        root.addView(statusPanel, params);
    }

    private void buildControlsPanel(FrameLayout root) {
        controlsPanel = new FrameLayout(this);
        final GradientDrawable background = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xD9000000, 0x55000000, 0xE6000000}
        );
        controlsPanel.setBackground(background);

        final LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(44, 26, 36, 0);
        final TextView brand = new TextView(this);
        brand.setText("N");
        brand.setTextColor(0xFFF02D62);
        brand.setTextSize(25f);
        brand.setPadding(0, 0, 16, 0);
        topBar.addView(brand);
        titleText = new TextView(this);
        titleText.setText(title);
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(22f);
        titleText.setSingleLine(true);
        topBar.addView(titleText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        languageButton = compactButton("שפה", view -> showLanguageMenu());
        topBar.addView(languageButton);
        closeButton = compactButton("✕", view -> finishPlayer());
        closeButton.setContentDescription("Close player");
        topBar.addView(closeButton);
        controlsPanel.addView(topBar, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        primaryControls = new LinearLayout(this);
        primaryControls.setOrientation(LinearLayout.HORIZONTAL);
        primaryControls.setGravity(Gravity.CENTER);
        rewindButton = primaryButton("↶ 10", view -> seekBy(-SEEK_STEP_MS));
        primaryControls.addView(rewindButton);
        playPauseButton = primaryButton("❚❚", view -> togglePlayPause());
        playPauseButton.setTextSize(31f);
        primaryControls.addView(playPauseButton);
        forwardButton = primaryButton("10 ↷", view -> seekBy(SEEK_STEP_MS));
        primaryControls.addView(forwardButton);
        controlsPanel.addView(primaryControls, wrapContent(Gravity.CENTER));

        final LinearLayout brightnessPanel = new LinearLayout(this);
        brightnessPanel.setOrientation(LinearLayout.VERTICAL);
        brightnessPanel.setGravity(Gravity.CENTER);
        brightnessPanel.setPadding(28, 0, 0, 0);
        final TextView brightnessIcon = new TextView(this);
        brightnessIcon.setText("☀");
        brightnessIcon.setTextColor(Color.WHITE);
        brightnessIcon.setTextSize(23f);
        brightnessIcon.setGravity(Gravity.CENTER);
        brightnessPanel.addView(brightnessIcon, new LinearLayout.LayoutParams(64, 54));
        final Button brighter = compactButton("＋", view -> changeBrightness(10));
        brighter.setContentDescription("Increase brightness");
        brightnessPanel.addView(brighter);
        final Button dimmer = compactButton("−", view -> changeBrightness(-10));
        dimmer.setContentDescription("Decrease brightness");
        brightnessPanel.addView(dimmer);
        final FrameLayout.LayoutParams brightnessParams = new FrameLayout.LayoutParams(130, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.START);
        controlsPanel.addView(brightnessPanel, brightnessParams);

        final LinearLayout bottomPanel = new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setPadding(44, 0, 44, 28);

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
        bottomPanel.addView(timeline, matchWidthWrapHeight());

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
        speedButton = button("מהירות", view -> showSpeedMenu());
        actions.addView(speedButton);
        lockButton = button("נעילת מסך", view -> lockControls());
        actions.addView(lockButton);
        tracksButton = button("שמע וכתוביות", view -> showTracksMenu());
        actions.addView(tracksButton);
        sourceButton = button("מקור אחר", view -> finish());
        actions.addView(sourceButton);
        actionScroller.addView(actions, matchWidthWrapHeight());
        bottomPanel.addView(actionScroller, matchWidthWrapHeight());
        controlsPanel.addView(bottomPanel, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        unlockButton = primaryButton("🔒", view -> unlockControls());
        unlockButton.setContentDescription("Unlock controls");
        unlockButton.setVisibility(View.GONE);
        root.addView(unlockButton, wrapContent(Gravity.CENTER));
        applyControlLabels();

        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
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
        playerState = PlayerState.RESOLVING_SOURCE;
        if (isLocalStreamServerUrl(streamUrl)) {
            startVlcFallback("פותח במצב תאימות לטלוויזיה…");
        } else {
            startMedia3();
        }
    }

    private void startMedia3() {
        releasePlayers();
        playerState = PlayerState.PREPARING;
        usingVlc = false;
        firstFrameRendered = false;
        playerView.setVisibility(View.VISIBLE);
        vlcView.setVisibility(View.GONE);
        showStatus("מתחבר למקור…");
        final DefaultRenderersFactory renderers = new DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true);
        exoPlayer = new ExoPlayer.Builder(this, renderers)
            .setLoadControl(new DefaultLoadControl.Builder()
                .setBufferDurationsMs(12_000, 50_000, 500, 1_000)
                .build())
            .setHandleAudioBecomingNoisy(true)
            .build();
        exoPlayer.setTrackSelectionParameters(
            exoPlayer.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        );
        exoPlayer.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) {
                handler.removeCallbacks(slowSourceNoticeRunnable);
                if (PlaybackFailurePolicy.shouldSwitchEngine(error.getErrorCodeName())) {
                    playerState = PlayerState.SWITCHING_ENGINE;
                    startVlcFallback(text("הפורמט דורש מצב תאימות…", "This format needs compatibility mode…"));
                } else {
                    playerState = PlayerState.ERROR;
                    showRecoveryStatus(
                        text("לא הצלחנו להתחבר למקור הזה", "We could not connect to this source"),
                        text("נסה שוב", "Try again"),
                        NativePlayerActivity.this::retryCurrentEngine
                    );
                }
            }
            @Override public void onRenderedFirstFrame() {
                handler.removeCallbacks(slowSourceNoticeRunnable);
                firstFrameRendered = true;
                playerState = PlayerState.PLAYING;
                hideStatus();
                applyPendingResume();
                scheduleControlsHide();
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    playerState = PlayerState.BUFFERING;
                    showStatus(text("טוען וידאו…", "Buffering video…"));
                }
                if (state == Player.STATE_READY) {
                    playerState = exoPlayer != null && exoPlayer.isPlaying() ? PlayerState.PLAYING : PlayerState.PAUSED;
                    applyPendingResume();
                }
                if (state == Player.STATE_ENDED) {
                    playerState = PlayerState.ENDED;
                    reportPlaybackEnded();
                }
            }
        });
        playerView.setPlayer(exoPlayer);
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)));
        exoPlayer.prepare();
        exoPlayer.play();
        handler.removeCallbacks(slowSourceNoticeRunnable);
        handler.postDelayed(slowSourceNoticeRunnable, SLOW_SOURCE_NOTICE_MS);
    }

    private void startVlcFallback(String message) {
        if (usingVlc || isFinishing()) return;
        handler.removeCallbacks(slowSourceNoticeRunnable);
        playerState = PlayerState.SWITCHING_ENGINE;
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
        options.add("--network-caching=1000");
        options.add("--live-caching=1000");
        options.add("--file-caching=700");
        options.add("--clock-jitter=0");
        options.add("--clock-synchro=0");
        libVlc = new LibVLC(this, options);
        vlcPlayer = new MediaPlayer(libVlc);
        vlcPlayer.attachViews(vlcView, null, false, false);
        vlcPlayer.setEventListener(event -> {
            if (event.type == MediaPlayer.Event.Playing || event.type == MediaPlayer.Event.Vout) {
                vlcStarted = true;
                playerState = PlayerState.PLAYING;
                hideStatus();
                applyPendingResume();
                if (!initialTrackPreferencesApplied) {
                    initialTrackPreferencesApplied = true;
                    applyPreferredTracks();
                }
                scheduleControlsHide();
            } else if (event.type == MediaPlayer.Event.Buffering && !vlcStarted) {
                playerState = PlayerState.BUFFERING;
                showStatus("טוען במצב תאימות…");
            } else if (event.type == MediaPlayer.Event.EncounteredError) {
                playerState = PlayerState.ERROR;
                showRecoveryStatus(
                    text("המקור הזה לא מגיב", "This source is not responding"),
                    text("נסה שוב", "Try again"),
                    this::retryCurrentEngine
                );
            } else if (event.type == MediaPlayer.Event.EndReached) {
                runOnUiThread(this::reportPlaybackEnded);
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

    private void reportPlaybackEnded() {
        if (playbackEndReported || isFinishing()) return;
        playbackEndReported = true;
        clearProgress();
        final Intent result = new Intent();
        result.putExtra(EXTRA_PLAYBACK_ENDED, true);
        setResult(RESULT_OK, result);
        showStatus(text("עובר לפרק הבא…", "Starting the next episode…"));
        handler.postDelayed(this::finish, 650L);
    }

    private void togglePlayPause() {
        if (exoPlayer != null) {
            if (exoPlayer.isPlaying()) exoPlayer.pause(); else exoPlayer.play();
        } else if (vlcPlayer != null) {
            if (vlcPlayer.isPlaying()) vlcPlayer.pause(); else vlcPlayer.play();
        }
        updatePlayPauseLabel();
        playerState = isPlaying() ? PlayerState.PLAYING : PlayerState.PAUSED;
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

    private void showTracksMenu() {
        final String[] labels = {text("כתוביות", "Subtitles"), text("שמע", "Audio")};
        new AlertDialog.Builder(this)
            .setTitle(text("שמע וכתוביות", "Audio & subtitles"))
            .setItems(labels, (dialog, which) -> {
                if (which == 0) showSubtitleMenu(); else showAudioMenu();
            })
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
        speedButton.setText(text("מהירות", "Speed"));
        languageButton.setText(text("שפה", "Language"));
        tracksButton.setText(text("שמע וכתוביות", "Audio & subtitles"));
        lockButton.setText(text("נעילת מסך", "Lock controls"));
        sourceButton.setText(text("מקור אחר", "Other source"));
        closeButton.setContentDescription(text("סגירת נגן", "Close player"));
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
        playPauseButton.setText(isPlaying() ? "❚❚" : "▶");
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
        if (controlsLocked) {
            unlockButton.setVisibility(View.VISIBLE);
            if (focusControl) unlockButton.requestFocus();
            return;
        }
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

    private void lockControls() {
        controlsLocked = true;
        controlsPanel.setVisibility(View.GONE);
        unlockButton.setVisibility(View.VISIBLE);
        unlockButton.requestFocus();
        handler.removeCallbacks(hideControlsRunnable);
    }

    private void unlockControls() {
        controlsLocked = false;
        unlockButton.setVisibility(View.GONE);
        showControls(true);
    }

    private void setPlayerBrightness(int value) {
        final WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = Math.max(0.05f, value / 100f);
        getWindow().setAttributes(params);
    }

    private void changeBrightness(int delta) {
        brightnessPercent = Math.max(5, Math.min(100, brightnessPercent + delta));
        setPlayerBrightness(brightnessPercent);
        showStatusTemporarily(text("בהירות " + brightnessPercent + "%", "Brightness " + brightnessPercent + "%"));
    }

    private void finishPlayer() {
        saveProgress();
        finish();
    }

    private void showStatus(String text) {
        runOnUiThread(() -> {
            statusText.setText(text);
            if (statusActions != null) statusActions.setVisibility(View.GONE);
            statusPanel.setVisibility(View.VISIBLE);
            if (primaryControls != null) primaryControls.setVisibility(View.INVISIBLE);
        });
    }

    private void showRecoveryStatus(String message, String primaryLabel, Runnable primaryAction) {
        runOnUiThread(() -> {
            statusText.setText(message);
            if (statusActions != null) {
                statusPrimaryButton.setText(primaryLabel);
                statusPrimaryButton.setOnClickListener(view -> primaryAction.run());
                statusActions.setVisibility(View.VISIBLE);
                statusPrimaryButton.requestFocus();
            }
            statusPanel.setVisibility(View.VISIBLE);
            if (primaryControls != null) primaryControls.setVisibility(View.INVISIBLE);
        });
    }

    private void retryCurrentEngine() {
        if (usingVlc) {
            releasePlayers();
            usingVlc = false;
            startVlcFallback(text("מנסה שוב במצב תאימות…", "Retrying compatibility mode…"));
        } else {
            startMedia3();
        }
    }

    private void showStatusTemporarily(String text) {
        showStatus(text);
        handler.postDelayed(this::hideStatus, 2_500L);
    }

    private void hideStatus() {
        runOnUiThread(() -> {
            statusPanel.setVisibility(View.GONE);
            if (primaryControls != null) primaryControls.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (controlsLocked) {
                unlockButton.setVisibility(View.VISIBLE);
                unlockButton.requestFocus();
                return true;
            }
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
        if (controlsLocked) {
            unlockControls();
            return;
        }
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
        playerState = PlayerState.IDLE;
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
        focused.setColor(0xFFF02D62);
        focused.setCornerRadius(18f);
        focused.setStroke(4, 0xFFF6F1EA);
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
                view.animate().scaleX(1.08f).scaleY(1.08f).setDuration(90L).start();
                view.setElevation(18f);
                scheduleControlsHide();
            } else {
                view.animate().scaleX(1f).scaleY(1f).setDuration(90L).start();
                view.setElevation(0f);
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

    private Button compactButton(String label, View.OnClickListener listener) {
        final Button result = button(label, listener);
        result.setMinWidth(88);
        result.setTextSize(15f);
        return result;
    }

    private Button primaryButton(String label, View.OnClickListener listener) {
        final Button result = button(label, listener);
        result.setMinWidth(142);
        result.setMinHeight(94);
        result.setTextSize(22f);
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(22, 8, 22, 8);
        result.setLayoutParams(params);
        return result;
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
