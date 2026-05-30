package de.coulees.B1progame.musicxcst.service.audio;

import de.coulees.B1progame.musicxcst.data.MusicEntry;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AudioMetadataIndex {
    private final Map<String, MusicEntry> entries = new LinkedHashMap<>();

    public MusicEntry get(String musicId) {
        return entries.get(musicId);
    }

    public void put(MusicEntry entry) {
        entries.put(entry.musicId, entry);
    }

    public Map<String, MusicEntry> snapshot() {
        return Map.copyOf(entries);
    }
}
