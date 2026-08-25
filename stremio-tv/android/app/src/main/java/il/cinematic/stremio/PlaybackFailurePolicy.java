package il.cinematic.stremio;

import java.util.Locale;

/** Decides whether an engine switch is justified by evidence of incompatibility. */
final class PlaybackFailurePolicy {
    private PlaybackFailurePolicy() {}

    static boolean shouldSwitchEngine(String errorCodeName) {
        if (errorCodeName == null) return false;
        final String code = errorCodeName.toUpperCase(Locale.ROOT);
        return code.contains("DECOD") ||
            code.contains("PARSING_CONTAINER_UNSUPPORTED") ||
            code.contains("CONTENT_TYPE_MISMATCHED");
    }
}

