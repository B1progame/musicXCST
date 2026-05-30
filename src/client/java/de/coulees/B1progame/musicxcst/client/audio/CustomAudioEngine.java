package de.coulees.B1progame.musicxcst.client.audio;

import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.network.JukeboxStartPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxStopPayload;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class CustomAudioEngine {
    private static final Map<BlockPos, AudioPlayer.PlayingSound> PLAYING = new HashMap<>();

    private CustomAudioEngine() {
    }

    public static void play(JukeboxStartPayload payload, Path audioFile) {
        stop(new JukeboxStopPayload(payload.pos()));
        try {
            PLAYING.put(payload.pos().immutable(), AudioPlayer.play(payload, audioFile));
        } catch (IOException exception) {
            Musicxcst.LOGGER.warn("Failed to decode Blueprint CD audio '{}': {}", payload.displayName(), exception.getMessage());
        }
    }

    public static void stop(JukeboxStopPayload payload) {
        AudioPlayer.PlayingSound sound = PLAYING.remove(payload.pos());
        if (sound != null) {
            sound.close();
        }
    }

    public static void tick() {
        Iterator<Map.Entry<BlockPos, AudioPlayer.PlayingSound>> iterator = PLAYING.entrySet().iterator();
        while (iterator.hasNext()) {
            AudioPlayer.PlayingSound sound = iterator.next().getValue();
            if (!sound.active()) {
                sound.close();
                iterator.remove();
            }
        }
    }

    public static void stopAll() {
        for (AudioPlayer.PlayingSound sound : PLAYING.values()) {
            sound.close();
        }
        PLAYING.clear();
    }
}
