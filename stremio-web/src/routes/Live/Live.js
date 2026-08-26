// Copyright (C) 2017-2026 Smart code 203358507

const React = require('react');
const classnames = require('classnames');
const { useTranslation } = require('react-i18next');
const { Button, Image, MainNavBars } = require('stremio/components');
const { default: Icon } = require('@stremio/stremio-icons/react');
const { withCoreSuspender } = require('stremio/common');
const { default: getMetaDetailsHref } = require('stremio/common/getMetaDetailsHref');
const useBoard = require('../Board/useBoard');
const styles = require('./styles');

const FAVORITES_KEY = 'nuvyro.liveFavorites';
const RECENT_KEY = 'nuvyro.liveRecent';

const readIds = (key) => {
    try {
        const value = JSON.parse(localStorage.getItem(key) || '[]');
        return Array.isArray(value) ? value.filter((id) => typeof id === 'string') : [];
    } catch (_) {
        return [];
    }
};

const writeIds = (key, value) => {
    try {
        localStorage.setItem(key, JSON.stringify(value));
    } catch (_) {
        // Live navigation remains functional without persistence.
    }
};

const Live = () => {
    const { i18n } = useTranslation();
    const [board, loadBoardRows, refreshBoard] = useBoard();
    const [filter, setFilter] = React.useState('all');
    const [selectedId, setSelectedId] = React.useState(null);
    const [favorites, setFavorites] = React.useState(() => readIds(FAVORITES_KEY));
    const [recent, setRecent] = React.useState(() => readIds(RECENT_KEY));
    const copy = React.useMemo(() => (i18n.resolvedLanguage || i18n.language || 'en').startsWith('he') ? ({
        title: 'שידורים חיים',
        subtitle: 'כל הערוצים שלך במקום אחד',
        all: 'הכול',
        favorites: 'מועדפים',
        recent: 'אחרונים',
        refresh: 'רענון',
        watch: 'פתח ערוץ',
        connecting: 'מרעננים את רשימת הערוצים…',
        live: 'בשידור חי',
        emptyTitle: 'אין עדיין ערוצי Live',
        emptyCopy: 'התקן תוסף חוקי שתומך בקטלוג TV או Channel והוא יופיע כאן אוטומטית.',
        noMatches: 'אין ערוצים בתצוגה הזאת עדיין'
    }) : ({
        title: 'Live TV',
        subtitle: 'All your channels in one place',
        all: 'All',
        favorites: 'Favorites',
        recent: 'Recent',
        refresh: 'Refresh',
        watch: 'Open channel',
        connecting: 'Refreshing your channel list…',
        live: 'Live now',
        emptyTitle: 'No Live channels yet',
        emptyCopy: 'Install a legal add-on with a TV or Channel catalog and it will appear here automatically.',
        noMatches: 'There are no channels in this view yet'
    }), [i18n.resolvedLanguage, i18n.language]);

    React.useEffect(() => {
        loadBoardRows({ start: 0, end: 60 });
    }, [loadBoardRows]);

    React.useEffect(() => {
        const refreshWhenVisible = () => {
            if (document.visibilityState === 'visible') refreshBoard();
        };
        const interval = window.setInterval(refreshWhenVisible, 5 * 60 * 1000);
        document.addEventListener('visibilitychange', refreshWhenVisible);
        return () => {
            window.clearInterval(interval);
            document.removeEventListener('visibilitychange', refreshWhenVisible);
        };
    }, [refreshBoard]);

    const channels = React.useMemo(() => {
        const seen = new Set();
        return (board.catalogs || [])
            .filter((catalog) => catalog.content?.type === 'Ready')
            .flatMap((catalog) => {
                const catalogType = String(catalog.type || '').toLowerCase();
                return catalog.content.content
                    .filter((item) => ['tv', 'channel'].includes(String(item.type || catalogType).toLowerCase()))
                    .map((item) => ({ ...item, catalogName: catalog.name || catalog.label || '' }));
            })
            .filter((item) => {
                const key = item.id || item.name;
                if (!key || seen.has(key)) return false;
                seen.add(key);
                return true;
            });
    }, [board.catalogs]);

    const visibleChannels = React.useMemo(() => {
        if (filter === 'favorites') return channels.filter((item) => favorites.includes(item.id));
        if (filter === 'recent') {
            return recent.map((id) => channels.find((item) => item.id === id)).filter(Boolean);
        }
        return channels;
    }, [channels, favorites, recent, filter]);
    const selectedChannel = React.useMemo(() => visibleChannels.find((item) => item.id === selectedId) || visibleChannels[0] || null, [visibleChannels, selectedId]);
    const loading = React.useMemo(() => (board.catalogs || []).some((catalog) => catalog.content?.type === 'Loading'), [board.catalogs]);

    const rememberRecent = React.useCallback((id) => {
        if (typeof id !== 'string') return;
        setRecent((current) => {
            const next = [id, ...current.filter((value) => value !== id)].slice(0, 20);
            writeIds(RECENT_KEY, next);
            return next;
        });
    }, []);

    const toggleFavorite = React.useCallback((event, id) => {
        event.preventDefault();
        event.stopPropagation();
        setFavorites((current) => {
            const next = current.includes(id) ? current.filter((value) => value !== id) : [id, ...current];
            writeIds(FAVORITES_KEY, next);
            return next;
        });
    }, []);

    return (
        <MainNavBars className={styles['live-container']} route={'live'}>
            <div className={styles['live-content']}>
                <header className={styles['hero']}>
                    <div><div className={styles['eyebrow']}><span />{copy.live}</div><h1>{copy.title}</h1><p>{copy.subtitle}</p></div>
                    <nav className={styles['filters']}>
                        {[
                            ['all', copy.all],
                            ['favorites', copy.favorites],
                            ['recent', copy.recent]
                        ].map(([value, label]) => (
                            <Button key={value} className={classnames(styles['filter'], { [styles['selected']]: filter === value })} onClick={() => setFilter(value)}>
                                {label}
                            </Button>
                        ))}
                        <Button className={styles['refresh']} title={copy.refresh} onClick={refreshBoard}>
                            <Icon name={'cloud-sync'} /><span>{copy.refresh}</span>
                        </Button>
                    </nav>
                </header>
                {
                    channels.length === 0 && loading ?
                        <section className={styles['loading']}><div className={styles['spinner']} /><strong>{copy.connecting}</strong></section>
                        : channels.length === 0 ?
                            <section className={styles['empty']}>
                                <Icon className={styles['empty-icon']} name={'tv'} />
                                <h2>{copy.emptyTitle}</h2>
                                <p>{copy.emptyCopy}</p>
                            </section>
                            : visibleChannels.length === 0 ?
                                <section className={styles['empty']}><p>{copy.noMatches}</p></section>
                                :
                                <section className={styles['live-workspace']}>
                                    <div className={styles['preview']}>
                                        <Image className={styles['preview-art']} src={selectedChannel.background || selectedChannel.poster} alt={' '} />
                                        <div className={styles['preview-shade']} />
                                        <div className={styles['preview-copy']}>
                                            <span className={styles['live-badge']}>{copy.live}</span>
                                            <h2>{selectedChannel.name}</h2>
                                            {selectedChannel.catalogName ? <p>{selectedChannel.catalogName}</p> : null}
                                            <Button className={styles['watch']} href={getMetaDetailsHref(selectedChannel.deepLinks)} onClick={() => rememberRecent(selectedChannel.id)}>
                                                <Icon name={'play'} /><span>{copy.watch}</span>
                                            </Button>
                                        </div>
                                        {loading ? <div className={styles['updating']}><div className={styles['spinner']} />{copy.connecting}</div> : null}
                                    </div>
                                    <div className={styles['channels']}>
                                        {visibleChannels.map((channel) => (
                                            <article key={channel.id} className={classnames(styles['channel-card'], { [styles['selected-channel']]: selectedChannel.id === channel.id })}>
                                                <Button
                                                    className={styles['channel-action']}
                                                    href={getMetaDetailsHref(channel.deepLinks)}
                                                    onClick={() => rememberRecent(channel.id)}
                                                    onFocus={() => setSelectedId(channel.id)}
                                                >
                                                    <Image className={styles['art']} src={channel.background || channel.poster} alt={' '} />
                                                    <div className={styles['shade']} />
                                                    <div className={styles['channel-copy']}>
                                                        <span className={styles['live-badge']}>{copy.live}</span>
                                                        <strong>{channel.name}</strong>
                                                        {channel.catalogName ? <small>{channel.catalogName}</small> : null}
                                                    </div>
                                                    <Icon className={styles['play']} name={'play'} />
                                                </Button>
                                                <Button
                                                    className={classnames(styles['favorite'], { [styles['active']]: favorites.includes(channel.id) })}
                                                    onClick={(event) => toggleFavorite(event, channel.id)}
                                                >
                                                    <Icon name={favorites.includes(channel.id) ? 'heart' : 'heart-outline'} />
                                                </Button>
                                            </article>
                                        ))}
                                    </div>
                                </section>
                }
            </div>
        </MainNavBars>
    );
};

const LiveFallback = () => <MainNavBars className={styles['live-container']} route={'live'} />;

module.exports = withCoreSuspender(Live, LiveFallback);
