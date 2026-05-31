package de.coulees.B1progame.musicxcst.client.render;

import com.geckolib.renderer.GeoBlockRenderer;
import de.coulees.B1progame.musicxcst.block.entity.CdWriterBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public final class CdWriterBlockRenderer extends GeoBlockRenderer<CdWriterBlockEntity, BlockEntityRenderState> {
    public CdWriterBlockRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new CdWriterGeoModel());
    }
}
