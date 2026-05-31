package de.coulees.B1progame.musicxcst.service.audio;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlaybackSessionManager {
    private final Map<BlockPos, PlaybackSession> sessions = new LinkedHashMap<>();
    private final Map<BlockPos, Set<UUID>> listeners = new HashMap<>();

    public void start(PlaybackSession session) {
        sessions.put(session.sourcePos(), session);
        listeners.remove(session.sourcePos());
    }

    public void stop(BlockPos sourcePos) {
        sessions.remove(sourcePos);
        listeners.remove(sourcePos);
    }

    public Map<BlockPos, PlaybackSession> sessions() {
        return Map.copyOf(sessions);
    }

    public PlaybackSession session(BlockPos sourcePos) {
        return sessions.get(sourcePos);
    }

    public boolean markListening(BlockPos sourcePos, UUID playerId) {
        return listeners.computeIfAbsent(sourcePos, ignored -> new HashSet<>()).add(playerId);
    }

    public boolean unmarkListening(BlockPos sourcePos, UUID playerId) {
        Set<UUID> current = listeners.get(sourcePos);
        if (current == null) {
            return false;
        }
        boolean removed = current.remove(playerId);
        if (current.isEmpty()) {
            listeners.remove(sourcePos);
        }
        return removed;
    }

    public void clear() {
        sessions.clear();
        listeners.clear();
    }

    public record PlaybackSession(String musicId, ResourceKey<Level> dimension, BlockPos sourcePos, int radiusBlocks, long startedAtMillis, long durationMillis) {
    }
}
