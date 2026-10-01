package traincraft.client.model.recovered;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.power.LegacyPoweredLocomotive;

public final class RecoveredModelLocoMinetrain extends EntityModel<LegacyPoweredLocomotive> {
  public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(Traincraft.MOD_ID, "recoveredmodellocominetrain"), "main");
  private final ModelPart root;
  public RecoveredModelLocoMinetrain(ModelPart root){this.root=root;}
  public static LayerDefinition createBodyLayer(){
    MeshDefinition mesh=new MeshDefinition();
    PartDefinition root=mesh.getRoot();
    root.addOrReplaceChild("box", CubeListBuilder.create().texOffs(0,38).addBox(0.000000000F,0.000000000F,0.000000000F,2.000000000F,3.000000000F,4.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(-12.000000000F,2.000000000F,-2.000000000F,0.000000000F,0.000000000F,0.000000000F));
    root.addOrReplaceChild("box0", CubeListBuilder.create().texOffs(0,0).addBox(0.000000000F,0.000000000F,0.000000000F,16.000000000F,6.000000000F,1.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(-11.000000000F,17.000000000F,-6.000000000F,-4.886921906F,0.000000000F,0.000000000F));
    root.addOrReplaceChild("box1", CubeListBuilder.create().texOffs(0,57).addBox(0.000000000F,0.000000000F,0.000000000F,6.000000000F,6.000000000F,1.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(7.000000000F,0.000000000F,-4.000000000F,0.000000000F,-3.141592654F,0.000000000F));
    root.addOrReplaceChild("box10", CubeListBuilder.create().texOffs(0,13).addBox(0.000000000F,0.000000000F,0.000000000F,4.000000000F,3.000000000F,2.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(-10.000000000F,17.000000000F,-1.000000000F,0.000000000F,0.000000000F,0.000000000F));
    root.addOrReplaceChild("box11", CubeListBuilder.create().texOffs(24,1).addBox(0.000000000F,0.000000000F,0.000000000F,10.000000000F,4.000000000F,10.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(7.000000000F,2.000000000F,-5.000000000F,0.000000000F,0.000000000F,0.000000000F));
    root.addOrReplaceChild("box12", CubeListBuilder.create().texOffs(0,38).addBox(0.000000000F,0.000000000F,0.000000000F,2.000000000F,3.000000000F,4.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(17.000000000F,2.000000000F,-2.000000000F,0.000000000F,0.000000000F,0.000000000F));
    root.addOrReplaceChild("box13", CubeListBuilder.create().texOffs(44,30).addBox(0.000000000F,0.000000000F,0.000000000F,2.000000000F,6.000000000F,8.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(5.000000000F,7.000000000F,-4.000000000F,0.000000000F,0.000000000F,0.000000000F));
    root.addOrReplaceChild("box14", CubeListBuilder.create().texOffs(0,19).addBox(0.000000000F,0.000000000F,0.000000000F,4.000000000F,6.000000000F,8.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(12.000000000F,6.000000000F,-4.000000000F,0.000000000F,0.000000000F,0.000000000F));
    root.addOrReplaceChild("box15", CubeListBuilder.create().texOffs(0,34).addBox(0.000000000F,0.000000000F,0.000000000F,16.000000000F,10.000000000F,12.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(-11.000000000F,7.000000000F,-6.000000000F,0.000000000F,0.000000000F,0.000000000F));
    root.addOrReplaceChild("box16", CubeListBuilder.create().texOffs(27,26).addBox(0.000000000F,0.000000000F,0.000000000F,2.000000000F,4.000000000F,4.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(5.000000000F,13.000000000F,-2.000000000F,0.000000000F,0.000000000F,0.000000000F));
    root.addOrReplaceChild("box2", CubeListBuilder.create().texOffs(0,57).addBox(0.000000000F,0.000000000F,0.000000000F,6.000000000F,6.000000000F,1.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(-2.000000000F,0.000000000F,5.000000000F,0.000000000F,-3.141592654F,0.000000000F));
    root.addOrReplaceChild("box20", CubeListBuilder.create().texOffs(0,57).addBox(0.000000000F,0.000000000F,0.000000000F,6.000000000F,6.000000000F,1.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(-2.000000000F,0.000000000F,-4.000000000F,0.000000000F,-3.141592654F,0.000000000F));
    root.addOrReplaceChild("box21", CubeListBuilder.create().texOffs(0,57).addBox(0.000000000F,0.000000000F,0.000000000F,6.000000000F,6.000000000F,1.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(7.000000000F,0.000000000F,5.000000000F,0.000000000F,-3.141592654F,0.000000000F));
    root.addOrReplaceChild("box23", CubeListBuilder.create().texOffs(25,16).addBox(0.000000000F,0.000000000F,0.000000000F,2.000000000F,2.000000000F,8.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(-6.000000000F,2.000000000F,-4.000000000F,0.000000000F,0.000000000F,0.000000000F));
    root.addOrReplaceChild("box24", CubeListBuilder.create().texOffs(12,10).addBox(0.000000000F,0.000000000F,0.000000000F,2.000000000F,4.000000000F,4.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(5.000000000F,3.000000000F,-2.000000000F,0.000000000F,0.000000000F,0.000000000F));
    root.addOrReplaceChild("box25", CubeListBuilder.create().texOffs(50,52).addBox(0.000000000F,0.000000000F,0.000000000F,1.000000000F,6.000000000F,6.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(-10.000000000F,1.000000000F,-3.000000000F,0.000000000F,0.000000000F,0.000000000F));
    root.addOrReplaceChild("box3", CubeListBuilder.create().texOffs(15,57).addBox(0.000000000F,0.000000000F,0.000000000F,16.000000000F,6.000000000F,1.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(-9.000000000F,1.000000000F,-3.000000000F,0.000000000F,0.000000000F,0.000000000F));
    root.addOrReplaceChild("box4", CubeListBuilder.create().texOffs(15,57).addBox(0.000000000F,0.000000000F,0.000000000F,16.000000000F,6.000000000F,1.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(-9.000000000F,1.000000000F,2.000000000F,0.000000000F,0.000000000F,0.000000000F));
    root.addOrReplaceChild("box5", CubeListBuilder.create().texOffs(0,0).addBox(0.000000000F,0.000000000F,0.000000000F,16.000000000F,6.000000000F,1.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(5.000000000F,17.000000000F,6.000000000F,-4.886921906F,-3.141592654F,0.000000000F));
    root.addOrReplaceChild("box6", CubeListBuilder.create().texOffs(59,45).addBox(0.000000000F,0.000000000F,0.000000000F,1.000000000F,11.000000000F,1.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(16.000000000F,6.000000000F,-5.000000000F,0.000000000F,0.000000000F,0.000000000F));
    root.addOrReplaceChild("box7", CubeListBuilder.create().texOffs(59,45).addBox(0.000000000F,0.000000000F,0.000000000F,1.000000000F,11.000000000F,1.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(16.000000000F,6.000000000F,4.000000000F,0.000000000F,0.000000000F,0.000000000F));
    root.addOrReplaceChild("box8", CubeListBuilder.create().texOffs(40,18).addBox(0.000000000F,0.000000000F,0.000000000F,1.000000000F,1.000000000F,10.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(16.000000000F,17.000000000F,-5.000000000F,0.000000000F,0.000000000F,0.000000000F));
    root.addOrReplaceChild("box9", CubeListBuilder.create().texOffs(25,16).addBox(0.000000000F,0.000000000F,0.000000000F,2.000000000F,2.000000000F,8.000000000F, new CubeDeformation(0.000000000F)), PartPose.offsetAndRotation(3.000000000F,2.000000000F,-4.000000000F,0.000000000F,0.000000000F,0.000000000F));
    return LayerDefinition.create(mesh,256,256);
  }
  @Override public void setupAnim(LegacyPoweredLocomotive e,float a,float b,float c,float d,float e2){}
  @Override public void renderToBuffer(PoseStack p,VertexConsumer v,int light,int overlay,float r,float g,float bl,float a){root.render(p,v,light,overlay,r,g,bl,a);}
}
