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
                .texOffs(18, 68).addBox(9.0F, 1.0F, -9.0F, 1.0F, 12.0F, 8.0F)
                .texOffs(0, 24).addBox(4.0F, 29.0F, -4.0F, 2.0F, 8.0F, 8.0F)
                .texOffs(0, 68).addBox(-10.0F, 1.0F, -9.0F, 1.0F, 12.0F, 8.0F)
                .texOffs(0, 24).addBox(4.0F, 29.0F, -16.0F, 2.0F, 8.0F, 8.0F)
                .texOffs(88, 26).addBox(-10.0F, 13.0F, -20.0F, 1.0F, 12.0F, 19.0F)
                .texOffs(0, 12).addBox(-3.0F, 7.0F, 3.0F, 6.0F, 6.0F, 6.0F)
                .texOffs(88, 57).addBox(9.0F, 13.0F, -20.0F, 1.0F, 12.0F, 19.0F)
                .texOffs(0, 40).addBox(-6.0F, 29.0F, -16.0F, 2.0F, 8.0F, 8.0F)
                .texOffs(0, 40).addBox(-6.0F, 29.0F, -4.0F, 2.0F, 8.0F, 8.0F)
                .texOffs(39, 0).addBox(-3.0F, 7.0F, 16.0F, 6.0F, 8.0F, 6.0F)
                .texOffs(60, 42).addBox(-7.0F, 31.0F, -14.0F, 1.0F, 1.0F, 13.0F)
                .texOffs(60, 94).addBox(-6.0F, 13.0F, -4.0F, 12.0F, 12.0F, 22.0F)
                .texOffs(7, 59).addBox(-10.0F, 25.0F, -22.0F, 20.0F, 4.0F, 30.0F)
                .texOffs(0, 37).addBox(-10.0F, 0.0F, -20.0F, 20.0F, 1.0F, 20.0F);

        root.addOrReplaceChild("body", boxes, PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 128);
    }

    public static ModelPart body(ModelPart bakedRoot) {
        return bakedRoot.getChild("body");
    }
}
