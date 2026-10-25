package traincraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import java.io.DataInputStream;
import java.io.BufferedInputStream;
import java.util.Locale;
import traincraft.entity.train.freight.TctTanker;

/** DEV46 purpose-built OBJ-to-binary static triangle renderer. No frozen steam renderer edits. */
public final class TctTankerRenderer extends EntityRenderer<TctTanker> {
    private static final String BASE = "traincraft";
    private final Mesh mesh;
    private static final String[] TYPES = {"universal", "water", "lava"};
    // DEV50d3T: geometry-verified four axle groups in the shared TCTM mesh.
    // Couplers, trucks and underframe remain stationary; only wheelsets roll.
    private final java.util.WeakHashMap<TctTanker, WheelMotion> wheelMotion = new java.util.WeakHashMap<>();
    private static final double ROLL_RADIUS = 0.2695D; // authored Y=0.018..0.557
    private static final double[] AXLE_X = {-2.7695D, -1.7915D, 1.8585D, 2.8365D};
    private static final double AXLE_Y = 0.2875D;

    private static final class WheelMotion {
        double lastX, lastZ, angle;
        boolean started;
    }
    private double rollAngle(TctTanker entity, float partialTick, float trainYaw) {
        WheelMotion motion = wheelMotion.computeIfAbsent(entity, e -> new WheelMotion());
        double ix = net.minecraft.util.Mth.lerp(partialTick, entity.xOld, entity.getX());
        double iz = net.minecraft.util.Mth.lerp(partialTick, entity.zOld, entity.getZ());
        if (motion.started) {
            double dx = ix - motion.lastX, dz = iz - motion.lastZ;
            double distanceSquared = dx * dx + dz * dz;
            // Treat large teleports or recoupling corrections as resets, not wheel travel.
            if (distanceSquared <= 0.25D) {
                double a = Math.toRadians(270.0D - trainYaw);
                double signedTravel = dx * Math.cos(a) - dz * Math.sin(a);
                motion.angle = Math.IEEEremainder(motion.angle - signedTravel / ROLL_RADIUS, 2.0D * Math.PI);
            }
        }
        motion.lastX = ix; motion.lastZ = iz; motion.started = true;
        return motion.angle;
    }


