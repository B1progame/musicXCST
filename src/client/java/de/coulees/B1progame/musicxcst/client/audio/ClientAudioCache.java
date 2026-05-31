package de.coulees.B1progame.musicxcst.client.audio;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Set;

public final class ClientAudioCache {
    private static final Path CACHE_ROOT = Minecraft.getInstance().gameDirectory.toPath().resolve("musicxcst-cache").normalize();

    private ClientAudioCache() {
    }

    public static Path completePath(String musicId, String sha256) {
        return CACHE_ROOT.resolve(safeName(musicId + "-" + sha256 + ".ogg")).normalize();
    }

    public static Path previewCompletePath(String musicId, String sha256) {
        return CACHE_ROOT.resolve(safeName(musicId + "-preview-" + sha256 + ".ogg")).normalize();
    }

    public static Path tempPath(String musicId, String sha256) {
        return CACHE_ROOT.resolve(safeName(musicId + "-" + sha256 + ".part")).normalize();
    }

    public static Path previewTempPath(String musicId, String sha256) {
        return CACHE_ROOT.resolve(safeName(musicId + "-preview-" + sha256 + ".part")).normalize();
    }

    public static boolean hasComplete(String musicId, String sha256, long expectedSize) {
        Path path = completePath(musicId, sha256);
        return Files.isRegularFile(path) && size(path) == expectedSize && verifySha256(path, sha256);
    }

    public static boolean hasPreviewComplete(String musicId, String sha256, long expectedSize) {
        Path path = previewCompletePath(musicId, sha256);
        return Files.isRegularFile(path) && size(path) == expectedSize && verifySha256(path, sha256);
    }

    public static void writeChunk(String musicId, String sha256, long offset, byte[] data, boolean preview) throws IOException {
        Files.createDirectories(CACHE_ROOT);
        Path path = preview ? previewTempPath(musicId, sha256) : tempPath(musicId, sha256);
        if (!path.startsWith(CACHE_ROOT)) {
            throw new IOException("Cache path escaped cache root.");
        }

        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
            file.seek(offset);
            file.write(data);
        }
    }

    public static Path finish(String musicId, String sha256, long expectedSize, boolean preview) throws IOException {
        Path temp = preview ? previewTempPath(musicId, sha256) : tempPath(musicId, sha256);
        Path complete = preview ? previewCompletePath(musicId, sha256) : completePath(musicId, sha256);
        if (size(temp) != expectedSize) {
            throw new IOException("Downloaded audio size does not match server metadata.");
        }
        if (!verifySha256(temp, sha256)) {
            throw new IOException("Downloaded audio checksum does not match server metadata.");
        }
        Files.move(temp, complete, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return complete;
    }

    public static void deleteTemp(String musicId, String sha256) {
        deleteTemp(musicId, sha256, false);
    }

    public static void deleteTemp(String musicId, String sha256, boolean preview) {
        try {
            Files.deleteIfExists(preview ? previewTempPath(musicId, sha256) : tempPath(musicId, sha256));
        } catch (IOException exception) {
            Musicxcst.LOGGER.debug("Failed to delete partial audio cache file: {}", exception.getMessage());
        }
    }

    public static void pruneExcept(Set<String> validCacheKeys) {
        if (validCacheKeys == null) {
            return;
        }
        try {
            if (!Files.isDirectory(CACHE_ROOT)) {
                return;
            }
            try (var paths = Files.list(CACHE_ROOT)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".ogg"))
                        .filter(path -> !validCacheKeys.contains(stripExtension(path.getFileName().toString())))
                        .forEach(ClientAudioCache::deleteQuietly);
            }
        } catch (IOException exception) {
            Musicxcst.LOGGER.debug("Failed to prune audio cache: {}", exception.getMessage());
        }
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            return -1L;
        }
    }

    private static boolean verifySha256(Path path, String expected) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(path));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString().equalsIgnoreCase(expected);
        } catch (IOException | NoSuchAlgorithmException exception) {
            return false;
        }
    }

    private static String safeName(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
    }

    private static String stripExtension(String fileName) {
        return fileName.endsWith(".ogg") ? fileName.substring(0, fileName.length() - 4) : fileName;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            Musicxcst.LOGGER.debug("Failed to delete stale audio cache file '{}': {}", path.getFileName(), exception.getMessage());
        }
    }
}
