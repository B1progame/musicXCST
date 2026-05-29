package de.coulees.B1progame.musicxcst.data;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;

public final class DiscData {
    public String musicId;
    public String displayName;
    public String ownerUuid;
    public String ownerName;
    public String hexColor;
    public String designId;
    public String status;
    public int schemaVersion;

    public static DiscData fromEntry(MusicEntry entry) {
        DiscData data = new DiscData();
        data.musicId = entry.musicId;
        data.displayName = entry.displayName;
        data.ownerUuid = entry.ownerUuid;
        data.ownerName = entry.ownerName;
        data.hexColor = entry.hexColor;
        data.designId = "default";
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
        data.designId = tag.getStringOr("designId", "");
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
        tag.putString("designId", data.designId);
        tag.putString("status", data.status);
        tag.putInt("schemaVersion", data.schemaVersion);

        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(Musicxcst.DISC_DATA_KEY, tag));
        stack.set(DataComponents.CUSTOM_NAME, buildHoverName(data));
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(renderColor(data)));
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

    private static int renderColor(DiscData data) {
        if (MusicStatus.isInvalidLike(data.status)) {
            return 0xC93A3A;
        }

        Integer color = parseRgb(data.hexColor);
        return color != null ? color : 0x2C6DCC;
    }
}
