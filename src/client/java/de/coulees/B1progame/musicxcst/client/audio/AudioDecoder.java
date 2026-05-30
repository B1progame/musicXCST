package de.coulees.B1progame.musicxcst.client.audio;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AudioDecoder {
    private AudioDecoder() {
    }

    public static DecodedAudio decodeOgg(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        ByteBuffer encoded = BufferUtils.createByteBuffer(bytes.length);
        encoded.put(bytes);
        encoded.flip();

        IntBuffer channels = BufferUtils.createIntBuffer(1);
        IntBuffer sampleRate = BufferUtils.createIntBuffer(1);
        ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(encoded, channels, sampleRate);
        if (pcm == null) {
            throw new IOException("STBVorbis could not decode OGG audio.");
        }
        return new DecodedAudio(pcm, channels.get(0), sampleRate.get(0));
    }

    public record DecodedAudio(ShortBuffer pcm, int channels, int sampleRate) {
    }
}
