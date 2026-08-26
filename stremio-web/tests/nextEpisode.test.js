const { getNextPlayableEpisode } = require('../src/routes/MetaDetails/nextEpisode');

describe('getNextPlayableEpisode', () => {
    test('uses the exact next video even when ids do not follow a predictable format', () => {
        const videos = [
            { id: 'provider:opaque-a', season: 1, episode: 1 },
            { id: 'provider:opaque-b', season: 1, episode: 2 }
        ];

        expect(getNextPlayableEpisode(videos, 'provider:opaque-a')).toEqual(videos[1]);
    });

    test('crosses season boundaries', () => {
        const videos = [
            { id: 's2e1', season: 2, episode: 1 },
            { id: 's1e9', season: 1, episode: 9 }
        ];

        expect(getNextPlayableEpisode(videos, 's1e9').id).toBe('s2e1');
    });

    test('skips upcoming and malformed episodes', () => {
        const videos = [
            { id: 's1e1', season: 1, episode: 1 },
            { id: 's1e2', season: 1, episode: 2, upcoming: true },
            { id: 'special' },
            { id: 's1e3', season: 1, episode: 3 }
        ];

        expect(getNextPlayableEpisode(videos, 's1e1').id).toBe('s1e3');
    });

    test('returns null at the end or when current episode is unknown', () => {
        const videos = [{ id: 's1e1', season: 1, episode: 1 }];

        expect(getNextPlayableEpisode(videos, 's1e1')).toBeNull();
        expect(getNextPlayableEpisode(videos, 'missing')).toBeNull();
    });
});
