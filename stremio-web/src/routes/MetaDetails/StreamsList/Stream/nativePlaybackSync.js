const createMarkVideoAsWatchedAction = (videoId, videoReleased) => {
    if (typeof videoId !== 'string' || videoId.length === 0) return null;

    return {
        action: 'MetaDetails',
        args: {
            action: 'MarkVideoAsWatched',
            args: [{ id: videoId, released: videoReleased }, true]
        }
    };
};

module.exports = {
    createMarkVideoAsWatchedAction,
};
