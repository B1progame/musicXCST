package de.coulees.B1progame.musicxcst.client.audio;

import de.coulees.B1progame.musicxcst.network.JukeboxStartPayload;

public final class PlaybackSyncManager {
    private PlaybackSyncManager() {
    }

    public static long elapsedMillis(JukeboxStartPayload payload) {
        return Math.max(0L, System.currentTimeMillis() - payload.startedAtMillis());
    }
}
