/*
 * Traincraft ShaySteamLocomotiveModel for Minecraft 1.20.1.
 * Step 8.3.2-r1 mechanically recovered from production-era ModelLocoSteamShay.
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

public final class ShaySteamLocomotiveModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(Traincraft.MOD_ID, "locomotive_steam_shay"), "main");

    private ShaySteamLocomotiveModel() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Legacy part: boiler_blank_lower
        root.addOrReplaceChild("part_000",
                CubeListBuilder.create().texOffs(1, 180)
                        .addBox(0.0F, 0.0F, 0.0F, 24.0F, 4.0F, 4.0F),
                PartPose.offsetAndRotation(2.0F, 14.0F, -8.0F, -5.49778714F, 0.0F, 0.0F));

        // Legacy part: boiler_blank_upper
        root.addOrReplaceChild("part_001",
                CubeListBuilder.create().texOffs(1, 170)
                        .addBox(0.0F, 0.0F, 0.0F, 24.0F, 4.0F, 4.0F),
                PartPose.offsetAndRotation(2.0F, 18.0F, -8.0F, -5.49778714F, 0.0F, 0.0F));

        // Legacy part: boiler_piston_lower
        root.addOrReplaceChild("part_002",
                CubeListBuilder.create().texOffs(1, 150)
                        .addBox(0.0F, 0.0F, 0.0F, 24.0F, 4.0F, 4.0F),
                PartPose.offsetAndRotation(26.0F, 14.0F, 2.0F, -5.49778714F, -3.14159265F, 0.0F));

        // Legacy part: boiler_piston_upper
        root.addOrReplaceChild("part_003",
                CubeListBuilder.create().texOffs(1, 160)
                        .addBox(0.0F, 0.0F, 0.0F, 24.0F, 4.0F, 4.0F),
                PartPose.offsetAndRotation(26.0F, 18.0F, 2.0F, -5.49778714F, -3.14159265F, 0.0F));

        // Legacy part: box
        root.addOrReplaceChild("part_004",
                CubeListBuilder.create().texOffs(203, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 0.0F, new CubeDeformation(0.02F)),
                PartPose.offset(-24.0F, 0.0F, 5.0F));

        // Legacy part: box0
        root.addOrReplaceChild("part_005",
                CubeListBuilder.create().texOffs(3, 27)
                        .addBox(0.0F, 0.0F, 0.0F, 17.0F, 5.0F, 1.0F),
                PartPose.offset(-25.0F, 1.0F, -6.0F));

        // Legacy part: box1
        root.addOrReplaceChild("part_006",
                CubeListBuilder.create().texOffs(203, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 0.0F, new CubeDeformation(0.02F)),
                PartPose.offset(-15.0F, 0.0F, 5.0F));

        // Legacy part: box10
        root.addOrReplaceChild("part_007",
                CubeListBuilder.create().texOffs(28, 8)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 14.0F),
                PartPose.offset(-22.0F, 2.0F, -7.0F));

        // Legacy part: box100
        root.addOrReplaceChild("part_008",
                CubeListBuilder.create().texOffs(220, 128)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 2.0F),
                PartPose.offset(-14.0F, 6.0F, -11.0F));

        // Legacy part: box101
        root.addOrReplaceChild("part_009",
                CubeListBuilder.create().texOffs(220, 128)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 2.0F),
                PartPose.offset(-14.0F, 6.0F, 9.0F));

        // Legacy part: box104
        root.addOrReplaceChild("part_010",
                CubeListBuilder.create().texOffs(219, 229)
                        .addBox(0.0F, 0.0F, 0.0F, 0.0F, 4.0F, 18.0F, new CubeDeformation(0.02F)),
                PartPose.offset(28.0F, 2.0F, -9.0F));

        // Legacy part: box105
        root.addOrReplaceChild("part_011",
                CubeListBuilder.create().texOffs(219, 229)
                        .addBox(0.0F, 0.0F, 0.0F, 0.0F, 4.0F, 18.0F, new CubeDeformation(0.02F)),
                PartPose.offset(-28.0F, 2.0F, -9.0F));

        // Legacy part: box107
        root.addOrReplaceChild("part_012",
                CubeListBuilder.create().texOffs(196, 236)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 1.0F, 18.0F),
                PartPose.offset(-30.0F, 2.0F, -9.0F));

        // Legacy part: box11
        root.addOrReplaceChild("part_013",
                CubeListBuilder.create().texOffs(40, 27)
                        .addBox(0.0F, 0.0F, 0.0F, 17.0F, 5.0F, 1.0F),
                PartPose.offset(-25.0F, 1.0F, 5.0F));

        // Legacy part: box12
        root.addOrReplaceChild("part_014",
                CubeListBuilder.create().texOffs(3, 37)
                        .addBox(0.0F, 0.0F, 0.0F, 7.0F, 3.0F, 6.0F),
                PartPose.offset(13.0F, 4.0F, -3.0F));

        // Legacy part: box14
        root.addOrReplaceChild("part_015",
                CubeListBuilder.create().texOffs(48, 3)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 3.0F, 14.0F),
                PartPose.offset(-18.0F, 3.0F, -7.0F));

        // Legacy part: box15
        root.addOrReplaceChild("part_016",
                CubeListBuilder.create().texOffs(3, 13)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 2.0F, 10.0F),
                PartPose.offset(24.0F, 4.0F, -5.0F));

        // Legacy part: box16
        root.addOrReplaceChild("part_017",
                CubeListBuilder.create().texOffs(211, 209)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 5.0F, 20.0F),
                PartPose.offsetAndRotation(-26.0F, 6.0F, 10.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box17
        root.addOrReplaceChild("part_018",
                CubeListBuilder.create().texOffs(40, 27)
                        .addBox(0.0F, 0.0F, 0.0F, 17.0F, 5.0F, 1.0F),
                PartPose.offset(8.0F, 1.0F, 5.0F));

        // Legacy part: box18
        root.addOrReplaceChild("part_019",
                CubeListBuilder.create().texOffs(48, 3)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 3.0F, 14.0F),
                PartPose.offset(15.0F, 3.0F, -7.0F));

        // Legacy part: box19
        root.addOrReplaceChild("part_020",
                CubeListBuilder.create().texOffs(28, 8)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 14.0F),
                PartPose.offset(11.0F, 2.0F, -7.0F));

        // Legacy part: box2
        root.addOrReplaceChild("part_021",
                CubeListBuilder.create().texOffs(1, 238)
                        .addBox(0.0F, 0.0F, 0.0F, 52.0F, 3.0F, 14.0F),
                PartPose.offset(-26.0F, 7.0F, -9.0F));

        // Legacy part: box20
        root.addOrReplaceChild("part_022",
                CubeListBuilder.create().texOffs(3, 13)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 2.0F, 10.0F),
                PartPose.offset(-25.0F, 4.0F, -5.0F));

        // Legacy part: box21
        root.addOrReplaceChild("part_023",
                CubeListBuilder.create().texOffs(28, 8)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 14.0F),
                PartPose.offset(20.0F, 2.0F, -7.0F));

        // Legacy part: box22
        root.addOrReplaceChild("part_024",
                CubeListBuilder.create().texOffs(3, 27)
                        .addBox(0.0F, 0.0F, 0.0F, 17.0F, 5.0F, 1.0F),
                PartPose.offset(8.0F, 1.0F, -6.0F));

        // Legacy part: box23
        root.addOrReplaceChild("part_025",
                CubeListBuilder.create().texOffs(213, 152)
                        .addBox(14.0F, 0.0F, 0.0F, 1.0F, 11.0F, 20.0F),
                PartPose.offset(-13.0F, 11.0F, -10.0F));

        // Legacy part: box24
        root.addOrReplaceChild("part_026",
                CubeListBuilder.create().texOffs(188, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 0.0F, new CubeDeformation(0.02F)),
                PartPose.offset(9.0F, 0.0F, -5.0F));

        // Legacy part: box25
        root.addOrReplaceChild("part_027",
                CubeListBuilder.create().texOffs(188, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 0.0F, new CubeDeformation(0.02F)),
                PartPose.offset(18.0F, 0.0F, -5.0F));

        // Legacy part: box26
        root.addOrReplaceChild("part_028",
                CubeListBuilder.create().texOffs(203, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 0.0F, new CubeDeformation(0.02F)),
                PartPose.offset(18.0F, 0.0F, 5.0F));

        // Legacy part: box27
        root.addOrReplaceChild("part_029",
                CubeListBuilder.create().texOffs(203, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 0.0F, new CubeDeformation(0.02F)),
                PartPose.offset(9.0F, 0.0F, 5.0F));

        // Legacy part: box28
        root.addOrReplaceChild("part_030",
                CubeListBuilder.create().texOffs(196, 236)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 1.0F, 18.0F),
                PartPose.offset(28.0F, 2.0F, -9.0F));

        // Legacy part: box29
        root.addOrReplaceChild("part_031",
                CubeListBuilder.create().texOffs(213, 184)
                        .addBox(0.0F, -1.0F, 0.0F, 1.0F, 3.0F, 20.0F),
                PartPose.offset(-17.0F, 18.0F, -10.0F));

        // Legacy part: box3
        root.addOrReplaceChild("part_032",
                CubeListBuilder.create().texOffs(28, 8)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 14.0F),
                PartPose.offset(-13.0F, 2.0F, -7.0F));

        // Legacy part: box30
        root.addOrReplaceChild("part_033",
                CubeListBuilder.create().texOffs(1, 190)
                        .addBox(0.0F, 0.0F, 0.0F, 24.0F, 4.0F, 10.0F),
                PartPose.offset(2.0F, 14.0F, -8.0F));

        // Legacy part: box32
        root.addOrReplaceChild("part_034",
                CubeListBuilder.create().texOffs(3, 13)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 2.0F, 10.0F),
                PartPose.offset(8.0F, 4.0F, -5.0F));

        // Legacy part: box33
        root.addOrReplaceChild("part_035",
                CubeListBuilder.create().texOffs(155, 159)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 4.0F),
                PartPose.offset(-2.0F, 11.0F, 6.0F));

        // Legacy part: box34
        root.addOrReplaceChild("part_036",
                CubeListBuilder.create().texOffs(149, 224)
                        .addBox(0.0F, 0.0F, 0.0F, 11.0F, 6.0F, 20.0F),
                PartPose.offset(-26.0F, 11.0F, -10.0F));

        // Legacy part: box35
        root.addOrReplaceChild("part_037",
                CubeListBuilder.create().texOffs(238, 186)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 8.0F, 1.0F),
                PartPose.offset(-10.0F, 11.0F, 10.0F));

        // Legacy part: box36
        root.addOrReplaceChild("part_038",
                CubeListBuilder.create().texOffs(205, 185)
                        .addBox(0.0F, 0.0F, 0.0F, 12.0F, 10.0F, 1.0F),
                PartPose.offset(-10.0F, 11.0F, -11.0F));

        // Legacy part: box37
        root.addOrReplaceChild("part_039",
                CubeListBuilder.create().texOffs(129, 223)
                        .addBox(0.0F, 0.0F, 0.0F, 13.0F, 10.0F, 1.0F),
                PartPose.offset(-27.0F, 11.0F, -11.0F));

        // Legacy part: box39
        root.addOrReplaceChild("part_040",
                CubeListBuilder.create().texOffs(120, 239)
                        .addBox(0.0F, 0.0F, 0.0F, 13.0F, 10.0F, 1.0F),
                PartPose.offset(-27.0F, 11.0F, 10.0F));

        // Legacy part: box4
        root.addOrReplaceChild("part_041",
                CubeListBuilder.create().texOffs(3, 13)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 2.0F, 10.0F),
                PartPose.offset(-9.0F, 4.0F, -5.0F));

        // Legacy part: box40
        root.addOrReplaceChild("part_042",
                CubeListBuilder.create().texOffs(129, 189)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 10.0F, 20.0F),
                PartPose.offset(-27.0F, 11.0F, -10.0F));

        // Legacy part: box41
        root.addOrReplaceChild("part_043",
                CubeListBuilder.create().texOffs(193, 232)
                        .addBox(0.0F, -1.0F, 0.0F, 3.0F, 4.0F, 6.0F),
                PartPose.offset(-25.0F, 18.0F, -3.0F));

        // Legacy part: box42
        root.addOrReplaceChild("part_044",
                CubeListBuilder.create().texOffs(3, 50)
                        .addBox(0.0F, 0.0F, 0.0F, 18.0F, 2.0F, 6.0F),
                PartPose.offset(-15.0F, 31.0F, -3.0F));

        // Legacy part: box45
        root.addOrReplaceChild("part_045",
                CubeListBuilder.create().texOffs(213, 121)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 3.0F, 20.0F),
                PartPose.offset(1.0F, 28.0F, -10.0F));

        // Legacy part: box46
        root.addOrReplaceChild("part_046",
                CubeListBuilder.create().texOffs(213, 146)
                        .addBox(0.0F, 0.0F, 0.0F, 12.0F, 1.0F, 1.0F),
                PartPose.offset(-10.0F, 27.0F, 10.0F));

        // Legacy part: box48
        root.addOrReplaceChild("part_047",
                CubeListBuilder.create().texOffs(221, 163)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 6.0F, 1.0F),
                PartPose.offset(-2.0F, 21.0F, 10.0F));

        // Legacy part: box49
        root.addOrReplaceChild("part_048",
                CubeListBuilder.create().texOffs(214, 163)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 6.0F, 1.0F),
                PartPose.offset(-10.0F, 21.0F, 10.0F));

        // Legacy part: box50
        root.addOrReplaceChild("part_049",
                CubeListBuilder.create().texOffs(221, 154)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 6.0F, 1.0F),
                PartPose.offset(-2.0F, 21.0F, -11.0F));

        // Legacy part: box51
        root.addOrReplaceChild("part_050",
                CubeListBuilder.create().texOffs(214, 154)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 6.0F, 1.0F),
                PartPose.offset(-10.0F, 21.0F, -11.0F));

        // Legacy part: box52
        root.addOrReplaceChild("part_051",
                CubeListBuilder.create().texOffs(213, 149)
                        .addBox(0.0F, 0.0F, 0.0F, 12.0F, 1.0F, 1.0F),
                PartPose.offset(-10.0F, 27.0F, -11.0F));

        // Legacy part: box53
        root.addOrReplaceChild("part_052",
                CubeListBuilder.create().texOffs(246, 163)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 1.0F),
                PartPose.offset(1.0F, 22.0F, -10.0F));

        // Legacy part: box54
        root.addOrReplaceChild("part_053",
                CubeListBuilder.create().texOffs(246, 163)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 1.0F),
                PartPose.offset(1.0F, 22.0F, 9.0F));

        // Legacy part: box55
        root.addOrReplaceChild("part_054",
                CubeListBuilder.create().texOffs(236, 163)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 1.0F),
                PartPose.offset(1.0F, 22.0F, -5.0F));

        // Legacy part: box56
        root.addOrReplaceChild("part_055",
                CubeListBuilder.create().texOffs(236, 163)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 1.0F),
                PartPose.offset(1.0F, 22.0F, 4.0F));

        // Legacy part: box57
        root.addOrReplaceChild("part_056",
                CubeListBuilder.create().texOffs(237, 147)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 2.0F, 8.0F),
                PartPose.offset(1.0F, 22.0F, -4.0F));

        // Legacy part: box58
        root.addOrReplaceChild("part_057",
                CubeListBuilder.create().texOffs(1, 135)
                        .addBox(0.0F, 0.0F, 0.0F, 24.0F, 10.0F, 4.0F),
                PartPose.offset(2.0F, 11.0F, -5.0F));

        // Legacy part: box6
        root.addOrReplaceChild("part_058",
                CubeListBuilder.create().texOffs(211, 209)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 5.0F, 20.0F),
                PartPose.offset(26.0F, 6.0F, -10.0F));

        // Legacy part: box63
        root.addOrReplaceChild("part_059",
                CubeListBuilder.create().texOffs(188, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 0.0F, new CubeDeformation(0.02F)),
                PartPose.offset(-15.0F, 0.0F, -5.0F));

        // Legacy part: box64
        root.addOrReplaceChild("part_060",
                CubeListBuilder.create().texOffs(61, 186)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 6.0F),
                PartPose.offset(26.0F, 13.0F, -6.0F));

        // Legacy part: box65
        root.addOrReplaceChild("part_061",
                CubeListBuilder.create().texOffs(192, 165)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 8.0F, 8.0F),
                PartPose.offset(6.0F, 10.0F, 3.0F));

        // Legacy part: box66
        root.addOrReplaceChild("part_062",
                CubeListBuilder.create().texOffs(129, 171)
                        .addBox(0.0F, 0.0F, 0.0F, 12.0F, 1.0F, 8.0F),
                PartPose.offset(-5.0F, 18.0F, 3.0F));

        // Legacy part: box67
        root.addOrReplaceChild("part_063",
                CubeListBuilder.create().texOffs(172, 165)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 8.0F, 8.0F),
                PartPose.offset(-5.0F, 10.0F, 3.0F));

        // Legacy part: box68
        root.addOrReplaceChild("part_064",
                CubeListBuilder.create().texOffs(205, 197)
                        .addBox(0.0F, 0.0F, 0.0F, 12.0F, 2.0F, 1.0F),
                PartPose.offset(-10.0F, 19.0F, 10.0F));

        // Legacy part: box69
        root.addOrReplaceChild("part_065",
                CubeListBuilder.create().texOffs(1, 214)
                        .addBox(0.0F, 0.0F, 0.0F, 52.0F, 1.0F, 22.0F),
                PartPose.offset(-26.0F, 10.0F, -11.0F));

        // Legacy part: box7
        root.addOrReplaceChild("part_066",
                CubeListBuilder.create().texOffs(188, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 6.0F, 0.0F, new CubeDeformation(0.02F)),
                PartPose.offset(-24.0F, 0.0F, -5.0F));

        // Legacy part: box70
        root.addOrReplaceChild("part_067",
                CubeListBuilder.create().texOffs(130, 152)
                        .addBox(0.0F, 0.0F, 0.0F, 10.0F, 4.0F, 2.0F),
                PartPose.offset(-4.0F, 13.0F, 7.0F));

        // Legacy part: box71
        root.addOrReplaceChild("part_068",
                CubeListBuilder.create().texOffs(130, 160)
                        .addBox(0.0F, 0.0F, 0.0F, 10.0F, 8.0F, 2.0F),
                PartPose.offset(-4.0F, 10.0F, 3.0F));

        // Legacy part: box73
        root.addOrReplaceChild("part_069",
                CubeListBuilder.create().texOffs(77, 188)
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 14.0F, 4.0F),
                PartPose.offset(22.0F, 19.0F, -3.0F));

        // Legacy part: box74
        root.addOrReplaceChild("part_070",
                CubeListBuilder.create().texOffs(94, 187)
                        .addBox(0.0F, -1.0F, 0.0F, 3.0F, 4.0F, 4.0F),
                PartPose.offsetAndRotation(-26.0F, 21.0F, 2.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box75
        root.addOrReplaceChild("part_071",
                CubeListBuilder.create().texOffs(58, 173)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 4.0F, 6.0F),
                PartPose.offset(10.0F, 20.0F, -6.0F));

        // Legacy part: box76
        root.addOrReplaceChild("part_072",
                CubeListBuilder.create().texOffs(59, 163)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 4.0F, 4.0F),
                PartPose.offset(11.0F, 22.0F, -5.0F));

        // Legacy part: box77
        root.addOrReplaceChild("part_073",
                CubeListBuilder.create().texOffs(94, 197)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 4.0F, 4.0F),
                PartPose.offset(25.0F, 21.0F, -5.0F));

        // Legacy part: box79
        root.addOrReplaceChild("part_074",
                CubeListBuilder.create().texOffs(166, 151)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offset(2.0F, 7.0F, 7.0F));

        // Legacy part: box8
        root.addOrReplaceChild("part_075",
                CubeListBuilder.create().texOffs(3, 37)
                        .addBox(0.0F, 0.0F, 0.0F, 7.0F, 3.0F, 6.0F),
                PartPose.offset(-20.0F, 4.0F, -3.0F));

        // Legacy part: box80
        root.addOrReplaceChild("part_076",
                CubeListBuilder.create().texOffs(156, 151)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offset(-2.0F, 7.0F, 7.0F));

        // Legacy part: box81
        root.addOrReplaceChild("part_077",
                CubeListBuilder.create().texOffs(177, 153)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 4.0F),
                PartPose.offset(4.0F, 2.0F, 6.0F));

        // Legacy part: box82
        root.addOrReplaceChild("part_078",
                CubeListBuilder.create().texOffs(189, 153)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 4.0F),
                PartPose.offset(-3.0F, 2.0F, 6.0F));

        // Legacy part: box89
        root.addOrReplaceChild("part_079",
                CubeListBuilder.create().texOffs(16, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 4.0F, 4.0F),
                PartPose.offset(13.0F, 1.0F, 5.0F));

        // Legacy part: box9
        root.addOrReplaceChild("part_080",
                CubeListBuilder.create().texOffs(0, 208)
                        .addBox(0.0F, 0.0F, 0.0F, 60.0F, 3.0F, 2.0F),
                PartPose.offset(-30.0F, 6.0F, -1.0F));

        // Legacy part: box90
        root.addOrReplaceChild("part_081",
                CubeListBuilder.create().texOffs(16, 11)
                        .addBox(1.0F, 0.0F, 0.0F, 2.0F, 4.0F, 4.0F),
                PartPose.offset(21.0F, 1.0F, 5.0F));

        // Legacy part: box91
        root.addOrReplaceChild("part_082",
                CubeListBuilder.create().texOffs(16, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 4.0F, 4.0F),
                PartPose.offset(-11.0F, 1.0F, 5.0F));

        // Legacy part: box92
        root.addOrReplaceChild("part_083",
                CubeListBuilder.create().texOffs(16, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 4.0F, 4.0F),
                PartPose.offset(-20.0F, 1.0F, 5.0F));

        // Legacy part: box95
        root.addOrReplaceChild("part_084",
                CubeListBuilder.create().texOffs(110, 184)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 11.0F, 10.0F),
                PartPose.offset(-1.0F, 3.0F, -8.0F));

        // Legacy part: box96
        root.addOrReplaceChild("part_085",
                CubeListBuilder.create().texOffs(154, 185)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 11.0F, 10.0F),
                PartPose.offset(-2.0F, 11.0F, -8.0F));

        // Legacy part: box97
        root.addOrReplaceChild("part_086",
                CubeListBuilder.create().texOffs(84, 178)
                        .addBox(0.0F, 0.0F, 0.0F, 11.0F, 3.0F, 0.0F, new CubeDeformation(0.02F)),
                PartPose.offset(13.0F, 11.0F, 9.0F));

        // Legacy part: box98
        root.addOrReplaceChild("part_087",
                CubeListBuilder.create().texOffs(84, 178)
                        .addBox(0.0F, 0.0F, 0.0F, 11.0F, 3.0F, 0.0F, new CubeDeformation(0.02F)),
                PartPose.offset(13.0F, 11.0F, 5.0F));

        // Legacy part: brake_cyl
        root.addOrReplaceChild("part_088",
                CubeListBuilder.create().texOffs(84, 170)
                        .addBox(0.0F, 0.0F, 0.0F, 11.0F, 3.0F, 3.0F),
                PartPose.offsetAndRotation(24.0F, 14.0F, 9.0F, -5.49778714F, -3.14159265F, 0.0F));

        // Legacy part: coal_pile
        root.addOrReplaceChild("part_089",
                CubeListBuilder.create().texOffs(168, 201)
                        .addBox(-8.0F, 0.0F, 0.0F, 10.0F, 1.0F, 20.0F),
                PartPose.offsetAndRotation(-19.0F, 18.0F, -10.0F, 0.0F, 0.0F, -0.0872664626F));

        // Legacy part: crank_1
        root.addOrReplaceChild("part_090",
                CubeListBuilder.create().texOffs(189, 142)
                        .addBox(0.0F, -1.0F, 0.0F, 1.0F, 5.0F, 3.0F),
                PartPose.offsetAndRotation(3.0F, 1.0F, 8.0F, -0.785398163F, 0.0F, 0.0F));

        // Legacy part: crank_2
        root.addOrReplaceChild("part_091",
                CubeListBuilder.create().texOffs(189, 142)
                        .addBox(0.0F, -1.0F, 0.0F, 1.0F, 5.0F, 3.0F),
                PartPose.offsetAndRotation(1.0F, 1.0F, 8.0F, -0.785398163F, 0.0F, 0.0F));

        // Legacy part: crank_3
        root.addOrReplaceChild("part_092",
                CubeListBuilder.create().texOffs(175, 142)
                        .addBox(0.0F, -1.0F, 0.0F, 1.0F, 3.0F, 5.0F),
                PartPose.offsetAndRotation(0.0F, 1.0F, 7.0F, -0.785398163F, 0.0F, 0.0F));

        // Legacy part: crank_4
        root.addOrReplaceChild("part_093",
                CubeListBuilder.create().texOffs(175, 142)
                        .addBox(0.0F, -1.0F, 0.0F, 1.0F, 3.0F, 5.0F),
                PartPose.offsetAndRotation(-2.0F, 1.0F, 7.0F, -0.785398163F, 0.0F, 0.0F));

        // Legacy part: ladder_blank_side
        root.addOrReplaceChild("part_094",
                CubeListBuilder.create().texOffs(221, 121)
                        .addBox(0.0F, -4.0F, 0.0F, 4.0F, 5.0F, 1.0F),
                PartPose.offsetAndRotation(-14.0F, 10.0F, -9.0F, -6.17846555F, 0.0F, 0.0F));

        // Legacy part: ladder_piston_side
        root.addOrReplaceChild("part_095",
                CubeListBuilder.create().texOffs(221, 121)
                        .addBox(0.0F, -4.0F, 0.0F, 4.0F, 5.0F, 1.0F),
                PartPose.offsetAndRotation(-10.0F, 10.0F, 9.0F, -6.17846555F, -3.14159265F, 0.0F));

        // Legacy part: piston_front
        root.addOrReplaceChild("part_096",
                CubeListBuilder.create().texOffs(189, 164)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 1.0F),
                PartPose.offsetAndRotation(2.0F, 2.0F, 9.0F, -0.27925268F, 0.0F, 0.0F));

        // Legacy part: piston_rear
        root.addOrReplaceChild("part_097",
                CubeListBuilder.create().texOffs(184, 164)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, 2.0F, 7.0F, -0.27925268F, -3.14159265F, 0.0F));

        // Legacy part: rod_frnt_mid
        root.addOrReplaceChild("part_098",
                CubeListBuilder.create().texOffs(136, 147)
                        .addBox(0.0F, 0.0F, -1.0F, 9.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(5.0F, 3.0F, 9.0F, -0.785398163F, -6.17846555F, 0.0F));

        // Legacy part: rod_frnt_track
        root.addOrReplaceChild("part_099",
                CubeListBuilder.create().texOffs(3, 6)
                        .addBox(2.0F, 0.0F, -1.0F, 9.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(12.0F, 3.0F, 8.0F, -0.785398163F, 0.0F, 0.0F));

        // Legacy part: rod_rear_mid
        root.addOrReplaceChild("part_100",
                CubeListBuilder.create().texOffs(157, 147)
                        .addBox(-7.0F, 0.0F, -1.0F, 7.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(-3.0F, 3.0F, 9.0F, -0.785398163F, -0.157079633F, 0.0F));

        // Legacy part: rod_rear_truck
        root.addOrReplaceChild("part_101",
                CubeListBuilder.create().texOffs(3, 6)
                        .addBox(-31.0F, 0.0F, -1.0F, 9.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(12.0F, 3.0F, 8.0F, -0.785398163F, 0.0F, 0.0F));

        // Legacy part: roof_blank_lower
        root.addOrReplaceChild("part_102",
                CubeListBuilder.create().texOffs(4, 67)
                        .addBox(0.0F, -1.0F, 0.0F, 18.0F, 2.0F, 5.0F),
                PartPose.offsetAndRotation(-15.0F, 28.0F, -11.0F, -0.715584993F, 0.0F, 0.0F));

        // Legacy part: roof_blank_upper
        root.addOrReplaceChild("part_103",
                CubeListBuilder.create().texOffs(4, 59)
                        .addBox(0.0F, -2.0F, 0.0F, 18.0F, 2.0F, 5.0F),
                PartPose.offsetAndRotation(3.0F, 33.0F, -3.0F, -6.09119909F, -3.14159265F, 0.0F));

        // Legacy part: roof_piston_lower
        root.addOrReplaceChild("part_104",
                CubeListBuilder.create().texOffs(4, 67)
                        .addBox(0.0F, -1.0F, 0.0F, 18.0F, 2.0F, 5.0F),
                PartPose.offsetAndRotation(3.0F, 28.0F, 11.0F, -0.715584993F, -3.14159265F, 0.0F));

        // Legacy part: roof_piston_upper
        root.addOrReplaceChild("part_105",
                CubeListBuilder.create().texOffs(4, 59)
                        .addBox(0.0F, -2.0F, 0.0F, 18.0F, 2.0F, 5.0F),
                PartPose.offsetAndRotation(-15.0F, 33.0F, 3.0F, -6.09119909F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 256, 256);
    }

    public static ModelPart root(ModelPart bakedRoot) {
        return bakedRoot;
    }
}
