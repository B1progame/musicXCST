package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FfmpegSetupRequestPayload() implements CustomPacketPayload {
    public static final Type<FfmpegSetupRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "ffmpeg_setup_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FfmpegSetupRequestPayload> CODEC = StreamCodec.ofMember(FfmpegSetupRequestPayload::write, FfmpegSetupRequestPayload::read);

    private static FfmpegSetupRequestPayload read(RegistryFriendlyByteBuf buffer) {
        return new FfmpegSetupRequestPayload();
    }

    private void write(RegistryFriendlyByteBuf buffer) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
