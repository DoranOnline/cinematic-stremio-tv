const HTTP_URL = /^https?:\/\//i;

const isPlayableStream = (stream) => {
    const externalPlayer = stream?.deepLinks?.externalPlayer;
    const streamingUrl = externalPlayer?.streaming;
    const magnetUrl = externalPlayer?.magnet;

    // Direct streams are ready for the native player. Torrent add-ons may
    // initially expose only a magnet link while Stremio's local server is
    // preparing the HTTP playback URL, so they must remain visible as well.
    return (typeof streamingUrl === 'string' && HTTP_URL.test(streamingUrl)) ||
        (typeof magnetUrl === 'string' && /^magnet:\?/i.test(magnetUrl));
};

const hasPlayableStreams = (streams) => {
    return Array.isArray(streams) && streams.some(isPlayableStream);
};

module.exports = {
    isPlayableStream,
    hasPlayableStreams
};
