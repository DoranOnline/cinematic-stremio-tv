const isPlayableEpisode = (video) => video &&
    typeof video.id === 'string' && video.id.length > 0 &&
    typeof video.season === 'number' &&
    typeof video.episode === 'number' &&
    !video.upcoming;

const getNextPlayableEpisode = (videos, currentVideoId) => {
    if (!Array.isArray(videos) || typeof currentVideoId !== 'string') return null;

    const orderedEpisodes = videos
        .filter(isPlayableEpisode)
        .slice()
        .sort((left, right) => left.season - right.season || left.episode - right.episode);
    const currentIndex = orderedEpisodes.findIndex((candidate) => candidate.id === currentVideoId);

    return currentIndex >= 0 ? orderedEpisodes[currentIndex + 1] || null : null;
};

module.exports = { getNextPlayableEpisode };
