package de.coulees.B1progame.musicxcst.client.render;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.block.entity.CdWriterBlockEntity;
import net.minecraft.resources.Identifier;

public final class CdWriterGeoModel extends GeoModel<CdWriterBlockEntity> {
    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "geo/cd_writer.geo.json");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "textures/block/cd_writer.png");
    private static final Identifier ANIMATION = Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "animations/cd_writer.animation.json");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(CdWriterBlockEntity animatable) {
        return ANIMATION;
    }
}
