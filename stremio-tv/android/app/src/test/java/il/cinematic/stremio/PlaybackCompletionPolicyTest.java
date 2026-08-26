package il.cinematic.stremio;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackCompletionPolicyTest {
    @Test
    public void completesNearTheEndEvenWithoutEndedEvent() {
        assertTrue(PlaybackCompletionPolicy.isCompleted(5_820_000L, 6_000_000L));
        assertTrue(PlaybackCompletionPolicy.isCompleted(5_994_000L, 6_000_000L));
    }

    @Test
    public void doesNotCompleteShortOrPartiallyWatchedMedia() {
        assertFalse(PlaybackCompletionPolicy.isCompleted(30_000L, 50_000L));
        assertFalse(PlaybackCompletionPolicy.isCompleted(4_500_000L, 6_000_000L));
        assertFalse(PlaybackCompletionPolicy.isCompleted(0L, 0L));
    }
}
