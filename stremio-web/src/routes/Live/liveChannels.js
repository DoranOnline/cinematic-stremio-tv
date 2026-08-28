const normalizeLabel = (value) => typeof value === 'string' ? value.trim() : '';

const getChannelCategories = (channel) => {
    const labels = [
        ...(Array.isArray(channel?.genres) ? channel.genres : []),
        channel?.catalogName
    ].map(normalizeLabel).filter(Boolean);
    return Array.from(new Set(labels));
};

const getLiveCategories = (channels) => Array.from(new Set(
    (Array.isArray(channels) ? channels : []).flatMap(getChannelCategories)
)).sort((left, right) => left.localeCompare(right));

const channelMatchesCategory = (channel, category) => !category || getChannelCategories(channel).includes(category);

const getProgramLabel = (channel) => normalizeLabel(
    channel?.behaviorHints?.currentProgram ||
    channel?.currentProgram
);

module.exports = { getChannelCategories, getLiveCategories, channelMatchesCategory, getProgramLabel };
