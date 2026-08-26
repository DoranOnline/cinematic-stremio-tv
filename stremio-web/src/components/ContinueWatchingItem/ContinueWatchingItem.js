// Copyright (C) 2017-2023 Smart code 203358507

const React = require('react');
const PropTypes = require('prop-types');
const { useCore } = require('stremio/core');
const LibItem = require('stremio/components/LibItem');

const ContinueWatchingItem = ({ _id, notifications, ...props }) => {
    const core = useCore();
    const [nativeProgress, setNativeProgress] = React.useState(null);

    const refreshNativeProgress = React.useCallback(() => {
        if (typeof _id !== 'string') return;
        try {
            const progress = JSON.parse(localStorage.getItem('nuvyro.playbackProgress') || '{}')[`meta:${_id}`];
            setNativeProgress(progress && !progress.ended ? progress.progress : null);
        } catch (_) {
            setNativeProgress(null);
        }
    }, [_id]);

    React.useEffect(() => {
        refreshNativeProgress();
        window.addEventListener('nuvyro-playback-progress', refreshNativeProgress);
        return () => window.removeEventListener('nuvyro-playback-progress', refreshNativeProgress);
    }, [refreshNativeProgress]);

    const onDismissClick = React.useCallback((event) => {
        event.preventDefault();
        if (typeof _id === 'string') {
            core.transport.dispatch({
                action: 'Ctx',
                args: {
                    action: 'RewindLibraryItem',
                    args: _id
                }
            });
            core.transport.dispatch({
                action: 'Ctx',
                args: {
                    action: 'DismissNotificationItem',
                    args: _id
                }
            });
        }
    }, [_id]);

    return (
        <LibItem
            {...props}
            progress={nativeProgress === null ? props.progress : nativeProgress}
            _id={_id}
            posterChangeCursor={true}
            notifications={notifications}
            onDismissClick={onDismissClick}
        />
    );
};

ContinueWatchingItem.propTypes = {
    _id: PropTypes.string,
    notifications: PropTypes.object,
    progress: PropTypes.number,
    deepLinks: PropTypes.shape({
        metaDetailsVideos: PropTypes.string,
        metaDetailsStreams: PropTypes.string,
        player: PropTypes.string
    }),
};

module.exports = ContinueWatchingItem;
