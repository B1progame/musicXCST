package de.coulees.B1progame.musicxcst.client.audio;

import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.network.AudioCacheWarmPayload;
import de.coulees.B1progame.musicxcst.network.AudioCachePrunePayload;
import de.coulees.B1progame.musicxcst.network.AudioChunkPayload;
import de.coulees.B1progame.musicxcst.network.AudioChunkRequestPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxStartPayload;
import de.coulees.B1progame.musicxcst.service.audio.AudioChunkDownloadManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Map;

public final class ClientAudioDownloadManager {
    private static final Map<String, PendingPlayback> PENDING = new HashMap<>();

    private ClientAudioDownloadManager() {
    }

    public static void handleStart(JukeboxStartPayload payload) {
        if (payload.sha256() == null || payload.sha256().isBlank() || payload.sizeBytes() <= 0L) {
            showError("Blueprint CD audio metadata is incomplete.");
            return;
        }

        if (ClientAudioCache.hasComplete(payload.musicId(), payload.sha256(), payload.sizeBytes())) {
            Path cached = ClientAudioCache.completePath(payload.musicId(), payload.sha256());
            CustomAudioEngine.play(payload, cached);
            return;
        }

        ClientAudioCache.deleteTemp(payload.musicId(), payload.sha256());
        PENDING.put(payload.musicId(), PendingPlayback.forPlayback(payload));
        requestChunk(payload.musicId(), 0L);
    }

    public static void handleCacheWarm(AudioCacheWarmPayload payload) {
        if (payload.sha256() == null || payload.sha256().isBlank() || payload.sizeBytes() <= 0L) {
            return;
        }
        if (ClientAudioCache.hasComplete(payload.musicId(), payload.sha256(), payload.sizeBytes())) {
            return;
        }
        if (PENDING.containsKey(payload.musicId())) {
            return;
        }

        ClientAudioCache.deleteTemp(payload.musicId(), payload.sha256());
        PENDING.put(payload.musicId(), PendingPlayback.forCacheWarm(payload));
        requestChunk(payload.musicId(), 0L);
    }

    public static void handleCachePrune(AudioCachePrunePayload payload) {
        Set<String> validCacheKeys = Arrays.stream(payload.validCacheKeys().split("\n"))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
        ClientAudioCache.pruneExcept(validCacheKeys);
    }

    public static void handleChunk(AudioChunkPayload payload) {
        PendingPlayback pending = PENDING.get(payload.musicId());
        if (pending == null) {
            return;
        }
        if (!pending.sha256().equalsIgnoreCase(payload.sha256())) {
            PENDING.remove(payload.musicId());
            ClientAudioCache.deleteTemp(payload.musicId(), pending.sha256());
            showError("Blueprint CD audio checksum changed during download.");
            return;
        }

        try {
            ClientAudioCache.writeChunk(payload.musicId(), payload.sha256(), payload.offset(), payload.data());
            long nextOffset = payload.offset() + payload.data().length;
            if (!payload.last() && nextOffset < payload.totalSize()) {
                pending.nextOffset = nextOffset;
                requestChunk(payload.musicId(), nextOffset);
                return;
            }

            Path cached = ClientAudioCache.finish(payload.musicId(), payload.sha256(), payload.totalSize());
            PENDING.remove(payload.musicId());
            if (pending.start != null) {
                CustomAudioEngine.play(pending.start, cached);
            } else {
                Musicxcst.LOGGER.debug("Cached Blueprint CD audio '{}'.", pending.displayName);
            }
        } catch (IOException exception) {
            PENDING.remove(payload.musicId());
            ClientAudioCache.deleteTemp(payload.musicId(), pending.sha256());
            Musicxcst.LOGGER.warn("Failed to cache Blueprint CD audio '{}': {}", pending.displayName, exception.getMessage());
            showError("Failed to download Blueprint CD audio: " + exception.getMessage());
        }
    }

    public static void clear() {
        PENDING.clear();
    }

    private static void requestChunk(String musicId, long offset) {
        int chunkBytes = offset == 0L
                ? AudioChunkDownloadManager.FIRST_CHUNK_BYTES
                : AudioChunkDownloadManager.DEFAULT_CHUNK_BYTES;
        ClientPlayNetworking.send(new AudioChunkRequestPayload(musicId, offset, chunkBytes));
    }

    private static void showError(String message) {
        Musicxcst.LOGGER.warn(message);
    }

    private static final class PendingPlayback {
        private final JukeboxStartPayload start;
        private final String sha256;
        private final String displayName;
        private long nextOffset;

        private PendingPlayback(JukeboxStartPayload start, String sha256, String displayName, long nextOffset) {
            this.start = start;
            this.sha256 = sha256;
            this.displayName = displayName;
            this.nextOffset = nextOffset;
        }

        private static PendingPlayback forPlayback(JukeboxStartPayload start) {
            return new PendingPlayback(start, start.sha256(), start.displayName(), 0L);
        }

        private static PendingPlayback forCacheWarm(AudioCacheWarmPayload payload) {
            return new PendingPlayback(null, payload.sha256(), payload.displayName(), 0L);
        }

        private String sha256() {
            return sha256;
        }
    }
}
