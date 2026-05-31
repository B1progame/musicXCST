package de.coulees.B1progame.musicxcst.client.audio;

import de.coulees.B1progame.musicxcst.network.JukeboxStartPayload;
import net.minecraft.core.BlockPos;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.system.libc.LibCStdlib;

import java.io.IOException;
import java.nio.file.Path;

public final class AudioPlayer {
    private AudioPlayer() {
    }

    public static PlayingSound play(JukeboxStartPayload payload, Path file) throws IOException {
        AudioDecoder.DecodedAudio decoded = AudioDecoder.decodeOgg(file);
        int format = decoded.channels() == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
        float durationSeconds = decoded.pcm().remaining() / (float) decoded.channels() / decoded.sampleRate();
        float elapsedSeconds = PlaybackSyncManager.elapsedMillis(payload) / 1000.0F;
        if (elapsedSeconds >= durationSeconds) {
            LibCStdlib.free(decoded.pcm());
            return null;
        }

        int buffer = AL10.alGenBuffers();
        int source = AL10.alGenSources();
        AL10.alBufferData(buffer, format, decoded.pcm(), decoded.sampleRate());
        LibCStdlib.free(decoded.pcm());

        AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
        AL10.alSourcef(source, AL10.AL_GAIN, 1.0F);
        AL10.alSourcef(source, AL10.AL_PITCH, 1.0F);

        if (payload.positional()) {
            BlockPos pos = payload.pos();
            AL10.alSource3f(source, AL10.AL_POSITION, pos.getX() + 0.5F, pos.getY() + 0.5F, pos.getZ() + 0.5F);
            AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 16.0F);
            AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, payload.radiusBlocks());
            AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0F);
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
        } else {
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0.0F, 0.0F, 0.0F);
        }

        AL10.alSourcef(source, AL11.AL_SEC_OFFSET, elapsedSeconds);
        AL10.alSourcePlay(source);
        return new PlayingSound(source, buffer, payload.musicId(), payload.startedAtMillis(), payload.pos(), payload.radiusBlocks(), payload.positional());
    }

    public record PlayingSound(int source, int buffer, String musicId, long startedAtMillis, BlockPos pos, int radiusBlocks, boolean positional) implements AutoCloseable {
        public boolean matches(JukeboxStartPayload payload) {
            return musicId.equals(payload.musicId()) && startedAtMillis == payload.startedAtMillis();
        }

        public void setGain(float gain) {
            AL10.alSourcef(source, AL10.AL_GAIN, Math.max(0.0F, Math.min(1.0F, gain)));
        }

        public boolean active() {
            int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
            return state == AL10.AL_PLAYING || state == AL10.AL_PAUSED;
        }

        public void resumeIfPaused() {
            if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PAUSED) {
                AL10.alSourcePlay(source);
            }
        }

        @Override
        public void close() {
            AL10.alSourceStop(source);
            AL10.alDeleteSources(source);
            AL10.alDeleteBuffers(buffer);
        }
    }
}
