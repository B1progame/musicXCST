package de.coulees.B1progame.musicxcst.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.data.DiscData;
import de.coulees.B1progame.musicxcst.data.MusicEntry;
import de.coulees.B1progame.musicxcst.data.MusicStatus;
import de.coulees.B1progame.musicxcst.data.StorageStats;
import de.coulees.B1progame.musicxcst.init.ModItems;
import de.coulees.B1progame.musicxcst.network.ClientMusicUploadRequestPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CstMusicCommands {
    private static final int PAGE_SIZE = 8;

    private CstMusicCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("cstmusic")
                .executes(ctx -> help(ctx.getSource()))
                .then(Commands.literal("help").executes(ctx -> help(ctx.getSource())))
                .then(Commands.literal("create")
                        .requires(CstMusicCommands::isAdmin)
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("colorAndLocation", StringArgumentType.greedyString())
                                        .executes(ctx -> create(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                StringArgumentType.getString(ctx, "colorAndLocation")
                                        )))))
                .then(Commands.literal("createupload")
                        .requires(CstMusicCommands::isAdmin)
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("hexColor", StringArgumentType.string())
                                        .suggests(CstMusicCommands::suggestHexColors)
                                        .then(Commands.argument("uploadedFile", StringArgumentType.string())
                                                .suggests(CstMusicCommands::suggestUploadedFiles)
                                                .executes(ctx -> createFromUpload(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name"),
                                                        StringArgumentType.getString(ctx, "hexColor"),
                                                        StringArgumentType.getString(ctx, "uploadedFile")
                                                ))))))
                .then(Commands.literal("upload")
                        .requires(CstMusicCommands::isAdmin)
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("path", StringArgumentType.greedyString())
                                        .executes(ctx -> uploadFromClientPath(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                StringArgumentType.getString(ctx, "path")
                                        )))))
                .then(Commands.literal("list")
                        .executes(ctx -> listOwn(ctx.getSource(), false))
                        .then(Commands.literal("all")
                                .executes(ctx -> listOwn(ctx.getSource(), true))))
                .then(Commands.literal("info")
                        .executes(ctx -> infoUsage(ctx.getSource()))
                        .then(Commands.argument("musicRef", StringArgumentType.string())
                                .suggests(CstMusicCommands::suggestOwnMusicReferences)
                                .executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "musicRef"), false))))
                .then(Commands.literal("delete")
                        .executes(ctx -> deleteUsage(ctx.getSource()))
                        .then(Commands.argument("musicRef", StringArgumentType.string())
                                .suggests(CstMusicCommands::suggestOwnMusicReferences)
                                .executes(ctx -> delete(ctx.getSource(), StringArgumentType.getString(ctx, "musicRef"), false))))
                .then(Commands.literal("storage")
                        .executes(ctx -> storage(ctx.getSource())))
                .then(Commands.literal("download")
                        .then(Commands.literal("all")
                                .executes(ctx -> downloadAll(ctx.getSource())))
                        .then(Commands.literal("auto")
                                .then(Commands.literal("30m")
                                        .executes(ctx -> downloadAuto(ctx.getSource(), 30)))
                                .then(Commands.literal("1h")
                                        .executes(ctx -> downloadAuto(ctx.getSource(), 60)))
                                .then(Commands.literal("1h30m")
                                        .executes(ctx -> downloadAuto(ctx.getSource(), 90))))
                        .then(Commands.literal("off")
                                .executes(ctx -> downloadOff(ctx.getSource()))))
                .then(Commands.literal("admin")
                        .requires(CstMusicCommands::isAdmin)
                        .then(Commands.literal("storage").executes(ctx -> adminStorage(ctx.getSource())))
                        .then(Commands.literal("ffmpeg")
                                .then(Commands.literal("status").executes(ctx -> ffmpegStatus(ctx.getSource())))
                                .then(Commands.literal("path")
                                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                                .executes(ctx -> ffmpegPath(ctx.getSource(), StringArgumentType.getString(ctx, "path")))))
                                .then(Commands.literal("download")
                                        .then(Commands.literal("confirm").executes(ctx -> ffmpegDownloadConfirm(ctx.getSource()))))
                                .then(Commands.literal("reset").executes(ctx -> ffmpegReset(ctx.getSource()))))
                        .then(Commands.literal("list")
                                .executes(ctx -> adminList(ctx.getSource(), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> adminList(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("musicId", StringArgumentType.word())
                                        .suggests(CstMusicCommands::suggestAllMusicIds)
                                        .executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "musicId"), true))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("musicId", StringArgumentType.word())
                                        .suggests(CstMusicCommands::suggestAllMusicIds)
                                        .executes(ctx -> delete(ctx.getSource(), StringArgumentType.getString(ctx, "musicId"), true))))
                        .then(Commands.literal("play")
                                .then(Commands.argument("musicId", StringArgumentType.word())
                                        .suggests(CstMusicCommands::suggestAllMusicIds)
                                        .executes(ctx -> adminPlay(ctx.getSource(), StringArgumentType.getString(ctx, "musicId")))))
                        .then(Commands.literal("debugdisc")
                                .executes(ctx -> giveDebugDisc(ctx.getSource(), "quadrants"))
                                .then(Commands.literal("checkerboard").executes(ctx -> giveDebugDisc(ctx.getSource(), "checkerboard")))
                                .then(Commands.literal("quadrants").executes(ctx -> giveDebugDisc(ctx.getSource(), "quadrants")))
                                .then(Commands.literal("red").executes(ctx -> giveDebugDisc(ctx.getSource(), "red")))
                                .then(Commands.literal("green").executes(ctx -> giveDebugDisc(ctx.getSource(), "green")))
                                .then(Commands.literal("blue").executes(ctx -> giveDebugDisc(ctx.getSource(), "blue")))
                                .then(Commands.literal("transparent-center").executes(ctx -> giveDebugDisc(ctx.getSource(), "transparent-center")))
                                .then(Commands.literal("invalid").executes(ctx -> giveDebugDisc(ctx.getSource(), "invalid"))))
                        .then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource())))
                        .then(Commands.literal("repairindex").executes(ctx -> repairIndex(ctx.getSource())))));
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("musicXCST guide").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.literal("CD Writer block workflow:").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("  1. Place a blank Blueprint CD in the CD Writer input slot.").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("  2. Enter a disc name, choose a local file with the folder button, and edit the disc color/texture.").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("  3. Press Print and keep the GUI/server connection open until conversion finishes.").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("  Supported files: mp3, mp4, wav, ogg, flac, m4a, aac, webm, avi.").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("Manage your music:").withStyle(ChatFormatting.GRAY), false);
        sendCommandHelp(source, "/cstmusic list", "Show your available uploaded music names.");
        sendCommandHelp(source, "/cstmusic list all", "Show your uploaded music names including deleted or missing entries.");
        sendCommandHelp(source, "/cstmusic info <uploadedFile>", "Show status, owner, file size, checksum, and color.");
        sendCommandHelp(source, "/cstmusic delete <uploadedFile>", "Delete one of your music entries.");
        sendCommandHelp(source, "/cstmusic storage", "Show your storage usage and server storage if you are admin.");
        source.sendSuccess(() -> Component.literal("Avoid playback gaps by pre-downloading full audio:").withStyle(ChatFormatting.GRAY), false);
        sendCommandHelp(source, "/cstmusic download all", "Download all active Blueprint CD audio to your local cache now.");
        sendCommandHelp(source, "/cstmusic download auto 30m", "Automatically refresh your local cache every 30 minutes.");
        sendCommandHelp(source, "/cstmusic download off", "Disable automatic cache refresh.");
        source.sendSuccess(() -> Component.literal("Tips: use Download All before long sessions; previews play while full audio downloads.").withStyle(ChatFormatting.YELLOW), false);
        if (isAdmin(source)) {
            source.sendSuccess(() -> Component.literal("Admin creation commands:").withStyle(ChatFormatting.RED), false);
            sendCommandHelp(source, "/cstmusic upload \"Song Name\" \"C:\\Music\\song.mp3\"", "Admin-only client upload command.");
            sendCommandHelp(source, "/cstmusic createupload \"Disc Name\" #00AAFF \"song.mp3\"", "Admin-only command for writing an uploaded file to a held Blueprint CD.");
            sendCommandHelp(source, "/cstmusic create \"Disc Name\" #00AAFF \"music-import/song.mp3\"", "Admin-only server-side import command.");
            source.sendSuccess(() -> Component.literal("Admin tools:").withStyle(ChatFormatting.RED), false);
            sendCommandHelp(source, "/cstmusic admin storage", "Show detailed server storage usage.");
            sendCommandHelp(source, "/cstmusic admin list 1", "List all music entries by page.");
            sendCommandHelp(source, "/cstmusic admin info <musicId>", "Inspect any music entry.");
            sendCommandHelp(source, "/cstmusic admin delete <musicId>", "Delete any music entry.");
            sendCommandHelp(source, "/cstmusic admin play <musicId>", "Play any active entry for yourself.");
            sendCommandHelp(source, "/cstmusic admin debugdisc quadrants", "Create a render-test Blueprint CD with stored design pixels.");
            sendCommandHelp(source, "/cstmusic admin reload", "Reload config and music index.");
            sendCommandHelp(source, "/cstmusic admin repairindex", "Repair the music index from stored files.");
            sendCommandHelp(source, "/cstmusic admin ffmpeg status", "Show server FFmpeg mode and availability.");
            sendCommandHelp(source, "/cstmusic admin ffmpeg path <path>", "Use an explicit server FFmpeg executable path.");
            sendCommandHelp(source, "/cstmusic admin ffmpeg download confirm", "Download verified managed FFmpeg where supported.");
            sendCommandHelp(source, "/cstmusic admin ffmpeg reset", "Remove managed FFmpeg files and return to system mode.");
        }
        return 1;
    }

    private static void sendCommandHelp(CommandSourceStack source, String command, String tip) {
        source.sendSuccess(() -> Component.literal("  ")
                .append(Component.literal(command).withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent.SuggestCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(tip))))), false);
    }

    private static Component field(String label, String value, ChatFormatting valueColor) {
        return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(valueColor));
    }

    private static Component entryLine(String primary, String discName, String status) {
        return Component.literal("- ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(primary).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" | Disc: ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(discName).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(status).withStyle(statusColor(status)));
    }

    private static ChatFormatting statusColor(String status) {
        if (MusicStatus.ACTIVE.equals(status)) {
            return ChatFormatting.GREEN;
        }
        if (MusicStatus.DELETED.equals(status)) {
            return ChatFormatting.DARK_GRAY;
        }
        if (MusicStatus.MISSING.equals(status)) {
            return ChatFormatting.YELLOW;
        }
        return ChatFormatting.RED;
    }

    private static int create(CommandSourceStack source, String name, String colorAndLocation) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CreateArguments arguments = parseCreateTail(colorAndLocation);
        String result = Musicxcst.LIBRARY.createDiscForPlayer(source, player, name, arguments.hexColor(), arguments.location());
        source.sendSuccess(() -> Component.literal(result).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int createFromUpload(CommandSourceStack source, String name, String hexColor, String uploadedFile) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!isHexColor(hexColor)) {
            throw new IllegalArgumentException("Invalid hex color. Use RRGGBB or #RRGGBB.");
        }

        ServerPlayer player = source.getPlayerOrException();
        String result = Musicxcst.LIBRARY.createDiscFromUploadedFile(player, name, hexColor, uploadedFile);
        source.sendSuccess(() -> Component.literal(result).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int uploadFromClientPath(CommandSourceStack source, String name, String path) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!ServerPlayNetworking.canSend(player, ClientMusicUploadRequestPayload.TYPE)) {
            throw new IllegalArgumentException("This client cannot upload music files. Restart the client with the latest musicXCST jar.");
        }

        ServerPlayNetworking.send(player, new ClientMusicUploadRequestPayload(name, path));
        source.sendSuccess(() -> Component.literal("Starting client upload for '").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(name).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("'.")), false);
        return 1;
    }

    private static CreateArguments parseCreateTail(String input) {
        String trimmed = input == null ? "" : input.trim();
        int split = findFirstWhitespace(trimmed);
        if (split <= 0 || split >= trimmed.length() - 1) {
            throw new IllegalArgumentException("Usage: /cstmusic create \"Song Name\" #RRGGBB \"path/to/song.mp3\"");
        }

        String color = trimmed.substring(0, split).trim();
        String location = trimmed.substring(split + 1).trim();
        if (!isHexColor(color)) {
            throw new IllegalArgumentException("Invalid hex color. Use RRGGBB or #RRGGBB.");
        }
        return new CreateArguments(color, location);
    }

    private static int findFirstWhitespace(String input) {
        for (int index = 0; index < input.length(); index++) {
            if (Character.isWhitespace(input.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isHexColor(String input) {
        String normalized = input.startsWith("#") ? input.substring(1) : input;
        return normalized.toLowerCase(Locale.ROOT).matches("[0-9a-f]{6}");
    }

    private static int listOwn(CommandSourceStack source, boolean includeUnavailable) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<MusicEntry> entries = Musicxcst.LIBRARY.listEntriesForPlayer(player).stream()
                .filter(entry -> includeUnavailable || MusicStatus.ACTIVE.equals(entry.status))
                .toList();
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal(includeUnavailable
                    ? "No music entries registered."
                    : "No available music entries. Use /cstmusic list all to show deleted or missing entries.").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal(includeUnavailable
                ? "Your music uploads:"
                : "Your available music uploads:").withStyle(ChatFormatting.GOLD), false);
        for (MusicEntry entry : entries) {
            source.sendSuccess(() -> entryLine(playerReference(entry), entry.displayName, entry.status), false);
        }
        return entries.size();
    }

    private static int info(CommandSourceStack source, String musicRef, boolean adminScope) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        MusicEntry entry = adminScope
                ? Musicxcst.LIBRARY.requireAdminVisibleEntry(source, musicRef)
                : Musicxcst.LIBRARY.requirePlayerVisibleEntry(source.getPlayerOrException(), musicRef);

        source.sendSuccess(() -> field("Uploaded file", playerReference(entry), ChatFormatting.AQUA), false);
        source.sendSuccess(() -> field("Disc name", entry.displayName, ChatFormatting.GOLD), false);
        source.sendSuccess(() -> field("Music ID", entry.musicId, ChatFormatting.DARK_GRAY), false);
        source.sendSuccess(() -> field("Owner", entry.ownerName + " (" + entry.ownerUuid + ")", ChatFormatting.GRAY), false);
        source.sendSuccess(() -> field("Status", entry.status, statusColor(entry.status)), false);
        source.sendSuccess(() -> field("Color", entry.hexColor, ChatFormatting.LIGHT_PURPLE), false);
        source.sendSuccess(() -> field("Stored audio", storedAudioPath(entry), ChatFormatting.GRAY), false);
        source.sendSuccess(() -> field("Created", Long.toString(entry.createdAtEpochMillis), ChatFormatting.GRAY), false);
        source.sendSuccess(() -> field("File size", entry.fileSizeBytes + " bytes", ChatFormatting.AQUA), false);
        source.sendSuccess(() -> field("SHA-256", entry.sha256, ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    private static int infoUsage(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        source.sendSuccess(() -> Component.literal("Usage: ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("/cstmusic info <uploadedFile>").withStyle(ChatFormatting.AQUA)), false);
        return listOwn(source, false);
    }

    private static int delete(CommandSourceStack source, String musicRef, boolean adminScope) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String result = adminScope
                ? Musicxcst.LIBRARY.deleteEntryAsAdmin(source, musicRef)
                : Musicxcst.LIBRARY.deleteEntryAsOwner(source.getPlayerOrException(), musicRef);
        source.sendSuccess(() -> Component.literal(result).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int deleteUsage(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        source.sendSuccess(() -> Component.literal("Usage: ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("/cstmusic delete <uploadedFile>").withStyle(ChatFormatting.AQUA)), false);
        return listOwn(source, false);
    }

    private static int storage(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        StorageStats own = Musicxcst.LIBRARY.getPlayerStorage(player);
        source.sendSuccess(() -> field("Your storage", own.describe(), ChatFormatting.AQUA), false);
        source.sendSuccess(() -> field("Your file limit", Musicxcst.LIBRARY.describePlayerFileLimit(player), ChatFormatting.YELLOW), false);
        if (isAdmin(source)) {
            source.sendSuccess(() -> field("Server storage", Musicxcst.LIBRARY.getServerStorage().describe(), ChatFormatting.AQUA), false);
        }
        return 1;
    }

    private static int downloadAll(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String result = Musicxcst.LIBRARY.warmPlayerCache(player);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int downloadAuto(CommandSourceStack source, int intervalMinutes) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String result = Musicxcst.LIBRARY.setPlayerAutoDownload(player, intervalMinutes);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int downloadOff(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String result = Musicxcst.LIBRARY.disablePlayerAutoDownload(player);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int adminStorage(CommandSourceStack source) {
        StorageStats stats = Musicxcst.LIBRARY.getServerStorage();
        source.sendSuccess(() -> field("Server storage", stats.describeDetailed(), ChatFormatting.AQUA), false);
        return 1;
    }

    private static int adminList(CommandSourceStack source, int page) {
        List<MusicEntry> entries = Musicxcst.LIBRARY.listAllEntries();
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No music entries registered.").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        int totalPages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int clampedPage = Math.min(page, totalPages);
        int start = (clampedPage - 1) * PAGE_SIZE;
        int end = Math.min(entries.size(), start + PAGE_SIZE);

        source.sendSuccess(() -> Component.literal("All entries page ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(clampedPage + "/" + totalPages).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(":")), false);
        for (int index = start; index < end; index++) {
            MusicEntry entry = entries.get(index);
            source.sendSuccess(() -> Component.literal("- ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(entry.musicId).withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(displayName(entry)).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(entry.ownerName).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(entry.status).withStyle(statusColor(entry.status))), false);
        }
        return end - start;
    }

    private static int reload(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(Musicxcst.LIBRARY.reload()), true);
        return 1;
    }

    private static int repairIndex(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(Musicxcst.LIBRARY.repairIndex()), true);
        return 1;
    }

    private static int ffmpegStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(Musicxcst.LIBRARY.ffmpegStatus()), false);
        return 1;
    }

    private static int ffmpegPath(CommandSourceStack source, String path) {
        source.sendSuccess(() -> Component.literal(Musicxcst.LIBRARY.setFfmpegPath(path)), true);
        return 1;
    }

    private static int ffmpegDownloadConfirm(CommandSourceStack source) {
        Musicxcst.LIBRARY.downloadManagedFfmpeg(source);
        return 1;
    }

    private static int ffmpegReset(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(Musicxcst.LIBRARY.resetManagedFfmpeg()), true);
        return 1;
    }

    private static int adminPlay(CommandSourceStack source, String musicId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String result = Musicxcst.LIBRARY.playEntryForAdmin(source, player, musicId);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int giveDebugDisc(CommandSourceStack source, String pattern) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int[] pixels = switch (pattern) {
            case "checkerboard" -> checkerboardDesign();
            case "red" -> solidDesign(0xFFFF0000);
            case "green" -> solidDesign(0xFF00FF00);
            case "blue" -> solidDesign(0xFF0000FF);
            case "transparent-center" -> quadrantsDesign();
            case "invalid" -> quadrantsDesign();
            default -> quadrantsDesign();
        };
        verifyDiscDesignRoundTrip(pixels);

        DiscData data = new DiscData();
        data.musicId = "debug-" + UUID.randomUUID().toString().replace("-", "");
        data.displayName = "Debug " + pattern + " Blueprint CD";
        data.ownerUuid = player.getUUID().toString();
        data.ownerName = player.getName().getString();
        data.hexColor = pattern.equals("invalid") ? "#C93A3A" : "#00AAFF";
        data.designPixels = DiscData.sanitizeDesign(pixels);
        data.designId = DiscData.encodeDesignId(data.designPixels);
        data.status = pattern.equals("invalid") ? MusicStatus.INVALID : MusicStatus.ACTIVE;
        data.schemaVersion = Musicxcst.DISC_SCHEMA_VERSION;

        ItemStack stack = new ItemStack(ModItems.BLUEPRINT_CD);
        DiscData.writeToStack(stack, data);
        DiscData readBack = DiscData.fromStack(stack);
        if (readBack == null || !data.designId.equals(readBack.designId)) {
            throw new IllegalStateException("Debug disc stack write/read lost design pixels.");
        }

        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        source.sendSuccess(() -> Component.literal("Created debug Blueprint CD " + pattern + " with " + DiscData.designDebugSummary(data.designPixels)), false);
        return 1;
    }

    private static void verifyDiscDesignRoundTrip(int[] pixels) {
        int[] sanitized = DiscData.sanitizeDesign(pixels);
        String designId = DiscData.encodeDesignId(sanitized);
        int[] decoded = DiscData.decodeDesignId(designId)
                .orElseThrow(() -> new IllegalStateException("Debug disc design ID failed to decode."));
        for (int index = 0; index < sanitized.length; index++) {
            if (sanitized[index] != decoded[index]) {
                throw new IllegalStateException("Debug disc design round-trip mismatch at pixel " + index + ".");
            }
        }
    }

    private static int[] quadrantsDesign() {
        int[] pixels = new int[DiscData.DESIGN_PIXELS];
        for (int y = 0; y < DiscData.DESIGN_SIZE; y++) {
            for (int x = 0; x < DiscData.DESIGN_SIZE; x++) {
                int color = y < 8
                        ? (x < 8 ? 0xFFFF0000 : 0xFF00FF00)
                        : (x < 8 ? 0xFF0000FF : 0xFFFFFF00);
                pixels[y * DiscData.DESIGN_SIZE + x] = discMask(x, y) ? color : 0;
            }
        }
        return pixels;
    }

    private static int[] checkerboardDesign() {
        int[] pixels = new int[DiscData.DESIGN_PIXELS];
        for (int y = 0; y < DiscData.DESIGN_SIZE; y++) {
            for (int x = 0; x < DiscData.DESIGN_SIZE; x++) {
                if (!discMask(x, y)) {
                    continue;
                }
                pixels[y * DiscData.DESIGN_SIZE + x] = ((x + y) & 1) == 0 ? 0xFFFFFFFF : 0xFF111111;
            }
        }
        return pixels;
    }

    private static int[] solidDesign(int color) {
        int[] pixels = new int[DiscData.DESIGN_PIXELS];
        for (int y = 0; y < DiscData.DESIGN_SIZE; y++) {
            for (int x = 0; x < DiscData.DESIGN_SIZE; x++) {
                if (discMask(x, y)) {
                    pixels[y * DiscData.DESIGN_SIZE + x] = color;
                }
            }
        }
        return pixels;
    }

    private static boolean discMask(int x, int y) {
        float dx = x + 0.5F - 8.0F;
        float dy = y + 0.5F - 8.0F;
        float distanceSquared = dx * dx + dy * dy;
        return distanceSquared <= 58.0F && distanceSquared >= 4.0F;
    }

    private static boolean isAdmin(CommandSourceStack source) {
        return source.permissions() instanceof LevelBasedPermissionSet levelBased
                && levelBased.level().isEqualOrHigherThan(PermissionLevel.GAMEMASTERS);
    }

    private static CompletableFuture<Suggestions> suggestOwnMusicIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            return SharedSuggestionProvider.suggest(
                    Musicxcst.LIBRARY.listEntriesForPlayer(player).stream()
                            .filter(entry -> !MusicStatus.isInvalidLike(entry.status))
                            .map(entry -> entry.musicId),
                    builder
            );
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            return Suggestions.empty();
        }
    }

    private static CompletableFuture<Suggestions> suggestOwnMusicReferences(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Musicxcst.LIBRARY.listEntriesForPlayer(player).stream()
                    .filter(entry -> !MusicStatus.isInvalidLike(entry.status))
                    .map(CstMusicCommands::playerReference)
                    .distinct()
                    .map(StringArgumentType::escapeIfRequired)
                    .forEach(builder::suggest);
            return builder.buildFuture();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            return Suggestions.empty();
        }
    }

    private static CompletableFuture<Suggestions> suggestAllMusicIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                Musicxcst.LIBRARY.listAllEntries().stream()
                        .filter(entry -> !MusicStatus.isInvalidLike(entry.status))
                        .map(entry -> entry.musicId),
                builder
        );
    }

    private static CompletableFuture<Suggestions> suggestUploadedFiles(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            return SharedSuggestionProvider.suggest(Musicxcst.LIBRARY.listUploadedFilesForPlayer(player), builder);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            return Suggestions.empty();
        }
    }

    private static CompletableFuture<Suggestions> suggestHexColors(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.of("#C93A3A", "#00AAFF", "C93A3A"), builder);
    }

    private static String playerReference(MusicEntry entry) {
        if (entry.originalFileName != null && !entry.originalFileName.isBlank()) {
            return entry.originalFileName;
        }
        if (entry.displayName != null && !entry.displayName.isBlank()) {
            return entry.displayName;
        }
        return entry.musicId;
    }

    private static String storedAudioPath(MusicEntry entry) {
        if (entry.normalizedRelativePath != null && !entry.normalizedRelativePath.isBlank()) {
            return "normalized/" + entry.normalizedRelativePath;
        }
        if (entry.safeRelativePath != null && !entry.safeRelativePath.isBlank()) {
            return entry.safeRelativePath;
        }
        return "-";
    }

    private static String displayName(MusicEntry entry) {
        String uploaded = playerReference(entry);
        if (entry.displayName == null || entry.displayName.isBlank() || entry.displayName.equals(uploaded)) {
            return uploaded;
        }
        return uploaded + " / " + entry.displayName;
    }

    private record CreateArguments(String hexColor, String location) {
    }
}
