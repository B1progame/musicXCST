package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record JukeboxVolumeUpdatePayload(BlockPos pos, int volumePercent) implements CustomPacketPayload {
    public static final Type<JukeboxVolumeUpdatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "jukebox_volume_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JukeboxVolumeUpdatePayload> CODEC = StreamCodec.ofMember(JukeboxVolumeUpdatePayload::write, JukeboxVolumeUpdatePayload::read);

    private static JukeboxVolumeUpdatePayload read(RegistryFriendlyByteBuf buffer) {
        return new JukeboxVolumeUpdatePayload(buffer.readBlockPos(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(volumePercent);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
