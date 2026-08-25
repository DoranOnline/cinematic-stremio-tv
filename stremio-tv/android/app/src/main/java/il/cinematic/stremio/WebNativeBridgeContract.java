package il.cinematic.stremio;

/** Central compatibility names for the current bridge and the future versioned contract. */
final class WebNativeBridgeContract {
    static final int VERSION = 1;
    static final String LEGACY_OBJECT_NAME = "CinematicAndroid";
    static final String EVENT_PLAYBACK_ENDED = "nuvyro-playback-ended";
    static final String RETURN_FOCUS_ATTRIBUTE = "data-cinematic-return-focus";

    private WebNativeBridgeContract() {}
}

