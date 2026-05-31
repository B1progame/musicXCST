package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AudioCacheWarmPayload(String musicId, String displayName, String sha256, long sizeBytes) implements CustomPacketPayload {
    public static final Type<AudioCacheWarmPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "audio_cache_warm"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AudioCacheWarmPayload> CODEC = StreamCodec.ofMember(AudioCacheWarmPayload::write, AudioCacheWarmPayload::read);

    private static AudioCacheWarmPayload read(RegistryFriendlyByteBuf buffer) {
        return new AudioCacheWarmPayload(
                buffer.readUtf(128),
                buffer.readUtf(128),
                buffer.readUtf(128),
                buffer.readLong()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(musicId, 128);
        buffer.writeUtf(displayName, 128);
        buffer.writeUtf(sha256, 128);
        buffer.writeLong(sizeBytes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
