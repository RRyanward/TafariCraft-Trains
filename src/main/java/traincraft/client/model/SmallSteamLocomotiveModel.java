/*
 * Traincraft Small Steam Locomotive model for Minecraft 1.20.1.
 * Geometry converted from assets/traincraft/jtmt/train/steam/small.jtmt.
 * Legacy JTMT Y coordinates are mirrored into modern ModelPart space so the
 * running gear stays below the boiler and the cab roof stays above it.
 * Distributed under LGPL-v3.0.
 */
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

public final class SmallSteamLocomotiveModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(Traincraft.MOD_ID, "locomotive_steam_small"), "main");

    private SmallSteamLocomotiveModel() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        CubeListBuilder boxes = CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0F, 25.0F, 8.0F, 6.0F, 4.0F, 8.0F)
                .texOffs(43, 112).addBox(-2.0F, 1.0F, 12.0F, 4.0F, 12.0F, 4.0F)
                .texOffs(64, 0).addBox(-10.0F, 29.0F, 8.0F, 20.0F, 4.0F, 12.0F)
                .texOffs(0, 103).addBox(-10.0F, 1.0F, -1.0F, 20.0F, 24.0F, 1.0F)
                .texOffs(60, 42).addBox(6.0F, 31.0F, -11.0F, 1.0F, 1.0F, 13.0F)
                .texOffs(18, 68).addBox(9.0F, 1.0F, -9.0F, 1.0F, 12.0F, 8.0F)                .texOffs(0, 68).addBox(-10.0F, 1.0F, -9.0F, 1.0F, 12.0F, 8.0F)                .texOffs(88, 26).addBox(-10.0F, 13.0F, -20.0F, 1.0F, 12.0F, 19.0F)
                .texOffs(0, 12).addBox(-3.0F, 7.0F, 3.0F, 6.0F, 6.0F, 6.0F)
                .texOffs(88, 57).addBox(9.0F, 13.0F, -20.0F, 1.0F, 12.0F, 19.0F)                .texOffs(39, 0).addBox(-3.0F, 7.0F, 16.0F, 6.0F, 8.0F, 6.0F)
                .texOffs(60, 42).addBox(-7.0F, 31.0F, -14.0F, 1.0F, 1.0F, 13.0F)
                .texOffs(60, 94).addBox(-6.0F, 13.0F, -4.0F, 12.0F, 12.0F, 22.0F)
                .texOffs(7, 59).addBox(-10.0F, 25.0F, -22.0F, 20.0F, 4.0F, 30.0F)
                .texOffs(0, 37).addBox(-10.0F, 0.0F, -20.0F, 20.0F, 1.0F, 20.0F);
        // TC_DEV38_R1G1R2_NATIVE_WHEEL_SPLIT
        // These are the exact four 2x8x8 wheel cubes removed from the flat body
        // above. Local cube coordinates are centered on each original cube so
        // xRot spins the authored texture around the actual axle center.
        PartDefinition body = root.addOrReplaceChild("body", boxes, PartPose.ZERO);

        PartDefinition rightFront = body.addOrReplaceChild(
                "wheel_right_front",
                CubeListBuilder.create().texOffs(0, 24)
                        .addBox(-1.0F, -4.0F, -4.0F, 2.0F, 8.0F, 8.0F),
                PartPose.offset(5.0F, 33.0F, 0.0F));
        // DEV54H15AK: crank caps follow the wheel's existing rotation;
        // these are hidden unless the independent Small Steam pilot is ON.
        rightFront.addOrReplaceChild("h15ak_crank",
                CubeListBuilder.create().texOffs(0, 24)
                    .addBox(-.22F,-.45F,-.45F,.44F,.90F,.90F),
                PartPose.offset(1.45F,0.0F,1.50F));
        body.addOrReplaceChild(
                "wheel_right_rear",
                CubeListBuilder.create().texOffs(0, 24)
                        .addBox(-1.0F, -4.0F, -4.0F, 2.0F, 8.0F, 8.0F),
                PartPose.offset(5.0F, 33.0F, -12.0F));
        body.addOrReplaceChild(
                "wheel_left_rear",
                CubeListBuilder.create().texOffs(0, 40)
                        .addBox(-1.0F, -4.0F, -4.0F, 2.0F, 8.0F, 8.0F),
                PartPose.offset(-5.0F, 33.0F, -12.0F));
        PartDefinition leftFront = body.addOrReplaceChild(
                "wheel_left_front",
                CubeListBuilder.create().texOffs(0, 40)
                        .addBox(-1.0F, -4.0F, -4.0F, 2.0F, 8.0F, 8.0F),
                PartPose.offset(-5.0F, 33.0F, 0.0F));
        leftFront.addOrReplaceChild("h15ak_crank",
                CubeListBuilder.create().texOffs(0, 40)
                    .addBox(-.22F,-.45F,-.45F,.44F,.90F,.90F),
                PartPose.offset(-1.45F,0.0F,1.50F));
        // A separate moving rod from each FRONT driving crank to a horizontal
        // slider direction toward the piston. Existing wheels and any old rod
        // decorations are NOT replaced, shifted or hidden.
        body.addOrReplaceChild("h15ak_rod_right",
                CubeListBuilder.create().texOffs(0, 24)
                    .addBox(-.42F,-.43F,0.0F,.84F,.86F,8.0F),
                PartPose.offset(6.45F,33.0F,1.5F));
        body.addOrReplaceChild("h15ak_rod_left",
                CubeListBuilder.create().texOffs(0, 40)
                    .addBox(-.42F,-.43F,0.0F,.84F,.86F,8.0F),
                PartPose.offset(-6.45F,33.0F,1.5F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    public static void animateWheels(ModelPart body, float phase) {
        // Existing authored wheel parts only; no synthetic geometry.
        body.getChild("wheel_right_front").xRot = phase;
        body.getChild("wheel_right_rear").xRot = phase;
        body.getChild("wheel_left_rear").xRot = phase;
        body.getChild("wheel_left_front").xRot = phase;
    }
    // DEV54H15AK: call after accepted animateWheels with identical phase.
    // The 8-pixel connecting rod remains physically jointed to the orbiting
    // front-wheel pin while the far endpoint slides on y=33 in model space.
    public static void animateFrontCrankLinks(ModelPart body,float phase,boolean enabled){
        ModelPart right=body.getChild("h15ak_rod_right");
        ModelPart left=body.getChild("h15ak_rod_left");
        right.visible=enabled;
        left.visible=enabled;
        body.getChild("wheel_right_front").getChild("h15ak_crank").visible=enabled;
        body.getChild("wheel_left_front").getChild("h15ak_crank").visible=enabled;
        if(!enabled)return;
        final float radius=1.5F, length=8.0F;
        final float pinY=33.0F-radius*(float)Math.sin(phase);
        final float pinZ=radius*(float)Math.cos(phase);
        final float dy=33.0F-pinY;
        final float tilt=(float)Math.asin(Math.max(-1f,Math.min(1f,-dy/length)));
        right.y=pinY;right.z=pinZ;right.xRot=tilt;
        left.y=pinY;left.z=pinZ;left.xRot=tilt;
    }
    public static ModelPart body(ModelPart bakedRoot) {
        return bakedRoot.getChild("body");
    }
}
