/*
 * Traincraft UssrSteamTenderModel for Minecraft 1.20.1.
 * Recovered mechanically from Traincraft 4.4.1_020 CE 7.1 ModelTenderEr_Ussr.
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

public final class UssrSteamTenderModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(Traincraft.MOD_ID, "steam_tender_ussr"), "main");

    private UssrSteamTenderModel() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Legacy part: body
        root.addOrReplaceChild("part_000",
                CubeListBuilder.create().texOffs(140, 0)
                        .addBox(0.0F, 0.0F, 0.0F, 36.0F, 12.0F, 22.0F),
                PartPose.offset(-19.0F, 9.0F, -11.0F));

        // Legacy part: box
        root.addOrReplaceChild("part_001",
                CubeListBuilder.create().texOffs(179, 112)
                        .addBox(0.0F, 0.0F, 0.0F, 36.0F, 5.0F, 0.0F),
                PartPose.offset(-19.0F, 21.0F, -11.0F));

        // Legacy part: box0
        root.addOrReplaceChild("part_002",
                CubeListBuilder.create().texOffs(41, 91)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 3.0F, 16.0F),
                PartPose.offset(23.0F, 5.0F, -8.0F));

        // Legacy part: box1
        root.addOrReplaceChild("part_003",
                CubeListBuilder.create().texOffs(97, 10)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 25.0F, 20.0F),
                PartPose.offset(17.0F, 9.0F, -10.0F));

        // Legacy part: box10
        root.addOrReplaceChild("part_004",
                CubeListBuilder.create().texOffs(80, 76)
                        .addBox(0.0F, 0.0F, -5.0F, 8.0F, 1.0F, 5.0F),
                PartPose.offsetAndRotation(24.0F, 35.0F, -4.0F,
                        -2.94960644F, -3.14159265F, 0.0F));

        // Legacy part: box11
        root.addOrReplaceChild("part_005",
                CubeListBuilder.create().texOffs(29, 21)
                        .addBox(0.0F, 0.0F, 0.0F, 7.0F, 22.0F, 1.0F),
                PartPose.offset(17.0F, 9.0F, 10.0F));

        // Legacy part: box12
        root.addOrReplaceChild("part_006",
                CubeListBuilder.create().texOffs(54, 3)
                        .addBox(0.0F, 0.0F, 0.0F, 0.0F, 20.0F, 20.0F),
                PartPose.offset(24.0F, 14.0F, -10.0F));

        // Legacy part: box13
        root.addOrReplaceChild("part_007",
                CubeListBuilder.create().texOffs(60, 74)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 2.0F, 3.0F),
                PartPose.offset(-17.0F, 21.0F, -7.0F));

        // Legacy part: box14
        root.addOrReplaceChild("part_008",
                CubeListBuilder.create().texOffs(89, 70)
                        .addBox(0.0F, 0.0F, 0.0F, 0.0F, 5.0F, 22.0F),
                PartPose.offset(-19.0F, 21.0F, -11.0F));

        // Legacy part: box15
        root.addOrReplaceChild("part_009",
                CubeListBuilder.create().texOffs(192, 118)
                        .addBox(0.0F, 0.0F, 0.0F, 28.0F, 8.0F, 1.0F),
                PartPose.offset(-11.0F, 21.0F, -9.0F));

        // Legacy part: box16
        root.addOrReplaceChild("part_010",
                CubeListBuilder.create().texOffs(150, 92)
                        .addBox(0.0F, 0.0F, 0.0F, 18.0F, 6.0F, 0.0F),
                PartPose.offset(4.0F, 0.0F, 5.0F));

        // Legacy part: box17
        root.addOrReplaceChild("part_011",
                CubeListBuilder.create().texOffs(81, 84)
                        .addBox(0.0F, 0.0F, -4.0F, 8.0F, 1.0F, 4.0F),
                PartPose.offsetAndRotation(16.0F, 34.0F, 9.0F,
                        -2.26892803F, 0.0F, 0.0F));

        // Legacy part: box18
        root.addOrReplaceChild("part_012",
                CubeListBuilder.create().texOffs(134, 118)
                        .addBox(0.0F, 0.0F, 0.0F, 28.0F, 8.0F, 1.0F),
                PartPose.offset(-11.0F, 21.0F, 8.0F));

        // Legacy part: box19
        root.addOrReplaceChild("part_013",
                CubeListBuilder.create().texOffs(17, 104)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 8.0F, 16.0F),
                PartPose.offset(-11.0F, 21.0F, -8.0F));

        // Legacy part: box2
        root.addOrReplaceChild("part_014",
                CubeListBuilder.create().texOffs(77, 65)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 1.0F, 8.0F),
                PartPose.offset(16.0F, 34.0F, -4.0F));

        // Legacy part: box21
        root.addOrReplaceChild("part_015",
                CubeListBuilder.create().texOffs(136, 73)
                        .addBox(0.0F, 0.0F, 0.0F, 45.0F, 2.0F, 14.0F),
                PartPose.offsetAndRotation(23.0F, 6.0F, 7.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box22
        root.addOrReplaceChild("part_016",
                CubeListBuilder.create().texOffs(150, 92)
                        .addBox(0.0F, 0.0F, 0.0F, 18.0F, 6.0F, 0.0F),
                PartPose.offset(-21.0F, 0.0F, 5.0F));

        // Legacy part: box23
        root.addOrReplaceChild("part_017",
                CubeListBuilder.create().texOffs(60, 74)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 2.0F, 3.0F),
                PartPose.offset(-17.0F, 21.0F, 4.0F));

        // Legacy part: box24
        root.addOrReplaceChild("part_018",
                CubeListBuilder.create().texOffs(40, 69)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 2.0F, 4.0F),
                PartPose.offset(-18.0F, 21.0F, -2.0F));

        // Legacy part: box25
        root.addOrReplaceChild("part_019",
                CubeListBuilder.create().texOffs(190, 92)
                        .addBox(0.0F, 0.0F, 0.0F, 18.0F, 5.0F, 12.0F),
                PartPose.offsetAndRotation(22.0F, 1.0F, 6.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box26
        root.addOrReplaceChild("part_020",
                CubeListBuilder.create().texOffs(38, 48)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 5.0F, 14.0F),
                PartPose.offset(-22.0F, 9.0F, -7.0F));

        // Legacy part: box27
        root.addOrReplaceChild("part_021",
                CubeListBuilder.create().texOffs(60, 46)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 14.0F),
                PartPose.offsetAndRotation(-22.0F, 13.0F, -7.0F,
                        0.0F, 0.0F, -5.93411946F));

        // Legacy part: box28
        root.addOrReplaceChild("part_022",
                CubeListBuilder.create().texOffs(190, 92)
                        .addBox(0.0F, 0.0F, 0.0F, 18.0F, 5.0F, 12.0F),
                PartPose.offsetAndRotation(-3.0F, 1.0F, 6.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box29
        root.addOrReplaceChild("part_023",
                CubeListBuilder.create().texOffs(2, 51)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 14.0F),
                PartPose.offsetAndRotation(-6.0F, 2.0F, 7.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box3
        root.addOrReplaceChild("part_024",
                CubeListBuilder.create().texOffs(79, 2)
                        .addBox(0.0F, 0.0F, 0.0F, 36.0F, 5.0F, 0.0F),
                PartPose.offset(-19.0F, 21.0F, 11.0F));

        // Legacy part: box30
        root.addOrReplaceChild("part_025",
                CubeListBuilder.create().texOffs(2, 51)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 14.0F),
                PartPose.offsetAndRotation(9.0F, 2.0F, 7.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box31
        root.addOrReplaceChild("part_026",
                CubeListBuilder.create().texOffs(2, 51)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 14.0F),
                PartPose.offsetAndRotation(19.0F, 2.0F, 7.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box32
        root.addOrReplaceChild("part_027",
                CubeListBuilder.create().texOffs(107, 103)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 20.0F, 1.0F),
                PartPose.offset(24.0F, 12.0F, -6.0F));

        // Legacy part: box33
        root.addOrReplaceChild("part_028",
                CubeListBuilder.create().texOffs(107, 103)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 20.0F, 1.0F),
                PartPose.offset(24.0F, 12.0F, 5.0F));

        // Legacy part: box34
        root.addOrReplaceChild("part_029",
                CubeListBuilder.create().texOffs(103, 115)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 1.0F, 10.0F),
                PartPose.offset(24.0F, 31.0F, -5.0F));

        // Legacy part: box35
        root.addOrReplaceChild("part_030",
                CubeListBuilder.create().texOffs(3, 68)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 4.0F, 4.0F),
                PartPose.offsetAndRotation(3.0F, 2.0F, 4.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box36
        root.addOrReplaceChild("part_031",
                CubeListBuilder.create().texOffs(73, 118)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 6.0F, 1.0F),
                PartPose.offset(-22.0F, 2.0F, -10.0F));

        // Legacy part: box37
        root.addOrReplaceChild("part_032",
                CubeListBuilder.create().texOffs(85, 118)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 6.0F, 1.0F),
                PartPose.offset(-22.0F, 2.0F, 9.0F));

        // Legacy part: box38
        root.addOrReplaceChild("part_033",
                CubeListBuilder.create().texOffs(150, 100)
                        .addBox(0.0F, 0.0F, 0.0F, 18.0F, 6.0F, 0.0F),
                PartPose.offset(4.0F, 0.0F, -5.0F));

        // Legacy part: box39
        root.addOrReplaceChild("part_034",
                CubeListBuilder.create().texOffs(150, 100)
                        .addBox(0.0F, 0.0F, 0.0F, 18.0F, 6.0F, 0.0F),
                PartPose.offset(-21.0F, 0.0F, -5.0F));

        // Legacy part: box4
        root.addOrReplaceChild("part_035",
                CubeListBuilder.create().texOffs(80, 76)
                        .addBox(0.0F, 0.0F, -5.0F, 8.0F, 1.0F, 5.0F),
                PartPose.offsetAndRotation(16.0F, 35.0F, 4.0F,
                        -2.94960644F, 0.0F, 0.0F));

        // Legacy part: box42
        root.addOrReplaceChild("part_036",
                CubeListBuilder.create().texOffs(1, 78)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 5.0F, 20.0F),
                PartPose.offset(18.0F, 9.0F, -10.0F));

        // Legacy part: box5
        root.addOrReplaceChild("part_037",
                CubeListBuilder.create().texOffs(10, 21)
                        .addBox(0.0F, 0.0F, 0.0F, 7.0F, 22.0F, 1.0F),
                PartPose.offset(17.0F, 9.0F, -11.0F));

        // Legacy part: box50
        root.addOrReplaceChild("part_038",
                CubeListBuilder.create().texOffs(142, 64)
                        .addBox(0.0F, 0.0F, 0.0F, 51.0F, 3.0F, 4.0F),
                PartPose.offset(-25.0F, 6.0F, -2.0F));

        // Legacy part: box6
        root.addOrReplaceChild("part_039",
                CubeListBuilder.create().texOffs(124, 39)
                        .addBox(0.0F, 0.0F, 0.0F, 46.0F, 1.0F, 20.0F),
                PartPose.offsetAndRotation(24.0F, 8.0F, 10.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box64
        root.addOrReplaceChild("part_040",
                CubeListBuilder.create().texOffs(2, 51)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 14.0F),
                PartPose.offsetAndRotation(-16.0F, 2.0F, 7.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box7
        root.addOrReplaceChild("part_041",
                CubeListBuilder.create().texOffs(60, 95)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 5.0F, 16.0F),
                PartPose.offset(-23.0F, 5.0F, -8.0F));

        // Legacy part: box8
        root.addOrReplaceChild("part_042",
                CubeListBuilder.create().texOffs(81, 84)
                        .addBox(0.0F, 0.0F, -4.0F, 8.0F, 1.0F, 4.0F),
                PartPose.offsetAndRotation(24.0F, 34.0F, -9.0F,
                        -2.26892803F, -3.14159265F, 0.0F));

        // Legacy part: box9
        root.addOrReplaceChild("part_043",
                CubeListBuilder.create().texOffs(125, 75)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 0.0F, 8.0F),
                PartPose.offsetAndRotation(23.0F, 14.0F, -4.0F,
                        0.0F, 0.0F, -6.23082543F));

        return LayerDefinition.create(mesh, 256, 128);
    }

    public static ModelPart root(ModelPart bakedRoot) {
        return bakedRoot;
    }
}
