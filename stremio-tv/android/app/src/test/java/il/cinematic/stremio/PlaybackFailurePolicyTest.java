package il.cinematic.stremio;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackFailurePolicyTest {
    @Test
    public void decoderAndUnsupportedContainerFailuresSwitchEngine() {
        assertTrue(PlaybackFailurePolicy.shouldSwitchEngine("ERROR_CODE_DECODER_INIT_FAILED"));
        assertTrue(PlaybackFailurePolicy.shouldSwitchEngine("ERROR_CODE_DECODING_FORMAT_UNSUPPORTED"));
        assertTrue(PlaybackFailurePolicy.shouldSwitchEngine("ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED"));
    }

    @Test
    public void networkAndHttpFailuresDoNotSwitchEngineBlindly() {
        assertFalse(PlaybackFailurePolicy.shouldSwitchEngine("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"));
        assertFalse(PlaybackFailurePolicy.shouldSwitchEngine("ERROR_CODE_IO_BAD_HTTP_STATUS"));
        assertFalse(PlaybackFailurePolicy.shouldSwitchEngine(null));
    }
}

