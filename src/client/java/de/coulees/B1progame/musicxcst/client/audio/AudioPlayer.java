package de.coulees.B1progame.musicxcst.client.audio;

import de.coulees.B1progame.musicxcst.network.JukeboxStartPayload;
import net.minecraft.core.BlockPos;
import org.lwjgl.openal.AL10;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.file.Path;

public final class AudioPlayer {
    private AudioPlayer() {
    }

    public static PlayingSound play(JukeboxStartPayload payload, Path file) throws IOException {
        AudioDecoder.DecodedAudio decoded = AudioDecoder.decodeOgg(file);
        int format = decoded.channels() == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
        int buffer = AL10.alGenBuffers();
        int source = AL10.alGenSources();
        AL10.alBufferData(buffer, format, decoded.pcm(), decoded.sampleRate());
        MemoryUtil.memFree(decoded.pcm());

        AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
        AL10.alSourcef(source, AL10.AL_GAIN, 1.0F);
        AL10.alSourcef(source, AL10.AL_PITCH, 1.0F);

        if (payload.positional()) {
            BlockPos pos = payload.pos();
            AL10.alSource3f(source, AL10.AL_POSITION, pos.getX() + 0.5F, pos.getY() + 0.5F, pos.getZ() + 0.5F);
            AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 16.0F);
            AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, payload.radiusBlocks());
            AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 1.0F);
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
        } else {
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0.0F, 0.0F, 0.0F);
        }

        AL10.alSourcePlay(source);
        return new PlayingSound(source, buffer);
    }

    public record PlayingSound(int source, int buffer) implements AutoCloseable {
        public boolean active() {
            int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
            return state == AL10.AL_PLAYING || state == AL10.AL_PAUSED;
        }

        @Override
        public void close() {
            AL10.alSourceStop(source);
            AL10.alDeleteSources(source);
            AL10.alDeleteBuffers(buffer);
        }
    }
}
