package de.coulees.B1progame.musicxcst.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.data.MusicEntry;
import de.coulees.B1progame.musicxcst.data.MusicStatus;
import de.coulees.B1progame.musicxcst.data.StorageStats;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
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
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("colorAndLocation", StringArgumentType.greedyString())
                                        .executes(ctx -> create(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                StringArgumentType.getString(ctx, "colorAndLocation")
                                        )))))
                .then(Commands.literal("list")
                        .executes(ctx -> listOwn(ctx.getSource())))
                .then(Commands.literal("info")
                        .then(Commands.argument("musicId", StringArgumentType.word())
                                .suggests(CstMusicCommands::suggestOwnMusicIds)
                                .executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "musicId"), false))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("musicId", StringArgumentType.word())
                                .suggests(CstMusicCommands::suggestOwnMusicIds)
                                .executes(ctx -> delete(ctx.getSource(), StringArgumentType.getString(ctx, "musicId"), false))))
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
                        .then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource())))
                        .then(Commands.literal("repairindex").executes(ctx -> repairIndex(ctx.getSource())))));
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("musicXCST commands:"), false);
        source.sendSuccess(() -> Component.literal("/cstmusic help"), false);
        source.sendSuccess(() -> Component.literal("/cstmusic create <name> <hexColor> <location>"), false);
        source.sendSuccess(() -> Component.literal("/cstmusic list"), false);
        source.sendSuccess(() -> Component.literal("/cstmusic info <musicId>"), false);
        source.sendSuccess(() -> Component.literal("/cstmusic delete <musicId>"), false);
        source.sendSuccess(() -> Component.literal("/cstmusic storage"), false);
        source.sendSuccess(() -> Component.literal("/cstmusic download all"), false);
        source.sendSuccess(() -> Component.literal("/cstmusic download auto <30m|1h|1h30m>"), false);
        source.sendSuccess(() -> Component.literal("/cstmusic download off"), false);
        if (isAdmin(source)) {
            source.sendSuccess(() -> Component.literal("/cstmusic admin storage"), false);
            source.sendSuccess(() -> Component.literal("/cstmusic admin list [page]"), false);
            source.sendSuccess(() -> Component.literal("/cstmusic admin info <musicId>"), false);
            source.sendSuccess(() -> Component.literal("/cstmusic admin delete <musicId>"), false);
            source.sendSuccess(() -> Component.literal("/cstmusic admin play <musicId>"), false);
            source.sendSuccess(() -> Component.literal("/cstmusic admin reload"), false);
            source.sendSuccess(() -> Component.literal("/cstmusic admin repairindex"), false);
        }
        return 1;
    }

    private static int create(CommandSourceStack source, String name, String colorAndLocation) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CreateArguments arguments = parseCreateTail(colorAndLocation);
        String result = Musicxcst.LIBRARY.createDiscForPlayer(source, player, name, arguments.hexColor(), arguments.location());
        source.sendSuccess(() -> Component.literal(result), false);
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

    private static int listOwn(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<MusicEntry> entries = Musicxcst.LIBRARY.listEntriesForPlayer(player);
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No music entries registered."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("Your registered music entries:"), false);
        for (MusicEntry entry : entries) {
            source.sendSuccess(() -> Component.literal("- " + entry.musicId + " | " + entry.displayName + " | " + entry.status), false);
        }
        return entries.size();
    }

    private static int info(CommandSourceStack source, String musicId, boolean adminScope) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        MusicEntry entry = adminScope
                ? Musicxcst.LIBRARY.requireAdminVisibleEntry(source, musicId)
                : Musicxcst.LIBRARY.requirePlayerVisibleEntry(source.getPlayerOrException(), musicId);

        source.sendSuccess(() -> Component.literal("Music ID: " + entry.musicId), false);
        source.sendSuccess(() -> Component.literal("Name: " + entry.displayName), false);
        source.sendSuccess(() -> Component.literal("Owner: " + entry.ownerName + " (" + entry.ownerUuid + ")"), false);
        source.sendSuccess(() -> Component.literal("Status: " + entry.status), false);
        source.sendSuccess(() -> Component.literal("Color: " + entry.hexColor), false);
        source.sendSuccess(() -> Component.literal("Import Path: " + entry.safeRelativePath), false);
        source.sendSuccess(() -> Component.literal("Original Name: " + entry.originalFileName), false);
        source.sendSuccess(() -> Component.literal("Created: " + entry.createdAtEpochMillis), false);
        source.sendSuccess(() -> Component.literal("File Size: " + entry.fileSizeBytes + " bytes"), false);
        source.sendSuccess(() -> Component.literal("SHA-256: " + entry.sha256), false);
        return 1;
    }

    private static int delete(CommandSourceStack source, String musicId, boolean adminScope) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String result = adminScope
                ? Musicxcst.LIBRARY.deleteEntryAsAdmin(source, musicId)
                : Musicxcst.LIBRARY.deleteEntryAsOwner(source.getPlayerOrException(), musicId);
        source.sendSuccess(() -> Component.literal(result), true);
        return 1;
    }

    private static int storage(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        StorageStats own = Musicxcst.LIBRARY.getPlayerStorage(player);
        source.sendSuccess(() -> Component.literal("Your storage: " + own.describe()), false);
        if (isAdmin(source)) {
            source.sendSuccess(() -> Component.literal("Server storage: " + Musicxcst.LIBRARY.getServerStorage().describe()), false);
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
        source.sendSuccess(() -> Component.literal("Server storage: " + stats.describeDetailed()), false);
        return 1;
    }

    private static int adminList(CommandSourceStack source, int page) {
        List<MusicEntry> entries = Musicxcst.LIBRARY.listAllEntries();
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No music entries registered."), false);
            return 1;
        }

        int totalPages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int clampedPage = Math.min(page, totalPages);
        int start = (clampedPage - 1) * PAGE_SIZE;
        int end = Math.min(entries.size(), start + PAGE_SIZE);

        source.sendSuccess(() -> Component.literal("All entries page " + clampedPage + "/" + totalPages + ":"), false);
        for (int index = start; index < end; index++) {
            MusicEntry entry = entries.get(index);
            source.sendSuccess(() -> Component.literal("- " + entry.musicId + " | " + entry.displayName + " | " + entry.ownerName + " | " + entry.status), false);
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

    private static int adminPlay(CommandSourceStack source, String musicId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String result = Musicxcst.LIBRARY.playEntryForAdmin(source, player, musicId);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static boolean isAdmin(CommandSourceStack source) {
        return source.permissions() instanceof LevelBasedPermissionSet levelBased
                && levelBased.level().isEqualOrHigherThan(PermissionLevel.ADMINS);
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

    private static CompletableFuture<Suggestions> suggestAllMusicIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                Musicxcst.LIBRARY.listAllEntries().stream()
                        .filter(entry -> !MusicStatus.isInvalidLike(entry.status))
                        .map(entry -> entry.musicId),
                builder
        );
    }

    private record CreateArguments(String hexColor, String location) {
    }
}
