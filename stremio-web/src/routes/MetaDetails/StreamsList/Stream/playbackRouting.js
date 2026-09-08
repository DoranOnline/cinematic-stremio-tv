const shouldOpenWithNativePlayer = (usesNativePlayer, streamLink) => {
    return usesNativePlayer === true &&
        typeof streamLink === 'string' &&
        /^https?:\/\//i.test(streamLink);
};

module.exports = {
    shouldOpenWithNativePlayer
};
