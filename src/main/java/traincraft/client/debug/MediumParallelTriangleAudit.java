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
 * DEV50b2x — reversible F9 face-construction isolation, NO SOURCE MESH EDIT.
 * The installed DEV50b2p curve OBJ cells already contain 4336 triangles per
 * side; testing another naive quad split would not be meaningful.
 * Isolate baked rail SIDE faces' lighting, winding, packed normals, and tiny
 * triangles instead. Mode zero returns the exact delegate. F6/F8/F10 zero.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MediumParallelTriangleAudit {
    private static volatile int mode=0;
    private static final String[] LABELS={
            "ORIGINAL (exact delegate)",
            "RAIL_SIDE_FLAT_SHADE (geometry unchanged)",
            "RAIL_SIDE_REVERSE_WINDING (test only)",
            "RAIL_SIDE_REBUILT_VERTEX_NORMALS (test only)",
            "RAIL_SIDE_HIDE_SMALL_TRIANGLES (holes expected)"
    };
    private MediumParallelTriangleAudit() { }

    static void cycle(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null || mc.screen!=null) return;
        mode=(mode+1)%LABELS.length;
        Traincraft.LOGGER.warn("[TC-DEV50B2X] F9 MODE={} {} -- CLIENT TEST ONLY; NO WORLD OR OBJ EDITS",mode,LABELS[mode]);
        mc.gui.getChat().addMessage(Component.literal("[TC triangle audit] F9 " + mode
                + "/4: " + LABELS[mode] + " (F6/F7/F8/F10=0)"));
        mc.levelRenderer.allChanged();
    }

    private static boolean target(ResourceLocation id){
        if(!(id instanceof ModelResourceLocation model)
                || !Traincraft.MOD_ID.equals(id.getNamespace())
                || "inventory".equals(model.getVariant())) return false;
        return id.getPath().equals("track_parallel_curve_medium_left_segment")
                || id.getPath().equals("track_parallel_curve_medium_right_segment");
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void register(ModelEvent.ModifyBakingResult event){
        int found=0;
        for(Map.Entry<ResourceLocation,BakedModel> e:event.getModels().entrySet()){
            if(target(e.getKey()) && !(e.getValue() instanceof AuditModel)){
                e.setValue(new AuditModel(e.getValue()));
                found++;
            }
        }

        // DEV58B9R4R1: obsolete rail F-key developer hint retired; renderer stays at default mode zero.
    }

    private static boolean near(float x,float y){return Math.abs(x-y)<0.001f;}
    private static boolean sideQuad(BakedQuad quad){
        int[] raw=quad.getVertices();
        if(raw==null || raw.length<12 || raw.length%4!=0) return false;
        int stride=raw.length/4;
        if(stride<3) return false;
        float lo=Float.POSITIVE_INFINITY, hi=Float.NEGATIVE_INFINITY;
        boolean rail=false,sleeper=false;
        for(int i=0;i<4;i++){
            float y=Float.intBitsToFloat(raw[i*stride+1]);
            if(!Float.isFinite(y)) return false;
            lo=Math.min(lo,y);hi=Math.max(hi,y);
            if(near(y,0.025803f)||near(y,0.041289f)||near(y,0.071584f)||near(y,0.085803f))rail=true;
            if(near(y,-0.030000f)||near(y,-0.025278f)||near(y,0.002000f)
                    ||near(y,0.030000f)||near(y,0.054721f))sleeper=true;
        }
        return rail && !sleeper && hi-lo>0.001f && lo>=0.024f && hi<=0.0879f;
    }

    private static BakedQuad withoutShade(BakedQuad q){
        if(!q.isShade())return q;
        return new BakedQuad(q.getVertices().clone(),q.getTintIndex(),q.getDirection(),q.getSprite(),false);
    }
    private static BakedQuad reverse(BakedQuad q){
        int[] src=q.getVertices();if(src.length%4!=0)return q;
        int stride=src.length/4;int[] dst=src.clone();int[] permutation={0,3,2,1};
        for(int i=0;i<4;i++)System.arraycopy(src,permutation[i]*stride,dst,i*stride,stride);
        // The index 7 encoded packed normal is deliberately unchanged: this
        // mode isolates winding from vertex normal shading (mode 3).
        return new BakedQuad(dst,q.getTintIndex(),q.getDirection(),q.getSprite(),q.isShade());
    }

    private static float[] geometryNormal(BakedQuad q){
        int[] raw=q.getVertices();if(raw.length%4!=0)return null;
        int stride=raw.length/4;if(stride<3)return null;
        float[][] p=new float[4][3];
        for(int i=0;i<4;i++)for(int j=0;j<3;j++){
            float a=Float.intBitsToFloat(raw[i*stride+j]);
            if(!Float.isFinite(a))return null;
            p[i][j]=a;
        }
        int[][] sets={{0,1,2},{0,2,3},{0,1,3},{1,2,3}};
        for(int[] t:sets){
            float ax=p[t[1]][0]-p[t[0]][0],ay=p[t[1]][1]-p[t[0]][1],az=p[t[1]][2]-p[t[0]][2];
            float bx=p[t[2]][0]-p[t[0]][0],by=p[t[2]][1]-p[t[0]][1],bz=p[t[2]][2]-p[t[0]][2];
            float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
            float size=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(size>0.000001f)return new float[]{nx/size,ny/size,nz/size,size*0.5f};
        }
        return null;
    }
    private static int encodeNormal(float x,float y,float z){
        int a=Math.round(Math.max(-1f,Math.min(1f,x))*127f)&255;
        int b=Math.round(Math.max(-1f,Math.min(1f,y))*127f)&255;
        int c=Math.round(Math.max(-1f,Math.min(1f,z))*127f)&255;
        return a|(b<<8)|(c<<16);
    }
    private static BakedQuad recalcNormals(BakedQuad q){
        int[] src=q.getVertices();int stride=src.length/4;if(stride<8)return q;
        float[] n=geometryNormal(q);if(n==null)return q;
        int[] dst=src.clone();int normal=encodeNormal(n[0],n[1],n[2]);
        for(int i=0;i<4;i++)dst[i*stride+7]=normal;
        return new BakedQuad(dst,q.getTintIndex(),q.getDirection(),q.getSprite(),q.isShade());
    }
    private static boolean tiny(BakedQuad q){
        float[] n=geometryNormal(q);
        return n!=null && n[3]<0.0001f;
    }
    private static final class AuditModel implements BakedModel{
        private final BakedModel delegate;
        AuditModel(BakedModel source){delegate=source;}
        private List<BakedQuad> convert(List<BakedQuad> input){
            int current=mode;
            if(current==0||input.isEmpty())return input;
            List<BakedQuad> out=new ArrayList<>(input.size());
            for(BakedQuad q:input){
                if(!sideQuad(q)){out.add(q);continue;}
                switch(current){
                    case 1 -> out.add(withoutShade(q));
                    case 2 -> out.add(reverse(q));
                    case 3 -> out.add(recalcNormals(q));
                    case 4 -> {if(!tiny(q))out.add(q);}
                    default -> out.add(q);
                }
            }
            return out;
        }
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state,@Nullable Direction side,RandomSource rand){return convert(delegate.getQuads(state,side,rand));}
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state,@Nullable Direction side,RandomSource rand,ModelData data,@Nullable RenderType type){return convert(delegate.getQuads(state,side,rand,data,type));}
        @Override public ModelData getModelData(BlockAndTintGetter world,BlockPos pos,BlockState state,ModelData data){return delegate.getModelData(world,pos,state,data);}
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource rand,ModelData data){return delegate.getRenderTypes(state,rand,data);}
        @Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous){return delegate.getRenderPasses(stack,fabulous);}
        @Override public List<RenderType> getRenderTypes(ItemStack stack,boolean fabulous){return delegate.getRenderTypes(stack,fabulous);}
        @Override public BakedModel applyTransform(ItemDisplayContext context,PoseStack pose,boolean left){delegate.applyTransform(context,pose,left);return this;}
        @Override public boolean useAmbientOcclusion(){return delegate.useAmbientOcclusion();}
        @Override public boolean isGui3d(){return delegate.isGui3d();}
        @Override public boolean usesBlockLight(){return delegate.usesBlockLight();}
        @Override public boolean isCustomRenderer(){return delegate.isCustomRenderer();}
        @Override public TextureAtlasSprite getParticleIcon(){return delegate.getParticleIcon();}
        @Override public TextureAtlasSprite getParticleIcon(ModelData data){return delegate.getParticleIcon(data);}
        @Override public ItemTransforms getTransforms(){return delegate.getTransforms();}
        @Override public ItemOverrides getOverrides(){return delegate.getOverrides();}
    }
}
