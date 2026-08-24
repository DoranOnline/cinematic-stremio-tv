package il.cinematic.stremio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VersionComparatorTest {
    @Test
    public void comparesReleaseVersions() {
        assertTrue(VersionComparator.isNewer("v0.8", "0.7"));
        assertTrue(VersionComparator.isNewer("1.0.1", "1.0"));
        assertFalse(VersionComparator.isNewer("v0.7", "0.7-auto-update"));
        assertFalse(VersionComparator.isNewer("0.6", "0.7"));
    }
}
