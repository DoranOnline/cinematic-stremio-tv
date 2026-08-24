package il.cinematic.stremio;

final class RemoteKeyGate {
    private final long repeatIntervalMs;
    private long lastDispatchAtMs = Long.MIN_VALUE;
    private String lastDirection;

    RemoteKeyGate(long repeatIntervalMs) {
        this.repeatIntervalMs = repeatIntervalMs;
    }

    boolean shouldDispatch(String direction, int repeatCount, long nowMs) {
        final boolean directionChanged = lastDirection == null || !lastDirection.equals(direction);
        if (repeatCount == 0 || directionChanged || lastDispatchAtMs == Long.MIN_VALUE || nowMs - lastDispatchAtMs >= repeatIntervalMs) {
            lastDirection = direction;
            lastDispatchAtMs = nowMs;
            return true;
        }
        return false;
    }
}
