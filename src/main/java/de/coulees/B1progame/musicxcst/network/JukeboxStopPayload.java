package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record JukeboxStopPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<JukeboxStopPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "jukebox_stop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JukeboxStopPayload> CODEC = StreamCodec.ofMember(JukeboxStopPayload::write, JukeboxStopPayload::read);

    private static JukeboxStopPayload read(RegistryFriendlyByteBuf buffer) {
        return new JukeboxStopPayload(buffer.readBlockPos());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
