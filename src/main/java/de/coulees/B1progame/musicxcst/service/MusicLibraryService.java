package de.coulees.B1progame.musicxcst.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.chat.ChatFeedback;
import de.coulees.B1progame.musicxcst.config.CstMusicConfig;
import de.coulees.B1progame.musicxcst.data.DiscData;
import de.coulees.B1progame.musicxcst.data.MusicEntry;
import de.coulees.B1progame.musicxcst.data.MusicIndexFile;
import de.coulees.B1progame.musicxcst.data.MusicStatus;
import de.coulees.B1progame.musicxcst.data.StorageStats;
import de.coulees.B1progame.musicxcst.block.entity.CdWriterBlockEntity;
import de.coulees.B1progame.musicxcst.init.ModItems;
import de.coulees.B1progame.musicxcst.menu.CdWriterMenu;
import de.coulees.B1progame.musicxcst.media.FfmpegLocator;
import de.coulees.B1progame.musicxcst.media.ManagedFfmpegProvider;
import de.coulees.B1progame.musicxcst.media.MediaTranscoder;
import de.coulees.B1progame.musicxcst.network.AudioCacheWarmPayload;
import de.coulees.B1progame.musicxcst.network.AudioCachePrunePayload;
import de.coulees.B1progame.musicxcst.network.AudioChunkPayload;
import de.coulees.B1progame.musicxcst.network.AudioChunkRequestPayload;
import de.coulees.B1progame.musicxcst.network.ClientMusicUploadChunkPayload;
import de.coulees.B1progame.musicxcst.network.ClientMusicUploadStartPayload;
import de.coulees.B1progame.musicxcst.network.CdWriterDonePayload;
import de.coulees.B1progame.musicxcst.network.CdWriterWritePayload;
import de.coulees.B1progame.musicxcst.network.FfmpegSetupStatusPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxSettingsOpenPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxSettingsUpdatePayload;
import de.coulees.B1progame.musicxcst.network.JukeboxStartPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxStopPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxVolumeUpdatePayload;
import de.coulees.B1progame.musicxcst.network.MusicLimitConfirmPayload;
import de.coulees.B1progame.musicxcst.network.MusicLimitConfirmResponsePayload;
import de.coulees.B1progame.musicxcst.network.UploadedMusicListPayload;
import de.coulees.B1progame.musicxcst.service.audio.AudioChunkDownloadManager;
import de.coulees.B1progame.musicxcst.service.audio.PlaybackRangeTracker;
import de.coulees.B1progame.musicxcst.service.audio.PlaybackSessionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class MusicLibraryService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Map<String, MusicEntry> entries = new LinkedHashMap<>();
    private final PlaybackSessionManager playbackSessions = new PlaybackSessionManager();
    private final PlaybackRangeTracker playbackRange = new PlaybackRangeTracker();
    private final Map<BlockPos, Boolean> jukeboxLooping = new LinkedHashMap<>();
    private final Map<BlockPos, Integer> jukeboxVolumes = new LinkedHashMap<>();
    private final Map<UUID, Long> playerAutoDownloadIntervals = new LinkedHashMap<>();
    private final Map<UUID, Long> nextPlayerAutoDownloadTick = new LinkedHashMap<>();
    private final Map<String, PendingClientUpload> pendingClientUploads = new LinkedHashMap<>();
    private final Map<UUID, PendingLimitConfirmation> pendingLimitConfirmations = new LinkedHashMap<>();
    private final FfmpegLocator ffmpegLocator = new FfmpegLocator();
    private final ManagedFfmpegProvider managedFfmpegProvider = new ManagedFfmpegProvider();
    private final MediaTranscoder mediaTranscoder = new MediaTranscoder();
    private MinecraftServer server;
    private CstMusicConfig config = new CstMusicConfig();
    private Path configPath;
    private Path indexPath;
    private Path jukeboxSettingsPath;
    private Path importRoot;
    private Path normalizedRoot;
    private volatile boolean managedFfmpegDownloadRunning;
    private volatile String managedFfmpegDownloadStatus = "No managed FFmpeg download is running.";

    public void onServerStarted(MinecraftServer server) {
        this.server = server;
        initializePaths(server);
        loadConfig();
        loadIndex();
        loadJukeboxSettings();
        refreshStatuses();
        saveIndex();
        syncAllPlayers();
    }

    public void onServerStopping(MinecraftServer server) {
        saveJukeboxSettings();
        playbackSessions.clear();
        jukeboxLooping.clear();
        jukeboxVolumes.clear();
        playerAutoDownloadIntervals.clear();
        nextPlayerAutoDownloadTick.clear();
        pendingClientUploads.clear();
        pendingLimitConfirmations.clear();
        saveConfig();
        saveIndex();
        this.server = null;
    }

    public void onPlayerDisconnected(ServerPlayer player) {
        playbackSessions.unmarkListeningEverywhere(player.getUUID());
        nextPlayerAutoDownloadTick.remove(player.getUUID());
        pendingClientUploads.values().removeIf(upload -> upload.ownerUuid().equals(player.getUUID()));
        pendingLimitConfirmations.remove(player.getUUID());
    }

    public void onServerTick(MinecraftServer server) {
        if (this.server == null) {
            return;
        }

        int rangeInterval = Math.max(1, config.rangeCheckIntervalTicks);
        if (server.getTickCount() % rangeInterval == 0) {
            refreshStatuses();
            syncJukeboxPlaybackSessions();
        }

        if (server.getTickCount() % 100 == 0) {
            syncAllPlayers();
        }

        warmCachesAutomatically(server);
    }

    public String reload() {
        ensureServer();
        loadConfig();
        loadIndex();
        loadJukeboxSettings();
        refreshStatuses();
        saveIndex();
        syncAllPlayers();
        return "musicXCST config and metadata reloaded.";
    }

    public String repairIndex() {
        ensureServer();
        refreshStatuses();
        saveIndex();
        syncAllPlayers();
        return "musicXCST metadata index repaired. Entries: " + entries.size();
    }

    public String ffmpegStatus() {
        ensureServer();
        return managedFfmpegProvider.status(server.getServerDirectory(), config, ffmpegLocator);
    }

    public String setFfmpegPath(String path) {
        ensureServer();
        String cleanedPath = stripWrappingQuotes(path == null ? "" : path.trim());
        if (cleanedPath.isBlank()) {
            throw new IllegalArgumentException("FFmpeg path cannot be blank.");
        }
        config.ffmpegMode = "path";
        config.ffmpegPath = cleanedPath;
        saveConfig();
        return ffmpegLocator.locate(server.getServerDirectory(), config).isPresent()
                ? "FFmpeg path saved and verified."
                : "FFmpeg path saved, but it could not be verified yet.";
    }

    public String resetManagedFfmpeg() {
        ensureServer();
        managedFfmpegProvider.reset(server.getServerDirectory());
        config.ffmpegManagedDownloadAllowed = false;
        config.ffmpegManagedSourceUrl = "";
        config.ffmpegManagedVersion = "";
        config.ffmpegManagedSha256 = "";
        config.ffmpegManagedLicense = "";
        if ("managed".equals(FfmpegLocator.normalizedMode(config))) {
            config.ffmpegMode = "system";
        }
        saveConfig();
        return "Managed FFmpeg files were removed and ffmpegMode is now " + config.ffmpegMode + ".";
    }

    public void downloadManagedFfmpeg(CommandSourceStack source) {
        ensureServer();
        Musicxcst.LOGGER.info("Managed FFmpeg download requested from admin command by '{}'.", source.getTextName());
        boolean started = startManagedFfmpegDownload(
                "command:" + source.getTextName(),
                message -> Musicxcst.LOGGER.info("Managed FFmpeg setup: {}", message),
                (success, message) -> server.execute(() -> {
                    if (success) {
                        source.sendSuccess(() -> ChatFeedback.success(message), true);
                    } else {
                        source.sendFailure(ChatFeedback.error(message));
                    }
                })
        );
        if (started) {
            source.sendSuccess(() -> ChatFeedback.progress("Starting managed FFmpeg download after explicit admin confirmation. This may take a while."), false);
        } else {
            source.sendFailure(ChatFeedback.warning("Managed FFmpeg setup is already running: " + managedFfmpegDownloadStatus));
        }
    }

    public void requestManagedFfmpegDownload(ServerPlayer player) {
        ensureServer();
        String playerName = player.getName().getString();
        Musicxcst.LOGGER.info("GUI managed FFmpeg download packet received from '{}'.", playerName);
        if (!isAdmin(player.createCommandSourceStack())) {
            Musicxcst.LOGGER.warn("GUI managed FFmpeg download denied for '{}' because they lack admin permission.", playerName);
            sendFfmpegSetupStatus(player, "Missing permission. Use /cstmusic admin ffmpeg download confirm as an admin.", true, false);
            return;
        }

        boolean started = startManagedFfmpegDownload(
                "gui:" + playerName,
                message -> server.execute(() -> sendFfmpegSetupStatus(player, message, false, false)),
                (success, message) -> server.execute(() -> sendFfmpegSetupStatus(player, message, true, success))
        );
        if (started) {
            sendFfmpegSetupStatus(player, "Starting managed FFmpeg download after explicit admin confirmation. This may take a while.", false, false);
        } else {
            sendFfmpegSetupStatus(player, "Managed FFmpeg setup is already running: " + managedFfmpegDownloadStatus, false, false);
        }
    }

    private boolean startManagedFfmpegDownload(String requester, Consumer<String> progress, BiConsumer<Boolean, String> completion) {
        synchronized (this) {
            if (managedFfmpegDownloadRunning) {
                Musicxcst.LOGGER.info("Managed FFmpeg setup request from '{}' joined existing task: {}", requester, managedFfmpegDownloadStatus);
                return false;
            }
            managedFfmpegDownloadRunning = true;
            managedFfmpegDownloadStatus = "Starting managed FFmpeg download.";
        }
        Musicxcst.LOGGER.info("Shared managed FFmpeg download method called by '{}'.", requester);
        Thread thread = new Thread(() -> {
            try {
                ManagedFfmpegProvider.ManagedInstallResult result = managedFfmpegProvider.install(
                        server.getServerDirectory(),
                        config,
                        message -> {
                            managedFfmpegDownloadStatus = message;
                            Musicxcst.LOGGER.info("Managed FFmpeg setup: {}", message);
                            progress.accept(message);
                        }
                );
                config.ffmpegMode = "managed";
                saveConfig();
                String locatorResult = ffmpegLocator.locate(server.getServerDirectory(), config).orElse("unavailable after install");
                String successMessage = "Managed FFmpeg installed: " + result.versionLine();
                managedFfmpegDownloadStatus = successMessage;
                Musicxcst.LOGGER.info("Managed FFmpeg config saved; locator result after install: {}", locatorResult);
                completion.accept(true, successMessage);
            } catch (IllegalArgumentException exception) {
                String failureMessage = "Managed FFmpeg setup failed: " + exception.getMessage();
                managedFfmpegDownloadStatus = failureMessage;
                Musicxcst.LOGGER.warn("Managed FFmpeg setup failed for '{}': {}", requester, exception.getMessage());
                completion.accept(false, failureMessage);
            } finally {
                managedFfmpegDownloadRunning = false;
            }
        }, "musicxcst-server-managed-ffmpeg-download");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    private void sendFfmpegSetupStatus(ServerPlayer player, String message, boolean done, boolean success) {
        Musicxcst.LOGGER.info("Sending managed FFmpeg GUI status to '{}': done={}, success={}, message={}", player.getName().getString(), done, success, message);
        if (ServerPlayNetworking.canSend(player, FfmpegSetupStatusPayload.TYPE)) {
            ServerPlayNetworking.send(player, new FfmpegSetupStatusPayload(message, done, success));
        } else {
            player.sendSystemMessage(success ? ChatFeedback.success(message) : done ? ChatFeedback.error(message) : ChatFeedback.progress(message));
        }
    }

    public String createDiscForPlayer(CommandSourceStack source, ServerPlayer player, String requestedName, String requestedColor, String requestedLocation) {
        ensureServer();
        String sanitizedName = sanitizeSongName(requestedName);
        String normalizedColor = normalizeHexColor(requestedColor);
        if (normalizedColor == null) {
            throw new IllegalArgumentException("Invalid hex color. Use RRGGBB or #RRGGBB.");
        }

        ItemStack selected = player.getInventory().getSelectedItem();
        if (selected.getItem() != ModItems.BLUEPRINT_CD) {
            throw new IllegalArgumentException("Hold a Blueprint CD in your selected slot first.");
        }
        enforcePlayerFileLimitOrThrow(player);

        String musicId = UUID.randomUUID().toString().replace("-", "");
        ImportedMusicFile importedFile = resolveMusicFile(source, player, requestedLocation, musicId);
        Path resolved = importedFile.path();
        long fileSize = safeFileSize(resolved);
        validateFile(resolved, fileSize);
        validateQuota(player.getUUID(), fileSize);
        NormalizedAudio normalizedAudio = normalizeAudio(player, resolved, musicId, fileSize);

        MusicEntry entry = new MusicEntry();
        entry.musicId = musicId;
        entry.displayName = sanitizedName;
        entry.originalFileName = importedFile.originalFileName();
        entry.safeRelativePath = importedFile.safeRelativePath();
        entry.ownerUuid = player.getUUID().toString();
        entry.ownerName = player.getName().getString();
        entry.createdAtEpochMillis = Instant.now().toEpochMilli();
        entry.fileSizeBytes = fileSize;
        entry.sha256 = sha256(resolved);
        entry.normalizedRelativePath = normalizedAudio.safeRelativePath();
        entry.normalizedSizeBytes = normalizedAudio.sizeBytes();
        entry.normalizedSha256 = normalizedAudio.sha256();
        entry.durationMillis = probeAudioDurationMillis(resolvePlayableOggWithoutStatus(entry));
        validateMusicDuration(entry.durationMillis);
        entry.status = MusicStatus.ACTIVE;
        entry.designId = DiscData.encodeDesignId(DiscData.defaultDesign());
        NormalizedAudio previewAudio = createPreviewAudio(player, entry);
        entry.previewRelativePath = previewAudio.safeRelativePath();
        entry.previewSizeBytes = previewAudio.sizeBytes();
        entry.previewSha256 = previewAudio.sha256();
        entry.normalizedFormat = config.normalizedOutputFormat;
        entry.normalizedSampleRate = config.sampleRate;
        entry.normalizedBitrate = config.audioBitrate;
        entry.hexColor = normalizedColor;
        entry.schemaVersion = Musicxcst.DISC_SCHEMA_VERSION;
        entries.put(entry.musicId, entry);
        saveIndex();

        DiscData data = DiscData.fromEntry(entry);
        if (selected.getCount() == 1) {
            DiscData.writeToStack(selected, data);
        } else {
            selected.shrink(1);
            ItemStack written = new ItemStack(ModItems.BLUEPRINT_CD);
            DiscData.writeToStack(written, data);
            if (!player.getInventory().add(written)) {
                player.drop(written, false);
            }
        }

        syncPlayerInventory(player);
        warmPreviewCacheForAllPlayers(entry);
        return "Created Blueprint CD '" + sanitizedName + "' with music ID " + entry.musicId + ".";
    }

    public String createDiscFromUploadedFile(ServerPlayer player, String requestedName, String requestedColor, String uploadedFileName) {
        ensureServer();
        return createDiscFromUploadedFile(player, requestedName, requestedColor, uploadedFileName, DiscData.defaultDesign(), player.getInventory().getSelectedItem(), null, false);
    }

    private String createDiscFromUploadedFile(ServerPlayer player, String requestedName, String requestedColor, String uploadedFileName, int[] designPixels, ItemStack selected, @Nullable Runnable inventoryChanged, boolean skipPlayerFileLimit) {
        if (!skipPlayerFileLimit) {
            enforcePlayerFileLimitOrThrow(player);
        }
        Musicxcst.LOGGER.debug("CD Writer server creating disc with design {}", DiscData.designDebugSummary(designPixels));
        String displayName = sanitizeSongName(requestedName);
        String normalizedColor = normalizeHexColor(requestedColor);
        if (normalizedColor == null) {
            throw new IllegalArgumentException("Invalid hex color. Use RRGGBB or #RRGGBB.");
        }

        if (selected.getItem() != ModItems.BLUEPRINT_CD) {
            throw new IllegalArgumentException("Place a Blueprint CD in the CD Writer slot first.");
        }

        Path source = uploadedMusicPath(player, uploadedFileName);
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("Uploaded song not found. Upload it first with /cstmusic upload <name> <path>.");
        }

        String musicId = UUID.randomUUID().toString().replace("-", "");
        long fileSize = safeFileSize(source);
        validateFile(source, fileSize);
        validateQuota(player.getUUID(), fileSize);
        NormalizedAudio normalizedAudio = normalizeAudio(player, source, musicId, fileSize);

        MusicEntry entry = new MusicEntry();
        entry.musicId = musicId;
        entry.displayName = displayName;
        entry.originalFileName = source.getFileName().toString();
        entry.safeRelativePath = "";
        entry.ownerUuid = player.getUUID().toString();
        entry.ownerName = player.getName().getString();
        entry.createdAtEpochMillis = Instant.now().toEpochMilli();
        entry.fileSizeBytes = normalizedAudio.sizeBytes();
        entry.sha256 = normalizedAudio.sha256();
        entry.normalizedRelativePath = normalizedAudio.safeRelativePath();
        entry.normalizedSizeBytes = normalizedAudio.sizeBytes();
        entry.normalizedSha256 = normalizedAudio.sha256();
        // Prefer client-provided duration if available (written as a sidecar file next to uploaded OGG)
        long durationMillis = 0L;
        try {
            Path uploaded = source;
            Path meta = uploaded.resolveSibling(uploaded.getFileName().toString() + ".duration");
            if (Files.isRegularFile(meta)) {
                String s = Files.readString(meta, java.nio.charset.StandardCharsets.UTF_8).trim();
                try {
                    durationMillis = Long.parseLong(s);
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        if (durationMillis <= 0L) {
            durationMillis = probeAudioDurationMillis(resolvePlayableOggWithoutStatus(entry));
        }
        entry.durationMillis = durationMillis;
        validateMusicDuration(entry.durationMillis);
        entry.status = MusicStatus.ACTIVE;
        entry.designId = DiscData.encodeDesignId(DiscData.sanitizeDesign(designPixels));
        NormalizedAudio previewAudio = createPreviewAudio(player, entry);
        entry.previewRelativePath = previewAudio.safeRelativePath();
        entry.previewSizeBytes = previewAudio.sizeBytes();
        entry.previewSha256 = previewAudio.sha256();
        entry.normalizedFormat = config.normalizedOutputFormat;
        entry.normalizedSampleRate = config.sampleRate;
        entry.normalizedBitrate = config.audioBitrate;
        entry.hexColor = normalizedColor;
        entry.schemaVersion = Musicxcst.DISC_SCHEMA_VERSION;
        entries.put(entry.musicId, entry);
        saveIndex();

        DiscData data = DiscData.fromEntry(entry);
        data.designPixels = DiscData.sanitizeDesign(designPixels);
        data.designId = DiscData.encodeDesignId(data.designPixels);
        if (selected.getCount() == 1) {
            DiscData.writeToStack(selected, data);
            DiscData writtenData = DiscData.fromStack(selected);
            Musicxcst.LOGGER.debug("CD Writer server selected stack after write {}", writtenData == null ? "missing disc data" : DiscData.designDebugSummary(writtenData.designPixels));
        } else {
            selected.shrink(1);
            ItemStack written = new ItemStack(ModItems.BLUEPRINT_CD);
            DiscData.writeToStack(written, data);
            DiscData writtenData = DiscData.fromStack(written);
            Musicxcst.LOGGER.debug("CD Writer server new stack after write {}", writtenData == null ? "missing disc data" : DiscData.designDebugSummary(writtenData.designPixels));
            if (!player.getInventory().add(written)) {
                player.drop(written, false);
            }
        }
        if (inventoryChanged != null) {
            inventoryChanged.run();
        }

        syncPlayerInventory(player);
        warmPreviewCacheForAllPlayers(entry);
        return "Created Blueprint CD '" + displayName + "' from uploaded file " + source.getFileName() + ".";
    }

    public List<MusicEntry> listEntriesForPlayer(ServerPlayer player) {
        return entries.values().stream()
                .filter(entry -> Objects.equals(entry.ownerUuid, player.getUUID().toString()))
                .sorted(Comparator.comparingLong(entry -> -entry.createdAtEpochMillis))
                .toList();
    }

    public List<MusicEntry> listAllEntries() {
        return entries.values().stream()
                .sorted(Comparator.comparingLong(entry -> -entry.createdAtEpochMillis))
                .toList();
    }

    public List<String> listUploadedFilesForPlayer(ServerPlayer player) {
        Path folder = uploadFolder(player);
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (var paths = Files.list(folder)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(fileName -> !fileName.endsWith(".upload"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            Musicxcst.LOGGER.debug("Failed to list uploaded music files for {}: {}", player.getName().getString(), exception.getMessage());
            return List.of();
        }
    }

    public void sendUploadedMusicList(ServerPlayer player) {
        ensureServer();
        if (ServerPlayNetworking.canSend(player, UploadedMusicListPayload.TYPE)) {
            ServerPlayNetworking.send(player, new UploadedMusicListPayload(listUploadedFilesForPlayer(player)));
        }
    }

    public MusicEntry requirePlayerVisibleEntry(ServerPlayer player, String musicId) {
        MusicEntry entry = requirePlayerEntryReference(player, musicId);
        if (!Objects.equals(entry.ownerUuid, player.getUUID().toString())) {
            throw new IllegalArgumentException("You can only access your own music entries.");
        }
        return entry;
    }

    public MusicEntry requireAdminVisibleEntry(CommandSourceStack source, String musicId) {
        if (!isAdmin(source)) {
            throw new IllegalArgumentException("Admin permissions required.");
        }
        return requireEntry(musicId);
    }

    public String deleteEntryAsOwner(ServerPlayer player, String musicId) {
        MusicEntry entry = requirePlayerVisibleEntry(player, musicId);
        markDeleted(entry);
        return "Deleted music entry " + displayEntryReference(entry) + ".";
    }

    public String deleteEntryAsAdmin(CommandSourceStack source, String musicId) {
        if (!isAdmin(source)) {
            throw new IllegalArgumentException("Admin permissions required.");
        }
        MusicEntry entry = requireEntry(musicId);
        markDeleted(entry);
        return "Deleted music entry " + displayEntryReference(entry) + ".";
    }

    public StorageStats getPlayerStorage(ServerPlayer player) {
        StorageStats stats = new StorageStats();
        stats.quotaBytes = config.maxStoragePerPlayerBytes;
        for (MusicEntry entry : entries.values()) {
            if (!Objects.equals(entry.ownerUuid, player.getUUID().toString())) {
                continue;
            }
            accumulate(stats, entry);
        }
        return stats;
    }

    public String describePlayerFileLimit(ServerPlayer player) {
        return describePlayerFileLimit(player, false);
    }

    public String describePlayerFileLimit(ServerPlayer player, boolean isAdmin) {
        StorageStats stats = getPlayerStorage(player);
        int activeFiles = stats.activeCount;
        if (!config.maxMusicFilesPerPlayerEnabled) {
            if (isAdmin) {
                return activeFiles + " active file(s), limit disabled. Set maxMusicFilesPerPlayerEnabled=true to enforce maxMusicFilesPerPlayer.";
            } else {
                return activeFiles + " active file(s), limit disabled.";
            }
        }

        int limit = playerFileLimit();
        return activeFiles + " / " + limit + " active file(s), mode=" + playerLimitMode() + ".";
    }

    public StorageStats getServerStorage() {
        StorageStats stats = new StorageStats();
        stats.quotaBytes = config.maxTotalServerStorageBytes;
        for (MusicEntry entry : entries.values()) {
            accumulate(stats, entry);
        }
        return stats;
    }

    public String playEntryForAdmin(CommandSourceStack source, ServerPlayer player, String musicId) {
        MusicEntry entry = requireAdminVisibleEntry(source, musicId);
        BlockPos pos = player.blockPosition();
        JukeboxStartPayload payload = startPayload(entry, pos, false);
        if (!ServerPlayNetworking.canSend(player, JukeboxStartPayload.TYPE)) {
            throw new IllegalArgumentException("This client cannot receive musicXCST playback packets. Restart the client with the latest mod jar.");
        }
        ServerPlayNetworking.send(player, payload);
        return "Playing '" + entry.displayName + "' for " + player.getName().getString() + ".";
    }

    public String warmPlayerCache(ServerPlayer player) {
        ensureServer();
        int sent = sendCacheWarmPayloads(player, activeEntries());
        return sent == 0
                ? "No active music entries are available to download."
                : "Started caching " + sent + " song(s).";
    }

    public String setPlayerAutoDownload(ServerPlayer player, int intervalMinutes) {
        ensureServer();
        long intervalTicks = Math.max(1L, intervalMinutes) * 60L * 20L;
        playerAutoDownloadIntervals.put(player.getUUID(), intervalTicks);
        nextPlayerAutoDownloadTick.put(player.getUUID(), (long) server.getTickCount());
        return "Automatic music downloads enabled every " + describeMinutes(intervalMinutes) + ".";
    }

    public String disablePlayerAutoDownload(ServerPlayer player) {
        ensureServer();
        playerAutoDownloadIntervals.remove(player.getUUID());
        nextPlayerAutoDownloadTick.remove(player.getUUID());
        return "Automatic music downloads disabled.";
    }

    public void openJukeboxSettings(ServerPlayer player, BlockPos pos) {
        ensureServer();
        if (!player.level().getBlockState(pos).is(Blocks.JUKEBOX) || player.blockPosition().distSqr(pos) > 64.0D) {
            return;
        }
        if (ServerPlayNetworking.canSend(player, JukeboxSettingsOpenPayload.TYPE)) {
            ServerPlayNetworking.send(player, new JukeboxSettingsOpenPayload(pos.immutable(), isJukeboxLooping(pos), jukeboxVolume(pos)));
        }
    }

    public void openCdWriter(ServerPlayer player, BlockPos pos) {
        ensureServer();
        if (!(player.level().getBlockEntity(pos) instanceof CdWriterBlockEntity blockEntity) || player.blockPosition().distSqr(pos) > 64.0D) {
            return;
        }
        player.openMenu(new ExtendedMenuProvider<BlockPos>() {
            @Override
            public BlockPos getScreenOpeningData(ServerPlayer serverPlayer) {
                return pos.immutable();
            }

            @Override
            public Component getDisplayName() {
                return Component.literal("CD Writer");
            }

            @Override
            public CdWriterMenu createMenu(int containerId, Inventory inventory, Player menuPlayer) {
                return new CdWriterMenu(containerId, inventory, blockEntity, ContainerLevelAccess.create(menuPlayer.level(), pos), pos);
            }
        });
    }

    public void writeCdFromUploadedFile(ServerPlayer player, CdWriterWritePayload payload) {
        writeCdFromUploadedFile(player, payload, false);
    }

    private void writeCdFromUploadedFile(ServerPlayer player, CdWriterWritePayload payload, boolean skipPlayerFileLimit) {
        ensureServer();
        BlockPos pos = payload.pos().immutable();
        if (!(player.level().getBlockEntity(pos) instanceof CdWriterBlockEntity blockEntity) || player.blockPosition().distSqr(pos) > 64.0D) {
            player.sendSystemMessage(ChatFeedback.error("CD Writer is no longer available."));
            finishCdWriterClient(player, pos);
            return;
        }
        try {
            if (!(player.containerMenu instanceof CdWriterMenu menu) || !menu.pos().equals(pos)) {
                player.sendSystemMessage(ChatFeedback.warning("Open the CD Writer before writing a disc."));
                return;
            }
            if (menu.hasOutput()) {
                player.sendSystemMessage(ChatFeedback.warning("Take the finished CD out of the CD Writer first."));
                return;
            }
            if (!skipPlayerFileLimit && !preparePlayerFileLimitForCdWriter(player, payload)) {
                return;
            }
            Musicxcst.LOGGER.debug("CD Writer server received payload design {}", DiscData.designDebugSummary(payload.designPixels()));
            blockEntity.setConverting(true);
            menu.setConverting(true);
            String result = createDiscFromUploadedFile(player, payload.discName(), payload.hexColor(), payload.uploadedFileName(), payload.designPixels(), menu.inputStack(), menu::inputChanged, true);
            menu.moveInputToOutput();
            DiscData outputData = DiscData.fromStack(menu.getSlot(CdWriterMenu.OUTPUT_SLOT).getItem());
            Musicxcst.LOGGER.debug("CD Writer server output slot after move {}", outputData == null ? "missing disc data" : DiscData.designDebugSummary(outputData.designPixels));
            player.sendSystemMessage(ChatFeedback.status(result));
        } catch (IllegalArgumentException exception) {
            player.sendSystemMessage(ChatFeedback.error("CD Writer error: " + exception.getMessage()));
        } finally {
            blockEntity.setConverting(false);
            if (player.containerMenu instanceof CdWriterMenu menu && menu.pos().equals(pos)) {
                menu.setConverting(false);
            }
            finishCdWriterClient(player, pos);
        }
    }

    private void finishCdWriterClient(ServerPlayer player, BlockPos pos) {
        if (ServerPlayNetworking.canSend(player, CdWriterDonePayload.TYPE)) {
            ServerPlayNetworking.send(player, new CdWriterDonePayload(pos));
        }
    }

    public void handleMusicLimitConfirmation(ServerPlayer player, MusicLimitConfirmResponsePayload payload) {
        ensureServer();
        PendingLimitConfirmation pending = pendingLimitConfirmations.remove(player.getUUID());
        if (pending == null || !pending.payload().pos().equals(payload.pos())) {
            player.sendSystemMessage(ChatFeedback.warning("No pending music limit confirmation."));
            return;
        }
        if (!payload.confirmed()) {
            player.sendSystemMessage(ChatFeedback.warning("Music upload cancelled."));
            finishCdWriterClient(player, payload.pos());
            return;
        }

        try {
            deleteOldestOwnedEntryForLimit(player);
            player.sendSystemMessage(ChatFeedback.warning("Your oldest uploaded track was deleted to make space."));
            writeCdFromUploadedFile(player, pending.payload(), true);
        } catch (IllegalArgumentException exception) {
            player.sendSystemMessage(ChatFeedback.error("CD Writer error: " + exception.getMessage()));
            finishCdWriterClient(player, payload.pos());
        }
    }

    public void updateJukeboxSettings(ServerPlayer player, JukeboxSettingsUpdatePayload payload) {
        ensureServer();
        BlockPos pos = payload.pos().immutable();
        if (!player.level().getBlockState(pos).is(Blocks.JUKEBOX) || player.blockPosition().distSqr(pos) > 64.0D) {
            return;
        }
        jukeboxLooping.put(pos, payload.looping());
        jukeboxVolumes.put(pos, clampVolume(payload.volumePercent()));
        saveJukeboxSettings();
        sendJukeboxVolumeUpdate(player.level(), pos, clampVolume(payload.volumePercent()));
        if (player.level().getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox) {
            startJukeboxPlayback(player.level(), pos, jukebox.getTheItem());
        }
    }

    public void startClientUpload(ServerPlayer player, ClientMusicUploadStartPayload payload) {
        ensureServer();
        if (config.maxMusicFilesPerPlayerEnabled && isPlayerFileLimitReached(player)) {
            String mode = playerLimitMode();
            if ("block_new_upload".equals(mode)) {
                player.sendSystemMessage(ChatFeedback.warning("You reached the limit of " + playerFileLimit() + " music files. Delete an old upload before creating a new one."));
                return;
            }
            if ("auto_delete_oldest".equals(mode)) {
                deleteOldestOwnedEntryForLimit(player);
                player.sendSystemMessage(ChatFeedback.warning("You reached the limit of " + playerFileLimit() + " music files. Your oldest uploaded track was deleted to make space."));
            }
        }
        if (payload.sizeBytes() <= 0L || payload.sizeBytes() > config.maxFileSizeBytes) {
            throw new IllegalArgumentException("Upload file size is not allowed.");
        }
        String uploadId = sanitizeUploadId(payload.uploadId());
        String sourceFileName = sanitizeFileName(payload.fileName());
        String extension = extension(sourceFileName);
        if (!"ogg".equals(extension)) {
            throw new IllegalArgumentException("Client uploads must be normalized .ogg audio.");
        }
        String uploadName = sanitizeSongName(payload.uploadName());
        String fileName = normalizedUploadedFileName(sourceFileName);
        Path folder = uploadFolder(player);
        Path temp = folder.resolve(fileName + ".upload").normalize();
        if (!temp.startsWith(importRoot)) {
            throw new IllegalArgumentException("Upload path escapes the import root.");
        }

        try {
            Files.createDirectories(folder);
            Files.deleteIfExists(temp);
            pendingClientUploads.put(uploadId, new PendingClientUpload(
                    uploadId,
                    player.getUUID(),
                    uploadName,
                    fileName,
                    temp,
                    payload.sizeBytes(),
                    payload.durationMillis(),
                    0L,
                    System.currentTimeMillis()
            ));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to prepare music upload.", exception);
        }
    }

    public void receiveClientUploadChunk(ServerPlayer player, ClientMusicUploadChunkPayload payload) {
        ensureServer();
        String uploadId = sanitizeUploadId(payload.uploadId());
        PendingClientUpload upload = pendingClientUploads.get(uploadId);
        if (upload == null || !upload.ownerUuid().equals(player.getUUID())) {
            return;
        }
        if (payload.offset() != upload.receivedBytes() || payload.data().length == 0) {
            pendingClientUploads.remove(uploadId);
            deleteQuietly(upload.tempPath());
            throw new IllegalArgumentException("Music upload chunk order is invalid.");
        }
        if (payload.data().length > Math.max(16 * 1024, config.clientUploadBytesPerSecond)) {
            pendingClientUploads.remove(uploadId);
            deleteQuietly(upload.tempPath());
            throw new IllegalArgumentException("Music upload chunk exceeds the configured upload rate.");
        }

        try {
            Files.write(upload.tempPath(), payload.data(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException exception) {
            pendingClientUploads.remove(uploadId);
            deleteQuietly(upload.tempPath());
            throw new IllegalArgumentException("Failed to write music upload chunk.", exception);
        }

        long received = upload.receivedBytes() + payload.data().length;
        PendingClientUpload updated = upload.withReceivedBytes(received);
        if (!payload.last()) {
            pendingClientUploads.put(uploadId, updated);
            return;
        }

        pendingClientUploads.remove(uploadId);
        if (received != upload.sizeBytes()) {
            deleteQuietly(upload.tempPath());
            throw new IllegalArgumentException("Music upload size does not match metadata.");
        }
        Path finalPath = uploadFolder(player).resolve(updated.originalFileName()).normalize();
        if (!finalPath.startsWith(importRoot)) {
            deleteQuietly(updated.tempPath());
            throw new IllegalArgumentException("Upload destination escapes the import root.");
        }
        try {
            Files.move(updated.tempPath(), finalPath, StandardCopyOption.REPLACE_EXISTING);
            // Persist client-reported duration if provided
            try {
                if (upload.durationMillis() > 0L) {
                    Path meta = finalPath.resolveSibling(finalPath.getFileName().toString() + ".duration");
                    Files.writeString(meta, Long.toString(upload.durationMillis()), java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                }
            } catch (IOException ignored) {
            }
            player.sendSystemMessage(ChatFeedback.success("Uploaded music file '" + updated.displayName() + "'. Use the CD Writer Print button to write it to a disc."));
        } catch (IOException exception) {
            deleteQuietly(updated.tempPath());
            throw new IllegalArgumentException("Failed to finish music upload.", exception);
        }
    }

    public void startJukeboxPlayback(Level level, BlockPos pos, ItemStack stack) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (stack.getItem() != ModItems.BLUEPRINT_CD) {
            syncVanillaJukeboxControls(serverLevel, pos, stack);
            return;
        }

        refreshStatuses();
        DiscData discData = DiscData.fromStack(stack);
        if (discData == null || MusicStatus.isInvalidLike(discData.status)) {
            stopJukeboxPlayback(level, pos);
            return;
        }

        MusicEntry entry = entries.get(discData.musicId);
        if (entry == null || !MusicStatus.ACTIVE.equals(entry.status)) {
            invalidateAndPopJukeboxDisc(serverLevel, pos, stack, discData, entry);
            stopJukeboxPlayback(level, pos);
            return;
        }

        try {
            PlaybackSessionManager.PlaybackSession existing = playbackSessions.session(pos);
            long startedAtMillis = existing != null && Objects.equals(existing.musicId(), entry.musicId)
                    ? existing.startedAtMillis()
                    : System.currentTimeMillis();
            if (existing == null || !Objects.equals(existing.musicId(), entry.musicId)) {
                playbackSessions.start(new PlaybackSessionManager.PlaybackSession(
                        entry.musicId,
                        serverLevel.dimension(),
                        pos.immutable(),
                        config.playbackRadiusBlocks,
                        startedAtMillis,
                        Math.max(1L, entry.durationMillis)
                ));
            }
            JukeboxStartPayload payload = startPayload(entry, pos, true, startedAtMillis);
            int sent = 0;
            for (ServerPlayer player : PlayerLookup.around(serverLevel, Vec3.atCenterOf(pos), config.playbackRadiusBlocks)) {
                if (ServerPlayNetworking.canSend(player, JukeboxStartPayload.TYPE)) {
                    ServerPlayNetworking.send(player, payload);
                    playbackSessions.markListening(pos.immutable(), player.getUUID());
                    sent++;
                }
            }
            if (sent == 0) {
                Musicxcst.LOGGER.warn("Blueprint CD '{}' started, but no nearby clients could receive musicXCST playback packets.", entry.displayName);
            }
        } catch (IllegalArgumentException exception) {
            Musicxcst.LOGGER.warn("Blueprint CD '{}' cannot be played: {}", entry.displayName, exception.getMessage());
            stopJukeboxPlayback(level, pos);
        }
    }

    private void syncVanillaJukeboxControls(ServerLevel level, BlockPos pos, ItemStack stack) {
        playbackSessions.stop(pos);
        if (stack.isEmpty()) {
            return;
        }
        sendJukeboxVolumeUpdate(level, pos, jukeboxVolume(pos));
        if (!isJukeboxLooping(pos) || !(level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox)) {
            return;
        }
        if (!jukebox.getSongPlayer().isPlaying()) {
            Musicxcst.LOGGER.debug("Restarting vanilla jukebox song at {} because MusicXCST loop is enabled.", pos);
            jukebox.tryForcePlaySong();
        }
    }

    public boolean rejectBlueprintJukeboxInsert(Player player, ItemStack stack) {
        if (stack.getItem() != ModItems.BLUEPRINT_CD) {
            return false;
        }

        DiscData data = DiscData.fromStack(stack);
        if (data == null) {
            player.sendOverlayMessage(ChatFeedback.warning("Blank Blueprint CDs cannot be inserted into jukeboxes."));
            return true;
        }

        refreshStatuses();
        MusicEntry entry = entries.get(data.musicId);
        String status = entry == null ? MusicStatus.INVALID : entry.status;
        if (MusicStatus.ACTIVE.equals(status)) {
            data.status = status;
            data.hexColor = entry.hexColor;
            data.displayName = entry.displayName;
            DiscData.writeToStack(stack, data);
            return false;
        }

        if (MusicStatus.isInvalidLike(status)) {
            data.status = status;
            data.hexColor = "#C93A3A";
            data.displayName = entry == null ? data.displayName : entry.displayName;
            DiscData.writeToStack(stack, data);
            player.sendOverlayMessage(ChatFeedback.error("This Blueprint CD is invalid and cannot be inserted."));
            return true;
        }

        player.sendOverlayMessage(ChatFeedback.progress("This Blueprint CD is still converting. Try again when it is ready."));
        return true;
    }

    private void invalidateAndPopJukeboxDisc(ServerLevel level, BlockPos pos, ItemStack stack, DiscData data, MusicEntry entry) {
        data.status = entry == null ? MusicStatus.INVALID : entry.status;
        data.hexColor = "#C93A3A";
        data.displayName = entry == null ? data.displayName : entry.displayName;
        DiscData.writeToStack(stack, data);

        if (level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox) {
            jukebox.setSongItemWithoutPlaying(stack);
            jukebox.popOutTheItem();
        }
    }

    public void sendAudioChunk(ServerPlayer player, AudioChunkRequestPayload request) {
        ensureServer();
        MusicEntry entry = entries.get(request.musicId());
        if (entry == null || !MusicStatus.ACTIVE.equals(entry.status)) {
            return;
        }

        Path file = request.preview() ? resolvePreviewOgg(entry) : resolvePlayableOgg(entry);
        long totalSize = safeFileSize(file);
        long offset = Math.max(0L, request.offset());
        if (offset >= totalSize) {
            ServerPlayNetworking.send(player, new AudioChunkPayload(entry.musicId, totalSize, totalSize, checksumForPlayback(entry, file, request.preview()), new byte[0], true, request.preview()));
            return;
        }

        int maxBytes = new AudioChunkDownloadManager().clampRequestedBytes(request.maxBytes());
        int count = (int) Math.min(maxBytes, totalSize - offset);
        byte[] data;
        try (InputStream input = Files.newInputStream(file)) {
            input.skipNBytes(offset);
            data = input.readNBytes(count);
        } catch (IOException exception) {
            Musicxcst.LOGGER.warn("Failed to stream Blueprint CD audio chunk '{}': {}", entry.musicId, exception.getMessage());
            return;
        }

        boolean last = offset + data.length >= totalSize;
        ServerPlayNetworking.send(player, new AudioChunkPayload(entry.musicId, offset, totalSize, checksumForPlayback(entry, file, request.preview()), data, last, request.preview()));
    }

    private void warmCachesAutomatically(MinecraftServer server) {
        List<MusicEntry> activeEntries = activeEntries();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Long intervalTicks = playerAutoDownloadIntervals.get(player.getUUID());
            if (intervalTicks == null) {
                continue;
            }

            long nextTick = nextPlayerAutoDownloadTick.getOrDefault(player.getUUID(), 0L);
            if (server.getTickCount() < nextTick) {
                continue;
            }

            sendCacheWarmPayloads(player, activeEntries);
            nextPlayerAutoDownloadTick.put(player.getUUID(), server.getTickCount() + intervalTicks);
        }
    }

    private int sendCacheWarmPayloads(ServerPlayer player, List<MusicEntry> candidates) {
        if (!ServerPlayNetworking.canSend(player, AudioCacheWarmPayload.TYPE)) {
            return 0;
        }

        sendCachePrunePayload(player, candidates);
        int sent = 0;
        for (MusicEntry entry : candidates) {
            if (!MusicStatus.ACTIVE.equals(entry.status)) {
                continue;
            }
            try {
                ServerPlayNetworking.send(player, cacheWarmPayload(entry));
                sent++;
            } catch (IllegalArgumentException exception) {
                Musicxcst.LOGGER.debug("Skipping cache warm for '{}': {}", entry.musicId, exception.getMessage());
            }
        }
        return sent;
    }

    private void sendCachePrunePayload(ServerPlayer player, List<MusicEntry> candidates) {
        if (!ServerPlayNetworking.canSend(player, AudioCachePrunePayload.TYPE)) {
            return;
        }
        String validCacheKeys = candidates.stream()
                .filter(entry -> MusicStatus.ACTIVE.equals(entry.status))
                .map(this::cacheKeys)
                .filter(key -> !key.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        ServerPlayNetworking.send(player, new AudioCachePrunePayload(validCacheKeys));
    }

    private void warmPreviewCacheForAllPlayers(MusicEntry entry) {
        AudioCacheWarmPayload payload;
        try {
            payload = previewCacheWarmPayload(entry);
        } catch (IllegalArgumentException exception) {
            Musicxcst.LOGGER.debug("Skipping preview cache warm for '{}': {}", entry.musicId, exception.getMessage());
            return;
        }

        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(target, AudioCacheWarmPayload.TYPE)) {
                ServerPlayNetworking.send(target, payload);
            }
        }
    }

    private List<MusicEntry> activeEntries() {
        return entries.values().stream()
                .filter(entry -> MusicStatus.ACTIVE.equals(entry.status))
                .toList();
    }

    private String cacheKey(MusicEntry entry) {
        if (entry.musicId == null || entry.musicId.isBlank()) {
            return "";
        }
        try {
            AudioCacheWarmPayload payload = cacheWarmPayload(entry);
            return cacheKey(payload.musicId(), payload.sha256(), payload.preview());
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private String cacheKeys(MusicEntry entry) {
        String full = cacheKey(entry);
        String preview = "";
        try {
            AudioCacheWarmPayload payload = previewCacheWarmPayload(entry);
            preview = cacheKey(payload.musicId(), payload.sha256(), true);
        } catch (IllegalArgumentException ignored) {
        }
        if (full.isBlank()) {
            return preview;
        }
        if (preview.isBlank()) {
            return full;
        }
        return full + "\n" + preview;
    }

    private String cacheKey(String musicId, String sha256, boolean preview) {
        String suffix = preview ? "-preview-" : "-";
        return (musicId + suffix + sha256).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
    }

    private String describeMinutes(int minutes) {
        return switch (minutes) {
            case 30 -> "30 minutes";
            case 60 -> "1 hour";
            case 90 -> "1 hour 30 minutes";
            default -> minutes + " minutes";
        };
    }

    public void stopJukeboxPlayback(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        playbackSessions.stop(pos);
        JukeboxStopPayload payload = new JukeboxStopPayload(pos);
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, JukeboxStopPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    private void sendJukeboxVolumeUpdate(Level level, BlockPos pos, int volumePercent) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        PlaybackSessionManager.PlaybackSession session = playbackSessions.session(pos);
        int radius = session == null ? config.playbackRadiusBlocks : session.radiusBlocks();
        Vec3 center = Vec3.atCenterOf(pos);
        JukeboxVolumeUpdatePayload payload = new JukeboxVolumeUpdatePayload(pos.immutable(), clampVolume(volumePercent));
        for (ServerPlayer listener : serverLevel.players()) {
            if (listener.position().distanceToSqr(center) <= (double) radius * radius
                    && ServerPlayNetworking.canSend(listener, JukeboxVolumeUpdatePayload.TYPE)) {
                ServerPlayNetworking.send(listener, payload);
            }
        }
    }

    private void stopFinishedJukeboxPlayback(PlaybackSessionManager.PlaybackSession session) {
        ServerLevel level = server.getLevel(session.dimension());
        if (level == null) {
            playbackSessions.stop(session.sourcePos());
            return;
        }

        if (level.getBlockEntity(session.sourcePos()) instanceof JukeboxBlockEntity jukebox) {
            jukebox.popOutTheItem();
        } else {
            stopJukeboxPlayback(level, session.sourcePos());
        }
    }

    private void syncJukeboxPlaybackSessions() {
        for (PlaybackSessionManager.PlaybackSession session : playbackSessions.sessions().values()) {
            MusicEntry entry = entries.get(session.musicId());
            if (entry == null || !MusicStatus.ACTIVE.equals(entry.status)) {
                playbackSessions.stop(session.sourcePos());
                continue;
            }
            if (!isJukeboxLooping(session.sourcePos()) && session.durationMillis() > 0L
                    && System.currentTimeMillis() - session.startedAtMillis() >= session.durationMillis()) {
                stopFinishedJukeboxPlayback(session);
                continue;
            }

            JukeboxStartPayload payload;
            try {
                payload = startPayload(entry, session.sourcePos(), true, session.startedAtMillis());
            } catch (IllegalArgumentException exception) {
                Musicxcst.LOGGER.warn("Blueprint CD '{}' cannot continue playback: {}", entry.displayName, exception.getMessage());
                playbackSessions.stop(session.sourcePos());
                continue;
            }

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.level().dimension().equals(session.dimension())) {
                    if (playbackSessions.unmarkListening(session.sourcePos(), player.getUUID())
                            && ServerPlayNetworking.canSend(player, JukeboxStopPayload.TYPE)) {
                        ServerPlayNetworking.send(player, new JukeboxStopPayload(session.sourcePos()));
                    }
                    continue;
                }

                if (!playbackRange.isInRange(player, session.sourcePos(), session.radiusBlocks())) {
                    continue;
                }

                if (playbackSessions.markListening(session.sourcePos(), player.getUUID())
                        && ServerPlayNetworking.canSend(player, JukeboxStartPayload.TYPE)) {
                    ServerPlayNetworking.send(player, payload);
                }
            }
        }
    }

    private void accumulate(StorageStats stats, MusicEntry entry) {
        stats.fileCount++;
        switch (entry.status) {
            case MusicStatus.ACTIVE -> {
                stats.activeCount++;
                stats.bytes += entry.fileSizeBytes;
            }
            case MusicStatus.DELETED -> stats.deletedCount++;
            case MusicStatus.MISSING -> stats.missingCount++;
            default -> stats.invalidCount++;
        }
    }

    private Path resolvePlayableOgg(MusicEntry entry) {
        if (entry == null || !MusicStatus.ACTIVE.equals(entry.status)) {
            throw new IllegalArgumentException("Music entry is not active.");
        }
        return resolvePlayableOggWithoutStatus(entry);
    }

    private Path resolvePlayableOggWithoutStatus(MusicEntry entry) {
        Path file;
        Path root;
        if (entry.normalizedRelativePath != null && !entry.normalizedRelativePath.isBlank()) {
            root = normalizedRoot;
            file = normalizedRoot.resolve(entry.normalizedRelativePath).normalize();
        } else {
            root = importRoot;
            file = importRoot.resolve(entry.safeRelativePath).normalize();
        }

        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Normalized music file is missing.");
        }
        if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg")) {
            throw new IllegalArgumentException("Only normalized .ogg files can play. Re-import this entry with FFmpeg available.");
        }
        return file;
    }

    private Path resolvePreviewOgg(MusicEntry entry) {
        if (entry == null || !MusicStatus.ACTIVE.equals(entry.status)) {
            throw new IllegalArgumentException("Music entry is not active.");
        }
        if (entry.previewRelativePath == null || entry.previewRelativePath.isBlank()) {
            throw new IllegalArgumentException("Preview audio is missing.");
        }

        Path file = normalizedRoot.resolve(entry.previewRelativePath).normalize();
        if (!file.startsWith(normalizedRoot) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Preview audio file is missing.");
        }
        return file;
    }

    private JukeboxStartPayload startPayload(MusicEntry entry, BlockPos pos, boolean positional) {
        return startPayload(entry, pos, positional, System.currentTimeMillis());
    }

    private JukeboxStartPayload startPayload(MusicEntry entry, BlockPos pos, boolean positional, long startedAtMillis) {
        Path file = resolvePlayableOgg(entry);
        return new JukeboxStartPayload(
                pos,
                entry.musicId,
                entry.displayName,
                checksumForPlayback(entry, file, false),
                safeFileSize(file),
                previewChecksumForPlayback(entry),
                entry.previewSizeBytes,
                startedAtMillis,
                config.playbackRadiusBlocks,
                positional,
                positional && isJukeboxLooping(pos),
                jukeboxVolume(pos)
        );
    }

    private boolean isJukeboxLooping(BlockPos pos) {
        return Boolean.TRUE.equals(jukeboxLooping.get(pos));
    }

    private int jukeboxVolume(BlockPos pos) {
        return clampVolume(jukeboxVolumes.getOrDefault(pos, 100));
    }

    private static int clampVolume(int volumePercent) {
        return Math.max(0, Math.min(100, volumePercent));
    }

    private AudioCacheWarmPayload cacheWarmPayload(MusicEntry entry) {
        Path file = resolvePlayableOgg(entry);
        return new AudioCacheWarmPayload(
                entry.musicId,
                entry.displayName,
                checksumForPlayback(entry, file, false),
                safeFileSize(file),
                false
        );
    }

    private AudioCacheWarmPayload previewCacheWarmPayload(MusicEntry entry) {
        Path file = resolvePreviewOgg(entry);
        return new AudioCacheWarmPayload(
                entry.musicId,
                entry.displayName,
                checksumForPlayback(entry, file, true),
                safeFileSize(file),
                true
        );
    }

    private String checksumForPlayback(MusicEntry entry, Path file, boolean preview) {
        if (preview && entry.previewSha256 != null && !entry.previewSha256.isBlank()) {
            return entry.previewSha256;
        }
        if (!preview && entry.normalizedSha256 != null && !entry.normalizedSha256.isBlank()) {
            return entry.normalizedSha256;
        }
        return sha256(file);
    }

    private String previewChecksumForPlayback(MusicEntry entry) {
        if (entry.previewRelativePath == null || entry.previewRelativePath.isBlank()) {
            return "";
        }
        try {
            return checksumForPlayback(entry, resolvePreviewOgg(entry), true);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private MusicEntry requireEntry(String musicId) {
        MusicEntry entry = entries.get(musicId);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown music ID: " + musicId);
        }
        return entry;
    }

    private MusicEntry requirePlayerEntryReference(ServerPlayer player, String reference) {
        String cleanedReference = stripWrappingQuotes(reference == null ? "" : reference.trim());
        MusicEntry byId = entries.get(cleanedReference);
        if (byId != null) {
            return byId;
        }

        String owner = player.getUUID().toString();
        List<MusicEntry> matches = entries.values().stream()
                .filter(entry -> Objects.equals(entry.ownerUuid, owner))
                .filter(entry -> !MusicStatus.isInvalidLike(entry.status))
                .filter(entry -> Objects.equals(entry.originalFileName, cleanedReference)
                        || Objects.equals(entry.displayName, cleanedReference))
                .sorted(Comparator.comparingLong(entry -> -entry.createdAtEpochMillis))
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Unknown uploaded music: " + cleanedReference);
        }
        return matches.get(0);
    }

    private String displayEntryReference(MusicEntry entry) {
        if (entry.originalFileName != null && !entry.originalFileName.isBlank()) {
            return entry.originalFileName;
        }
        if (entry.displayName != null && !entry.displayName.isBlank()) {
            return entry.displayName;
        }
        return entry.musicId;
    }

    private void markDeleted(MusicEntry entry) {
        stopPlaybackForEntry(entry.musicId);
        deleteEntryFiles(entry);
        entry.status = MusicStatus.DELETED;
        saveIndex();
        syncAllPlayers();
    }

    private void initializePaths(MinecraftServer server) {
        Path serverDirectory = server.getServerDirectory();
        this.configPath = serverDirectory.resolve("config").resolve("musicxcst.json");
        this.indexPath = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("musicxcst").resolve("music-index.json");
        this.jukeboxSettingsPath = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("musicxcst").resolve("jukebox-settings.json");
        this.importRoot = server.getWorldPath(LevelResource.ROOT).resolve(config.serverImportFolder).normalize();
        this.normalizedRoot = server.getWorldPath(LevelResource.ROOT).resolve(config.serverNormalizedAudioFolder).normalize();
    }

    private void loadConfig() {
        try {
            Files.createDirectories(configPath.getParent());
            if (Files.notExists(configPath)) {
                this.config = new CstMusicConfig();
                saveConfig();
            } else {
                try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                    this.config = GSON.fromJson(reader, CstMusicConfig.class);
                    if (this.config == null) {
                        this.config = new CstMusicConfig();
                    }
                }
            }
            if (this.config.previewCacheSeconds == 7) {
                this.config.previewCacheSeconds = 15;
                saveConfig();
            }
            if (!FfmpegLocator.normalizedMode(this.config).equals(this.config.ffmpegMode)) {
                Musicxcst.LOGGER.warn("Migrating unsupported ffmpegMode '{}' to '{}'. Public MusicXCST builds do not bundle FFmpeg binaries.", this.config.ffmpegMode, FfmpegLocator.normalizedMode(this.config));
                this.config.ffmpegMode = FfmpegLocator.normalizedMode(this.config);
                saveConfig();
            }
            this.importRoot = server.getWorldPath(LevelResource.ROOT).resolve(config.serverImportFolder).normalize();
            this.normalizedRoot = server.getWorldPath(LevelResource.ROOT).resolve(config.serverNormalizedAudioFolder).normalize();
            Files.createDirectories(importRoot);
            Files.createDirectories(normalizedRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load config " + configPath, exception);
        }
    }

    private void saveConfig() {
        if (configPath == null) {
            return;
        }

        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save config " + configPath, exception);
        }
    }

    private void loadIndex() {
        entries.clear();
        try {
            Files.createDirectories(indexPath.getParent());
            if (Files.notExists(indexPath)) {
                saveIndex();
                return;
            }

            try (Reader reader = Files.newBufferedReader(indexPath, StandardCharsets.UTF_8)) {
                MusicIndexFile file = GSON.fromJson(reader, MusicIndexFile.class);
                if (file == null || file.entries == null) {
                    return;
                }
                for (MusicEntry entry : file.entries) {
                    if (entry != null && entry.musicId != null && !entry.musicId.isBlank()) {
                        entries.put(entry.musicId, entry);
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load music metadata index " + indexPath, exception);
        }
    }

    private void saveIndex() {
        if (indexPath == null) {
            return;
        }

        try {
            Files.createDirectories(indexPath.getParent());
            MusicIndexFile file = new MusicIndexFile();
            file.entries = new ArrayList<>(entries.values());
            try (Writer writer = Files.newBufferedWriter(indexPath, StandardCharsets.UTF_8)) {
                GSON.toJson(file, writer);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save music metadata index " + indexPath, exception);
        }
    }

    private void loadJukeboxSettings() {
        jukeboxLooping.clear();
        jukeboxVolumes.clear();
        try {
            Files.createDirectories(jukeboxSettingsPath.getParent());
            if (Files.notExists(jukeboxSettingsPath)) {
                saveJukeboxSettings();
                return;
            }

            try (Reader reader = Files.newBufferedReader(jukeboxSettingsPath, StandardCharsets.UTF_8)) {
                JukeboxSettingsFile file = GSON.fromJson(reader, JukeboxSettingsFile.class);
                if (file == null || file.settings == null) {
                    return;
                }
                for (SavedJukeboxSetting setting : file.settings) {
                    if (setting == null || setting.pos == null || setting.pos.isBlank()) {
                        continue;
                    }
                    BlockPos pos = parseBlockPos(setting.pos);
                    if (pos == null) {
                        continue;
                    }
                    if (setting.looping) {
                        jukeboxLooping.put(pos, true);
                    }
                    int volume = clampVolume(setting.volumePercent);
                    if (volume != 100) {
                        jukeboxVolumes.put(pos, volume);
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load jukebox settings " + jukeboxSettingsPath, exception);
        }
    }

    private void saveJukeboxSettings() {
        if (jukeboxSettingsPath == null) {
            return;
        }

        try {
            Files.createDirectories(jukeboxSettingsPath.getParent());
            JukeboxSettingsFile file = new JukeboxSettingsFile();
            Map<BlockPos, SavedJukeboxSetting> byPos = new LinkedHashMap<>();

            for (Map.Entry<BlockPos, Boolean> entry : jukeboxLooping.entrySet()) {
                if (Boolean.TRUE.equals(entry.getValue())) {
                    byPos.computeIfAbsent(entry.getKey().immutable(), SavedJukeboxSetting::new).looping = true;
                }
            }
            for (Map.Entry<BlockPos, Integer> entry : jukeboxVolumes.entrySet()) {
                int volume = clampVolume(entry.getValue());
                if (volume != 100) {
                    byPos.computeIfAbsent(entry.getKey().immutable(), SavedJukeboxSetting::new).volumePercent = volume;
                }
            }

            file.settings = new ArrayList<>(byPos.values());
            try (Writer writer = Files.newBufferedWriter(jukeboxSettingsPath, StandardCharsets.UTF_8)) {
                GSON.toJson(file, writer);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save jukebox settings " + jukeboxSettingsPath, exception);
        }
    }

    private static BlockPos parseBlockPos(String value) {
        String[] parts = value.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String formatBlockPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private void refreshStatuses() {
        for (MusicEntry entry : entries.values()) {
            if (MusicStatus.DELETED.equals(entry.status)) {
                continue;
            }

            Path file = entry.normalizedRelativePath == null || entry.normalizedRelativePath.isBlank()
                    ? importRoot.resolve(entry.safeRelativePath).normalize()
                    : normalizedRoot.resolve(entry.normalizedRelativePath).normalize();
            Path root = entry.normalizedRelativePath == null || entry.normalizedRelativePath.isBlank() ? importRoot : normalizedRoot;
            if (!file.startsWith(root)) {
                entry.status = MusicStatus.INVALID;
            } else if (Files.isRegularFile(file)) {
                entry.status = MusicStatus.ACTIVE;
                if (entry.normalizedRelativePath != null && !entry.normalizedRelativePath.isBlank()) {
                    entry.normalizedSizeBytes = safeFileSize(file);
                } else {
                    entry.fileSizeBytes = safeFileSize(file);
                }
                if (entry.durationMillis <= 0L && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg")) {
                    entry.durationMillis = probeAudioDurationMillis(file);
                }
            } else {
                entry.status = MusicStatus.MISSING;
            }
        }
    }

    private void validateQuota(UUID playerUuid, long fileSize) {
        long ownBytes = 0L;
        long serverBytes = 0L;
        String owner = playerUuid.toString();
        for (MusicEntry entry : entries.values()) {
            if (MusicStatus.DELETED.equals(entry.status)) {
                continue;
            }
            serverBytes += entry.fileSizeBytes;
            if (Objects.equals(entry.ownerUuid, owner)) {
                ownBytes += entry.fileSizeBytes;
            }
        }

        if (ownBytes + fileSize > config.maxStoragePerPlayerBytes) {
            throw new IllegalArgumentException("Player storage quota exceeded.");
        }
        if (serverBytes + fileSize > config.maxTotalServerStorageBytes) {
            throw new IllegalArgumentException("Server storage quota exceeded.");
        }
    }

    private void enforcePlayerFileLimitOrThrow(ServerPlayer player) {
        if (!isPlayerFileLimitReached(player)) {
            return;
        }

        String mode = playerLimitMode();
        if ("auto_delete_oldest".equals(mode)) {
            deleteOldestOwnedEntryForLimit(player);
            player.sendSystemMessage(ChatFeedback.warning("You reached the limit of " + playerFileLimit() + " music files. Your oldest uploaded track was deleted to make space."));
            return;
        }
        if ("block_new_upload".equals(mode)) {
            throw new IllegalArgumentException("You reached the limit of " + playerFileLimit() + " music files. Delete an old upload before creating a new one.");
        }
        throw new IllegalArgumentException("You reached the limit of " + playerFileLimit() + " music files. Use the CD Writer GUI to confirm deleting your oldest uploaded track.");
    }

    private boolean preparePlayerFileLimitForCdWriter(ServerPlayer player, CdWriterWritePayload payload) {
        if (!isPlayerFileLimitReached(player)) {
            return true;
        }

        String mode = playerLimitMode();
        if ("auto_delete_oldest".equals(mode)) {
            deleteOldestOwnedEntryForLimit(player);
            player.sendSystemMessage(ChatFeedback.warning("You reached the limit of " + playerFileLimit() + " music files. Your oldest uploaded track was deleted to make space."));
            return true;
        }
        if ("block_new_upload".equals(mode)) {
            throw new IllegalArgumentException("You reached the limit of " + playerFileLimit() + " music files. Delete an old upload before creating a new one.");
        }

        pendingLimitConfirmations.put(player.getUUID(), new PendingLimitConfirmation(payload));
        if (ServerPlayNetworking.canSend(player, MusicLimitConfirmPayload.TYPE)) {
            ServerPlayNetworking.send(player, new MusicLimitConfirmPayload(payload.pos(), playerFileLimit()));
        }
        player.sendSystemMessage(ChatFeedback.warning("You have reached the server limit of " + playerFileLimit() + " music files. Continuing will delete your oldest uploaded track."));
        return false;
    }

    private boolean isPlayerFileLimitReached(ServerPlayer player) {
        if (!config.maxMusicFilesPerPlayerEnabled) {
            return false;
        }
        int limit = playerFileLimit();
        return limit >= 0 && activeOwnedEntries(player).size() >= limit;
    }

    private int playerFileLimit() {
        return Math.max(0, config.maxMusicFilesPerPlayer);
    }

    private String playerLimitMode() {
        String mode = config.playerLimitMode == null ? "" : config.playerLimitMode.trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "auto_delete_oldest", "block_new_upload" -> mode;
            default -> "confirm_delete_oldest";
        };
    }

    private List<MusicEntry> activeOwnedEntries(ServerPlayer player) {
        String owner = player.getUUID().toString();
        return entries.values().stream()
                .filter(entry -> Objects.equals(entry.ownerUuid, owner))
                .filter(entry -> MusicStatus.ACTIVE.equals(entry.status))
                .sorted(Comparator.comparingLong(entry -> entry.createdAtEpochMillis))
                .toList();
    }

    private void deleteOldestOwnedEntryForLimit(ServerPlayer player) {
        MusicEntry oldest = activeOwnedEntries(player).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No owned music entry is available to delete."));
        stopPlaybackForEntry(oldest.musicId);
        deleteEntryFiles(oldest);
        oldest.status = MusicStatus.DELETED;
        saveIndex();
        syncAllPlayers();
        Musicxcst.LOGGER.info("Deleted oldest music entry '{}' for player {} due to file limit.", oldest.musicId, player.getName().getString());
    }

    private void stopPlaybackForEntry(String musicId) {
        for (PlaybackSessionManager.PlaybackSession session : playbackSessions.sessions().values()) {
            if (Objects.equals(session.musicId(), musicId)) {
                ServerLevel level = server.getLevel(session.dimension());
                if (level != null) {
                    stopJukeboxPlayback(level, session.sourcePos());
                } else {
                    playbackSessions.stop(session.sourcePos());
                }
            }
        }
    }

    private void deleteEntryFiles(MusicEntry entry) {
        deleteEntryFile(importRoot, entry.safeRelativePath);
        deleteEntryFile(normalizedRoot, entry.normalizedRelativePath);
        deleteEntryFile(normalizedRoot, entry.previewRelativePath);
        deleteReusableUploadedFile(entry);
    }

    private void deleteReusableUploadedFile(MusicEntry entry) {
        if (entry.ownerUuid == null || entry.ownerUuid.isBlank() || entry.originalFileName == null || entry.originalFileName.isBlank()) {
            return;
        }
        try {
            UUID ownerUuid = UUID.fromString(entry.ownerUuid);
            Path ownerUploadFolder = uploadFolder(ownerUuid);
            Path uploadedFile = ownerUploadFolder.resolve(entry.originalFileName).normalize();
            if (uploadedFile.startsWith(ownerUploadFolder)) {
                Files.deleteIfExists(uploadedFile);
            }
        } catch (IllegalArgumentException | IOException exception) {
            Musicxcst.LOGGER.debug("Failed to delete reusable uploaded music file for entry '{}': {}", entry.musicId, exception.getMessage());
        }
    }

    private void deleteEntryFile(Path root, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        Path file = root.resolve(relativePath).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("Refusing to delete a music file outside its storage root.");
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to delete old music file " + file.getFileName() + ".", exception);
        }
    }

    private void validateFile(Path resolved, long fileSize) {
        if (!Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException("Music file not found in server import folder.");
        }
        if (fileSize <= 0L) {
            throw new IllegalArgumentException("Music file is empty.");
        }
        if (fileSize > config.maxFileSizeBytes) {
            throw new IllegalArgumentException("Music file exceeds the configured max size.");
        }

        String extension = extension(resolved.getFileName().toString());
        if (extension.isBlank()) {
            throw new IllegalArgumentException("Music file has no extension.");
        }
        if (!config.allowedFileExtensions.contains(extension)) {
            throw new IllegalArgumentException("Extension ." + extension + " is not allowed.");
        }
    }

    private void validateMusicDuration(long durationMillis) {
        if (!config.maxMusicDurationEnabled) {
            return;
        }
        int maxSeconds = Math.max(1, config.maxMusicDurationSeconds);
        if (durationMillis <= 0L) {
            Musicxcst.LOGGER.warn("Could not determine uploaded track duration; rejecting upload because maxMusicDurationEnabled is true.");
            throw new IllegalArgumentException("Could not determine track duration for the configured duration limit.");
        }
        long durationSeconds = (durationMillis + 999L) / 1000L;
        if (durationSeconds > maxSeconds) {
            throw new IllegalArgumentException("This track is too long. Maximum allowed duration: " + formatDuration(maxSeconds) + ".");
        }
    }

    private static String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    private NormalizedAudio normalizeAudio(ServerPlayer player, Path source, String musicId, long sourceSizeBytes) {
        String outputFormat = config.normalizedOutputFormat == null || config.normalizedOutputFormat.isBlank()
                ? "ogg"
                : config.normalizedOutputFormat.toLowerCase(Locale.ROOT);
        if (!"ogg".equals(outputFormat)) {
            throw new IllegalArgumentException("Only OGG Vorbis normalized output is supported in this version.");
        }

        Path folder = normalizedRoot.resolve(musicId.substring(0, 2)).normalize();
        if (!folder.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Normalized audio folder escapes the server storage root.");
        }

        Path output = folder.resolve(musicId + ".ogg").normalize();
        if (!output.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Normalized audio output escapes the server storage root.");
        }

        try {
            Files.createDirectories(folder);
            if ("ogg".equals(extension(source.getFileName().toString()))) {
                player.sendOverlayMessage(ChatFeedback.success("Blueprint CD audio is already ready."));
                Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING);
            } else {
                if (!config.allowServerSideTranscoding) {
                    throw new IllegalArgumentException("Server-side transcoding is disabled. Use the CD Writer GUI so the client converts the file before upload.");
                }
                runFfmpegNormalization(player, source, output, sourceSizeBytes);
            }
            long sizeBytes = safeFileSize(output);
            return new NormalizedAudio(normalizedRoot.relativize(output).toString().replace('\\', '/'), sizeBytes, sha256(output));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to store normalized audio.", exception);
        }
    }

    private NormalizedAudio createPreviewAudio(ServerPlayer player, MusicEntry entry) {
        Path source = resolvePlayableOgg(entry);
        int previewSeconds = Math.max(1, Math.min(60, config.previewCacheSeconds));
        Path folder = normalizedRoot.resolve(entry.musicId.substring(0, 2)).normalize();
        Path output = folder.resolve(entry.musicId + "-preview.ogg").normalize();
        if (!output.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Preview audio output escapes the server storage root.");
        }

        try {
            Files.createDirectories(folder);
            var ffmpegOpt = ffmpegLocator.locate(server.getServerDirectory(), config);
            if (ffmpegOpt.isPresent()) {
                mediaTranscoder.createPreview(ffmpegOpt.get(), source, output, config, previewSeconds);
            } else {
                Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING);
            }
            return new NormalizedAudio(normalizedRoot.relativize(output).toString().replace('\\', '/'), safeFileSize(output), sha256(output));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to create preview audio.", exception);
        }
    }

    private void runFfmpegNormalization(ServerPlayer player, Path source, Path output, long sourceSizeBytes) {
        var ffmpegOpt = ffmpegLocator.locate(server.getServerDirectory(), config);
        if (ffmpegOpt.isEmpty()) {
            throw new IllegalArgumentException("Server-side transcoding is unavailable because FFmpeg is not installed. Convert the file to .ogg on the client or install/configure FFmpeg on the server.");
        }
        String ffmpeg = ffmpegOpt.get();
        mediaTranscoder.transcodeToOgg(ffmpeg, source, output, config, message -> player.sendOverlayMessage(ChatFeedback.progress(message)));
        player.sendOverlayMessage(ChatFeedback.success("Blueprint CD audio is ready."));
    }

    private int estimatedConversionSeconds(long sourceSizeBytes) {
        long megabytes = Math.max(1L, (sourceSizeBytes + 1024L * 1024L - 1L) / (1024L * 1024L));
        return (int) Math.max(5L, Math.min(120L, 6L + megabytes * 2L));
    }

    private long probeAudioDurationMillis(Path file) {
        var ffmpeg = ffmpegLocator.locate(server.getServerDirectory(), config);
        if (ffmpeg.isPresent()) {
            return mediaTranscoder.probeDurationMillis(ffmpeg.get(), file);
        }
        // Fallback: try to probe OGG files without FFmpeg by parsing the container
        String fname = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fname.endsWith(".ogg")) {
            long d = MediaTranscoder.probeOggDurationMillis(file);
            if (d > 0L) return d;
        }
        return 0L;
    }

    private long parseDurationMillis(String ffmpegOutput) {
        return MediaTranscoder.parseDurationMillis(ffmpegOutput);
    }

    private boolean isFfmpegAvailable(String executable) {
        return ffmpegLocator.isAvailable(executable);
    }

    private String safeProcessLog(String log) {
        return MediaTranscoder.safeProcessLog(log);
    }

    private String extension(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        return dot < 0 ? "" : lower.substring(dot + 1);
    }

    private ImportedMusicFile resolveMusicFile(CommandSourceStack source, ServerPlayer player, String requestedLocation, String musicId) {
        String cleanedLocation = stripWrappingQuotes(requestedLocation.trim());
        String normalizedInput = cleanedLocation.replace('\\', '/');
        if (normalizedInput.isBlank()) {
            throw new IllegalArgumentException("Music location cannot be empty.");
        }

        Path requestedPath = Path.of(cleanedLocation);
        if (requestedPath.isAbsolute()) {
            return importAbsolutePath(source, player, requestedPath, musicId);
        }
        if (normalizedInput.contains("..")) {
            throw new IllegalArgumentException("Path traversal is not allowed.");
        }

        Path resolved = importRoot.resolve(normalizedInput).normalize();
        if (!resolved.startsWith(importRoot)) {
            throw new IllegalArgumentException("Resolved path escapes the server import folder.");
        }
        return new ImportedMusicFile(resolved, resolved.getFileName().toString(), importRoot.relativize(resolved).toString().replace('\\', '/'));
    }

    private String stripWrappingQuotes(String input) {
        if (input.length() >= 2 && input.startsWith("\"") && input.endsWith("\"")) {
            return input.substring(1, input.length() - 1);
        }
        return input;
    }

    private String sanitizeUploadId(String uploadId) {
        String sanitized = uploadId == null ? "" : uploadId.replaceAll("[^a-zA-Z0-9_-]+", "");
        if (sanitized.isBlank() || sanitized.length() > 64) {
            throw new IllegalArgumentException("Invalid upload id.");
        }
        return sanitized;
    }

    private Path uploadFolder(ServerPlayer player) {
        return uploadFolder(player.getUUID());
    }

    private Path uploadFolder(UUID ownerUuid) {
        return importRoot.resolve("client-uploads").resolve(ownerUuid.toString()).normalize();
    }

    private Path uploadedMusicPath(ServerPlayer player, String uploadedFileName) {
        String safeFileName = sanitizeFileName(stripWrappingQuotes(uploadedFileName.trim()));
        Path path = uploadFolder(player).resolve(safeFileName).normalize();
        if (!path.startsWith(importRoot)) {
            throw new IllegalArgumentException("Uploaded file path escapes the import root.");
        }
        return path;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            Musicxcst.LOGGER.debug("Failed to delete file '{}': {}", path.getFileName(), exception.getMessage());
        }
    }

    private ImportedMusicFile importAbsolutePath(CommandSourceStack source, ServerPlayer player, Path requestedPath, String musicId) {
        boolean allowedSingleplayer = server.isSingleplayer() && config.allowSingleplayerAbsolutePaths;
        boolean allowedAdminServerPath = server.isDedicatedServer() && config.allowAdminAbsoluteServerPaths && isAdmin(source);
        if (!allowedSingleplayer && !allowedAdminServerPath) {
            throw new IllegalArgumentException("Absolute paths are only allowed in singleplayer by default. On dedicated servers, copy the file into music-import or enable admin absolute server paths in config.");
        }

        Path sourcePath = requestedPath.normalize();
        long fileSize = safeFileSize(sourcePath);
        validateFile(sourcePath, fileSize);

        String safeFileName = sanitizeFileName(sourcePath.getFileName().toString());
        Path ownerFolder = importRoot.resolve(config.absoluteImportSubfolder).resolve(player.getUUID().toString()).normalize();
        if (!ownerFolder.startsWith(importRoot)) {
            throw new IllegalArgumentException("Absolute import folder escapes the server import folder.");
        }

        Path destination = ownerFolder.resolve(musicId + "-" + safeFileName).normalize();
        if (!destination.startsWith(importRoot)) {
            throw new IllegalArgumentException("Absolute import destination escapes the server import folder.");
        }

        try {
            Files.createDirectories(ownerFolder);
            Files.copy(sourcePath, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to import music file. The server can only read files on the server computer.", exception);
        }

        return new ImportedMusicFile(destination, safeFileName, importRoot.relativize(destination).toString().replace('\\', '/'));
    }

    private String sanitizeFileName(String input) {
        String sanitized = input == null ? "music" : input.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "_").trim();
        if (sanitized.isBlank()) {
            sanitized = "music";
        }
        if (sanitized.length() > 80) {
            int dot = sanitized.lastIndexOf('.');
            String extension = dot >= 0 ? sanitized.substring(dot) : "";
            String base = dot >= 0 ? sanitized.substring(0, dot) : sanitized;
            int maxBaseLength = Math.max(1, 80 - extension.length());
            sanitized = base.substring(0, Math.min(base.length(), maxBaseLength)) + extension;
        }
        return sanitized;
    }

    private String normalizedUploadedFileName(String sourceFileName) {
        String sanitized = sanitizeFileName(sourceFileName);
        int dot = sanitized.lastIndexOf('.');
        String baseName = dot > 0 ? sanitized.substring(0, dot) : sanitized;
        return sanitizeFileName(sanitizeSongName(baseName) + ".ogg");
    }

    private long safeFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read file size for " + path.getFileName(), exception);
        }
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(path);
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalArgumentException("Failed to hash file " + path.getFileName(), exception);
        }
    }

    private String sanitizeSongName(String input) {
        String sanitized = input == null ? "" : input.replaceAll("[\\p{Cntrl}]+", " ").trim().replaceAll("\\s+", " ");
        if (sanitized.isBlank()) {
            throw new IllegalArgumentException("Song name cannot be blank.");
        }
        if (sanitized.length() > 64) {
            sanitized = sanitized.substring(0, 64).trim();
        }
        return sanitized;
    }

    private String normalizeHexColor(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.matches("[0-9a-fA-F]{6}")) {
            return null;
        }
        return "#" + normalized.toUpperCase(Locale.ROOT);
    }

    private void syncAllPlayers() {
        ensureServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncPlayerInventory(player);
        }
    }

    private void syncPlayerInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() != ModItems.BLUEPRINT_CD) {
                continue;
            }

            DiscData data = DiscData.fromStack(stack);
            if (data == null) {
                continue;
            }

            MusicEntry entry = entries.get(data.musicId);
            String targetStatus = entry == null ? MusicStatus.INVALID : entry.status;
            String targetColor = entry == null ? "#C93A3A" : entry.hexColor;
            String targetName = entry == null ? data.displayName : entry.displayName;

            if (!Objects.equals(data.status, targetStatus)
                    || !Objects.equals(data.hexColor, targetColor)
                    || !Objects.equals(data.displayName, targetName)) {
                data.status = targetStatus;
                data.hexColor = targetColor;
                data.displayName = targetName;
                DiscData.writeToStack(stack, data);
                inventory.setItem(slot, stack);
            }
        }
    }

    private void ensureServer() {
        if (server == null) {
            throw new IllegalStateException("musicXCST server services are not initialized yet.");
        }
    }

    private boolean isAdmin(CommandSourceStack source) {
        return source.permissions() instanceof LevelBasedPermissionSet levelBased
                && levelBased.level().isEqualOrHigherThan(PermissionLevel.GAMEMASTERS);
    }

    private record ImportedMusicFile(Path path, String originalFileName, String safeRelativePath) {
    }

    private record NormalizedAudio(String safeRelativePath, long sizeBytes, String sha256) {
    }

    private record PendingLimitConfirmation(CdWriterWritePayload payload) {
    }

    private record PendingClientUpload(String uploadId, UUID ownerUuid, String displayName, String originalFileName, Path tempPath, long sizeBytes, long durationMillis, long receivedBytes, long startedAtMillis) {
        private PendingClientUpload withReceivedBytes(long receivedBytes) {
            return new PendingClientUpload(uploadId, ownerUuid, displayName, originalFileName, tempPath, sizeBytes, durationMillis, receivedBytes, startedAtMillis);
        }
    }

    private static final class JukeboxSettingsFile {
        List<SavedJukeboxSetting> settings = new ArrayList<>();
    }

    private static final class SavedJukeboxSetting {
        String pos;
        boolean looping;
        int volumePercent = 100;

        private SavedJukeboxSetting() {
        }

        private SavedJukeboxSetting(BlockPos pos) {
            this.pos = formatBlockPos(pos);
        }
    }
}
