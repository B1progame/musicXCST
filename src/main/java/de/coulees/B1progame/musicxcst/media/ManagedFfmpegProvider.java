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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
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

        Path executable = installDirectory(baseDirectory, platform.get()).resolve("bin").resolve(platform.get().executableName()).normalize();
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
        progress(progress, "Fetching latest FFmpeg release metadata from BtbN/FFmpeg-Builds...");
        ManagedDownload download = downloadFor(platform)
                .orElseThrow(() -> {
                    String msg = manualInstallMessage(platform);
                    Musicxcst.LOGGER.error("Failed to fetch FFmpeg release info: {}", msg);
                    return new IllegalArgumentException(msg);
                });

        progress(progress, "Selected FFmpeg asset " + download.archiveName() + " from release " + download.version() + ".");
        progress(progress, "Downloading FFmpeg archive and verifying checksum...");

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
            progress(progress, "FFmpeg installation completed successfully.");
            Musicxcst.LOGGER.info("Managed FFmpeg installed: {}", executable);
            return new ManagedInstallResult(executable.toString(), report.firstLine(), report.configuration());
        } catch (IOException exception) {
            String msg = "Failed to install managed FFmpeg: " + exception.getMessage();
            Musicxcst.LOGGER.error(msg, exception);
            throw new IllegalArgumentException(msg, exception);
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
        if (!"windows".equals(platform.os()) || !"x86_64".equals(platform.arch())) {
            Musicxcst.LOGGER.warn("Managed FFmpeg is not available for platform: {} / {}", platform.os(), platform.arch());
            return Optional.empty();
        }
        final String apiUrl = "https://api.github.com/repos/BtbN/FFmpeg-Builds/releases/latest";
        try {
            Musicxcst.LOGGER.info("Fetching latest FFmpeg release from: {}", apiUrl);
            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("User-Agent", "MusicXCST");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                Musicxcst.LOGGER.warn("Failed to fetch FFmpeg release metadata: HTTP {}", code);
                conn.disconnect();
                return Optional.empty();
            }
            try (InputStream is = conn.getInputStream();
                 Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                Release release = GSON.fromJson(reader, Release.class);
                conn.disconnect();
                if (release == null || release.assets == null) {
                    Musicxcst.LOGGER.warn("GitHub release response was null or had no assets");
                    return Optional.empty();
                }
                Musicxcst.LOGGER.info("Found FFmpeg release: {}", release.tag_name);
                
                Asset selected = null;
                for (Asset a : release.assets) {
                    if (a == null || a.name == null || a.browser_download_url == null) continue;
                    String name = a.name.toLowerCase(Locale.ROOT);
                    if (name.contains("win64") && name.contains("lgpl") && name.endsWith(".zip") && !name.contains("gpl") && !name.contains("nonfree")) {
                        selected = a;
                        Musicxcst.LOGGER.info("Selected FFmpeg asset: {}", a.name);
                        break;
                    }
                }
                if (selected == null) {
                    for (Asset a : release.assets) {
                        if (a == null || a.name == null) continue;
                        String name = a.name.toLowerCase(Locale.ROOT);
                        if (name.contains("win64") && name.endsWith(".zip") && name.contains("lgpl")) {
                            selected = a;
                            Musicxcst.LOGGER.info("Selected FFmpeg asset (fallback): {}", a.name);
                            break;
                        }
                    }
                }
                if (selected == null) {
                    Musicxcst.LOGGER.warn("No suitable FFmpeg asset found in release {}", release.tag_name);
                    return Optional.empty();
                }
                
                Asset checksumAsset = null;
                for (Asset a : release.assets) {
                    if (a == null || a.name == null) continue;
                    String name = a.name.toLowerCase(Locale.ROOT);
                    if ((name.contains("checksums") || name.contains("sha256")) && (name.endsWith(".txt") || name.endsWith(".sha256") || name.endsWith(".sum") || name.endsWith(".txt.asc"))) {
                        checksumAsset = a;
                        Musicxcst.LOGGER.info("Found checksum asset: {}", a.name);
                        break;
                    }
                }
                if (checksumAsset == null) {
                    for (Asset a : release.assets) {
                        if (a == null || a.name == null) continue;
                        if (a.name.toLowerCase(Locale.ROOT).contains("sha256")) {
                            checksumAsset = a;
                            Musicxcst.LOGGER.info("Found checksum asset (fallback): {}", a.name);
                            break;
                        }
                    }
                }
                if (checksumAsset == null) {
                    Musicxcst.LOGGER.warn("No checksum asset found in FFmpeg release {}", release.tag_name);
                    return Optional.empty();
                }
                
                Musicxcst.LOGGER.info("Downloading checksum file: {}", checksumAsset.browser_download_url);
                String checksumText = downloadText(checksumAsset.browser_download_url);
                if (checksumText == null) {
                    Musicxcst.LOGGER.warn("Failed to download checksum file");
                    return Optional.empty();
                }
                
                String expectedSha = parseChecksumForFile(checksumText, selected.name);
                if (expectedSha == null || !isValidSha(expectedSha)) {
                    Musicxcst.LOGGER.warn("Checksum for {} not found or invalid in release {}. Parsed: {}", selected.name, release.tag_name, expectedSha);
                    return Optional.empty();
                }
                Musicxcst.LOGGER.info("Checksum verified: {} = {}", selected.name, expectedSha);
                
                URI assetUri = URI.create(selected.browser_download_url);
                if (!"https".equalsIgnoreCase(assetUri.getScheme()) || !(assetUri.getHost().endsWith("github.com") || assetUri.getHost().endsWith("githubusercontent.com"))) {
                    Musicxcst.LOGGER.warn("Rejected non-GitHub asset URL: {}", selected.browser_download_url);
                    return Optional.empty();
                }
                
                Musicxcst.LOGGER.info("Download configuration ready for asset: {} from {}", selected.name, release.tag_name);
                return Optional.of(new ManagedDownload(selected.browser_download_url, expectedSha, release.tag_name, LGPL_LICENSE, selected.name));
            }
        } catch (Exception e) {
            Musicxcst.LOGGER.error("Failed to fetch FFmpeg release metadata", e);
            return Optional.empty();
        }
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

    private String downloadText(String urlStr) throws IOException {
        URI uri = URI.create(urlStr);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Checksum URLs must use HTTPS.");
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        conn.setRequestProperty("User-Agent", "MusicXCST");
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            Musicxcst.LOGGER.warn("Failed to download checksum file: HTTP {}", code);
            conn.disconnect();
            return null;
        }
        try (InputStream is = conn.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            conn.disconnect();
            return sb.toString();
        } catch (IOException e) {
            conn.disconnect();
            throw e;
        }
    }

    private static String parseChecksumForFile(String checksumsText, String filename) {
        if (checksumsText == null) return null;
        for (String line : checksumsText.split("\\r?\\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+");
            if (parts.length < 2) continue;
            String sha = parts[0];
            String name = parts[parts.length - 1];
            if (name.startsWith("*") || name.startsWith("./")) {
                name = name.replaceFirst("^[\\*\\.\\/]+", "");
            }
            if (name.equals(filename)) return sha;
        }
        return null;
    }

    private static boolean isValidSha(String s) {
        return s != null && s.matches("(?i)^[0-9a-f]{64}$");
    }

    private void downloadArchive(ManagedDownload download, Path archive, Consumer<String> progress) throws IOException {
        URI uri = URI.create(download.url());
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Managed FFmpeg downloads must use HTTPS.");
        }
        URL url = uri.toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(60_000);
        connection.setRequestProperty("User-Agent", "MusicXCST");
        connection.setInstanceFollowRedirects(true);
        int code = connection.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed to download FFmpeg archive: HTTP " + code);
        }
        long totalBytes = Math.max(0L, connection.getContentLengthLong());
        Path temp = archive.resolveSibling(archive.getFileName().toString() + ".part");
        byte[] buffer = new byte[64 * 1024];
        long startedAtNanos = System.nanoTime();
        long lastReportNanos = 0L;
        long downloaded = 0L;
        try (InputStream input = connection.getInputStream();
             OutputStream output = Files.newOutputStream(temp, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.StandardOpenOption.WRITE)) {
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
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        try {
            Files.move(temp, archive, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Fall back to non-atomic move if not supported
            Files.move(temp, archive, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
        Path binDir = targetRoot.resolve("bin").normalize();
        if (!binDir.startsWith(targetRoot)) {
            throw new IOException("Managed FFmpeg bin directory path escaped install root.");
        }
        Files.createDirectories(binDir);
        
        Musicxcst.LOGGER.info("Extracting FFmpeg from {} to {}", archive, binDir);
        boolean extracted = false;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName().replace('\\', '/');
                Path normalizedEntry = Path.of(entryName).normalize();
                if (entry.isDirectory() || entryName.startsWith("/") || entryName.contains("..") || normalizedEntry.isAbsolute()) {
                    continue;
                }
                if (entryName.contains("/bin/")) {
                    String relativeBinPath = entryName.substring(entryName.indexOf("/bin/") + 5);
                    if (relativeBinPath.isEmpty()) continue;
                    
                    Path targetFile = binDir.resolve(relativeBinPath).normalize();
                    if (!targetFile.startsWith(binDir)) {
                        Musicxcst.LOGGER.warn("Rejecting zip-slip path in bin/: {}", entryName);
                        continue;
                    }
                    
                    if (entry.isDirectory()) {
                        Files.createDirectories(targetFile);
                    } else {
                        Files.createDirectories(targetFile.getParent());
                        Files.copy(zip, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        if (relativeBinPath.equalsIgnoreCase(platform.executableName())) {
                            extracted = true;
                            Musicxcst.LOGGER.info("Found FFmpeg executable: {}", entryName);
                        }
                    }
                }
            }
        }
        if (!extracted) {
            throw new IOException("The FFmpeg archive did not contain the expected bin/" + platform.executableName() + " entry from " + download.url() + ".");
        }
        
        Path executable = binDir.resolve(platform.executableName()).normalize();
        Musicxcst.LOGGER.info("Extracted FFmpeg bin directory to: {}", binDir);
        return executable;
    }

    private VersionReport versionReport(Path executable) throws IOException {
        try {
            if (!Files.isRegularFile(executable)) {
                throw new IOException("FFmpeg executable does not exist at: " + executable);
            }
            if (!Files.isExecutable(executable)) {
                throw new IOException("FFmpeg executable is not executable: " + executable);
            }
            Musicxcst.LOGGER.info("Running ffmpeg -version from: {}", executable);
            Process process = new ProcessBuilder(executable.toString(), "-version").redirectErrorStream(true).start();
            boolean finished = process.waitFor(10L, TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                Musicxcst.LOGGER.warn("ffmpeg -version timed out. Output so far: {}", output);
                throw new IOException("ffmpeg -version timed out.");
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                Musicxcst.LOGGER.warn("ffmpeg -version failed with exit code {}. Output: {}", exitCode, output);
                throw new IOException("ffmpeg -version failed with exit code " + exitCode + ". Output: " + output);
            }
            String firstLine = output.lines().findFirst().orElse("ffmpeg version unknown");
            String configuration = output.lines()
                    .filter(line -> line.startsWith("configuration:"))
                    .findFirst()
                    .orElse("");
            Musicxcst.LOGGER.info("FFmpeg version: {}", firstLine);
            Musicxcst.LOGGER.info("FFmpeg configuration: {}", configuration);
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

    private static final class Release {
        public String tag_name;
        public Asset[] assets;
    }

    private static final class Asset {
        public String name;
        public String browser_download_url;
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
