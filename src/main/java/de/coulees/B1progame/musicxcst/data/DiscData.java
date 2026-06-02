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
import java.util.Base64;
import java.util.Optional;

public final class DiscData {
    public static final int DESIGN_SIZE = 16;
    public static final int DESIGN_PIXELS = DESIGN_SIZE * DESIGN_SIZE;
    public static final String DESIGN_ID_PREFIX = "MXC1:";
    private static final String LEGACY_DESIGN_ID_PREFIX = "MXC16.";
    private static final String PALETTE_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_";
    public static final int DESIGN_ID_MAX_LENGTH = 1400;
    private static final Identifier VALID_MODEL = Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "blueprint_cd");
    private static final Identifier INVALID_MODEL = Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "blueprint_cd_invalid");

    public String musicId;
    public String displayName;
    public String ownerUuid;
    public String ownerName;
    public String hexColor;
    public String designId;
    public int[] designPixels;
    public String status;
    public int schemaVersion;

    public static DiscData fromEntry(MusicEntry entry) {
        DiscData data = new DiscData();
        data.musicId = entry.musicId;
        data.displayName = entry.displayName;
        data.ownerUuid = entry.ownerUuid;
        data.ownerName = entry.ownerName;
        data.hexColor = entry.hexColor;
        data.designPixels = defaultDesign();
        data.designId = encodeDesignId(data.designPixels);
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
        String storedDesignId = tag.getStringOr("designId", "");
        Optional<int[]> storedPixels = tag.getIntArray("designPixels");
        if (storedPixels.isPresent() && storedPixels.get().length == DESIGN_PIXELS) {
            data.designPixels = sanitizeDesign(storedPixels.get());
        } else {
            data.designPixels = decodeDesignId(storedDesignId).orElseGet(DiscData::defaultDesign);
        }
        data.designId = encodeDesignId(data.designPixels);
        data.status = tag.getStringOr("status", MusicStatus.INVALID);
        data.schemaVersion = tag.getIntOr("schemaVersion", Musicxcst.DISC_SCHEMA_VERSION);
        return data.musicId.isBlank() ? null : data;
    }

    public static void writeToStack(ItemStack stack, DiscData data) {
        CompoundTag tag = new CompoundTag();
        tag.putString("musicId", data.musicId);
        tag.putString("displayName", data.displayName);
        tag.putString("ownerUuid", data.ownerUuid);
        tag.putString("ownerName", data.ownerName);
        tag.putString("hexColor", data.hexColor);
        int[] sanitizedDesign = sanitizeDesign(data.designPixels);
        tag.putString("designId", encodeDesignId(sanitizedDesign));
        tag.putIntArray("designPixels", sanitizedDesign);
        tag.putString("status", data.status);
        tag.putInt("schemaVersion", data.schemaVersion);

        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(Musicxcst.DISC_DATA_KEY, tag));
        stack.set(DataComponents.CUSTOM_NAME, buildHoverName(data));
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(renderColor(data)));
        stack.set(DataComponents.ITEM_MODEL, MusicStatus.isInvalidLike(data.status) ? INVALID_MODEL : VALID_MODEL);
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
        return pixels;
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

    public static int[] sanitizeDesign(int[] pixels) {
        int[] sanitized = defaultDesign();
        if (pixels == null || pixels.length != DESIGN_PIXELS) {
            return sanitized;
        }

        for (int index = 0; index < pixels.length; index++) {
            sanitized[index] = sanitizeDesignPixel(pixels[index]);
        }
        return sanitized;
    }

    public static int sanitizeDesignPixel(int pixel) {
        return (pixel >>> 24) == 0 ? 0 : 0xFF000000 | (pixel & 0x00FFFFFF);
    }

    public static String encodeDesignId(int[] pixels) {
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
                    return encodeLegacyDesignId(sanitized);
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

    public static Optional<int[]> decodeDesignId(String designId) {
        if (designId == null) {
            return Optional.empty();
        }

        String trimmed = designId.trim();
        if (trimmed.length() > DESIGN_ID_MAX_LENGTH) {
            return Optional.empty();
        }
        if (trimmed.startsWith(LEGACY_DESIGN_ID_PREFIX)) {
            return decodeLegacyDesignId(trimmed);
        }
        if (!trimmed.startsWith(DESIGN_ID_PREFIX)) {
            return Optional.empty();
        }

        int separator = trimmed.indexOf(';', DESIGN_ID_PREFIX.length());
        if (separator < 0) {
            return Optional.empty();
        }

        String paletteText = trimmed.substring(DESIGN_ID_PREFIX.length(), separator);
        String body = trimmed.substring(separator + 1);
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

    private static int mix(int rgb, int other, float otherAmount) {
        float sourceAmount = 1.0F - otherAmount;
        int red = Math.round(((rgb >> 16) & 0xFF) * sourceAmount + ((other >> 16) & 0xFF) * otherAmount);
        int green = Math.round(((rgb >> 8) & 0xFF) * sourceAmount + ((other >> 8) & 0xFF) * otherAmount);
        int blue = Math.round((rgb & 0xFF) * sourceAmount + (other & 0xFF) * otherAmount);
        return (red << 16) | (green << 8) | blue;
    }

    private static int renderColor(DiscData data) {
        if (MusicStatus.isInvalidLike(data.status)) {
            return 0xC93A3A;
        }

        Integer designColor = averageDesignColor(data.designPixels);
        if (designColor != null) {
            return designColor;
        }

        Integer color = parseRgb(data.hexColor);
        return color != null ? color : 0x2C6DCC;
    }

    private static Integer averageDesignColor(int[] pixels) {
        int[] sanitized = sanitizeDesign(pixels);
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        int count = 0;

        for (int pixel : sanitized) {
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
}
