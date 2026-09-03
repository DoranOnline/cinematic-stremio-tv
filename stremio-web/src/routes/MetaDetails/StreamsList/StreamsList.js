// Copyright (C) 2017-2023 Smart code 203358507

const React = require('react');
const { useNavigate } = require('react-router');
const { default: toPath } = require('stremio-router/toPath');
const PropTypes = require('prop-types');
const classnames = require('classnames');
const { useTranslation } = require('react-i18next');
const { default: Icon } = require('@stremio/stremio-icons/react');
const { Button, Image, MultiselectMenu } = require('stremio/components');
const { useCore } = require('stremio/core');
const Stream = require('./Stream');
const styles = require('./styles');
const { usePlatform, useProfile } = require('stremio/common');
const { default: SeasonEpisodePicker } = require('../EpisodePicker');
const { rankSources } = require('./sourceIntelligence');
const { hasPlayableStreams, isPlayableStream } = require('./streamAvailability');

const ALL_ADDONS_KEY = 'ALL';
const PREFERRED_ADDONS_KEY = 'cinematic.preferredAddons';

const loadPreferredAddons = () => {
    try {
        const value = JSON.parse(localStorage.getItem(PREFERRED_ADDONS_KEY) || '[]');
        return Array.isArray(value) ? value.filter((name) => typeof name === 'string') : [];
    } catch (_) {
        return [];
    }
};

