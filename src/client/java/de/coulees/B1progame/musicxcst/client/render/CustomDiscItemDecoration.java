package de.coulees.B1progame.musicxcst.client.render;

import de.coulees.B1progame.musicxcst.data.DiscData;
import de.coulees.B1progame.musicxcst.init.ModItems;
import net.fabricmc.fabric.api.client.rendering.v1.ExtractItemDecorationsCallback;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

public final class CustomDiscItemDecoration {
    private CustomDiscItemDecoration() {
    }

    public static void register() {
        ExtractItemDecorationsCallback.EVENT.register(CustomDiscItemDecoration::render);
    }

    private static void render(GuiGraphicsExtractor guiGraphics, Font font, ItemStack stack, int x, int y) {
        if (stack.getItem() != ModItems.BLUEPRINT_CD) {
            return;
        }

        DiscData data = DiscData.fromStack(stack);
        if (data == null) {
            return;
        }

        int[] pixels = DiscData.sanitizeDesign(data.designPixels);
        for (int py = 0; py < DiscData.DESIGN_SIZE; py++) {
            for (int px = 0; px < DiscData.DESIGN_SIZE; px++) {
                int color = pixels[py * DiscData.DESIGN_SIZE + px];
                if ((color >>> 24) != 0) {
                    guiGraphics.fill(x + px, y + py, x + px + 1, y + py + 1, color);
                }
            }
        }
    }
}
