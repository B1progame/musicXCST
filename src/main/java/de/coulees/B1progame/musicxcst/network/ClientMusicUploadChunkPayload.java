package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientMusicUploadChunkPayload(String uploadId, long offset, byte[] data, boolean last) implements CustomPacketPayload {
    public static final Type<ClientMusicUploadChunkPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "client_music_upload_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientMusicUploadChunkPayload> CODEC = StreamCodec.ofMember(ClientMusicUploadChunkPayload::write, ClientMusicUploadChunkPayload::read);

    private static ClientMusicUploadChunkPayload read(RegistryFriendlyByteBuf buffer) {
        return new ClientMusicUploadChunkPayload(
                buffer.readUtf(64),
                buffer.readLong(),
                buffer.readByteArray(256 * 1024),
                buffer.readBoolean()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(uploadId, 64);
        buffer.writeLong(offset);
        buffer.writeByteArray(data);
        buffer.writeBoolean(last);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
