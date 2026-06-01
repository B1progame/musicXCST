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
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
    private static final int FILE_BUTTON_X = 180;
    private static final int FILE_BUTTON_Y = 35;
    private static final int FILE_BUTTON_WIDTH = 19;
    private static final int FILE_BUTTON_HEIGHT = 20;
    private static final int EDITOR_X = 12;
    private static final int EDITOR_Y = 76;
    private static final int EDITOR_CELL = 4;
    private static final int PALETTE_X = 82;
    private static final int PALETTE_Y = 76;
    private static final int TOOL_Y = 144;
    private static final String[] SUPPORTED_FILE_PATTERNS = {
            "*.mp3", "*.mp4", "*.wav", "*.ogg", "*.flac", "*.m4a", "*.aac", "*.webm", "*.avi"
    };
    private static final Set<String> SUPPORTED_FILE_EXTENSIONS = Set.of("mp3", "mp4", "wav", "ogg", "flac", "m4a", "aac", "webm", "avi");
    private static final Map<BlockPos, ScreenState> SAVED_STATES = new HashMap<>();
    private static final int[] COLORS = {
            0xF14A54, 0xFFB536, 0xF8E85D, 0x5ED66E, 0x42C6D5, 0x3F7BFF, 0x8F62FF, 0xF1F1F1
    };

    private EditBox nameBox;
    private EditBox pathBox;
    private final int[] discPixels = new int[16 * 16];
    private final int[] savedDiscPixels = new int[16 * 16];
    private int selectedColor = 0x00AAFF;
    private int savedColor = selectedColor;
    private boolean textureSaved;
    private boolean uploading;
    private int ticks;
    private String progressText = "";
    private Path selectedFile;
    private boolean updatingPathBox;

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

        this.pathBox = addRenderableWidget(new ScaledEditBox(this.font, leftPos + 12, topPos + 41, 164, 18, Component.literal("File path")));
        this.pathBox.setBordered(false);
        this.pathBox.setMaxLength(260);
        this.pathBox.setSuggestion("File path");
        this.pathBox.setResponder(value -> {
            if (!updatingPathBox) {
                selectedFile = null;
            }
        });
        restoreState();

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
        renderFileButtonIcon(guiGraphics);
        renderTextureEditor(guiGraphics);
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
        if (inside(x, y, FILE_BUTTON_X, FILE_BUTTON_Y, FILE_BUTTON_WIDTH, FILE_BUTTON_HEIGHT)) {
            openFilePicker();
            return true;
        }
        if (x >= PRINT_BUTTON_X && x <= PRINT_BUTTON_X + PRINT_BUTTON_WIDTH && y >= PRINT_BUTTON_Y && y <= PRINT_BUTTON_Y + PRINT_BUTTON_HEIGHT) {
            write();
            return true;
        }
        if (handleTextureEditorClick(x, y, event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        int x = (int) event.x() - leftPos;
        int y = (int) event.y() - topPos;
        if (handleTextureEditorClick(x, y, event.button())) {
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
            SAVED_STATES.remove(pos);
        }
    }

    @Override
    public void removed() {
        saveState();
        super.removed();
    }

    private void write() {
        if (uploading) {
            return;
        }
        String name = nameBox.getValue().trim();
        String path = selectedFile != null ? selectedFile.toString() : pathBox.getValue().trim();
        if (name.isBlank() || path.isBlank()) {
            message("Enter a song name and file path first.");
            return;
        }
        if (!isSupportedFile(path)) {
            message("Unsupported file type. Use mp3, mp4, wav, ogg, flac, m4a, aac, webm, or avi.");
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

    private boolean canWrite() {
        return !uploading && hasWritableDisc() && !nameBox.getValue().trim().isBlank() && !(selectedFile == null && pathBox.getValue().trim().isBlank());
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
        return String.format(Locale.ROOT, "#%06X", textureColor());
    }

    private void renderTextureEditor(GuiGraphicsExtractor guiGraphics) {
        for (int py = 0; py < 16; py++) {
            for (int px = 0; px < 16; px++) {
                int value = discPixels[py * 16 + px];
                int color = value == 0 ? (((px + py) & 1) == 0 ? 0x44FFFFFF : 0x22000000) : 0xFF000000 | value;
                int x = leftPos + EDITOR_X + px * EDITOR_CELL;
                int y = topPos + EDITOR_Y + py * EDITOR_CELL;
                guiGraphics.fill(x, y, x + EDITOR_CELL, y + EDITOR_CELL, color);
            }
        }
        guiGraphics.outline(leftPos + EDITOR_X - 1, topPos + EDITOR_Y - 1, 16 * EDITOR_CELL + 2, 16 * EDITOR_CELL + 2, 0xFF2B0F24);

        for (int index = 0; index < COLORS.length; index++) {
            int x = PALETTE_X + (index % 2) * 8;
            int y = PALETTE_Y + (index / 2) * 8;
            guiGraphics.fill(leftPos + x, topPos + y, leftPos + x + 7, topPos + y + 7, 0xFF000000 | COLORS[index]);
            if (selectedColor == COLORS[index]) {
                guiGraphics.outline(leftPos + x - 1, topPos + y - 1, 9, 9, 0xFFFFFFFF);
            }
        }
        guiGraphics.fill(leftPos + PALETTE_X, topPos + PALETTE_Y + 38, leftPos + PALETTE_X + 15, topPos + PALETTE_Y + 51, 0xFF000000 | selectedColor);
        guiGraphics.outline(leftPos + PALETTE_X, topPos + PALETTE_Y + 38, 15, 13, 0xFFFFFFFF);
        drawScaledCentered(guiGraphics, "Clear", leftPos + 22, topPos + TOOL_Y, 0.55F, 0xFFFFFFFF);
        drawScaledCentered(guiGraphics, "Save", leftPos + 52, topPos + TOOL_Y, 0.55F, 0xFFFFFFFF);
        drawScaledCentered(guiGraphics, "Back", leftPos + 82, topPos + TOOL_Y, 0.55F, 0xFFFFFFFF);
    }

    private void renderFileButtonIcon(GuiGraphicsExtractor guiGraphics) {
        int x = leftPos + FILE_BUTTON_X;
        int y = topPos + FILE_BUTTON_Y;
        guiGraphics.outline(x + 2, y + 3, 15, 14, 0xFF063F3F);
        guiGraphics.fill(x + 5, y + 5, x + 11, y + 8, 0xFFFFDA68);
        guiGraphics.fill(x + 4, y + 8, x + 16, y + 15, 0xFFFFC83D);
        guiGraphics.fill(x + 5, y + 9, x + 15, y + 14, 0xFFFFE083);
        guiGraphics.outline(x + 4, y + 8, 12, 7, 0xFF7A5A0B);
    }

    private boolean handleTextureEditorClick(int x, int y, int button) {
        if (inside(x, y, EDITOR_X, EDITOR_Y, 16 * EDITOR_CELL, 16 * EDITOR_CELL)) {
            int px = (x - EDITOR_X) / EDITOR_CELL;
            int py = (y - EDITOR_Y) / EDITOR_CELL;
            discPixels[py * 16 + px] = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? 0 : selectedColor;
            return true;
        }
        for (int index = 0; index < COLORS.length; index++) {
            int sx = PALETTE_X + (index % 2) * 8;
            int sy = PALETTE_Y + (index / 2) * 8;
            if (inside(x, y, sx, sy, 7, 7)) {
                selectedColor = COLORS[index];
                return true;
            }
        }
        if (inside(x, y, PALETTE_X, PALETTE_Y + 38, 15, 13)) {
            openColorPicker();
            return true;
        }
        if (inside(x, y, 8, TOOL_Y - 2, 28, 9)) {
            Arrays.fill(discPixels, 0);
            return true;
        }
        if (inside(x, y, 38, TOOL_Y - 2, 28, 9)) {
            System.arraycopy(discPixels, 0, savedDiscPixels, 0, discPixels.length);
            savedColor = selectedColor;
            textureSaved = true;
            message("Disc texture saved in this GUI session.");
            return true;
        }
        if (inside(x, y, 68, TOOL_Y - 2, 28, 9)) {
            if (textureSaved) {
                System.arraycopy(savedDiscPixels, 0, discPixels, 0, savedDiscPixels.length);
                selectedColor = savedColor;
            } else {
                Arrays.fill(discPixels, 0);
                selectedColor = 0x00AAFF;
            }
            return true;
        }
        return false;
    }

    private void openFilePicker() {
        Minecraft client = Minecraft.getInstance();
        Thread pickerThread = new Thread(() -> {
            String selected = openSupportedFileDialog();
            if (selected != null && !selected.isBlank()) {
                Path path = Path.of(selected);
                client.execute(() -> setSelectedFile(path));
            }
        }, "musicxcst-file-picker");
        pickerThread.setDaemon(true);
        pickerThread.start();
    }

    private void openColorPicker() {
        Minecraft client = Minecraft.getInstance();
        Thread pickerThread = new Thread(() -> {
            String selected = TinyFileDialogs.tinyfd_colorChooser("Choose disc color", String.format(Locale.ROOT, "#%06X", selectedColor), null, null);
            if (selected != null && selected.matches("#?[0-9a-fA-F]{6}")) {
                String normalized = selected.startsWith("#") ? selected.substring(1) : selected;
                int color = Integer.parseInt(normalized, 16);
                client.execute(() -> selectedColor = color);
            }
        }, "musicxcst-color-picker");
        pickerThread.setDaemon(true);
        pickerThread.start();
    }

    private String openSupportedFileDialog() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(SUPPORTED_FILE_PATTERNS.length);
            for (String pattern : SUPPORTED_FILE_PATTERNS) {
                filters.put(stack.UTF8(pattern));
            }
            filters.flip();
            return TinyFileDialogs.tinyfd_openFileDialog("Choose music file", null, filters, "Supported audio/video files", false);
        }
    }

    private void setSelectedFile(Path path) {
        selectedFile = path;
        updatingPathBox = true;
        pathBox.setValue(shortenFileName(path.getFileName().toString(), pathBox.getWidth()));
        updatingPathBox = false;
    }

    private void saveState() {
        if (nameBox == null || pathBox == null) {
            return;
        }
        SAVED_STATES.put(menu.pos(), new ScreenState(nameBox.getValue(), pathBox.getValue(), selectedFile, discPixels.clone(), selectedColor));
    }

    private void restoreState() {
        ScreenState state = SAVED_STATES.get(menu.pos());
        if (state == null) {
            return;
        }
        nameBox.setValue(state.name);
        updatingPathBox = true;
        pathBox.setValue(state.path);
        updatingPathBox = false;
        selectedFile = state.selectedFile;
        System.arraycopy(state.discPixels, 0, discPixels, 0, Math.min(state.discPixels.length, discPixels.length));
        selectedColor = state.selectedColor;
    }

    private String shortenFileName(String fileName, int maxWidth) {
        int usableWidth = Math.round(maxWidth / 0.8F) - 8;
        if (this.font.width(fileName) <= usableWidth) {
            return fileName;
        }
        String suffix = "...";
        int suffixWidth = this.font.width(suffix);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < fileName.length(); index++) {
            if (this.font.width(builder.toString() + fileName.charAt(index)) + suffixWidth > usableWidth) {
                break;
            }
            builder.append(fileName.charAt(index));
        }
        return builder + suffix;
    }

    private int textureColor() {
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        int count = 0;
        for (int pixel : discPixels) {
            if (pixel != 0) {
                red += (pixel >> 16) & 0xFF;
                green += (pixel >> 8) & 0xFF;
                blue += pixel & 0xFF;
                count++;
            }
        }
        if (count == 0) {
            return selectedColor;
        }
        return ((int) (red / count) << 16) | ((int) (green / count) << 8) | (int) (blue / count);
    }

    private boolean isSupportedFile(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot >= path.length() - 1) {
            return false;
        }
        return SUPPORTED_FILE_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static boolean inside(int x, int y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
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

    private record ScreenState(String name, String path, Path selectedFile, int[] discPixels, int selectedColor) {
    }
}
