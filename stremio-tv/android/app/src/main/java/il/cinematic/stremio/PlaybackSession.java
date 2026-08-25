package il.cinematic.stremio;

import java.util.Objects;

/**
 * Immutable identity for one viewing session. V3 will extend this value object with
 * structured sources and episode context while retaining the legacy bridge adapter.
 */
final class PlaybackSession {
    private final String sessionId;
    private final String contentId;
    private final String videoId;
    private final String title;
    private final long positionMs;

    PlaybackSession(
        String sessionId,
        String contentId,
        String videoId,
        String title,
        long positionMs
    ) {
        this.sessionId = requireText(sessionId, "sessionId");
        this.contentId = normalize(contentId);
        this.videoId = requireText(videoId, "videoId");
        this.title = requireText(title, "title");
        this.positionMs = Math.max(0L, positionMs);
    }

    static PlaybackSession fromLegacy(String streamUrl, String videoId, String title) {
        final String stableVideoId = isBlank(videoId) ? requireText(streamUrl, "streamUrl") : videoId.trim();
        final String stableTitle = isBlank(title) ? "NUVYRO" : title.trim();
        final String sessionId = stableVideoId + ":" + System.currentTimeMillis();
        return new PlaybackSession(sessionId, "", stableVideoId, stableTitle, 0L);
    }

    String getSessionId() { return sessionId; }
    String getContentId() { return contentId; }
    String getVideoId() { return videoId; }
    String getTitle() { return title; }
    long getPositionMs() { return positionMs; }

    PlaybackSession withPosition(long newPositionMs) {
        return new PlaybackSession(sessionId, contentId, videoId, title, newPositionMs);
    }

    private static String requireText(String value, String field) {
        if (isBlank(value)) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PlaybackSession)) return false;
        final PlaybackSession that = (PlaybackSession) other;
        return positionMs == that.positionMs &&
            sessionId.equals(that.sessionId) && contentId.equals(that.contentId) &&
            videoId.equals(that.videoId) && title.equals(that.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, contentId, videoId, title, positionMs);
    }
}

