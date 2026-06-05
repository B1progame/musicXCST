package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record UploadedMusicListRequestPayload() implements CustomPacketPayload {
    public static final Type<UploadedMusicListRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "uploaded_music_list_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UploadedMusicListRequestPayload> CODEC = StreamCodec.ofMember(UploadedMusicListRequestPayload::write, UploadedMusicListRequestPayload::read);

    private static UploadedMusicListRequestPayload read(RegistryFriendlyByteBuf buffer) {
        return new UploadedMusicListRequestPayload();
    }

    private void write(RegistryFriendlyByteBuf buffer) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
