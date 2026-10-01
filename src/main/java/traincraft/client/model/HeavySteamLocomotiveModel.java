/*
 * Traincraft HeavySteamLocomotive model for Minecraft 1.20.1.
 * Recovered mechanically from Traincraft 4.4.1_020 CE 7.1 ModelHeavySteamLoco.class.
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

public final class HeavySteamLocomotiveModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(Traincraft.MOD_ID, "locomotive_steam_heavy"), "main");

    private HeavySteamLocomotiveModel() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("part_000",
                CubeListBuilder.create().texOffs(106, 82)
                        .addBox(0.0F, 0.0F, 0.0F, 11.0F, 11.0F, 1.0F),
                PartPose.offset(-34.0F, -1.0F, -7.0F));

        root.addOrReplaceChild("part_001",
                CubeListBuilder.create().texOffs(109, 73)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 8.0F, 1.0F),
                PartPose.offset(-55.0F, -1.0F, -7.0F));

        root.addOrReplaceChild("part_002",
                CubeListBuilder.create().texOffs(109, 73)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 8.0F, 1.0F),
                PartPose.offset(-43.0F, -1.0F, -7.0F));

        root.addOrReplaceChild("part_003",
                CubeListBuilder.create().texOffs(106, 82)
                        .addBox(0.0F, 0.0F, 0.0F, 11.0F, 11.0F, 1.0F),
                PartPose.offset(-34.0F, -1.0F, 6.0F));

        root.addOrReplaceChild("part_004",
                CubeListBuilder.create().texOffs(1, 173)
                        .addBox(0.0F, 0.0F, 0.0F, 45.0F, 10.0F, 10.0F),
                PartPose.offsetAndRotation(-55.0F, 15.0F, 0.0F,
                        -0.7853982F, 0.0F, 0.0F));

        root.addOrReplaceChild("part_005",
                CubeListBuilder.create().texOffs(3, 74)
                        .addBox(0.0F, 0.0F, 0.0F, 39.0F, 7.0F, 10.0F),
                PartPose.offset(-52.0F, 19.0F, -5.0F));

        root.addOrReplaceChild("part_006",
                CubeListBuilder.create().texOffs(41, 107)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 5.0F, 20.0F),
                PartPose.offset(-60.0F, 1.0F, -10.0F));

        root.addOrReplaceChild("part_007",
                CubeListBuilder.create().texOffs(4, 118)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 4.0F, 20.0F),
                PartPose.offset(4.0F, 1.0F, -10.0F));

        root.addOrReplaceChild("part_008",
                CubeListBuilder.create().texOffs(2, 2)
                        .addBox(0.0F, 0.0F, 0.0F, 43.0F, 1.0F, 20.0F),
                PartPose.offset(-53.0F, 10.0F, -10.0F));

        root.addOrReplaceChild("part_009",
                CubeListBuilder.create().texOffs(113, 51)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 7.0F, 4.0F),
                PartPose.offset(-48.0F, 26.0F, -2.0F));

        root.addOrReplaceChild("part_010",
                CubeListBuilder.create().texOffs(42, 94)
                        .addBox(0.0F, 0.0F, 0.0F, 17.0F, 6.0F, 6.0F),
                PartPose.offset(-35.0F, 25.0F, -3.0F));

        root.addOrReplaceChild("part_011",
                CubeListBuilder.create().texOffs(1, 93)
                        .addBox(0.0F, 0.0F, 0.0F, 11.0F, 7.0F, 18.0F),
                PartPose.offset(-21.0F, 11.0F, -9.0F));

        root.addOrReplaceChild("part_012",
                CubeListBuilder.create().texOffs(67, 195)
                        .addBox(0.0F, 0.0F, 0.0F, 15.0F, 13.0F, 1.0F),
                PartPose.offset(-8.0F, 8.0F, -10.0F));

        root.addOrReplaceChild("part_013",
                CubeListBuilder.create().texOffs(83, 74)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 4.0F, 20.0F),
                PartPose.offset(-57.0F, 6.0F, -10.0F));

        root.addOrReplaceChild("part_014",
                CubeListBuilder.create().texOffs(106, 82)
                        .addBox(0.0F, 0.0F, 0.0F, 11.0F, 11.0F, 1.0F),
                PartPose.offset(-22.0F, -1.0F, 6.0F));

        root.addOrReplaceChild("part_015",
                CubeListBuilder.create().texOffs(58, 212)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 1.0F, 20.0F),
                PartPose.offset(-60.0F, 6.0F, -10.0F));

        root.addOrReplaceChild("part_016",
                CubeListBuilder.create().texOffs(85, 214)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 21.0F, 20.0F),
                PartPose.offset(-10.0F, 8.0F, -10.0F));

        root.addOrReplaceChild("part_017",
                CubeListBuilder.create().texOffs(1, 198)
                        .addBox(0.0F, 0.0F, 0.0F, 20.0F, 2.0F, 18.0F),
                PartPose.offset(-8.0F, 8.0F, -9.0F));

        root.addOrReplaceChild("part_018",
                CubeListBuilder.create().texOffs(106, 82)
                        .addBox(0.0F, 0.0F, 0.0F, 11.0F, 11.0F, 1.0F),
                PartPose.offset(-10.0F, -1.0F, 6.0F));

        root.addOrReplaceChild("part_019",
                CubeListBuilder.create().texOffs(33, 132)
                        .addBox(0.0F, 0.0F, 0.0F, 27.0F, 1.0F, 14.0F),
                PartPose.offset(-10.0F, 32.0F, -7.0F));

        root.addOrReplaceChild("part_020",
                CubeListBuilder.create().texOffs(81, 119)
                        .addBox(0.0F, 0.0F, 0.0F, 23.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(-41.0F, 6.0F, 8.0F,
                        0.0F, 0.0F, -0.06981317F));

        root.addOrReplaceChild("part_021",
                CubeListBuilder.create().texOffs(94, 98)
                        .addBox(0.0F, 0.0F, 0.0F, 16.0F, 9.0F, 1.0F),
                PartPose.offset(-59.0F, 15.0F, -10.0F));

        root.addOrReplaceChild("part_022",
                CubeListBuilder.create().texOffs(77, 125)
                        .addBox(0.0F, 0.0F, 0.0F, 25.0F, 1.0F, 1.0F),
                PartPose.offset(-29.0F, 1.0F, 7.0F));

        root.addOrReplaceChild("part_023",
                CubeListBuilder.create().texOffs(1, 50)
                        .addBox(0.0F, 0.0F, 0.0F, 47.0F, 10.0F, 12.0F),
                PartPose.offset(-56.0F, 2.0F, -6.0F));

        root.addOrReplaceChild("part_024",
                CubeListBuilder.create().texOffs(49, 119)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 3.0F, 3.0F),
                PartPose.offset(-62.0F, 2.0F, 4.0F));

        root.addOrReplaceChild("part_025",
                CubeListBuilder.create().texOffs(2, 235)
                        .addBox(0.0F, 0.0F, 0.0F, 13.0F, 7.0F, 12.0F),
                PartPose.offset(-9.0F, 2.0F, -6.0F));

        root.addOrReplaceChild("part_026",
                CubeListBuilder.create().texOffs(52, 238)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 3.0F, 14.0F),
                PartPose.offset(-10.0F, 29.0F, -7.0F));

        root.addOrReplaceChild("part_027",
                CubeListBuilder.create().texOffs(6, 226)
                        .addBox(0.0F, 0.0F, 0.0F, 27.0F, 5.0F, 1.0F),
                PartPose.offsetAndRotation(-10.0F, 30.0F, -10.0F,
                        -5.4454274F, 0.0F, 0.0F));

        root.addOrReplaceChild("part_028",
                CubeListBuilder.create().texOffs(6, 226)
                        .addBox(0.0F, 0.0F, 0.0F, 27.0F, 5.0F, 1.0F),
                PartPose.offsetAndRotation(-10.0F, 29.0F, 10.0F,
                        -0.8552113F, 0.0F, 0.0F));

        root.addOrReplaceChild("part_029",
                CubeListBuilder.create().texOffs(78, 122)
                        .addBox(0.0F, 0.0F, 0.0F, 25.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(-41.0F, 4.0F, 8.0F,
                        0.0F, 0.0F, -0.12217305F));

        root.addOrReplaceChild("part_030",
                CubeListBuilder.create().texOffs(94, 109)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(-19.0F, 5.0F, 8.0F,
                        0.0F, 0.0F, -1.012291F));

        root.addOrReplaceChild("part_031",
                CubeListBuilder.create().texOffs(120, 218)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 8.0F, 1.0F),
                PartPose.offset(6.0F, 21.0F, -10.0F));

        root.addOrReplaceChild("part_032",
                CubeListBuilder.create().texOffs(120, 218)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 8.0F, 1.0F),
                PartPose.offset(-2.0F, 21.0F, -10.0F));

        root.addOrReplaceChild("part_033",
                CubeListBuilder.create().texOffs(94, 98)
                        .addBox(0.0F, 0.0F, 0.0F, 16.0F, 9.0F, 1.0F),
                PartPose.offset(-59.0F, 15.0F, 9.0F));

        root.addOrReplaceChild("part_034",
                CubeListBuilder.create().texOffs(106, 82)
                        .addBox(0.0F, 0.0F, 0.0F, 11.0F, 11.0F, 1.0F),
                PartPose.offset(-22.0F, -1.0F, -7.0F));

        root.addOrReplaceChild("part_035",
                CubeListBuilder.create().texOffs(77, 125)
                        .addBox(0.0F, 0.0F, 0.0F, 25.0F, 1.0F, 1.0F),
                PartPose.offset(-29.0F, 1.0F, -8.0F));

        root.addOrReplaceChild("part_036",
                CubeListBuilder.create().texOffs(78, 122)
                        .addBox(0.0F, 0.0F, 0.0F, 25.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(-41.0F, 4.0F, -9.0F,
                        0.0F, 0.0F, -0.12217305F));

        root.addOrReplaceChild("part_037",
                CubeListBuilder.create().texOffs(81, 119)
                        .addBox(0.0F, 0.0F, 0.0F, 23.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(-41.0F, 6.0F, -9.0F,
                        0.0F, 0.0F, -0.06981317F));

        root.addOrReplaceChild("part_038",
                CubeListBuilder.create().texOffs(94, 109)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(-19.0F, 5.0F, -9.0F,
                        0.0F, 0.0F, -1.012291F));

        root.addOrReplaceChild("part_039",
                CubeListBuilder.create().texOffs(67, 195)
                        .addBox(0.0F, 0.0F, 0.0F, 15.0F, 13.0F, 1.0F),
                PartPose.offset(-8.0F, 8.0F, 9.0F));

        root.addOrReplaceChild("part_040",
                CubeListBuilder.create().texOffs(120, 218)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 8.0F, 1.0F),
                PartPose.offset(-2.0F, 21.0F, 9.0F));

        root.addOrReplaceChild("part_041",
                CubeListBuilder.create().texOffs(109, 23)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 7.0F, 7.0F),
                PartPose.offsetAndRotation(-57.0F, 14.0F, 0.0F,
                        -0.7853982F, 0.0F, 0.0F));

        root.addOrReplaceChild("part_042",
                CubeListBuilder.create().texOffs(114, 41)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 4.0F, 4.0F),
                PartPose.offset(-58.0F, 26.0F, -2.0F));

        root.addOrReplaceChild("part_043",
                CubeListBuilder.create().texOffs(49, 119)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 3.0F, 3.0F),
                PartPose.offset(-62.0F, 2.0F, -7.0F));

        root.addOrReplaceChild("part_044",
                CubeListBuilder.create().texOffs(120, 218)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 8.0F, 1.0F),
                PartPose.offset(6.0F, 21.0F, 9.0F));

        root.addOrReplaceChild("part_045",
                CubeListBuilder.create().texOffs(1, 153)
                        .addBox(0.0F, 0.0F, 0.0F, 45.0F, 6.0F, 14.0F),
                PartPose.offset(-55.0F, 16.0F, -7.0F));

        root.addOrReplaceChild("part_046",
                CubeListBuilder.create().texOffs(106, 82)
                        .addBox(0.0F, 0.0F, 0.0F, 11.0F, 11.0F, 1.0F),
                PartPose.offset(-10.0F, -1.0F, -7.0F));

        root.addOrReplaceChild("part_047",
                CubeListBuilder.create().texOffs(4, 28)
                        .addBox(0.0F, 0.0F, 0.0F, 45.0F, 10.0F, 10.0F),
                PartPose.offsetAndRotation(-55.0F, 9.0F, 0.0F,
                        -0.7853982F, 0.0F, 0.0F));

        root.addOrReplaceChild("part_048",
                CubeListBuilder.create().texOffs(85, 193)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 18.0F),
                PartPose.offset(-52.0F, 19.0F, -9.0F));

        root.addOrReplaceChild("part_049",
                CubeListBuilder.create().texOffs(120, 62)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 4.0F, 4.0F),
                PartPose.offset(-54.0F, 26.0F, -2.0F));

        root.addOrReplaceChild("part_050",
                CubeListBuilder.create().texOffs(109, 73)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 8.0F, 1.0F),
                PartPose.offset(-55.0F, -1.0F, 6.0F));

        root.addOrReplaceChild("part_051",
                CubeListBuilder.create().texOffs(109, 73)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 8.0F, 1.0F),
                PartPose.offset(-43.0F, -1.0F, 6.0F));

        root.addOrReplaceChild("part_052",
                CubeListBuilder.create().texOffs(65, 107)
                        .addBox(0.0F, 0.0F, 0.0F, 10.0F, 7.0F, 4.0F),
                PartPose.offset(-50.0F, 2.0F, 7.0F));

        root.addOrReplaceChild("part_053",
                CubeListBuilder.create().texOffs(65, 107)
                        .addBox(0.0F, 0.0F, 0.0F, 10.0F, 7.0F, 4.0F),
                PartPose.offset(-50.0F, 2.0F, -11.0F));

        return LayerDefinition.create(mesh, 130, 256);
    }

    public static ModelPart root(ModelPart bakedRoot) {
        return bakedRoot;
    }
}
