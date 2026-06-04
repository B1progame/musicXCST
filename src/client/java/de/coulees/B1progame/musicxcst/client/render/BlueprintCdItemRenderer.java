package de.coulees.B1progame.musicxcst.client.render;

import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.data.DiscData;
import de.coulees.B1progame.musicxcst.data.MusicStatus;
import de.coulees.B1progame.musicxcst.init.ModItems;
import de.coulees.B1progame.musicxcst.mixin.client.ItemStackLayerRenderStateAccessor;
import de.coulees.B1progame.musicxcst.mixin.client.ItemStackRenderStateAccessor;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlueprintCdItemRenderer {
    private static final int MAX_CACHE_SIZE = 128;
    // The overlay is baked from the base item model's own front/back face bounds.
    // This offset only nudges those copied faces to avoid depth fighting without detaching the design.
    private static final float Z_FIGHT_OFFSET = -0.0001F;
    private static final Identifier WHITE_PIXEL = Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "item/custom_disc_pixel");
    private static final Set<String> LOGGED_CONTEXTS = new HashSet<>();
    private static final Map<String, CachedDesign> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedDesign> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    private BlueprintCdItemRenderer() {
    }

    public static void appendDesignLayer(ItemStackRenderState renderState, ItemStack stack, ItemDisplayContext displayContext) {
        if (stack.getItem() != ModItems.BLUEPRINT_CD) {
            return;
        }

        DiscData data = DiscData.fromStack(stack);
        if (data == null) {
            return;
        }

        ItemStackRenderStateAccessor renderStateAccessor = (ItemStackRenderStateAccessor) renderState;
        int activeLayerCount = renderStateAccessor.musicxcst$getActiveLayerCount();
        ItemStackRenderState.LayerRenderState[] layers = renderStateAccessor.musicxcst$getLayers();
        if (activeLayerCount <= 0 || layers.length == 0) {
            Musicxcst.LOGGER.debug("Blueprint CD renderer found no base item layer for {}", displayContext);
            return;
        }

        ItemStackRenderState.LayerRenderState sourceLayer = layers[0];
        ItemStackLayerRenderStateAccessor source = (ItemStackLayerRenderStateAccessor) sourceLayer;
        FaceBounds frontBounds = FaceBounds.from(sourceLayer.prepareQuadList(), Direction.SOUTH);
        FaceBounds backBounds = FaceBounds.from(sourceLayer.prepareQuadList(), Direction.NORTH);
        if (frontBounds == null && backBounds == null) {
            Musicxcst.LOGGER.debug("Blueprint CD renderer found no base item front/back quads for {}", displayContext);
            return;
        }

        int[] pixels = pixelsForRender(data);
        String cacheKey = (data.status == null ? "" : data.status) + ":" + DiscData.encodeDesignId(pixels) + ":front=" + cacheKey(frontBounds) + ":back=" + cacheKey(backBounds);
        CachedDesign cached = CACHE.computeIfAbsent(cacheKey, ignored -> bake(pixels, frontBounds, backBounds));
        if (cached.quads().isEmpty()) {
            return;
        }

        logContextOnce(displayContext, data);
        ItemStackRenderState.LayerRenderState layer = renderState.newLayer();
        layer.setItemTransform(source.musicxcst$getItemTransform());
        layer.setLocalTransform(source.musicxcst$getLocalTransform());
        layer.setUsesBlockLight(source.musicxcst$getUsesBlockLight());
        layer.setFoilType(source.musicxcst$getFoilType());
        layer.setExtents(cached::extents);
        layer.prepareQuadList().addAll(cached.quads());
        IntList tintLayers = layer.tintLayers();
        for (int tint : cached.tints()) {
            tintLayers.add(tint);
        }
        renderState.appendModelIdentityElement(cacheKey);
    }

    private static CachedDesign bake(int[] pixels, FaceBounds frontBounds, FaceBounds backBounds) {
        Musicxcst.LOGGER.debug("Baking Blueprint CD item-layer design {} in front={} back={}", DiscData.designDebugSummary(pixels), cacheKey(frontBounds), cacheKey(backBounds));
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getAtlasManager()
                .get(Sheets.ITEMS_MAPPER.apply(WHITE_PIXEL));
        Material.Baked material = new Material.Baked(sprite, true);
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
                if (frontBounds != null) {
                    quads.add(pixelQuad(material, frontBounds, x, y, tintIndex, Direction.SOUTH));
                }
                if (backBounds != null) {
                    quads.add(pixelQuad(material, backBounds, x, y, tintIndex, Direction.NORTH));
                }
            }
        }

        int[] tintArray = tints.stream().mapToInt(Integer::intValue).toArray();
        return new CachedDesign(List.copyOf(quads), tintArray, extents(frontBounds, backBounds));
    }

    private static BakedQuad pixelQuad(Material.Baked material, FaceBounds bounds, int x, int y, int tintIndex, Direction direction) {
        TextureAtlasSprite sprite = material.sprite();
        float pixelWidth = bounds.width() / DiscData.DESIGN_SIZE;
        float pixelHeight = bounds.height() / DiscData.DESIGN_SIZE;
        float x0 = bounds.minX() + x * pixelWidth;
        float x1 = x0 + pixelWidth;
        float y1 = bounds.maxY() - y * pixelHeight;
        float y0 = y1 - pixelHeight;
        float z = direction == Direction.NORTH ? bounds.z() - Z_FIGHT_OFFSET : bounds.z() + Z_FIGHT_OFFSET;
        Vector3fc p0 = new Vector3f(x0, y0, z);
        Vector3fc p1 = new Vector3f(x1, y0, z);
        Vector3fc p2 = new Vector3f(x1, y1, z);
        Vector3fc p3 = new Vector3f(x0, y1, z);
        long uv0 = UVPair.pack(sprite.getU(0.0F), sprite.getV(16.0F));
        long uv1 = UVPair.pack(sprite.getU(16.0F), sprite.getV(16.0F));
        long uv2 = UVPair.pack(sprite.getU(16.0F), sprite.getV(0.0F));
        long uv3 = UVPair.pack(sprite.getU(0.0F), sprite.getV(0.0F));
        BakedQuad.MaterialInfo materialInfo = BakedQuad.MaterialInfo.of(material, sprite.transparency(), tintIndex, false, 0);
        if (direction == Direction.NORTH) {
            return new BakedQuad(p3, p2, p1, p0, uv3, uv2, uv1, uv0, direction, materialInfo);
        }
        return new BakedQuad(p0, p1, p2, p3, uv0, uv1, uv2, uv3, direction, materialInfo);
    }

    private static int[] pixelsForRender(DiscData data) {
        int[] pixels = DiscData.sanitizeDesign(data.designPixels);
        if (!MusicStatus.isInvalidLike(data.status)) {
            return pixels;
        }

        int[] invalid = new int[pixels.length];
        for (int index = 0; index < pixels.length; index++) {
            int pixel = pixels[index];
            if ((pixel >>> 24) == 0) {
                invalid[index] = 0;
                continue;
            }
            int red = Math.max(160, (pixel >> 16) & 0xFF);
            int green = (((pixel >> 8) & 0xFF) * 35) / 100;
            int blue = ((pixel & 0xFF) * 35) / 100;
            invalid[index] = 0xFF000000 | (red << 16) | (green << 8) | blue;
        }
        return invalid;
    }

    private static void logContextOnce(ItemDisplayContext displayContext, DiscData data) {
        String key = displayContext.name() + ":" + DiscData.encodeDesignId(data.designPixels);
        if (LOGGED_CONTEXTS.add(key)) {
            Musicxcst.LOGGER.debug("Blueprint CD item-layer renderer received {} design {}", displayContext, DiscData.designDebugSummary(data.designPixels));
        }
    }

    private static String cacheKey(FaceBounds bounds) {
        return bounds == null ? "none" : bounds.cacheKey();
    }

    private static Vector3fc[] extents(FaceBounds frontBounds, FaceBounds backBounds) {
        FaceBounds primary = frontBounds != null ? frontBounds : backBounds;
        FaceBounds secondary = backBounds != null ? backBounds : frontBounds;
        return new Vector3fc[]{
                new Vector3f(Math.min(primary.minX(), secondary.minX()), Math.min(primary.minY(), secondary.minY()), Math.min(primary.z(), secondary.z()) - Z_FIGHT_OFFSET),
                new Vector3f(Math.max(primary.maxX(), secondary.maxX()), Math.max(primary.maxY(), secondary.maxY()), Math.max(primary.z(), secondary.z()) + Z_FIGHT_OFFSET)
        };
    }

    private record FaceBounds(float minX, float minY, float z, float maxX, float maxY) {
        private static FaceBounds from(List<BakedQuad> quads, Direction direction) {
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            for (BakedQuad quad : quads) {
                if (quad.direction() != direction) {
                    continue;
                }
                for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                    Vector3fc position = quad.position(vertex);
                    minX = Math.min(minX, position.x());
                    minY = Math.min(minY, position.y());
                    minZ = Math.min(minZ, position.z());
                    maxX = Math.max(maxX, position.x());
                    maxY = Math.max(maxY, position.y());
                    maxZ = Math.max(maxZ, position.z());
                }
            }
            if (!Float.isFinite(minX) || maxX <= minX || maxY <= minY) {
                return null;
            }
            float z = direction == Direction.NORTH ? minZ : maxZ;
            return new FaceBounds(minX, minY, z, maxX, maxY);
        }

        private float width() {
            return maxX - minX;
        }

        private float height() {
            return maxY - minY;
        }

        private String cacheKey() {
            return minX + "," + minY + "," + z + "," + maxX + "," + maxY;
        }
    }

    private record CachedDesign(List<BakedQuad> quads, int[] tints, Vector3fc[] extents) {
    }
}