const StreamsList = ({ className, video, metaId, type, onEpisodeSearch, ...props }) => {
    const { t, i18n } = useTranslation();
    const core = useCore();
    const platform = usePlatform();
    const profile = useProfile();
    const navigate = useNavigate();
    const streamsContainerRef = React.useRef(null);
    const [selectedAddon, setSelectedAddon] = React.useState(ALL_ADDONS_KEY);
    const [preferredAddons, setPreferredAddons] = React.useState(loadPreferredAddons);
    const rememberAddon = React.useCallback((addonName) => {
        setPreferredAddons((current) => {
            const next = [addonName, ...current.filter((name) => name !== addonName)].slice(0, 8);
            try {
                localStorage.setItem(PREFERRED_ADDONS_KEY, JSON.stringify(next));
            } catch (_) {
                // Preference persistence is optional; playback must continue.
            }
            return next;
        });
    }, []);
    const onAddonSelected = React.useCallback((value) => {
        streamsContainerRef.current.scrollTo({ top: 0, left: 0, behavior: platform.name === 'ios' ? 'smooth' : 'instant' });
        setSelectedAddon(value);
    }, [platform]);
    const showInstallAddonsButton = React.useMemo(() => {
        return !profile || profile.auth === null || profile.auth?.user?.isNewUser === true && !video?.upcoming;
    }, [profile, video]);
    const backButtonOnClick = React.useCallback(() => {
        if (video.deepLinks && typeof video.deepLinks.metaDetailsVideos === 'string') {
            const navigateTo = `${video.deepLinks.metaDetailsVideos}${
                typeof video.season === 'number'
                    ? `?${new URLSearchParams({ 'season': video.season })}`
                    : ''}`;
            navigate(toPath(navigateTo), { replace: true });
        } else {
            navigate(-1);
        }
    }, [video]);
    const countLoadingAddons = React.useMemo(() => {
        return props.streams.filter((stream) => stream.content.type === 'Loading').length;
    }, [props.streams]);
    const streamsByAddon = React.useMemo(() => {
        return props.streams
            .filter((streams) => streams.content.type === 'Ready' && hasPlayableStreams(streams.content.content))
            .reduce((streamsByAddon, streams) => {
                streamsByAddon[streams.addon.transportUrl] = {
                    addon: streams.addon,
                    streams: streams.content.content.filter(isPlayableStream).map((stream) => ({
                        ...stream,
                        onClick: () => {
                            rememberAddon(streams.addon.manifest.name);
                            core.transport.analytics({
                                event: 'StreamClicked',
                                args: {
                                    stream
                                }
                            });
                        },
                        addonName: streams.addon.manifest.name
                    }))
                };

                return streamsByAddon;
            }, {});
    }, [props.streams, rememberAddon]);
    const orderedAddonKeys = React.useMemo(() => {
        return Object.keys(streamsByAddon).sort((left, right) => {
            const leftName = streamsByAddon[left].addon.manifest.name;
            const rightName = streamsByAddon[right].addon.manifest.name;
            const leftIndex = preferredAddons.indexOf(leftName);
            const rightIndex = preferredAddons.indexOf(rightName);
            if (leftIndex === -1 && rightIndex === -1) return 0;
            if (leftIndex === -1) return 1;
            if (rightIndex === -1) return -1;
            return leftIndex - rightIndex;
        });
    }, [streamsByAddon, preferredAddons]);
    const filteredStreams = React.useMemo(() => {
        const candidates = selectedAddon === ALL_ADDONS_KEY ?
            orderedAddonKeys.map((key) => streamsByAddon[key].streams).flat(1)
            :
            streamsByAddon[selectedAddon] ?
                streamsByAddon[selectedAddon].streams
                :
                [];
        return rankSources(candidates, preferredAddons);
    }, [streamsByAddon, selectedAddon, orderedAddonKeys, preferredAddons]);
    const nativePlaybackSources = React.useMemo(() => filteredStreams
        .map((stream) => ({
            url: stream.deepLinks?.externalPlayer?.streaming,
            label: [stream.addonName, ...(stream.intelligence?.badges || []).slice(0, 3)].filter(Boolean).join(' • ')
        }))
        .filter((source) => typeof source.url === 'string' && /^https?:\/\//i.test(source.url))
        .slice(0, 12), [filteredStreams]);

    React.useEffect(() => {
        // Source choice is always explicit. Clear requests left by older
        // releases so opening an episode never auto-clicks a slow source.
        try {
            sessionStorage.removeItem('nuvyro.autoplayVideoId');
        } catch (_) {
            // Manual source selection remains usable without session storage.
        }
    }, [video?.id]);
    const selectableOptions = React.useMemo(() => {
        return {
            options: [
                {
                    value: ALL_ADDONS_KEY,
                    label: t('ALL_ADDONS'),
                    title: t('ALL_ADDONS')
                },
                ...orderedAddonKeys.map((transportUrl) => ({
                    value: transportUrl,
                    label: streamsByAddon[transportUrl].addon.manifest.name,
                    title: streamsByAddon[transportUrl].addon.manifest.name,
                }))
            ],
            value: selectedAddon,
            onSelect: onAddonSelected
        };
    }, [streamsByAddon, selectedAddon, orderedAddonKeys]);

    const handleEpisodePicker = React.useCallback((season, episode) => {
        onEpisodeSearch(season, episode);
    }, [onEpisodeSearch]);
    const watchCopy = React.useMemo(() => {
        const language = i18n.resolvedLanguage || i18n.language || 'en';
        return language.startsWith('he') ? {
            title: 'בחרו מקור והתחילו לצפות',
            ready: 'מוכנים עכשיו',
            checking: 'עדיין נבדקים'
        } : {
            title: 'Choose a source and start watching',
            ready: 'ready now',
            checking: 'still checking'
        };
    }, [i18n.resolvedLanguage, i18n.language]);

    return (
        <div className={classnames(className, styles['streams-list-container'])}>
            <div className={styles['select-choices-wrapper']}>
                {
                    video ?
                        <React.Fragment>
                            <Button className={classnames(styles['button-container'], styles['back-button-container'])} tabIndex={0} onClick={backButtonOnClick}>
                                <Icon className={styles['icon']} name={'chevron-back'} />
                            </Button>
                            <div className={styles['episode-title']}>
                                {typeof video.season === 'number' && typeof video.episode === 'number'
                                    ? `S${video.season}E${video.episode}${video.title ? ` ${video.title}` : ''}`
                                    : (video.title ?? '')}
                            </div>
                        </React.Fragment>
                        :
                        null
                }
                {
                    Object.keys(streamsByAddon).length > 1 ?
                        <MultiselectMenu
                            {...selectableOptions}
                            className={styles['select-input-container']}
                        />
                        :
                        null
                }
            </div>
            <div className={styles['watch-heading']}>
                <div className={styles['watch-kicker']} aria-hidden={'true'} />
                <div className={styles['watch-title']}>{watchCopy.title}</div>
                <div className={classnames(styles['watch-status'], { [styles['status-empty']]: filteredStreams.length === 0 })}>
                    <span>{filteredStreams.length} {watchCopy.ready}</span>
                    {
                        countLoadingAddons > 0 ?
                            <span className={styles['watch-checking']}>· {countLoadingAddons} {watchCopy.checking}</span>
                            : null
                    }
                </div>
            </div>
            {
                props.streams.length === 0 ?
                    <div className={styles['message-container']}>
                        {
                            type === 'series' ?
                                <SeasonEpisodePicker className={styles['search']} onSubmit={handleEpisodePicker} />
                                : null
                        }
                        <Image className={styles['image']} src={require('/assets/images/empty.png')} alt={' '} />
                        <div className={styles['label']}>{t('ERR_NO_ADDONS_FOR_STREAMS')}</div>
                    </div>
                    :
                    props.streams.every((streams) => streams.content.type === 'Err') ?
                        <div className={styles['message-container']}>
                            {
                                type === 'series' ?
                                    <SeasonEpisodePicker className={styles['search']} onSubmit={handleEpisodePicker} />
                                    : null
                            }
                            {
                                video?.upcoming ?
                                    <div className={styles['label']}>{t('UPCOMING')}...</div>
                                    : null
                            }
                            <Image className={styles['image']} src={require('/assets/images/empty.png')} alt={' '} />
                            <div className={styles['label']}>{t('NO_STREAM')}</div>
                            {
                                showInstallAddonsButton ?
                                    <Button className={styles['install-button-container']} title={t('ADDON_CATALOGUE_MORE')} href={'#/addons'}>
                                        <Icon className={styles['icon']} name={'addons'} />
                                        <div className={styles['label']}>{t('ADDON_CATALOGUE_MORE')}</div>
                                    </Button>
                                    :
                                    null
                            }
                        </div>
                        :
                        filteredStreams.length === 0 ?
                            <div className={styles['streams-container']}>
                                <Stream.Placeholder />
                                <Stream.Placeholder />
                            </div>
                            :
                            <React.Fragment>
                                <div className={styles['streams-container']} ref={streamsContainerRef}>
                                    {filteredStreams.map((stream, index) => (
                                        <Stream
                                            key={index}
                                            videoId={video?.id}
                                            metaId={metaId}
                                            videoReleased={video?.released}
                                            addonName={stream.addonName}
                                            badges={stream.intelligence.badges}
                                            bestMatch={stream.isBestMatch}
                                            name={stream.name}
                                            description={stream.description}
                                            thumbnail={stream.thumbnail}
                                            progress={stream.progress}
                                            deepLinks={stream.deepLinks}
                                            playbackSources={nativePlaybackSources}
                                            onClick={stream.onClick}
                                        />
                                    ))}
                                    {
                                        showInstallAddonsButton ?
                                            <Button className={styles['install-button-container']} title={t('ADDON_CATALOGUE_MORE')} href={'#/addons'}>
                                                <Icon className={styles['icon']} name={'addons'} />
                                                <div className={styles['label']}>{t('ADDON_CATALOGUE_MORE')}</div>
                                            </Button>
                                            :
                                            null
                                    }
                                </div>
                            </React.Fragment>
            }
        </div>
    );
};

StreamsList.propTypes = {
    className: PropTypes.string,
    streams: PropTypes.arrayOf(PropTypes.object).isRequired,
    video: PropTypes.object,
    metaId: PropTypes.string,
    type: PropTypes.string,
    onEpisodeSearch: PropTypes.func
};

module.exports = StreamsList;
