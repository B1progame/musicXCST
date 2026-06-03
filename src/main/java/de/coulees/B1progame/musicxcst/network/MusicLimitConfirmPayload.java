package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MusicLimitConfirmPayload(BlockPos pos, int limit) implements CustomPacketPayload {
    public static final Type<MusicLimitConfirmPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "music_limit_confirm"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MusicLimitConfirmPayload> CODEC = StreamCodec.ofMember(MusicLimitConfirmPayload::write, MusicLimitConfirmPayload::read);

    private static MusicLimitConfirmPayload read(RegistryFriendlyByteBuf buffer) {
        return new MusicLimitConfirmPayload(buffer.readBlockPos(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(limit);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
