package de.coulees.B1progame.musicxcst.network;

import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.data.DiscData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CdWriterWritePayload(BlockPos pos, String discName, String hexColor, String uploadedFileName, int[] designPixels) implements CustomPacketPayload {
    public static final Type<CdWriterWritePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "cd_writer_write"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CdWriterWritePayload> CODEC = StreamCodec.ofMember(CdWriterWritePayload::write, CdWriterWritePayload::read);
    private static final int MAX_DESIGN_INTS = 1024;

    public CdWriterWritePayload {
        designPixels = DiscData.sanitizeDesign(designPixels);
    }

    private static CdWriterWritePayload read(RegistryFriendlyByteBuf buffer) {
        return new CdWriterWritePayload(
                buffer.readBlockPos(),
                buffer.readUtf(96),
                buffer.readUtf(16),
                buffer.readUtf(128),
                readDesignPixels(buffer)
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeUtf(discName, 96);
        buffer.writeUtf(hexColor, 16);
        buffer.writeUtf(uploadedFileName, 128);
        writeDesignPixels(buffer, designPixels);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static int[] readDesignPixels(RegistryFriendlyByteBuf buffer) {
        int length = buffer.readVarInt();
        if (length < 0 || length > MAX_DESIGN_INTS) {
            throw new IllegalArgumentException("Invalid CD Writer design length: " + length);
        }

        int[] pixels = new int[DiscData.DESIGN_PIXELS];
        int readable = Math.min(length, DiscData.DESIGN_PIXELS);
        for (int index = 0; index < readable; index++) {
            pixels[index] = DiscData.sanitizeDesignPixel(buffer.readInt());
        }
        for (int index = readable; index < length; index++) {
            buffer.readInt();
        }
        return length == DiscData.DESIGN_PIXELS ? pixels : DiscData.defaultDesign();
    }

    private static void writeDesignPixels(RegistryFriendlyByteBuf buffer, int[] pixels) {
        int[] sanitized = DiscData.sanitizeDesign(pixels);
        buffer.writeVarInt(sanitized.length);
        for (int pixel : sanitized) {
            buffer.writeInt(pixel);
        }
    }
}
