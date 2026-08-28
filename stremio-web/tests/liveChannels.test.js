const { getChannelCategories, getLiveCategories, channelMatchesCategory, getProgramLabel } = require('../src/routes/Live/liveChannels');

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
});
