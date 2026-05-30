package de.coulees.B1progame.musicxcst.service.audio;

import de.coulees.B1progame.musicxcst.config.CstMusicConfig;

import java.nio.file.Path;

public final class AudioCompressionService {
    public record CompressionPlan(Path source, Path output, String format, String bitrate, int sampleRate, int channels) {
    }

    public CompressionPlan planVorbis(Path source, Path output, CstMusicConfig config) {
        int channels = config.monoDownmix ? 1 : (config.stereoEnabled ? 2 : 1);
        int sampleRate = config.sampleRate <= 0 ? 44100 : config.sampleRate;
        String bitrate = config.audioBitrate == null || config.audioBitrate.isBlank() ? "128k" : config.audioBitrate;
        return new CompressionPlan(source, output, "ogg", bitrate, sampleRate, channels);
    }
}
