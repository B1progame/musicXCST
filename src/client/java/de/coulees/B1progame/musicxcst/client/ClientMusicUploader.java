package de.coulees.B1progame.musicxcst.client;

import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.network.ClientMusicUploadChunkPayload;
import de.coulees.B1progame.musicxcst.network.ClientMusicUploadStartPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class ClientMusicUploader {
    private static final int CHUNK_BYTES = 64 * 1024;

    private ClientMusicUploader() {
    }

    public static void register() {
    }

    public static int startUpload(Minecraft client, String name, String pathText) {
        Path path = Path.of(stripWrappingQuotes(pathText.trim())).normalize();
        if (!Files.isRegularFile(path)) {
            sendClientMessage(client, Component.literal("Music file was not found on this computer."));
            return 0;
        }

        Thread uploadThread = new Thread(() -> uploadFile(client, name, path), "musicxcst-client-upload");
        uploadThread.setDaemon(true);
        uploadThread.start();
        sendClientMessage(client, Component.literal("Started music upload. Keep the server connection open."));
        return 1;
    }

    private static void uploadFile(Minecraft client, String name, Path path) {
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        try {
            long sizeBytes = Files.size(path);
            long started = System.currentTimeMillis();
            ClientPlayNetworking.send(new ClientMusicUploadStartPayload(uploadId, name, path.getFileName().toString(), sizeBytes));
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[CHUNK_BYTES];
                long offset = 0L;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    byte[] data = read == buffer.length ? buffer.clone() : java.util.Arrays.copyOf(buffer, read);
                    offset += read;
                    ClientPlayNetworking.send(new ClientMusicUploadChunkPayload(uploadId, offset - read, data, offset >= sizeBytes));
                    showProgress(client, offset, sizeBytes, started);
                    Thread.sleep(250L);
                }
            }
            sendClientMessage(client, Component.literal("Music upload finished. The server is processing it."));
        } catch (IOException exception) {
            Musicxcst.LOGGER.warn("Failed to upload music file '{}': {}", path.getFileName(), exception.getMessage());
            sendClientMessage(client, Component.literal("Failed to read music file: " + exception.getMessage()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            sendClientMessage(client, Component.literal("Music upload was interrupted."));
        }
    }

    private static String stripWrappingQuotes(String input) {
        if (input.length() >= 2 && input.startsWith("\"") && input.endsWith("\"")) {
            return input.substring(1, input.length() - 1);
        }
        return input;
    }

    private static void showProgress(Minecraft client, long uploadedBytes, long sizeBytes, long startedAtMillis) {
        double percent = sizeBytes <= 0L ? 100.0D : (uploadedBytes * 100.0D / sizeBytes);
        long elapsedMillis = Math.max(1L, System.currentTimeMillis() - startedAtMillis);
        double bytesPerMillis = uploadedBytes / (double) elapsedMillis;
        long remainingMillis = bytesPerMillis <= 0.0D ? 0L : (long) ((sizeBytes - uploadedBytes) / bytesPerMillis);
        String message = "Upload " + String.format(java.util.Locale.ROOT, "%.1f", percent) + "%, ETA " + formatEta(remainingMillis);
        client.execute(() -> client.gui.setOverlayMessage(Component.literal(message), false));
    }

    private static void sendClientMessage(Minecraft client, Component message) {
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(message);
            }
        });
    }

    private static String formatEta(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        return minutes > 0L ? minutes + "m " + remainingSeconds + "s" : remainingSeconds + "s";
    }
}
