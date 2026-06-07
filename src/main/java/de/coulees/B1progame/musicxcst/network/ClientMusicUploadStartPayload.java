package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientMusicUploadStartPayload(String uploadId, String uploadName, String fileName, long sizeBytes, long durationMillis) implements CustomPacketPayload {
    public static final Type<ClientMusicUploadStartPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "client_music_upload_start"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientMusicUploadStartPayload> CODEC = StreamCodec.ofMember(ClientMusicUploadStartPayload::write, ClientMusicUploadStartPayload::read);

    private static ClientMusicUploadStartPayload read(RegistryFriendlyByteBuf buffer) {
        return new ClientMusicUploadStartPayload(
                buffer.readUtf(64),
                buffer.readUtf(96),
                buffer.readUtf(128),
                buffer.readLong(),
                buffer.readLong()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(uploadId, 64);
        buffer.writeUtf(uploadName, 96);
        buffer.writeUtf(fileName, 128);
        buffer.writeLong(sizeBytes);
        buffer.writeLong(durationMillis);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
