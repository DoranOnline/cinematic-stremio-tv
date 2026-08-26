// Copyright (C) 2017-2023 Smart code 203358507

const React = require('react');
const { useParams, useLocation, useNavigate } = require('react-router');
const { useTranslation } = require('react-i18next');
const classnames = require('classnames');
const { useCore } = require('stremio/core');
const { useContentGamepadNavigation } = require('stremio/services/GamepadNavigation');
const { withCoreSuspender } = require('stremio/common');
const { useNavigateWithOrigin } = require('stremio-router');
const { HorizontalNavBar, DelayedRenderer, Image, MetaPreview } = require('stremio/components');
const StreamsList = require('./StreamsList');
const VideosList = require('./VideosList');
const useMetaDetails = require('./useMetaDetails');
const useSeason = require('./useSeason');
const styles = require('./styles');

const GAMEPAD_HANDLER_ID = 'metadetails';

const MetaDetails = () => {
    const { type, id, videoId } = useParams();
    const location = useLocation();
    const navigate = useNavigate();
    const { getStoredOrigin } = useNavigateWithOrigin();
    const contentRef = React.useRef(null);
    const episodeFocusPathRef = React.useRef(null);
    const { t } = useTranslation();
    const core = useCore();
    const urlParams = React.useMemo(() => ({
        type,
        id,
        videoId
    }), [type, id, videoId]);
    const metaDetails = useMetaDetails(urlParams);
    const [season, setSeason] = useSeason(urlParams);
    const [metaPath, streamPath] = React.useMemo(() => {
        return metaDetails.selected !== null ?
            [metaDetails.selected.metaPath, metaDetails.selected.streamPath]
            :
            [null, null];
    }, [metaDetails.selected]);
    const video = React.useMemo(() => {
        return streamPath !== null && metaDetails.metaItem !== null && metaDetails.metaItem.content.type === 'Ready' ?
            metaDetails.metaItem.content.content.videos.reduce((result, video) => {
                if (video.id === streamPath.id) {
                    return video;
                }

                return result;
            }, null)
            :
            null;
    }, [metaDetails.metaItem, streamPath]);
    const addToLibrary = React.useCallback(() => {
        if (metaDetails.metaItem === null || metaDetails.metaItem.content.type !== 'Ready') {
            return;
        }

        core.transport.dispatch({
            action: 'Ctx',
            args: {
                action: 'AddToLibrary',
                args: metaDetails.metaItem.content.content
            }
        });
    }, [metaDetails]);
    const removeFromLibrary = React.useCallback(() => {
        if (metaDetails.metaItem === null || metaDetails.metaItem.content.type !== 'Ready') {
            return;
        }

        core.transport.dispatch({
            action: 'Ctx',
            args: {
                action: 'RemoveFromLibrary',
                args: metaDetails.metaItem.content.content.id
            }
        });
    }, [metaDetails]);
    const toggleWatched = React.useCallback(() => {
        if (metaDetails.metaItem === null || metaDetails.metaItem.content.type !== 'Ready') {
            return;
        }

        core.transport.dispatch({
            action: 'MetaDetails',
            args: {
                action: 'MarkAsWatched',
                args: !metaDetails.metaItem.content.content.watched
            }
        });
    }, [metaDetails]);
    const toggleNotifications = React.useCallback(() => {
        if (metaDetails.libraryItem) {
            core.transport.dispatch({
                action: 'Ctx',
                args: {
                    action: 'ToggleLibraryItemNotifications',
                    args: [metaDetails.libraryItem._id, !metaDetails.libraryItem.state.noNotif],
                }
            });
        }
    }, [metaDetails.libraryItem]);
    const seasonOnSelect = React.useCallback((event) => {
        setSeason(event.value);
    }, [setSeason]);
    const handleEpisodeSearch = React.useCallback((season, episode) => {
        const searchVideoHash = encodeURIComponent(`${urlParams.id}:${season}:${episode}`);
        const url = location.pathname;
        const searchVideoPath = (urlParams.videoId === undefined || urlParams.videoId === null || urlParams.videoId === '') ?
            url + (!url.endsWith('/') ? '/' : '') + searchVideoHash
            : url.replace(encodeURIComponent(urlParams.videoId), searchVideoHash);
        navigate(searchVideoPath, { replace: true });
    }, [urlParams, location]);

    React.useEffect(() => {
        const handleNativePlaybackEnded = (event) => {
            const videos = metaDetails.metaItem?.content?.type === 'Ready' ?
                metaDetails.metaItem.content.content.videos
                :
                null;
            if (!video || !Array.isArray(videos)) return;
            if (typeof event.detail?.videoId === 'string' && event.detail.videoId !== video.id) return;
            core.transport.dispatch({
                action: 'MetaDetails',
                args: {
                    action: 'MarkVideoAsWatched',
                    args: [{ id: video.id, released: video.released }, true]
                }
            });
            const orderedEpisodes = videos
                .filter((candidate) => typeof candidate.season === 'number' && typeof candidate.episode === 'number' && !candidate.upcoming)
                .sort((left, right) => left.season - right.season || left.episode - right.episode);
            const currentIndex = orderedEpisodes.findIndex((candidate) => candidate.id === video.id);
            const nextEpisode = currentIndex >= 0 ? orderedEpisodes[currentIndex + 1] : null;
            if (!nextEpisode || typeof nextEpisode.season !== 'number' || typeof nextEpisode.episode !== 'number') return;
            try {
                sessionStorage.setItem('nuvyro.autoplayVideoId', nextEpisode.id || 'next');
            } catch (_) {
                // Autoplay remains best-effort if storage is unavailable.
            }
            handleEpisodeSearch(nextEpisode.season, nextEpisode.episode);
        };
        window.addEventListener('nuvyro-playback-ended', handleNativePlaybackEnded);
        return () => window.removeEventListener('nuvyro-playback-ended', handleNativePlaybackEnded);
    }, [metaDetails.metaItem, video, handleEpisodeSearch]);

    React.useEffect(() => {
        if (streamPath !== null || metaDetails.metaItem?.content?.type !== 'Ready' ||
            episodeFocusPathRef.current === location.pathname) return;
        episodeFocusPathRef.current = location.pathname;
        const timer = window.setTimeout(() => {
            const firstEpisode = contentRef.current?.querySelector('[data-nuvyro-episode="true"]');
            if (firstEpisode instanceof HTMLElement) {
                firstEpisode.focus({ preventScroll: false });
                firstEpisode.scrollIntoView({ block: 'center', inline: 'nearest' });
            }
        }, 450);
        return () => window.clearTimeout(timer);
    }, [location.pathname, streamPath, metaDetails.metaItem]);

    const renderBackgroundImageFallback = React.useCallback(() => null, []);
    const renderBackground = React.useMemo(() => !!(
        metaPath &&
        metaDetails?.metaItem &&
        metaDetails.metaItem.content.type !== 'Loading' &&
        typeof metaDetails.metaItem.content.content?.background === 'string' &&
        metaDetails.metaItem.content.content.background.length > 0
    ), [metaPath, metaDetails]);
    const originPath = React.useMemo(() => getStoredOrigin(), [getStoredOrigin]);

    useContentGamepadNavigation(contentRef, GAMEPAD_HANDLER_ID);
    return (
        <div className={styles['metadetails-container']}>
            {
                renderBackground ?
                    <div className={styles['background-image-layer']}>
                        <Image
                            className={styles['background-image']}
                            src={metaDetails.metaItem.content.content.background}
                            renderFallback={renderBackgroundImageFallback}
                            alt={' '}
                        />
                    </div>
                    :
                    null
            }
            <HorizontalNavBar
                className={styles['nav-bar']}
                backButton={true}
                fullscreenButton={true}
                navMenu={true}
                originPath={originPath}
            />
            <div ref={contentRef} className={classnames(styles['metadetails-content'], {
                [styles['watch-options-active']]: streamPath !== null
            })}>
                {
                    metaPath === null ?
                        <DelayedRenderer delay={500}>
                            <div className={styles['meta-message-container']}>
                                <Image className={styles['image']} src={require('/assets/images/empty.png')} alt={' '} />
                                <div className={styles['message-label']}>{t('ERR_NO_META_SELECTED')}</div>
                            </div>
                        </DelayedRenderer>
                        :
                        metaDetails.metaItem === null ?
                            <div className={styles['meta-message-container']}>
                                <Image className={styles['image']} src={require('/assets/images/empty.png')} alt={' '} />
                                <div className={styles['message-label']}>{t('ERR_NO_ADDONS_FOR_META')}</div>
                            </div>
                            :
                            metaDetails.metaItem.content.type === 'Err' ?
                                <div className={styles['meta-message-container']}>
                                    <Image className={styles['image']} src={require('/assets/images/empty.png')} alt={' '} />
                                    <div className={styles['message-label']}>{t('ERR_NO_META_FOUND')}</div>
                                </div>
                                :
                                metaDetails.metaItem.content.type === 'Loading' ?
                                    <MetaPreview.Placeholder className={styles['meta-preview']} />
                                    :
                                    streamPath === null ? <React.Fragment>
                                        <MetaPreview
                                            className={classnames(styles['meta-preview'], 'animation-fade-in')}
                                            name={metaDetails.metaItem.content.content.name}
                                            logo={metaDetails.metaItem.content.content.logo}
                                            runtime={metaDetails.metaItem.content.content.runtime}
                                            releaseInfo={metaDetails.metaItem.content.content.releaseInfo}
                                            released={metaDetails.metaItem.content.content.released}
                                            description={
                                                video !== null && typeof video.overview === 'string' && video.overview.length > 0 ?
                                                    video.overview
                                                    :
                                                    metaDetails.metaItem.content.content.description
                                            }
                                            links={metaDetails.metaItem.content.content.links}
                                            trailerStreams={metaDetails.metaItem.content.content.trailerStreams}
                                            inLibrary={metaDetails.metaItem.content.content.inLibrary}
                                            toggleInLibrary={metaDetails.metaItem.content.content.inLibrary ? removeFromLibrary : addToLibrary}
                                            watched={metaDetails.metaItem.content.content.watched}
                                            toggleWatched={toggleWatched}
                                            metaId={metaDetails.metaItem.content.content.id}
                                            ratingInfo={metaDetails.ratingInfo}
                                        />
                                    </React.Fragment> : null
                }
                {streamPath === null ? <div className={styles['spacing']} /> : null}
                {
                    streamPath !== null ?
                        <StreamsList
                            className={styles['streams-list']}
                            streams={metaDetails.streams}
                            video={video}
                            metaId={metaDetails.metaItem?.content?.content?.id || id}
                            type={streamPath.type}
                            onEpisodeSearch={handleEpisodeSearch}
                        />
                        :
                        metaPath !== null ?
                            <VideosList
                                className={styles['videos-list']}
                                metaItem={metaDetails.metaItem}
                                libraryItem={metaDetails.libraryItem}
                                season={season}
                                selectedVideoId={metaDetails.libraryItem?.state?.video_id}
                                seasonOnSelect={seasonOnSelect}
                                toggleNotifications={toggleNotifications}
                            />
                            :
                            null
                }
            </div>
        </div>
    );
};

const MetaDetailsFallback = () => (
    <div className={styles['metadetails-container']}>
        <HorizontalNavBar
            className={styles['nav-bar']}
            backButton={true}
            fullscreenButton={true}
            navMenu={true}
        />
    </div>
);

module.exports = withCoreSuspender(MetaDetails, MetaDetailsFallback);
