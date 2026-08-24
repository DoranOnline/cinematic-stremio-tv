// Copyright (C) 2017-2024 Smart code 203358507

const RESOLUTIONS = [
    { pattern: /\b(2160p|4k|uhd)\b/i, label: '4K', score: 45 },
    { pattern: /\b(1080p|full[ .-]?hd)\b/i, label: '1080p', score: 35 },
    { pattern: /\b720p\b/i, label: '720p', score: 20 },
    { pattern: /\b(480p|sd)\b/i, label: 'SD', score: 5 }
];

const extractNumber = (text, patterns) => {
    for (const pattern of patterns) {
        const match = text.match(pattern);
        if (match) return Number(match[1]);
    }
    return null;
};

const analyzeSource = ({ name = '', description = '', addonName = '', preferredAddons = [], hasPlayableUrl = false }) => {
    const text = `${name} ${description}`;
    const resolution = RESOLUTIONS.find(({ pattern }) => pattern.test(text)) || null;
    const peers = extractNumber(text, [/(?:👤|peers?|seeders?|seeds?)\s*[:=]?\s*(\d+)/i, /(\d+)\s*(?:peers?|seeders?|seeds?)/i]);
    const sizeMatch = text.match(/\b(\d+(?:\.\d+)?)\s*(GB|MB)\b/i);
    const badges = [];
    if (resolution) badges.push(resolution.label);
    if (/\b(dolby[ .-]?vision|dv)\b/i.test(text)) badges.push('Dolby Vision');
    else if (/\bhdr10\+?\b|\bhdr\b/i.test(text)) badges.push('HDR');
    if (/\b(hevc|h\.?265|x265)\b/i.test(text)) badges.push('HEVC');
    else if (/\b(avc|h\.?264|x264)\b/i.test(text)) badges.push('H.264');
    if (/\bheb(?:rew)?\b|עברית/i.test(text)) badges.push('עברית');
    if (sizeMatch) badges.push(`${sizeMatch[1]} ${sizeMatch[2].toUpperCase()}`);
    if (peers !== null) badges.push(`${peers} peers`);

    const preferredIndex = preferredAddons.indexOf(addonName);
    const preferredScore = preferredIndex === -1 ? 0 : Math.max(8, 30 - preferredIndex * 4);
    const peerScore = peers === null ? 0 : Math.min(30, Math.log2(peers + 1) * 4);
    const score = (hasPlayableUrl ? 60 : -100) + (resolution?.score || 0) + preferredScore + peerScore;

    return { badges, score, resolution: resolution?.label || null, peers };
};

const rankSources = (sources, preferredAddons = []) => sources
    .map((source, originalIndex) => ({
        ...source,
        originalIndex,
        intelligence: analyzeSource({
            ...source,
            preferredAddons,
            hasPlayableUrl: typeof source.deepLinks?.externalPlayer?.streaming === 'string'
        })
    }))
    .sort((left, right) => right.intelligence.score - left.intelligence.score || left.originalIndex - right.originalIndex)
    .map((source, index) => ({ ...source, isBestMatch: index === 0 && source.intelligence.score > 0 }));

module.exports = { analyzeSource, rankSources };
