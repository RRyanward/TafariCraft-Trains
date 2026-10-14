/* Traincraft classic ModelFreightCart2 converted for Minecraft 1.20.1. */
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

/** Geometry ported box-for-box from Traincraft 1.7.10 ModelFreightCart2. */
public final class FreightCartModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(Traincraft.MOD_ID, "freight_cart"), "main");

    private FreightCartModel() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        CubeListBuilder body = CubeListBuilder.create()
                .texOffs(1, 24).addBox(-5.0F, 1.0F, -9.0F, 9.0F, 4.0F, 18.0F)                .texOffs(1, 124).addBox(-22.0F, 5.0F, -10.0F, 43.0F, 27.0F, 20.0F)
                .texOffs(64, 26).addBox(-5.0F, 10.0F, -11.0F, 12.0F, 17.0F, 1.0F)
                .texOffs(64, 26).addBox(-5.0F, 10.0F, 10.0F, 12.0F, 17.0F, 1.0F)
                .texOffs(41, 26).addBox(-18.0F, 15.0F, 10.0F, 10.0F, 10.0F, 1.0F)
                .texOffs(63, 49).addBox(-6.0F, 7.0F, -11.0F, 25.0F, 3.0F, 1.0F)
                .texOffs(1, 96).addBox(-21.0F, 1.0F, -6.0F, 15.0F, 4.0F, 12.0F)
                .texOffs(3, 118).addBox(-18.0F, 1.0F, -8.0F, 9.0F, 2.0F, 1.0F)
                .texOffs(3, 118).addBox(-18.0F, 1.0F, 7.0F, 9.0F, 2.0F, 1.0F)
                .texOffs(3, 118).addBox(8.0F, 1.0F, 7.0F, 9.0F, 2.0F, 1.0F)
                .texOffs(3, 118).addBox(8.0F, 1.0F, -8.0F, 9.0F, 2.0F, 1.0F)                .texOffs(63, 49).addBox(-6.0F, 27.0F, -11.0F, 25.0F, 3.0F, 1.0F)
                .texOffs(7, 77).addBox(20.0F, 2.0F, -1.0F, 3.0F, 2.0F, 2.0F)
                .texOffs(63, 49).addBox(-6.0F, 7.0F, 10.0F, 25.0F, 3.0F, 1.0F)
                .texOffs(7, 77).addBox(-24.0F, 2.0F, -1.0F, 3.0F, 2.0F, 2.0F)
                .texOffs(41, 26).addBox(-18.0F, 15.0F, -11.0F, 10.0F, 10.0F, 1.0F)                .texOffs(1, 96).addBox(5.0F, 1.0F, -6.0F, 15.0F, 4.0F, 12.0F)
                .texOffs(63, 49).addBox(-6.0F, 27.0F, 10.0F, 25.0F, 3.0F, 1.0F);
        // TC_DEV38_R1G1R2_NATIVE_WHEEL_SPLIT
        // Exact eight 6x6x1 wheel slabs re-parented around their geometric
        // centers. At zRot=0 every cube occupies its original world position.
        PartDefinition bodyPart = root.addOrReplaceChild("body", body, PartPose.ZERO);

        bodyPart.addOrReplaceChild(
                "wheel_left_front_outer",
                CubeListBuilder.create().texOffs(2, 66)
                        .addBox(-3.0F, -3.0F, -0.5F, 6.0F, 6.0F, 1.0F),
                PartPose.offset(-17.0F, 2.0F, -6.5F));
        bodyPart.addOrReplaceChild(
                "wheel_left_front_inner",
                CubeListBuilder.create().texOffs(2, 66)
                        .addBox(-3.0F, -3.0F, -0.5F, 6.0F, 6.0F, 1.0F),
                PartPose.offset(-10.0F, 2.0F, -6.5F));
        bodyPart.addOrReplaceChild(
                "wheel_left_back_inner",
                CubeListBuilder.create().texOffs(2, 66)
                        .addBox(-3.0F, -3.0F, -0.5F, 6.0F, 6.0F, 1.0F),
                PartPose.offset(-10.0F, 2.0F, 6.5F));
        bodyPart.addOrReplaceChild(
                "wheel_left_back_outer",
                CubeListBuilder.create().texOffs(2, 66)
                        .addBox(-3.0F, -3.0F, -0.5F, 6.0F, 6.0F, 1.0F),
                PartPose.offset(-17.0F, 2.0F, 6.5F));

        bodyPart.addOrReplaceChild(
                "wheel_right_front_outer",
                CubeListBuilder.create().texOffs(2, 66)
                        .addBox(-3.0F, -3.0F, -0.5F, 6.0F, 6.0F, 1.0F),
                PartPose.offset(16.0F, 2.0F, -6.5F));
        bodyPart.addOrReplaceChild(
                "wheel_right_front_inner",
                CubeListBuilder.create().texOffs(2, 66)
                        .addBox(-3.0F, -3.0F, -0.5F, 6.0F, 6.0F, 1.0F),
                PartPose.offset(9.0F, 2.0F, -6.5F));
        bodyPart.addOrReplaceChild(
                "wheel_right_back_inner",
                CubeListBuilder.create().texOffs(2, 66)
                        .addBox(-3.0F, -3.0F, -0.5F, 6.0F, 6.0F, 1.0F),
                PartPose.offset(9.0F, 2.0F, 6.5F));
        bodyPart.addOrReplaceChild(
                "wheel_right_back_outer",
                CubeListBuilder.create().texOffs(2, 66)
                        .addBox(-3.0F, -3.0F, -0.5F, 6.0F, 6.0F, 1.0F),
                PartPose.offset(16.0F, 2.0F, 6.5F));
        return LayerDefinition.create(mesh, 128, 256);
    }

    public static void animateWheels(ModelPart bakedRoot, float phase) {
        ModelPart body = bakedRoot.getChild("body");

        body.getChild("wheel_left_front_outer").zRot = phase;
        body.getChild("wheel_left_front_inner").zRot = phase;
        body.getChild("wheel_left_back_inner").zRot = phase;
        body.getChild("wheel_left_back_outer").zRot = phase;

        body.getChild("wheel_right_front_outer").zRot = phase;
        body.getChild("wheel_right_front_inner").zRot = phase;
        body.getChild("wheel_right_back_inner").zRot = phase;
        body.getChild("wheel_right_back_outer").zRot = phase;
    }
    public static ModelPart root(ModelPart bakedRoot) {
        return bakedRoot;
    }
}
