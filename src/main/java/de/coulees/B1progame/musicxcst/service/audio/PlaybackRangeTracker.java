package de.coulees.B1progame.musicxcst.service.audio;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class PlaybackRangeTracker {
    public boolean isInRange(ServerPlayer player, BlockPos sourcePos, int radiusBlocks) {
        return player.blockPosition().distSqr(sourcePos) <= (double) radiusBlocks * radiusBlocks;
    }
}
