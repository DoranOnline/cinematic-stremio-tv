package il.cinematic.stremio;

final class VersionComparator {
    private VersionComparator() {}

    static boolean isNewer(String candidate, String current) {
        final int[] left = parse(candidate);
        final int[] right = parse(current);
        for (int index = 0; index < Math.max(left.length, right.length); index++) {
            final int leftPart = index < left.length ? left[index] : 0;
            final int rightPart = index < right.length ? right[index] : 0;
            if (leftPart != rightPart) {
                return leftPart > rightPart;
            }
        }
        return false;
    }

    private static int[] parse(String version) {
        final String normalized = version == null ? "" : version.replaceFirst("^[vV]", "");
        final String numeric = normalized.split("[-+]", 2)[0];
        final String[] parts = numeric.split("\\.");
        final int[] result = new int[parts.length];
        for (int index = 0; index < parts.length; index++) {
            try {
                result[index] = Integer.parseInt(parts[index].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
                result[index] = 0;
            }
        }
        return result;
    }
}
