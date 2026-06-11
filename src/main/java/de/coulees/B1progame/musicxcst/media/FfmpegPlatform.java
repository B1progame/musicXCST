package de.coulees.B1progame.musicxcst.media;

import java.util.Locale;
import java.util.Optional;

public record FfmpegPlatform(String id, String os, String arch, String executableName, String assetToken, String archiveExtension) {
    public static Optional<FfmpegPlatform> current() {
        String osName = System.getProperty("musicxcst.fakeOsName", System.getProperty("os.name", "")).toLowerCase(Locale.ROOT);
        String archName = System.getProperty("musicxcst.fakeOsArch", System.getProperty("os.arch", "")).toLowerCase(Locale.ROOT);
        String arch = normalizeArch(archName);
        if (arch == null) {
            return Optional.empty();
        }

        if (osName.contains("win")) {
            if (!"x86_64".equals(arch)) {
                return Optional.empty();
            }
            return Optional.of(new FfmpegPlatform("windows-" + arch, "windows", arch, "ffmpeg.exe", "win64", ".zip"));
        }
        if (osName.contains("linux")) {
            String assetToken = switch (arch) {
                case "x86_64" -> "linux64";
                case "aarch64" -> "linuxarm64";
                default -> null;
            };
            if (assetToken == null) {
                return Optional.empty();
            }
            return Optional.of(new FfmpegPlatform("linux-" + arch, "linux", arch, "ffmpeg", assetToken, ".tar.xz"));
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return Optional.of(new FfmpegPlatform("macos-" + arch, "macos", arch, "ffmpeg", "", ""));
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
