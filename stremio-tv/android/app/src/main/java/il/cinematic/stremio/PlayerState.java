package il.cinematic.stremio;

/** The explicit states visible to the NUVYRO player UI and bridge. */
enum PlayerState {
    IDLE,
    RESOLVING_SOURCE,
    PREPARING,
    BUFFERING,
    PLAYING,
    PAUSED,
    SEEKING,
    ENDED,
    ERROR,
    SWITCHING_ENGINE,
    SWITCHING_SOURCE,
    LOADING_NEXT_EPISODE
}

