// Copyright (C) 2017-2023 Smart code 203358507

const React = require('react');
const { useCore } = require('stremio/core');
const { useModelState } = require('stremio/common');

const useBoard = () => {
    const core = useCore();
    const [refreshVersion, setRefreshVersion] = React.useState(0);
    const action = React.useMemo(() => ({
        action: 'Load',
        args: {
            model: 'CatalogsWithExtra',
            args: { extra: [] }
        }
    }), [refreshVersion]);
    const loadRange = React.useCallback((range) => {
        core.transport.dispatch({
            action: 'CatalogsWithExtra',
            args: {
                action: 'LoadRange',
                args: range
            }
        }, 'board');
    }, []);
    const refresh = React.useCallback(() => setRefreshVersion((value) => value + 1), []);
    const board = useModelState({ model: 'board', action });
    return [board, loadRange, refresh];
};

module.exports = useBoard;
