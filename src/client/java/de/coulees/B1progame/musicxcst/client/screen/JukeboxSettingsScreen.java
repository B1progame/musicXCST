package de.coulees.B1progame.musicxcst.client.screen;

import de.coulees.B1progame.musicxcst.network.JukeboxSettingsUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class JukeboxSettingsScreen extends Screen {
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
        int left = this.width / 2 - 90;
        int top = this.height / 2 - 35;
        this.loopButton = addRenderableWidget(Button.builder(loopLabel(), button -> toggleLooping())
                .bounds(left, top, 180, 20)
                .build());
        Button planned = addRenderableWidget(Button.builder(Component.literal("Planned"), button -> {
        }).bounds(left, top + 28, 180, 20).build());
        planned.active = false;
    }

    private void toggleLooping() {
        looping = !looping;
        loopButton.setMessage(loopLabel());
        ClientPlayNetworking.send(new JukeboxSettingsUpdatePayload(pos, looping));
    }

    private Component loopLabel() {
        return Component.literal("Loop song: " + (looping ? "On" : "Off"));
    }
}
