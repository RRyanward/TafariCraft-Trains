/* Traincraft classic steam tender model converted for Minecraft 1.20.1. */
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

/** Geometry ported from the legacy ModelNormalSteamTender. */
public final class SteamTenderModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(Traincraft.MOD_ID, "steam_tender"), "main");

    private SteamTenderModel() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        CubeListBuilder body = CubeListBuilder.create()
                .texOffs(2, 9).addBox(-6.0F, 0.0F, -16.0F, 1.0F, 6.0F, 14.0F)
                .texOffs(2, 9).addBox(5.0F, 0.0F, -16.0F, 1.0F, 6.0F, 14.0F)
                .texOffs(35, 9).addBox(6.0F, 0.0F, -16.0F, 1.0F, 6.0F, 14.0F)
                .texOffs(35, 9).addBox(-7.0F, 0.0F, -16.0F, 1.0F, 6.0F, 14.0F)
                .texOffs(35, 9).addBox(-7.0F, 0.0F, 3.0F, 1.0F, 6.0F, 14.0F)
                .texOffs(107, 56).addBox(8.0F, 19.0F, -9.0F, 1.0F, 3.0F, 21.0F)
                .texOffs(165, 56).addBox(-9.0F, 19.0F, -9.0F, 1.0F, 3.0F, 21.0F)
                .texOffs(31, 69).addBox(-8.0F, 9.0F, 5.0F, 16.0F, 10.0F, 6.0F)
                .texOffs(110, 13).addBox(-2.0F, 19.0F, 6.0F, 4.0F, 2.0F, 4.0F)
                .texOffs(132, 2).addBox(-8.0F, 9.0F, -7.0F, 16.0F, 6.0F, 12.0F)
                .texOffs(143, 36).addBox(0.0F, 9.0F, -11.0F, 6.0F, 5.0F, 4.0F)
                .texOffs(35, 9).addBox(6.0F, 0.0F, 3.0F, 1.0F, 6.0F, 14.0F)
                .texOffs(142, 46).addBox(-5.0F, 9.0F, -9.0F, 6.0F, 9.0F, 6.0F)
                .texOffs(165, 35).addBox(1.0F, 15.0F, -5.0F, 6.0F, 5.0F, 6.0F)
                .texOffs(140, 21).addBox(-4.0F, 15.0F, -1.0F, 6.0F, 8.0F, 6.0F)
                .texOffs(165, 22).addBox(-8.0F, 15.0F, -4.0F, 6.0F, 6.0F, 6.0F)
                .texOffs(131, 65).addBox(8.0F, 9.0F, -14.0F, 1.0F, 10.0F, 26.0F)
                .texOffs(71, 21).addBox(-6.0F, 9.0F, 12.0F, 12.0F, 4.0F, 4.0F)
                .texOffs(65, 44).addBox(-7.0F, 5.0F, -2.0F, 14.0F, 1.0F, 1.0F)
                .texOffs(65, 44).addBox(-7.0F, 5.0F, -17.0F, 14.0F, 1.0F, 1.0F)
                .texOffs(31, 33).addBox(-2.0F, 7.0F, 17.0F, 4.0F, 2.0F, 4.0F)
                .texOffs(2, 33).addBox(-2.0F, 4.0F, -11.0F, 4.0F, 3.0F, 4.0F)
                .texOffs(186, 87).addBox(-8.0F, 9.0F, 11.0F, 16.0F, 13.0F, 1.0F)
                .texOffs(29, 40).addBox(-2.0F, 6.0F, -20.0F, 4.0F, 2.0F, 7.0F)
                .texOffs(66, 47).addBox(-6.0F, 2.0F, -10.0F, 12.0F, 2.0F, 2.0F)
                .texOffs(64, 44).addBox(-7.0F, 5.0F, 2.0F, 14.0F, 1.0F, 1.0F)
                .texOffs(65, 44).addBox(-7.0F, 5.0F, 17.0F, 14.0F, 1.0F, 1.0F)
                .texOffs(75, 65).addBox(-9.0F, 9.0F, -14.0F, 1.0F, 10.0F, 26.0F)
                .texOffs(3, 89).addBox(-9.0F, 7.0F, -16.0F, 18.0F, 2.0F, 33.0F)
                .texOffs(2, 33).addBox(-2.0F, 4.0F, 8.0F, 4.0F, 3.0F, 4.0F)
                .texOffs(2, 9).addBox(-6.0F, 0.0F, 3.0F, 1.0F, 6.0F, 14.0F)
                .texOffs(2, 9).addBox(5.0F, 0.0F, 3.0F, 1.0F, 6.0F, 14.0F)
                .texOffs(66, 47).addBox(-6.0F, 2.0F, 9.0F, 12.0F, 2.0F, 2.0F);

        root.addOrReplaceChild("body", body, PartPose.ZERO);

        CubeListBuilder slopedLip = CubeListBuilder.create()
                .texOffs(70, 13).addBox(0.0F, 0.0F, 0.0F, 12.0F, 1.0F, 5.0F);
        root.addOrReplaceChild(
                "sloped_lip",
                slopedLip,
                PartPose.offsetAndRotation(-6.0F, 13.0F, 12.0F, 0.2268928F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 256, 128);
    }

    public static ModelPart root(ModelPart bakedRoot) {
        return bakedRoot;
    }
}
