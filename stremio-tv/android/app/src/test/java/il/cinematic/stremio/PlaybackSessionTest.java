package il.cinematic.stremio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class PlaybackSessionTest {
    @Test
    public void legacySessionNormalizesMissingValues() {
        final PlaybackSession session = PlaybackSession.fromLegacy(
            "https://example.com/video.mp4", "", ""
        );

        assertEquals("https://example.com/video.mp4", session.getVideoId());
        assertEquals("NUVYRO", session.getTitle());
        assertEquals(0L, session.getPositionMs());
    }

    @Test
    public void withPositionIsImmutableAndClampsNegativeValues() {
        final PlaybackSession original = new PlaybackSession("s", "c", "v", "Title", 10L);
        final PlaybackSession moved = original.withPosition(25L);
        final PlaybackSession clamped = original.withPosition(-1L);

        assertEquals(10L, original.getPositionMs());
        assertEquals(25L, moved.getPositionMs());
        assertEquals(0L, clamped.getPositionMs());
        assertNotEquals(original, moved);
    }

    @Test(expected = IllegalArgumentException.class)
    public void sessionRejectsBlankVideoIdentity() {
        new PlaybackSession("s", "c", " ", "Title", 0L);
    }
}
