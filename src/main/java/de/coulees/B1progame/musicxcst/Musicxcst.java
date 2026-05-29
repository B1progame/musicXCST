package de.coulees.B1progame.musicxcst;

import de.coulees.B1progame.musicxcst.command.CstMusicCommands;
import de.coulees.B1progame.musicxcst.init.ModItems;
import de.coulees.B1progame.musicxcst.network.JukeboxStartPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxStopPayload;
import de.coulees.B1progame.musicxcst.service.MusicLibraryService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
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
        PayloadTypeRegistry.clientboundPlay().registerLarge(JukeboxStartPayload.TYPE, JukeboxStartPayload.CODEC, 32 * 1024 * 1024);
        PayloadTypeRegistry.clientboundPlay().register(JukeboxStopPayload.TYPE, JukeboxStopPayload.CODEC);
        ModItems.register();
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> entries.accept(ModItems.BLUEPRINT_CD));
        CommandRegistrationCallback.EVENT.register(CstMusicCommands::register);
        ServerLifecycleEvents.SERVER_STARTED.register(LIBRARY::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(LIBRARY::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(LIBRARY::onServerTick);
    }
}
