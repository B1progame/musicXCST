package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CdWriterWritePayload(BlockPos pos, String discName, String hexColor, String uploadedFileName) implements CustomPacketPayload {
    public static final Type<CdWriterWritePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "cd_writer_write"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CdWriterWritePayload> CODEC = StreamCodec.ofMember(CdWriterWritePayload::write, CdWriterWritePayload::read);

    private static CdWriterWritePayload read(RegistryFriendlyByteBuf buffer) {
        return new CdWriterWritePayload(
                buffer.readBlockPos(),
                buffer.readUtf(96),
                buffer.readUtf(16),
                buffer.readUtf(128)
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeUtf(discName, 96);
        buffer.writeUtf(hexColor, 16);
        buffer.writeUtf(uploadedFileName, 128);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
