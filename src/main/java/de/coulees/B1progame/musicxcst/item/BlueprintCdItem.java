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
        return false;
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
            tooltip.accept(Component.translatable("tooltip.musicxcst.blueprint_cd.blank").withStyle(ChatFormatting.AQUA));
            tooltip.accept(Component.translatable("tooltip.musicxcst.blueprint_cd.blank_hint").withStyle(ChatFormatting.GRAY));
            return;
        }

        tooltip.accept(Component.translatable("tooltip.musicxcst.blueprint_cd.music_id", data.musicId).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.musicxcst.blueprint_cd.owner", data.ownerName).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.musicxcst.blueprint_cd.color", data.hexColor).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.musicxcst.blueprint_cd.status", data.status).withStyle(MusicStatus.isInvalidLike(data.status) ? ChatFormatting.RED : ChatFormatting.GREEN));

        if (MusicStatus.isInvalidLike(data.status)) {
            tooltip.accept(Component.translatable("tooltip.musicxcst.blueprint_cd.invalid_warning").withStyle(ChatFormatting.RED));
        }
    }
}
