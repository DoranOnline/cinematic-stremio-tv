const HTTP_URL = /^https?:\/\//i;

const isPlayableStream = (stream) => {
    const url = stream?.deepLinks?.externalPlayer?.streaming;
    return typeof url === 'string' && HTTP_URL.test(url);
};

const hasPlayableStreams = (streams) => {
    return Array.isArray(streams) && streams.some(isPlayableStream);
};

module.exports = {
    isPlayableStream,
    hasPlayableStreams
};
