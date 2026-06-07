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
            long size = Files.size(output);
            long duration = probeDurationMillis(ffmpeg, output);
            return new TranscodeResult(output, size, duration);
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

    public static long probeOggDurationMillis(Path file) {
        try {
            long fileSize = Files.size(file);
            if (fileSize <= 0L) return 0L;

            int headRead = (int) Math.min(64 * 1024, fileSize);
            byte[] header = new byte[headRead];
            try (java.io.InputStream is = Files.newInputStream(file)) {
                int r = is.read(header);
                if (r <= 0) return 0L;
            }

            byte[] vorbisBytes = "vorbis".getBytes(StandardCharsets.US_ASCII);
            int idx = indexOf(header, vorbisBytes);
            if (idx >= 0 && idx + 14 < header.length) {
                int srOffset = idx + 6 + 4 + 1; // idx + 11
                if (srOffset + 3 < header.length) {
                    int sampleRate = ((header[srOffset] & 0xFF))
                            | ((header[srOffset + 1] & 0xFF) << 8)
                            | ((header[srOffset + 2] & 0xFF) << 16)
                            | ((header[srOffset + 3] & 0xFF) << 24);

                    int tailRead = (int) Math.min(64 * 1024, fileSize);
                    byte[] tail = new byte[tailRead];
                    try (java.io.InputStream is2 = Files.newInputStream(file)) {
                        long skip = Math.max(0L, fileSize - tailRead);
                        is2.skip(skip);
                        int r2 = is2.read(tail);
                        if (r2 <= 0) return 0L;
                    }

                    int last = lastIndexOf(tail, new byte[]{'O', 'g', 'g', 'S'});
                    if (last >= 0 && last + 14 < tail.length) {
                        long gp = 0L;
                        for (int i = 0; i < 8; i++) {
                            gp |= (long) (tail[last + 6 + i] & 0xFF) << (8 * i);
                        }
                        if (sampleRate > 0 && gp > 0) {
                            return (gp * 1000L) / sampleRate;
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return 0L;
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static int lastIndexOf(byte[] data, byte[] pattern) {
        outer:
        for (int i = data.length - pattern.length; i >= 0; i--) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
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
