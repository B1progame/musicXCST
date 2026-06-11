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
    private static final float Z_FIGHT_OFFSET = 0.0001F;
    private static final float OVERLAY_MIN_U = 0.0F;
    private static final float OVERLAY_MAX_U = 1.0F;
    private static final float OVERLAY_MIN_V = 0.0F;
    private static final float OVERLAY_MAX_V = 1.0F;
    // These profiles intentionally mimic the old 16x16 overlay feel.
    // If you want to retune the look yourself, change the values here.
    private static final PlacementProfile PROFILE_16 = new PlacementProfile(8.0F, 3.5F, 3.5F);
    private static final PlacementProfile PROFILE_32 = new PlacementProfile(8.001F, 3.5F, 3.5F);
    private static final PlacementProfile PROFILE_64 = new PlacementProfile(8.01F, 3.5F, 3.5F);
    private static final PlacementProfile PROFILE_128 = new PlacementProfile(8.1F, 3.5F, 3.5F);
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
        if (data == null || MusicStatus.isInvalidLike(data.status) || !DiscData.hasCustomDesign(data)) {
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
        List<BakedQuad> sourceQuads = sourceLayer.prepareQuadList();
        FaceBounds frontBounds = FaceBounds.from(sourceQuads, Direction.SOUTH);
        FaceBounds backBounds = FaceBounds.from(sourceQuads, Direction.NORTH);
        if (frontBounds == null && backBounds == null) {
            Musicxcst.LOGGER.debug("Blueprint CD renderer found no base item front/back quads for {}", displayContext);
            return;
        }
        FaceBounds overlayFrontBounds = frontBounds == null ? null : frontBounds.overlayFace();
        FaceBounds overlayBackBounds = backBounds == null ? null : backBounds.overlayFace();

        DiscData.DesignData renderDesign = renderDesign(data);
        String cacheKey = (data.status == null ? "" : data.status) + ":" + DiscData.encodeDesignId(renderDesign) + ":placement=" + placementKey(renderDesign) + ":front=" + cacheKey(overlayFrontBounds) + ":back=" + cacheKey(overlayBackBounds);
        CachedDesign cached = CACHE.computeIfAbsent(cacheKey, ignored -> bake(renderDesign, overlayFrontBounds, overlayBackBounds));
        if (cached.quads().isEmpty()) {
            sourceQuads.clear();
            return;
        }

        sourceQuads.clear();
        logContextOnce(displayContext, renderDesign);
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

    private static CachedDesign bake(DiscData.DesignData design, FaceBounds frontBounds, FaceBounds backBounds) {
        DiscData.DesignData sanitized = DiscData.sanitizeDesignData(design);
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getAtlasManager()
                .get(WHITE_PIXEL_SPRITE);
        Material.Baked material = new Material.Baked(sprite, false);
        logBakeDiagnostics(sanitized, sprite, material, frontBounds, backBounds);

        List<BakedQuad> quads = new ArrayList<>();
        List<Integer> tints = new ArrayList<>();

        for (int y = 0; y < sanitized.height(); y++) {
            for (int x = 0; x < sanitized.width(); x++) {
                int color = sanitized.pixels()[y * sanitized.width() + x];
                if ((color >>> 24) == 0) {
                    continue;
                }

                int tintIndex = tints.size();
                tints.add(color);
                if (frontBounds != null) {
                    quads.add(pixelQuad(material, frontBounds, sanitized, x, y, tintIndex, Direction.SOUTH));
                }
                if (backBounds != null) {
                    quads.add(pixelQuad(material, backBounds, sanitized, x, y, tintIndex, Direction.NORTH));
                }
            }
        }

        int[] tintArray = tints.stream().mapToInt(Integer::intValue).toArray();
        Musicxcst.LOGGER.debug("Blueprint CD item-layer baked quads={} tintEntries={} firstTints={}", quads.size(), tintArray.length, firstVisiblePixels(tintArray));
        return new CachedDesign(List.copyOf(quads), tintArray, extents(sanitized, frontBounds, backBounds), material);
    }

    private static BakedQuad pixelQuad(Material.Baked material, FaceBounds bounds, DiscData.DesignData design, int x, int y, int tintIndex, Direction direction) {
        TextureAtlasSprite sprite = material.sprite();
        PlacementProfile placement = placementFor(design);
        float x0;
        float x1;
        float y0;
        float y1;
        float pixelWidth = bounds.width() / design.width();
        float pixelHeight = bounds.height() / design.height();
        float centerX = bounds.minX() + (x + 0.5F + placement.offsetXPixels()) * pixelWidth;
        float centerY = bounds.maxY() - (y + 0.5F + placement.offsetYPixels()) * pixelHeight;
        float halfWidth = pixelWidth * placement.pixelScale() * 0.5F;
        float halfHeight = pixelHeight * placement.pixelScale() * 0.5F;
        x0 = centerX - halfWidth;
        x1 = centerX + halfWidth;
        y0 = centerY - halfHeight;
        y1 = centerY + halfHeight;
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

    private static DiscData.DesignData renderDesign(DiscData data) {
        DiscData.DesignData design = data.design();
        if (design.width() == DiscData.DESIGN_SIZE && design.height() == DiscData.DESIGN_SIZE) {
            return DiscData.sanitizeDesignData(design);
        }
        return DiscData.toItemRenderDesign(design);
    }

    private static void logContextOnce(ItemDisplayContext displayContext, DiscData.DesignData design) {
        String key = displayContext.name() + ":" + DiscData.encodeDesignId(design);
        if (LOGGED_CONTEXTS.add(key)) {
            Musicxcst.LOGGER.debug("Blueprint CD item-layer renderer received {} design {}", displayContext, DiscData.designDebugSummary(design));
        }
    }

    private static void logBakeDiagnostics(DiscData.DesignData sanitized, TextureAtlasSprite sprite, Material.Baked material, FaceBounds frontBounds, FaceBounds backBounds) {
        Identifier resolvedName = sprite.contents().name();
        boolean missing = MissingTextureAtlasSprite.getLocation().equals(resolvedName);
        boolean itemAtlas = TextureAtlas.LOCATION_ITEMS.equals(sprite.atlasLocation());
        Musicxcst.LOGGER.debug(
                "Baking Blueprint CD item-layer design {} in front={} back={} pixelScale={} requestedTexture={} requestedSprite={} resolvedSprite={} atlas={} itemAtlas={} size={}x{} missing={} transparency={} forceTranslucent={} firstPixels={}",
                DiscData.designDebugSummary(sanitized),
                cacheKey(frontBounds),
                cacheKey(backBounds),
                placementFor(sanitized).pixelScale(),
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
                firstVisiblePixels(sanitized.pixels())
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

    private static String placementKey(DiscData.DesignData design) {
        PlacementProfile placement = placementFor(design);
        return design.width() + "x" + design.height() + "," + placement.pixelScale() + "," + placement.offsetXPixels() + "," + placement.offsetYPixels();
    }

    private static Vector3fc[] extents(DiscData.DesignData design, FaceBounds frontBounds, FaceBounds backBounds) {
        FaceBounds primary = (frontBounds != null ? frontBounds : backBounds).expandedForOverlay(design);
        FaceBounds secondary = (backBounds != null ? backBounds : frontBounds).expandedForOverlay(design);
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

        private FaceBounds expandedForOverlay(DiscData.DesignData design) {
            PlacementProfile placement = placementFor(design);
            float pixelWidth = width() / design.width();
            float pixelHeight = height() / design.height();
            float halfWidth = pixelWidth * placement.pixelScale() * 0.5F;
            float halfHeight = pixelHeight * placement.pixelScale() * 0.5F;
            float left = minX + (0.5F + placement.offsetXPixels()) * pixelWidth - halfWidth;
            float right = minX + (design.width() - 0.5F + placement.offsetXPixels()) * pixelWidth + halfWidth;
            float top = maxY - (0.5F + placement.offsetYPixels()) * pixelHeight + halfHeight;
            float bottom = maxY - (design.height() - 0.5F + placement.offsetYPixels()) * pixelHeight - halfHeight;
            return new FaceBounds(left, bottom, z, right, top);
        }
    }

    private static PlacementProfile placementFor(DiscData.DesignData design) {
        return switch (design.width()) {
            case 16 -> PROFILE_16;
            case 32 -> PROFILE_32;
            case 64 -> PROFILE_64;
            case 128 -> PROFILE_128;
            default -> PROFILE_16;
        };
    }

    private record CachedDesign(List<BakedQuad> quads, int[] tints, Vector3fc[] extents, Material.Baked material) {
    }

    private record PlacementProfile(float pixelScale, float offsetXPixels, float offsetYPixels) {
    }
}
