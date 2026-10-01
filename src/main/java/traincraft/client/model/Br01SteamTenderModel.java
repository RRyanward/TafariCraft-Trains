/*
 * Traincraft Br01SteamTenderModel for Minecraft 1.20.1.
 * Step 8.3.0 mechanically recovered from production-era ModelTenderBR01_DB.
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

public final class Br01SteamTenderModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(Traincraft.MOD_ID, "steam_tender_br01"), "main");

    private Br01SteamTenderModel() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Legacy part: box
        root.addOrReplaceChild("part_000",
                CubeListBuilder.create().texOffs(98, 93)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 7.0F, 1.0F),
                PartPose.offsetAndRotation(9.0F, 25.0F, 8.0F, -6.14355897F, -3.14159265F, 0.0F));

        // Legacy part: box0
        root.addOrReplaceChild("part_001",
                CubeListBuilder.create().texOffs(44, 36)
                        .addBox(0.0F, 0.0F, 0.0F, 37.0F, 14.0F, 5.0F),
                PartPose.offsetAndRotation(20.0F, 9.0F, 11.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box11
        root.addOrReplaceChild("part_002",
                CubeListBuilder.create().texOffs(1, 18)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 15.0F, 1.0F),
                PartPose.offset(-19.0F, 9.0F, -9.0F));

        // Legacy part: box12
        root.addOrReplaceChild("part_003",
                CubeListBuilder.create().texOffs(44, 36)
                        .addBox(0.0F, 0.0F, 0.0F, 37.0F, 14.0F, 5.0F),
                PartPose.offsetAndRotation(20.0F, 9.0F, -6.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box13
        root.addOrReplaceChild("part_004",
                CubeListBuilder.create().texOffs(0, 77)
                        .addBox(0.0F, 0.0F, 0.0F, 32.0F, 3.0F, 5.0F),
                PartPose.offsetAndRotation(15.0F, 23.0F, -6.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box14
        root.addOrReplaceChild("part_005",
                CubeListBuilder.create().texOffs(82, 57)
                        .addBox(0.0F, 0.0F, 0.0F, 11.0F, 17.0F, 12.0F),
                PartPose.offsetAndRotation(20.0F, 9.0F, 6.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box15
        root.addOrReplaceChild("part_006",
                CubeListBuilder.create().texOffs(45, 60)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 6.0F, 12.0F),
                PartPose.offsetAndRotation(22.0F, 11.0F, 6.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box16
        root.addOrReplaceChild("part_007",
                CubeListBuilder.create().texOffs(118, 114)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 3.0F, 3.0F),
                PartPose.offset(23.0F, 7.0F, 4.0F));

        // Legacy part: box17
        root.addOrReplaceChild("part_008",
                CubeListBuilder.create().texOffs(106, 90)
                        .addBox(0.0F, 0.0F, 1.0F, 1.0F, 9.0F, 1.0F),
                PartPose.offset(-20.0F, 4.0F, 6.0F));

        // Legacy part: box18
        root.addOrReplaceChild("part_009",
                CubeListBuilder.create().texOffs(59, 114)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 4.0F),
                PartPose.offset(-23.0F, 4.0F, 7.0F));

        // Legacy part: box19
        root.addOrReplaceChild("part_010",
                CubeListBuilder.create().texOffs(59, 114)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 4.0F),
                PartPose.offset(-23.0F, 4.0F, -11.0F));

        // Legacy part: box20
        root.addOrReplaceChild("part_011",
                CubeListBuilder.create().texOffs(46, 110)
                        .addBox(0.0F, 0.0F, 1.0F, 4.0F, 1.0F, 3.0F),
                PartPose.offset(-23.0F, 10.0F, 6.0F));

        // Legacy part: box22
        root.addOrReplaceChild("part_012",
                CubeListBuilder.create().texOffs(98, 93)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 7.0F, 1.0F),
                PartPose.offsetAndRotation(4.0F, 25.0F, 8.0F, -6.14355897F, -3.14159265F, 0.0F));

        // Legacy part: box23
        root.addOrReplaceChild("part_013",
                CubeListBuilder.create().texOffs(38, 92)
                        .addBox(0.0F, 0.0F, 0.0F, 25.0F, 6.0F, 1.0F),
                PartPose.offsetAndRotation(10.0F, 26.0F, -6.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box25
        root.addOrReplaceChild("part_014",
                CubeListBuilder.create().texOffs(98, 93)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 7.0F, 1.0F),
                PartPose.offsetAndRotation(-12.0F, 25.0F, -8.0F, -6.14355897F, 0.0F, 0.0F));

        // Legacy part: box26
        root.addOrReplaceChild("part_015",
                CubeListBuilder.create().texOffs(38, 92)
                        .addBox(0.0F, 0.0F, 0.0F, 25.0F, 6.0F, 1.0F),
                PartPose.offsetAndRotation(10.0F, 26.0F, 7.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box27
        root.addOrReplaceChild("part_016",
                CubeListBuilder.create().texOffs(46, 110)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 3.0F),
                PartPose.offset(-23.0F, 7.0F, 7.0F));

        // Legacy part: box29
        root.addOrReplaceChild("part_017",
                CubeListBuilder.create().texOffs(18, 40)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 16.0F),
                PartPose.offset(9.0F, 3.0F, -8.0F));

        // Legacy part: box3
        root.addOrReplaceChild("part_018",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(0.0F, 0.0F, 0.0F, 11.0F, 1.0F, 16.0F),
                PartPose.offset(-23.0F, 13.0F, -8.0F));

        // Legacy part: box30
        root.addOrReplaceChild("part_019",
                CubeListBuilder.create().texOffs(18, 40)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 16.0F),
                PartPose.offset(-14.0F, 3.0F, -8.0F));

        // Legacy part: box32
        root.addOrReplaceChild("part_020",
                CubeListBuilder.create().texOffs(102, 0)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 6.0F, 12.0F),
                PartPose.offsetAndRotation(10.0F, 26.0F, 6.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box33
        root.addOrReplaceChild("part_021",
                CubeListBuilder.create().texOffs(69, 59)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 8.0F, 1.0F),
                PartPose.offsetAndRotation(-15.0F, 32.0F, 7.0F, 0.0F, 3.14159265F, 2.91469985F));

        // Legacy part: box34
        root.addOrReplaceChild("part_022",
                CubeListBuilder.create().texOffs(3, 47)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 16.0F, 12.0F),
                PartPose.offsetAndRotation(-13.0F, 14.0F, 6.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box35
        root.addOrReplaceChild("part_023",
                CubeListBuilder.create().texOffs(69, 59)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 8.0F, 1.0F),
                PartPose.offsetAndRotation(-15.0F, 32.0F, -6.0F, 0.0F, 3.14159265F, 2.91469985F));

        // Legacy part: box36
        root.addOrReplaceChild("part_024",
                CubeListBuilder.create().texOffs(1, 18)
                        .addBox(0.0F, 0.0F, 19.0F, 2.0F, 15.0F, 1.0F),
                PartPose.offset(-19.0F, 9.0F, -11.0F));

        // Legacy part: box37
        root.addOrReplaceChild("part_025",
                CubeListBuilder.create().texOffs(0, 111)
                        .addBox(0.0F, 0.0F, 0.0F, 18.0F, 7.0F, 10.0F),
                PartPose.offsetAndRotation(-3.0F, 0.0F, 5.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box38
        root.addOrReplaceChild("part_026",
                CubeListBuilder.create().texOffs(0, 111)
                        .addBox(0.0F, 0.0F, 0.0F, 18.0F, 7.0F, 10.0F),
                PartPose.offsetAndRotation(20.0F, 0.0F, 5.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box39
        root.addOrReplaceChild("part_027",
                CubeListBuilder.create().texOffs(1, 85)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 14.0F),
                PartPose.offsetAndRotation(-6.0F, 2.0F, 7.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box47
        root.addOrReplaceChild("part_028",
                CubeListBuilder.create().texOffs(1, 85)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 14.0F),
                PartPose.offsetAndRotation(-16.0F, 2.0F, 7.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box48
        root.addOrReplaceChild("part_029",
                CubeListBuilder.create().texOffs(1, 85)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 14.0F),
                PartPose.offsetAndRotation(17.0F, 2.0F, 7.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box49
        root.addOrReplaceChild("part_030",
                CubeListBuilder.create().texOffs(98, 93)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 7.0F, 1.0F),
                PartPose.offsetAndRotation(-1.0F, 25.0F, 8.0F, -6.14355897F, -3.14159265F, 0.0F));

        // Legacy part: box5
        root.addOrReplaceChild("part_031",
                CubeListBuilder.create().texOffs(106, 90)
                        .addBox(0.0F, 0.0F, 1.0F, 1.0F, 9.0F, 1.0F),
                PartPose.offset(-23.0F, 4.0F, 6.0F));

        // Legacy part: box50
        root.addOrReplaceChild("part_032",
                CubeListBuilder.create().texOffs(0, 102)
                        .addBox(0.0F, 0.0F, 0.0F, 50.0F, 3.0F, 4.0F),
                PartPose.offset(-24.0F, 6.0F, -2.0F));

        // Legacy part: box51
        root.addOrReplaceChild("part_033",
                CubeListBuilder.create().texOffs(1, 85)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 14.0F),
                PartPose.offsetAndRotation(7.0F, 2.0F, 7.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box52
        root.addOrReplaceChild("part_034",
                CubeListBuilder.create().texOffs(118, 114)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 3.0F, 3.0F),
                PartPose.offset(23.0F, 7.0F, -7.0F));

        // Legacy part: box53
        root.addOrReplaceChild("part_035",
                CubeListBuilder.create().texOffs(98, 93)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 7.0F, 1.0F),
                PartPose.offsetAndRotation(-2.0F, 25.0F, -8.0F, -6.14355897F, 0.0F, 0.0F));

        // Legacy part: box54
        root.addOrReplaceChild("part_036",
                CubeListBuilder.create().texOffs(98, 93)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 7.0F, 1.0F),
                PartPose.offsetAndRotation(3.0F, 25.0F, -8.0F, -6.14355897F, 0.0F, 0.0F));

        // Legacy part: box55
        root.addOrReplaceChild("part_037",
                CubeListBuilder.create().texOffs(98, 93)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 7.0F, 1.0F),
                PartPose.offsetAndRotation(8.0F, 25.0F, -8.0F, -6.14355897F, 0.0F, 0.0F));

        // Legacy part: box56
        root.addOrReplaceChild("part_038",
                CubeListBuilder.create().texOffs(96, 69)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 5.0F, 12.0F),
                PartPose.offsetAndRotation(-14.0F, 11.0F, 6.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box58
        root.addOrReplaceChild("part_039",
                CubeListBuilder.create().texOffs(98, 93)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 7.0F, 1.0F),
                PartPose.offsetAndRotation(-6.0F, 25.0F, 8.0F, -6.14355897F, -3.14159265F, 0.0F));

        // Legacy part: box59
        root.addOrReplaceChild("part_040",
                CubeListBuilder.create().texOffs(98, 93)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 7.0F, 1.0F),
                PartPose.offsetAndRotation(-11.0F, 25.0F, 8.0F, -6.14355897F, -3.14159265F, 0.0F));

        // Legacy part: box6
        root.addOrReplaceChild("part_041",
                CubeListBuilder.create().texOffs(10, 17)
                        .addBox(0.0F, 0.0F, 0.0F, 45.0F, 5.0F, 14.0F),
                PartPose.offsetAndRotation(22.0F, 6.0F, 7.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box60
        root.addOrReplaceChild("part_042",
                CubeListBuilder.create().texOffs(118, 87)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 18.0F, 4.0F),
                PartPose.offsetAndRotation(21.0F, 4.0F, 10.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box61
        root.addOrReplaceChild("part_043",
                CubeListBuilder.create().texOffs(118, 87)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 18.0F, 4.0F),
                PartPose.offsetAndRotation(21.0F, 4.0F, -6.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box62
        root.addOrReplaceChild("part_044",
                CubeListBuilder.create().texOffs(22, 88)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 3.0F, 3.0F),
                PartPose.offsetAndRotation(19.0F, 21.0F, 10.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box63
        root.addOrReplaceChild("part_045",
                CubeListBuilder.create().texOffs(22, 88)
                        .addBox(0.0F, 0.0F, 0.0F, 3.0F, 3.0F, 3.0F),
                PartPose.offsetAndRotation(19.0F, 21.0F, -7.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box64
        root.addOrReplaceChild("part_046",
                CubeListBuilder.create().texOffs(2, 4)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 8.0F, 1.0F),
                PartPose.offset(-24.0F, 15.0F, -9.0F));

        // Legacy part: box65
        root.addOrReplaceChild("part_047",
                CubeListBuilder.create().texOffs(2, 4)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 8.0F, 1.0F),
                PartPose.offset(-24.0F, 15.0F, 8.0F));

        // Legacy part: box66
        root.addOrReplaceChild("part_048",
                CubeListBuilder.create().texOffs(68, 110)
                        .addBox(0.0F, 0.0F, 0.0F, 18.0F, 5.0F, 12.0F),
                PartPose.offsetAndRotation(-3.0F, 1.0F, 6.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box67
        root.addOrReplaceChild("part_049",
                CubeListBuilder.create().texOffs(68, 110)
                        .addBox(0.0F, 0.0F, 0.0F, 18.0F, 5.0F, 12.0F),
                PartPose.offsetAndRotation(20.0F, 1.0F, 6.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box68
        root.addOrReplaceChild("part_050",
                CubeListBuilder.create().texOffs(56, 2)
                        .addBox(0.0F, 0.0F, 0.0F, 22.0F, 1.0F, 12.0F),
                PartPose.offsetAndRotation(-13.0F, 27.0F, -6.0F, 0.0F, 0.0F, -6.23082543F));

        // Legacy part: box69
        root.addOrReplaceChild("part_051",
                CubeListBuilder.create().texOffs(46, 110)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 3.0F),
                PartPose.offset(-23.0F, 7.0F, -10.0F));

        // Legacy part: box7
        root.addOrReplaceChild("part_052",
                CubeListBuilder.create().texOffs(62, 71)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 5.0F, 16.0F),
                PartPose.offsetAndRotation(23.0F, 6.0F, 8.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box70
        root.addOrReplaceChild("part_053",
                CubeListBuilder.create().texOffs(46, 110)
                        .addBox(0.0F, 0.0F, 1.0F, 4.0F, 1.0F, 3.0F),
                PartPose.offset(-23.0F, 10.0F, -11.0F));

        // Legacy part: box71
        root.addOrReplaceChild("part_054",
                CubeListBuilder.create().texOffs(106, 90)
                        .addBox(3.0F, 0.0F, 1.0F, 1.0F, 9.0F, 1.0F),
                PartPose.offset(-23.0F, 4.0F, -9.0F));

        // Legacy part: box8
        root.addOrReplaceChild("part_055",
                CubeListBuilder.create().texOffs(0, 77)
                        .addBox(0.0F, 0.0F, 0.0F, 32.0F, 3.0F, 5.0F),
                PartPose.offsetAndRotation(15.0F, 23.0F, 11.0F, 0.0F, -3.14159265F, 0.0F));

        // Legacy part: box81
        root.addOrReplaceChild("part_056",
                CubeListBuilder.create().texOffs(106, 90)
                        .addBox(0.0F, 0.0F, 1.0F, 1.0F, 9.0F, 1.0F),
                PartPose.offset(-23.0F, 4.0F, -9.0F));

        // Legacy part: box9
        root.addOrReplaceChild("part_057",
                CubeListBuilder.create().texOffs(98, 93)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 7.0F, 1.0F),
                PartPose.offsetAndRotation(-7.0F, 25.0F, -8.0F, -6.14355897F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    public static ModelPart root(ModelPart bakedRoot) {
        return bakedRoot;
    }
}
