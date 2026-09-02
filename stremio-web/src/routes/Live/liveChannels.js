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

const isYouTubeCatalog = (catalog) => [catalog?.name, catalog?.label, catalog?.id]
    .some((value) => normalizeLabel(value).toLowerCase().includes('youtube'));

const collectLiveChannels = (catalogs) => {
    const seen = new Set();
    return (Array.isArray(catalogs) ? catalogs : [])
        .filter((catalog) => catalog?.content?.type === 'Ready' && !isYouTubeCatalog(catalog))
        .flatMap((catalog) => {
            const catalogType = normalizeLabel(catalog?.type).toLowerCase();
            return (Array.isArray(catalog.content.content) ? catalog.content.content : [])
                // Stremio uses `channel` for YouTube-style creator catalogs as
                // well as some non-linear content. Live TV is intentionally
                // restricted to explicit TV catalogs so it never fabricates a
                // channel guide from ordinary videos.
                .filter((item) => normalizeLabel(item?.type || catalogType).toLowerCase() === 'tv')
                .map((item) => ({ ...item, catalogName: catalog.name || catalog.label || '' }));
        })
        .filter((item) => {
            const key = item.id || item.name;
            if (!key || seen.has(key)) return false;
            seen.add(key);
            return true;
        });
};

module.exports = {
    getChannelCategories,
    getLiveCategories,
    channelMatchesCategory,
    getProgramLabel,
    isYouTubeCatalog,
    collectLiveChannels
};
