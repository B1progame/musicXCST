package de.coulees.B1progame.musicxcst.mixin.client;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface ItemStackLayerRenderStateAccessor {
    @Accessor("itemTransform")
    ItemTransform musicxcst$getItemTransform();

    @Accessor("localTransform")
    Matrix4f musicxcst$getLocalTransform();
}
