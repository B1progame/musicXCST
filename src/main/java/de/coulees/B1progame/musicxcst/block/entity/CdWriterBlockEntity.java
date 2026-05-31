package de.coulees.B1progame.musicxcst.block.entity;

import de.coulees.B1progame.musicxcst.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class CdWriterBlockEntity extends BlockEntity {
    public CdWriterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CD_WRITER, pos, state);
    }
}
