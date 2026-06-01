package de.coulees.B1progame.musicxcst.media;

import de.coulees.B1progame.musicxcst.config.CstMusicConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class FfmpegLocator {
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(10);

    private final BundledFfmpegProvider bundledProvider = new BundledFfmpegProvider();
    private final SystemFfmpegProvider systemProvider = new SystemFfmpegProvider();

    public Optional<String> locate(Path baseDirectory, CstMusicConfig config) {
        String mode = config.ffmpegMode == null || config.ffmpegMode.isBlank()
                ? "bundled"
                : config.ffmpegMode.toLowerCase(Locale.ROOT);

        if ("disabled".equals(mode)) {
            return Optional.empty();
        }
        if ("path".equals(mode)) {
            return configuredPath(config).filter(this::isAvailable);
        }
        if ("system".equals(mode)) {
            return systemProvider.resolve().filter(this::isAvailable);
        }
        if ("bundled".equals(mode)) {
            Optional<String> bundled = bundled(baseDirectory).filter(this::isAvailable);
            if (bundled.isPresent()) {
                return bundled;
            }
            return systemProvider.resolve().filter(this::isAvailable);
        }

        Optional<String> bundled = bundled(baseDirectory).filter(this::isAvailable);
        return bundled.isPresent() ? bundled : systemProvider.resolve().filter(this::isAvailable);
    }

    public String require(Path baseDirectory, CstMusicConfig config) {
        return locate(baseDirectory, config)
                .orElseThrow(() -> new IllegalArgumentException("FFmpeg is unavailable. Bundle it under " + BundledFfmpegProvider.supportedResourcePaths() + ", configure ffmpegPath, use system ffmpeg, or upload an already-normalized .ogg."));
    }

    private Optional<String> configuredPath(CstMusicConfig config) {
        String configured = config.ffmpegPath == null ? "" : config.ffmpegPath.trim();
        return configured.isBlank() ? Optional.empty() : Optional.of(configured);
    }

    private Optional<String> bundled(Path baseDirectory) {
        try {
            return bundledProvider.resolve(baseDirectory).map(Path::toString);
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    public boolean isAvailable(String executable) {
        try {
            Process process = new ProcessBuilder(List.of(executable, "-version")).redirectErrorStream(true).start();
            boolean finished = process.waitFor(PROBE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
