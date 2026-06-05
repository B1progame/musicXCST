package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record UploadedMusicListPayload(List<String> fileNames) implements CustomPacketPayload {
    private static final int MAX_FILES = 64;
    private static final int MAX_FILE_NAME_LENGTH = 96;

    public static final Type<UploadedMusicListPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "uploaded_music_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UploadedMusicListPayload> CODEC = StreamCodec.ofMember(UploadedMusicListPayload::write, UploadedMusicListPayload::read);

    public UploadedMusicListPayload {
        fileNames = List.copyOf(fileNames == null ? List.of() : fileNames.stream()
                .filter(fileName -> fileName != null && !fileName.isBlank())
                .limit(MAX_FILES)
                .toList());
    }

    private static UploadedMusicListPayload read(RegistryFriendlyByteBuf buffer) {
        int count = Math.min(MAX_FILES, buffer.readVarInt());
        List<String> fileNames = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            fileNames.add(buffer.readUtf(MAX_FILE_NAME_LENGTH));
        }
        return new UploadedMusicListPayload(fileNames);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        int count = Math.min(MAX_FILES, fileNames.size());
        buffer.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            buffer.writeUtf(fileNames.get(index), MAX_FILE_NAME_LENGTH);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
