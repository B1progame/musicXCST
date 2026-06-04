package de.coulees.B1progame.musicxcst.client;

import de.coulees.B1progame.musicxcst.config.CstMusicConfig;
import de.coulees.B1progame.musicxcst.media.FfmpegLocator;
import net.minecraft.client.Minecraft;

public final class ClientFfmpegStatus {
    private static final FfmpegLocator FFMPEG_LOCATOR = new FfmpegLocator();
    private static boolean checked;
    private static boolean missing;

    private ClientFfmpegStatus() {
    }

    public static void checkOnce(Minecraft client) {
        if (checked) {
            return;
        }
        checked = true;
        CstMusicConfig config = ClientFfmpegConfig.load(client);
        String mode = FfmpegLocator.normalizedMode(config);
        missing = !"disabled".equals(mode)
                && (!config.clientFfmpegSetupAcknowledged || FFMPEG_LOCATOR.locate(client.gameDirectory.toPath(), config).isEmpty());
    }

    public static boolean isMissing() {
        return missing;
    }

    public static void markConfigured() {
        missing = false;
    }

    public static void markConfigured(Minecraft client) {
        CstMusicConfig config = ClientFfmpegConfig.load(client);
        config.clientFfmpegSetupAcknowledged = true;
        ClientFfmpegConfig.save(client, config);
        markConfigured();
    }
}
