package de.coulees.B1progame.musicxcst.media;

import java.nio.file.Path;

public record TranscodeResult(Path output, long sizeBytes, long durationMillis) {
}
