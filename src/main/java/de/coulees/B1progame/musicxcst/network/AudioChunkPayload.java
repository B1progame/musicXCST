package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AudioChunkPayload(String musicId, long offset, long totalSize, String sha256, byte[] data, boolean last, boolean preview) implements CustomPacketPayload {
    public static final Type<AudioChunkPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "audio_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AudioChunkPayload> CODEC = StreamCodec.ofMember(AudioChunkPayload::write, AudioChunkPayload::read);

    private static AudioChunkPayload read(RegistryFriendlyByteBuf buffer) {
        return new AudioChunkPayload(
                buffer.readUtf(128),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readUtf(128),
                buffer.readByteArray(256 * 1024),
                buffer.readBoolean(),
                buffer.readBoolean()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(musicId, 128);
        buffer.writeLong(offset);
        buffer.writeLong(totalSize);
        buffer.writeUtf(sha256, 128);
        buffer.writeByteArray(data);
        buffer.writeBoolean(last);
        buffer.writeBoolean(preview);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
