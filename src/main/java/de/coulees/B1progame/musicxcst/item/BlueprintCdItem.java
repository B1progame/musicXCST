package de.coulees.B1progame.musicxcst.item;

import de.coulees.B1progame.musicxcst.data.DiscData;
import de.coulees.B1progame.musicxcst.data.MusicStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public final class BlueprintCdItem extends Item {
    public BlueprintCdItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return DiscData.fromStack(stack) != null;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        DiscData data = DiscData.fromStack(stack);
        if (data == null) {
            return 0x2C6DCC;
        }

        if (MusicStatus.isInvalidLike(data.status)) {
            return 0xC93A3A;
        }

        Integer color = DiscData.parseRgb(data.hexColor);
        return color != null ? color : 0x2C6DCC;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        DiscData data = DiscData.fromStack(stack);
        if (data == null) {
            tooltip.accept(Component.literal("Blank writable Blueprint CD").withStyle(ChatFormatting.AQUA));
            tooltip.accept(Component.literal("Use /cstmusic create while holding this disc.").withStyle(ChatFormatting.GRAY));
            return;
        }

        tooltip.accept(Component.literal("Music ID: " + data.musicId).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal("Owner: " + data.ownerName).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal("Color: " + data.hexColor).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal("Status: " + data.status).withStyle(MusicStatus.isInvalidLike(data.status) ? ChatFormatting.RED : ChatFormatting.GREEN));

        if (MusicStatus.isInvalidLike(data.status)) {
            tooltip.accept(Component.literal("Warning: audio is missing, deleted, or invalid.").withStyle(ChatFormatting.RED));
        }
    }
}
