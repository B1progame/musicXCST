package de.coulees.B1progame.musicxcst.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.config.CstMusicConfig;
import de.coulees.B1progame.musicxcst.data.DiscData;
import de.coulees.B1progame.musicxcst.data.MusicEntry;
import de.coulees.B1progame.musicxcst.data.MusicIndexFile;
import de.coulees.B1progame.musicxcst.data.MusicStatus;
import de.coulees.B1progame.musicxcst.data.StorageStats;
import de.coulees.B1progame.musicxcst.init.ModItems;
import de.coulees.B1progame.musicxcst.network.AudioCacheWarmPayload;
import de.coulees.B1progame.musicxcst.network.AudioChunkPayload;
import de.coulees.B1progame.musicxcst.network.AudioChunkRequestPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxSettingsOpenPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxSettingsUpdatePayload;
import de.coulees.B1progame.musicxcst.network.JukeboxStartPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxStopPayload;
import de.coulees.B1progame.musicxcst.service.audio.AudioChunkDownloadManager;
import de.coulees.B1progame.musicxcst.service.audio.BundledFfmpegResolver;
import de.coulees.B1progame.musicxcst.service.audio.PlaybackRangeTracker;
import de.coulees.B1progame.musicxcst.service.audio.PlaybackSessionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;

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

