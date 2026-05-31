package de.coulees.B1progame.musicxcst.client.render;

import com.geckolib.renderer.GeoBlockRenderer;
import de.coulees.B1progame.musicxcst.block.CdWriterBlock;
import de.coulees.B1progame.musicxcst.block.entity.CdWriterBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

public final class CdWriterBlockRenderer extends GeoBlockRenderer<CdWriterBlockEntity, BlockEntityRenderState> {
    public CdWriterBlockRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new CdWriterGeoModel());
    }

    @Override
    protected Direction getBlockStateDirection(CdWriterBlockEntity blockEntity) {
        return blockEntity.getBlockState().getValue(CdWriterBlock.FACING);
    }
}
