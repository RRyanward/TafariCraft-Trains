package traincraft.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DEV50b2r: controlled, CLIENT-ONLY rendering isolation of the affected Normal
 * Straight / Medium Parallel Curve / Parallel Switch families.  Does not change
 * placement, saved track blocks, model resources, train movement or pathing.
 * Mode 0 is 100% delegated to the already installed renderer.
 * F8 cycles modes; each mode forces a visual chunk rebuild for A/B screenshots.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MediumParallelRenderIsolation {
    private static volatile int mode = 0;
    private static final int MODES = 5;
    private static final String[] LABELS = {
            "ORIGINAL (unchanged)",
            "NO_TINT (rail tint 4200/4201 disabled)",
            "HIDE_CROSS_CELL_FACES (holes EXPECTED)",
            "HIDE_PARALLEL_CURVES (holes EXPECTED)",
            "HIDE_NORMAL_STRAIGHTS (holes EXPECTED)"
    };
    private MediumParallelRenderIsolation() { }

    static void cycle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) return;
        mode = (mode + 1) % MODES;
        Traincraft.LOGGER.warn("[TC-DEV50B2R] MODE={} {} -- CLIENT DISPLAY TEST ONLY; NO WORLD EDITS",
                mode, LABELS[mode]);
        mc.gui.getChat().addMessage(Component.literal(
                "[TC render test] Mode " + mode + "/4: " + LABELS[mode]
                + " (F8 cycles; 0 restores original)"));
        // Force cached chunk meshes to be rebuilt with the selected mode.
        mc.levelRenderer.allChanged();
    }

    private enum Kind { STRAIGHT, CURVE, SWITCH, NONE }

    private static Kind kind(ResourceLocation key) {
        if (!(key instanceof ModelResourceLocation model)
                || !Traincraft.MOD_ID.equals(key.getNamespace())
                || "inventory".equals(model.getVariant())) return Kind.NONE;
        String path = key.getPath();
        if (path.equals("track_normal")) return Kind.STRAIGHT;
        if (path.equals("track_parallel_curve_medium_left_segment")
                || path.equals("track_parallel_curve_medium_right_segment")) return Kind.CURVE;
        if (path.equals("track_switch_parallel_left_segment")
                || path.equals("track_switch_parallel_right_segment")) return Kind.SWITCH;
        return Kind.NONE;
    }

    // Runs AFTER the installed TrackMaterialModelEvents wrapping (lowest MOD priority).
    // No existing source file is edited or unwrapped.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void attach(ModelEvent.ModifyBakingResult event) {
        int straight = 0, curve = 0, sw = 0;
        for (Map.Entry<ResourceLocation, BakedModel> e : event.getModels().entrySet()) {
            Kind kind = kind(e.getKey());
            if (kind == Kind.NONE || e.getValue() instanceof IsolationModel) continue;
            e.setValue(new IsolationModel(e.getValue(), kind));
            switch (kind) {
                case STRAIGHT -> straight++;
                case CURVE -> curve++;
                case SWITCH -> sw++;
                default -> { }
            }
        }

        // DEV58B9R4R1: obsolete rail F-key developer hint retired; renderer stays at default mode zero.
    }

    private static boolean crossesCell(BakedQuad quad) {
        int[] v = quad.getVertices();
        int stride = v.length / 4;
        if (stride < 3) return false;
        for (int i=0; i<4; ++i) {
            int k = i * stride;
            float x=Float.intBitsToFloat(v[k]);
            float y=Float.intBitsToFloat(v[k+1]);
            float z=Float.intBitsToFloat(v[k+2]);
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) continue;
            // Only lateral/XZ limits matter. Many old track meshes extend across
            // cells by design: this is an ISOLATION TEST, not a real fix.
            if (x < -0.002f || x > 1.002f || z < -0.002f || z > 1.002f) return true;
        }
        return false;
    }

    private static final class IsolationModel implements BakedModel {
        private final BakedModel delegate;
        private final Kind kind;
        IsolationModel(BakedModel delegate, Kind kind) {
            this.delegate = delegate;
            this.kind = kind;
        }
        private List<BakedQuad> apply(List<BakedQuad> source) {
            int current = mode;
            if (current == 0 || source.isEmpty()) return source;
            if ((current == 3 && kind == Kind.CURVE)
                    || (current == 4 && kind == Kind.STRAIGHT)) return List.of();
            if (current == 1) {
                ArrayList<BakedQuad> changed = null;
                for (int i=0; i<source.size(); ++i) {
                    BakedQuad quad = source.get(i);
                    if (quad.getTintIndex() != 4200 && quad.getTintIndex() != 4201) {
                        if (changed != null) changed.add(quad);
                        continue;
                    }
                    if (changed == null) {
                        changed = new ArrayList<>(source.size());
                        changed.addAll(source.subList(0, i));
                    }
                    changed.add(new BakedQuad(quad.getVertices(), -1,
                            quad.getDirection(), quad.getSprite(), quad.isShade()));
                }
                return changed == null ? source : changed;
            }
            if (current == 2) {
                ArrayList<BakedQuad> clipped = new ArrayList<>(source.size());
                for (BakedQuad quad : source) if (!crossesCell(quad)) clipped.add(quad);
                return clipped;
            }
            return source;
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return apply(delegate.getQuads(state, side, random));
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                RandomSource random, ModelData data, @Nullable RenderType type) {
            return apply(delegate.getQuads(state, side, random, data, type));
        }
        @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos,
                BlockState state, ModelData data) { return delegate.getModelData(level, pos, state, data); }
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            return delegate.getRenderTypes(state, random, data);
        }
        @Override public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            return delegate.getRenderPasses(stack, fabulous);
        }
        @Override public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
            return delegate.getRenderTypes(stack, fabulous);
        }
        @Override public BakedModel applyTransform(ItemDisplayContext ctx, PoseStack pose, boolean left) {
            delegate.applyTransform(ctx, pose, left); return this;
        }
        @Override public boolean useAmbientOcclusion() { return delegate.useAmbientOcclusion(); }
        @Override public boolean isGui3d() { return delegate.isGui3d(); }
        @Override public boolean usesBlockLight() { return delegate.usesBlockLight(); }
        @Override public boolean isCustomRenderer() { return delegate.isCustomRenderer(); }
        @Override public TextureAtlasSprite getParticleIcon() { return delegate.getParticleIcon(); }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return delegate.getParticleIcon(data); }
        @Override public ItemTransforms getTransforms() { return delegate.getTransforms(); }
        @Override public ItemOverrides getOverrides() { return delegate.getOverrides(); }
    }
}
