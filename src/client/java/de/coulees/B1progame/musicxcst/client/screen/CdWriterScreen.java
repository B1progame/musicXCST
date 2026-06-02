package de.coulees.B1progame.musicxcst.client.screen;

import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.client.ClientMusicUploader;
import de.coulees.B1progame.musicxcst.data.DiscData;
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
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

public final class CdWriterScreen extends AbstractContainerScreen<CdWriterMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "textures/gui/cd_writer.png");
    private static final Identifier PRINT_BUTTON_TEXTURE = Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "textures/gui/print_button_with_name.png");
    private static final Identifier EXPLORER_BUTTON_TEXTURE = Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "textures/gui/explorer_button.png");
    private static final Identifier DESIGN_PANEL_TEXTURE = Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "textures/gui/texture_editor_with_link_placement.png");
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
    private static final int FILE_BUTTON_WIDTH = 20;
    private static final int FILE_BUTTON_HEIGHT = 20;
    private static final int DESIGN_PANEL_X = 8;
    private static final int DESIGN_PANEL_Y = 70;
    private static final int DESIGN_PANEL_WIDTH = 88;
    private static final int DESIGN_PANEL_HEIGHT = 88;
    private static final int DESIGN_PREVIEW_X = DESIGN_PANEL_X + 10;
    private static final int DESIGN_PREVIEW_Y = DESIGN_PANEL_Y + 10;
    private static final int DESIGN_PREVIEW_CELL = 2;
    private static final int THEME_X = DESIGN_PANEL_X + 40;
    private static final int THEME_Y = DESIGN_PANEL_Y + 23;
    private static final int THEME_SIZE = 6;
    private static final int ADVANCED_BUTTON_X = DESIGN_PANEL_X + 55;
    private static final int ADVANCED_BUTTON_Y = DESIGN_PANEL_Y + 46;
    private static final int ADVANCED_BUTTON_WIDTH = 16;
    private static final int ADVANCED_BUTTON_HEIGHT = 7;
    private static final int IMPORT_BOX_X = DESIGN_PANEL_X + 10;
    private static final int IMPORT_BOX_Y = DESIGN_PANEL_Y + 64;
    private static final int IMPORT_BOX_WIDTH = 49;
    private static final int IMPORT_BOX_HEIGHT = 10;
    private static final int IMPORT_BUTTON_X = DESIGN_PANEL_X + 63;
    private static final int IMPORT_BUTTON_Y = DESIGN_PANEL_Y + 64;
    private static final int IMPORT_BUTTON_WIDTH = 15;
    private static final int IMPORT_BUTTON_HEIGHT = 10;
    private static final String[] SUPPORTED_FILE_PATTERNS = {
            "*.mp3", "*.mp4", "*.wav", "*.ogg", "*.flac", "*.m4a", "*.aac", "*.webm", "*.avi"
    };
    private static final Set<String> SUPPORTED_FILE_EXTENSIONS = Set.of("mp3", "mp4", "wav", "ogg", "flac", "m4a", "aac", "webm", "avi");
    private static final Map<BlockPos, ScreenState> SAVED_STATES = new HashMap<>();
    private static final int[] THEME_COLORS = {
            0xFF0000, 0xFCFF00, 0x15FF00, 0xDF00FF, 0x009DFF, 0x3A4246
    };

    private EditBox nameBox;
    private EditBox pathBox;
    private EditBox designIdBox;
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
        resetWorkingDesign();
        saveWorkingDesign();
    }

    @Override
    protected void init() {
        super.init();
        this.nameBox = addRenderableWidget(new ScaledEditBox(this.font, leftPos + 14, topPos + 15, 174, 18, Component.literal("Song name")));
        this.nameBox.setBordered(false);
        this.nameBox.setMaxLength(64);
        this.nameBox.setSuggestion("Song name");

        this.pathBox = addRenderableWidget(new ScaledEditBox(this.font, leftPos + 14, topPos + 41, 164, 18, Component.literal("File path")));
        this.pathBox.setBordered(false);
        this.pathBox.setMaxLength(260);
        this.pathBox.setSuggestion("File path");
        this.pathBox.setResponder(value -> {
            if (!updatingPathBox) {
                selectedFile = null;
            }
        });

        this.designIdBox = addRenderableWidget(new ScaledEditBox(this.font, leftPos + IMPORT_BOX_X, topPos + IMPORT_BOX_Y, IMPORT_BOX_WIDTH, IMPORT_BOX_HEIGHT, Component.literal("Design ID")));
        this.designIdBox.setBordered(false);
        this.designIdBox.setMaxLength(DiscData.DESIGN_ID_MAX_LENGTH);
        this.designIdBox.setSuggestion("Design ID");
        restoreState();
        if (this.designIdBox.getValue().isBlank()) {
            updateDesignIdBox();
        }

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
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, EXPLORER_BUTTON_TEXTURE, leftPos + FILE_BUTTON_X, topPos + FILE_BUTTON_Y, 0, 0, FILE_BUTTON_WIDTH, FILE_BUTTON_HEIGHT, FILE_BUTTON_WIDTH, FILE_BUTTON_HEIGHT);
        renderTextureEditor(guiGraphics);
        if (uploading) {
            guiGraphics.centeredText(this.font, progressText.isBlank() ? loadingText() : progressText, leftPos + GUI_WIDTH / 2, topPos - 13, 0xFFFFE680);
        }
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, PRINT_BUTTON_TEXTURE, leftPos + PRINT_BUTTON_X, topPos + PRINT_BUTTON_Y, 0, 0, PRINT_BUTTON_WIDTH, PRINT_BUTTON_HEIGHT, PRINT_BUTTON_WIDTH, PRINT_BUTTON_HEIGHT);
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
                EditBox next = focusedBox == nameBox ? pathBox : focusedBox == pathBox ? designIdBox : nameBox;
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
                ClientPlayNetworking.send(new CdWriterWritePayload(menu.pos(), name, hexColor(), uploadedFileName, designForWrite())), progress -> this.progressText = progress);
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
        if (designIdBox != null && designIdBox.canConsumeInput()) {
            return designIdBox;
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
        int x0 = leftPos + DESIGN_PANEL_X;
        int y0 = topPos + DESIGN_PANEL_Y;
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, DESIGN_PANEL_TEXTURE, x0, y0, 0, 0, DESIGN_PANEL_WIDTH, DESIGN_PANEL_HEIGHT, DESIGN_PANEL_WIDTH, DESIGN_PANEL_HEIGHT);
        renderDiscPreview(guiGraphics, DESIGN_PREVIEW_X, DESIGN_PREVIEW_Y, DESIGN_PREVIEW_CELL);
    }

    private boolean handleTextureEditorClick(int x, int y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        for (int index = 0; index < THEME_COLORS.length; index++) {
            int sx = THEME_X + (index % 3) * 11;
            int sy = THEME_Y + (index / 3) * 11;
            if (inside(x, y, sx, sy, THEME_SIZE, THEME_SIZE)) {
                applyTheme(THEME_COLORS[index]);
                return true;
            }
        }

        if (inside(x, y, ADVANCED_BUTTON_X, ADVANCED_BUTTON_Y, ADVANCED_BUTTON_WIDTH, ADVANCED_BUTTON_HEIGHT)) {
            openAdvancedEditor();
            return true;
        }

        if (inside(x, y, IMPORT_BUTTON_X, IMPORT_BUTTON_Y, IMPORT_BUTTON_WIDTH, IMPORT_BUTTON_HEIGHT)) {
            importDesignId();
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        return super.mouseReleased(event);
    }

    private void renderDiscPreview(GuiGraphicsExtractor guiGraphics, int x, int y, int cell) {
        int previewX = leftPos + x;
        int previewY = topPos + y;
        for (int py = 0; py < 16; py++) {
            for (int px = 0; px < 16; px++) {
                int value = discPixels[py * 16 + px];
                int color = (value >>> 24) == 0 ? (((px + py) & 1) == 0 ? 0x44FFFFFF : 0x22000000) : value;
                guiGraphics.fill(previewX + px * cell, previewY + py * cell, previewX + (px + 1) * cell, previewY + (py + 1) * cell, color);
            }
        }
        guiGraphics.outline(previewX - 1, previewY - 1, DiscData.DESIGN_SIZE * cell + 2, DiscData.DESIGN_SIZE * cell + 2, 0xFFE8D7EF);
    }

    private void applyTheme(int color) {
        int[] theme = themedDesign(color);
        System.arraycopy(theme, 0, discPixels, 0, discPixels.length);
        selectedColor = color;
        saveWorkingDesign();
    }

    private void resetWorkingDesign() {
        int[] defaults = DiscData.defaultDesign();
        System.arraycopy(defaults, 0, discPixels, 0, discPixels.length);
        selectedColor = 0x38BDF8;
    }

    private void saveWorkingDesign() {
        System.arraycopy(DiscData.sanitizeDesign(discPixels), 0, savedDiscPixels, 0, savedDiscPixels.length);
        savedColor = selectedColor;
        textureSaved = true;
        updateDesignIdBox();
    }

    private void importDesignId() {
        String designId = designIdBox.getValue().trim();
        DiscData.decodeDesignId(designId).ifPresentOrElse(pixels -> {
            System.arraycopy(pixels, 0, discPixels, 0, discPixels.length);
            selectedColor = textureColor(pixels);
            saveWorkingDesign();
            message("Imported disc design.");
        }, () -> message("Invalid disc design ID."));
    }

    private void openAdvancedEditor() {
        Minecraft client = Minecraft.getInstance();
        try {
            Path editor = client.gameDirectory.toPath()
                    .resolve("config")
                    .resolve(Musicxcst.MOD_ID)
                    .resolve("disc-design-editor.html")
                    .normalize();
            Files.createDirectories(editor.getParent());
            Files.writeString(editor, advancedEditorHtml(DiscData.encodeDesignId(discPixels)), StandardCharsets.UTF_8);
            Util.getPlatform().openUri(editor.toUri());
            message("Opened local disc design editor. Paste the finished Design ID back here and press Import.");
        } catch (IOException exception) {
            message("Could not write the local design editor: " + exception.getMessage());
        }
    }

    private void updateDesignIdBox() {
        if (designIdBox != null) {
            designIdBox.setValue(DiscData.encodeDesignId(discPixels));
        }
    }

    private static int[] themedDesign(int accentColor) {
        int[] pixels = new int[DiscData.DESIGN_PIXELS];
        int center = DiscData.DESIGN_SIZE / 2;
        int dark = darken(accentColor, 0.28F);
        int mid = darken(accentColor, 0.70F);
        for (int y = 0; y < DiscData.DESIGN_SIZE; y++) {
            for (int x = 0; x < DiscData.DESIGN_SIZE; x++) {
                int dx = x - center;
                int dy = y - center;
                int distanceSquared = dx * dx + dy * dy;
                int index = y * DiscData.DESIGN_SIZE + x;
                if (distanceSquared <= 49) {
                    pixels[index] = 0xFF000000 | dark;
                }
                if (distanceSquared <= 35) {
                    pixels[index] = 0xFF000000 | accentColor;
                }
                if (distanceSquared <= 15) {
                    pixels[index] = 0xFF000000 | mid;
                }
                if (distanceSquared <= 5) {
                    pixels[index] = 0xFFF8FAFC;
                }
            }
        }
        return pixels;
    }

    private static int darken(int rgb, float factor) {
        int red = Math.max(0, Math.min(255, Math.round(((rgb >> 16) & 0xFF) * factor)));
        int green = Math.max(0, Math.min(255, Math.round(((rgb >> 8) & 0xFF) * factor)));
        int blue = Math.max(0, Math.min(255, Math.round((rgb & 0xFF) * factor)));
        return (red << 16) | (green << 8) | blue;
    }

    private static String advancedEditorHtml(String initialDesignId) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>MusicXCST Disc Design Editor</title>
                <style>
                :root{color-scheme:dark;font-family:Arial,sans-serif;background:#0d1116;color:#d6dee8}
                body{margin:0;height:100vh;overflow:hidden;background:#0d1116}
                header{height:32px;background:#181d24;border-bottom:1px solid #2b3440;display:flex;align-items:center;gap:18px;padding:0 12px;color:#b9c4d0}
                header .tab{height:32px;display:flex;align-items:center;border-bottom:2px solid transparent;padding:0 8px}
                header .tab.active{color:#fff;border-color:#2f80ff;background:#202832}
                main{height:calc(100vh - 56px);display:grid;grid-template-columns:46px 1fr 320px}
                .toolbar{background:#151a21;border-right:1px solid #2b3440;padding:8px 6px;display:flex;flex-direction:column;gap:7px}
                .tool{height:32px;border-radius:3px;padding:0;font-weight:700}
                .stage{display:grid;place-items:center;background:#10151a}
                .canvasWrap{padding:42px;background:#0e1318;border:1px solid #27313c;box-shadow:inset 0 0 0 1px #151c24}
                canvas{image-rendering:pixelated;background:#111820;border:1px solid #26323d}
                aside{background:#1a2028;border-left:1px solid #2d3844;display:grid;grid-template-rows:auto auto auto 1fr;min-width:0}
                .panel{border-bottom:1px solid #2d3844;padding:10px}
                h1,h2{font-size:13px;letter-spacing:.04em;font-weight:400;color:#aeb9c6;margin:0 0 8px;text-transform:uppercase}
                button,input,textarea{font:13px Arial,sans-serif}
                button{background:#222a34;color:#d9e0e8;border:1px solid #3a4653;padding:7px 9px;cursor:pointer}
                button:hover,button.active{border-color:#2f80ff;background:#283342}
                input[type=color]{width:56px;height:32px;background:#222a34;border:1px solid #3a4653}
                textarea{width:100%;height:126px;box-sizing:border-box;background:#111820;color:#d9e0e8;border:1px solid #3a4653;padding:8px;resize:vertical;font-family:Consolas,monospace;font-size:12px}
                .row{display:flex;gap:7px;align-items:center;flex-wrap:wrap}
                .hint{color:#8f9ba8;font-size:12px;line-height:1.35}
                .channels{display:grid;grid-template-columns:repeat(3,1fr);gap:4px;margin-top:8px;color:#c9d2dc;text-align:center}
                .channels span{background:#18202a;padding:5px 0;border-bottom:2px solid #2f80ff}
                .keys{display:grid;grid-template-columns:54px 1fr;gap:5px;color:#aeb9c6;font-size:12px}
                .key{background:#101820;border:1px solid #34404d;text-align:center;padding:4px 0;color:#fff}
                footer{height:23px;background:#181d24;border-top:1px solid #2b3440;display:flex;justify-content:space-between;align-items:center;padding:0 12px;color:#8f9ba8;font-size:12px}
                </style>
                </head>
                <body>
                <header><div class="tab active">Texture</div><div class="tab">Paint</div><div class="tab">Preview</div><div>MusicXCST Disc Texture Editor</div></header>
                <main>
                  <nav class="toolbar">
                    <button id="draw" class="tool active" title="Brush (B)">B</button>
                    <button id="erase" class="tool" title="Eraser (E)">E</button>
                    <button id="pick" class="tool" title="Color Picker (Alt)">Alt</button>
                  </nav>
                  <section class="stage">
                    <div class="canvasWrap"><canvas id="grid" width="720" height="720" aria-label="16 by 16 disc pixel grid"></canvas></div>
                  </section>
                  <aside>
                  <section class="panel">
                    <h1>Color</h1>
                    <div class="row">
                      <input id="color" type="color" value="#38bdf8">
                      <button id="draw2" class="active">Brush</button>
                      <button id="erase2">Eraser</button>
                    </div>
                    <div class="channels"><span id="r">0</span><span id="g">0</span><span id="b">0</span></div>
                  </section>
                  <section class="panel">
                    <h2>Tools</h2>
                    <div class="row">
                      <button id="clear">Clear</button>
                      <button id="reset">Reset</button>
                      <button id="finish">Finish</button>
                    </div>
                  </section>
                  <section class="panel">
                    <h2>Shortcuts</h2>
                    <div class="keys"><div class="key">B</div><div>Brush</div><div class="key">E</div><div>Eraser</div><div class="key">Alt</div><div>Color picker</div></div>
                  </section>
                  <section class="panel">
                    <h2>Design ID</h2>
                    <textarea id="output" spellcheck="false" placeholder="Click Finish to generate a Design ID."></textarea>
                    <div class="hint">Compact palette code. Paste it into the CD Writer GUI and press Import. This file is local and offline.</div>
                  </section>
                  </aside>
                </main>
                <footer><span>texture</span><span>100%</span></footer>
                <script>
                const PREFIX="MXC1:";
                const LEGACY="MXC16.";
                const CHARS="0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_";
                const SIZE=16;
                const initial="__INITIAL_ID__";
                const canvas=document.getElementById("grid");
                const ctx=canvas.getContext("2d");
                const color=document.getElementById("color");
                const output=document.getElementById("output");
                let mode="draw";
                let dragging=false;
                let pixels=decode(initial)||defaultPixels();
                function defaultPixels(){
                  const p=new Array(SIZE*SIZE).fill(0);
                  const rows=[
                    "................","................","................",".....AAAAA......",
                    "..AAABBBBBAAA...",".ABBBBAAABBBBA..","ABBBBBABBABBBBA.",
                    "ABBBBBAAABBBBBA.","ABBBBBABBABBBBA.","AABBBBAAABBBBAA.",
                    ".AAAABBBBBAAAA..","..AAAAAAAAAAA...",".....AAAAA......",
                    "................","................","................"
                  ];
                  const colors={A:0xff212121|0,B:0xff616161|0};
                  for(let y=0;y<SIZE;y++)for(let x=0;x<SIZE;x++){
                    const ch=rows[y][x];
                    p[y*SIZE+x]=colors[ch]||0;
                  }
                  return p;
                }
                function draw(){
                  ctx.clearRect(0,0,720,720);
                  const cell=720/SIZE;
                  for(let y=0;y<SIZE;y++)for(let x=0;x<SIZE;x++){
                    const v=pixels[y*SIZE+x]>>>0;
                    if((v>>>24)===0){ctx.fillStyle=((x+y)&1)?"#2b2230":"#4a3c52";}
                    else{ctx.fillStyle="#"+(v&0xffffff).toString(16).padStart(6,"0");}
                    ctx.fillRect(x*cell,y*cell,cell,cell);
                    ctx.strokeStyle="rgba(255,255,255,.08)";
                    ctx.strokeRect(x*cell,y*cell,cell,cell);
                  }
                }
                function pos(e){
                  const r=canvas.getBoundingClientRect();
                  return {x:Math.max(0,Math.min(15,Math.floor((e.clientX-r.left)/(r.width/SIZE)))),y:Math.max(0,Math.min(15,Math.floor((e.clientY-r.top)/(r.height/SIZE))))};
                }
                function paint(e){
                  const p=pos(e);
                  pixels[p.y*SIZE+p.x]=mode==="erase"?0:(0xff000000|parseInt(color.value.slice(1),16));
                  draw();
                }
                function setMode(next){
                  mode=next;
                  document.getElementById("draw").classList.toggle("active",mode==="draw");
                  document.getElementById("draw2").classList.toggle("active",mode==="draw");
                  document.getElementById("erase").classList.toggle("active",mode==="erase");
                  document.getElementById("erase2").classList.toggle("active",mode==="erase");
                }
                function colorRgb(){
                  const v=parseInt(color.value.slice(1),16);
                  document.getElementById("r").textContent=(v>>16)&255;
                  document.getElementById("g").textContent=(v>>8)&255;
                  document.getElementById("b").textContent=v&255;
                }
                function encode(){
                  const palette=[], body=[];
                  for(const raw of pixels){
                    const v=raw>>>0;
                    if((v>>>24)===0){body.push(".");continue}
                    const rgb=(v&0xffffff).toString(16).padStart(6,"0").toUpperCase();
                    let i=palette.indexOf(rgb);
                    if(i<0){palette.push(rgb);i=palette.length-1}
                    body.push(CHARS[i]);
                  }
                  return PREFIX+palette.join(",")+";"+body.join("");
                }
                function decode(id){
                  if(!id||id.length>1400)return null;
                  if(id.startsWith(PREFIX)){
                    try{
                      const cut=id.indexOf(";");
                      if(cut<0)return null;
                      const palette=id.slice(PREFIX.length,cut).split(",").filter(Boolean);
                      const body=id.slice(cut+1);
                      if(body.length!==SIZE*SIZE)return null;
                      return [...body].map(ch=>{
                        if(ch==="."||ch===" ")return 0;
                        const i=CHARS.indexOf(ch);
                        if(i<0||i>=palette.length)throw new Error("bad index");
                        return 0xff000000|parseInt(palette[i],16);
                      });
                    }catch(e){return null;}
                  }
                  if(!id.startsWith(LEGACY))return null;
                  try{
                    let s=id.slice(LEGACY.length).replaceAll("-","+").replaceAll("_","/");
                    while(s.length%4)s+="=";
                    const bin=atob(s);
                    if(bin.length!==SIZE*SIZE*4)return null;
                    const bytes=new Uint8Array(bin.length);
                    for(let i=0;i<bin.length;i++)bytes[i]=bin.charCodeAt(i);
                    const view=new DataView(bytes.buffer);
                    const arr=new Array(SIZE*SIZE);
                    for(let i=0;i<arr.length;i++){
                      const v=view.getInt32(i*4,false);
                      arr[i]=((v>>>24)===0)?0:(0xff000000|(v&0xffffff));
                    }
                    return arr;
                  }catch(e){return null;}
                }
                canvas.addEventListener("mousedown",e=>{dragging=true;paint(e)});
                canvas.addEventListener("mousemove",e=>{if(dragging)paint(e)});
                window.addEventListener("mouseup",()=>dragging=false);
                document.getElementById("draw").onclick=()=>setMode("draw");
                document.getElementById("draw2").onclick=()=>setMode("draw");
                document.getElementById("erase").onclick=()=>setMode("erase");
                document.getElementById("erase2").onclick=()=>setMode("erase");
                document.getElementById("pick").onclick=()=>color.click();
                document.getElementById("clear").onclick=()=>{pixels.fill(0);draw()};
                document.getElementById("reset").onclick=()=>{pixels=defaultPixels();draw()};
                document.getElementById("finish").onclick=async()=>{
                  const id=encode();
                  output.value=id;
                  try{await navigator.clipboard.writeText(id)}catch(e){}
                };
                color.addEventListener("input",colorRgb);
                window.addEventListener("keydown",event=>{
                  if(event.key==="b"||event.key==="B"){setMode("draw");event.preventDefault();}
                  if(event.key==="e"||event.key==="E"){setMode("erase");event.preventDefault();}
                  if(event.key==="Alt"){color.click();event.preventDefault();}
                });
                colorRgb();
                draw();
                </script>
                </body>
                </html>
                """.replace("__INITIAL_ID__", initialDesignId);
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
        if (nameBox == null || pathBox == null || designIdBox == null) {
            return;
        }
        SAVED_STATES.put(menu.pos(), new ScreenState(nameBox.getValue(), pathBox.getValue(), designIdBox.getValue(), selectedFile, DiscData.sanitizeDesign(discPixels), DiscData.sanitizeDesign(savedDiscPixels), selectedColor, savedColor, textureSaved));
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
        designIdBox.setValue(state.designId);
        selectedFile = state.selectedFile;
        System.arraycopy(DiscData.sanitizeDesign(state.discPixels), 0, discPixels, 0, discPixels.length);
        System.arraycopy(DiscData.sanitizeDesign(state.savedDiscPixels), 0, savedDiscPixels, 0, savedDiscPixels.length);
        selectedColor = state.selectedColor;
        savedColor = state.savedColor;
        textureSaved = state.textureSaved;
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
        return textureColor(designForWrite());
    }

    private int[] designForWrite() {
        return DiscData.sanitizeDesign(textureSaved ? savedDiscPixels : discPixels);
    }

    private int textureColor(int[] pixels) {
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        int count = 0;
        for (int pixel : DiscData.sanitizeDesign(pixels)) {
            if ((pixel >>> 24) != 0) {
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

    private void drawButton(GuiGraphicsExtractor guiGraphics, String text, int x, int y, int width, int height) {
        guiGraphics.fill(leftPos + x, topPos + y, leftPos + x + width, topPos + y + height, 0xFF2A2030);
        guiGraphics.outline(leftPos + x, topPos + y, width, height, 0xFF7B3A70);
        drawScaledCentered(guiGraphics, text, leftPos + x + width / 2, topPos + y + 2, 0.45F, 0xFFFFFFFF);
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

    private record ScreenState(String name, String path, String designId, Path selectedFile, int[] discPixels, int[] savedDiscPixels, int selectedColor, int savedColor, boolean textureSaved) {
    }
}
