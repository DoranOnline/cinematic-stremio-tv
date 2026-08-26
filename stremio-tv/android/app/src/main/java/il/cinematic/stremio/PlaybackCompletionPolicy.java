package il.cinematic.stremio;

/** Shared completion rule for streams that never emit a clean ended event. */
final class PlaybackCompletionPolicy {
    private static final long MINIMUM_DURATION_MS = 60_000L;
    private static final long END_TOLERANCE_MS = 8_000L;
    private static final double COMPLETION_RATIO = 0.97d;

    private PlaybackCompletionPolicy() {}

    static boolean isCompleted(long positionMs, long durationMs) {
        if (durationMs < MINIMUM_DURATION_MS || positionMs < 0L) return false;
        return positionMs >= durationMs - END_TOLERANCE_MS ||
            (double) positionMs / (double) durationMs >= COMPLETION_RATIO;
    }
}
