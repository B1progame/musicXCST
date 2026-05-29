package de.coulees.B1progame.musicxcst.data;

public final class StorageStats {
    public long bytes;
    public int fileCount;
    public int activeCount;
    public int deletedCount;
    public int missingCount;
    public int invalidCount;
    public long quotaBytes;

    public String describe() {
        return fileCount + " files, " + formatBytes(bytes) + " used";
    }

    public String describeDetailed() {
        return describe()
                + ", active=" + activeCount
                + ", deleted=" + deletedCount
                + ", missing=" + missingCount
                + ", invalid=" + invalidCount
                + ", quota=" + formatBytes(quotaBytes);
    }

    public static String formatBytes(long bytes) {
        double mb = bytes / 1024.0 / 1024.0;
        double gb = mb / 1024.0;
        return bytes + " bytes (" + String.format("%.2f MB / %.3f GB", mb, gb) + ")";
    }
}
