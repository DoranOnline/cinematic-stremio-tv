import React, { useCallback, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { usePlatform } from 'stremio/common';
import { Option, Section } from '../components';
import styles from './Info.less';

type Props = {
    streamingServer: StreamingServer,
};

const Info = ({ streamingServer }: Props) => {
    const { shell } = usePlatform();
    const { t } = useTranslation();
    const nativeUpdater = typeof window !== 'undefined' ? (window as any).CinematicAndroid : null;
    const [nativeVersion] = useState(() => {
        try {
            return nativeUpdater?.getAppVersion?.() || process.env.VERSION;
        } catch (_) {
            return process.env.VERSION;
        }
    });

    const onCheckForUpdate = useCallback(() => {
        nativeUpdater?.checkForAppUpdate?.();
    }, [nativeUpdater]);

    const settings = useMemo(() => (
        streamingServer?.settings?.type === 'Ready' ?
            streamingServer.settings.content as StreamingServerSettings : null
    ), [streamingServer?.settings]);

    return (
        <Section className={styles['info']}>
            <Option label={t('SETTINGS_APP_VERSION')}>
                <div className={styles['label']}>
                    {nativeVersion}
                </div>
            </Option>
            {
                typeof nativeUpdater?.checkForAppUpdate === 'function' &&
                    <div className={styles['update-card']}>
                        <div className={styles['update-copy']}>
                            <div className={styles['update-title']}>עדכוני אפליקציה</div>
                            <div className={styles['update-description']}>
                                בדוק, הורד ופתח את התקנת הגרסה החדשה ישירות מכאן.
                            </div>
                        </div>
                        <button
                            className={styles['update-button']}
                            onClick={onCheckForUpdate}
                            data-nav-row="settings-update"
                        >
                            בדוק עדכון
                        </button>
                    </div>
            }
            <Option label={t('SETTINGS_BUILD_VERSION')}>
                <div className={styles['label']}>
                    {process.env.COMMIT_HASH}
                </div>
            </Option>
            {
                settings?.serverVersion &&
                    <Option label={t('SETTINGS_SERVER_VERSION')}>
                        <div className={styles['label']}>
                            {settings.serverVersion}
                        </div>
                    </Option>
            }
            {
                typeof shell.state.version === 'string' &&
                    <Option label={t('SETTINGS_SHELL_VERSION')}>
                        <div className={styles['label']}>
                            {shell.state.version}
                        </div>
                    </Option>
            }
        </Section>
    );
};

export default Info;