public final class MusicLibraryService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Map<String, MusicEntry> entries = new LinkedHashMap<>();
    private final PlaybackSessionManager playbackSessions = new PlaybackSessionManager();
    private final PlaybackRangeTracker playbackRange = new PlaybackRangeTracker();
    private final Map<BlockPos, Boolean> jukeboxLooping = new LinkedHashMap<>();
    private MinecraftServer server;
    private CstMusicConfig config = new CstMusicConfig();
    private long nextAutomaticCacheWarmTick;
    private Path configPath;
    private Path indexPath;
    private Path importRoot;
    private Path normalizedRoot;

    public void onServerStarted(MinecraftServer server) {
        this.server = server;
        initializePaths(server);
        loadConfig();
        loadIndex();
        refreshStatuses();
        saveIndex();
        syncAllPlayers();
    }

    public void onServerStopping(MinecraftServer server) {
        playbackSessions.clear();
        jukeboxLooping.clear();
        saveConfig();
        saveIndex();
        this.server = null;
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
        entry.normalizedFormat = config.normalizedOutputFormat;
        entry.normalizedSampleRate = config.sampleRate;
        entry.normalizedBitrate = config.audioBitrate;
        entry.status = MusicStatus.ACTIVE;
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
        return "Created Blueprint CD '" + sanitizedName + "' with music ID " + entry.musicId + ".";
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

    public MusicEntry requirePlayerVisibleEntry(ServerPlayer player, String musicId) {
        MusicEntry entry = requireEntry(musicId);
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
        return "Deleted music entry " + entry.musicId + ".";
    }

    public String deleteEntryAsAdmin(CommandSourceStack source, String musicId) {
        if (!isAdmin(source)) {
            throw new IllegalArgumentException("Admin permissions required.");
        }
        MusicEntry entry = requireEntry(musicId);
        markDeleted(entry);
        return "Deleted music entry " + entry.musicId + ".";
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
        int sent = sendCacheWarmPayloads(player, listEntriesForPlayer(player));
        return sent == 0
                ? "No active music entries are available to download."
                : "Started caching " + sent + " song(s).";
    }

    public void openJukeboxSettings(ServerPlayer player, BlockPos pos) {
        ensureServer();
        if (!player.level().getBlockState(pos).is(Blocks.JUKEBOX) || player.blockPosition().distSqr(pos) > 64.0D) {
            return;
        }
        if (ServerPlayNetworking.canSend(player, JukeboxSettingsOpenPayload.TYPE)) {
            ServerPlayNetworking.send(player, new JukeboxSettingsOpenPayload(pos.immutable(), isJukeboxLooping(pos)));
        }
    }

    public void updateJukeboxSettings(ServerPlayer player, JukeboxSettingsUpdatePayload payload) {
        ensureServer();
        BlockPos pos = payload.pos().immutable();
        if (!player.level().getBlockState(pos).is(Blocks.JUKEBOX) || player.blockPosition().distSqr(pos) > 64.0D) {
            return;
        }

        jukeboxLooping.put(pos, payload.looping());
        if (player.level().getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox) {
            startJukeboxPlayback(player.level(), pos, jukebox.getTheItem());
        }
    }

    public void startJukeboxPlayback(Level level, BlockPos pos, ItemStack stack) {
        if (!(level instanceof ServerLevel serverLevel) || stack.getItem() != ModItems.BLUEPRINT_CD) {
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
                        startedAtMillis
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

    public boolean rejectBlueprintJukeboxInsert(Player player, ItemStack stack) {
        if (stack.getItem() != ModItems.BLUEPRINT_CD) {
            return false;
        }

        DiscData data = DiscData.fromStack(stack);
        if (data == null) {
            player.sendOverlayMessage(Component.literal("Blank Blueprint CDs cannot be inserted into jukeboxes."));
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
            player.sendOverlayMessage(Component.literal("This Blueprint CD is invalid and cannot be inserted."));
            return true;
        }

        player.sendOverlayMessage(Component.literal("This Blueprint CD is still converting. Try again when it is ready."));
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

        Path file = resolvePlayableOgg(entry);
        long totalSize = safeFileSize(file);
        long offset = Math.max(0L, request.offset());
        if (offset >= totalSize) {
            ServerPlayNetworking.send(player, new AudioChunkPayload(entry.musicId, totalSize, totalSize, checksumForPlayback(entry, file), new byte[0], true));
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
        ServerPlayNetworking.send(player, new AudioChunkPayload(entry.musicId, offset, totalSize, checksumForPlayback(entry, file), data, last));
    }

    private void warmCachesAutomatically(MinecraftServer server) {
        if (!"auto".equalsIgnoreCase(config.cacheWarmMode)) {
            return;
        }

        int intervalMinutes = Math.max(1, config.cacheWarmIntervalMinutes);
        long intervalTicks = intervalMinutes * 60L * 20L;
        if (nextAutomaticCacheWarmTick == 0L) {
            nextAutomaticCacheWarmTick = server.getTickCount() + intervalTicks;
            return;
        }
        if (server.getTickCount() < nextAutomaticCacheWarmTick) {
            return;
        }

        nextAutomaticCacheWarmTick = server.getTickCount() + intervalTicks;
        List<MusicEntry> activeEntries = entries.values().stream()
                .filter(entry -> MusicStatus.ACTIVE.equals(entry.status))
                .toList();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendCacheWarmPayloads(player, activeEntries);
        }
    }

    private int sendCacheWarmPayloads(ServerPlayer player, List<MusicEntry> candidates) {
        if (!ServerPlayNetworking.canSend(player, AudioCacheWarmPayload.TYPE)) {
            return 0;
        }

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

    private void syncJukeboxPlaybackSessions() {
        for (PlaybackSessionManager.PlaybackSession session : playbackSessions.sessions().values()) {
            MusicEntry entry = entries.get(session.musicId());
            if (entry == null || !MusicStatus.ACTIVE.equals(entry.status)) {
                playbackSessions.stop(session.sourcePos());
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
        stats.bytes += entry.fileSizeBytes;
        stats.fileCount++;
        switch (entry.status) {
            case MusicStatus.ACTIVE -> stats.activeCount++;
            case MusicStatus.DELETED -> stats.deletedCount++;
            case MusicStatus.MISSING -> stats.missingCount++;
            default -> stats.invalidCount++;
        }
    }

    private Path resolvePlayableOgg(MusicEntry entry) {
        if (entry == null || !MusicStatus.ACTIVE.equals(entry.status)) {
            throw new IllegalArgumentException("Music entry is not active.");
        }

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

    private JukeboxStartPayload startPayload(MusicEntry entry, BlockPos pos, boolean positional) {
        return startPayload(entry, pos, positional, System.currentTimeMillis());
    }

    private JukeboxStartPayload startPayload(MusicEntry entry, BlockPos pos, boolean positional, long startedAtMillis) {
        Path file = resolvePlayableOgg(entry);
        return new JukeboxStartPayload(
                pos,
                entry.musicId,
                entry.displayName,
                checksumForPlayback(entry, file),
                safeFileSize(file),
                startedAtMillis,
                config.playbackRadiusBlocks,
                positional,
                positional && isJukeboxLooping(pos)
        );
    }

    private boolean isJukeboxLooping(BlockPos pos) {
        return Boolean.TRUE.equals(jukeboxLooping.get(pos));
    }

    private AudioCacheWarmPayload cacheWarmPayload(MusicEntry entry) {
        Path file = resolvePlayableOgg(entry);
        return new AudioCacheWarmPayload(
                entry.musicId,
                entry.displayName,
                checksumForPlayback(entry, file),
                safeFileSize(file)
        );
    }

    private String checksumForPlayback(MusicEntry entry, Path file) {
        if (entry.normalizedSha256 != null && !entry.normalizedSha256.isBlank()) {
            return entry.normalizedSha256;
        }
        return sha256(file);
    }

    private MusicEntry requireEntry(String musicId) {
        MusicEntry entry = entries.get(musicId);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown music ID: " + musicId);
        }
        return entry;
    }

    private void markDeleted(MusicEntry entry) {
        entry.status = MusicStatus.DELETED;
        saveIndex();
        syncAllPlayers();
    }

    private void initializePaths(MinecraftServer server) {
        Path serverDirectory = server.getServerDirectory();
        this.configPath = serverDirectory.resolve("config").resolve("musicxcst.json");
        this.indexPath = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("musicxcst").resolve("music-index.json");
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
                player.sendOverlayMessage(Component.literal("Blueprint CD audio is already ready."));
                Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING);
            } else {
                runFfmpegNormalization(player, source, output, sourceSizeBytes);
            }
            long sizeBytes = safeFileSize(output);
            return new NormalizedAudio(normalizedRoot.relativize(output).toString().replace('\\', '/'), sizeBytes, sha256(output));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to store normalized audio.", exception);
        }
    }

    private void runFfmpegNormalization(ServerPlayer player, Path source, Path output, long sourceSizeBytes) {
        String ffmpeg = resolveFfmpegExecutable();

        List<String> command = new ArrayList<>();
        command.add(ffmpeg);
        command.add("-y");
        command.add("-i");
        command.add(source.toString());
        command.add("-vn");
        command.add("-map");
        command.add("a:0");
        command.add("-c:a");
        command.add("libvorbis");
        command.add("-b:a");
        command.add(config.audioBitrate == null || config.audioBitrate.isBlank() ? "128k" : config.audioBitrate);
        command.add("-ar");
        command.add(Integer.toString(config.sampleRate <= 0 ? 44100 : config.sampleRate));
        command.add("-ac");
        command.add(config.monoDownmix ? "1" : (config.stereoEnabled ? "2" : "1"));
        command.add(output.toString());

        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            StringBuilder log = new StringBuilder();
            Thread logReader = new Thread(() -> {
                try {
                    log.append(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException exception) {
                    log.append(exception.getMessage());
                }
            }, "musicxcst-ffmpeg-log");
            logReader.setDaemon(true);
            logReader.start();

            int estimatedSeconds = estimatedConversionSeconds(sourceSizeBytes);
            long startedAtMillis = System.currentTimeMillis();
            while (!process.waitFor(1L, TimeUnit.SECONDS)) {
                int elapsedSeconds = (int) Math.max(1L, (System.currentTimeMillis() - startedAtMillis) / 1000L);
                int remainingSeconds = Math.max(1, estimatedSeconds - elapsedSeconds);
                player.sendOverlayMessage(Component.literal("Converting Blueprint CD audio... about " + remainingSeconds + "s left."));
            }

            int exit = process.exitValue();
            logReader.join(1000L);
            if (exit != 0) {
                throw new IllegalArgumentException("FFmpeg failed to normalize audio. " + safeProcessLog(log.toString()));
            }
            player.sendOverlayMessage(Component.literal("Blueprint CD audio is ready."));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to run FFmpeg. Check the configured ffmpegPath.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("FFmpeg normalization was interrupted.", exception);
        }
    }

    private int estimatedConversionSeconds(long sourceSizeBytes) {
        long megabytes = Math.max(1L, (sourceSizeBytes + 1024L * 1024L - 1L) / (1024L * 1024L));
        return (int) Math.max(5L, Math.min(120L, 6L + megabytes * 2L));
    }

    private String resolveFfmpegExecutable() {
        String configured = config.ffmpegPath == null ? "" : config.ffmpegPath.trim();
        boolean explicitPath = !configured.isBlank() && !"ffmpeg".equalsIgnoreCase(configured);
        if (explicitPath) {
            if (isFfmpegAvailable(configured)) {
                return configured;
            }
            throw new IllegalArgumentException("Configured ffmpegPath does not point to a working FFmpeg executable: " + configured);
        }

        try {
            Path bundled = BundledFfmpegResolver.resolve(server.getServerDirectory());
            if (bundled != null && isFfmpegAvailable(bundled.toString())) {
                return bundled.toString();
            }
        } catch (IOException exception) {
            Musicxcst.LOGGER.warn("Failed to extract bundled FFmpeg: {}", exception.getMessage());
        }

        if (isFfmpegAvailable("ffmpeg")) {
            return "ffmpeg";
        }

        throw new IllegalArgumentException("FFmpeg is required to import this audio format. Add a bundled binary at one of these resource paths: "
                + BundledFfmpegResolver.supportedResourcePaths()
                + ", configure ffmpegPath, or import an already-compatible .ogg file.");
    }

    private boolean isFfmpegAvailable(String executable) {
        try {
            Process process = new ProcessBuilder(executable, "-version").redirectErrorStream(true).start();
            return process.waitFor() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String safeProcessLog(String log) {
        if (log == null || log.isBlank()) {
            return "No FFmpeg output was captured.";
        }
        String scrubbed = log.replace('\\', '/').replaceAll("[A-Za-z]:/[^\\s]+", "<local-path>");
        return scrubbed.length() > 500 ? scrubbed.substring(0, 500) : scrubbed;
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
                && levelBased.level().isEqualOrHigherThan(PermissionLevel.ADMINS);
    }

    private record ImportedMusicFile(Path path, String originalFileName, String safeRelativePath) {
    }

    private record NormalizedAudio(String safeRelativePath, long sizeBytes, String sha256) {
    }
}
