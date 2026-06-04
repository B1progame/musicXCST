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

    private final SystemFfmpegProvider systemProvider = new SystemFfmpegProvider();
    private final ManagedFfmpegProvider managedProvider = new ManagedFfmpegProvider();

    public Optional<String> locate(Path baseDirectory, CstMusicConfig config) {
        String mode = normalizedMode(config);

        if ("disabled".equals(mode)) {
            return Optional.empty();
        }
        if ("path".equals(mode)) {
            return configuredPath(config).filter(this::isAvailable);
        }
        if ("system".equals(mode)) {
            return systemProvider.resolve().filter(this::isAvailable);
        }
        if ("managed".equals(mode)) {
            return managedProvider.resolveInstalled(baseDirectory).filter(this::isAvailable);
        }

        return systemProvider.resolve().filter(this::isAvailable);
    }

    public String require(Path baseDirectory, CstMusicConfig config) {
        return locate(baseDirectory, config)
                .orElseThrow(() -> new IllegalArgumentException("FFmpeg is unavailable. Install FFmpeg on PATH, set ffmpegMode=path with ffmpegPath, use managed setup after explicit consent, or use an already-compatible .ogg file when conversion is not needed."));
    }

    public static String normalizedMode(CstMusicConfig config) {
        String mode = config.ffmpegMode == null || config.ffmpegMode.isBlank()
                ? "system"
                : config.ffmpegMode.toLowerCase(Locale.ROOT).trim();
        return switch (mode) {
            case "disabled", "path", "system", "managed" -> mode;
            case "bundled" -> "system";
            default -> "system";
        };
    }

    private Optional<String> configuredPath(CstMusicConfig config) {
        String configured = config.ffmpegPath == null ? "" : config.ffmpegPath.trim();
        return configured.isBlank() ? Optional.empty() : Optional.of(configured);
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
