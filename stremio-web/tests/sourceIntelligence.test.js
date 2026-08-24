const { analyzeSource, rankSources } = require('../src/routes/MetaDetails/StreamsList/sourceIntelligence');

describe('source intelligence', () => {
    test('extracts useful viewing badges from noisy addon text', () => {
        const result = analyzeSource({
            name: 'Movie.2160p.DV.HEVC',
            description: 'עברית • 12.4 GB • Seeders: 148',
            addonName: 'Example',
            hasPlayableUrl: true
        });
        expect(result.badges).toEqual(expect.arrayContaining(['4K', 'Dolby Vision', 'HEVC', 'עברית', '12.4 GB', '148 peers']));
    });

    test('ranks playable, preferred, healthy sources ahead of raw results', () => {
        const ranked = rankSources([
            { addonName: 'Slow', name: '720p', description: '2 peers', deepLinks: {} },
            { addonName: 'Favorite', name: '1080p HEVC', description: '80 seeders', deepLinks: { externalPlayer: { streaming: 'https://example.test/video' } } }
        ], ['Favorite']);
        expect(ranked[0].addonName).toBe('Favorite');
        expect(ranked[0].isBestMatch).toBe(true);
        expect(ranked[1].isBestMatch).toBe(false);
    });

    test('never labels an unplayable source as best match', () => {
        const [source] = rankSources([{ addonName: 'Example', name: '4K', description: '999 peers', deepLinks: {} }]);
        expect(source.isBestMatch).toBe(false);
    });
});
