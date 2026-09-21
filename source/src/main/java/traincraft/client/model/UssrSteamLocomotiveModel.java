/*
 * Traincraft UssrSteamLocomotiveModel for Minecraft 1.20.1.
 * Recovered mechanically from Traincraft 4.4.1_020 CE 7.1 ModelLocoEr_Ussr.
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

public final class UssrSteamLocomotiveModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(Traincraft.MOD_ID, "locomotive_steam_ussr"), "main");

    private UssrSteamLocomotiveModel() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Legacy part: body
        root.addOrReplaceChild("part_000",
                CubeListBuilder.create().texOffs(134, 117)
                        .addBox(0.0F, 0.0F, 0.0F, 13.0F, 2.0F, 22.0F),
                PartPose.offset(4.0F, 12.0F, -11.0F));

        // Legacy part: box
        root.addOrReplaceChild("part_001",
                CubeListBuilder.create().texOffs(70, 69)
                        .addBox(0.0F, 0.0F, 0.0F, 0.0F, 20.0F, 22.0F),
                PartPose.offset(4.0F, 14.0F, -11.0F));

        // Legacy part: box0
        root.addOrReplaceChild("part_002",
                CubeListBuilder.create().texOffs(113, 240)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 3.0F, 4.0F),
                PartPose.offset(-54.0F, 5.0F, -2.0F));

        // Legacy part: box1
        root.addOrReplaceChild("part_003",
                CubeListBuilder.create().texOffs(137, 64)
                        .addBox(0.0F, 0.0F, 0.0F, 55.0F, 8.0F, 0.0F),
                PartPose.offset(-51.0F, 15.0F, 11.0F));

        // Legacy part: box10
        root.addOrReplaceChild("part_004",
                CubeListBuilder.create().texOffs(80, 76)
                        .addBox(0.0F, 0.0F, -5.0F, 20.0F, 1.0F, 5.0F),
                PartPose.offsetAndRotation(23.0F, 35.0F, -4.0F,
                        -2.94960644F, -3.14159265F, 0.0F));

        // Legacy part: box11
        root.addOrReplaceChild("part_005",
                CubeListBuilder.create().texOffs(137, 57)
                        .addBox(0.0F, 0.0F, 0.0F, 0.0F, 8.0F, 22.0F),
                PartPose.offset(-51.0F, 15.0F, -11.0F));

        // Legacy part: box12
        root.addOrReplaceChild("part_006",
                CubeListBuilder.create().texOffs(0, 187)
                        .addBox(0.0F, 0.0F, 0.0F, 26.0F, 13.0F, 1.0F),
                PartPose.offsetAndRotation(-13.0F, 1.0F, -8.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box13
        root.addOrReplaceChild("part_007",
                CubeListBuilder.create().texOffs(77, 237)
                        .addBox(0.0F, 0.0F, 0.0F, 7.0F, 11.0F, 5.0F),
                PartPose.offsetAndRotation(-39.0F, 3.0F, -6.0F,
                        0.0F, 3.14159265F, 6.28318531F));

        // Legacy part: box14
        root.addOrReplaceChild("part_008",
                CubeListBuilder.create().texOffs(48, 112)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 4.0F, 22.0F),
                PartPose.offsetAndRotation(22.0F, 30.0F, 11.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box15
        root.addOrReplaceChild("part_009",
                CubeListBuilder.create().texOffs(36, 56)
                        .addBox(0.0F, 0.0F, 0.0F, 0.0F, 16.0F, 14.0F),
                PartPose.offset(22.0F, 14.0F, -7.0F));

        // Legacy part: box16
        root.addOrReplaceChild("part_010",
                CubeListBuilder.create().texOffs(116, 249)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 3.0F, 3.0F),
                PartPose.offset(-51.0F, 10.0F, 3.0F));

        // Legacy part: box17
        root.addOrReplaceChild("part_011",
                CubeListBuilder.create().texOffs(81, 84)
                        .addBox(0.0F, 0.0F, -4.0F, 20.0F, 1.0F, 4.0F),
                PartPose.offsetAndRotation(3.0F, 34.0F, 9.0F,
                        -2.26892803F, 0.0F, 0.0F));

        // Legacy part: box18
        root.addOrReplaceChild("part_012",
                CubeListBuilder.create().texOffs(69, 187)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 11.0F, 12.0F),
                PartPose.offsetAndRotation(-40.0F, 9.0F, 6.0F,
                        0.0F, 3.14159265F, 6.28318531F));

        // Legacy part: box19
        root.addOrReplaceChild("part_013",
                CubeListBuilder.create().texOffs(61, 241)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 8.0F, 4.0F),
                PartPose.offsetAndRotation(-44.0F, 15.0F, -9.0F,
                        -6.02138592F, 0.0F, 0.0F));

        // Legacy part: box2
        root.addOrReplaceChild("part_014",
                CubeListBuilder.create().texOffs(77, 65)
                        .addBox(0.0F, 0.0F, 0.0F, 20.0F, 1.0F, 8.0F),
                PartPose.offset(3.0F, 34.0F, -4.0F));

        // Legacy part: box20
        root.addOrReplaceChild("part_015",
                CubeListBuilder.create().texOffs(33, 108)
                        .addBox(0.0F, 0.0F, 0.0F, 13.0F, 17.0F, 0.0F),
                PartPose.offset(4.0F, 14.0F, 11.0F));

        // Legacy part: box21
        root.addOrReplaceChild("part_016",
                CubeListBuilder.create().texOffs(4, 108)
                        .addBox(0.0F, 0.0F, 0.0F, 13.0F, 17.0F, 0.0F),
                PartPose.offset(4.0F, 14.0F, -11.0F));

        // Legacy part: box22
        root.addOrReplaceChild("part_017",
                CubeListBuilder.create().texOffs(104, 125)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 12.0F),
                PartPose.offset(-51.0F, 14.0F, -6.0F));

        // Legacy part: box23
        root.addOrReplaceChild("part_018",
                CubeListBuilder.create().texOffs(26, 154)
                        .addBox(0.0F, 0.0F, 0.0F, 54.0F, 9.0F, 0.0F),
                PartPose.offsetAndRotation(11.0F, 0.0F, -5.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box24
        root.addOrReplaceChild("part_019",
                CubeListBuilder.create().texOffs(163, 198)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 8.0F, 20.0F),
                PartPose.offsetAndRotation(-28.0F, 6.0F, 10.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box25
        root.addOrReplaceChild("part_020",
                CubeListBuilder.create().texOffs(36, 235)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 12.0F, 1.0F),
                PartPose.offsetAndRotation(-47.0F, 2.0F, -9.0F,
                        0.0F, 3.14159265F, 6.28318531F));

        // Legacy part: box26
        root.addOrReplaceChild("part_021",
                CubeListBuilder.create().texOffs(0, 206)
                        .addBox(0.0F, -1.0F, 0.0F, 4.0F, 1.0F, 3.0F),
                PartPose.offset(18.0F, 3.0F, -11.0F));

        // Legacy part: box27
        root.addOrReplaceChild("part_022",
                CubeListBuilder.create().texOffs(0, 206)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 3.0F),
                PartPose.offset(18.0F, 5.0F, -10.0F));

        // Legacy part: box28
        root.addOrReplaceChild("part_023",
                CubeListBuilder.create().texOffs(134, 3)
                        .addBox(0.0F, 0.0F, 0.0F, 40.0F, 6.0F, 6.0F),
                PartPose.offsetAndRotation(-7.0F, 20.0F, 7.0F,
                        -5.49778714F, -3.14159265F, 0.0F));

        // Legacy part: box29
        root.addOrReplaceChild("part_024",
                CubeListBuilder.create().texOffs(46, 235)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 12.0F, 1.0F),
                PartPose.offsetAndRotation(-51.0F, 2.0F, 9.0F,
                        0.0F, 0.0F, -6.28318531F));

        // Legacy part: box3
        root.addOrReplaceChild("part_025",
                CubeListBuilder.create().texOffs(134, 31)
                        .addBox(0.0F, 0.0F, 0.0F, 51.0F, 6.0F, 6.0F),
                PartPose.offsetAndRotation(-47.0F, 26.0F, -7.0F,
                        -5.49778714F, 0.0F, 0.0F));

        // Legacy part: box30
        root.addOrReplaceChild("part_026",
                CubeListBuilder.create().texOffs(0, 206)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 3.0F),
                PartPose.offset(18.0F, 11.0F, -8.0F));

        // Legacy part: box31
        root.addOrReplaceChild("part_027",
                CubeListBuilder.create().texOffs(0, 206)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 3.0F),
                PartPose.offset(18.0F, 2.0F, 8.0F));

        // Legacy part: box32
        root.addOrReplaceChild("part_028",
                CubeListBuilder.create().texOffs(61, 241)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 8.0F, 4.0F),
                PartPose.offsetAndRotation(-41.0F, 15.0F, 9.0F,
                        -6.02138592F, -3.14159265F, 0.0F));

        // Legacy part: box33
        root.addOrReplaceChild("part_029",
                CubeListBuilder.create().texOffs(8, 21)
                        .addBox(0.0F, 0.0F, 0.0F, 40.0F, 14.0F, 6.0F),
                PartPose.offset(-47.0F, 16.0F, -3.0F));

        // Legacy part: box34
        root.addOrReplaceChild("part_030",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(0.0F, 0.0F, 0.0F, 40.0F, 6.0F, 14.0F),
                PartPose.offset(-47.0F, 20.0F, -7.0F));

        // Legacy part: box35
        root.addOrReplaceChild("part_031",
                CubeListBuilder.create().texOffs(134, 149)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 4.0F, 4.0F),
                PartPose.offset(-50.0F, 28.0F, -2.0F));

        // Legacy part: box36
        root.addOrReplaceChild("part_032",
                CubeListBuilder.create().texOffs(134, 44)
                        .addBox(0.0F, 0.0F, 0.0F, 51.0F, 6.0F, 6.0F),
                PartPose.offsetAndRotation(4.0F, 26.0F, 7.0F,
                        -5.49778714F, -3.14159265F, 0.0F));

        // Legacy part: box37
        root.addOrReplaceChild("part_033",
                CubeListBuilder.create().texOffs(77, 237)
                        .addBox(0.0F, 0.0F, 0.0F, 7.0F, 11.0F, 5.0F),
                PartPose.offsetAndRotation(-39.0F, 3.0F, 11.0F,
                        0.0F, 3.14159265F, 6.28318531F));

        // Legacy part: box38
        root.addOrReplaceChild("part_034",
                CubeListBuilder.create().texOffs(134, 16)
                        .addBox(0.0F, 0.0F, 0.0F, 40.0F, 6.0F, 6.0F),
                PartPose.offsetAndRotation(-47.0F, 20.0F, -7.0F,
                        -5.49778714F, 0.0F, 0.0F));

        // Legacy part: box39
        root.addOrReplaceChild("part_035",
                CubeListBuilder.create().texOffs(134, 122)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 9.0F, 4.0F),
                PartPose.offset(-45.0F, 28.0F, -2.0F));

        // Legacy part: box4
        root.addOrReplaceChild("part_036",
                CubeListBuilder.create().texOffs(80, 76)
                        .addBox(0.0F, 0.0F, -5.0F, 20.0F, 1.0F, 5.0F),
                PartPose.offsetAndRotation(3.0F, 35.0F, 4.0F,
                        -2.94960644F, 0.0F, 0.0F));

        // Legacy part: box40
        root.addOrReplaceChild("part_037",
                CubeListBuilder.create().texOffs(134, 93)
                        .addBox(0.0F, 0.0F, 0.0F, 55.0F, 1.0F, 5.0F),
                PartPose.offset(-51.0F, 14.0F, -11.0F));

        // Legacy part: box41
        root.addOrReplaceChild("part_038",
                CubeListBuilder.create().texOffs(4, 68)
                        .addBox(0.0F, 0.0F, 0.0F, 0.0F, 16.0F, 22.0F),
                PartPose.offset(17.0F, 14.0F, -11.0F));

        // Legacy part: box42
        root.addOrReplaceChild("part_039",
                CubeListBuilder.create().texOffs(160, 149)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 1.0F, 14.0F),
                PartPose.offset(17.0F, 13.0F, -7.0F));

        // Legacy part: box43
        root.addOrReplaceChild("part_040",
                CubeListBuilder.create().texOffs(0, 206)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 3.0F),
                PartPose.offset(18.0F, 5.0F, 7.0F));

        // Legacy part: box44
        root.addOrReplaceChild("part_041",
                CubeListBuilder.create().texOffs(0, 173)
                        .addBox(0.0F, 0.0F, 0.0F, 26.0F, 13.0F, 1.0F),
                PartPose.offsetAndRotation(-13.0F, 1.0F, 9.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box45
        root.addOrReplaceChild("part_042",
                CubeListBuilder.create().texOffs(71, 230)
                        .addBox(0.0F, 0.0F, 0.0F, 16.0F, 2.0F, 1.0F),
                PartPose.offsetAndRotation(-31.0F, 4.0F, 9.0F,
                        -6.26573201F, -3.14159265F, 0.0F));

        // Legacy part: box46
        root.addOrReplaceChild("part_043",
                CubeListBuilder.create().texOffs(0, 206)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 3.0F),
                PartPose.offset(18.0F, 8.0F, -9.0F));

        // Legacy part: box47
        root.addOrReplaceChild("part_044",
                CubeListBuilder.create().texOffs(2, 51)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 12.0F),
                PartPose.offsetAndRotation(-6.0F, 3.0F, 6.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box48
        root.addOrReplaceChild("part_045",
                CubeListBuilder.create().texOffs(26, 164)
                        .addBox(0.0F, 0.0F, 0.0F, 54.0F, 9.0F, 0.0F),
                PartPose.offsetAndRotation(11.0F, 0.0F, 5.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box49
        root.addOrReplaceChild("part_046",
                CubeListBuilder.create().texOffs(71, 226)
                        .addBox(0.0F, 0.0F, 0.0F, 38.0F, 2.0F, 1.0F),
                PartPose.offsetAndRotation(5.0F, 1.0F, -6.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box5
        root.addOrReplaceChild("part_047",
                CubeListBuilder.create().texOffs(137, 64)
                        .addBox(0.0F, 0.0F, 0.0F, 55.0F, 8.0F, 0.0F),
                PartPose.offset(-51.0F, 15.0F, -11.0F));

        // Legacy part: box50
        root.addOrReplaceChild("part_048",
                CubeListBuilder.create().texOffs(107, 113)
                        .addBox(0.0F, 0.0F, 0.0F, 7.0F, 3.0F, 4.0F),
                PartPose.offset(15.0F, 6.0F, -2.0F));

        // Legacy part: box51
        root.addOrReplaceChild("part_049",
                CubeListBuilder.create().texOffs(27, 230)
                        .addBox(0.0F, 0.0F, 0.0F, 20.0F, 2.0F, 1.0F),
                PartPose.offsetAndRotation(-13.0F, 1.0F, -7.0F,
                        0.0F, 3.14159265F, 6.12610567F));

        // Legacy part: box52
        root.addOrReplaceChild("part_050",
                CubeListBuilder.create().texOffs(191, 141)
                        .addBox(0.0F, 0.0F, 0.0F, 18.0F, 3.0F, 14.0F),
                PartPose.offsetAndRotation(-7.0F, 10.0F, -7.0F,
                        0.0F, 0.0F, -6.24827872F));

        // Legacy part: box53
        root.addOrReplaceChild("part_051",
                CubeListBuilder.create().texOffs(2, 51)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 12.0F),
                PartPose.offsetAndRotation(-24.0F, 3.0F, 6.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box54
        root.addOrReplaceChild("part_052",
                CubeListBuilder.create().texOffs(0, 206)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 3.0F),
                PartPose.offset(18.0F, 8.0F, 6.0F));

        // Legacy part: box55
        root.addOrReplaceChild("part_053",
                CubeListBuilder.create().texOffs(210, 224)
                        .addBox(0.0F, 0.0F, 0.0F, 0.0F, 5.0F, 20.0F),
                PartPose.offsetAndRotation(-8.0F, 9.0F, 10.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box56
        root.addOrReplaceChild("part_054",
                CubeListBuilder.create().texOffs(2, 51)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 12.0F),
                PartPose.offsetAndRotation(3.0F, 3.0F, 6.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box57
        root.addOrReplaceChild("part_055",
                CubeListBuilder.create().texOffs(71, 230)
                        .addBox(0.0F, 0.0F, 0.0F, 16.0F, 2.0F, 1.0F),
                PartPose.offsetAndRotation(-31.0F, 4.0F, -8.0F,
                        -0.0174532925F, -3.14159265F, 0.0F));

        // Legacy part: box58
        root.addOrReplaceChild("part_056",
                CubeListBuilder.create().texOffs(150, 145)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 6.0F, 6.0F),
                PartPose.offset(-48.0F, 18.0F, -3.0F));

        // Legacy part: box59
        root.addOrReplaceChild("part_057",
                CubeListBuilder.create().texOffs(109, 198)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 7.0F, 8.0F),
                PartPose.offset(-20.0F, 28.0F, -4.0F));

        // Legacy part: box6
        root.addOrReplaceChild("part_058",
                CubeListBuilder.create().texOffs(100, 174)
                        .addBox(0.0F, 0.0F, 0.0F, 70.0F, 6.0F, 8.0F),
                PartPose.offsetAndRotation(19.0F, 4.0F, 4.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box60
        root.addOrReplaceChild("part_059",
                CubeListBuilder.create().texOffs(197, 105)
                        .addBox(0.0F, 0.0F, 0.0F, 14.0F, 17.0F, 14.0F),
                PartPose.offsetAndRotation(7.0F, 13.0F, 7.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box61
        root.addOrReplaceChild("part_060",
                CubeListBuilder.create().texOffs(174, 239)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 12.0F, 1.0F),
                PartPose.offsetAndRotation(22.0F, 2.0F, -8.0F,
                        -0.314159265F, -3.14159265F, 0.0F));

        // Legacy part: box62
        root.addOrReplaceChild("part_061",
                CubeListBuilder.create().texOffs(174, 239)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 12.0F, 1.0F),
                PartPose.offsetAndRotation(18.0F, 2.0F, 8.0F,
                        -0.314159265F, 0.0F, 0.0F));

        // Legacy part: box63
        root.addOrReplaceChild("part_062",
                CubeListBuilder.create().texOffs(0, 206)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 3.0F),
                PartPose.offset(18.0F, 11.0F, 5.0F));

        // Legacy part: box64
        root.addOrReplaceChild("part_063",
                CubeListBuilder.create().texOffs(2, 51)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 12.0F),
                PartPose.offsetAndRotation(-15.0F, 3.0F, 6.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box65
        root.addOrReplaceChild("part_064",
                CubeListBuilder.create().texOffs(134, 93)
                        .addBox(0.0F, 0.0F, 0.0F, 55.0F, 1.0F, 5.0F),
                PartPose.offset(-51.0F, 14.0F, 6.0F));

        // Legacy part: box66
        root.addOrReplaceChild("part_065",
                CubeListBuilder.create().texOffs(2, 51)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 12.0F),
                PartPose.offsetAndRotation(-33.0F, 3.0F, 6.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box67
        root.addOrReplaceChild("part_066",
                CubeListBuilder.create().texOffs(212, 197)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 5.0F, 10.0F),
                PartPose.offsetAndRotation(19.0F, 8.0F, 5.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box68
        root.addOrReplaceChild("part_067",
                CubeListBuilder.create().texOffs(187, 212)
                        .addBox(0.0F, 0.0F, 0.0F, 0.0F, 3.0F, 20.0F),
                PartPose.offsetAndRotation(4.0F, 9.0F, 10.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box69
        root.addOrReplaceChild("part_068",
                CubeListBuilder.create().texOffs(11, 71)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 16.0F, 0.0F),
                PartPose.offset(17.0F, 14.0F, 7.0F));

        // Legacy part: box7
        root.addOrReplaceChild("part_069",
                CubeListBuilder.create().texOffs(12, 204)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 5.0F, 16.0F),
                PartPose.offset(-52.0F, 5.0F, -8.0F));

        // Legacy part: box70
        root.addOrReplaceChild("part_070",
                CubeListBuilder.create().texOffs(54, 89)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 16.0F, 0.0F),
                PartPose.offset(17.0F, 14.0F, -7.0F));

        // Legacy part: box71
        root.addOrReplaceChild("part_071",
                CubeListBuilder.create().texOffs(71, 226)
                        .addBox(0.0F, 0.0F, 0.0F, 38.0F, 2.0F, 1.0F),
                PartPose.offsetAndRotation(5.0F, 1.0F, 7.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box72
        root.addOrReplaceChild("part_072",
                CubeListBuilder.create().texOffs(27, 230)
                        .addBox(0.0F, 0.0F, 0.0F, 20.0F, 2.0F, 1.0F),
                PartPose.offsetAndRotation(-13.0F, 1.0F, 8.0F,
                        0.0F, 3.14159265F, 6.12610567F));

        // Legacy part: box73
        root.addOrReplaceChild("part_073",
                CubeListBuilder.create().texOffs(187, 212)
                        .addBox(0.0F, 0.0F, 0.0F, 0.0F, 3.0F, 20.0F),
                PartPose.offsetAndRotation(17.0F, 9.0F, 10.0F,
                        0.0F, -3.14159265F, 0.0F));

        // Legacy part: box74
        root.addOrReplaceChild("part_074",
                CubeListBuilder.create().texOffs(154, 105)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 6.0F, 2.0F),
                PartPose.offset(-5.0F, 28.0F, -1.0F));

        // Legacy part: box75
        root.addOrReplaceChild("part_075",
                CubeListBuilder.create().texOffs(83, 212)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 6.0F),
                PartPose.offset(-27.0F, 29.0F, -3.0F));

        // Legacy part: box76
        root.addOrReplaceChild("part_076",
                CubeListBuilder.create().texOffs(83, 212)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 6.0F),
                PartPose.offset(-36.0F, 29.0F, -3.0F));

        // Legacy part: box8
        root.addOrReplaceChild("part_077",
                CubeListBuilder.create().texOffs(81, 84)
                        .addBox(0.0F, 0.0F, -4.0F, 20.0F, 1.0F, 4.0F),
                PartPose.offsetAndRotation(23.0F, 34.0F, -9.0F,
                        -2.26892803F, -3.14159265F, 0.0F));

        // Legacy part: box83
        root.addOrReplaceChild("part_078",
                CubeListBuilder.create().texOffs(136, 233)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 5.0F, 13.0F),
                PartPose.offsetAndRotation(-25.0F, 10.0F, 3.0F,
                        0.0F, 1.57079633F, -0.785398163F));

        // Legacy part: box9
        root.addOrReplaceChild("part_079",
                CubeListBuilder.create().texOffs(116, 249)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 3.0F, 3.0F),
                PartPose.offset(-51.0F, 10.0F, -6.0F));

        return LayerDefinition.create(mesh, 256, 256);
    }

    public static ModelPart root(ModelPart bakedRoot) {
        return bakedRoot;
    }
}
