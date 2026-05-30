package de.coulees.B1progame.musicxcst.service.audio;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PlaybackSessionManager {
    private final Map<BlockPos, PlaybackSession> sessions = new LinkedHashMap<>();

    public void start(PlaybackSession session) {
        sessions.put(session.sourcePos(), session);
    }

    public void stop(BlockPos sourcePos) {
        sessions.remove(sourcePos);
    }

    public Map<BlockPos, PlaybackSession> sessions() {
        return Map.copyOf(sessions);
    }

    public record PlaybackSession(String musicId, ResourceKey<Level> dimension, BlockPos sourcePos, int radiusBlocks, long startedAtMillis) {
    }
}
