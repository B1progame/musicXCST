package de.coulees.B1progame.musicxcst.client.audio;

import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.network.AudioChunkPayload;
import de.coulees.B1progame.musicxcst.network.AudioChunkRequestPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxStartPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class ClientAudioDownloadManager {
    private static final int CHUNK_SIZE = 128 * 1024;
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
        PENDING.put(payload.musicId(), new PendingPlayback(payload, 0L));
        requestChunk(payload.musicId(), 0L);
    }

    public static void handleChunk(AudioChunkPayload payload) {
        PendingPlayback pending = PENDING.get(payload.musicId());
        if (pending == null) {
            return;
        }
        if (!pending.start.sha256().equalsIgnoreCase(payload.sha256())) {
            PENDING.remove(payload.musicId());
            ClientAudioCache.deleteTemp(payload.musicId(), pending.start.sha256());
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
            CustomAudioEngine.play(pending.start, cached);
        } catch (IOException exception) {
            PENDING.remove(payload.musicId());
            ClientAudioCache.deleteTemp(payload.musicId(), pending.start.sha256());
            Musicxcst.LOGGER.warn("Failed to cache Blueprint CD audio '{}': {}", pending.start.displayName(), exception.getMessage());
            showError("Failed to download Blueprint CD audio: " + exception.getMessage());
        }
    }

    public static void clear() {
        PENDING.clear();
    }

    private static void requestChunk(String musicId, long offset) {
        ClientPlayNetworking.send(new AudioChunkRequestPayload(musicId, offset, CHUNK_SIZE));
    }

    private static void showError(String message) {
        Musicxcst.LOGGER.warn(message);
    }

    private static final class PendingPlayback {
        private final JukeboxStartPayload start;
        private long nextOffset;

        private PendingPlayback(JukeboxStartPayload start, long nextOffset) {
            this.start = start;
            this.nextOffset = nextOffset;
        }
    }
}
