package de.coulees.B1progame.musicxcst.client;

import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.network.JukeboxStartPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxStopPayload;
import net.minecraft.core.BlockPos;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class ClientJukeboxAudio {
    private static final Map<BlockPos, PlayingSound> PLAYING = new HashMap<>();

    private ClientJukeboxAudio() {
    }

    public static void start(JukeboxStartPayload payload) {
        stop(new JukeboxStopPayload(payload.pos()));

        ByteBuffer encoded = BufferUtils.createByteBuffer(payload.audioBytes().length);
        encoded.put(payload.audioBytes());
        encoded.flip();

        IntBuffer channels = BufferUtils.createIntBuffer(1);
        IntBuffer sampleRate = BufferUtils.createIntBuffer(1);
        ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(encoded, channels, sampleRate);
        if (pcm == null) {
            Musicxcst.LOGGER.warn("Failed to decode Blueprint CD OGG audio '{}'.", payload.displayName());
            return;
        }

        int format = channels.get(0) == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
        int buffer = AL10.alGenBuffers();
        int source = AL10.alGenSources();
        AL10.alBufferData(buffer, format, pcm, sampleRate.get(0));
        MemoryUtil.memFree(pcm);

        BlockPos pos = payload.pos();
        AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
        AL10.alSourcef(source, AL10.AL_GAIN, 1.0F);
        AL10.alSourcef(source, AL10.AL_PITCH, 1.0F);
        AL10.alSource3f(source, AL10.AL_POSITION, pos.getX() + 0.5F, pos.getY() + 0.5F, pos.getZ() + 0.5F);
        AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 16.0F);
        AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, 96.0F);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 1.0F);
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
        AL10.alSourcePlay(source);

        PLAYING.put(pos.immutable(), new PlayingSound(source, buffer));
    }

    public static void stop(JukeboxStopPayload payload) {
        PlayingSound sound = PLAYING.remove(payload.pos());
        if (sound != null) {
            sound.close();
        }
    }

    public static void tick() {
        Iterator<Map.Entry<BlockPos, PlayingSound>> iterator = PLAYING.entrySet().iterator();
        while (iterator.hasNext()) {
            PlayingSound sound = iterator.next().getValue();
            int state = AL10.alGetSourcei(sound.source, AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING && state != AL10.AL_PAUSED) {
                sound.close();
                iterator.remove();
            }
        }
    }

    public static void stopAll() {
        for (PlayingSound sound : PLAYING.values()) {
            sound.close();
        }
        PLAYING.clear();
    }

    private record PlayingSound(int source, int buffer) implements AutoCloseable {
        @Override
        public void close() {
            AL10.alSourceStop(source);
            AL10.alDeleteSources(source);
            AL10.alDeleteBuffers(buffer);
        }
    }
}
