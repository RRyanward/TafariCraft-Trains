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
 * DEV50b2w: F10 client-only model depth-bias isolation of medium parallel
 * curve rail sides. F6 showed orange/dark artifacts throughout three side
 * height bands; it did NOT prove a particular subface is wrong.
 * Tiny reversible offsets along the baked side-quad XZ normal are diagnostic.
 * F8/F7/F6 should all remain Mode 0; only F10 changes the quad positions.
 * This class does not change OBJ files, blockstates, rail pathing or worldgen.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MediumParallelRailDepthIsolation {
    private static volatile int mode = 0;
    // EXPERIMENT ONLY. Offset baked rail-side quads along their face normals.
    // It is not a permanent mesh edit or a GPU glPolygonOffset setting.
    // Test both signs; model face winding may vary by side.
    private static final float[] OFFSETS = {0f, 0.0005f, 0.002f, -0.002f, 0.006f, -0.006f};
    private static final String[] MODES = {
            "ORIGINAL (exact delegate)",
            "RAIL_SIDE_NORMAL_OFFSET +0.0005 BLOCK",
            "RAIL_SIDE_NORMAL_OFFSET +0.002 BLOCK",
            "RAIL_SIDE_NORMAL_OFFSET -0.002 BLOCK",
            "RAIL_SIDE_NORMAL_OFFSET +0.006 BLOCK",
            "RAIL_SIDE_NORMAL_OFFSET -0.006 BLOCK"
    };

    private MediumParallelRailDepthIsolation() { }

    static void cycle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) return;
        mode = (mode + 1) % MODES.length;
        Traincraft.LOGGER.warn("[TC-DEV50B2W] F10 MODE={} {} -- CLIENT-ONLY DEPTH ISOLATION; NO WORLD EDITS OR SOURCE MODEL CHANGES",
                mode, MODES[mode]);
        mc.gui.getChat().addMessage(Component.literal("[TC depth isolation] F10 " + mode
                + "/5: " + MODES[mode] + " (F8, F7 and F6 should remain mode 0)"));
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
            if (!target(entry.getKey()) || entry.getValue() instanceof RailDepthIsolationModel) continue;
            entry.setValue(new RailDepthIsolationModel(entry.getValue()));
            count++;
        }
        Traincraft.LOGGER.warn("[TC-DEV50B2W] WRAPPED curveStates={} F10=rail-side normal offsets; F8/F7 must be mode 0; startup F10=0",
                count);
    }

    // Measurement origin: F7 success isolated the artifact to the rail SIDE/BASE,
    // so split that group using the exact source OBJ vertical intervals.
    // All thresholds are model-local block units. Nonmatching faces fail open for
    // hide modes. NO force-removal of geometry is persisted to the world.
    // Targets SIDE quads only, using the accepted DEV50b2u height pattern.
    // Sleepers and rail tops pass through bit-for-bit. We do not alter normals
    // or shading/tint indices; the small world-position change isolates depth.
    private static boolean near(float a, float b) { return Math.abs(a-b) < 0.001f; }
    private static boolean railSide(BakedQuad quad) {
        int[] raw = quad.getVertices();
        if (raw == null || raw.length < 12 || raw.length % 4 != 0) return false;
        int stride=raw.length/4;
        if (stride<3) return false;
        float lo=Float.POSITIVE_INFINITY,hi=Float.NEGATIVE_INFINITY;
        boolean rail=false,sleeper=false;
        for(int i=0;i<4;i++) {
            float y=Float.intBitsToFloat(raw[i*stride+1]);
            if(!Float.isFinite(y)) return false;
            lo=Math.min(lo,y); hi=Math.max(hi,y);
            if(near(y,0.025803f)||near(y,0.041289f)
                    ||near(y,0.071584f)||near(y,0.085803f)) rail=true;
            if(near(y,-0.030000f)||near(y,-0.025278f)
                    ||near(y,0.002000f)||near(y,0.030000f)
                    ||near(y,0.054721f)) sleeper=true;
        }
        if(!rail || sleeper) return false;
        if(hi-lo < 0.001f) return false; // keep horizontal tops/foot surfaces
        return lo>=0.024f && hi<=0.0879f;
    }

    private static BakedQuad offsetQuad(BakedQuad quad, float displacement) {
        int[] source=quad.getVertices();
        int stride=source.length/4;
        if(stride<3) return quad;
        float[][] p=new float[4][3];
        for(int i=0;i<4;i++) {
            for(int j=0;j<3;j++) {
                float f=Float.intBitsToFloat(source[i*stride+j]);
                if(!Float.isFinite(f)) return quad;
                p[i][j]=f;
            }
        }
        // Face normal from first nondegenerate triangle of the existing quad.
        // Baked models sometimes repeat vertices for triangulated OBJ faces.
        float nx=0f, ny=0f, nz=0f;
        int[][] triplets={{0,1,2},{0,2,3},{0,1,3},{1,2,3}};
        for(int[] t:triplets) {
            float ax=p[t[1]][0]-p[t[0]][0];
            float ay=p[t[1]][1]-p[t[0]][1];
            float az=p[t[1]][2]-p[t[0]][2];
            float bx=p[t[2]][0]-p[t[0]][0];
            float by=p[t[2]][1]-p[t[0]][1];
            float bz=p[t[2]][2]-p[t[0]][2];
            nx=ay*bz-az*by; ny=az*bx-ax*bz; nz=ax*by-ay*bx;
            float sq=nx*nx+ny*ny+nz*nz;
            if(sq>1.0e-12f) break;
        }
        // Isolate the lateral (XZ) depth of rail sides, not their height.
        float lateral=(float)Math.sqrt(nx*nx+nz*nz);
        if(lateral < 1.0e-6f) return quad;
        nx/=lateral; nz/=lateral;
        int[] out=source.clone();
        for(int i=0;i<4;i++) {
            out[i*stride]=Float.floatToRawIntBits(p[i][0]+nx*displacement);
            out[i*stride+2]=Float.floatToRawIntBits(p[i][2]+nz*displacement);
        }
        return new BakedQuad(out,quad.getTintIndex(),quad.getDirection(),
                quad.getSprite(),quad.isShade());
    }

    private static final class RailDepthIsolationModel implements BakedModel {
        private final BakedModel delegate;
        RailDepthIsolationModel(BakedModel source) { this.delegate=source; }

        private List<BakedQuad> filter(List<BakedQuad> source) {
            int selected=mode;
            if(selected==0 || source.isEmpty()) return source; // original EXACT
            float offset=OFFSETS[selected];
            List<BakedQuad> result=new ArrayList<>(source.size());
            for(BakedQuad q:source) {
                result.add(railSide(q)?offsetQuad(q,offset):q);
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
