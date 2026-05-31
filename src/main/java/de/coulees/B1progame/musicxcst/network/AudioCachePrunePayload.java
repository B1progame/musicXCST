package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AudioCachePrunePayload(String validCacheKeys) implements CustomPacketPayload {
    public static final Type<AudioCachePrunePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "audio_cache_prune"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AudioCachePrunePayload> CODEC = StreamCodec.ofMember(AudioCachePrunePayload::write, AudioCachePrunePayload::read);

    private static AudioCachePrunePayload read(RegistryFriendlyByteBuf buffer) {
        return new AudioCachePrunePayload(buffer.readUtf(32767));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(validCacheKeys, 32767);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
