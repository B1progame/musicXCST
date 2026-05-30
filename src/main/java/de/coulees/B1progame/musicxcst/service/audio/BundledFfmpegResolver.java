package de.coulees.B1progame.musicxcst.service.audio;

import de.coulees.B1progame.musicxcst.Musicxcst;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class BundledFfmpegResolver {
    private static final String RESOURCE_ROOT = "/musicxcst/ffmpeg/";

    private BundledFfmpegResolver() {
    }

    public static Path resolve(Path serverDirectory) throws IOException {
        Platform platform = currentPlatform();
        if (platform == null) {
            return null;
        }

        String resourcePath = RESOURCE_ROOT + platform.resourceFolder + "/" + platform.executableName;
        try (InputStream input = BundledFfmpegResolver.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                return null;
            }

            Path output = serverDirectory
                    .resolve("config")
                    .resolve(Musicxcst.MOD_ID)
                    .resolve("ffmpeg")
                    .resolve(platform.resourceFolder)
                    .resolve(platform.executableName)
                    .normalize();
            Files.createDirectories(output.getParent());
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
            output.toFile().setReadable(true, true);
            output.toFile().setExecutable(true, true);
            return output;
        }
    }

    public static String supportedResourcePaths() {
        return String.join(", ",
                "musicxcst/ffmpeg/windows-x86_64/ffmpeg.exe",
                "musicxcst/ffmpeg/linux-x86_64/ffmpeg",
                "musicxcst/ffmpeg/linux-aarch64/ffmpeg",
                "musicxcst/ffmpeg/macos-x86_64/ffmpeg",
                "musicxcst/ffmpeg/macos-aarch64/ffmpeg");
    }

    private static Platform currentPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String normalizedArch = normalizeArch(arch);
        if (normalizedArch == null) {
            return null;
        }

        if (os.contains("win")) {
            return new Platform("windows-" + normalizedArch, "ffmpeg.exe");
        }
        if (os.contains("linux")) {
            return new Platform("linux-" + normalizedArch, "ffmpeg");
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return new Platform("macos-" + normalizedArch, "ffmpeg");
        }
        return null;
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

    private record Platform(String resourceFolder, String executableName) {
    }
}
