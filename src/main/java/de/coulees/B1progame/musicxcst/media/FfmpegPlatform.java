package de.coulees.B1progame.musicxcst.media;

import java.util.Locale;
import java.util.Optional;

public record FfmpegPlatform(String id, String os, String arch, String executableName) {
    public static Optional<FfmpegPlatform> current() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String archName = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String arch = normalizeArch(archName);
        if (arch == null) {
            return Optional.empty();
        }

        if (osName.contains("win")) {
            return Optional.of(new FfmpegPlatform("windows-" + arch, "windows", arch, "ffmpeg.exe"));
        }
        if (osName.contains("linux")) {
            return Optional.of(new FfmpegPlatform("linux-" + arch, "linux", arch, "ffmpeg"));
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return Optional.of(new FfmpegPlatform("macos-" + arch, "macos", arch, "ffmpeg"));
        }
        return Optional.empty();
    }

    private static String normalizeArch(String arch) {
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            return "x86_64";
        }
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "aarch64";
        }
        return null;
    }
}
