package de.coulees.B1progame.musicxcst.client.screen;

import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.client.ClientMusicUploader;
import de.coulees.B1progame.musicxcst.menu.CdWriterMenu;
import de.coulees.B1progame.musicxcst.network.CdWriterWritePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class CdWriterScreen extends AbstractContainerScreen<CdWriterMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "textures/gui/cd_writer.png");
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 256;
    private static final int GUI_WIDTH = 276;
    private static final int GUI_HEIGHT = 166;
    private static final int PRINT_BUTTON_X = 237;
    private static final int PRINT_BUTTON_Y = 41;
    private static final int PRINT_BUTTON_WIDTH = 29;
    private static final int PRINT_BUTTON_HEIGHT = 8;
    private static final int[] COLORS = {
            0xF14A54, 0xFFB536, 0xF8E85D, 0x5ED66E, 0x42C6D5, 0x3F7BFF, 0x8F62FF, 0xF1F1F1
    };

    private EditBox nameBox;
    private EditBox pathBox;
    private int color = 0x00AAFF;
    private boolean uploading;
    private int ticks;
    private String progressText = "";

    public CdWriterScreen(CdWriterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, GUI_WIDTH, GUI_HEIGHT);
        this.titleLabelX = -1000;
        this.inventoryLabelX = -1000;
    }

    @Override
    protected void init() {
        super.init();
        this.nameBox = addRenderableWidget(new ScaledEditBox(this.font, leftPos + 12, topPos + 13, 174, 18, Component.literal("Song name")));
        this.nameBox.setBordered(false);
        this.nameBox.setMaxLength(64);
        this.nameBox.setSuggestion("Song name");

        this.pathBox = addRenderableWidget(new ScaledEditBox(this.font, leftPos + 12, topPos + 41, 190, 18, Component.literal("File path")));
        this.pathBox.setBordered(false);
        this.pathBox.setMaxLength(260);
        this.pathBox.setSuggestion("File path");

        setInitialFocus(this.nameBox);
        this.nameBox.setFocused(true);
    }

    @Override
    protected void containerTick() {
        ticks++;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(guiGraphics);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, GUI_WIDTH, GUI_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        guiGraphics.fill(leftPos + 24, topPos + 88, leftPos + 96, topPos + 135, 0xFF000000 | color);
        guiGraphics.text(this.font, hexColor(), leftPos + 14, topPos + 145, 0xFFFFFFFF, false);
        if (uploading) {
            guiGraphics.centeredText(this.font, progressText.isBlank() ? loadingText() : progressText, leftPos + GUI_WIDTH / 2, topPos - 13, 0xFFFFE680);
        }
        guiGraphics.fill(leftPos + PRINT_BUTTON_X, topPos + PRINT_BUTTON_Y, leftPos + PRINT_BUTTON_X + PRINT_BUTTON_WIDTH, topPos + PRINT_BUTTON_Y + PRINT_BUTTON_HEIGHT, 0xFF00FEFF);
        drawScaledCentered(guiGraphics, "Print", leftPos + PRINT_BUTTON_X + PRINT_BUTTON_WIDTH / 2, topPos + PRINT_BUTTON_Y + 1, 0.6F, canWrite() ? 0xFF102222 : 0xFF406060);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        int x = (int) event.x() - leftPos;
        int y = (int) event.y() - topPos;
        if (x >= PRINT_BUTTON_X && x <= PRINT_BUTTON_X + PRINT_BUTTON_WIDTH && y >= PRINT_BUTTON_Y && y <= PRINT_BUTTON_Y + PRINT_BUTTON_HEIGHT) {
            write();
            return true;
        }
        if (x >= 8 && x <= 101 && y >= 72 && y <= 162) {
            pickColorFromPanel(x, y);
            return true;
        }
        for (int index = 0; index < COLORS.length; index++) {
            int swatchX = 12 + index * 11;
            if (x >= swatchX && x <= swatchX + 8 && y >= 134 && y <= 142) {
                color = COLORS[index];
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        int x = (int) event.x() - leftPos;
        int y = (int) event.y() - topPos;
        if (x >= 8 && x <= 101 && y >= 72 && y <= 162) {
            pickColorFromPanel(x, y);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        EditBox focusedBox = focusedBox();
        if (focusedBox != null) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                return super.keyPressed(event);
            }
            if (event.key() == GLFW.GLFW_KEY_TAB) {
                EditBox next = focusedBox == nameBox ? pathBox : nameBox;
                focusedBox.setFocused(false);
                next.setFocused(true);
                setFocused(next);
                return true;
            }
            focusedBox.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        EditBox focusedBox = focusedBox();
        if (focusedBox != null) {
            return focusedBox.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ContainerInput input) {
        if (uploading && (slot == menu.getSlot(CdWriterMenu.INPUT_SLOT) || slot == menu.getSlot(CdWriterMenu.OUTPUT_SLOT))) {
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, input);
    }

    @Override
    protected void extractSlot(GuiGraphicsExtractor guiGraphics, Slot slot, int mouseX, int mouseY) {
        if (slot == menu.getSlot(CdWriterMenu.OUTPUT_SLOT)) {
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate(0.0F, -0.5F);
            super.extractSlot(guiGraphics, slot, mouseX, mouseY);
            guiGraphics.pose().popMatrix();
            return;
        }
        super.extractSlot(guiGraphics, slot, mouseX, mouseY);
    }

    public void finishConverting(BlockPos pos) {
        if (menu.pos().equals(pos)) {
            uploading = false;
            progressText = "";
        }
    }

    private void write() {
        if (uploading) {
            return;
        }
        String name = nameBox.getValue().trim();
        String path = pathBox.getValue().trim();
        if (name.isBlank() || path.isBlank()) {
            message("Enter a song name and file path first.");
            return;
        }
        if (!hasWritableDisc()) {
            message("Place a Blueprint CD in the CD Writer slot.");
            return;
        }
        if (menu.hasOutput()) {
            message("Take the finished CD out first.");
            return;
        }

        uploading = true;
        progressText = "Starting upload...";
        Minecraft client = Minecraft.getInstance();
        int started = ClientMusicUploader.startUpload(client, name, path, uploadedFileName ->
                ClientPlayNetworking.send(new CdWriterWritePayload(menu.pos(), name, hexColor(), uploadedFileName)), progress -> this.progressText = progress);
        if (started == 0) {
            uploading = false;
            progressText = "";
        }
    }

    private boolean hasWritableDisc() {
        ItemStack stack = menu.inputStack();
        return !stack.isEmpty() && !menu.hasOutput();
    }

    private EditBox focusedBox() {
        if (nameBox != null && nameBox.canConsumeInput()) {
            return nameBox;
        }
        if (pathBox != null && pathBox.canConsumeInput()) {
            return pathBox;
        }
        return null;
    }

    private void pickColorFromPanel(int x, int y) {
        float hue = Math.max(0.0F, Math.min(1.0F, (x - 8) / 93.0F));
        float brightness = 1.0F - Math.max(0.0F, Math.min(1.0F, (y - 72) / 90.0F)) * 0.55F;
        color = java.awt.Color.HSBtoRGB(hue, 0.8F, brightness) & 0xFFFFFF;
    }

    private boolean canWrite() {
        return !uploading && hasWritableDisc() && !nameBox.getValue().trim().isBlank() && !pathBox.getValue().trim().isBlank();
    }

    private String loadingText() {
        return switch ((ticks / 8) % 4) {
            case 0 -> "Converting";
            case 1 -> "Converting.";
            case 2 -> "Converting..";
            default -> "Converting...";
        };
    }

    private String hexColor() {
        return String.format(Locale.ROOT, "#%06X", color);
    }

    private void drawScaledCentered(GuiGraphicsExtractor guiGraphics, String text, int centerX, int y, float scale, int color) {
        int width = this.font.width(text);
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(centerX, y);
        guiGraphics.pose().scale(scale, scale);
        guiGraphics.text(this.font, text, Math.round(-width / 2.0F), 0, color, false);
        guiGraphics.pose().popMatrix();
    }

    private void message(String text) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(text));
        }
    }

    private static final class ScaledEditBox extends EditBox {
        private static final float TEXT_SCALE = 0.8F;

        private ScaledEditBox(net.minecraft.client.gui.Font font, int x, int y, int width, int height, Component message) {
            super(font, x, y, width, height, message);
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().scaleAround(TEXT_SCALE, getX(), getY());
            super.extractWidgetRenderState(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.pose().popMatrix();
        }
    }
}
