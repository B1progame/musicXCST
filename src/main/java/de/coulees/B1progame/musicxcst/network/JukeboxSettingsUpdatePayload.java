package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record JukeboxSettingsUpdatePayload(BlockPos pos, boolean looping) implements CustomPacketPayload {
    public static final Type<JukeboxSettingsUpdatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "jukebox_settings_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JukeboxSettingsUpdatePayload> CODEC = StreamCodec.ofMember(JukeboxSettingsUpdatePayload::write, JukeboxSettingsUpdatePayload::read);

    private static JukeboxSettingsUpdatePayload read(RegistryFriendlyByteBuf buffer) {
        return new JukeboxSettingsUpdatePayload(buffer.readBlockPos(), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeBoolean(looping);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
