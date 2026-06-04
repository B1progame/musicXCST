package de.coulees.B1progame.musicxcst.media;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.config.CstMusicConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ManagedFfmpegProvider {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String WINDOWS_X86_64_URL = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-n8.1-latest-win64-lgpl-8.1.zip";
    private static final String WINDOWS_X86_64_SHA256 = "8d68576f84043b3e2027ed020de9f814e39795007c64061bf40310e0d3f7fee6";
    private static final String WINDOWS_X86_64_VERSION = "BtbN FFmpeg n8.1 latest win64 LGPL 8.1, GitHub release published 2026-06-04";
    private static final String LGPL_LICENSE = "LGPL build variant from BtbN/FFmpeg-Builds; do not use if ffmpeg -version reports --enable-nonfree.";

    public Optional<String> resolveInstalled(Path baseDirectory) {
        Optional<FfmpegPlatform> platform = FfmpegPlatform.current();
        if (platform.isEmpty()) {
            return Optional.empty();
        }

        Path executable = installDirectory(baseDirectory, platform.get()).resolve(platform.get().executableName()).normalize();
        if (!Files.isRegularFile(executable)) {
            return Optional.empty();
        }
        Path metadata = metadataPath(baseDirectory, platform.get());
        if (!Files.isRegularFile(metadata)) {
            return Optional.empty();
        }
        return Optional.of(executable.toString());
    }

    public ManagedInstallResult install(Path baseDirectory, CstMusicConfig config, Consumer<String> progress) {
        FfmpegPlatform platform = FfmpegPlatform.current()
                .orElseThrow(() -> new IllegalArgumentException("Managed FFmpeg is not available for this operating system or CPU architecture. Install FFmpeg manually and use system/path mode."));
        ManagedDownload download = downloadFor(platform)
                .orElseThrow(() -> new IllegalArgumentException(manualInstallMessage(platform)));

        config.ffmpegManagedDownloadAllowed = true;
        config.ffmpegManagedSourceUrl = download.url();
        config.ffmpegManagedVersion = download.version();
        config.ffmpegManagedSha256 = download.sha256();
        config.ffmpegManagedLicense = download.license();

        try {
            progress(progress, "Preparing FFmpeg download from verified source...");
            Path targetRoot = installDirectory(baseDirectory, platform);
            Files.createDirectories(targetRoot);
            Path archive = targetRoot.resolve(download.archiveName()).normalize();
            downloadArchive(download, archive, progress);
            progress(progress, "Verifying FFmpeg download with SHA-256...");
            verifySha256(archive, download.sha256());
            progress(progress, "Extracting FFmpeg executable from verified archive...");
            Path executable = extractZip(download, archive, targetRoot, platform);
            executable.toFile().setReadable(true, true);
            executable.toFile().setExecutable(true, true);
            progress(progress, "Running ffmpeg -version and checking license flags...");
            VersionReport report = versionReport(executable);
            if (report.configuration().toLowerCase(Locale.ROOT).contains("--enable-nonfree")) {
                Files.deleteIfExists(executable);
                throw new IllegalArgumentException("Downloaded FFmpeg reports --enable-nonfree and will not be used.");
            }
            writeMetadata(baseDirectory, platform, download, executable, report);
            Files.deleteIfExists(archive);
            return new ManagedInstallResult(executable.toString(), report.firstLine(), report.configuration());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to install managed FFmpeg: " + exception.getMessage(), exception);
        }
    }

    public void reset(Path baseDirectory) {
        FfmpegPlatform.current().ifPresent(platform -> deleteRecursively(installDirectory(baseDirectory, platform)));
    }

    public String status(Path baseDirectory, CstMusicConfig config, FfmpegLocator locator) {
        Optional<String> located = locator.locate(baseDirectory, config);
        if (located.isPresent()) {
            return "FFmpeg mode '" + FfmpegLocator.normalizedMode(config) + "' is available at " + located.get() + ".";
        }
        return "FFmpeg is unavailable in mode '" + FfmpegLocator.normalizedMode(config) + "'. " + manualInstallMessage(FfmpegPlatform.current().orElse(null));
    }

    public static String manualInstallMessage(FfmpegPlatform platform) {
        String platformText = platform == null ? "this platform" : platform.id();
        return "Managed FFmpeg download is not configured for " + platformText + ". Install FFmpeg manually, put it on PATH, or set ffmpegMode=path and ffmpegPath to the executable.";
    }

    private Optional<ManagedDownload> downloadFor(FfmpegPlatform platform) {
        if ("windows".equals(platform.os()) && "x86_64".equals(platform.arch())) {
            return Optional.of(new ManagedDownload(
                    WINDOWS_X86_64_URL,
                    WINDOWS_X86_64_SHA256,
                    WINDOWS_X86_64_VERSION,
                    LGPL_LICENSE,
                    "ffmpeg-n8.1-latest-win64-lgpl-8.1.zip"
            ));
        }
        return Optional.empty();
    }

    private Path installDirectory(Path baseDirectory, FfmpegPlatform platform) {
        return baseDirectory.resolve("config")
                .resolve(Musicxcst.MOD_ID)
                .resolve("ffmpeg")
                .resolve("managed")
                .resolve(platform.id())
                .normalize();
    }

    private Path metadataPath(Path baseDirectory, FfmpegPlatform platform) {
        return installDirectory(baseDirectory, platform).resolve("metadata.json").normalize();
    }

    private void downloadArchive(ManagedDownload download, Path archive, Consumer<String> progress) throws IOException {
        URI uri = URI.create(download.url());
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Managed FFmpeg downloads must use HTTPS.");
        }
        URL url = uri.toURL();
        URLConnection connection = url.openConnection();
        long totalBytes = Math.max(0L, connection.getContentLengthLong());
        long startedAtNanos = System.nanoTime();
        long lastReportNanos = 0L;
        long downloaded = 0L;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = connection.getInputStream();
             OutputStream output = Files.newOutputStream(archive, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.StandardOpenOption.WRITE)) {
            progress(progress, downloadProgressMessage(downloaded, totalBytes, startedAtNanos));
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
                downloaded += read;
                long now = System.nanoTime();
                if (now - lastReportNanos >= TimeUnit.MILLISECONDS.toNanos(500L) || downloaded == totalBytes) {
                    lastReportNanos = now;
                    progress(progress, downloadProgressMessage(downloaded, totalBytes, startedAtNanos));
                }
            }
        }
    }

    private String downloadProgressMessage(long downloadedBytes, long totalBytes, long startedAtNanos) {
        double elapsedSeconds = Math.max(0.001D, (System.nanoTime() - startedAtNanos) / 1_000_000_000.0D);
        double bytesPerSecond = downloadedBytes / elapsedSeconds;
        if (totalBytes > 0L) {
            int percent = (int) Math.max(0L, Math.min(100L, downloadedBytes * 100L / totalBytes));
            long remainingBytes = Math.max(0L, totalBytes - downloadedBytes);
            long etaSeconds = bytesPerSecond <= 1.0D ? -1L : Math.round(remainingBytes / bytesPerSecond);
            return "Downloading FFmpeg: " + percent + "% (" + formatBytes(downloadedBytes) + " / " + formatBytes(totalBytes) + ", "
                    + formatBytes((long) bytesPerSecond) + "/s, ETA " + formatEta(etaSeconds) + ").";
        }
        return "Downloading FFmpeg: " + formatBytes(downloadedBytes) + " received (" + formatBytes((long) bytesPerSecond) + "/s, size unknown).";
    }

    private String formatEta(long seconds) {
        if (seconds < 0L) {
            return "unknown";
        }
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        return minutes > 0L ? minutes + "m " + remainingSeconds + "s" : remainingSeconds + "s";
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kib = bytes / 1024.0D;
        if (kib < 1024.0D) {
            return String.format(Locale.ROOT, "%.1f KiB", kib);
        }
        double mib = kib / 1024.0D;
        if (mib < 1024.0D) {
            return String.format(Locale.ROOT, "%.1f MiB", mib);
        }
        return String.format(Locale.ROOT, "%.1f GiB", mib / 1024.0D);
    }

    private void verifySha256(Path archive, String expectedSha256) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(Files.newInputStream(archive), digest)) {
                input.transferTo(OutputDiscard.INSTANCE);
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!actual.equalsIgnoreCase(expectedSha256)) {
                Files.deleteIfExists(archive);
                throw new IllegalArgumentException("Downloaded FFmpeg SHA-256 mismatch. Expected " + expectedSha256 + " but got " + actual + ".");
            }
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private Path extractZip(ManagedDownload download, Path archive, Path targetRoot, FfmpegPlatform platform) throws IOException {
        Path executable = targetRoot.resolve(platform.executableName()).normalize();
        if (!executable.startsWith(targetRoot)) {
            throw new IOException("Managed FFmpeg executable path escaped install root.");
        }
        boolean extracted = false;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName().replace('\\', '/');
                Path normalizedEntry = Path.of(entryName).normalize();
                if (entry.isDirectory() || entryName.startsWith("/") || entryName.contains("..") || normalizedEntry.isAbsolute()) {
                    continue;
                }
                if (entryName.endsWith("/bin/" + platform.executableName())) {
                    Files.copy(zip, executable, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    extracted = true;
                }
            }
        }
        if (!extracted) {
            throw new IOException("The FFmpeg archive did not contain the expected bin/" + platform.executableName() + " entry from " + download.url() + ".");
        }
        return executable;
    }

    private VersionReport versionReport(Path executable) throws IOException {
        try {
            Process process = new ProcessBuilder(executable.toString(), "-version").redirectErrorStream(true).start();
            boolean finished = process.waitFor(10L, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("ffmpeg -version timed out.");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IOException("ffmpeg -version failed.");
            }
            String firstLine = output.lines().findFirst().orElse("ffmpeg version unknown");
            String configuration = output.lines()
                    .filter(line -> line.startsWith("configuration:"))
                    .findFirst()
                    .orElse("");
            return new VersionReport(firstLine, configuration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("ffmpeg -version was interrupted.", exception);
        }
    }

    private void writeMetadata(Path baseDirectory, FfmpegPlatform platform, ManagedDownload download, Path executable, VersionReport report) throws IOException {
        Metadata metadata = new Metadata();
        metadata.platform = platform.id();
        metadata.executable = executable.toString();
        metadata.sourceUrl = download.url();
        metadata.version = download.version();
        metadata.sha256 = download.sha256();
        metadata.license = download.license();
        metadata.installedAt = Instant.now().toString();
        metadata.ffmpegVersionLine = report.firstLine();
        metadata.ffmpegConfiguration = report.configuration();
        try (Writer writer = Files.newBufferedWriter(metadataPath(baseDirectory, platform), StandardCharsets.UTF_8)) {
            GSON.toJson(metadata, writer);
        }
    }

    public Optional<Metadata> readMetadata(Path baseDirectory) {
        Optional<FfmpegPlatform> platform = FfmpegPlatform.current();
        if (platform.isEmpty()) {
            return Optional.empty();
        }
        Path metadataPath = metadataPath(baseDirectory, platform.get());
        if (!Files.isRegularFile(metadataPath)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(metadataPath, StandardCharsets.UTF_8)) {
            return Optional.ofNullable(GSON.fromJson(reader, Metadata.class));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private void progress(Consumer<String> progress, String message) {
        if (progress != null) {
            progress.accept(message);
        }
    }

    private void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    Musicxcst.LOGGER.warn("Failed to delete managed FFmpeg path '{}': {}", path, exception.getMessage());
                }
            });
        } catch (IOException exception) {
            Musicxcst.LOGGER.warn("Failed to reset managed FFmpeg directory '{}': {}", root, exception.getMessage());
        }
    }

    private record ManagedDownload(String url, String sha256, String version, String license, String archiveName) {
    }

    public record ManagedInstallResult(String executable, String versionLine, String configuration) {
    }

    private record VersionReport(String firstLine, String configuration) {
    }

    public static final class Metadata {
        public String platform;
        public String executable;
        public String sourceUrl;
        public String version;
        public String sha256;
        public String license;
        public String installedAt;
        public String ffmpegVersionLine;
        public String ffmpegConfiguration;
    }

    private static final class OutputDiscard extends OutputStream {
        private static final OutputDiscard INSTANCE = new OutputDiscard();

        private OutputDiscard() {
        }

        @Override
        public void write(int b) {
        }
    }
}
