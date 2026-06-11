package de.coulees.B1progame.musicxcst.data;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

public final class DiscData {
    public static final int DESIGN_SIZE = 16;
    public static final int DESIGN_PIXELS = DESIGN_SIZE * DESIGN_SIZE;
    public static final int MAX_DESIGN_SIZE = 128;
    public static final int GUI_PREVIEW_RENDER_SIZE = 32;
    public static final int MAX_ITEM_RENDER_SIZE = 128;
    public static final String DESIGN_ID_PREFIX = "MXC1:";
    private static final String BASE64_DESIGN_ID_PREFIX = "MXCST1.";
    private static final String LEGACY_DESIGN_ID_PREFIX = "MXC16.";
    private static final String HIGH_RES_DESIGN_ID_PREFIX = "MXC2.";
    private static final String PALETTE_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_";
    public static final int DESIGN_ID_MAX_LENGTH = 100_000;
    private static final int MAX_NBT_STRING_BYTES = 65_535;
    private static final int DESIGN_PAYLOAD_BYTES = 1 + DESIGN_PIXELS * Integer.BYTES;
    private static final Identifier VALID_MODEL = Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "blueprint_cd");
    private static final Identifier INVALID_MODEL = Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "blueprint_cd_invalid");

    public String musicId;
    public String displayName;
    public String ownerUuid;
    public String ownerName;
    public String hexColor;
    public String designId;
    public int[] designPixels;
    public int designWidth;
    public int designHeight;
    public int designFormatVersion;
    public String designSourceMode;
    public boolean customEditorCreated;
    public boolean importedTexture;
    public String status;
    public int schemaVersion;

    public static DiscData fromEntry(MusicEntry entry) {
        DiscData data = new DiscData();
        data.musicId = entry.musicId;
        data.displayName = entry.displayName;
        data.ownerUuid = entry.ownerUuid;
        data.ownerName = entry.ownerName;
        data.hexColor = entry.hexColor;
        applyDesign(data, decodeDesign(entry.designId).orElseGet(DiscData::defaultDesignData));
        data.status = entry.status;
        data.schemaVersion = entry.schemaVersion;
        return data;
    }

    public static DiscData fromStack(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty()) {
            return null;
        }

        CompoundTag root = customData.copyTag();
        if (!root.contains(Musicxcst.DISC_DATA_KEY)) {
            return null;
        }

        CompoundTag tag = root.getCompoundOrEmpty(Musicxcst.DISC_DATA_KEY);
        DiscData data = new DiscData();
        data.musicId = tag.getStringOr("musicId", "");
        data.displayName = tag.getStringOr("displayName", "");
        data.ownerUuid = tag.getStringOr("ownerUuid", "");
        data.ownerName = tag.getStringOr("ownerName", "");
        data.hexColor = tag.getStringOr("hexColor", "");
        data.status = tag.getStringOr("status", MusicStatus.INVALID);
        data.schemaVersion = tag.getIntOr("schemaVersion", Musicxcst.DISC_SCHEMA_VERSION);

        String storedDesignId = tag.getStringOr("designId", "");
        int storedWidth = tag.getIntOr("designWidth", DESIGN_SIZE);
        int storedHeight = tag.getIntOr("designHeight", DESIGN_SIZE);
        int storedFormatVersion = tag.getIntOr("designFormatVersion", 1);
        String storedSourceMode = tag.getStringOr("designSourceMode", "");
        boolean storedCustomEditor = tag.getBooleanOr("designCustomEditor", false);
        boolean storedImported = tag.getBooleanOr("designImported", false);
        Optional<int[]> storedPixels = tag.getIntArray("designPixels");
        DesignData decoded = null;
        if (storedPixels.isPresent()) {
            decoded = sanitizeDesignData(new DesignData(
                    storedWidth,
                    storedHeight,
                    storedPixels.get(),
                    storedFormatVersion,
                    storedSourceMode,
                    storedCustomEditor,
                    storedImported
            ));
            if (!isSupportedSize(decoded.width(), decoded.height()) || decoded.pixels().length != decoded.width() * decoded.height()) {
                decoded = null;
            }
        }
        if (decoded == null) {
            decoded = decodeDesign(storedDesignId).orElseGet(DiscData::defaultDesignData);
        }
        applyDesign(data, decoded);
        data.designId = encodeDesignId(data.design());
        return data.musicId.isBlank() ? null : data;
    }

    public static void writeToStack(ItemStack stack, DiscData data) {
        CompoundTag tag = new CompoundTag();
        tag.putString("musicId", data.musicId);
        tag.putString("displayName", data.displayName);
        tag.putString("ownerUuid", data.ownerUuid);
        tag.putString("ownerName", data.ownerName);
        boolean invalid = MusicStatus.isInvalidLike(data.status);
        tag.putString("hexColor", invalid ? "" : data.hexColor);

        DesignData sanitizedDesign = invalid ? defaultDesignData() : sanitizeDesignData(data.design());
        Musicxcst.LOGGER.debug("DiscData.writeToStack design {}", designDebugSummary(sanitizedDesign));
        String encodedDesignId = encodeDesignId(sanitizedDesign);
        if (canStoreDesignIdInNbt(encodedDesignId)) {
            tag.putString("designId", encodedDesignId);
        }
        tag.putIntArray("designPixels", sanitizedDesign.pixels());
        tag.putInt("designWidth", sanitizedDesign.width());
        tag.putInt("designHeight", sanitizedDesign.height());
        tag.putInt("designFormatVersion", sanitizedDesign.formatVersion());
        if (!sanitizedDesign.sourceMode().isBlank()) {
            tag.putString("designSourceMode", sanitizedDesign.sourceMode());
        }
        tag.putBoolean("designCustomEditor", sanitizedDesign.customEditorCreated());
        tag.putBoolean("designImported", sanitizedDesign.importedTexture());
        tag.putString("status", data.status);
        tag.putInt("schemaVersion", data.schemaVersion);

        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(Musicxcst.DISC_DATA_KEY, tag));
        stack.set(DataComponents.CUSTOM_NAME, buildHoverName(data));
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(renderColor(data)));
        stack.set(DataComponents.ITEM_MODEL, invalid ? INVALID_MODEL : VALID_MODEL);
    }

    public DesignData design() {
        return sanitizeDesignData(new DesignData(
                designWidth <= 0 ? DESIGN_SIZE : designWidth,
                designHeight <= 0 ? DESIGN_SIZE : designHeight,
                designPixels,
                designFormatVersion <= 0 ? 1 : designFormatVersion,
                designSourceMode == null ? "" : designSourceMode,
                customEditorCreated,
                importedTexture
        ));
    }

    public static Component buildHoverName(DiscData data) {
        if (MusicStatus.isInvalidLike(data.status)) {
            return Component.literal("[INVALID] " + data.displayName).withStyle(ChatFormatting.RED);
        }

        Integer color = parseRgb(data.hexColor);
        if (color != null) {
            return Component.literal(data.displayName).withColor(color);
        }
        return Component.literal(data.displayName).withStyle(ChatFormatting.AQUA);
    }

    public static Integer parseRgb(String hexColor) {
        if (hexColor == null || hexColor.isBlank()) {
            return null;
        }

        String normalized = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;
        if (!normalized.matches("[0-9a-fA-F]{6}")) {
            return null;
        }

        try {
            return Integer.parseInt(normalized, 16);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static int[] defaultDesign() {
        return defaultDesignData().pixels().clone();
    }

    public static DesignData defaultDesignData() {
        int[] pixels = new int[DESIGN_PIXELS];
        String[] rows = {
                "................",
                "................",
                "................",
                ".....AAAAA......",
                "..AAABBBBBAAA...",
                ".ABBBBCCCHBBBA..",
                "ABHHBBCHHCBHBBA.",
                "ABHBBHCCCBBBHBA.",
                "ABBHBBDBBCHHBBA.",
                "AEBBBHCCCBBBBEA.",
                ".FEEEBBBBBEEEF..",
                "..FFFEEEEEFFF...",
                ".....EEEEE......",
                "................",
                "................",
                "................"
        };

        for (int y = 0; y < DESIGN_SIZE; y++) {
            for (int x = 0; x < DESIGN_SIZE; x++) {
                pixels[y * DESIGN_SIZE + x] = switch (rows[y].charAt(x)) {
                    case 'A' -> 0xFF212121;
                    case 'B' -> 0xFF616161;
                    case 'C' -> 0xFF101010;
                    case 'D' -> 0xFF262626;
                    case 'E' -> 0xFF2F2F2F;
                    case 'F' -> 0xFF262626;
                    case 'H' -> 0xFFA6A6A6;
                    default -> 0;
                };
            }
        }
        return new DesignData(DESIGN_SIZE, DESIGN_SIZE, pixels, 1, "pixel", false, false);
    }

    public static int[] themedBlueprintDesign(int accentColor) {
        int[] base = defaultDesign();
        int[] pixels = new int[DESIGN_PIXELS];
        int rgb = accentColor & 0x00FFFFFF;
        for (int index = 0; index < base.length; index++) {
            int pixel = base[index];
            if ((pixel >>> 24) == 0) {
                pixels[index] = 0;
                continue;
            }

            int gray = pixel & 0x00FFFFFF;
            pixels[index] = switch (gray) {
                case 0x101010 -> 0xFF000000 | mix(rgb, 0x000000, 0.84F);
                case 0x212121 -> 0xFF000000 | mix(rgb, 0x000000, 0.72F);
                case 0x262626 -> 0xFF000000 | mix(rgb, 0x000000, 0.62F);
                case 0x2F2F2F -> 0xFF000000 | mix(rgb, 0x000000, 0.54F);
                case 0x616161 -> 0xFF000000 | mix(rgb, 0x000000, 0.22F);
                case 0xA6A6A6 -> 0xFF000000 | mix(rgb, 0xFFFFFF, 0.42F);
                default -> pixel;
            };
        }
        return pixels;
    }

    public static DesignData legacyDesignData(int[] pixels) {
        return sanitizeDesignData(new DesignData(DESIGN_SIZE, DESIGN_SIZE, pixels, 1, "pixel", false, false));
    }

    public static DesignData sanitizeDesignData(DesignData design) {
        if (design == null || !isSupportedSize(design.width(), design.height())) {
            return defaultDesignData();
        }
        if (design.pixels() == null || design.pixels().length != design.width() * design.height()) {
            return defaultDesignData();
        }
        int[] sanitized = new int[design.pixels().length];
        for (int index = 0; index < sanitized.length; index++) {
            sanitized[index] = sanitizeDesignPixel(design.pixels()[index]);
        }
        return new DesignData(
                design.width(),
                design.height(),
                sanitized,
                Math.max(1, design.formatVersion()),
                normalizeSourceMode(design.sourceMode()),
                design.customEditorCreated(),
                design.importedTexture()
        );
    }

    public static int[] sanitizeDesign(int[] pixels) {
        if (pixels == null) {
            return defaultDesign();
        }
        if (pixels.length == DESIGN_PIXELS) {
            int[] sanitized = new int[DESIGN_PIXELS];
            for (int index = 0; index < pixels.length; index++) {
                sanitized[index] = sanitizeDesignPixel(pixels[index]);
            }
            return sanitized;
        }

        int size = squareSizeForLength(pixels.length);
        if (size > 0 && isSupportedSize(size, size)) {
            return downscaleNearest(pixels, size, size, DESIGN_SIZE, DESIGN_SIZE);
        }
        return defaultDesign();
    }

    public static int sanitizeDesignPixel(int pixel) {
        return (pixel >>> 24) == 0 ? 0 : 0xFF000000 | (pixel & 0x00FFFFFF);
    }

    public static String designDebugSummary(int[] pixels) {
        return designDebugSummary(legacyDesignData(pixels));
    }

    public static String designDebugSummary(DesignData design) {
        DesignData sanitized = sanitizeDesignData(design);
        int opaque = 0;
        long checksum = 1125899906842597L;
        for (int pixel : sanitized.pixels()) {
            if ((pixel >>> 24) != 0) {
                opaque++;
            }
            checksum = checksum * 31L + pixel;
        }
        return "size=" + sanitized.width() + "x" + sanitized.height() + ", pixels=" + sanitized.pixels().length + ", opaque=" + opaque + ", checksum=" + Long.toUnsignedString(checksum, 16);
    }

    public static String encodeDesignId(int[] pixels) {
        return encodePaletteDesignId(sanitizeDesign(pixels));
    }

    public static String encodeDesignId(DesignData design) {
        DesignData sanitized = sanitizeDesignData(design);
        if (sanitized.width() == DESIGN_SIZE && sanitized.height() == DESIGN_SIZE && sanitized.formatVersion() <= 1) {
            return encodePaletteDesignId(sanitized.pixels());
        }
        return encodeHighResDesignId(sanitized);
    }

    public static Optional<DesignData> decodeDesign(String designId) {
        if (designId == null) {
            return Optional.empty();
        }

        String trimmed = designId.trim();
        if (trimmed.length() > DESIGN_ID_MAX_LENGTH) {
            return Optional.empty();
        }
        if (trimmed.startsWith(HIGH_RES_DESIGN_ID_PREFIX)) {
            return decodeHighResDesignId(trimmed);
        }
        if (trimmed.startsWith(LEGACY_DESIGN_ID_PREFIX)) {
            return decodeLegacyDesignId(trimmed).map(DiscData::legacyDesignData);
        }
        if (trimmed.startsWith(DESIGN_ID_PREFIX)) {
            return decodePaletteDesignId(trimmed).map(DiscData::legacyDesignData);
        }
        if (!trimmed.startsWith(BASE64_DESIGN_ID_PREFIX)) {
            return Optional.empty();
        }

        String encoded = trimmed.substring(BASE64_DESIGN_ID_PREFIX.length());
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            if (bytes.length != DESIGN_PAYLOAD_BYTES || (bytes[0] & 0xFF) != DESIGN_SIZE) {
                return Optional.empty();
            }

            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            buffer.get();
            int[] pixels = new int[DESIGN_PIXELS];
            for (int index = 0; index < pixels.length; index++) {
                pixels[index] = sanitizeDesignPixel(buffer.getInt());
            }
            return Optional.of(legacyDesignData(pixels));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static Optional<int[]> decodeDesignId(String designId) {
        return decodeDesign(designId).map(DiscData::toLegacyPixels);
    }

    public static int[] toLegacyPixels(DesignData design) {
        DesignData sanitized = sanitizeDesignData(design);
        if (sanitized.width() == DESIGN_SIZE && sanitized.height() == DESIGN_SIZE) {
            return sanitized.pixels().clone();
        }
        return downscaleNearest(sanitized.pixels(), sanitized.width(), sanitized.height(), DESIGN_SIZE, DESIGN_SIZE);
    }

    public static DesignData toItemRenderDesign(DesignData design) {
        return resampleToMaxSize(design, MAX_ITEM_RENDER_SIZE);
    }

    public static DesignData toGuiPreviewDesign(DesignData design) {
        return resampleToMaxSize(design, GUI_PREVIEW_RENDER_SIZE);
    }

    private static DesignData resampleToMaxSize(DesignData design, int maxSize) {
        DesignData sanitized = sanitizeDesignData(design);
        if (sanitized.width() <= maxSize && sanitized.height() <= maxSize) {
            return sanitized;
        }
        return new DesignData(
                maxSize,
                maxSize,
                downscaleNearest(sanitized.pixels(), sanitized.width(), sanitized.height(), maxSize, maxSize),
                sanitized.formatVersion(),
                sanitized.sourceMode(),
                sanitized.customEditorCreated(),
                sanitized.importedTexture()
        );
    }

    public static boolean hasCustomDesign(DiscData data) {
        return hasCustomDesign(data.design());
    }

    public static boolean hasCustomDesign(DesignData design) {
        DesignData sanitized = sanitizeDesignData(design);
        if (sanitized.width() != DESIGN_SIZE || sanitized.height() != DESIGN_SIZE) {
            return true;
        }
        int[] defaults = defaultDesign();
        for (int index = 0; index < DESIGN_PIXELS; index++) {
            if (sanitized.pixels()[index] != defaults[index]) {
                return true;
            }
        }
        return false;
    }

    private static String encodeBase64DesignId(int[] pixels) {
        int[] sanitized = sanitizeDesign(pixels);
        ByteBuffer buffer = ByteBuffer.allocate(DESIGN_PAYLOAD_BYTES);
        buffer.put((byte) DESIGN_SIZE);
        for (int pixel : sanitized) {
            buffer.putInt(pixel);
        }
        return BASE64_DESIGN_ID_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    private static String encodePaletteDesignId(int[] pixels) {
        int[] sanitized = sanitizeDesign(pixels);
        int[] palette = new int[Math.min(DESIGN_PIXELS, PALETTE_CHARS.length())];
        int paletteSize = 0;
        StringBuilder body = new StringBuilder(DESIGN_PIXELS);

        for (int pixel : sanitized) {
            if ((pixel >>> 24) == 0) {
                body.append('.');
                continue;
            }

            int rgb = pixel & 0x00FFFFFF;
            int index = -1;
            for (int paletteIndex = 0; paletteIndex < paletteSize; paletteIndex++) {
                if (palette[paletteIndex] == rgb) {
                    index = paletteIndex;
                    break;
                }
            }

            if (index < 0) {
                if (paletteSize >= PALETTE_CHARS.length()) {
                    return encodeBase64DesignId(sanitized);
                }
                index = paletteSize;
                palette[paletteSize++] = rgb;
            }
            body.append(PALETTE_CHARS.charAt(index));
        }

        StringBuilder header = new StringBuilder();
        for (int index = 0; index < paletteSize; index++) {
            if (index > 0) {
                header.append(',');
            }
            header.append(String.format("%06X", palette[index]));
        }
        return DESIGN_ID_PREFIX + header + ";" + body;
    }

    private static String encodeHighResDesignId(DesignData design) {
        DesignData sanitized = sanitizeDesignData(design);
        byte[] sourceModeBytes = normalizeSourceMode(sanitized.sourceMode()).getBytes(StandardCharsets.UTF_8);
        int metadataBytes = 1 + Short.BYTES + Short.BYTES + 1 + 1 + sourceModeBytes.length;
        ByteBuffer buffer = ByteBuffer.allocate(metadataBytes + sanitized.pixels().length * Integer.BYTES);
        buffer.put((byte) Math.max(2, sanitized.formatVersion()));
        buffer.putShort((short) sanitized.width());
        buffer.putShort((short) sanitized.height());
        int flags = (sanitized.customEditorCreated() ? 1 : 0) | (sanitized.importedTexture() ? 2 : 0);
        buffer.put((byte) flags);
        buffer.put((byte) sourceModeBytes.length);
        buffer.put(sourceModeBytes);
        for (int pixel : sanitized.pixels()) {
            buffer.putInt(pixel);
        }
        return HIGH_RES_DESIGN_ID_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    private static Optional<int[]> decodePaletteDesignId(String designId) {
        int separator = designId.indexOf(';', DESIGN_ID_PREFIX.length());
        if (separator < 0) {
            return Optional.empty();
        }

        String paletteText = designId.substring(DESIGN_ID_PREFIX.length(), separator);
        String body = designId.substring(separator + 1);
        if (body.length() != DESIGN_PIXELS) {
            return Optional.empty();
        }

        String[] colorTexts = paletteText.isBlank() ? new String[0] : paletteText.split(",");
        if (colorTexts.length > PALETTE_CHARS.length()) {
            return Optional.empty();
        }

        int[] palette = new int[colorTexts.length];
        for (int index = 0; index < colorTexts.length; index++) {
            if (!colorTexts[index].matches("[0-9a-fA-F]{6}")) {
                return Optional.empty();
            }
            palette[index] = Integer.parseInt(colorTexts[index], 16);
        }

        int[] pixels = new int[DESIGN_PIXELS];
        for (int index = 0; index < body.length(); index++) {
            char code = body.charAt(index);
            if (code == '.' || code == ' ') {
                pixels[index] = 0;
                continue;
            }
            int paletteIndex = PALETTE_CHARS.indexOf(code);
            if (paletteIndex < 0 || paletteIndex >= palette.length) {
                return Optional.empty();
            }
            pixels[index] = 0xFF000000 | palette[paletteIndex];
        }
        return Optional.of(pixels);
    }

    private static String encodeLegacyDesignId(int[] pixels) {
        ByteBuffer buffer = ByteBuffer.allocate(DESIGN_PIXELS * Integer.BYTES);
        for (int pixel : sanitizeDesign(pixels)) {
            buffer.putInt(pixel);
        }
        return LEGACY_DESIGN_ID_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    private static Optional<int[]> decodeLegacyDesignId(String designId) {
        String encoded = designId.substring(LEGACY_DESIGN_ID_PREFIX.length());
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            if (bytes.length != DESIGN_PIXELS * Integer.BYTES) {
                return Optional.empty();
            }

            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            int[] pixels = new int[DESIGN_PIXELS];
            for (int index = 0; index < pixels.length; index++) {
                pixels[index] = sanitizeDesignPixel(buffer.getInt());
            }
            return Optional.of(pixels);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Optional<DesignData> decodeHighResDesignId(String designId) {
        String encoded = designId.substring(HIGH_RES_DESIGN_ID_PREFIX.length());
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            if (buffer.remaining() < 1 + Short.BYTES + Short.BYTES + 1 + 1) {
                return Optional.empty();
            }
            int formatVersion = buffer.get() & 0xFF;
            int width = buffer.getShort() & 0xFFFF;
            int height = buffer.getShort() & 0xFFFF;
            int flags = buffer.get() & 0xFF;
            int sourceModeLength = buffer.get() & 0xFF;
            if (!isSupportedSize(width, height) || sourceModeLength > buffer.remaining()) {
                return Optional.empty();
            }
            byte[] sourceModeBytes = new byte[sourceModeLength];
            buffer.get(sourceModeBytes);
            int expectedPixels = width * height;
            if (buffer.remaining() != expectedPixels * Integer.BYTES) {
                return Optional.empty();
            }
            int[] pixels = new int[expectedPixels];
            for (int index = 0; index < pixels.length; index++) {
                pixels[index] = sanitizeDesignPixel(buffer.getInt());
            }
            return Optional.of(sanitizeDesignData(new DesignData(
                    width,
                    height,
                    pixels,
                    Math.max(2, formatVersion),
                    new String(sourceModeBytes, StandardCharsets.UTF_8),
                    (flags & 1) != 0,
                    (flags & 2) != 0
            )));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static int mix(int rgb, int other, float otherAmount) {
        float sourceAmount = 1.0F - otherAmount;
        int red = Math.round(((rgb >> 16) & 0xFF) * sourceAmount + ((other >> 16) & 0xFF) * otherAmount);
        int green = Math.round(((rgb >> 8) & 0xFF) * sourceAmount + ((other >> 8) & 0xFF) * otherAmount);
        int blue = Math.round((rgb & 0xFF) * sourceAmount + (other & 0xFF) * otherAmount);
        return (red << 16) | (green << 8) | blue;
    }

    private static int renderColor(DiscData data) {
        if (MusicStatus.isInvalidLike(data.status)) {
            return 0xFFFFFF;
        }

        Integer designColor = averageDesignColor(data.design());
        if (designColor != null) {
            return designColor;
        }

        Integer color = parseRgb(data.hexColor);
        return color != null ? color : 0x2C6DCC;
    }

    private static Integer averageDesignColor(DesignData design) {
        DesignData sanitized = sanitizeDesignData(design);
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        int count = 0;

        for (int pixel : sanitized.pixels()) {
            if ((pixel >>> 24) != 0) {
                red += (pixel >> 16) & 0xFF;
                green += (pixel >> 8) & 0xFF;
                blue += pixel & 0xFF;
                count++;
            }
        }

        if (count == 0) {
            return null;
        }
        return ((int) (red / count) << 16) | ((int) (green / count) << 8) | (int) (blue / count);
    }

    private static int[] downscaleNearest(int[] pixels, int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        int[] out = new int[targetWidth * targetHeight];
        for (int y = 0; y < targetHeight; y++) {
            int sourceY = Math.min(sourceHeight - 1, Math.round(((y + 0.5F) * sourceHeight) / targetHeight - 0.5F));
            for (int x = 0; x < targetWidth; x++) {
                int sourceX = Math.min(sourceWidth - 1, Math.round(((x + 0.5F) * sourceWidth) / targetWidth - 0.5F));
                out[y * targetWidth + x] = sanitizeDesignPixel(pixels[sourceY * sourceWidth + sourceX]);
            }
        }
        return out;
    }

    private static void applyDesign(DiscData data, DesignData design) {
        DesignData sanitized = sanitizeDesignData(design);
        data.designPixels = sanitized.pixels().clone();
        data.designWidth = sanitized.width();
        data.designHeight = sanitized.height();
        data.designFormatVersion = sanitized.formatVersion();
        data.designSourceMode = sanitized.sourceMode();
        data.customEditorCreated = sanitized.customEditorCreated();
        data.importedTexture = sanitized.importedTexture();
        data.designId = encodeDesignId(sanitized);
    }

    private static boolean isSupportedSize(int width, int height) {
        return width == height && (width == 16 || width == 32 || width == 64 || width == 128);
    }

    private static int squareSizeForLength(int length) {
        int root = (int) Math.round(Math.sqrt(length));
        return root * root == length ? root : -1;
    }

    private static String normalizeSourceMode(String sourceMode) {
        if (sourceMode == null || sourceMode.isBlank()) {
            return "";
        }
        String normalized = sourceMode.trim();
        if (normalized.length() > 32) {
            normalized = normalized.substring(0, 32);
        }
        return normalized;
    }

    private static boolean canStoreDesignIdInNbt(String designId) {
        return designId != null && designId.length() <= MAX_NBT_STRING_BYTES;
    }

    public record DesignData(int width, int height, int[] pixels, int formatVersion, String sourceMode,
                             boolean customEditorCreated, boolean importedTexture) {
    }
}
