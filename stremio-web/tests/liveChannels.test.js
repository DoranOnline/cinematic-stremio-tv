const { getChannelCategories, getLiveCategories, channelMatchesCategory, getProgramLabel, collectLiveChannels } = require('../src/routes/Live/liveChannels');

describe('live channel presentation', () => {
    const channels = [
        { id: 'one', genres: ['News', 'Local'], catalogName: 'Israel', description: 'Morning update' },
        { id: 'two', genres: ['Sports'], catalogName: 'Israel', behaviorHints: { currentProgram: 'Live match' } }
    ];

    test('builds stable categories from real catalog metadata', () => {
        expect(getChannelCategories(channels[0])).toEqual(['News', 'Local', 'Israel']);
        expect(getLiveCategories(channels)).toEqual(['Israel', 'Local', 'News', 'Sports']);
        expect(channelMatchesCategory(channels[1], 'Sports')).toBe(true);
        expect(channelMatchesCategory(channels[1], 'News')).toBe(false);
    });

    test('uses only explicit program metadata and never mislabels a long description as EPG data', () => {
        expect(getProgramLabel(channels[1])).toBe('Live match');
        expect(getProgramLabel(channels[0])).toBe('');
        expect(getProgramLabel({})).toBe('');
    });

    test('keeps explicit TV catalogs and removes YouTube or generic channel catalogs', () => {
        const catalogs = [
            {
                name: 'Israel Live TV',
                type: 'tv',
                content: { type: 'Ready', content: [{ id: 'tv:one', name: 'News', type: 'tv' }] }
            },
            {
                name: 'YouTube',
                type: 'channel',
                content: { type: 'Ready', content: [{ id: 'yt:one', name: 'Creator', type: 'channel' }] }
            },
            {
                name: 'Video channels',
                type: 'channel',
                content: { type: 'Ready', content: [{ id: 'video:one', name: 'Playlist', type: 'channel' }] }
            }
        ];

        expect(collectLiveChannels(catalogs)).toEqual([
            expect.objectContaining({ id: 'tv:one', catalogName: 'Israel Live TV' })
        ]);
    });
});
