package il.cinematic.stremio;

import java.util.concurrent.atomic.AtomicBoolean;

final class PlayerLaunchGate {
    private final AtomicBoolean launchInFlight = new AtomicBoolean(false);

    boolean tryAcquire() {
        return launchInFlight.compareAndSet(false, true);
    }

    void release() {
        launchInFlight.set(false);
    }

    boolean isLocked() {
        return launchInFlight.get();
    }
}
