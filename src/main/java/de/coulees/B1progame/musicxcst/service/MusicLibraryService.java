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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.UUID;

public final class MusicLibraryService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Map<String, MusicEntry> entries = new LinkedHashMap<>();
    private MinecraftServer server;
    private CstMusicConfig config = new CstMusicConfig();
    private Path configPath;
    private Path indexPath;
    private Path importRoot;

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
        saveConfig();
        saveIndex();
        this.server = null;
    }

    public void onServerTick(MinecraftServer server) {
        if (this.server == null || server.getTickCount() % 100 != 0) {
            return;
        }

        syncAllPlayers();
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

    public String createDiscForPlayer(ServerPlayer player, String requestedName, String requestedColor, String requestedLocation) {
        ensureServer();
        String sanitizedName = sanitizeSongName(requestedName);
        String normalizedColor = normalizeHexColor(requestedColor);
        if (normalizedColor == null) {
            throw new IllegalArgumentException("Invalid hex color. Use RRGGBB or #RRGGBB.");
        }

        ItemStack selected = player.getInventory().getSelectedItem();
        if (selected.getItem() != ModItems.BLUEPRINT_CD) {
            throw new IllegalArgumentException("Hold a blank Blueprint CD in your selected slot first.");
        }
        if (DiscData.fromStack(selected) != null) {
            throw new IllegalArgumentException("The selected Blueprint CD is already written.");
        }

        Path resolved = resolveImportPath(requestedLocation);
        long fileSize = safeFileSize(resolved);
        validateFile(resolved, fileSize);
        validateQuota(player.getUUID(), fileSize);

        MusicEntry entry = new MusicEntry();
        entry.musicId = UUID.randomUUID().toString().replace("-", "");
        entry.displayName = sanitizedName;
        entry.originalFileName = resolved.getFileName().toString();
        entry.safeRelativePath = importRoot.relativize(resolved).toString().replace('\\', '/');
        entry.ownerUuid = player.getUUID().toString();
        entry.ownerName = player.getName().getString();
        entry.createdAtEpochMillis = Instant.now().toEpochMilli();
        entry.fileSizeBytes = fileSize;
        entry.sha256 = sha256(resolved);
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
            Files.createDirectories(importRoot);
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

            Path file = importRoot.resolve(entry.safeRelativePath).normalize();
            if (!file.startsWith(importRoot)) {
                entry.status = MusicStatus.INVALID;
            } else if (Files.isRegularFile(file)) {
                entry.status = MusicStatus.ACTIVE;
                entry.fileSizeBytes = safeFileSize(file);
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

        String fileName = resolved.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException("Music file has no extension.");
        }
        String extension = fileName.substring(dot + 1);
        if (!config.allowedFileExtensions.contains(extension)) {
            throw new IllegalArgumentException("Extension ." + extension + " is not allowed.");
        }
    }

    private Path resolveImportPath(String requestedLocation) {
        String normalizedInput = requestedLocation.trim().replace('\\', '/');
        if (normalizedInput.isBlank()) {
            throw new IllegalArgumentException("Music location cannot be empty.");
        }
        if (Path.of(normalizedInput).isAbsolute()) {
            throw new IllegalArgumentException("Absolute paths are not allowed in this version. Use a path inside the server import folder.");
        }
        if (normalizedInput.contains("..")) {
            throw new IllegalArgumentException("Path traversal is not allowed.");
        }

        Path resolved = importRoot.resolve(normalizedInput).normalize();
        if (!resolved.startsWith(importRoot)) {
            throw new IllegalArgumentException("Resolved path escapes the server import folder.");
        }
        return resolved;
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
}
