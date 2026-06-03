package de.coulees.B1progame.musicxcst.client;

import de.coulees.B1progame.musicxcst.client.audio.ClientAudioDownloadManager;
import de.coulees.B1progame.musicxcst.client.audio.CustomAudioEngine;
import de.coulees.B1progame.musicxcst.client.render.CustomDiscItemDecoration;
import de.coulees.B1progame.musicxcst.client.render.CdWriterBlockRenderer;
import de.coulees.B1progame.musicxcst.client.screen.CdWriterScreen;
import de.coulees.B1progame.musicxcst.client.screen.JukeboxSettingsScreen;
import de.coulees.B1progame.musicxcst.init.ModBlockEntities;
import de.coulees.B1progame.musicxcst.init.ModMenuTypes;
import de.coulees.B1progame.musicxcst.network.AudioCacheWarmPayload;
import de.coulees.B1progame.musicxcst.network.AudioCachePrunePayload;
import de.coulees.B1progame.musicxcst.network.AudioChunkPayload;
import de.coulees.B1progame.musicxcst.network.ClientMusicUploadRequestPayload;
import de.coulees.B1progame.musicxcst.network.CdWriterDonePayload;
import de.coulees.B1progame.musicxcst.network.JukeboxSettingsOpenPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxVolumeUpdatePayload;
import de.coulees.B1progame.musicxcst.network.MusicLimitConfirmPayload;
import de.coulees.B1progame.musicxcst.network.MusicLimitConfirmResponsePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.network.chat.Component;
import de.coulees.B1progame.musicxcst.network.JukeboxStartPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxStopPayload;

public class MusicxcstClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientMusicUploader.register();
        CustomDiscItemDecoration.register();
        BlockEntityRenderers.register(ModBlockEntities.CD_WRITER, CdWriterBlockRenderer::new);
        MenuScreens.register(ModMenuTypes.CD_WRITER, CdWriterScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(ClientMusicUploadRequestPayload.TYPE, (payload, context) -> context.client().execute(() -> ClientMusicUploader.startUpload(context.client(), payload.name(), payload.path())));
        ClientPlayNetworking.registerGlobalReceiver(CdWriterDonePayload.TYPE, (payload, context) -> context.client().execute(() -> {
            if (context.client().screen instanceof CdWriterScreen screen) {
                screen.finishConverting(payload.pos());
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(JukeboxStartPayload.TYPE, (payload, context) -> context.client().execute(() -> ClientAudioDownloadManager.handleStart(payload)));
        ClientPlayNetworking.registerGlobalReceiver(JukeboxSettingsOpenPayload.TYPE, (payload, context) -> context.client().execute(() -> context.client().setScreen(new JukeboxSettingsScreen(payload.pos(), payload.looping(), payload.volumePercent()))));
        ClientPlayNetworking.registerGlobalReceiver(JukeboxVolumeUpdatePayload.TYPE, (payload, context) -> context.client().execute(() -> CustomAudioEngine.updateVolume(payload.pos(), payload.volumePercent())));
        ClientPlayNetworking.registerGlobalReceiver(MusicLimitConfirmPayload.TYPE, (payload, context) -> context.client().execute(() -> context.client().setScreen(new ConfirmScreen(
                confirmed -> ClientPlayNetworking.send(new MusicLimitConfirmResponsePayload(payload.pos(), confirmed)),
                Component.literal("Music file limit reached"),
                Component.literal("You have reached the server limit of " + payload.limit() + " music files. Continuing will delete your oldest uploaded track. Continue?"),
                Component.literal("Yes"),
                Component.literal("No")
        ))));
        ClientPlayNetworking.registerGlobalReceiver(AudioCachePrunePayload.TYPE, (payload, context) -> context.client().execute(() -> ClientAudioDownloadManager.handleCachePrune(payload)));
        ClientPlayNetworking.registerGlobalReceiver(AudioCacheWarmPayload.TYPE, (payload, context) -> context.client().execute(() -> ClientAudioDownloadManager.handleCacheWarm(payload)));
        ClientPlayNetworking.registerGlobalReceiver(AudioChunkPayload.TYPE, (payload, context) -> context.client().execute(() -> ClientAudioDownloadManager.handleChunk(payload)));
        ClientPlayNetworking.registerGlobalReceiver(JukeboxStopPayload.TYPE, (payload, context) -> context.client().execute(() -> CustomAudioEngine.stop(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(CustomAudioEngine::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientAudioDownloadManager.clear();
            CustomAudioEngine.stopAll();
        });
    }
}
