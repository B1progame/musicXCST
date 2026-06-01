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
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

public final class ClientMusicUploader {
    private static final int CHUNK_BYTES = 64 * 1024;

    private ClientMusicUploader() {
    }

    public static void register() {
    }

    public static int startUpload(Minecraft client, String name, String pathText) {
        return startUpload(client, name, pathText, null);
    }

    public static int startUpload(Minecraft client, String name, String pathText, Consumer<String> afterUpload) {
        return startUpload(client, name, pathText, afterUpload, null);
    }

    public static int startUpload(Minecraft client, String name, String pathText, Consumer<String> afterUpload, Consumer<String> progress) {
        Path path = Path.of(stripWrappingQuotes(pathText.trim())).normalize();
        if (!Files.isRegularFile(path)) {
            sendClientMessage(client, Component.literal("Music file was not found on this computer."));
            return 0;
        }

        Thread uploadThread = new Thread(() -> uploadFile(client, name, path, afterUpload, progress), "musicxcst-client-upload");
        uploadThread.setDaemon(true);
        uploadThread.start();
        sendClientMessage(client, Component.literal("Started music upload. Keep the server connection open."));
        return 1;
    }

    private static void uploadFile(Minecraft client, String name, Path path, Consumer<String> afterUpload, Consumer<String> progress) {
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
                    showProgress(client, offset, sizeBytes, started, progress);
                    Thread.sleep(250L);
                }
            }
            sendClientMessage(client, Component.literal("Music upload finished. The server is processing it."));
            if (progress != null) {
                client.execute(() -> progress.accept("Processing on server..."));
            }
            if (afterUpload != null) {
                String uploadedFileName = uploadedFileName(name, path);
                client.execute(() -> afterUpload.accept(uploadedFileName));
            }
        } catch (IOException exception) {
            Musicxcst.LOGGER.warn("Failed to upload music file '{}': {}", path.getFileName(), exception.getMessage());
            sendClientMessage(client, Component.literal("Failed to read music file: " + exception.getMessage()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            sendClientMessage(client, Component.literal("Music upload was interrupted."));
        }
    }

    public static String uploadedFileName(String uploadName, Path sourcePath) {
        String sourceFileName = sourcePath.getFileName().toString();
        int dot = sourceFileName.lastIndexOf('.');
        String extension = dot >= 0 && dot < sourceFileName.length() - 1
                ? sourceFileName.substring(dot + 1).replaceAll("[^a-zA-Z0-9]+", "").toLowerCase(Locale.ROOT)
                : "ogg";
        if (extension.isBlank()) {
            extension = "ogg";
        }
        return sanitizeFileName(sanitizeSongName(uploadName) + "." + extension);
    }

    private static String stripWrappingQuotes(String input) {
        if (input.length() >= 2 && input.startsWith("\"") && input.endsWith("\"")) {
            return input.substring(1, input.length() - 1);
        }
        return input;
    }

    private static String sanitizeSongName(String input) {
        String sanitized = input == null ? "" : input.replaceAll("[\\p{Cntrl}]+", " ").trim().replaceAll("\\s+", " ");
        if (sanitized.isBlank()) {
            sanitized = "music";
        }
        if (sanitized.length() > 64) {
            sanitized = sanitized.substring(0, 64).trim();
        }
        return sanitized;
    }

    private static String sanitizeFileName(String input) {
        String sanitized = input == null ? "music" : input.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "_").trim();
        if (sanitized.isBlank()) {
            sanitized = "music";
        }
        if (sanitized.length() > 80) {
            int dot = sanitized.lastIndexOf('.');
            String extension = dot >= 0 ? sanitized.substring(dot) : "";
            String base = dot >= 0 ? sanitized.substring(0, dot) : sanitized;
            int maxBaseLength = Math.max(1, 80 - extension.length());
            sanitized = base.substring(0, Math.min(base.length(), maxBaseLength)) + extension;
        }
        return sanitized;
    }

    private static void showProgress(Minecraft client, long uploadedBytes, long sizeBytes, long startedAtMillis, Consumer<String> progress) {
        double percent = sizeBytes <= 0L ? 100.0D : (uploadedBytes * 100.0D / sizeBytes);
        long elapsedMillis = Math.max(1L, System.currentTimeMillis() - startedAtMillis);
        double bytesPerMillis = uploadedBytes / (double) elapsedMillis;
        long remainingMillis = bytesPerMillis <= 0.0D ? 0L : (long) ((sizeBytes - uploadedBytes) / bytesPerMillis);
        String message = "Upload " + String.format(java.util.Locale.ROOT, "%.1f", percent) + "%, ETA " + formatEta(remainingMillis);
        client.execute(() -> {
            client.gui.setOverlayMessage(Component.literal(message), false);
            if (progress != null) {
                progress.accept(message);
            }
        });
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
