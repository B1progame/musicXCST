package de.coulees.B1progame.musicxcst.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.network.ClientMusicUploadChunkPayload;
import de.coulees.B1progame.musicxcst.network.ClientMusicUploadStartPayload;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class ClientMusicUploader {
    private static final int CHUNK_BYTES = 64 * 1024;

    private ClientMusicUploader() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("cstmusic_upload")
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.string())
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("hexColor", StringArgumentType.word())
                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("path", StringArgumentType.greedyString())
                                        .executes(context -> upload(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "hexColor"),
                                                StringArgumentType.getString(context, "path")
                                        )))))));
    }

    private static int upload(FabricClientCommandSource source, String name, String hexColor, String pathText) {
        Path path = Path.of(stripWrappingQuotes(pathText.trim())).normalize();
        if (!Files.isRegularFile(path)) {
            source.sendFeedback(Component.literal("Music file was not found on this computer."));
            return 0;
        }

        Thread uploadThread = new Thread(() -> uploadFile(source, name, hexColor, path), "musicxcst-client-upload");
        uploadThread.setDaemon(true);
        uploadThread.start();
        source.sendFeedback(Component.literal("Started music upload. Keep the server connection open."));
        return 1;
    }

    private static void uploadFile(FabricClientCommandSource source, String name, String hexColor, Path path) {
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        try {
            long sizeBytes = Files.size(path);
            ClientPlayNetworking.send(new ClientMusicUploadStartPayload(uploadId, name, hexColor, path.getFileName().toString(), sizeBytes));
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[CHUNK_BYTES];
                long offset = 0L;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    byte[] data = read == buffer.length ? buffer.clone() : java.util.Arrays.copyOf(buffer, read);
                    offset += read;
                    ClientPlayNetworking.send(new ClientMusicUploadChunkPayload(uploadId, offset - read, data, offset >= sizeBytes));
                    Thread.sleep(250L);
                }
            }
            source.sendFeedback(Component.literal("Music upload finished. The server is processing it."));
        } catch (IOException exception) {
            Musicxcst.LOGGER.warn("Failed to upload music file '{}': {}", path.getFileName(), exception.getMessage());
            source.sendError(Component.literal("Failed to read music file: " + exception.getMessage()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            source.sendError(Component.literal("Music upload was interrupted."));
        }
    }

    private static String stripWrappingQuotes(String input) {
        if (input.length() >= 2 && input.startsWith("\"") && input.endsWith("\"")) {
            return input.substring(1, input.length() - 1);
        }
        return input;
    }
}
