const { shouldOpenWithNativePlayer } = require('../src/routes/MetaDetails/StreamsList/Stream/playbackRouting');

describe('stream playback routing', () => {
    test('opens a resolved HTTP stream in the native player', () => {
        expect(shouldOpenWithNativePlayer(true, 'http://127.0.0.1:11470/stream')).toBe(true);
        expect(shouldOpenWithNativePlayer(true, 'https://example.test/video.m3u8')).toBe(true);
    });

    test('keeps unresolved torrent sources on the Stremio player route', () => {
        expect(shouldOpenWithNativePlayer(true, undefined)).toBe(false);
        expect(shouldOpenWithNativePlayer(true, '')).toBe(false);
        expect(shouldOpenWithNativePlayer(true, 'magnet:?xt=urn:btih:test')).toBe(false);
    });

    test('does not use native playback when its bridge is unavailable', () => {
        expect(shouldOpenWithNativePlayer(false, 'https://example.test/video.mp4')).toBe(false);
    });
});
