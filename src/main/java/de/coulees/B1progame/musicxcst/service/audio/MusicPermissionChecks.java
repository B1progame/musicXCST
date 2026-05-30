package de.coulees.B1progame.musicxcst.service.audio;

import de.coulees.B1progame.musicxcst.data.MusicEntry;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public final class MusicPermissionChecks {
    public boolean owns(ServerPlayer player, MusicEntry entry) {
        return entry != null && Objects.equals(player.getUUID().toString(), entry.ownerUuid);
    }
}
