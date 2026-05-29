package de.coulees.B1progame.musicxcst.config;

import java.util.ArrayList;
import java.util.List;

public final class CstMusicConfig {
    public long maxFileSizeBytes = 25L * 1024L * 1024L;
    public long maxStoragePerPlayerBytes = 250L * 1024L * 1024L;
    public long maxTotalServerStorageBytes = 2L * 1024L * 1024L * 1024L;
    public List<String> allowedFileExtensions = new ArrayList<>(List.of("mp3", "mp4", "wav", "ogg", "flac", "m4a", "aac", "webm"));
    public String serverImportFolder = "music-import";
    public String absoluteImportSubfolder = "imported";
    public boolean allowSingleplayerAbsolutePaths = true;
    public boolean allowAdminAbsoluteServerPaths = false;
    public boolean softDeleteEnabled = true;
    public boolean allowFoundDiscsPlayback = true;
    public boolean ownerOnlyPlayback = false;
    public boolean adminBypass = true;
    public boolean debugLogging = false;
}
