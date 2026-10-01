/*
 * Traincraft Br01SteamLocomotiveModel for Minecraft 1.20.1.
 * Step 8.3.2-r1 mechanically recovered from production-era ModelLocoBR01_DB.
 * Geometry, UV origins, pivots and rotations are preserved exactly.
 * Distributed under LGPL-v3.0.
 */
package traincraft.client.model;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;

public final class Br01SteamLocomotiveModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(Traincraft.MOD_ID, "locomotive_steam_br01"), "main");

    private Br01SteamLocomotiveModel() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Legacy part: box
        root.addOrReplaceChild("part_000",
                CubeListBuilder.create().texOffs(22, 54)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 3.0F, 20.0F),
                PartPose.offset(4.0F, 30.0F, -10.0F));

        // Legacy part: box0
        root.addOrReplaceChild("part_001",
                CubeListBuilder.create().texOffs(36, 107)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 10.0F, 10.0F),
                PartPose.offset(-48.0F, 19.0F, -5.0F));

        // Legacy part: box1
        root.addOrReplaceChild("part_002",
                CubeListBuilder.create().texOffs(0, 117)
                        .addBox(0.0F, 0.0F, 0.0F, 16.0F, 10.0F, 1.0F),
                PartPose.offset(4.0F, 14.0F, 10.0F));

        // Legacy part: box10
        root.addOrReplaceChild("part_003",
                CubeListBuilder.create().texOffs(104, 25)
                        .addBox(0.0F, 0.0F, 0.0F, 23.0F, 1.0F, 5.0F),
                PartPose.offsetAndRotation(26.0F, 34.0F, 7.0F, -3.75245789F, -3.14159265F, 0.0F));

        // Legacy part: box102
        root.addOrReplaceChild("part_004",
                CubeListBuilder.create().texOffs(72, 173)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 12.0F, 1.0F),
                PartPose.offset(-26.0F, 3.0F, 8.0F));

        // Legacy part: box103
        root.addOrReplaceChild("part_005",
                CubeListBuilder.create().texOffs(0, 151)
                        .addBox(0.0F, 0.0F, 0.0F, 29.0F, 11.0F, 1.0F),
                PartPose.offset(-36.0F, 3.0F, 7.0F));

        // Legacy part: box106
        root.addOrReplaceChild("part_006",
                CubeListBuilder.create().texOffs(2, 51)
                        .addBox(0.0F, 2.0F, 0.0F, 21.0F, 2.0F, 1.0F),
                PartPose.offsetAndRotation(-27.0F, 3.0F, -7.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box108
        root.addOrReplaceChild("part_007",
                CubeListBuilder.create().texOffs(52, 50)
                        .addBox(0.0F, 0.0F, 0.0F, 30.0F, 2.0F, 1.0F),
                PartPose.offsetAndRotation(4.0F, 2.0F, -5.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box11
        root.addOrReplaceChild("part_008",
                CubeListBuilder.create().texOffs(170, 63)
                        .addBox(0.0F, 0.0F, 0.0F, 37.0F, 16.0F, 6.0F),
                PartPose.offsetAndRotation(-47.0F, 21.0F, 8.0F, -1.57079633F, 0.0F, 0.0F));

        // Legacy part: box110
        root.addOrReplaceChild("part_009",
                CubeListBuilder.create().texOffs(0, 163)
                        .addBox(0.0F, 0.0F, 0.0F, 29.0F, 11.0F, 1.0F),
                PartPose.offset(-36.0F, 3.0F, -8.0F));

        // Legacy part: box12
        root.addOrReplaceChild("part_010",
                CubeListBuilder.create().texOffs(69, 69)
                        .addBox(0.0F, 0.0F, 0.0F, 17.0F, 7.0F, 1.0F),
                PartPose.offsetAndRotation(-52.0F, 21.0F, -11.0F, -6.19591884F, 0.0F, 0.0F));

        // Legacy part: box13
        root.addOrReplaceChild("part_011",
                CubeListBuilder.create().texOffs(63, 200)
                        .addBox(0.0F, 0.0F, 0.0F, 9.0F, 4.0F, 4.0F),
                PartPose.offsetAndRotation(-35.0F, 10.0F, 9.0F, 0.0F, 3.14159265F, 6.28318531F));

        // Legacy part: box14
        root.addOrReplaceChild("part_012",
                CubeListBuilder.create().texOffs(49, 110)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 2.0F, 20.0F),
                PartPose.offset(-44.0F, 15.0F, -10.0F));

        // Legacy part: box15
        root.addOrReplaceChild("part_013",
                CubeListBuilder.create().texOffs(36, 84)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 4.0F, 16.0F),
                PartPose.offset(4.0F, 26.0F, -8.0F));

        // Legacy part: box16
        root.addOrReplaceChild("part_014",
                CubeListBuilder.create().texOffs(193, 234)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 3.0F, 3.0F),
                PartPose.offset(-55.0F, 7.0F, 4.0F));

        // Legacy part: box17
        root.addOrReplaceChild("part_015",
                CubeListBuilder.create().texOffs(45, 58)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 3.0F, 20.0F),
                PartPose.offset(19.0F, 30.0F, -10.0F));

        // Legacy part: box18
        root.addOrReplaceChild("part_016",
                CubeListBuilder.create().texOffs(159, 191)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 7.0F, 2.0F),
                PartPose.offsetAndRotation(-41.0F, 12.0F, 7.0F, -0.506145483F, 0.0F, 0.0F));

        // Legacy part: box19
        root.addOrReplaceChild("part_017",
                CubeListBuilder.create().texOffs(63, 200)
                        .addBox(0.0F, 0.0F, 0.0F, 9.0F, 4.0F, 4.0F),
                PartPose.offsetAndRotation(-35.0F, 10.0F, -5.0F, 0.0F, 3.14159265F, 6.28318531F));

        // Legacy part: box2
        root.addOrReplaceChild("part_018",
                CubeListBuilder.create().texOffs(41, 35)
                        .addBox(0.0F, 0.0F, 0.0F, 23.0F, 1.0F, 14.0F),
                PartPose.offset(3.0F, 33.0F, -7.0F));

        // Legacy part: box20
        root.addOrReplaceChild("part_019",
                CubeListBuilder.create().texOffs(68, 173)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 12.0F, 1.0F),
                PartPose.offset(-26.0F, 3.0F, -9.0F));

        // Legacy part: box21
        root.addOrReplaceChild("part_020",
                CubeListBuilder.create().texOffs(0, 103)
                        .addBox(0.0F, 0.0F, 0.0F, 16.0F, 10.0F, 1.0F),
                PartPose.offset(4.0F, 14.0F, -11.0F));

        // Legacy part: box22
        root.addOrReplaceChild("part_021",
                CubeListBuilder.create().texOffs(103, 132)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 4.0F, 4.0F),
                PartPose.offset(-49.0F, 27.0F, -2.0F));

        // Legacy part: box23
        root.addOrReplaceChild("part_022",
                CubeListBuilder.create().texOffs(171, 228)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 5.0F, 18.0F),
                PartPose.offsetAndRotation(20.0F, 6.0F, 9.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box24
        root.addOrReplaceChild("part_023",
                CubeListBuilder.create().texOffs(88, 93)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 8.0F, 1.0F),
                PartPose.offsetAndRotation(18.0F, 24.0F, -11.0F, -6.12610567F, 0.0F, 0.0F));

        // Legacy part: box25
        root.addOrReplaceChild("part_024",
                CubeListBuilder.create().texOffs(98, 93)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 8.0F, 1.0F),
                PartPose.offsetAndRotation(12.0F, 24.0F, -11.0F, -6.12610567F, 0.0F, 0.0F));

        // Legacy part: box26
        root.addOrReplaceChild("part_025",
                CubeListBuilder.create().texOffs(0, 68)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 12.0F, 20.0F),
                PartPose.offset(4.0F, 14.0F, -10.0F));

        // Legacy part: box27
        root.addOrReplaceChild("part_026",
                CubeListBuilder.create().texOffs(96, 54)
                        .addBox(0.0F, 0.0F, 0.0F, 20.0F, 2.0F, 1.0F),
                PartPose.offsetAndRotation(-9.0F, 2.0F, -6.0F, 0.0F, 3.14159265F, 6.12610567F));

        // Legacy part: box28
        root.addOrReplaceChild("part_027",
                CubeListBuilder.create().texOffs(115, 102)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 8.0F, 1.0F),
                PartPose.offsetAndRotation(7.0F, 24.0F, 11.0F, -6.10865238F, -3.14159265F, 0.0F));

        // Legacy part: box29
        root.addOrReplaceChild("part_028",
                CubeListBuilder.create().texOffs(52, 50)
                        .addBox(0.0F, 0.0F, 0.0F, 30.0F, 2.0F, 1.0F),
                PartPose.offsetAndRotation(4.0F, 2.0F, 6.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box3
        root.addOrReplaceChild("part_029",
                CubeListBuilder.create().texOffs(139, 48)
                        .addBox(0.0F, 0.0F, 0.0F, 51.0F, 7.0F, 7.0F),
                PartPose.offsetAndRotation(4.0F, 27.0F, 2.0F, -5.49778714F, -3.14159265F, 0.0F));

        // Legacy part: box30
        root.addOrReplaceChild("part_030",
                CubeListBuilder.create().texOffs(96, 54)
                        .addBox(0.0F, 0.0F, 0.0F, 20.0F, 2.0F, 1.0F),
                PartPose.offsetAndRotation(-9.0F, 2.0F, 7.0F, 0.0F, 3.14159265F, 6.12610567F));

        // Legacy part: box31
        root.addOrReplaceChild("part_031",
                CubeListBuilder.create().texOffs(80, 93)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 8.0F, 1.0F),
                PartPose.offsetAndRotation(20.0F, 24.0F, 11.0F, -6.10865238F, -3.14159265F, 0.0F));

        // Legacy part: box32
        root.addOrReplaceChild("part_032",
                CubeListBuilder.create().texOffs(217, 201)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 6.0F, 8.0F),
                PartPose.offset(-29.0F, 28.0F, -4.0F));

        // Legacy part: box33
        root.addOrReplaceChild("part_033",
                CubeListBuilder.create().texOffs(142, 106)
                        .addBox(0.0F, 0.0F, 0.0F, 51.0F, 16.0F, 6.0F),
                PartPose.offset(-47.0F, 16.0F, -3.0F));

        // Legacy part: box34
        root.addOrReplaceChild("part_034",
                CubeListBuilder.create().texOffs(217, 201)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 6.0F, 8.0F),
                PartPose.offset(-19.0F, 28.0F, -4.0F));

        // Legacy part: box35
        root.addOrReplaceChild("part_035",
                CubeListBuilder.create().texOffs(142, 138)
                        .addBox(0.0F, 0.0F, 0.0F, 49.0F, 1.0F, 4.0F),
                PartPose.offset(-45.0F, 17.0F, 7.0F));

        // Legacy part: box36
        root.addOrReplaceChild("part_036",
                CubeListBuilder.create().texOffs(139, 48)
                        .addBox(0.0F, 0.0F, 0.0F, 51.0F, 7.0F, 7.0F),
                PartPose.offsetAndRotation(4.0F, 27.0F, 8.0F, -5.49778714F, -3.14159265F, 0.0F));

        // Legacy part: box37
        root.addOrReplaceChild("part_037",
                CubeListBuilder.create().texOffs(160, 91)
                        .addBox(0.0F, 0.0F, 0.0F, 41.0F, 7.0F, 7.0F),
                PartPose.offsetAndRotation(-6.0F, 21.0F, 8.0F, -5.49778714F, -3.14159265F, 0.0F));

        // Legacy part: box38
        root.addOrReplaceChild("part_038",
                CubeListBuilder.create().texOffs(160, 91)
                        .addBox(0.0F, 0.0F, 0.0F, 41.0F, 7.0F, 7.0F),
                PartPose.offsetAndRotation(-6.0F, 21.0F, 2.0F, -5.49778714F, -3.14159265F, 0.0F));

        // Legacy part: box39
        root.addOrReplaceChild("part_039",
                CubeListBuilder.create().texOffs(232, 225)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 25.0F, 6.0F),
                PartPose.offset(-42.0F, 10.0F, -3.0F));

        // Legacy part: box4
        root.addOrReplaceChild("part_040",
                CubeListBuilder.create().texOffs(104, 25)
                        .addBox(0.0F, 0.0F, 0.0F, 23.0F, 1.0F, 5.0F),
                PartPose.offsetAndRotation(3.0F, 34.0F, -7.0F, -3.75245789F, 0.0F, 0.0F));

        // Legacy part: box40
        root.addOrReplaceChild("part_041",
                CubeListBuilder.create().texOffs(141, 132)
                        .addBox(0.0F, 0.0F, 0.0F, 49.0F, 1.0F, 4.0F),
                PartPose.offset(-45.0F, 17.0F, -11.0F));

        // Legacy part: box41
        root.addOrReplaceChild("part_042",
                CubeListBuilder.create().texOffs(10, 63)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 16.0F, 3.0F),
                PartPose.offset(19.0F, 14.0F, 7.0F));

        // Legacy part: box42
        root.addOrReplaceChild("part_043",
                CubeListBuilder.create().texOffs(18, 10)
                        .addBox(0.0F, 0.0F, 0.0F, 16.0F, 2.0F, 22.0F),
                PartPose.offset(4.0F, 12.0F, -11.0F));

        // Legacy part: box43
        root.addOrReplaceChild("part_044",
                CubeListBuilder.create().texOffs(1, 63)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 16.0F, 3.0F),
                PartPose.offset(19.0F, 14.0F, -10.0F));

        // Legacy part: box44
        root.addOrReplaceChild("part_045",
                CubeListBuilder.create().texOffs(129, 2)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 1.0F, 18.0F),
                PartPose.offset(-52.0F, 10.0F, -9.0F));

        // Legacy part: box45
        root.addOrReplaceChild("part_046",
                CubeListBuilder.create().texOffs(106, 102)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 8.0F, 1.0F),
                PartPose.offsetAndRotation(4.0F, 24.0F, -11.0F, -6.12610567F, 0.0F, 0.0F));

        // Legacy part: box46
        root.addOrReplaceChild("part_047",
                CubeListBuilder.create().texOffs(75, 93)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 8.0F, 1.0F),
                PartPose.offsetAndRotation(13.0F, 24.0F, 11.0F, -6.10865238F, -3.14159265F, 0.0F));

        // Legacy part: box47
        root.addOrReplaceChild("part_048",
                CubeListBuilder.create().texOffs(102, 166)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 1.0F, 3.0F),
                PartPose.offset(-51.0F, 7.0F, 8.0F));

        // Legacy part: box48
        root.addOrReplaceChild("part_049",
                CubeListBuilder.create().texOffs(0, 243)
                        .addBox(0.0F, 0.0F, 0.0F, 71.0F, 13.0F, 0.0F, new CubeDeformation(0.02F)),
                PartPose.offsetAndRotation(21.0F, 0.0F, 5.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box49
        root.addOrReplaceChild("part_050",
                CubeListBuilder.create().texOffs(198, 180)
                        .addBox(0.0F, 0.0F, 0.0F, 10.0F, 5.0F, 16.0F),
                PartPose.offset(9.0F, 8.0F, -8.0F));

        // Legacy part: box5
        root.addOrReplaceChild("part_051",
                CubeListBuilder.create().texOffs(173, 191)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 1.0F),
                PartPose.offset(-50.0F, 4.0F, -9.0F));

        // Legacy part: box50
        root.addOrReplaceChild("part_052",
                CubeListBuilder.create().texOffs(89, 170)
                        .addBox(0.0F, 0.0F, 0.0F, 79.0F, 3.0F, 4.0F),
                PartPose.offset(-56.0F, 6.0F, -2.0F));

        // Legacy part: box51
        root.addOrReplaceChild("part_053",
                CubeListBuilder.create().texOffs(102, 166)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 1.0F, 3.0F),
                PartPose.offset(-51.0F, 4.0F, 8.0F));

        // Legacy part: box52
        root.addOrReplaceChild("part_054",
                CubeListBuilder.create().texOffs(194, 29)
                        .addBox(0.0F, 0.0F, 0.0F, 15.0F, 2.0F, 16.0F),
                PartPose.offsetAndRotation(-10.0F, 13.0F, -8.0F, 0.0F, 0.0F, -6.23082543F));

        // Legacy part: box53
        root.addOrReplaceChild("part_055",
                CubeListBuilder.create().texOffs(102, 166)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 1.0F, 3.0F),
                PartPose.offset(-51.0F, 7.0F, -11.0F));

        // Legacy part: box54
        root.addOrReplaceChild("part_056",
                CubeListBuilder.create().texOffs(173, 191)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 1.0F),
                PartPose.offset(-50.0F, 4.0F, 8.0F));

        // Legacy part: box55
        root.addOrReplaceChild("part_057",
                CubeListBuilder.create().texOffs(0, 243)
                        .addBox(0.0F, 0.0F, 0.0F, 71.0F, 13.0F, 0.0F, new CubeDeformation(0.02F)),
                PartPose.offsetAndRotation(21.0F, 0.0F, -5.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box57
        root.addOrReplaceChild("part_058",
                CubeListBuilder.create().texOffs(2, 51)
                        .addBox(0.0F, 2.0F, 0.0F, 21.0F, 2.0F, 1.0F),
                PartPose.offsetAndRotation(-27.0F, 3.0F, 8.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box6
        root.addOrReplaceChild("part_059",
                CubeListBuilder.create().texOffs(75, 145)
                        .addBox(0.0F, 0.0F, 0.0F, 70.0F, 8.0F, 8.0F),
                PartPose.offsetAndRotation(18.0F, 2.0F, 4.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box60
        root.addOrReplaceChild("part_060",
                CubeListBuilder.create().texOffs(196, 0)
                        .addBox(0.0F, 0.0F, 0.0F, 14.0F, 13.0F, 16.0F),
                PartPose.offsetAndRotation(4.0F, 14.0F, 8.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box67
        root.addOrReplaceChild("part_061",
                CubeListBuilder.create().texOffs(151, 184)
                        .addBox(0.0F, 0.0F, 0.0F, 7.0F, 7.0F, 20.0F),
                PartPose.offsetAndRotation(-36.0F, 3.0F, 10.0F, 0.0F, 3.14159265F, 6.28318531F));

        // Legacy part: box7
        root.addOrReplaceChild("part_062",
                CubeListBuilder.create().texOffs(194, 224)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 5.0F, 16.0F),
                PartPose.offset(-53.0F, 6.0F, -8.0F));

        // Legacy part: box73
        root.addOrReplaceChild("part_063",
                CubeListBuilder.create().texOffs(125, 177)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 13.0F, 10.0F),
                PartPose.offsetAndRotation(-49.0F, 9.0F, -5.0F, 0.0F, 0.0F, -0.610865238F));

        // Legacy part: box74
        root.addOrReplaceChild("part_064",
                CubeListBuilder.create().texOffs(175, 38)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 6.0F, 2.0F),
                PartPose.offset(-4.0F, 28.0F, -1.0F));

        // Legacy part: box76
        root.addOrReplaceChild("part_065",
                CubeListBuilder.create().texOffs(49, 110)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 2.0F, 20.0F),
                PartPose.offset(-26.0F, 15.0F, -10.0F));

        // Legacy part: box77
        root.addOrReplaceChild("part_066",
                CubeListBuilder.create().texOffs(159, 2)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 1.0F, 14.0F),
                PartPose.offset(-48.0F, 13.0F, -7.0F));

        // Legacy part: box8
        root.addOrReplaceChild("part_067",
                CubeListBuilder.create().texOffs(69, 69)
                        .addBox(0.0F, 0.0F, 0.0F, 17.0F, 7.0F, 1.0F),
                PartPose.offsetAndRotation(-35.0F, 21.0F, 11.0F, -6.19591884F, -3.14159265F, 0.0F));

        // Legacy part: box81
        root.addOrReplaceChild("part_068",
                CubeListBuilder.create().texOffs(102, 166)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 1.0F, 3.0F),
                PartPose.offset(-51.0F, 4.0F, -11.0F));

        // Legacy part: box82
        root.addOrReplaceChild("part_069",
                CubeListBuilder.create().texOffs(98, 197)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 4.0F, 8.0F),
                PartPose.offsetAndRotation(-32.0F, 14.0F, -4.0F, 0.0F, 0.0F, -0.785398163F));

        // Legacy part: box83
        root.addOrReplaceChild("part_070",
                CubeListBuilder.create().texOffs(78, 112)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 4.0F, 8.0F),
                PartPose.offsetAndRotation(-26.0F, 14.0F, -4.0F, 0.0F, 0.0F, -0.785398163F));

        // Legacy part: box85
        root.addOrReplaceChild("part_071",
                CubeListBuilder.create().texOffs(0, 140)
                        .addBox(-1.0F, 0.0F, 0.0F, 1.0F, 4.0F, 6.0F),
                PartPose.offsetAndRotation(-49.0F, 1.0F, -7.0F, 0.0F, -0.610865238F, -0.0698131701F));

        // Legacy part: box86
        root.addOrReplaceChild("part_072",
                CubeListBuilder.create().texOffs(0, 140)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 4.0F, 6.0F),
                PartPose.offsetAndRotation(-49.0F, 1.0F, 7.0F, 0.0F, -2.53072742F, -6.21337214F));

        // Legacy part: box9
        root.addOrReplaceChild("part_073",
                CubeListBuilder.create().texOffs(193, 234)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 3.0F, 3.0F),
                PartPose.offset(-55.0F, 7.0F, -7.0F));

        // Legacy part: box94
        root.addOrReplaceChild("part_074",
                CubeListBuilder.create().texOffs(116, 133)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 4.0F, 3.0F),
                PartPose.offset(-52.0F, 11.0F, -7.0F));

        // Legacy part: box95
        root.addOrReplaceChild("part_075",
                CubeListBuilder.create().texOffs(116, 133)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 4.0F, 3.0F),
                PartPose.offset(-52.0F, 11.0F, 4.0F));

        // Legacy part: box97
        root.addOrReplaceChild("part_076",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 15.0F, 14.0F),
                PartPose.offsetAndRotation(9.0F, 14.0F, 7.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box98
        root.addOrReplaceChild("part_077",
                CubeListBuilder.create().texOffs(0, 175)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 1.0F, 20.0F),
                PartPose.offset(-45.0F, 23.0F, -10.0F));

        // Legacy part: box99
        root.addOrReplaceChild("part_078",
                CubeListBuilder.create().texOffs(159, 191)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 7.0F, 2.0F),
                PartPose.offsetAndRotation(-38.0F, 12.0F, -7.0F, -0.506145483F, -3.14159265F, 0.0F));

        return LayerDefinition.create(mesh, 256, 256);
    }

    public static ModelPart root(ModelPart bakedRoot) {
        return bakedRoot;
    }
}
