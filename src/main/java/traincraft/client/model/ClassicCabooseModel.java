/* Traincraft classic ModelCaboose converted for Minecraft 1.20.1. */
package traincraft.client.model;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;

/** Box-for-box geometry port of Traincraft 1.7.10 ModelCaboose. */
public final class ClassicCabooseModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(Traincraft.MOD_ID, "classic_caboose"), "main");

    private ClassicCabooseModel() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("box",
                CubeListBuilder.create().texOffs(3, 52)
                        .addBox(0.0F, 0.0F, 0.0F, 37.0F, 2.0F, 20.0F),
                PartPose.offset(-18.0F, 2.0F, -9.0F));
        root.addOrReplaceChild("box0",
                CubeListBuilder.create().texOffs(84, 215)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 1.0F),
                PartPose.offset(18.0F, 4.0F, 10.0F));
        root.addOrReplaceChild("box1",
                CubeListBuilder.create().texOffs(78, 215)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 1.0F),
                PartPose.offset(18.0F, 4.0F, -9.0F));
        root.addOrReplaceChild("box10",
                CubeListBuilder.create().texOffs(63, 155)
                        .addBox(0.0F, 0.0F, 0.0F, 23.0F, 20.0F, 1.0F),
                PartPose.offset(-11.0F, 4.0F, -8.0F));
        root.addOrReplaceChild("box11",
                CubeListBuilder.create().texOffs(2, 155)
                        .addBox(0.0F, 0.0F, 0.0F, 23.0F, 20.0F, 1.0F),
                PartPose.offset(-11.0F, 4.0F, 9.0F));
        root.addOrReplaceChild("box12",
                CubeListBuilder.create().texOffs(-1, 20)
                        .addBox(0.0F, 0.0F, 0.0F, 39.0F, 1.0F, 24.0F),
                PartPose.offset(-19.0F, 24.0F, -11.0F));
        root.addOrReplaceChild("box13",
                CubeListBuilder.create().texOffs(85, 192)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 13.0F, 1.0F),
                PartPose.offset(18.0F, 11.0F, 10.0F));
        root.addOrReplaceChild("box14",
                CubeListBuilder.create().texOffs(78, 192)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 13.0F, 1.0F),
                PartPose.offset(18.0F, 11.0F, -9.0F));
        root.addOrReplaceChild("box15",
                CubeListBuilder.create().texOffs(70, 192)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 13.0F, 1.0F),
                PartPose.offset(-18.0F, 11.0F, -9.0F));
        root.addOrReplaceChild("box16",
                CubeListBuilder.create().texOffs(62, 192)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 13.0F, 1.0F),
                PartPose.offset(-18.0F, 11.0F, 10.0F));
        root.addOrReplaceChild("box17",
                CubeListBuilder.create().texOffs(58, 135)
                        .addBox(0.0F, 0.0F, 0.0F, 13.0F, 2.0F, 14.0F),
                PartPose.offset(-19.0F, 25.0F, -6.0F));
        root.addOrReplaceChild("box18",
                CubeListBuilder.create().texOffs(38, 216)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 4.0F, 1.0F),
                PartPose.offset(-13.0F, -1.0F, -6.0F));
        root.addOrReplaceChild("box19",
                CubeListBuilder.create().texOffs(26, 216)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 4.0F, 1.0F),
                PartPose.offset(-13.0F, -1.0F, 7.0F));
        root.addOrReplaceChild("box2",
                CubeListBuilder.create().texOffs(71, 215)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 1.0F),
                PartPose.offset(18.0F, 4.0F, 1.0F));
        root.addOrReplaceChild("box20",
                CubeListBuilder.create().texOffs(14, 216)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 4.0F, 1.0F),
                PartPose.offset(10.0F, -1.0F, 7.0F));
        root.addOrReplaceChild("box21",
                CubeListBuilder.create().texOffs(2, 216)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 4.0F, 1.0F),
                PartPose.offset(10.0F, -1.0F, -6.0F));
        root.addOrReplaceChild("box22",
                CubeListBuilder.create().texOffs(25, 228)
                        .addBox(0.0F, 0.0F, 0.0F, 20.0F, 10.0F, 14.0F),
                PartPose.offset(-6.0F, 25.0F, -6.0F));
        root.addOrReplaceChild("box23",
                CubeListBuilder.create().texOffs(2, 126)
                        .addBox(0.0F, 0.0F, 0.0F, 20.0F, 2.0F, 6.0F),
                PartPose.offset(-6.0F, 35.0F, -2.0F));
        root.addOrReplaceChild("box24",
                CubeListBuilder.create().texOffs(6, 137)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 2.0F, 14.0F),
                PartPose.offset(14.0F, 25.0F, -6.0F));
        root.addOrReplaceChild("box3",
                CubeListBuilder.create().texOffs(65, 215)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 1.0F),
                PartPose.offset(-18.0F, 4.0F, -9.0F));
        root.addOrReplaceChild("box4",
                CubeListBuilder.create().texOffs(59, 215)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 1.0F),
                PartPose.offset(-18.0F, 4.0F, 10.0F));
        root.addOrReplaceChild("box5",
                CubeListBuilder.create().texOffs(53, 215)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 1.0F),
                PartPose.offset(-18.0F, 4.0F, 0.0F));
        root.addOrReplaceChild("box6",
                CubeListBuilder.create().texOffs(3, 182)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 22.0F),
                PartPose.offset(-18.0F, 10.0F, -10.0F));
        root.addOrReplaceChild("box7",
                CubeListBuilder.create().texOffs(3, 182)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 22.0F),
                PartPose.offset(18.0F, 10.0F, -10.0F));
        root.addOrReplaceChild("box8",
                CubeListBuilder.create().texOffs(49, 85)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 20.0F, 16.0F),
                PartPose.offset(-11.0F, 4.0F, -7.0F));
        root.addOrReplaceChild("box9",
                CubeListBuilder.create().texOffs(4, 85)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 20.0F, 16.0F),
                PartPose.offset(11.0F, 4.0F, -7.0F));

        return LayerDefinition.create(mesh, 128, 256);
    }

    public static ModelPart root(ModelPart bakedRoot) {
        return bakedRoot;
    }
}
