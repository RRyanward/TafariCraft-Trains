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
 * DEV50b2v: F6 subface isolation of rail foot, rail web and head sides, no geometry edits.
 * This diagnostic touches neither the OBJ models nor block state/placement.
 * Values are based on exact height bands in the installed DEV50b2p meshes:
 *   rail head: 0.071584, 0.085803
 *   rail web/base: 0.025803, 0.041289
 *   ties/sleepers: 0.002000, 0.030000, 0.054721
 * The meshes have 648 rail-top, 1952 rail-side/base, 1736 tie faces
 * PER left/right assembly, after the DEV50b2p static face split.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MediumParallelRailSideIsolation {
    private static volatile int mode = 0;
    private static final String[] MODES = {
            "ORIGINAL (no F6 filtering)",
            "HIDE_RAIL_FOOT_BASE (web, head and sleepers remain)",
            "HIDE_RAIL_WEB (foot, head and sleepers remain)",
            "HIDE_RAIL_HEAD_SIDES (foot, web and sleepers remain)",
            "RAIL_FOOT_BASE_ONLY (other faces hidden)",
            "RAIL_WEB_ONLY (other faces hidden)",
            "RAIL_HEAD_SIDES_ONLY (other faces hidden)"
    };

    private MediumParallelRailSideIsolation() { }

    static void cycle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) return;
        mode = (mode + 1) % MODES.length;
        Traincraft.LOGGER.warn("[TC-DEV50B2V] F6 MODE={} {} -- CLIENT-ONLY FACE FILTER; NO WORLD EDITS",
                mode, MODES[mode]);
        mc.gui.getChat().addMessage(Component.literal("[TC face isolation] F6 " + mode
                + "/6: " + MODES[mode] + " (F8 and F7 should remain mode 0)"));
        mc.levelRenderer.allChanged();
    }

    private static boolean target(ResourceLocation key) {
        if (!(key instanceof ModelResourceLocation model)
                || !Traincraft.MOD_ID.equals(key.getNamespace())
                || "inventory".equals(model.getVariant())) return false;
        return key.getPath().equals("track_parallel_curve_medium_left_segment")
                || key.getPath().equals("track_parallel_curve_medium_right_segment");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void attach(ModelEvent.ModifyBakingResult event) {
        int count = 0;
        for (Map.Entry<ResourceLocation, BakedModel> entry : event.getModels().entrySet()) {
            if (!target(entry.getKey()) || entry.getValue() instanceof FaceIsolationModel) continue;
            entry.setValue(new FaceIsolationModel(entry.getValue()));
            count++;
        }

        // DEV58B9R4R1: obsolete rail F-key developer hint retired; renderer stays at default mode zero.
    }

    // Measurement origin: F7 success isolated the artifact to the rail SIDE/BASE,
    // so split that group using the exact source OBJ vertical intervals.
    // All thresholds are model-local block units. Nonmatching faces fail open for
    // hide modes. NO force-removal of geometry is persisted to the world.
    private enum Group { RAIL_FOOT_BASE, RAIL_WEB, RAIL_HEAD_SIDES, OTHER }
    private static boolean near(float a, float b) { return Math.abs(a-b) < 0.001f; }

    private static Group group(BakedQuad quad) {
        int[] raw = quad.getVertices();
        if (raw == null || raw.length < 12 || raw.length % 4 != 0) return Group.OTHER;
        int stride = raw.length / 4;
        if (stride < 3) return Group.OTHER;
        float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
        boolean railHeight = false;
        boolean sleeperHeight = false;
        for (int i=0; i<4; i++) {
            float y = Float.intBitsToFloat(raw[i*stride+1]);
            if (!Float.isFinite(y)) return Group.OTHER;
            lo=Math.min(lo,y); hi=Math.max(hi,y);
            if (near(y,0.025803f) || near(y,0.041289f)
                    || near(y,0.071584f) || near(y,0.085803f)) railHeight=true;
            if (near(y,-0.030000f) || near(y,-0.025278f)
                    || near(y,0.002000f) || near(y,0.030000f)
                    || near(y,0.054721f)) sleeperHeight=true;
        }
        if (!railHeight || sleeperHeight) return Group.OTHER;
        // Keep horizontal head/top surfaces unchanged. Only target rail-side
        // geometry shown to be defective by F7 isolation.
        if (lo >= 0.070f && hi-lo < 0.001f) return Group.OTHER;
        if (lo >= 0.071f && hi <= 0.0869f && hi-lo > 0.001f)
            return Group.RAIL_HEAD_SIDES;
        if (lo >= 0.041f && hi <= 0.0729f)
            return Group.RAIL_WEB;
        if (lo >= 0.025f && hi <= 0.0429f)
            return Group.RAIL_FOOT_BASE;
        return Group.OTHER;
    }

    private static boolean keep(Group g, int selected) {
        if (selected == 0) return true;
        return switch (selected) {
            case 1 -> g != Group.RAIL_FOOT_BASE;
            case 2 -> g != Group.RAIL_WEB;
            case 3 -> g != Group.RAIL_HEAD_SIDES;
            case 4 -> g == Group.RAIL_FOOT_BASE;
            case 5 -> g == Group.RAIL_WEB;
            case 6 -> g == Group.RAIL_HEAD_SIDES;
            default -> true;
        };
    }

    private static final class FaceIsolationModel implements BakedModel {
        private final BakedModel delegate;
        FaceIsolationModel(BakedModel source) { this.delegate=source; }

        private List<BakedQuad> filter(List<BakedQuad> source) {
            int selected = mode;
            if (selected == 0 || source.isEmpty()) return source; // exact unchanged path
            List<BakedQuad> result = new ArrayList<>(source.size());
            for (BakedQuad face : source) {
                if (keep(group(face),selected)) result.add(face);
            }
            return result;
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                RandomSource random) { return filter(delegate.getQuads(state,side,random)); }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                RandomSource random, ModelData data, @Nullable RenderType renderType) {
            return filter(delegate.getQuads(state,side,random,data,renderType));
        }
        @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos,
                BlockState state, ModelData data) { return delegate.getModelData(level,pos,state,data); }
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            return delegate.getRenderTypes(state,random,data);
        }
        @Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous) {
            return delegate.getRenderPasses(stack,fabulous);
        }
        @Override public List<RenderType> getRenderTypes(ItemStack stack,boolean fabulous) {
            return delegate.getRenderTypes(stack,fabulous);
        }
        @Override public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose,boolean leftHand) {
            delegate.applyTransform(context,pose,leftHand); return this;
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
