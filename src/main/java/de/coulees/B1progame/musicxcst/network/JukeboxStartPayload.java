package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record JukeboxStartPayload(BlockPos pos, String musicId, String displayName, String sha256, long sizeBytes, String previewSha256, long previewSizeBytes, long startedAtMillis, int radiusBlocks, boolean positional, boolean looping, int volumePercent) implements CustomPacketPayload {
    public static final Type<JukeboxStartPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "jukebox_start"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JukeboxStartPayload> CODEC = StreamCodec.ofMember(JukeboxStartPayload::write, JukeboxStartPayload::read);

    private static JukeboxStartPayload read(RegistryFriendlyByteBuf buffer) {
        return new JukeboxStartPayload(
                buffer.readBlockPos(),
                buffer.readUtf(128),
                buffer.readUtf(128),
                buffer.readUtf(128),
                buffer.readLong(),
                buffer.readUtf(128),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarInt()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeUtf(musicId, 128);
        buffer.writeUtf(displayName, 128);
        buffer.writeUtf(sha256, 128);
        buffer.writeLong(sizeBytes);
        buffer.writeUtf(previewSha256 == null ? "" : previewSha256, 128);
        buffer.writeLong(previewSizeBytes);
        buffer.writeLong(startedAtMillis);
        buffer.writeVarInt(radiusBlocks);
        buffer.writeBoolean(positional);
        buffer.writeBoolean(looping);
        buffer.writeVarInt(volumePercent);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
