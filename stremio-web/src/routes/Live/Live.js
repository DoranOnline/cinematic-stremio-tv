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
    const [board, loadBoardRows] = useBoard();
    const [filter, setFilter] = React.useState('all');
    const [favorites, setFavorites] = React.useState(() => readIds(FAVORITES_KEY));
    const [recent, setRecent] = React.useState(() => readIds(RECENT_KEY));
    const copy = React.useMemo(() => (i18n.resolvedLanguage || i18n.language || 'en').startsWith('he') ? ({
        title: 'שידורים חיים',
        subtitle: 'כל הערוצים שלך במקום אחד',
        all: 'הכול',
        favorites: 'מועדפים',
        recent: 'אחרונים',
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
        live: 'Live now',
        emptyTitle: 'No Live channels yet',
        emptyCopy: 'Install a legal add-on with a TV or Channel catalog and it will appear here automatically.',
        noMatches: 'There are no channels in this view yet'
    }), [i18n.resolvedLanguage, i18n.language]);

    React.useEffect(() => {
        loadBoardRows({ start: 0, end: 60 });
    }, [loadBoardRows]);

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
                    <div className={styles['eyebrow']}><span />{copy.live}</div>
                    <h1>{copy.title}</h1>
                    <p>{copy.subtitle}</p>
                </header>
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
                </nav>
                {
                    channels.length === 0 ?
                        <section className={styles['empty']}>
                            <Icon className={styles['empty-icon']} name={'tv'} />
                            <h2>{copy.emptyTitle}</h2>
                            <p>{copy.emptyCopy}</p>
                        </section>
                        : visibleChannels.length === 0 ?
                            <section className={styles['empty']}><p>{copy.noMatches}</p></section>
                            :
                            <section className={styles['channels']}>
                                {visibleChannels.map((channel) => (
                                    <article key={channel.id} className={styles['channel-card']}>
                                        <Button
                                            className={styles['channel-action']}
                                            href={getMetaDetailsHref(channel.deepLinks)}
                                            onClick={() => rememberRecent(channel.id)}
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
                            </section>
                }
            </div>
        </MainNavBars>
    );
};

const LiveFallback = () => <MainNavBars className={styles['live-container']} route={'live'} />;

module.exports = withCoreSuspender(Live, LiveFallback);
