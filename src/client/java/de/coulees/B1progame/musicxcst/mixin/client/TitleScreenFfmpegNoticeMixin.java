package de.coulees.B1progame.musicxcst.mixin.client;

import de.coulees.B1progame.musicxcst.client.ClientFfmpegStatus;
import de.coulees.B1progame.musicxcst.client.screen.FfmpegSetupScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
public abstract class TitleScreenFfmpegNoticeMixin {
    private static final int NOTICE_WIDTH = 226;
    private static final int NOTICE_HEIGHT = 58;
    private static final int NOTICE_MARGIN = 12;

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void musicxcst$drawMissingFfmpegNotice(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!ClientFfmpegStatus.isMissing()) {
            return;
        }
        Screen screen = (Screen) (Object) this;
        Font font = Minecraft.getInstance().font;
        int x = screen.width - NOTICE_WIDTH - NOTICE_MARGIN;
        int y = NOTICE_MARGIN;
        boolean hovered = mouseX >= x && mouseX <= x + NOTICE_WIDTH && mouseY >= y && mouseY <= y + NOTICE_HEIGHT;
        guiGraphics.fill(x + 2, y + 2, x + NOTICE_WIDTH + 2, y + NOTICE_HEIGHT + 2, 0x88000000);
        guiGraphics.fill(x, y, x + NOTICE_WIDTH, y + NOTICE_HEIGHT, hovered ? 0xF0222630 : 0xEE151922);
        guiGraphics.fill(x, y, x + 4, y + NOTICE_HEIGHT, 0xFF5EA1FF);
        guiGraphics.outline(x, y, NOTICE_WIDTH, NOTICE_HEIGHT, hovered ? 0xFFFFD166 : 0xFF385F8A);
        guiGraphics.text(font, "MusicXCST setup", x + 12, y + 8, 0xFFFFFFFF, false);
        guiGraphics.text(font, "Managed FFmpeg is not installed", x + 12, y + 23, 0xFFC8D6E6, false);
        guiGraphics.fill(x + 12, y + 39, x + NOTICE_WIDTH - 12, y + 51, hovered ? 0xFF3A5F8B : 0xFF273849);
        guiGraphics.text(font, "Click to configure", x + 18, y + 41, 0xFFFFE08A, false);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void musicxcst$openFfmpegSetupFromNotice(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!ClientFfmpegStatus.isMissing()) {
            return;
        }
        Screen screen = (Screen) (Object) this;
        int x = screen.width - NOTICE_WIDTH - NOTICE_MARGIN;
        int y = NOTICE_MARGIN;
        if (event.x() >= x && event.x() <= x + NOTICE_WIDTH && event.y() >= y && event.y() <= y + NOTICE_HEIGHT) {
            Minecraft.getInstance().setScreen(new FfmpegSetupScreen(screen));
            cir.setReturnValue(true);
        }
    }
}
