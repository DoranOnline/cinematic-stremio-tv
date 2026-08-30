package il.cinematic.stremio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PlayerLaunchGateTest {
    @Test
    public void acceptsOnlyOneLaunchUntilPlayerReturns() {
        final PlayerLaunchGate gate = new PlayerLaunchGate();

        assertTrue(gate.tryAcquire());
        for (int duplicate = 0; duplicate < 12; duplicate++) {
            assertFalse(gate.tryAcquire());
        }

        gate.release();
        assertTrue(gate.tryAcquire());
    }
}
