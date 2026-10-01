/*
 * Traincraft ForneySteamLocomotiveModel for Minecraft 1.20.1.
 * Step 8.3.0 mechanically recovered from production-era ModelLocoForney.
 * Geometry, UV origins, pivots and rotations are preserved exactly.
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

public final class ForneySteamLocomotiveModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(Traincraft.MOD_ID, "locomotive_steam_forney"), "main");

    private ForneySteamLocomotiveModel() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Legacy part: bogey
        root.addOrReplaceChild("part_000",
                CubeListBuilder.create().texOffs(19, 75)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 6.0F),
                PartPose.offset(-6.0F, 0.0F, -22.0F));

        // Legacy part: bogey0
        root.addOrReplaceChild("part_001",
                CubeListBuilder.create().texOffs(19, 75)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 6.0F),
                PartPose.offset(5.0F, 0.0F, -22.0F));

        // Legacy part: box
        root.addOrReplaceChild("part_002",
                CubeListBuilder.create().texOffs(121, 57)
                        .addBox(0.0F, 0.0F, 0.0F, 16.0F, 8.0F, 1.0F),
                PartPose.offset(-8.0F, 1.0F, -24.0F));

        // Legacy part: box0
        root.addOrReplaceChild("part_003",
                CubeListBuilder.create().texOffs(94, 45)
                        .addBox(0.0F, 0.0F, 0.0F, 10.0F, 2.0F, 5.0F),
                PartPose.offset(-5.0F, 7.0F, -23.0F));

        // Legacy part: box1
        root.addOrReplaceChild("part_004",
                CubeListBuilder.create().texOffs(135, 67)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 6.0F, 0.0F),
                PartPose.offsetAndRotation(-1.0F, 2.0F, -27.0F, -5.68977336F, 0.0F, 0.0F));

        // Legacy part: box10
        root.addOrReplaceChild("part_005",
                CubeListBuilder.create().texOffs(132, 67)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 5.0F, 0.0F),
                PartPose.offsetAndRotation(-2.0F, 2.0F, -26.0F, -5.86430629F, 0.0F, 0.0F));

        // Legacy part: box11
        root.addOrReplaceChild("part_006",
                CubeListBuilder.create().texOffs(115, 75)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 8.0F),
                PartPose.offsetAndRotation(-7.0F, 1.0F, -24.0F, 0.0F, -4.41568301F, 0.0F));

        // Legacy part: box12
        root.addOrReplaceChild("part_007",
                CubeListBuilder.create().texOffs(127, 77)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 8.0F),
                PartPose.offsetAndRotation(8.0F, 1.0F, -25.0F, 0.0F, -1.85004901F, 0.0F));

        // Legacy part: box13
        root.addOrReplaceChild("part_008",
                CubeListBuilder.create().texOffs(126, 45)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 4.0F, 7.0F),
                PartPose.offset(5.0F, 6.0F, -19.0F));

        // Legacy part: box14
        root.addOrReplaceChild("part_009",
                CubeListBuilder.create().texOffs(136, 20)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 5.0F),
                PartPose.offset(6.0F, 10.0F, -18.0F));

        // Legacy part: box15
        root.addOrReplaceChild("part_010",
                CubeListBuilder.create().texOffs(178, 142)
                        .addBox(0.0F, 0.0F, 0.0F, 10.0F, 12.0F, 12.0F),
                PartPose.offset(-5.0F, 10.0F, -10.0F));

        // Legacy part: box16
        root.addOrReplaceChild("part_011",
                CubeListBuilder.create().texOffs(93, 34)
                        .addBox(0.0F, 0.0F, 0.0F, 10.0F, 5.0F, 5.0F),
                PartPose.offset(-5.0F, 4.0F, -18.0F));

        // Legacy part: box17
        root.addOrReplaceChild("part_012",
                CubeListBuilder.create().texOffs(105, 25)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 2.0F, 5.0F),
                PartPose.offset(-3.0F, 9.0F, -18.0F));

        // Legacy part: box18
        root.addOrReplaceChild("part_013",
                CubeListBuilder.create().texOffs(157, 10)
                        .addBox(0.0F, 0.0F, 0.0F, 10.0F, 10.0F, 8.0F),
                PartPose.offset(-5.0F, 11.0F, -18.0F));

        // Legacy part: box19
        root.addOrReplaceChild("part_014",
                CubeListBuilder.create().texOffs(102, 1)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 11.0F),
                PartPose.offset(-8.0F, 11.0F, -12.0F));

        // Legacy part: box2
        root.addOrReplaceChild("part_015",
                CubeListBuilder.create().texOffs(129, 67)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 0.0F),
                PartPose.offsetAndRotation(-4.0F, 1.0F, -26.0F, -5.93411946F, 0.0F, 0.0F));

        // Legacy part: box20
        root.addOrReplaceChild("part_016",
                CubeListBuilder.create().texOffs(103, 13)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 11.0F),
                PartPose.offset(4.0F, 11.0F, -12.0F));

        // Legacy part: box21
        root.addOrReplaceChild("part_017",
                CubeListBuilder.create().texOffs(205, 2)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 8.0F, 16.0F),
                PartPose.offset(-4.0F, 3.0F, -7.0F));

        // Legacy part: box22
        root.addOrReplaceChild("part_018",
                CubeListBuilder.create().texOffs(11, 91)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 22.0F),
                PartPose.offset(-5.0F, 4.0F, -13.0F));

        // Legacy part: box23
        root.addOrReplaceChild("part_019",
                CubeListBuilder.create().texOffs(11, 91)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 22.0F),
                PartPose.offset(4.0F, 4.0F, -13.0F));

        // Legacy part: box24
        root.addOrReplaceChild("part_020",
                CubeListBuilder.create().texOffs(42, 79)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 9.0F, 9.0F),
                PartPose.offset(-6.0F, 0.0F, -10.0F));

        // Legacy part: box25
        root.addOrReplaceChild("part_021",
                CubeListBuilder.create().texOffs(42, 79)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 9.0F, 9.0F),
                PartPose.offset(-6.0F, 0.0F, 1.0F));

        // Legacy part: box26
        root.addOrReplaceChild("part_022",
                CubeListBuilder.create().texOffs(12, 101)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 9.0F),
                PartPose.offsetAndRotation(4.0F, 4.0F, 9.0F, -0.994837674F, 0.0F, 0.0F));

        // Legacy part: box27
        root.addOrReplaceChild("part_023",
                CubeListBuilder.create().texOffs(4, 175)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 9.0F, 13.0F),
                PartPose.offset(-8.0F, 13.0F, 19.0F));

        // Legacy part: box28
        root.addOrReplaceChild("part_024",
                CubeListBuilder.create().texOffs(37, 117)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 9.0F, 9.0F),
                PartPose.offset(5.0F, 0.0F, 1.0F));

        // Legacy part: box29
        root.addOrReplaceChild("part_025",
                CubeListBuilder.create().texOffs(37, 117)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 9.0F, 9.0F),
                PartPose.offset(5.0F, 0.0F, -10.0F));

        // Legacy part: box3
        root.addOrReplaceChild("part_026",
                CubeListBuilder.create().texOffs(126, 67)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 0.0F),
                PartPose.offsetAndRotation(-6.0F, 1.0F, -25.0F, -6.09119909F, 0.0F, 0.0F));

        // Legacy part: box30
        root.addOrReplaceChild("part_027",
                CubeListBuilder.create().texOffs(8, 28)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 7.0F, 16.0F),
                PartPose.offset(-9.0F, 13.0F, 0.0F));

        // Legacy part: box31
        root.addOrReplaceChild("part_028",
                CubeListBuilder.create().texOffs(43, 28)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 7.0F, 16.0F),
                PartPose.offset(8.0F, 13.0F, 0.0F));

        // Legacy part: box32
        root.addOrReplaceChild("part_029",
                CubeListBuilder.create().texOffs(106, 80)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 10.0F, 2.0F),
                PartPose.offset(8.0F, 20.0F, 0.0F));

        // Legacy part: box33
        root.addOrReplaceChild("part_030",
                CubeListBuilder.create().texOffs(96, 80)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 10.0F, 2.0F),
                PartPose.offset(8.0F, 20.0F, 7.0F));

        // Legacy part: box34
        root.addOrReplaceChild("part_031",
                CubeListBuilder.create().texOffs(86, 80)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 10.0F, 2.0F),
                PartPose.offset(8.0F, 20.0F, 14.0F));

        // Legacy part: box35
        root.addOrReplaceChild("part_032",
                CubeListBuilder.create().texOffs(148, 94)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 10.0F, 2.0F),
                PartPose.offset(-9.0F, 20.0F, 0.0F));

        // Legacy part: box36
        root.addOrReplaceChild("part_033",
                CubeListBuilder.create().texOffs(138, 94)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 10.0F, 2.0F),
                PartPose.offset(-9.0F, 20.0F, 7.0F));

        // Legacy part: box37
        root.addOrReplaceChild("part_034",
                CubeListBuilder.create().texOffs(128, 94)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 10.0F, 2.0F),
                PartPose.offset(-9.0F, 20.0F, 14.0F));

        // Legacy part: box38
        root.addOrReplaceChild("part_035",
                CubeListBuilder.create().texOffs(89, 115)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 10.0F, 1.0F),
                PartPose.offset(-8.0F, 13.0F, 0.0F));

        // Legacy part: box39
        root.addOrReplaceChild("part_036",
                CubeListBuilder.create().texOffs(111, 100)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 4.0F, 1.0F),
                PartPose.offset(4.0F, 26.0F, 0.0F));

        // Legacy part: box4
        root.addOrReplaceChild("part_037",
                CubeListBuilder.create().texOffs(121, 66)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 5.0F, 1.0F),
                PartPose.offsetAndRotation(-8.0F, 1.0F, -25.0F, -6.0737458F, 0.0F, 0.0F));

        // Legacy part: box40
        root.addOrReplaceChild("part_038",
                CubeListBuilder.create().texOffs(114, 115)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 10.0F, 1.0F),
                PartPose.offset(5.0F, 13.0F, 0.0F));

        // Legacy part: box41
        root.addOrReplaceChild("part_039",
                CubeListBuilder.create().texOffs(94, 107)
                        .addBox(0.0F, 0.0F, 0.0F, 10.0F, 5.0F, 1.0F),
                PartPose.offset(-5.0F, 21.0F, 0.0F));

        // Legacy part: box42
        root.addOrReplaceChild("part_040",
                CubeListBuilder.create().texOffs(95, 100)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 4.0F, 1.0F),
                PartPose.offset(-5.0F, 26.0F, 0.0F));

        // Legacy part: box43
        root.addOrReplaceChild("part_041",
                CubeListBuilder.create().texOffs(36, 54)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 21.0F),
                PartPose.offset(8.0F, 30.0F, 0.0F));

        // Legacy part: box44
        root.addOrReplaceChild("part_042",
                CubeListBuilder.create().texOffs(102, 100)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 4.0F, 1.0F),
                PartPose.offset(-1.0F, 26.0F, 0.0F));

        // Legacy part: box45
        root.addOrReplaceChild("part_043",
                CubeListBuilder.create().texOffs(59, 52)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 21.0F),
                PartPose.offset(-9.0F, 30.0F, 0.0F));

        // Legacy part: box46
        root.addOrReplaceChild("part_044",
                CubeListBuilder.create().texOffs(89, 97)
                        .addBox(0.0F, 0.0F, 0.0F, 16.0F, 1.0F, 1.0F),
                PartPose.offset(-8.0F, 30.0F, 0.0F));

        // Legacy part: box47
        root.addOrReplaceChild("part_045",
                CubeListBuilder.create().texOffs(90, 94)
                        .addBox(0.0F, 0.0F, 0.0F, 14.0F, 1.0F, 1.0F),
                PartPose.offset(-7.0F, 31.0F, 0.0F));

        // Legacy part: box48
        root.addOrReplaceChild("part_046",
                CubeListBuilder.create().texOffs(37, 115)
                        .addBox(0.0F, 0.0F, 0.0F, 10.0F, 1.0F, 23.0F),
                PartPose.offsetAndRotation(0.0F, 32.0F, -1.0F, 0.0F, 0.0F, -0.13962634F));

        // Legacy part: box49
        root.addOrReplaceChild("part_047",
                CubeListBuilder.create().texOffs(103, 115)
                        .addBox(0.0F, 0.0F, 0.0F, 10.0F, 1.0F, 23.0F),
                PartPose.offsetAndRotation(0.0F, 32.0F, 22.0F, 0.0F, 3.14159265F, 0.13962634F));

        // Legacy part: box5
        root.addOrReplaceChild("part_048",
                CubeListBuilder.create().texOffs(33, 174)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 9.0F, 13.0F),
                PartPose.offset(7.0F, 13.0F, 19.0F));

        // Legacy part: box50
        root.addOrReplaceChild("part_049",
                CubeListBuilder.create().texOffs(161, 66)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 8.0F, 1.0F),
                PartPose.offset(-4.0F, 12.0F, -19.0F));

        // Legacy part: box51
        root.addOrReplaceChild("part_050",
                CubeListBuilder.create().texOffs(179, 118)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 5.0F, 6.0F),
                PartPose.offset(-3.0F, 21.0F, -17.0F));

        // Legacy part: box52
        root.addOrReplaceChild("part_051",
                CubeListBuilder.create().texOffs(183, 104)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 10.0F, 4.0F),
                PartPose.offset(-2.0F, 26.0F, -16.0F));

        // Legacy part: box53
        root.addOrReplaceChild("part_052",
                CubeListBuilder.create().texOffs(8, 141)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 14.0F),
                PartPose.offset(5.0F, 0.0F, 15.0F));

        // Legacy part: box54
        root.addOrReplaceChild("part_053",
                CubeListBuilder.create().texOffs(8, 141)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 14.0F),
                PartPose.offset(-6.0F, 0.0F, 15.0F));

        // Legacy part: box55
        root.addOrReplaceChild("part_054",
                CubeListBuilder.create().texOffs(209, 114)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 6.0F),
                PartPose.offset(-3.0F, 21.0F, -8.0F));

        // Legacy part: box56
        root.addOrReplaceChild("part_055",
                CubeListBuilder.create().texOffs(41, 143)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 14.0F),
                PartPose.offset(6.0F, 0.0F, 15.0F));

        // Legacy part: box57
        root.addOrReplaceChild("part_056",
                CubeListBuilder.create().texOffs(69, 10)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 7.0F, 5.0F),
                PartPose.offset(-3.0F, 22.0F, -24.0F));

        // Legacy part: box58
        root.addOrReplaceChild("part_057",
                CubeListBuilder.create().texOffs(40, 14)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 5.0F),
                PartPose.offset(-2.0F, 21.0F, -22.0F));

        // Legacy part: box59
        root.addOrReplaceChild("part_058",
                CubeListBuilder.create().texOffs(61, 3)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 3.0F, 3.0F),
                PartPose.offset(-2.0F, 29.0F, -23.0F));

        // Legacy part: box6
        root.addOrReplaceChild("part_059",
                CubeListBuilder.create().texOffs(148, 67)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 0.0F),
                PartPose.offsetAndRotation(5.0F, 1.0F, -25.0F, -6.09119909F, 0.0F, 0.0F));

        // Legacy part: box60
        root.addOrReplaceChild("part_060",
                CubeListBuilder.create().texOffs(81, 4)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 4.0F, 1.0F),
                PartPose.offset(-2.0F, 24.0F, -25.0F));

        // Legacy part: box61
        root.addOrReplaceChild("part_061",
                CubeListBuilder.create().texOffs(212, 105)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 3.0F, 4.0F),
                PartPose.offset(-2.0F, 27.0F, -7.0F));

        // Legacy part: box62
        root.addOrReplaceChild("part_062",
                CubeListBuilder.create().texOffs(0, 83)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 14.0F),
                PartPose.offsetAndRotation(2.0F, 8.0F, -14.0F, -6.09119909F, 0.0F, 0.0F));

        // Legacy part: box63
        root.addOrReplaceChild("part_063",
                CubeListBuilder.create().texOffs(0, 83)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 14.0F),
                PartPose.offsetAndRotation(-3.0F, 8.0F, -14.0F, -6.14355897F, 0.0F, 0.0F));

        // Legacy part: box64
        root.addOrReplaceChild("part_064",
                CubeListBuilder.create().texOffs(38, 100)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 10.0F),
                PartPose.offsetAndRotation(6.0F, 8.0F, -14.0F, -5.88175958F, 0.0F, 0.0F));

        // Legacy part: box65
        root.addOrReplaceChild("part_065",
                CubeListBuilder.create().texOffs(61, 97)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 13.0F),
                PartPose.offset(6.0F, 4.0F, -5.0F));

        // Legacy part: box66
        root.addOrReplaceChild("part_066",
                CubeListBuilder.create().texOffs(38, 100)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 10.0F),
                PartPose.offsetAndRotation(-7.0F, 7.0F, -14.0F, -6.17846555F, 0.0F, 0.0F));

        // Legacy part: box67
        root.addOrReplaceChild("part_067",
                CubeListBuilder.create().texOffs(61, 97)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 13.0F),
                PartPose.offset(-7.0F, 6.0F, -5.0F));

        // Legacy part: box68
        root.addOrReplaceChild("part_068",
                CubeListBuilder.create().texOffs(12, 122)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 2.0F, 7.0F),
                PartPose.offset(-2.0F, 7.0F, 30.0F));

        // Legacy part: box69
        root.addOrReplaceChild("part_069",
                CubeListBuilder.create().texOffs(90, 56)
                        .addBox(0.0F, 0.0F, 0.0F, 12.0F, 5.0F, 1.0F),
                PartPose.offset(-6.0F, 6.0F, 31.0F));

        // Legacy part: box7
        root.addOrReplaceChild("part_070",
                CubeListBuilder.create().texOffs(151, 66)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 5.0F, 1.0F),
                PartPose.offsetAndRotation(7.0F, 1.0F, -25.0F, -6.0737458F, 0.0F, 0.0F));

        // Legacy part: box70
        root.addOrReplaceChild("part_071",
                CubeListBuilder.create().texOffs(89, 64)
                        .addBox(0.0F, 0.0F, 0.0F, 14.0F, 1.0F, 1.0F),
                PartPose.offset(-7.0F, 5.0F, 31.0F));

        // Legacy part: box71
        root.addOrReplaceChild("part_072",
                CubeListBuilder.create().texOffs(41, 143)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 14.0F),
                PartPose.offset(-7.0F, 0.0F, 15.0F));

        // Legacy part: box72
        root.addOrReplaceChild("part_073",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(0.0F, 0.0F, 0.0F, 14.0F, 1.0F, 1.0F),
                PartPose.offset(-7.0F, 5.0F, 14.0F));

        // Legacy part: box73
        root.addOrReplaceChild("part_074",
                CubeListBuilder.create().texOffs(4, 197)
                        .addBox(0.0F, 0.0F, 0.0F, 14.0F, 9.0F, 1.0F),
                PartPose.offset(-7.0F, 13.0F, 31.0F));

        // Legacy part: box74
        root.addOrReplaceChild("part_075",
                CubeListBuilder.create().texOffs(136, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 5.0F),
                PartPose.offset(-8.0F, 10.0F, -18.0F));

        // Legacy part: box75
        root.addOrReplaceChild("part_076",
                CubeListBuilder.create().texOffs(40, 7)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 4.0F, 1.0F),
                PartPose.offset(-2.0F, 14.0F, -20.0F));

        // Legacy part: box76
        root.addOrReplaceChild("part_077",
                CubeListBuilder.create().texOffs(41, 12)
                        .addBox(0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 11.0F),
                PartPose.offsetAndRotation(6.0F, 7.0F, -23.0F, -0.942477796F, -0.174532925F, 0.0F));

        // Legacy part: box77
        root.addOrReplaceChild("part_078",
                CubeListBuilder.create().texOffs(41, 13)
                        .addBox(0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 11.0F),
                PartPose.offsetAndRotation(-6.0F, 7.0F, -23.0F, -0.942477796F, -6.09119909F, 0.0F));

        // Legacy part: box78
        root.addOrReplaceChild("part_079",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(0.0F, 0.0F, 0.0F, 14.0F, 1.0F, 1.0F),
                PartPose.offset(-7.0F, 5.0F, 29.0F));

        // Legacy part: box79
        root.addOrReplaceChild("part_080",
                CubeListBuilder.create().texOffs(0, 2)
                        .addBox(0.0F, 0.0F, 0.0F, 12.0F, 2.0F, 2.0F),
                PartPose.offset(-6.0F, 4.0F, 21.0F));

        // Legacy part: box8
        root.addOrReplaceChild("part_081",
                CubeListBuilder.create().texOffs(127, 31)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 4.0F, 7.0F),
                PartPose.offset(-9.0F, 6.0F, -19.0F));

        // Legacy part: box80
        root.addOrReplaceChild("part_082",
                CubeListBuilder.create().texOffs(0, 16)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 5.0F, 4.0F),
                PartPose.offset(-2.0F, 6.0F, 20.0F));

        // Legacy part: box81
        root.addOrReplaceChild("part_083",
                CubeListBuilder.create().texOffs(64, 152)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 4.0F, 22.0F),
                PartPose.offset(3.0F, 7.0F, 9.0F));

        // Legacy part: box82
        root.addOrReplaceChild("part_084",
                CubeListBuilder.create().texOffs(64, 152)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 4.0F, 22.0F),
                PartPose.offset(-4.0F, 7.0F, 9.0F));

        // Legacy part: box83
        root.addOrReplaceChild("part_085",
                CubeListBuilder.create().texOffs(0, 6)
                        .addBox(0.0F, 0.0F, 0.0F, 10.0F, 2.0F, 2.0F),
                PartPose.offset(-5.0F, 2.0F, -20.0F));

        // Legacy part: box84
        root.addOrReplaceChild("part_086",
                CubeListBuilder.create().texOffs(144, 67)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 0.0F),
                PartPose.offsetAndRotation(3.0F, 1.0F, -26.0F, -5.93411946F, 0.0F, 0.0F));

        // Legacy part: box85
        root.addOrReplaceChild("part_087",
                CubeListBuilder.create().texOffs(160, 50)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 6.0F),
                PartPose.offset(0.0F, 13.0F, 24.0F));

        // Legacy part: box86
        root.addOrReplaceChild("part_088",
                CubeListBuilder.create().texOffs(188, 50)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 6.0F),
                PartPose.offset(-4.0F, 17.0F, 21.0F));

        // Legacy part: box87
        root.addOrReplaceChild("part_089",
                CubeListBuilder.create().texOffs(160, 35)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 6.0F),
                PartPose.offset(-7.0F, 14.0F, 25.0F));

        // Legacy part: box88
        root.addOrReplaceChild("part_090",
                CubeListBuilder.create().texOffs(188, 50)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 6.0F),
                PartPose.offset(-6.0F, 12.0F, 20.0F));

        // Legacy part: box89
        root.addOrReplaceChild("part_091",
                CubeListBuilder.create().texOffs(23, 18)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 3.0F, 3.0F),
                PartPose.offset(-2.0F, 4.0F, -21.0F));

        // Legacy part: box9
        root.addOrReplaceChild("part_092",
                CubeListBuilder.create().texOffs(141, 67)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 5.0F, 0.0F),
                PartPose.offsetAndRotation(1.0F, 2.0F, -26.0F, -5.86430629F, 0.0F, 0.0F));

        // Legacy part: frame
        root.addOrReplaceChild("part_093",
                CubeListBuilder.create().texOffs(89, 178)
                        .addBox(0.0F, 0.0F, 0.0F, 18.0F, 2.0F, 35.0F),
                PartPose.offset(-9.0F, 11.0F, -1.0F));

        return LayerDefinition.create(mesh, 256, 256);
    }

    public static ModelPart root(ModelPart bakedRoot) {
        return bakedRoot;
    }
}
