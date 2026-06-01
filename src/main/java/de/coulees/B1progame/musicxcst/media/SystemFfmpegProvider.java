package de.coulees.B1progame.musicxcst.media;

import java.util.Optional;

public final class SystemFfmpegProvider {
    public Optional<String> resolve() {
        return Optional.of("ffmpeg");
    }
}
