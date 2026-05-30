package de.coulees.B1progame.musicxcst.service.audio;

import de.coulees.B1progame.musicxcst.config.CstMusicConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class AudioValidationService {
    public void validateInputFile(Path path, long fileSize, CstMusicConfig config) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Music file not found.");
        }
        if (fileSize <= 0L) {
            throw new IllegalArgumentException("Music file is empty.");
        }
        if (fileSize > config.maxFileSizeBytes) {
            throw new IllegalArgumentException("Music file exceeds the configured max size.");
        }
        String extension = extension(path.getFileName().toString());
        if (extension.isBlank() || !config.allowedFileExtensions.contains(extension)) {
            throw new IllegalArgumentException("Extension ." + extension + " is not allowed.");
        }
    }

    public String extension(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        return dot < 0 ? "" : lower.substring(dot + 1);
    }
}
