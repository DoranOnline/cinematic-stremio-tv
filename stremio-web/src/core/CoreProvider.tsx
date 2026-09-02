import React, { useEffect, useRef, useState } from 'react';
import CoreContext from './CoreContext';
import createTransport from './createTransport';
import Error from './Error';

const transport = createTransport();
const NATIVE_SERVER_WAIT_MS = 12_000;

const waitForNativeStreamingServer = async () => {
    const nativeBridge = (window as any).CinematicAndroid;
    if (typeof nativeBridge?.isStreamingServerReady !== 'function') return;

    const deadline = Date.now() + NATIVE_SERVER_WAIT_MS;
    while (Date.now() < deadline) {
        if (nativeBridge.isStreamingServerReady()) return;
        await new Promise((resolve) => window.setTimeout(resolve, 200));
    }
    throw new Error('The local streaming server did not become ready in time');
};

type Props = {
    appInfo: object,
    children: React.ReactNode,
};

const Core = (props: Props) => {
    const initialized = useRef(false);
    const [attempt, setAttempt] = useState(0);
    const [ready, setReady] = useState(false);
    const [error, setError] = useState<Error | null>();

    const stateListeners = useRef<CoreStateListener[]>([]);
    const eventListeners = useRef<CoreEventListener[]>([]);
    const errorListeners = useRef<CoreErrorListener[]>([]);

    const on = (name: CoreListenerType, listener: CoreListener) => {
        if (name === 'state') stateListeners.current = [...stateListeners.current, listener as CoreStateListener];
        if (name === 'event') eventListeners.current = [...eventListeners.current, listener as CoreEventListener];
        if (name === 'error') errorListeners.current = [...errorListeners.current, listener as CoreErrorListener];
    };

    const off = (name: CoreListenerType, listener: CoreListener) => {
        if (name === 'state') stateListeners.current = stateListeners.current.filter((l) => l !== listener);
        if (name === 'event') eventListeners.current = eventListeners.current.filter((l) => l !== listener);
        if (name === 'error') errorListeners.current = errorListeners.current.filter((l) => l !== listener);
    };

    useEffect(() => {
        if (initialized.current && attempt === 0) return;
        initialized.current = true;
        let cancelled = false;

        const onCoreEvent = ({ name, args }: NewStateEvent | CoreEventEvent) => {
            switch (name) {
                case 'NewState':
                    stateListeners.current.forEach((listener) => listener(args));
                    break;

                case 'CoreEvent': {
                    switch (args.event) {
                        case 'Error': {
                            const { source, error } = args.args;
                            errorListeners.current.forEach((listener) => listener(
                                source,
                                error,
                            ));
                            break;
                        }
                        default:
                            eventListeners.current.forEach((listener) => listener(
                                args.event,
                                args.args,
                            ));
                            break;
                    }
                    break;
                }

                default:
                    break;
            }
        };

        waitForNativeStreamingServer()
            .then(() => transport.init(props.appInfo))
            .then(() => {
                if (cancelled) return;
                window.core = transport;
                window.onCoreEvent = onCoreEvent;
                setReady(true);
                setError(null);
            })
            .catch((e: Error) => {
                if (cancelled) return;
                console.error('Failed to initialize core:', e);
                setReady(false);
                setError(e);
            });

        return () => {
            cancelled = true;
            stateListeners.current = [];
            eventListeners.current = [];
            errorListeners.current = [];
        };
    }, [attempt]);

    const retry = () => {
        setError(null);
        setReady(false);
        setAttempt((current) => current + 1);
    };

    return (
        <CoreContext.Provider value={{ transport, on, off }}>
            { error && !ready && <Error message={error.message} onRetry={retry} /> }
            { ready && !error && props.children }
        </CoreContext.Provider>
    );
};

export default Core;
