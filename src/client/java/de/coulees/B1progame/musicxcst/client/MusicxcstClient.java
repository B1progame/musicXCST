package de.coulees.B1progame.musicxcst.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import de.coulees.B1progame.musicxcst.network.JukeboxStartPayload;
import de.coulees.B1progame.musicxcst.network.JukeboxStopPayload;

public class MusicxcstClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(JukeboxStartPayload.TYPE, (payload, context) -> ClientJukeboxAudio.start(payload));
        ClientPlayNetworking.registerGlobalReceiver(JukeboxStopPayload.TYPE, (payload, context) -> ClientJukeboxAudio.stop(payload));
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientJukeboxAudio.tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientJukeboxAudio.stopAll());
    }
}
