package de.coulees.B1progame.musicxcst.service.audio;

public final class AudioImportService {
    public String sanitizeSongName(String input) {
        String sanitized = input == null ? "" : input.replaceAll("[\\p{Cntrl}]+", " ").trim().replaceAll("\\s+", " ");
        if (sanitized.isBlank()) {
            throw new IllegalArgumentException("Song name cannot be blank.");
        }
        return sanitized.length() > 64 ? sanitized.substring(0, 64).trim() : sanitized;
    }
}
