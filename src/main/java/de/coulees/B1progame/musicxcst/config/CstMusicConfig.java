package de.coulees.B1progame.musicxcst.config;

import java.util.ArrayList;
import java.util.List;

public final class CstMusicConfig {
    public long maxFileSizeBytes = 25L * 1024L * 1024L;
    public long maxStoragePerPlayerBytes = 250L * 1024L * 1024L;
    public long maxTotalServerStorageBytes = 2L * 1024L * 1024L * 1024L;
    public List<String> allowedFileExtensions = new ArrayList<>(List.of("mp3", "mp4", "wav", "ogg", "flac", "m4a", "aac", "webm"));
    public String serverImportFolder = "music-import";
    public String serverNormalizedAudioFolder = "music-normalized";
    public String normalizedOutputFormat = "ogg";
    public String audioBitrate = "128k";
    public int sampleRate = 44100;
    public int previewCacheSeconds = 7;
    public int clientUploadBytesPerSecond = 256 * 1024;
    public boolean stereoEnabled = true;
    public boolean monoDownmix = false;
    public String ffmpegPath = "ffmpeg";
    public int playbackRadiusBlocks = 96;
    public int rangeCheckIntervalTicks = 20;
    public int fadeInMilliseconds = 250;
    public int fadeOutMilliseconds = 500;
    public long clientCacheSizeBytes = 1024L * 1024L * 1024L;
    public String absoluteImportSubfolder = "imported";
    public boolean allowSingleplayerAbsolutePaths = true;
    public boolean allowAdminAbsoluteServerPaths = false;
    public boolean softDeleteEnabled = true;
    public boolean allowFoundDiscsPlayback = true;
    public boolean ownerOnlyPlayback = false;
    public boolean adminBypass = true;
    public boolean debugLogging = false;
}
