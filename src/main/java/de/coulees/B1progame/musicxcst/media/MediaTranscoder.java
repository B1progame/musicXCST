package de.coulees.B1progame.musicxcst.media;

import de.coulees.B1progame.musicxcst.config.CstMusicConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class MediaTranscoder {
    private static final Duration TRANSCODE_TIMEOUT = Duration.ofMinutes(10);

    public TranscodeResult transcodeToOgg(String ffmpeg, Path source, Path output, CstMusicConfig config, Consumer<String> progress) {
        try {
            Files.createDirectories(output.getParent());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to create transcoded audio folder.", exception);
        }

        List<String> command = new ArrayList<>();
        command.add(ffmpeg);
        command.add("-y");
        command.add("-i");
        command.add(source.toString());
        if (config.maxDurationSeconds > 0) {
            command.add("-t");
            command.add(Integer.toString(config.maxDurationSeconds));
        }
        command.add("-vn");
        command.add("-map");
        command.add("a:0");
        command.add("-c:a");
        command.add("libvorbis");
        command.add("-b:a");
        command.add(audioBitrate(config));
        command.add("-ar");
        command.add("48000");
        command.add("-ac");
        command.add("2");
        command.add(output.toString());
        run(command, "Transcoding audio", progress, TRANSCODE_TIMEOUT);
        try {
            return new TranscodeResult(output, Files.size(output), 0L);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read transcoded audio.", exception);
        }
    }

    public long probeDurationMillis(String ffmpeg, Path file) {
        try {
            Process process = new ProcessBuilder(List.of(ffmpeg, "-hide_banner", "-i", file.toString()))
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor(30L, TimeUnit.SECONDS);
            return parseDurationMillis(output);
        } catch (IOException exception) {
            return 0L;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return 0L;
        }
    }

    public void createPreview(String ffmpeg, Path source, Path output, CstMusicConfig config, int previewSeconds) {
        List<String> command = new ArrayList<>();
        command.add(ffmpeg);
        command.add("-y");
        command.add("-i");
        command.add(source.toString());
        command.add("-t");
        command.add(Integer.toString(previewSeconds));
        command.add("-vn");
        command.add("-map");
        command.add("a:0");
        command.add("-c:a");
        command.add("libvorbis");
        command.add("-b:a");
        command.add(audioBitrate(config));
        command.add("-ar");
        command.add(Integer.toString(config.sampleRate <= 0 ? 44100 : config.sampleRate));
        command.add("-ac");
        command.add(config.monoDownmix ? "1" : (config.stereoEnabled ? "2" : "1"));
        command.add(output.toString());
        run(command, "Creating preview audio", null, Duration.ofMinutes(3));
    }

    private void run(List<String> command, String label, Consumer<String> progress, Duration timeout) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            StringBuilder log = new StringBuilder();
            Thread logReader = new Thread(() -> {
                try {
                    log.append(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException exception) {
                    log.append(exception.getMessage());
                }
            }, "musicxcst-ffmpeg-log");
            logReader.setDaemon(true);
            logReader.start();

            long started = System.currentTimeMillis();
            while (!process.waitFor(1L, TimeUnit.SECONDS)) {
                if (System.currentTimeMillis() - started > timeout.toMillis()) {
                    process.destroyForcibly();
                    throw new IllegalArgumentException(label + " timed out.");
                }
                if (progress != null) {
                    progress.accept(label + "...");
                }
            }

            logReader.join(1000L);
            if (process.exitValue() != 0) {
                throw new IllegalArgumentException(label + " failed. " + safeProcessLog(log.toString()));
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to run FFmpeg.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException(label + " was interrupted.", exception);
        }
    }

    private String audioBitrate(CstMusicConfig config) {
        if (config.audioBitrateKbps > 0) {
            return config.audioBitrateKbps + "k";
        }
        return config.audioBitrate == null || config.audioBitrate.isBlank() ? "128k" : config.audioBitrate;
    }

    public static String safeProcessLog(String log) {
        if (log == null || log.isBlank()) {
            return "";
        }
        String sanitized = log.replace('\r', ' ').replace('\n', ' ').trim();
        return sanitized.length() <= 500 ? sanitized : sanitized.substring(0, 500) + "...";
    }

    public static long parseDurationMillis(String ffmpegOutput) {
        if (ffmpegOutput == null) {
            return 0L;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("Duration:\\s*(\\d+):(\\d+):(\\d+(?:\\.\\d+)?)")
                .matcher(ffmpegOutput);
        if (!matcher.find()) {
            return 0L;
        }

        long hours = Long.parseLong(matcher.group(1));
        long minutes = Long.parseLong(matcher.group(2));
        double seconds = Double.parseDouble(matcher.group(3));
        return (long) (((hours * 60L + minutes) * 60L + seconds) * 1000.0D);
    }
}
