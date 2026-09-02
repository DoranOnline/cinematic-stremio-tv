const {
    isPlayableStream,
    hasPlayableStreams
} = require('../src/routes/MetaDetails/StreamsList/streamAvailability');

describe('stream source availability', () => {
    test('keeps only add-ons that returned a playable HTTP stream', () => {
        const playable = {
            deepLinks: {
                externalPlayer: {
                    streaming: 'http://127.0.0.1:11470/stream/example'
                }
            }
        };

        expect(isPlayableStream(playable)).toBe(true);
        expect(hasPlayableStreams([playable])).toBe(true);
        expect(hasPlayableStreams([])).toBe(false);
        expect(hasPlayableStreams([{ name: 'metadata only' }])).toBe(false);
    });

    test('rejects actions and non-video deep links from the provider menu', () => {
        expect(isPlayableStream({
            deepLinks: { externalPlayer: { streaming: 'stremio:///detail/movie/example' } }
        })).toBe(false);
        expect(isPlayableStream({ externalUrl: 'https://example.com/watch' })).toBe(false);
    });
});
