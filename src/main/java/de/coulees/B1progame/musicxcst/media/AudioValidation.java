package de.coulees.B1progame.musicxcst.media;

import de.coulees.B1progame.musicxcst.config.CstMusicConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public final class AudioValidation {
    private static final Set<String> CLIENT_INPUT_EXTENSIONS = Set.of("mp3", "mp4", "wav", "flac", "m4a", "aac", "ogg", "webm", "avi");

    public void validateClientInput(Path path, CstMusicConfig config) {
        validateExistingFile(path);
        String extension = extension(path.getFileName().toString());
        if (!CLIENT_INPUT_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported input file. Use mp3, mp4, wav, flac, m4a, aac, ogg, webm, or avi.");
        }
        validateSize(path, config);
    }

    public void validateServerUpload(Path path, CstMusicConfig config) {
        validateExistingFile(path);
        if (!"ogg".equals(extension(path.getFileName().toString()))) {
            throw new IllegalArgumentException("Uploaded CD Writer audio must be normalized .ogg.");
        }
        validateSize(path, config);
    }

    public String extension(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        return dot < 0 || dot >= lower.length() - 1 ? "" : lower.substring(dot + 1);
    }

    private void validateExistingFile(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Music file was not found.");
        }
    }

    private void validateSize(Path path, CstMusicConfig config) {
        try {
            long maxBytes = maxUploadBytes(config);
            long size = Files.size(path);
            if (size <= 0L) {
                throw new IllegalArgumentException("Music file is empty.");
            }
            if (size > maxBytes) {
                throw new IllegalArgumentException("Music file exceeds the configured max upload size.");
            }
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Failed to read music file size.", exception);
        }
    }

    private long maxUploadBytes(CstMusicConfig config) {
        if (config.maxUploadMb > 0) {
            return config.maxUploadMb * 1024L * 1024L;
        }
        return config.maxFileSizeBytes;
    }
}
