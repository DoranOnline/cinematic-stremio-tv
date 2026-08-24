package il.cinematic.stremio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackLinkPolicyTest {
    @Test
    public void acceptsOnlyStremioPlayerLinks() {
        assertTrue(PlaybackLinkPolicy.isSupported("stremio:///player/movie/tt123/stream"));
        assertFalse(PlaybackLinkPolicy.isSupported("stremio:///detail/movie/tt123"));
        assertFalse(PlaybackLinkPolicy.isSupported("https://example.com/player/movie"));
        assertFalse(PlaybackLinkPolicy.isSupported(null));
    }

    @Test
    public void rejectsMalformedAndOversizedLinks() {
        assertFalse(PlaybackLinkPolicy.isSupported("not a uri"));
        assertFalse(PlaybackLinkPolicy.isSupported("stremio:///player/" + "x".repeat(16_384)));
    }
}
