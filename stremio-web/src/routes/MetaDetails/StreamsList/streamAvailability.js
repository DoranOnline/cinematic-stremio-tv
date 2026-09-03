const HTTP_URL = /^https?:\/\//i;

const isPlayableStream = (stream) => {
    const externalPlayer = stream?.deepLinks?.externalPlayer;
    const streamingUrl = externalPlayer?.streaming;
    const magnetUrl = externalPlayer?.magnet;
    const directUrl = stream?.url;
    const hasTorrentIdentity = typeof stream?.infoHash === 'string' && stream.infoHash.length > 0 &&
        Number.isInteger(stream?.fileIdx) && stream.fileIdx >= 0;

    // Stremio streams may arrive as a direct URL, a resolved external-player
    // URL, a magnet, or an infoHash/fileIdx pair. Torrent add-ons commonly use
    // the last form before the local streaming server resolves an HTTP URL.
    return (typeof streamingUrl === 'string' && HTTP_URL.test(streamingUrl)) ||
        (typeof directUrl === 'string' && HTTP_URL.test(directUrl)) ||
        (typeof magnetUrl === 'string' && /^magnet:\?/i.test(magnetUrl)) ||
        hasTorrentIdentity;
};

const hasPlayableStreams = (streams) => {
    return Array.isArray(streams) && streams.some(isPlayableStream);
};

module.exports = {
    isPlayableStream,
    hasPlayableStreams
};
