package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record JukeboxSettingsOpenPayload(BlockPos pos, boolean looping, int volumePercent) implements CustomPacketPayload {
    public static final Type<JukeboxSettingsOpenPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "jukebox_settings_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JukeboxSettingsOpenPayload> CODEC = StreamCodec.ofMember(JukeboxSettingsOpenPayload::write, JukeboxSettingsOpenPayload::read);

    private static JukeboxSettingsOpenPayload read(RegistryFriendlyByteBuf buffer) {
        return new JukeboxSettingsOpenPayload(buffer.readBlockPos(), buffer.readBoolean(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeBoolean(looping);
        buffer.writeVarInt(volumePercent);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