    public TctTankerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx); this.shadowRadius = 1.1F;
        mesh=Mesh.load(new ResourceLocation(BASE,"tct_meshes/tct_19k_shared.tctm"));
    }
    private int variant(TctTanker e) {
        String path = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        return path.endsWith("_water")?1:path.endsWith("_lava")?2:0;
    }
    @Override public void render(TctTanker entity,float yaw,float tick,PoseStack pose,MultiBufferSource buffers,int light) {
        int id=variant(entity);
        pose.pushPose();
        float trainYaw=entity.getRenderTrainFacingYaw(tick);
        var rail=traincraft.block.track.LegacyContinuousTrackPath.virtualBogiePose(entity,trainYaw,4.25D);
        if(rail!=null) {
            double dx=rail.center().x-entity.getX(),dz=rail.center().z-entity.getZ();
            if(dx*dx+dz*dz<=.04D) {pose.translate(dx,0D,dz);if(Math.abs(net.minecraft.util.Mth.wrapDegrees(rail.yaw()-trainYaw))<=20F)trainYaw=rail.yaw();}
        }
        pose.mulPose(Axis.YP.rotationDegrees(270F-trainYaw));
        // Mesh authored in world-block units. Wheels start at Y=0; verify rail-fit in DEV46 client.
        if(mesh!=null){
            ResourceLocation bodyTex=new ResourceLocation(BASE,"textures/tct_tankers/"+TYPES[id]+"_body.png");
            ResourceLocation wheelTex=new ResourceLocation(BASE,"textures/tct_tankers/"+TYPES[id]+"_wheels.png");
            ResourceLocation truckTex=new ResourceLocation(BASE,"textures/tct_tankers/"+TYPES[id]+"_trucks.png");
            RenderType bodyType=RenderType.entityCutoutNoCull(bodyTex);
            if(!mesh.gpuDraw("tanker-body",buffers,bodyType,pose,light,mesh.gpuBody))
                mesh.render(pose,buffers.getBuffer(bodyType),light,0);
            RenderType wheelType=RenderType.entityCutoutNoCull(wheelTex);
            if(mesh.wheelsSplit) {
                double angle=rollAngle(entity,tick,trainYaw);
                Dev58G11R15GpuBatch.Draw[] gpuWheelDraws=new Dev58G11R15GpuBatch.Draw[5];
                gpuWheelDraws[0]=new Dev58G11R15GpuBatch.Draw(mesh.gpuStaticWheels,pose.last());
                for(int axle=0;axle<4;axle++) {
                    pose.pushPose();
                    pose.translate(AXLE_X[axle],AXLE_Y,0D);
                    pose.mulPose(Axis.ZP.rotation((float)angle));
                    pose.translate(-AXLE_X[axle],-AXLE_Y,0D);
                    gpuWheelDraws[axle+1]=new Dev58G11R15GpuBatch.Draw(mesh.gpuAxles[axle],pose.last());
                    pose.popPose();
                }
                if(!Dev58G11R15GpuBatch.draw("tanker-wheels",buffers,wheelType,light,gpuWheelDraws)) {
                    VertexConsumer wheelSink=buffers.getBuffer(wheelType);
                    mesh.renderTriangleStarts(pose,wheelSink,light,1,mesh.staticWheelTriangles);
                    for(int axle=0;axle<4;axle++) {
                        pose.pushPose();
                        pose.translate(AXLE_X[axle],AXLE_Y,0D);
                        pose.mulPose(Axis.ZP.rotation((float)angle));
                        pose.translate(-AXLE_X[axle],-AXLE_Y,0D);
                        mesh.renderTriangleStarts(pose,wheelSink,light,1,mesh.axleWheelTriangles[axle]);
                        pose.popPose();
                    }
                }
            } else {
                // Unknown/replaced TCTM geometry: safe static fallback.
                mesh.render(pose,buffers.getBuffer(wheelType),light,1);
            }
            RenderType truckType=RenderType.entityCutoutNoCull(truckTex);
            if(!mesh.gpuDraw("tanker-trucks",buffers,truckType,pose,light,mesh.gpuTruck))
                mesh.render(pose,buffers.getBuffer(truckType),light,2);
        }
        pose.popPose();super.render(entity,yaw,tick,pose,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(TctTanker entity){return new ResourceLocation(BASE,"textures/tct_tankers/"+TYPES[variant(entity)]+"_body.png");}

    private static final class Mesh {
        private final float[][] positions,uvs,normals;
        private final int[][] colors;
        private final int[][] axleWheelTriangles = new int[4][];
        private final int[] staticWheelTriangles;
        private final boolean wheelsSplit;
        private final Dev58G11R15GpuBatch.Part gpuBody, gpuTruck, gpuStaticWheels;
        private final Dev58G11R15GpuBatch.Part[] gpuAxles=new Dev58G11R15GpuBatch.Part[4];
        private Mesh(float[][] pos,float[][] uv,float[][] normal,int[][] rgba,byte[] originalMeshHash){
            positions=pos;uvs=uv;normals=normal;colors=rgba;
            int[][] parts=loadWheelGroups(originalMeshHash,positions[1].length/3);
            wheelsSplit=parts!=null;
            if(parts==null){
                for(int i=0;i<4;i++)axleWheelTriangles[i]=new int[0];
                staticWheelTriangles=new int[0];
            }else{
                for(int i=0;i<4;i++)axleWheelTriangles[i]=parts[i];
                staticWheelTriangles=parts[4];
            }
            gpuBody=gpuBuildAll("body",0);
            gpuTruck=gpuBuildAll("trucks",2);
            gpuStaticWheels=parts==null?null:gpuBuildSubset("static-wheels",1,staticWheelTriangles);
            if(parts!=null)for(int axle=0;axle<4;axle++)
                gpuAxles[axle]=gpuBuildSubset("wheel-"+axle,1,axleWheelTriangles[axle]);
        }
        private Dev58G11R15GpuBatch.Part gpuBuildAll(String name,int group) {
            int size=positions[group].length/3;
            if(size==0 || size%3!=0)return null;
            int[] starts=new int[size/3];
            for(int i=0;i<starts.length;i++)starts[i]=i*3;
            return gpuBuildSubset(name,group,starts);
        }
        private Dev58G11R15GpuBatch.Part gpuBuildSubset(String name,int group,int[] starts) {
            if(starts==null || starts.length==0)return null;
            return new Dev58G11R15GpuBatch.Part("tct-"+name,starts.length*4,(sink,light)->{
                PoseStack.Pose identity=new PoseStack().last();
                for(int i:starts) {
                    emit(sink,identity,i,group,light);
                    emit(sink,identity,i+1,group,light);
                    emit(sink,identity,i+2,group,light);
                    emit(sink,identity,i+2,group,light);
                }
            });
        }
        private boolean gpuDraw(String name,MultiBufferSource source,RenderType type,
                                PoseStack pose,int light,Dev58G11R15GpuBatch.Part part) {
            return part!=null && Dev58G11R15GpuBatch.draw(name,source,type,light,
                    new Dev58G11R15GpuBatch.Draw(part,pose.last()));
        }
        private static int[][] loadWheelGroups(byte[] meshSha,int vertices){
            ResourceLocation ref=new ResourceLocation(BASE,"tct_meshes/tct_19k_wheelgroups.tcwg");
            try(var in=new DataInputStream(new BufferedInputStream(Minecraft.getInstance().getResourceManager().getResourceOrThrow(ref).open()))){
                if(in.readInt()!=0x54435747 || in.readInt()!=1 || in.readInt()!=vertices || in.readInt()!=5)
                    throw new IllegalStateException("Wheel-group header does not match TCTM");
                byte[] expect=new byte[32];in.readFully(expect);
                if(!java.util.Arrays.equals(expect,meshSha))
                    throw new IllegalStateException("Wheel-group mesh SHA256 does not match active model");
                int[][] groups=new int[5][];
                boolean[] seen=new boolean[vertices/3];
                final int[] expected={4148,4148,4148,4148,2262};
                for(int g=0;g<5;g++){
                    int count=in.readInt();if(count!=expected[g])throw new IllegalStateException("Unexpected group size "+g+" "+count);
                    groups[g]=new int[count];
                    for(int i=0;i<count;i++){
                        int vertex=in.readInt();
                        if(vertex<0||vertex>=vertices||vertex%3!=0||seen[vertex/3])
                            throw new IllegalStateException("Invalid or duplicate wheel triangle "+vertex);
                        seen[vertex/3]=true;groups[g][i]=vertex;
                    }
                }
                for(boolean present:seen)if(!present)throw new IllegalStateException("Missing wheel triangle");
                if(in.read()!=-1)throw new IllegalStateException("Trailing wheelmap bytes");
                org.slf4j.LoggerFactory.getLogger(TctTankerRenderer.class).info("[TC-DEV50D3T] Four tanker wheelsets ready; 2262 fixed hardware triangles preserved");
                return groups;
            }catch(Exception err){
                org.slf4j.LoggerFactory.getLogger(TctTankerRenderer.class).warn("[TC-DEV50D3T] Wheel animation disabled; using static safe fallback: {}",err.toString());
                return null;
            }
        }
        private static Mesh load(ResourceLocation location){
            try {
                byte[] meshBytes;
                try(var source=Minecraft.getInstance().getResourceManager().getResourceOrThrow(location).open()){
                    meshBytes=source.readAllBytes();
                }
                byte[] meshSha=java.security.MessageDigest.getInstance("SHA-256").digest(meshBytes);
                var in=new DataInputStream(new BufferedInputStream(new java.io.ByteArrayInputStream(meshBytes)));
                if(in.readInt()!=0x5443544d || in.readInt()!=2 || in.readInt()!=3)throw new IllegalStateException("Invalid TCTM v2 header: "+location);
                float[][] pos=new float[3][],uv=new float[3][],norm=new float[3][];int[][] col=new int[3][];
                for(int g=0;g<3;g++){
                    int n=in.readInt();if(n<0||n>500000)throw new IllegalStateException("Invalid vertex count "+n);
                    pos[g]=new float[n*3];uv[g]=new float[n*2];norm[g]=new float[n*3];col[g]=new int[n*3];
                    for(int v=0;v<n;v++){
                        for(int k=0;k<3;k++)pos[g][v*3+k]=in.readFloat();
                        for(int k=0;k<2;k++)uv[g][v*2+k]=in.readFloat();
                        for(int k=0;k<3;k++)norm[g][v*3+k]=in.readFloat();
                        for(int k=0;k<3;k++)col[g][v*3+k]=in.readUnsignedByte();
                    }
                }
                if(in.read()!=-1)throw new IllegalStateException("Trailing TCTM bytes");
                return new Mesh(pos,uv,norm,col,meshSha);
            }catch(Exception err){
                org.slf4j.LoggerFactory.getLogger(TctTankerRenderer.class).error("[TCT-DEV46] Cannot load tanker mesh {}",location,err);return null;
            }
        }
        private void render(PoseStack stack,VertexConsumer sink,int packedLight,int g){
            PoseStack.Pose p=stack.last();
            // TCTM is TRIANGLES; entityCutoutNoCull is QUADS. Send each
            // original triangle as a degenerate quad (A,B,C,C), preserving
            // authored winding, UVs, normals and flat-shaded materials.
            final int vertices = positions[g].length / 3;
            if(vertices % 3 != 0) throw new IllegalStateException("Non-triangle TCT mesh vertex count: " + vertices);
            for(int i=0;i<vertices;i+=3){
                emit(sink,p,i,g,packedLight);
                emit(sink,p,i+1,g,packedLight);
                emit(sink,p,i+2,g,packedLight);
                emit(sink,p,i+2,g,packedLight);
            }
        }
        private void renderTriangleStarts(PoseStack stack,VertexConsumer sink,int packedLight,int g,int[] triangles){
            PoseStack.Pose p=stack.last();
            for(int i:triangles){
                emit(sink,p,i,g,packedLight);
                emit(sink,p,i+1,g,packedLight);
                emit(sink,p,i+2,g,packedLight);
                emit(sink,p,i+2,g,packedLight);
            }
        }
        private void emit(VertexConsumer sink,PoseStack.Pose p,int i,int g,int packedLight){
            sink.vertex(p.pose(),positions[g][i*3],positions[g][i*3+1],positions[g][i*3+2])
                .color(colors[g][i*3],colors[g][i*3+1],colors[g][i*3+2],255)
                .uv(uvs[g][i*2],uvs[g][i*2+1]).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight).normal(p.normal(),normals[g][i*3],normals[g][i*3+1],normals[g][i*3+2]).endVertex();
        }
    }
}
