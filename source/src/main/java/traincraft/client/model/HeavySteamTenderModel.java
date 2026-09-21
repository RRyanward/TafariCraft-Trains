/*
 * Traincraft HeavySteamTender model for Minecraft 1.20.1.
 * Recovered mechanically from Traincraft 4.4.1_020 CE 7.1 ModelTenderHeavy.class.
 * Geometry, UV origins, part pivots and rotations preserve the production 1.7.10 model.
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

public final class HeavySteamTenderModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(Traincraft.MOD_ID, "steam_tender_heavy"), "main");

    private HeavySteamTenderModel() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("part_000",
                CubeListBuilder.create().texOffs(0, 27)
                        .addBox(0.0F, 0.0F, 0.0F, 43.0F, 16.0F, 20.0F),
                PartPose.offset(-21.0F, 5.0F, -10.0F));

        root.addOrReplaceChild("part_001",
                CubeListBuilder.create().texOffs(2, 66)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 1.0F),
                PartPose.offset(-18.0F, -1.0F, -7.0F));

        root.addOrReplaceChild("part_002",
                CubeListBuilder.create().texOffs(2, 66)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 1.0F),
                PartPose.offset(-11.0F, -1.0F, -7.0F));

        root.addOrReplaceChild("part_003",
                CubeListBuilder.create().texOffs(37, 64)
                        .addBox(0.0F, 0.0F, 0.0F, 31.0F, 4.0F, 1.0F),
                PartPose.offset(-24.0F, 21.0F, -10.0F));

        root.addOrReplaceChild("part_004",
                CubeListBuilder.create().texOffs(37, 64)
                        .addBox(0.0F, 0.0F, 0.0F, 31.0F, 4.0F, 1.0F),
                PartPose.offset(-24.0F, 21.0F, 9.0F));

        root.addOrReplaceChild("part_005",
                CubeListBuilder.create().texOffs(1, 1)
                        .addBox(0.0F, 0.0F, 0.0F, 26.0F, 3.0F, 12.0F),
                PartPose.offset(-20.0F, 21.0F, -6.0F));

        root.addOrReplaceChild("part_006",
                CubeListBuilder.create().texOffs(82, 65)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 4.0F, 20.0F),
                PartPose.offset(-21.0F, 1.0F, -10.0F));

        root.addOrReplaceChild("part_007",
                CubeListBuilder.create().texOffs(82, 65)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 4.0F, 20.0F),
                PartPose.offset(20.0F, 1.0F, -10.0F));

        root.addOrReplaceChild("part_008",
                CubeListBuilder.create().texOffs(1, 96)
                        .addBox(0.0F, 0.0F, 0.0F, 39.0F, 4.0F, 12.0F),
                PartPose.offset(-19.0F, 1.0F, -6.0F));

        root.addOrReplaceChild("part_009",
                CubeListBuilder.create().texOffs(3, 118)
                        .addBox(0.0F, 0.0F, 0.0F, 9.0F, 2.0F, 1.0F),
                PartPose.offset(-16.0F, 1.0F, -8.0F));

        root.addOrReplaceChild("part_010",
                CubeListBuilder.create().texOffs(3, 118)
                        .addBox(0.0F, 0.0F, 0.0F, 9.0F, 2.0F, 1.0F),
                PartPose.offset(-16.0F, 1.0F, 7.0F));

        root.addOrReplaceChild("part_011",
                CubeListBuilder.create().texOffs(3, 118)
                        .addBox(0.0F, 0.0F, 0.0F, 9.0F, 2.0F, 1.0F),
                PartPose.offset(8.0F, 1.0F, 7.0F));

        root.addOrReplaceChild("part_012",
                CubeListBuilder.create().texOffs(3, 118)
                        .addBox(0.0F, 0.0F, 0.0F, 9.0F, 2.0F, 1.0F),
                PartPose.offset(8.0F, 1.0F, -8.0F));

        root.addOrReplaceChild("part_013",
                CubeListBuilder.create().texOffs(2, 66)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 1.0F),
                PartPose.offset(-11.0F, -1.0F, 6.0F));

        root.addOrReplaceChild("part_014",
                CubeListBuilder.create().texOffs(7, 77)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 2.0F, 2.0F),
                PartPose.offset(22.0F, 2.0F, 5.0F));

        root.addOrReplaceChild("part_015",
                CubeListBuilder.create().texOffs(7, 77)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 2.0F, 2.0F),
                PartPose.offset(22.0F, 2.0F, -7.0F));

        root.addOrReplaceChild("part_016",
                CubeListBuilder.create().texOffs(34, 70)
                        .addBox(0.0F, 0.0F, 0.0F, 10.0F, 2.0F, 2.0F),
                PartPose.offset(-31.0F, 2.0F, -7.0F));

        root.addOrReplaceChild("part_017",
                CubeListBuilder.create().texOffs(34, 70)
                        .addBox(0.0F, 0.0F, 0.0F, 10.0F, 2.0F, 2.0F),
                PartPose.offset(-31.0F, 2.0F, 5.0F));

        root.addOrReplaceChild("part_018",
                CubeListBuilder.create().texOffs(3, 65)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 3.0F, 20.0F),
                PartPose.offset(-26.0F, 5.0F, -10.0F));

        root.addOrReplaceChild("part_019",
                CubeListBuilder.create().texOffs(34, 70)
                        .addBox(0.0F, 0.0F, 0.0F, 10.0F, 2.0F, 2.0F),
                PartPose.offset(-31.0F, 2.0F, -1.0F));

        root.addOrReplaceChild("part_020",
                CubeListBuilder.create().texOffs(2, 27)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 14.0F, 1.0F),
                PartPose.offset(-24.0F, 7.0F, -10.0F));

        root.addOrReplaceChild("part_021",
                CubeListBuilder.create().texOffs(20, 46)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 14.0F, 1.0F),
                PartPose.offset(-24.0F, 7.0F, 9.0F));

        root.addOrReplaceChild("part_022",
                CubeListBuilder.create().texOffs(2, 66)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 1.0F),
                PartPose.offset(-18.0F, -1.0F, 6.0F));

        root.addOrReplaceChild("part_023",
                CubeListBuilder.create().texOffs(2, 66)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 1.0F),
                PartPose.offset(13.0F, -1.0F, -7.0F));

        root.addOrReplaceChild("part_024",
                CubeListBuilder.create().texOffs(2, 66)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 1.0F),
                PartPose.offset(6.0F, -1.0F, -7.0F));

        root.addOrReplaceChild("part_025",
                CubeListBuilder.create().texOffs(2, 66)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 1.0F),
                PartPose.offset(6.0F, -1.0F, 6.0F));

        root.addOrReplaceChild("part_026",
                CubeListBuilder.create().texOffs(2, 66)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 1.0F),
                PartPose.offset(13.0F, -1.0F, 6.0F));

        root.addOrReplaceChild("part_027",
                CubeListBuilder.create().texOffs(55, 73)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 4.0F, 18.0F),
                PartPose.offset(6.0F, 21.0F, -9.0F));

        root.addOrReplaceChild("part_028",
                CubeListBuilder.create().texOffs(65, 1)
                        .addBox(0.0F, 0.0F, 0.0F, 9.0F, 4.0F, 20.0F),
                PartPose.offset(-4.0F, 1.0F, -10.0F));

        return LayerDefinition.create(mesh, 128, 256);
    }

    public static ModelPart root(ModelPart bakedRoot) {
        return bakedRoot;
    }
}
