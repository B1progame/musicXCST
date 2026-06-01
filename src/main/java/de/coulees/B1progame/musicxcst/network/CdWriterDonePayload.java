package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CdWriterDonePayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<CdWriterDonePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "cd_writer_done"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CdWriterDonePayload> CODEC = StreamCodec.ofMember(CdWriterDonePayload::write, CdWriterDonePayload::read);

    private static CdWriterDonePayload read(RegistryFriendlyByteBuf buffer) {
        return new CdWriterDonePayload(buffer.readBlockPos());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
