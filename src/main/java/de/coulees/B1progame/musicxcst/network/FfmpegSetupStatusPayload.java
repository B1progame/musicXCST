package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FfmpegSetupStatusPayload(String message, boolean done, boolean success) implements CustomPacketPayload {
    public static final Type<FfmpegSetupStatusPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "ffmpeg_setup_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FfmpegSetupStatusPayload> CODEC = StreamCodec.ofMember(FfmpegSetupStatusPayload::write, FfmpegSetupStatusPayload::read);

    private static FfmpegSetupStatusPayload read(RegistryFriendlyByteBuf buffer) {
        return new FfmpegSetupStatusPayload(buffer.readUtf(512), buffer.readBoolean(), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(message, 512);
        buffer.writeBoolean(done);
        buffer.writeBoolean(success);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
