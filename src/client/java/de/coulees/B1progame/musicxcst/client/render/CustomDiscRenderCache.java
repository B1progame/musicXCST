package de.coulees.B1progame.musicxcst.client.render;

import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.data.DiscData;
import de.coulees.B1progame.musicxcst.init.ModItems;
import de.coulees.B1progame.musicxcst.mixin.client.ItemStackLayerRenderStateAccessor;
import de.coulees.B1progame.musicxcst.mixin.client.ItemStackRenderStateAccessor;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CustomDiscRenderCache {
    private static final int MAX_CACHE_SIZE = 128;
    private static final float FRONT_Z = 0.53125F;
    private static final Identifier WHITE_PIXEL = Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "custom_disc_pixel");
    private static final Map<String, CachedDesign> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedDesign> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    private CustomDiscRenderCache() {
    }

    public static void appendCustomDiscLayer(ItemStackRenderState renderState, ItemStack stack) {
        if (stack.getItem() != ModItems.BLUEPRINT_CD) {
            return;
        }

        DiscData data = DiscData.fromStack(stack);
        if (data == null) {
            return;
        }

        String designId = DiscData.encodeDesignId(data.designPixels);
        CachedDesign cached = CACHE.computeIfAbsent(designId, ignored -> bake(data.designPixels));
        if (cached.quads().isEmpty()) {
            return;
        }

        ItemStackRenderStateAccessor renderStateAccessor = (ItemStackRenderStateAccessor) renderState;
        int activeLayerCount = renderStateAccessor.musicxcst$getActiveLayerCount();
        ItemStackRenderState.LayerRenderState[] layers = renderStateAccessor.musicxcst$getLayers();
        if (activeLayerCount <= 0 || layers.length == 0) {
            return;
        }

        ItemStackRenderState.LayerRenderState sourceLayer = layers[0];
        ItemStackLayerRenderStateAccessor sourceAccessor = (ItemStackLayerRenderStateAccessor) sourceLayer;
        ItemTransform itemTransform = sourceAccessor.musicxcst$getItemTransform();
        Matrix4fc localTransform = sourceAccessor.musicxcst$getLocalTransform();

        ItemStackRenderState.LayerRenderState layer = renderState.newLayer();
        layer.setItemTransform(itemTransform);
        layer.setLocalTransform(localTransform);
        layer.setUsesBlockLight(false);
        layer.prepareQuadList().addAll(cached.quads());
        IntList tints = layer.tintLayers();
        for (int tint : cached.tints()) {
            tints.add(tint);
        }
    }

    private static CachedDesign bake(int[] pixels) {
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getAtlasManager()
                .get(Sheets.ITEMS_MAPPER.apply(WHITE_PIXEL));
        int[] sanitized = DiscData.sanitizeDesign(pixels);
        List<BakedQuad> quads = new ArrayList<>();
        List<Integer> tints = new ArrayList<>();

        for (int y = 0; y < DiscData.DESIGN_SIZE; y++) {
            for (int x = 0; x < DiscData.DESIGN_SIZE; x++) {
                int color = sanitized[y * DiscData.DESIGN_SIZE + x];
                if ((color >>> 24) == 0) {
                    continue;
                }

                int tintIndex = tints.size();
                tints.add(color);
                quads.add(pixelQuad(sprite, x, y, tintIndex));
            }
        }

        return new CachedDesign(List.copyOf(quads), tints.stream().mapToInt(Integer::intValue).toArray());
    }

    private static BakedQuad pixelQuad(TextureAtlasSprite sprite, int x, int y, int tintIndex) {
        float x0 = x / 16.0F;
        float x1 = (x + 1) / 16.0F;
        float y0 = 1.0F - (y + 1) / 16.0F;
        float y1 = 1.0F - y / 16.0F;
        Vector3fc p0 = new Vector3f(x0, y0, FRONT_Z);
        Vector3fc p1 = new Vector3f(x1, y0, FRONT_Z);
        Vector3fc p2 = new Vector3f(x1, y1, FRONT_Z);
        Vector3fc p3 = new Vector3f(x0, y1, FRONT_Z);
        long uv0 = UVPair.pack(sprite.getU(0.0F), sprite.getV(16.0F));
        long uv1 = UVPair.pack(sprite.getU(16.0F), sprite.getV(16.0F));
        long uv2 = UVPair.pack(sprite.getU(16.0F), sprite.getV(0.0F));
        long uv3 = UVPair.pack(sprite.getU(0.0F), sprite.getV(0.0F));
        BakedQuad.MaterialInfo material = new BakedQuad.MaterialInfo(sprite, ChunkSectionLayer.TRANSLUCENT, Sheets.translucentItemSheet(), tintIndex, false, 0);
        return new BakedQuad(p0, p1, p2, p3, uv0, uv1, uv2, uv3, Direction.SOUTH, material);
    }

    private record CachedDesign(List<BakedQuad> quads, int[] tints) {
    }
}
