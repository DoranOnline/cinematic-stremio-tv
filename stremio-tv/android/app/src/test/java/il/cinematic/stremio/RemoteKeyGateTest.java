package il.cinematic.stremio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RemoteKeyGateTest {
    @Test
    public void alwaysDispatchesFreshPresses() {
        final RemoteKeyGate gate = new RemoteKeyGate(110L);

        assertTrue(gate.shouldDispatch("left", 0, 1000L));
        assertTrue(gate.shouldDispatch("left", 0, 1001L));
    }

    @Test
    public void throttlesFastKeyRepeats() {
        final RemoteKeyGate gate = new RemoteKeyGate(110L);

        assertTrue(gate.shouldDispatch("left", 0, 1000L));
        assertFalse(gate.shouldDispatch("left", 1, 1030L));
        assertFalse(gate.shouldDispatch("left", 2, 1099L));
        assertTrue(gate.shouldDispatch("left", 3, 1110L));
    }

    @Test
    public void recoversAfterLongPause() {
        final RemoteKeyGate gate = new RemoteKeyGate(110L);

        assertTrue(gate.shouldDispatch("left", 0, 1000L));
        assertTrue(gate.shouldDispatch("left", 12, 5000L));
    }

    @Test
    public void directionChangesAreNeverDropped() {
        final RemoteKeyGate gate = new RemoteKeyGate(110L);

        assertTrue(gate.shouldDispatch("left", 0, 1000L));
        assertFalse(gate.shouldDispatch("left", 1, 1030L));
        assertTrue(gate.shouldDispatch("down", 1, 1040L));
        assertTrue(gate.shouldDispatch("right", 2, 1050L));
    }
}
