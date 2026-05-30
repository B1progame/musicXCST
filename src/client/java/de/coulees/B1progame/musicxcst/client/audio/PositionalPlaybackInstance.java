package de.coulees.B1progame.musicxcst.client.audio;

import net.minecraft.core.BlockPos;

public record PositionalPlaybackInstance(String musicId, BlockPos sourcePos, int radiusBlocks) {
}
