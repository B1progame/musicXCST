package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MusicLimitConfirmResponsePayload(BlockPos pos, boolean confirmed) implements CustomPacketPayload {
    public static final Type<MusicLimitConfirmResponsePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "music_limit_confirm_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicLimitConfirmResponsePayload> CODEC = StreamCodec.ofMember(MusicLimitConfirmResponsePayload::write, MusicLimitConfirmResponsePayload::read);

    private static MusicLimitConfirmResponsePayload read(RegistryFriendlyByteBuf buffer) {
        return new MusicLimitConfirmResponsePayload(buffer.readBlockPos(), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeBoolean(confirmed);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
