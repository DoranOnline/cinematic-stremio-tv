// Copyright (C) 2017-2023 Smart code 203358507

import React from 'react';
import { useTranslation } from 'react-i18next';
import Image from 'stremio/components/Image';
import Button from 'stremio/components/Button';
import styles from './styles.less';

type Props = {
    message: string,
    onRetry: () => void,
};

const Error = ({ message, onRetry }: Props) => {
    const { t, i18n } = useTranslation();
    const hebrew = (i18n.resolvedLanguage || i18n.language || 'en').startsWith('he');
    const isNetworkError = /failed to fetch|network|load failed/i.test(message || '');

    const clearData = React.useCallback(() => {
        window.localStorage.clear();
        window.location.reload();
    }, []);

    return (
        <div className={styles['error-container']}>
            <Image
                className={styles['error-image']}
                src={require('/assets/images/empty.png')}
                alt={' '}
            />
            <div className={styles['info']}>
                <div className={styles['title']}>
                    {isNetworkError ? (hebrew ? 'לא הצלחנו להתחבר כרגע' : 'We could not connect right now') : t('GENERIC_ERROR_MESSAGE')}
                </div>
                <div className={styles['message']}>
                    {isNetworkError ?
                        (hebrew ? 'בדוק את החיבור ונסה שוב. החשבון והתוספים שלך לא נמחקו.' : 'Check the connection and try again. Your account and add-ons were not removed.')
                        : message}
                </div>
            </div>
            <div className={styles['buttons-container']}>
                <Button className={styles['button-container']} title={hebrew ? 'נסה שוב' : 'Try again'} onClick={onRetry}>
                    <div className={styles['label']}>
                        {hebrew ? 'נסה שוב' : 'Try again'}
                    </div>
                </Button>
                {!isNetworkError ?
                    <Button className={styles['button-container']} title={t('CLEAR_DATA')} onClick={clearData}>
                        <div className={styles['label']}>{t('CLEAR_DATA')}</div>
                    </Button>
                    : null}
            </div>
        </div>
    );
};

export default Error;
