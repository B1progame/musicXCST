package de.coulees.B1progame.musicxcst.client;

import de.coulees.B1progame.musicxcst.client.audio.ClientAudioDownloadManager;
import de.coulees.B1progame.musicxcst.client.audio.CustomAudioEngine;
import de.coulees.B1progame.musicxcst.client.screen.JukeboxSettingsScreen;
import de.coulees.B1progame.musicxcst.network.AudioCacheWarmPayload;
import de.coulees.B1progame.musicxcst.network.AudioCachePrunePayload;
import de.coulees.B1progame.musicxcst.network.AudioChunkPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxSettingsOpenPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import de.coulees.B1progame.musicxcst.network.JukeboxStartPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxStopPayload;

public class MusicxcstClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientMusicUploader.register();
        ClientPlayNetworking.registerGlobalReceiver(JukeboxStartPayload.TYPE, (payload, context) -> context.client().execute(() -> ClientAudioDownloadManager.handleStart(payload)));
        ClientPlayNetworking.registerGlobalReceiver(JukeboxSettingsOpenPayload.TYPE, (payload, context) -> context.client().execute(() -> context.client().setScreen(new JukeboxSettingsScreen(payload.pos(), payload.looping()))));
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
