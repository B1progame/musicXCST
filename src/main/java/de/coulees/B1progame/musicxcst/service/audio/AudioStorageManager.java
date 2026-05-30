package de.coulees.B1progame.musicxcst.service.audio;

import java.nio.file.Path;

public final class AudioStorageManager {
    private final Path importRoot;
    private final Path normalizedRoot;

    public AudioStorageManager(Path importRoot, Path normalizedRoot) {
        this.importRoot = importRoot.normalize();
        this.normalizedRoot = normalizedRoot.normalize();
    }

    public Path importRoot() {
        return importRoot;
    }

    public Path normalizedRoot() {
        return normalizedRoot;
    }

    public Path resolveNormalizedRelative(String safeRelativePath) {
        Path resolved = normalizedRoot.resolve(safeRelativePath).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Normalized audio path escapes storage root.");
        }
        return resolved;
    }
}
