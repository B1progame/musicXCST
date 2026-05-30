package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AudioChunkRequestPayload(String musicId, long offset, int maxBytes) implements CustomPacketPayload {
    public static final Type<AudioChunkRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "audio_chunk_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AudioChunkRequestPayload> CODEC = StreamCodec.ofMember(AudioChunkRequestPayload::write, AudioChunkRequestPayload::read);

    private static AudioChunkRequestPayload read(RegistryFriendlyByteBuf buffer) {
        return new AudioChunkRequestPayload(buffer.readUtf(128), buffer.readLong(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(musicId, 128);
        buffer.writeLong(offset);
        buffer.writeVarInt(maxBytes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
