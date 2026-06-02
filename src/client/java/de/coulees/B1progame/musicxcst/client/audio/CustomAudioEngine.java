package de.coulees.B1progame.musicxcst.client.audio;

import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.network.JukeboxStartPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxStopPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

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
        play(payload, audioFile, false);
    }

    public static void playPreview(JukeboxStartPayload payload, Path audioFile) {
        play(payload, audioFile, true);
    }

    private static void play(JukeboxStartPayload payload, Path audioFile, boolean preview) {
        AudioPlayer.PlayingSound current = PLAYING.get(payload.pos());
        if (current != null && current.matches(payload) && current.active()) {
            return;
        }

        stop(new JukeboxStopPayload(payload.pos()));
        try {
            AudioPlayer.PlayingSound sound = preview
                    ? AudioPlayer.playPreview(payload, audioFile)
                    : AudioPlayer.play(payload, audioFile);
            if (sound != null) {
                PLAYING.put(payload.pos().immutable(), sound);
            }
        } catch (IOException exception) {
            Musicxcst.LOGGER.warn("Failed to decode Blueprint CD audio '{}': {}", payload.displayName(), exception.getMessage());
        }
    }

    public static void replace(JukeboxStartPayload payload, Path audioFile) {
        stop(new JukeboxStopPayload(payload.pos()));
        play(payload, audioFile);
    }

    public static void stop(JukeboxStopPayload payload) {
        AudioPlayer.PlayingSound sound = PLAYING.remove(payload.pos());
        if (sound != null) {
            sound.close();
        }
    }

    public static void tick(Minecraft client) {
        Iterator<Map.Entry<BlockPos, AudioPlayer.PlayingSound>> iterator = PLAYING.entrySet().iterator();
        while (iterator.hasNext()) {
            AudioPlayer.PlayingSound sound = iterator.next().getValue();
            if (!sound.active()) {
                sound.close();
                iterator.remove();
                continue;
            }

            sound.resumeIfPaused();
            updateDistanceGain(client, sound);
        }
    }

    public static void resumeAll() {
        for (AudioPlayer.PlayingSound sound : PLAYING.values()) {
            sound.resumeIfPaused();
        }
    }

    public static void stopAll() {
        for (AudioPlayer.PlayingSound sound : PLAYING.values()) {
            sound.close();
        }
        PLAYING.clear();
    }

    private static void updateDistanceGain(Minecraft client, AudioPlayer.PlayingSound sound) {
        float volume = soundCategoryVolume(client) * jukeboxVolume(sound);
        if (!sound.positional()) {
            sound.setGain(volume);
            return;
        }

        Player player = client.player;
        if (player == null) {
            sound.setGain(0.0F);
            return;
        }

        double radius = Math.max(1.0D, sound.radiusBlocks());
        double distance = player.position().distanceTo(Vec3.atCenterOf(sound.pos()));
        if (distance >= radius) {
            sound.setGain(0.0F);
            return;
        }

        double normalized = distance / radius;
        sound.setGain((float) ((1.0D - normalized) * (1.0D - normalized)) * volume);
    }

    private static float soundCategoryVolume(Minecraft client) {
        return client.options.getSoundSourceVolume(SoundSource.MASTER) * client.options.getSoundSourceVolume(SoundSource.RECORDS);
    }

    private static float jukeboxVolume(AudioPlayer.PlayingSound sound) {
        return sound.volumePercent() / 100.0F;
    }
}
