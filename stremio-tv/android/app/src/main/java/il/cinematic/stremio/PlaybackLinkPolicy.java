package il.cinematic.stremio;

import java.net.URI;
import java.net.URISyntaxException;

final class PlaybackLinkPolicy {
    private PlaybackLinkPolicy() {}

    static boolean isSupported(String deepLink) {
        if (deepLink == null || deepLink.length() > 16_384) {
            return false;
        }

        try {
            final URI uri = new URI(deepLink);
            final String scheme = uri.getScheme();
            final String path = uri.getPath();
            return "stremio".equalsIgnoreCase(scheme)
                && path != null
                && path.startsWith("/player/");
        } catch (URISyntaxException ignored) {
            return false;
        }
    }
}
