const { createMarkVideoAsWatchedAction } = require('../src/routes/MetaDetails/StreamsList/Stream/nativePlaybackSync');

describe('native playback library sync', () => {
    test('creates the same MetaDetails watched action used by external playback', () => {
        const released = new Date('2007-09-24T00:00:00.000Z');

        expect(createMarkVideoAsWatchedAction('tt0898266:1:1', released)).toEqual({
            action: 'MetaDetails',
            args: {
                action: 'MarkVideoAsWatched',
                args: [{ id: 'tt0898266:1:1', released }, true]
            }
        });
    });

    test('does not dispatch an invalid history entry without a video id', () => {
        expect(createMarkVideoAsWatchedAction('', null)).toBeNull();
        expect(createMarkVideoAsWatchedAction(undefined, null)).toBeNull();
    });
});
