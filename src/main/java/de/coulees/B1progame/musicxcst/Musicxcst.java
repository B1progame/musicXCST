package de.coulees.B1progame.musicxcst;

import de.coulees.B1progame.musicxcst.command.CstMusicCommands;
import de.coulees.B1progame.musicxcst.init.ModBlockEntities;
import de.coulees.B1progame.musicxcst.init.ModBlocks;
import de.coulees.B1progame.musicxcst.init.ModItems;
import de.coulees.B1progame.musicxcst.init.ModSounds;
import de.coulees.B1progame.musicxcst.network.AudioCacheWarmPayload;
import de.coulees.B1progame.musicxcst.network.AudioCachePrunePayload;
import de.coulees.B1progame.musicxcst.network.AudioChunkPayload;
import de.coulees.B1progame.musicxcst.network.AudioChunkRequestPayload;
import de.coulees.B1progame.musicxcst.network.ClientMusicUploadChunkPayload;
import de.coulees.B1progame.musicxcst.network.ClientMusicUploadStartPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxSettingsOpenPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxSettingsUpdatePayload;
import de.coulees.B1progame.musicxcst.network.JukeboxStartPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxStopPayload;
import de.coulees.B1progame.musicxcst.service.MusicLibraryService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Musicxcst implements ModInitializer {
    public static final String MOD_ID = "musicxcst";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final int DISC_SCHEMA_VERSION = 1;
    public static final String DISC_DATA_KEY = "musicxcst_disc";
    public static final MusicLibraryService LIBRARY = new MusicLibraryService();

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(JukeboxStartPayload.TYPE, JukeboxStartPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(JukeboxStopPayload.TYPE, JukeboxStopPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(JukeboxSettingsOpenPayload.TYPE, JukeboxSettingsOpenPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AudioCachePrunePayload.TYPE, AudioCachePrunePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AudioCacheWarmPayload.TYPE, AudioCacheWarmPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().registerLarge(AudioChunkPayload.TYPE, AudioChunkPayload.CODEC, 256 * 1024);
        PayloadTypeRegistry.serverboundPlay().register(AudioChunkRequestPayload.TYPE, AudioChunkRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(JukeboxSettingsUpdatePayload.TYPE, JukeboxSettingsUpdatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().registerLarge(ClientMusicUploadChunkPayload.TYPE, ClientMusicUploadChunkPayload.CODEC, 256 * 1024);
        PayloadTypeRegistry.serverboundPlay().register(ClientMusicUploadStartPayload.TYPE, ClientMusicUploadStartPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AudioChunkRequestPayload.TYPE, (payload, context) -> LIBRARY.sendAudioChunk(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(JukeboxSettingsUpdatePayload.TYPE, (payload, context) -> LIBRARY.updateJukeboxSettings(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ClientMusicUploadStartPayload.TYPE, (payload, context) -> LIBRARY.startClientUpload(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ClientMusicUploadChunkPayload.TYPE, (payload, context) -> LIBRARY.receiveClientUploadChunk(context.player(), payload));
        ModSounds.register();
        ModBlocks.register();
        ModBlockEntities.register();
        ModItems.register();
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> {
                    entries.accept(ModItems.BLUEPRINT_CD);
                    entries.accept(ModItems.CD_WRITER);
                });
        CommandRegistrationCallback.EVENT.register(CstMusicCommands::register);
        ServerLifecycleEvents.SERVER_STARTED.register(LIBRARY::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(LIBRARY::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(LIBRARY::onServerTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LIBRARY.onPlayerDisconnected(handler.player));
    }
}
