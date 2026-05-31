package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientMusicUploadStartPayload(String uploadId, String displayName, String hexColor, String fileName, long sizeBytes) implements CustomPacketPayload {
    public static final Type<ClientMusicUploadStartPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "client_music_upload_start"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientMusicUploadStartPayload> CODEC = StreamCodec.ofMember(ClientMusicUploadStartPayload::write, ClientMusicUploadStartPayload::read);

    private static ClientMusicUploadStartPayload read(RegistryFriendlyByteBuf buffer) {
        return new ClientMusicUploadStartPayload(
                buffer.readUtf(64),
                buffer.readUtf(128),
                buffer.readUtf(16),
                buffer.readUtf(128),
                buffer.readLong()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(uploadId, 64);
        buffer.writeUtf(displayName, 128);
        buffer.writeUtf(hexColor, 16);
        buffer.writeUtf(fileName, 128);
        buffer.writeLong(sizeBytes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
