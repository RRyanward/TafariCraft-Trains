package traincraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.block.track.AbstractLegacyBatchRailBlock;
import traincraft.block.track.LegacyStraightAssemblyRailBlock;
import traincraft.block.track.TrackMaterial;
import traincraft.block.track.TrackMaterialBlockEntity;
import traincraft.registry.TCBlocks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TC_R2G1_NORMAL_ENERGY_R2F3T_SHADING
 *
 * Fresh two-material renderer based on the visual treatment from r2f3t.
 * It does not remap textures or UVs. It only assigns Forge tint indices to
 * rail-shaped quads while preserving the exact accepted model geometry.
 *
 * NORMAL = neutral metallic shade.
 * ENERGY = strong cyan shade.
 *
 * Legacy Steel/Copper/Snow material data falls back to NORMAL shading.
 */
@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class TrackMaterialModelEvents {
    private static final ModelProperty<TrackMaterial> MATERIAL = new ModelProperty<>();

    private static final int TINT_NORMAL = 4200;
    private static final int TINT_ENERGY = 4201;

    private TrackMaterialModelEvents() {
    }

    @SubscribeEvent
    public static void onModifyBaking(ModelEvent.ModifyBakingResult event) {
        int wrapped = 0;

        for (Map.Entry<ResourceLocation, BakedModel> entry : event.getModels().entrySet()) {
            ResourceLocation key = entry.getKey();
            if (!(key instanceof ModelResourceLocation)) {
                continue;
            }
            if (!Traincraft.MOD_ID.equals(key.getNamespace())) {
                continue;
            }

            ResourceLocation blockId =
                    new ResourceLocation(key.getNamespace(), key.getPath());
            Block block = ForgeRegistries.BLOCKS.getValue(blockId);

            if (!isSupportedTrackBlock(block)) {
                continue;
            }

            if (!(entry.getValue() instanceof ShadedTrackModel)) {
                entry.setValue(new ShadedTrackModel(entry.getValue()));
                wrapped++;
            }
        }

        int energyItemModels = 0;

        // r2g3f: use each Normal item model as the geometry/lettering source for
        // the matching Energy item. This gives Energy tracks identical 3D shape
        // and family lettering while the wrapper colors only the rail surfaces.
        for (Map.Entry<ResourceLocation, BakedModel> entry :
                event.getModels().entrySet()) {

            ResourceLocation key = entry.getKey();

            if (!(key instanceof ModelResourceLocation modelKey)
                    || !Traincraft.MOD_ID.equals(key.getNamespace())
                    || !"inventory".equals(modelKey.getVariant())) {
                continue;
            }

            String path = key.getPath();

            if (!path.startsWith("track_") || !path.endsWith("_energy")) {
                continue;
            }

            String normalPath =
                    path.substring(0, path.length() - "_energy".length());

            ModelResourceLocation normalKey =
                    new ModelResourceLocation(
                            new ResourceLocation(Traincraft.MOD_ID, normalPath),
                            "inventory"
                    );

            BakedModel normalModel = event.getModels().get(normalKey);

            if (normalModel != null) {
                entry.setValue(new EnergyItemModel(normalModel));
                energyItemModels++;
            }
        }

        Traincraft.LOGGER.info(
                "[TC_R2G3E] mapped {} Energy inventory models to Normal 3D geometry/lettering",
                energyItemModels
        );
        Traincraft.LOGGER.info(
                "[TC_R2G1] wrapped {} Normal/Energy track baked states",
                wrapped
        );
    }

    // TC_R2G3E_ENERGY_ITEM_PARITY
    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        ArrayList<net.minecraft.world.item.Item> items = new ArrayList<>();

        for (net.minecraft.world.item.Item item : ForgeRegistries.ITEMS.getValues()) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);

            if (id != null
                    && Traincraft.MOD_ID.equals(id.getNamespace())
                    && id.getPath().startsWith("track_")
                    && id.getPath().endsWith("_energy")) {
                items.add(item);
            }
        }

        event.register(
                (stack, tintIndex) ->
                        tintIndex == TINT_ENERGY ? 0x00C8FF : 0xFFFFFF,
                items.toArray(new net.minecraft.world.item.Item[0])
        );

        Traincraft.LOGGER.info(
                "[TC_R2G3E] registered electric item tint for {} Energy track items",
                items.size()
        );
    }
    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        ArrayList<Block> blocks = new ArrayList<>();

        for (Block block : ForgeRegistries.BLOCKS.getValues()) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);

            if (id == null || !Traincraft.MOD_ID.equals(id.getNamespace())) {
                continue;
            }

            if (isSupportedTrackBlock(block)) {
                blocks.add(block);
            }
        }

        event.register(
                (state, level, pos, tintIndex) -> switch (tintIndex) {
                    // Neutral steel/charcoal-like treatment for classic Normal rail.
                    case TINT_NORMAL -> 0xAEB8C2;

                    // Exact strong Energy cyan from the liked r2f3t pass.
                    case TINT_ENERGY -> 0x00C8FF;

                    default -> 0xFFFFFF;
                },
                blocks.toArray(new Block[0])
        );

        Traincraft.LOGGER.info(
                "[TC_R2G1] registered Normal/Energy shading for {} track blocks",
                blocks.size()
        );
    }

    private static boolean isSupportedTrackBlock(@Nullable Block block) {
        if (block == null) {
            return false;
        }

        return block instanceof AbstractLegacyBatchRailBlock
                || block instanceof LegacyStraightAssemblyRailBlock
                || block instanceof traincraft.block.track.LegacyMediumCurveRailBlock
                || block == TCBlocks.TRACK_NORMAL.get();
    }

    private static final class EnergyItemModel implements BakedModel {
        private final BakedModel delegate;

        private EnergyItemModel(BakedModel delegate) {
            this.delegate = delegate;
        }

        private List<BakedQuad> tintRailQuads(List<BakedQuad> source) {
            if (source.isEmpty()) {
                return source;
            }

            ArrayList<BakedQuad> out = null;

            for (int i = 0; i < source.size(); i++) {
                BakedQuad quad = source.get(i);

                if (!ShadedTrackModel.isCanonicalNormalTrackSprite(quad.getSprite())
                        || !ShadedTrackModel.isR2f3tRailSurfaceQuad(quad)) {
                    if (out != null) {
                        out.add(quad);
                    }
                    continue;
                }

                if (out == null) {
                    out = new ArrayList<>(source.size());
                    for (int j = 0; j < i; j++) {
                        out.add(source.get(j));
                    }
                }

                out.add(new BakedQuad(
                        quad.getVertices(),
                        TINT_ENERGY,
                        quad.getDirection(),
                        quad.getSprite(),
                        quad.isShade()
                ));
            }

            return out == null ? source : out;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state,
                                        @Nullable Direction side,
                                        RandomSource random) {
            return tintRailQuads(delegate.getQuads(state, side, random));
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state,
                                        @Nullable Direction side,
                                        RandomSource random,
                                        ModelData modelData,
                                        @Nullable RenderType renderType) {
            return tintRailQuads(
                    delegate.getQuads(state, side, random, modelData, renderType)
            );
        }

        @Override
        public List<BakedModel> getRenderPasses(ItemStack itemStack,
                                                boolean fabulous) {
            List<BakedModel> passes = delegate.getRenderPasses(itemStack, fabulous);

            if (passes.size() == 1 && passes.get(0) == delegate) {
                return List.of(this);
            }

            ArrayList<BakedModel> wrapped = new ArrayList<>(passes.size());
            for (BakedModel pass : passes) {
                wrapped.add(pass == delegate ? this : new EnergyItemModel(pass));
            }
            return wrapped;
        }

        @Override
        public List<RenderType> getRenderTypes(ItemStack itemStack,
                                               boolean fabulous) {
            return delegate.getRenderTypes(itemStack, fabulous);
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context,
                                         PoseStack poseStack,
                                         boolean applyLeftHandTransform) {
            delegate.applyTransform(context, poseStack, applyLeftHandTransform);
            return this;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return delegate.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return delegate.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return delegate.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return delegate.isCustomRenderer();
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return delegate.getParticleIcon();
        }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            return delegate.getParticleIcon(data);
        }

        @Override
        public ItemTransforms getTransforms() {
            return delegate.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return delegate.getOverrides();
        }
    }
    private static final class ShadedTrackModel implements BakedModel {
        private final BakedModel delegate;

        private ShadedTrackModel(BakedModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level,
                                      BlockPos pos,
                                      BlockState state,
                                      ModelData modelData) {
            ModelData base =
                    delegate.getModelData(level, pos, state, modelData);

            TrackMaterial material = resolveMaterial(level, pos, state);

            return base.derive()
                    .with(MATERIAL, material)
                    .build();
        }

        // TC_BATCH8_R3ZZW3A_RENDER_REGION_SAFE_MATERIAL_LOOKUP
        //
        // Chunk rebuilds pass a RenderChunkRegion to getModelData(). Large
        // assemblies such as 29x/32x can have their root BlockEntity several
        // chunks away from the segment currently being baked. RenderChunkRegion
        // is only a small snapshot and its getBlockEntity(root) can throw when
        // that root lies outside the snapshot.
        //
        // First use the supplied render snapshot when it can answer safely.
        // If the root is outside that snapshot, fall back to the real client
        // level for this read-only material lookup. If neither lookup is
        // available, NORMAL is a safe visual fallback; placement/pathing/data
        // are not affected.
        // TC_BATCH8_R3ZZW3A_RENDER_REGION_SAFE_MATERIAL_LOOKUP
        // TC_BATCH8_R4A3F_BASIC_ENERGY_PARITY
        private static TrackMaterial resolveMaterial(BlockAndTintGetter level,
                                                     BlockPos pos,
                                                     BlockState state) {
            BlockPos root = null;

            if (state.getBlock() instanceof AbstractLegacyBatchRailBlock batch) {
                root = batch.getAssemblyRoot(pos, state);
            }
            else if (state.getBlock() instanceof LegacyStraightAssemblyRailBlock) {
                Direction facing =
                        state.getValue(LegacyStraightAssemblyRailBlock.ASSEMBLY_FACING);
                int part =
                        state.getValue(LegacyStraightAssemblyRailBlock.PART);
                root = pos.relative(facing.getOpposite(), part);
            }
            else if (state.getBlock()
                    instanceof traincraft.block.track.LegacyMediumCurveRailBlock) {
                int part =
                        state.getValue(
                                traincraft.block.track.LegacyMediumCurveRailBlock.PART);
                if (part >= 0
                        && part < traincraft.block.track.LegacyMediumCurveRailBlock.PART_COUNT) {
                    Direction facing =
                            state.getValue(
                                    traincraft.block.track.LegacyMediumCurveRailBlock.ASSEMBLY_FACING);
                    BlockPos offset =
                            traincraft.block.track.LegacyMediumCurveRailBlock.offsetForPart(
                                    BlockPos.ZERO,
                                    facing,
                                    part);
                    root = pos.subtract(offset);
                }
            }
            else if (state.getBlock()
                    == traincraft.registry.TCBlocks.TRACK_NORMAL.get()) {
                root = pos;
            }

            if (root == null) {
                return TrackMaterial.NORMAL;
            }

            TrackMaterial material =
                    materialAtRootSafely(
                            level,
                            root);

            return material == null
                    ? TrackMaterial.NORMAL
                    : material;
        }


        private static TrackMaterial materialAtRootSafely(
                BlockAndTintGetter renderView,
                BlockPos root) {
            BlockEntity blockEntity = null;

            try {
                blockEntity =
                        renderView.getBlockEntity(
                                root);
            }
            catch (IndexOutOfBoundsException ignored) {
                // Expected when a large assembly root is outside the
                // RenderChunkRegion snapshot currently being rebuilt.
            }

            if (blockEntity instanceof TrackMaterialBlockEntity materialData) {
                return materialData.getMaterial();
            }

            net.minecraft.client.multiplayer.ClientLevel clientLevel =
                    net.minecraft.client.Minecraft.getInstance().level;

            if (clientLevel != null) {
                try {
                    blockEntity =
                            clientLevel.getBlockEntity(
                                    root);

                    if (blockEntity instanceof TrackMaterialBlockEntity materialData) {
                        return materialData.getMaterial();
                    }
                }
                catch (RuntimeException ignored) {
                    // Rendering must fail soft. Material shading is cosmetic;
                    // NORMAL below is safer than crashing a chunk rebuild.
                }
            }

            return null;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state,
                                        @Nullable Direction side,
                                        RandomSource random,
                                        ModelData modelData,
                                        @Nullable RenderType renderType) {
            List<BakedQuad> source =
                    delegate.getQuads(state, side, random, modelData, renderType);

            if (source.isEmpty()) {
                return source;
            }

            TrackMaterial material = modelData.get(MATERIAL);

            // Only Energy is special. Any absent/legacy material renders as Normal.
            int tintIndex =
                    material == TrackMaterial.ENERGY ? TINT_ENERGY : TINT_NORMAL;

            ArrayList<BakedQuad> out = null;

            int canonical = 0;
            int shaded = 0;

            for (int i = 0; i < source.size(); i++) {
                BakedQuad quad = source.get(i);

                boolean canonicalQuad =
                        isCanonicalNormalTrackSprite(quad.getSprite());

                if (canonicalQuad) {
                    canonical++;
                }

                if (!canonicalQuad || !isR2f3tRailSurfaceQuad(quad)) {
                    if (out != null) {
                        out.add(quad);
                    }
                    continue;
                }

                if (out == null) {
                    out = new ArrayList<>(source.size());
                    for (int j = 0; j < i; j++) {
                        out.add(source.get(j));
                    }
                }

                shaded++;
                out.add(new BakedQuad(
                        quad.getVertices(),
                        tintIndex,
                        quad.getDirection(),
                        quad.getSprite(),
                        quad.isShade()
                ));
            }

            return out == null ? source : out;
        }

        /**
         * Exact broad geometry classifier from the visual r2f3t pass.
         *
         * It is intentionally broader than the later strict experiments.
         * That broad treatment is what produced the stronger shaded rail look.
         */
        private static boolean isR2f3tRailSurfaceQuad(BakedQuad quad) {
            int[] data = quad.getVertices();
            int stride = data.length / 4;

            if (stride < 6) {
                return false;
            }

            float minX = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;

            for (int v = 0; v < 4; v++) {
                int base = v * stride;
                float x = Float.intBitsToFloat(data[base]);
                float y = Float.intBitsToFloat(data[base + 1]);
                float z = Float.intBitsToFloat(data[base + 2]);

                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }

            float spanX = maxX - minX;
            float spanY = maxY - minY;
            float spanZ = maxZ - minZ;

            float horizontalMajor = Math.max(spanX, spanZ);
            float horizontalMinor = Math.min(spanX, spanZ);
            float centerY = (minY + maxY) * 0.5F;

            return horizontalMinor <= 0.11F
                    && horizontalMajor >= 0.08F
                    && centerY >= -0.02F
                    && spanY <= 0.12F;
        }

        private static boolean isCanonicalNormalTrackSprite(TextureAtlasSprite sprite) {
            ResourceLocation id = sprite.contents().name();

            if (!Traincraft.MOD_ID.equals(id.getNamespace())) {
                return false;
            }

            String path = id.getPath();

            return path.equals("block/legacy_track_normal")
                    || path.equals("models/track_normal")
                    || path.equals("block/track_normal")
                    || path.equals("block/tracks/normal")
                    || path.endsWith("/legacy_track_normal")
                    || path.endsWith("/track_normal");
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state,
                                        @Nullable Direction side,
                                        RandomSource random) {
            return delegate.getQuads(state, side, random);
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state,
                                                 RandomSource random,
                                                 ModelData data) {
            return delegate.getRenderTypes(state, random, data);
        }

        @Override
        public List<BakedModel> getRenderPasses(ItemStack itemStack,
                                                boolean fabulous) {
            return delegate.getRenderPasses(itemStack, fabulous);
        }

        @Override
        public List<RenderType> getRenderTypes(ItemStack itemStack,
                                               boolean fabulous) {
            return delegate.getRenderTypes(itemStack, fabulous);
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context,
                                         PoseStack poseStack,
                                         boolean applyLeftHandTransform) {
            delegate.applyTransform(context, poseStack, applyLeftHandTransform);
            return this;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return delegate.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return delegate.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return delegate.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return delegate.isCustomRenderer();
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return delegate.getParticleIcon();
        }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            return delegate.getParticleIcon(data);
        }

        @Override
        public ItemTransforms getTransforms() {
            return delegate.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return delegate.getOverrides();
        }
    }
}
