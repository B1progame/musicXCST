package de.coulees.B1progame.musicxcst.service.audio;

public final class AudioChunkDownloadManager {
    public static final int DEFAULT_CHUNK_BYTES = 128 * 1024;

    public int clampRequestedBytes(int requestedBytes) {
        return Math.max(1, Math.min(requestedBytes, DEFAULT_CHUNK_BYTES));
    }
}
