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
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.SpriteId;
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
    private static final float Z_FIGHT_OFFSET = 0.0001F;
    // Normalized placement of the 16x16 editor design on the baked item face.
    // The item model already applies the hand/inventory/ground transforms; these
    // values only choose where the overlay lives inside that local item face.
    private static final float OVERLAY_MIN_U = 0.0F;
    private static final float OVERLAY_MAX_U = 1.0F;
    private static final float OVERLAY_MIN_V = 0.0F;
    private static final float OVERLAY_MAX_V = 1.0F;
    // Makes isolated editor pixels readable at normal item size. 1.0 means each
    // design pixel is exactly one 16x16 grid cell; higher values enlarge each
    // colored cell around its own center while keeping it on the item face.
    private static final float PIXEL_SIZE_SCALE = 8.0F;
    // Offset in editor-grid pixels. Negative X moves left; positive Y moves down.
    private static final float OVERLAY_OFFSET_X_PIXELS = 3.5F;
    private static final float OVERLAY_OFFSET_Y_PIXELS = 3.5F;
    private static final Identifier WHITE_PIXEL = Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "custom_disc_pixel");
    private static final SpriteId WHITE_PIXEL_SPRITE = Sheets.ITEMS_MAPPER.apply(WHITE_PIXEL);
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
        logBaseBoundsOnce(displayContext, frontBounds, backBounds);
        FaceBounds overlayFrontBounds = frontBounds == null ? null : frontBounds.overlayFace();
        FaceBounds overlayBackBounds = backBounds == null ? null : backBounds.overlayFace();

        int[] pixels = pixelsForRender(data);
        String cacheKey = (data.status == null ? "" : data.status) + ":" + DiscData.encodeDesignId(pixels) + ":placement=" + placementKey() + ":front=" + cacheKey(overlayFrontBounds) + ":back=" + cacheKey(overlayBackBounds);
        CachedDesign cached = CACHE.computeIfAbsent(cacheKey, ignored -> bake(pixels, overlayFrontBounds, overlayBackBounds));
        if (cached.quads().isEmpty()) {
            return;
        }

        logContextOnce(displayContext, data);
        ItemStackRenderState.LayerRenderState layer = renderState.newLayer();
        layer.setItemTransform(source.musicxcst$getItemTransform());
        layer.setLocalTransform(source.musicxcst$getLocalTransform());
        layer.setUsesBlockLight(source.musicxcst$getUsesBlockLight());
        layer.setFoilType(source.musicxcst$getFoilType());
        layer.setParticleMaterial(cached.material());
        layer.setExtents(cached::extents);
        layer.prepareQuadList().addAll(cached.quads());
        IntList tintLayers = layer.tintLayers();
        for (int tint : cached.tints()) {
            tintLayers.add(tint);
        }
        renderState.appendModelIdentityElement(cacheKey);
    }

    private static CachedDesign bake(int[] pixels, FaceBounds frontBounds, FaceBounds backBounds) {
        int[] sanitized = DiscData.sanitizeDesign(pixels);
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getAtlasManager()
                .get(WHITE_PIXEL_SPRITE);
        Material.Baked material = new Material.Baked(sprite, false);
        logBakeDiagnostics(sanitized, sprite, material, frontBounds, backBounds);

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
        Musicxcst.LOGGER.debug("Blueprint CD item-layer baked quads={} tintEntries={} firstTints={}", quads.size(), tintArray.length, firstVisiblePixels(tintArray));
        return new CachedDesign(List.copyOf(quads), tintArray, extents(frontBounds, backBounds), material);
    }

    private static BakedQuad pixelQuad(Material.Baked material, FaceBounds bounds, int x, int y, int tintIndex, Direction direction) {
        TextureAtlasSprite sprite = material.sprite();
        float pixelWidth = bounds.width() / DiscData.DESIGN_SIZE;
        float pixelHeight = bounds.height() / DiscData.DESIGN_SIZE;
        float centerX = bounds.minX() + (x + 0.5F + OVERLAY_OFFSET_X_PIXELS) * pixelWidth;
        float centerY = bounds.maxY() - (y + 0.5F + OVERLAY_OFFSET_Y_PIXELS) * pixelHeight;
        float halfWidth = pixelWidth * PIXEL_SIZE_SCALE * 0.5F;
        float halfHeight = pixelHeight * PIXEL_SIZE_SCALE * 0.5F;
        float x0 = centerX - halfWidth;
        float x1 = centerX + halfWidth;
        float y0 = centerY - halfHeight;
        float y1 = centerY + halfHeight;
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

    private static void logBaseBoundsOnce(ItemDisplayContext displayContext, FaceBounds frontBounds, FaceBounds backBounds) {
        String key = "base-bounds:" + displayContext.name();
        if (LOGGED_CONTEXTS.add(key)) {
            Musicxcst.LOGGER.info("Blueprint CD base bounds {} front={} frontOverlay={} back={} backOverlay={}",
                    displayContext,
                    cacheKey(frontBounds),
                    cacheKey(frontBounds == null ? null : frontBounds.overlayFace()),
                    cacheKey(backBounds),
                    cacheKey(backBounds == null ? null : backBounds.overlayFace()));
        }
    }

    private static void logBakeDiagnostics(int[] sanitized, TextureAtlasSprite sprite, Material.Baked material, FaceBounds frontBounds, FaceBounds backBounds) {
        Identifier resolvedName = sprite.contents().name();
        boolean missing = MissingTextureAtlasSprite.getLocation().equals(resolvedName);
        boolean itemAtlas = TextureAtlas.LOCATION_ITEMS.equals(sprite.atlasLocation());
        Musicxcst.LOGGER.debug(
                "Baking Blueprint CD item-layer design {} in front={} back={} pixelScale={} requestedTexture={} requestedSprite={} resolvedSprite={} atlas={} itemAtlas={} size={}x{} missing={} transparency={} forceTranslucent={} firstPixels={}",
                DiscData.designDebugSummary(sanitized),
                cacheKey(frontBounds),
                cacheKey(backBounds),
                PIXEL_SIZE_SCALE,
                WHITE_PIXEL,
                WHITE_PIXEL_SPRITE,
                resolvedName,
                sprite.atlasLocation(),
                itemAtlas,
                sprite.contents().width(),
                sprite.contents().height(),
                missing,
                sprite.transparency(),
                material.forceTranslucent(),
                firstVisiblePixels(sanitized)
        );
    }

    private static String firstVisiblePixels(int[] pixels) {
        StringBuilder builder = new StringBuilder("[");
        int appended = 0;
        for (int index = 0; index < pixels.length && appended < 8; index++) {
            int pixel = pixels[index];
            if ((pixel >>> 24) == 0) {
                continue;
            }
            if (appended > 0) {
                builder.append(", ");
            }
            builder.append(index)
                    .append('=')
                    .append(String.format("#%08X", pixel));
            appended++;
        }
        if (appended == 0) {
            builder.append("none");
        }
        return builder.append(']').toString();
    }

    private static String cacheKey(FaceBounds bounds) {
        return bounds == null ? "none" : bounds.cacheKey();
    }

    private static String placementKey() {
        return PIXEL_SIZE_SCALE + "," + OVERLAY_OFFSET_X_PIXELS + "," + OVERLAY_OFFSET_Y_PIXELS;
    }

    private static Vector3fc[] extents(FaceBounds frontBounds, FaceBounds backBounds) {
        FaceBounds primary = (frontBounds != null ? frontBounds : backBounds).expandedForOverlay();
        FaceBounds secondary = (backBounds != null ? backBounds : frontBounds).expandedForOverlay();
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

        private FaceBounds overlayFace() {
            float width = width();
            float height = height();
            return new FaceBounds(
                    minX + width * OVERLAY_MIN_U,
                    maxY - height * OVERLAY_MAX_V,
                    z,
                    minX + width * OVERLAY_MAX_U,
                    maxY - height * OVERLAY_MIN_V
            );
        }

        private FaceBounds expandedForOverlay() {
            float pixelWidth = width() / DiscData.DESIGN_SIZE;
            float pixelHeight = height() / DiscData.DESIGN_SIZE;
            float halfWidth = pixelWidth * PIXEL_SIZE_SCALE * 0.5F;
            float halfHeight = pixelHeight * PIXEL_SIZE_SCALE * 0.5F;
            float left = minX + (0.5F + OVERLAY_OFFSET_X_PIXELS) * pixelWidth - halfWidth;
            float right = minX + (DiscData.DESIGN_SIZE - 0.5F + OVERLAY_OFFSET_X_PIXELS) * pixelWidth + halfWidth;
            float top = maxY - (0.5F + OVERLAY_OFFSET_Y_PIXELS) * pixelHeight + halfHeight;
            float bottom = maxY - (DiscData.DESIGN_SIZE - 0.5F + OVERLAY_OFFSET_Y_PIXELS) * pixelHeight - halfHeight;
            return new FaceBounds(left, bottom, z, right, top);
        }
    }

    private record CachedDesign(List<BakedQuad> quads, int[] tints, Vector3fc[] extents, Material.Baked material) {
    }
}
