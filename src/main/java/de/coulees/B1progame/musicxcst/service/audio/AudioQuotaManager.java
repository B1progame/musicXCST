package de.coulees.B1progame.musicxcst.service.audio;

public final class AudioQuotaManager {
    public void requireWithinQuota(long currentBytes, long addedBytes, long quotaBytes, String message) {
        if (currentBytes + addedBytes > quotaBytes) {
            throw new IllegalArgumentException(message);
        }
    }
}
