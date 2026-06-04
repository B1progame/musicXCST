package de.coulees.B1progame.musicxcst.client.screen;

import de.coulees.B1progame.musicxcst.client.ClientFfmpegConfig;
import de.coulees.B1progame.musicxcst.client.ClientFfmpegStatus;
import de.coulees.B1progame.musicxcst.client.ClientMusicUploader;
import de.coulees.B1progame.musicxcst.config.CstMusicConfig;
import de.coulees.B1progame.musicxcst.media.FfmpegLocator;
import de.coulees.B1progame.musicxcst.media.ManagedFfmpegProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class FfmpegSetupScreen extends Screen {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 232;
    private static final int BUTTON_WIDTH = 164;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen previous;
    private final String uploadName;
    private final String uploadPath;
    private final Consumer<String> afterUpload;
    private final Consumer<String> uploadProgress;
    private final FfmpegLocator locator = new FfmpegLocator();
    private final ManagedFfmpegProvider managedProvider = new ManagedFfmpegProvider();
    private String status = "Choose a setup option.";
    private boolean installing;
    private Button systemButton;

    public FfmpegSetupScreen(Screen previous) {
        this(previous, null, null, null, null);
    }

    public FfmpegSetupScreen(Screen previous, String uploadName, String uploadPath, Consumer<String> afterUpload, Consumer<String> uploadProgress) {
        super(Component.literal("FFmpeg Setup"));
        this.previous = previous;
        this.uploadName = uploadName;
        this.uploadPath = uploadPath;
        this.afterUpload = afterUpload;
        this.uploadProgress = uploadProgress;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - PANEL_WIDTH / 2;
        int top = this.height / 2 - PANEL_HEIGHT / 2;
        this.systemButton = addRenderableWidget(Button.builder(Component.literal("Use system FFmpeg"), button -> useSystem())
                .bounds(left + 38, top + 146, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        CstMusicConfig config = ClientFfmpegConfig.load(Minecraft.getInstance());
        CstMusicConfig systemConfig = new CstMusicConfig();
        systemConfig.ffmpegMode = "system";
        this.systemButton.active = locator.locate(Minecraft.getInstance().gameDirectory.toPath(), systemConfig).isPresent();
        if (!this.systemButton.active && "system".equals(FfmpegLocator.normalizedMode(config))) {
            status = "System FFmpeg was not found on PATH.";
        }
        addRenderableWidget(Button.builder(Component.literal("Choose FFmpeg path"), button -> choosePath())
                .bounds(left + 218, top + 146, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Download FFmpeg"), button -> downloadManaged())
                .bounds(left + 38, top + 172, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Use OGG only"), button -> disableFfmpeg())
                .bounds(left + 218, top + 172, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> cancel())
                .bounds(left + 130, top + 202, 160, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(guiGraphics);
        int left = this.width / 2 - PANEL_WIDTH / 2;
        int top = this.height / 2 - PANEL_HEIGHT / 2;
        guiGraphics.fill(left + 3, top + 3, left + PANEL_WIDTH + 3, top + PANEL_HEIGHT + 3, 0x99000000);
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xF0141820);
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + 3, 0xFF5EA1FF);
        guiGraphics.outline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF2F6FB8);

        drawText(guiGraphics, "MusicXCST FFmpeg setup", left + 18, top + 16, 0xFFFFFFFF);
        drawText(guiGraphics, "Needed for MP3, MP4, WAV, FLAC, M4A, AAC, WebM, and AVI conversion.", left + 18, top + 32, 0xFFBFD7FF);

        int textY = top + 55;
        textY = drawWrapped(guiGraphics, "MusicXCST does not ship FFmpeg inside the mod jar. FFmpeg is a third-party executable, so setup only happens after you choose an option.", left + 18, textY, PANEL_WIDTH - 36, 0xFFE7ECF5);
        textY = drawWrapped(guiGraphics, "You can use system FFmpeg, choose a local executable, download a verified managed copy, or keep using compatible OGG files.", left + 18, textY + 5, PANEL_WIDTH - 36, 0xFFE7ECF5);

        guiGraphics.fill(left + 18, top + 118, left + PANEL_WIDTH - 18, top + 136, 0xFF202B36);
        guiGraphics.outline(left + 18, top + 118, PANEL_WIDTH - 36, 18, 0xFF3B5268);
        drawText(guiGraphics, status, left + 25, top + 123, 0xFFFFE08A);
    }

    private void drawText(GuiGraphicsExtractor guiGraphics, String text, int x, int y, int color) {
        guiGraphics.text(this.font, text, x, y, color, false);
    }

    private int drawWrapped(GuiGraphicsExtractor guiGraphics, String text, int x, int y, int maxWidth, int color) {
        int lineY = y;
        for (String line : wrap(text, maxWidth)) {
            drawText(guiGraphics, line, x, lineY, color);
            lineY += 11;
        }
        return lineY;
    }

    private List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (this.font.width(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            current.setLength(0);
            current.append(word);
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private void useSystem() {
        Minecraft client = Minecraft.getInstance();
        CstMusicConfig config = ClientFfmpegConfig.load(client);
        config.ffmpegMode = "system";
        ClientFfmpegConfig.save(client, config);
        if (locator.locate(client.gameDirectory.toPath(), config).isPresent()) {
            ClientFfmpegStatus.markConfigured();
            retryUpload(client);
        } else {
            status = "System FFmpeg was not found on PATH.";
        }
    }

    private void choosePath() {
        if (installing) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Thread picker = new Thread(() -> {
            String selected = openExecutableDialog();
            if (selected == null || selected.isBlank()) {
                return;
            }
            client.execute(() -> {
                CstMusicConfig config = ClientFfmpegConfig.load(client);
                config.ffmpegMode = "path";
                config.ffmpegPath = selected;
                ClientFfmpegConfig.save(client, config);
                if (locator.locate(client.gameDirectory.toPath(), config).isPresent()) {
                    ClientFfmpegStatus.markConfigured();
                    retryUpload(client);
                } else {
                    status = "The selected FFmpeg path did not run successfully.";
                }
            });
        }, "musicxcst-ffmpeg-path-picker");
        picker.setDaemon(true);
        picker.start();
    }

    private String openExecutableDialog() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String[] patterns = os.contains("win") ? new String[]{"*.exe"} : new String[]{"*"};
            PointerBuffer filters = stack.mallocPointer(patterns.length);
            for (String pattern : patterns) {
                filters.put(stack.UTF8(pattern));
            }
            filters.flip();
            return TinyFileDialogs.tinyfd_openFileDialog("Choose FFmpeg executable", null, filters, "FFmpeg executable", false);
        }
    }

    private void downloadManaged() {
        if (installing) {
            return;
        }
        installing = true;
        status = "Downloading managed FFmpeg after your explicit request...";
        Minecraft client = Minecraft.getInstance();
        Thread downloader = new Thread(() -> {
            CstMusicConfig config = ClientFfmpegConfig.load(client);
            try {
                ManagedFfmpegProvider.ManagedInstallResult result = managedProvider.install(
                        client.gameDirectory.toPath(),
                        config,
                        message -> client.execute(() -> status = message)
                );
                config.ffmpegMode = "managed";
                ClientFfmpegConfig.save(client, config);
                client.execute(() -> {
                    status = result.versionLine();
                    installing = false;
                    ClientFfmpegStatus.markConfigured();
                    retryUpload(client);
                });
            } catch (IllegalArgumentException exception) {
                client.execute(() -> {
                    status = exception.getMessage();
                    installing = false;
                });
            }
        }, "musicxcst-managed-ffmpeg-download");
        downloader.setDaemon(true);
        downloader.start();
    }

    private void disableFfmpeg() {
        Minecraft client = Minecraft.getInstance();
        CstMusicConfig config = ClientFfmpegConfig.load(client);
        config.ffmpegMode = "disabled";
        ClientFfmpegConfig.save(client, config);
        ClientFfmpegStatus.markConfigured();
        cancel();
    }

    private void cancel() {
        Minecraft client = Minecraft.getInstance();
        if (uploadName != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal("FFmpeg is required to convert this file type. You can install FFmpeg manually or choose an already supported OGG file."));
        }
        client.setScreen(previous);
    }

    private void retryUpload(Minecraft client) {
        client.setScreen(previous);
        if (uploadName != null && uploadPath != null) {
            ClientMusicUploader.startUpload(client, uploadName, uploadPath, afterUpload, uploadProgress);
        }
    }
}
