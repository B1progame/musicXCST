package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClientMusicUploadRequestPayload(String name, String path) implements CustomPacketPayload {
    public static final Type<ClientMusicUploadRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "client_music_upload_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientMusicUploadRequestPayload> CODEC = StreamCodec.ofMember(ClientMusicUploadRequestPayload::write, ClientMusicUploadRequestPayload::read);

    private static ClientMusicUploadRequestPayload read(RegistryFriendlyByteBuf buffer) {
        return new ClientMusicUploadRequestPayload(buffer.readUtf(96), buffer.readUtf(1024));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(name, 96);
        buffer.writeUtf(path, 1024);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
