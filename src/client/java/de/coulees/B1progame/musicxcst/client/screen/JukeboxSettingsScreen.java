package de.coulees.B1progame.musicxcst.client.screen;

import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.network.JukeboxSettingsUpdatePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class JukeboxSettingsScreen extends Screen {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "textures/gui/jukebox_gui.png");
    private static final int TEXTURE_SIZE = 256;
    private static final int GUI_U = 0;
    private static final int GUI_V = 127;
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 95;
    private static final int BUTTON_Y = 67;
    private static final int BUTTON_WIDTH = 40;
    private static final int BUTTON_HEIGHT = 12;

    private final BlockPos pos;
    private boolean looping;
    private Button loopButton;

    public JukeboxSettingsScreen(BlockPos pos, boolean looping) {
        super(Component.literal("Jukebox Settings"));
        this.pos = pos;
        this.looping = looping;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - GUI_WIDTH / 2;
        int top = this.height / 2 - GUI_HEIGHT / 2;
        this.loopButton = addRenderableWidget(Button.builder(loopLabel(), button -> toggleLooping())
                .bounds(left + 7, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        Button planned = addRenderableWidget(Button.builder(Component.literal("Planned"), button -> {
        }).bounds(left + 129, top + BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        planned.active = false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(guiGraphics);
        int left = this.width / 2 - GUI_WIDTH / 2;
        int top = this.height / 2 - GUI_HEIGHT / 2;
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, left, top, GUI_U, GUI_V, GUI_WIDTH, GUI_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private void toggleLooping() {
        looping = !looping;
        loopButton.setMessage(loopLabel());
        ClientPlayNetworking.send(new JukeboxSettingsUpdatePayload(pos, looping));
    }

    private Component loopLabel() {
        return Component.literal(looping ? "Loop On" : "Loop Off");
    }
}
