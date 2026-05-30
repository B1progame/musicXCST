package de.coulees.B1progame.musicxcst.mixin;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JukeboxBlockEntity.class)
public abstract class JukeboxBlockEntityMixin extends BlockEntity {
    protected JukeboxBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Inject(method = "setTheItem", at = @At("TAIL"))
    private void musicxcst$startCustomPlayback(ItemStack stack, CallbackInfo ci) {
        Level level = getLevel();
        if (level != null && !level.isClientSide()) {
            Musicxcst.LIBRARY.startJukeboxPlayback(level, getBlockPos(), stack);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private static void musicxcst$syncLoadedCustomPlayback(Level level, BlockPos pos, BlockState state, JukeboxBlockEntity blockEntity, CallbackInfo ci) {
        if (!level.isClientSide() && level.getGameTime() % 20L == 0L) {
            Musicxcst.LIBRARY.startJukeboxPlayback(level, pos, blockEntity.getTheItem());
        }
    }

    @Inject(method = "popOutTheItem", at = @At("HEAD"))
    private void musicxcst$stopCustomPlaybackOnPop(CallbackInfo ci) {
        Level level = getLevel();
        if (level != null && !level.isClientSide()) {
            Musicxcst.LIBRARY.stopJukeboxPlayback(level, getBlockPos());
        }
    }

    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void musicxcst$stopCustomPlaybackOnRemove(CallbackInfo ci) {
        Level level = getLevel();
        if (level != null && !level.isClientSide()) {
            Musicxcst.LIBRARY.stopJukeboxPlayback(level, getBlockPos());
        }
    }
}
