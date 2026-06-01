package de.coulees.B1progame.musicxcst.media;

import de.coulees.B1progame.musicxcst.Musicxcst;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;

public final class BundledFfmpegProvider {
    private static final String RESOURCE_ROOT = "/native/ffmpeg/";

    public Optional<Path> resolve(Path baseDirectory) throws IOException {
        Platform platform = currentPlatform();
        if (platform == null) {
            return Optional.empty();
        }

        String resourcePath = RESOURCE_ROOT + platform.resourceFolder + "/" + platform.executableName;
        try (InputStream input = BundledFfmpegProvider.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                return Optional.empty();
            }

            Path output = baseDirectory.resolve("config")
                    .resolve(Musicxcst.MOD_ID)
                    .resolve("native")
                    .resolve("ffmpeg")
                    .resolve(platform.resourceFolder)
                    .resolve(platform.executableName)
                    .normalize();
            Files.createDirectories(output.getParent());
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
            output.toFile().setReadable(true, true);
            output.toFile().setExecutable(true, true);
            return Optional.of(output);
        }
    }

    public static String supportedResourcePaths() {
        return String.join(", ",
                "native/ffmpeg/windows-x86_64/ffmpeg.exe",
                "native/ffmpeg/linux-x86_64/ffmpeg",
                "native/ffmpeg/linux-aarch64/ffmpeg",
                "native/ffmpeg/macos-x86_64/ffmpeg",
                "native/ffmpeg/macos-aarch64/ffmpeg");
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
