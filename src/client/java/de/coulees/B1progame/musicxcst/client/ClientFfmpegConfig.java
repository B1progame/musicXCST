package de.coulees.B1progame.musicxcst.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.config.CstMusicConfig;
import de.coulees.B1progame.musicxcst.media.FfmpegLocator;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ClientFfmpegConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ClientFfmpegConfig() {
    }

    public static CstMusicConfig load(Minecraft client) {
        Path path = configPath(client);
        try {
            Files.createDirectories(path.getParent());
            CstMusicConfig config;
            if (Files.notExists(path)) {
                config = new CstMusicConfig();
                save(client, config);
                return config;
            }
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, CstMusicConfig.class);
            }
            if (config == null) {
                config = new CstMusicConfig();
            }
            if (!FfmpegLocator.normalizedMode(config).equals(config.ffmpegMode)) {
                config.ffmpegMode = FfmpegLocator.normalizedMode(config);
                save(client, config);
            }
            return config;
        } catch (IOException exception) {
            Musicxcst.LOGGER.warn("Failed to load client FFmpeg config '{}': {}", path, exception.getMessage());
            return new CstMusicConfig();
        }
    }

    public static void save(Minecraft client, CstMusicConfig config) {
        Path path = configPath(client);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException exception) {
            Musicxcst.LOGGER.warn("Failed to save client FFmpeg config '{}': {}", path, exception.getMessage());
        }
    }

    private static Path configPath(Minecraft client) {
        return client.gameDirectory.toPath()
                .resolve("config")
                .resolve(Musicxcst.MOD_ID + "-client.json")
                .normalize();
    }
}
