// Copyright (C) 2017-2023 Smart code 203358507

const React = require('react');
const classnames = require('classnames');
const debounce = require('lodash.debounce');
const useTranslate = require('stremio/common/useTranslate');
const { useStreamingServer, useNotifications, withCoreSuspender, getVisibleChildrenRange, useProfile } = require('stremio/common');
const { ContinueWatchingItem, EventModal, MainNavBars, MetaItem, MetaRow, Button, Image } = require('stremio/components');
const { default: getMetaDetailsHref } = require('stremio/common/getMetaDetailsHref');
const useBoard = require('./useBoard');
const useContinueWatchingPreview = require('./useContinueWatchingPreview');
const styles = require('./styles');
const { default: StreamingServerWarning } = require('./StreamingServerWarning');

const THRESHOLD = 5;

const Board = () => {
    const t = useTranslate();
    const streamingServer = useStreamingServer();
    const continueWatchingPreview = useContinueWatchingPreview();
    const [board, loadBoardRows, refreshBoard] = useBoard();
    const notifications = useNotifications();
    const profile = useProfile();
    const displayCatalogs = React.useMemo(() => board.catalogs.filter((catalog) => {
        const contentType = catalog?.request?.path?.type || catalog?.deepLinks?.discover?.type;
        return contentType !== 'channel';
    }), [board.catalogs]);
    const boardCatalogsOffset = continueWatchingPreview.items.length > 0 ? 1 : 0;
    const spotlightItems = React.useMemo(() => {
        const seen = new Set();
        return displayCatalogs
            .filter((catalog) => catalog.content?.type === 'Ready')
            .flatMap((catalog) => catalog.content.content)
            .filter((item) => {
                const key = item.id || item.name;
                if (!key || seen.has(key)) return false;
                seen.add(key);
                return Boolean(item.background || item.poster);
            })
            .slice(0, 4);
    }, [displayCatalogs]);
    const spotlight = spotlightItems[0] || null;
    const scrollContainerRef = React.useRef();
    const showStreamingServerWarning = React.useMemo(() => {
        return streamingServer.settings !== null && streamingServer.settings.type === 'Err' && (
            isNaN(profile.settings.streamingServerWarningDismissed.getTime()) ||
            profile.settings.streamingServerWarningDismissed.getTime() < Date.now());
    }, [profile.settings, streamingServer.settings]);
    const onVisibleRangeChange = React.useCallback(() => {
        const range = getVisibleChildrenRange(scrollContainerRef.current);
        if (range === null) {
            return;
        }

        const start = Math.max(0, range.start - boardCatalogsOffset - THRESHOLD);
        const end = range.end - boardCatalogsOffset + THRESHOLD;
        if (end < start) {
            return;
        }

        loadBoardRows({ start, end });
    }, [boardCatalogsOffset]);
    const onScroll = React.useCallback(debounce(onVisibleRangeChange, 250), [onVisibleRangeChange]);
    React.useLayoutEffect(() => {
        onVisibleRangeChange();
    }, [board.catalogs, onVisibleRangeChange]);
    React.useEffect(() => {
        const refreshWhenVisible = () => {
            if (document.visibilityState === 'visible') refreshBoard();
        };
        const refreshAfterNativePlayback = () => refreshBoard();
        const interval = window.setInterval(refreshWhenVisible, 5 * 60 * 1000);
        document.addEventListener('visibilitychange', refreshWhenVisible);
        window.addEventListener('nuvyro-library-refresh-request', refreshAfterNativePlayback);
        return () => {
            window.clearInterval(interval);
            document.removeEventListener('visibilitychange', refreshWhenVisible);
            window.removeEventListener('nuvyro-library-refresh-request', refreshAfterNativePlayback);
        };
    }, [refreshBoard]);
    return (
        <div className={styles['board-container']}>
            <EventModal />
            <MainNavBars className={styles['board-content-container']} route={'board'}>
                <div ref={scrollContainerRef} className={styles['board-content']} onScroll={onScroll}>
                    {
                        spotlight ?
                            <section className={styles['spotlight-grid']} aria-label={spotlight.name}>
                                <div className={styles['cinematic-spotlight']}>
                                    <Image className={styles['spotlight-art']} src={spotlight.background || spotlight.poster} alt={' '} />
                                    <div className={styles['spotlight-shade']} />
                                    <div className={styles['spotlight-copy']}>
                                        <div className={styles['spotlight-kicker']}>{t.string('FEATURED')}</div>
                                        <h1 className={styles['spotlight-title']}>{spotlight.name}</h1>
                                        <div className={styles['spotlight-type']}>{spotlight.type}</div>
                                        <Button className={styles['spotlight-action']} title={spotlight.name} href={getMetaDetailsHref(spotlight.deepLinks)}>
                                            <span>{t.string('LIBRARY_DETAILS')}</span>
                                        </Button>
                                    </div>
                                </div>
                                {spotlightItems.slice(1).map((item) => (
                                    <Button key={item.id || item.name} className={styles['spotlight-secondary']} title={item.name} href={getMetaDetailsHref(item.deepLinks)}>
                                        <Image className={styles['spotlight-secondary-art']} src={item.background || item.poster} alt={' '} />
                                        <span className={styles['spotlight-secondary-name']}>{item.name}</span>
                                    </Button>
                                ))}
                            </section>
                            :
                            <div className={styles['spotlight-placeholder']} />
                    }
                    {
                        continueWatchingPreview.items.length > 0 ?
                            <MetaRow
                                className={classnames(styles['board-row'], styles['continue-watching-row'], 'animation-fade-in')}
                                title={t.string('BOARD_CONTINUE_WATCHING')}
                                catalog={continueWatchingPreview}
                                itemComponent={ContinueWatchingItem}
                                notifications={notifications}
                            />
                            :
                            null
                    }
                    {displayCatalogs.map((catalog, index) => {
                        switch (catalog.content?.type) {
                            case 'Ready': {
                                return (
                                    <MetaRow
                                        key={index}
                                        className={classnames(styles['board-row'], styles[`board-row-${catalog.content.content[0].posterShape}`], 'animation-fade-in')}
                                        catalog={catalog}
                                        itemComponent={MetaItem}
                                    />
                                );
                            }
                            case 'Err': {
                                if (catalog.content.content !== 'EmptyContent') {
                                    return (
                                        <MetaRow
                                            key={index}
                                            className={classnames(styles['board-row'], 'animation-fade-in')}
                                            catalog={catalog}
                                            message={catalog.content.content}
                                        />
                                    );
                                }
                                return null;
                            }
                            default: {
                                return (
                                    <MetaRow.Placeholder
                                        key={index}
                                        className={classnames(styles['board-row'], styles['board-row-poster'], 'animation-fade-in')}
                                        catalog={catalog}
                                        title={t.catalogTitle(catalog)}
                                    />
                                );
                            }
                        }
                    })}
                </div>
            </MainNavBars>
            {
                showStreamingServerWarning ?
                    <StreamingServerWarning className={styles['board-warning-container']} />
                    :
                    null
            }
        </div>
    );
};

const BoardFallback = () => (
    <div className={styles['board-container']}>
        <MainNavBars className={styles['board-content-container']} route={'board'} />
    </div>
);

module.exports = withCoreSuspender(Board, BoardFallback);
