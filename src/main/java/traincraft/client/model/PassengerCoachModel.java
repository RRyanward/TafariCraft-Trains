/* Traincraft classic Passenger Blue coach model converted from 1.7.10 ModelPassenger6. */
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

/**
 * Box-for-box geometry port of Traincraft 1.7.10 ModelPassenger6.
 * The original dynamic ModelLights helper is intentionally excluded here;
 * the classic textured coach body and running gear are preserved.
 */
public final class PassengerCoachModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(Traincraft.MOD_ID, "passenger_coach_blue"), "main");

    private PassengerCoachModel() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("box",
                CubeListBuilder.create().texOffs(158, 245)
                        .addBox(0.0F, 0.0F, 0.0F, 10.0F, 4.0F, 4.0F),
                PartPose.offsetAndRotation(
                        -9.0F, 2.0F, 4.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box0",
                CubeListBuilder.create().texOffs(136, 0)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 1.0F, 14.0F),
                PartPose.offsetAndRotation(
                        -31.0F, 4.0F, -7.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box1",
                CubeListBuilder.create().texOffs(188, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 7.0F, 0.0F),
                PartPose.offsetAndRotation(
                        -20.0F, 0.0F, 5.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box10",
                CubeListBuilder.create().texOffs(215, 51)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 2.0F, 15.0F),
                PartPose.offsetAndRotation(
                        -26.0F, 11.0F, -5.0F,
                        0.0F, 0.0F, -6.2308254F));
        root.addOrReplaceChild("box11",
                CubeListBuilder.create().texOffs(223, 25)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 10.0F, 15.0F),
                PartPose.offsetAndRotation(
                        -11.0F, 11.0F, 10.0F,
                        0.0F, -3.1415927F, 6.161012F));
        root.addOrReplaceChild("box12",
                CubeListBuilder.create().texOffs(215, 51)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 2.0F, 15.0F),
                PartPose.offsetAndRotation(
                        -11.0F, 11.0F, 10.0F,
                        0.0F, -3.1415927F, -6.2308254F));
        root.addOrReplaceChild("box13",
                CubeListBuilder.create().texOffs(1, 79)
                        .addBox(0.0F, 0.0F, 0.0F, 66.0F, 4.0F, 1.0F),
                PartPose.offsetAndRotation(
                        33.0F, 14.0F, -10.0F,
                        0.0F, -3.1415927F, 0.0F));
        root.addOrReplaceChild("box14",
                CubeListBuilder.create().texOffs(1, 86)
                        .addBox(0.0F, -6.0F, -1.0F, 66.0F, 6.0F, 1.0F),
                PartPose.offsetAndRotation(
                        33.0F, 14.0F, -11.0F,
                        -6.195919F, -3.1415927F, 0.0F));
        root.addOrReplaceChild("box15",
                CubeListBuilder.create().texOffs(1, 67)
                        .addBox(0.0F, 0.0F, -1.0F, 66.0F, 9.0F, 1.0F),
                PartPose.offsetAndRotation(
                        33.0F, 18.0F, -11.0F,
                        -0.034906585F, -3.1415927F, 0.0F));
        root.addOrReplaceChild("box16",
                CubeListBuilder.create().texOffs(188, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 7.0F, 0.0F),
                PartPose.offsetAndRotation(
                        -32.0F, 0.0F, -5.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box17",
                CubeListBuilder.create().texOffs(96, 1)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 14.0F),
                PartPose.offsetAndRotation(
                        -29.0F, 2.0F, -7.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box18",
                CubeListBuilder.create().texOffs(136, 0)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 1.0F, 14.0F),
                PartPose.offsetAndRotation(
                        -19.0F, 4.0F, -7.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box19",
                CubeListBuilder.create().texOffs(188, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 7.0F, 0.0F),
                PartPose.offsetAndRotation(
                        24.0F, 0.0F, -5.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box2",
                CubeListBuilder.create().texOffs(96, 1)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 14.0F),
                PartPose.offsetAndRotation(
                        15.0F, 2.0F, -7.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box20",
                CubeListBuilder.create().texOffs(96, 1)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 14.0F),
                PartPose.offsetAndRotation(
                        27.0F, 2.0F, -7.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box21",
                CubeListBuilder.create().texOffs(188, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 7.0F, 0.0F),
                PartPose.offsetAndRotation(
                        24.0F, 0.0F, 5.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box22",
                CubeListBuilder.create().texOffs(136, 0)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 1.0F, 14.0F),
                PartPose.offsetAndRotation(
                        13.0F, 4.0F, -7.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box23",
                CubeListBuilder.create().texOffs(209, 111)
                        .addBox(3.0F, 0.0F, 0.0F, 22.0F, 3.0F, 1.0F),
                PartPose.offsetAndRotation(
                        -36.0F, 1.0F, 5.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box24",
                CubeListBuilder.create().texOffs(181, 93)
                        .addBox(0.0F, 0.0F, 0.0F, 22.0F, 2.0F, 12.0F),
                PartPose.offsetAndRotation(
                        11.0F, 4.0F, -6.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box25",
                CubeListBuilder.create().texOffs(181, 93)
                        .addBox(0.0F, 0.0F, 0.0F, 22.0F, 2.0F, 12.0F),
                PartPose.offsetAndRotation(
                        -33.0F, 4.0F, -6.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box26",
                CubeListBuilder.create().texOffs(187, 246)
                        .addBox(0.0F, 0.0F, 0.0F, 10.0F, 3.0F, 4.0F),
                PartPose.offsetAndRotation(
                        -5.0F, 3.0F, -2.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box27",
                CubeListBuilder.create().texOffs(188, 239)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 1.0F, 4.0F),
                PartPose.offsetAndRotation(
                        5.0F, 3.0F, -2.0F,
                        0.0F, 0.0F, -5.846853F));
        root.addOrReplaceChild("box28",
                CubeListBuilder.create().texOffs(217, 246)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 4.0F, 3.0F),
                PartPose.offsetAndRotation(
                        1.0F, 2.0F, -8.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box29",
                CubeListBuilder.create().texOffs(188, 239)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 1.0F, 4.0F),
                PartPose.offsetAndRotation(
                        -5.0F, 3.0F, 2.0F,
                        0.0F, -3.1415927F, 5.846853F));
        root.addOrReplaceChild("box3",
                CubeListBuilder.create().texOffs(96, 1)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 14.0F),
                PartPose.offsetAndRotation(
                        -17.0F, 2.0F, -7.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box30",
                CubeListBuilder.create().texOffs(2, 191)
                        .addBox(0.0F, 0.0F, 0.0F, 54.0F, 21.0F, 0.0F),
                PartPose.offsetAndRotation(
                        -27.0F, 9.0F, -5.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box31",
                CubeListBuilder.create().texOffs(182, 209)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 17.0F, 2.0F),
                PartPose.offsetAndRotation(
                        -35.0F, 11.0F, -6.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box32",
                CubeListBuilder.create().texOffs(182, 209)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 17.0F, 2.0F),
                PartPose.offsetAndRotation(
                        -35.0F, 11.0F, 4.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box33",
                CubeListBuilder.create().texOffs(189, 210)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 4.0F, 8.0F),
                PartPose.offsetAndRotation(
                        -35.0F, 25.0F, -4.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box34",
                CubeListBuilder.create().texOffs(209, 55)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 0.0F, 8.0F),
                PartPose.offsetAndRotation(
                        -34.0F, 9.0F, 4.0F,
                        0.0F, -3.1415927F, 6.1784654F));
        root.addOrReplaceChild("box35",
                CubeListBuilder.create().texOffs(188, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 7.0F, 0.0F),
                PartPose.offsetAndRotation(
                        12.0F, 0.0F, 5.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box36",
                CubeListBuilder.create().texOffs(215, 209)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 17.0F, 2.0F),
                PartPose.offsetAndRotation(
                        34.0F, 11.0F, -6.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box37",
                CubeListBuilder.create().texOffs(215, 209)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 17.0F, 2.0F),
                PartPose.offsetAndRotation(
                        34.0F, 11.0F, 4.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box38",
                CubeListBuilder.create().texOffs(222, 209)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 4.0F, 8.0F),
                PartPose.offsetAndRotation(
                        34.0F, 25.0F, -4.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box39",
                CubeListBuilder.create().texOffs(209, 55)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 0.0F, 8.0F),
                PartPose.offsetAndRotation(
                        34.0F, 9.0F, -4.0F,
                        0.0F, 0.0F, -6.1784654F));
        root.addOrReplaceChild("box4",
                CubeListBuilder.create().texOffs(188, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 7.0F, 0.0F),
                PartPose.offsetAndRotation(
                        -32.0F, 0.0F, 5.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box40",
                CubeListBuilder.create().texOffs(200, 0)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 1.0F, 22.0F),
                PartPose.offsetAndRotation(
                        27.0F, 6.0F, -11.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box41",
                CubeListBuilder.create().texOffs(215, 51)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 2.0F, 15.0F),
                PartPose.offsetAndRotation(
                        -8.0F, 11.0F, -5.0F,
                        0.0F, 0.0F, -6.2308254F));
        root.addOrReplaceChild("box42",
                CubeListBuilder.create().texOffs(200, 0)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 1.0F, 22.0F),
                PartPose.offsetAndRotation(
                        -33.0F, 6.0F, -11.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box43",
                CubeListBuilder.create().texOffs(209, 73)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 1.0F, 3.0F),
                PartPose.offsetAndRotation(
                        -21.0F, 15.0F, 7.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box44",
                CubeListBuilder.create().texOffs(208, 78)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 1.0F, 3.0F),
                PartPose.offsetAndRotation(
                        -3.0F, 15.0F, 7.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box45",
                CubeListBuilder.create().texOffs(223, 25)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 10.0F, 15.0F),
                PartPose.offsetAndRotation(
                        26.0F, 11.0F, 10.0F,
                        0.0F, -3.1415927F, 6.195919F));
        root.addOrReplaceChild("box46",
                CubeListBuilder.create().texOffs(215, 51)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 2.0F, 15.0F),
                PartPose.offsetAndRotation(
                        11.0F, 11.0F, -5.0F,
                        0.0F, 0.0F, -6.2308254F));
        root.addOrReplaceChild("box47",
                CubeListBuilder.create().texOffs(215, 51)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 2.0F, 15.0F),
                PartPose.offsetAndRotation(
                        26.0F, 11.0F, 10.0F,
                        0.0F, -3.1415927F, -6.2308254F));
        root.addOrReplaceChild("box48",
                CubeListBuilder.create().texOffs(223, 25)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 10.0F, 15.0F),
                PartPose.offsetAndRotation(
                        11.0F, 11.0F, -5.0F,
                        0.0F, 0.0F, -6.161012F));
        root.addOrReplaceChild("box49",
                CubeListBuilder.create().texOffs(209, 73)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 1.0F, 3.0F),
                PartPose.offsetAndRotation(
                        16.0F, 15.0F, 7.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box5",
                CubeListBuilder.create().texOffs(188, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 7.0F, 0.0F),
                PartPose.offsetAndRotation(
                        12.0F, 0.0F, -5.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box50",
                CubeListBuilder.create().texOffs(209, 116)
                        .addBox(3.0F, 0.0F, 0.0F, 22.0F, 3.0F, 1.0F),
                PartPose.offsetAndRotation(
                        -36.0F, 1.0F, -6.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box51",
                CubeListBuilder.create().texOffs(136, 0)
                        .addBox(0.0F, 0.0F, 0.0F, 6.0F, 1.0F, 14.0F),
                PartPose.offsetAndRotation(
                        25.0F, 4.0F, -7.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box52",
                CubeListBuilder.create().texOffs(217, 71)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 15.0F),
                PartPose.offsetAndRotation(
                        -27.0F, 26.0F, -5.0F,
                        0.0F, 0.0F, -6.213372F));
        root.addOrReplaceChild("box53",
                CubeListBuilder.create().texOffs(141, 172)
                        .addBox(0.0F, -6.0F, -1.0F, 1.0F, 6.0F, 1.0F),
                PartPose.offsetAndRotation(
                        -34.0F, 14.0F, 11.0F,
                        -6.195919F, 0.0F, 0.0F));
        root.addOrReplaceChild("box54",
                CubeListBuilder.create().texOffs(141, 165)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 4.0F, 1.0F),
                PartPose.offsetAndRotation(
                        -34.0F, 14.0F, 10.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box55",
                CubeListBuilder.create().texOffs(141, 153)
                        .addBox(0.0F, 0.0F, -1.0F, 1.0F, 9.0F, 1.0F),
                PartPose.offsetAndRotation(
                        -34.0F, 18.0F, 11.0F,
                        -0.034906585F, 0.0F, 0.0F));
        root.addOrReplaceChild("box56",
                CubeListBuilder.create().texOffs(136, 153)
                        .addBox(0.0F, 0.0F, -1.0F, 1.0F, 9.0F, 1.0F),
                PartPose.offsetAndRotation(
                        33.0F, 18.0F, 11.0F,
                        -0.034906585F, 0.0F, 0.0F));
        root.addOrReplaceChild("box57",
                CubeListBuilder.create().texOffs(136, 165)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 4.0F, 1.0F),
                PartPose.offsetAndRotation(
                        33.0F, 14.0F, 10.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box58",
                CubeListBuilder.create().texOffs(136, 172)
                        .addBox(0.0F, -6.0F, -1.0F, 1.0F, 6.0F, 1.0F),
                PartPose.offsetAndRotation(
                        33.0F, 14.0F, 11.0F,
                        -6.195919F, 0.0F, 0.0F));
        root.addOrReplaceChild("box59",
                CubeListBuilder.create().texOffs(140, 79)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 4.0F, 1.0F),
                PartPose.offsetAndRotation(
                        34.0F, 14.0F, -10.0F,
                        0.0F, -3.1415927F, 0.0F));
        root.addOrReplaceChild("box6",
                CubeListBuilder.create().texOffs(209, 116)
                        .addBox(3.0F, 0.0F, 0.0F, 22.0F, 3.0F, 1.0F),
                PartPose.offsetAndRotation(
                        8.0F, 1.0F, -6.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box60",
                CubeListBuilder.create().texOffs(140, 86)
                        .addBox(0.0F, -6.0F, -1.0F, 1.0F, 6.0F, 1.0F),
                PartPose.offsetAndRotation(
                        34.0F, 14.0F, -11.0F,
                        -6.195919F, -3.1415927F, 0.0F));
        root.addOrReplaceChild("box61",
                CubeListBuilder.create().texOffs(140, 67)
                        .addBox(0.0F, 0.0F, -1.0F, 1.0F, 9.0F, 1.0F),
                PartPose.offsetAndRotation(
                        34.0F, 18.0F, -11.0F,
                        -0.034906585F, -3.1415927F, 0.0F));
        root.addOrReplaceChild("box62",
                CubeListBuilder.create().texOffs(145, 67)
                        .addBox(0.0F, 0.0F, -1.0F, 1.0F, 9.0F, 1.0F),
                PartPose.offsetAndRotation(
                        -33.0F, 18.0F, -11.0F,
                        -0.034906585F, -3.1415927F, 0.0F));
        root.addOrReplaceChild("box63",
                CubeListBuilder.create().texOffs(188, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 8.0F, 7.0F, 0.0F),
                PartPose.offsetAndRotation(
                        -20.0F, 0.0F, -5.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box64",
                CubeListBuilder.create().texOffs(213, 165)
                        .addBox(0.0F, 0.0F, 0.0F, 0.0F, 22.0F, 20.0F),
                PartPose.offsetAndRotation(
                        -34.0F, 8.0F, -10.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box65",
                CubeListBuilder.create().texOffs(0, 215)
                        .addBox(0.0F, 0.0F, 0.0F, 68.0F, 2.0F, 18.0F),
                PartPose.offsetAndRotation(
                        -34.0F, 6.0F, -9.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box66",
                CubeListBuilder.create().texOffs(152, 35)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 3.0F, 4.0F),
                PartPose.offsetAndRotation(
                        -36.0F, 6.0F, -2.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box67",
                CubeListBuilder.create().texOffs(153, 44)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 3.0F, 3.0F),
                PartPose.offsetAndRotation(
                        -35.0F, 7.0F, 5.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box68",
                CubeListBuilder.create().texOffs(153, 44)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 3.0F, 3.0F),
                PartPose.offsetAndRotation(
                        -35.0F, 7.0F, -8.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box69",
                CubeListBuilder.create().texOffs(0, 46)
                        .addBox(0.0F, -2.0F, 0.0F, 68.0F, 2.0F, 6.0F),
                PartPose.offsetAndRotation(
                        -34.0F, 32.0F, -3.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box7",
                CubeListBuilder.create().texOffs(1, 153)
                        .addBox(0.0F, 0.0F, -1.0F, 66.0F, 9.0F, 1.0F),
                PartPose.offsetAndRotation(
                        -33.0F, 18.0F, 11.0F,
                        -0.034906585F, 0.0F, 0.0F));
        root.addOrReplaceChild("box70",
                CubeListBuilder.create().texOffs(1, 55)
                        .addBox(0.0F, -2.0F, 0.0F, 68.0F, 2.0F, 5.0F),
                PartPose.offsetAndRotation(
                        34.0F, 32.0F, -3.0F,
                        -6.091199F, -3.1415927F, 0.0F));
        root.addOrReplaceChild("box71",
                CubeListBuilder.create().texOffs(1, 38)
                        .addBox(0.0F, -2.0F, 0.0F, 68.0F, 2.0F, 5.0F),
                PartPose.offsetAndRotation(
                        34.0F, 31.0F, -8.0F,
                        -5.3407073F, -3.1415927F, 0.0F));
        root.addOrReplaceChild("box72",
                CubeListBuilder.create().texOffs(1, 55)
                        .addBox(0.0F, -2.0F, 0.0F, 68.0F, 2.0F, 5.0F),
                PartPose.offsetAndRotation(
                        -34.0F, 32.0F, 3.0F,
                        -6.091199F, 0.0F, 0.0F));
        root.addOrReplaceChild("box73",
                CubeListBuilder.create().texOffs(1, 38)
                        .addBox(0.0F, -2.0F, 0.0F, 68.0F, 2.0F, 5.0F),
                PartPose.offsetAndRotation(
                        -34.0F, 31.0F, 8.0F,
                        -5.305801F, 0.0F, 0.0F));
        root.addOrReplaceChild("box74",
                CubeListBuilder.create().texOffs(0, 235)
                        .addBox(0.0F, 0.0F, 0.0F, 68.0F, 1.0F, 20.0F),
                PartPose.offsetAndRotation(
                        -34.0F, 8.0F, -10.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box75",
                CubeListBuilder.create().texOffs(171, 165)
                        .addBox(0.0F, 0.0F, 0.0F, 0.0F, 22.0F, 20.0F),
                PartPose.offsetAndRotation(
                        34.0F, 8.0F, -10.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box76",
                CubeListBuilder.create().texOffs(163, 44)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 3.0F, 3.0F),
                PartPose.offsetAndRotation(
                        34.0F, 7.0F, -8.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box77",
                CubeListBuilder.create().texOffs(163, 44)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 3.0F, 3.0F),
                PartPose.offsetAndRotation(
                        34.0F, 7.0F, 5.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box78",
                CubeListBuilder.create().texOffs(165, 35)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 3.0F, 4.0F),
                PartPose.offsetAndRotation(
                        34.0F, 6.0F, -2.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box79",
                CubeListBuilder.create().texOffs(223, 25)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 10.0F, 15.0F),
                PartPose.offsetAndRotation(
                        -8.0F, 11.0F, -5.0F,
                        0.0F, 0.0F, -6.161012F));
        root.addOrReplaceChild("box8",
                CubeListBuilder.create().texOffs(209, 111)
                        .addBox(3.0F, 0.0F, 0.0F, 22.0F, 3.0F, 1.0F),
                PartPose.offsetAndRotation(
                        8.0F, 1.0F, 5.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box80",
                CubeListBuilder.create().texOffs(145, 79)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 4.0F, 1.0F),
                PartPose.offsetAndRotation(
                        -33.0F, 14.0F, -10.0F,
                        0.0F, -3.1415927F, 0.0F));
        root.addOrReplaceChild("box81",
                CubeListBuilder.create().texOffs(223, 25)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 10.0F, 15.0F),
                PartPose.offsetAndRotation(
                        8.0F, 11.0F, 10.0F,
                        0.0F, -3.1415927F, 6.161012F));
        root.addOrReplaceChild("box82",
                CubeListBuilder.create().texOffs(215, 51)
                        .addBox(0.0F, 0.0F, 0.0F, 5.0F, 2.0F, 15.0F),
                PartPose.offsetAndRotation(
                        8.0F, 11.0F, 10.0F,
                        0.0F, -3.1415927F, -6.2308254F));
        root.addOrReplaceChild("box83",
                CubeListBuilder.create().texOffs(199, 122)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 21.0F, 15.0F),
                PartPose.offsetAndRotation(
                        -10.0F, 9.0F, -5.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box84",
                CubeListBuilder.create().texOffs(199, 122)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 21.0F, 15.0F),
                PartPose.offsetAndRotation(
                        9.0F, 9.0F, -5.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box85",
                CubeListBuilder.create().texOffs(169, 140)
                        .addBox(0.0F, 0.0F, 0.0F, 0.0F, 21.0F, 20.0F),
                PartPose.offsetAndRotation(
                        -27.0F, 9.0F, -10.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box86",
                CubeListBuilder.create().texOffs(145, 86)
                        .addBox(0.0F, -6.0F, -1.0F, 1.0F, 6.0F, 1.0F),
                PartPose.offsetAndRotation(
                        -33.0F, 14.0F, -11.0F,
                        -6.195919F, -3.1415927F, 0.0F));
        root.addOrReplaceChild("box87",
                CubeListBuilder.create().texOffs(213, 140)
                        .addBox(0.0F, 0.0F, 0.0F, 0.0F, 21.0F, 20.0F),
                PartPose.offsetAndRotation(
                        27.0F, 9.0F, -10.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box88",
                CubeListBuilder.create().texOffs(217, 71)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 15.0F),
                PartPose.offsetAndRotation(
                        -10.0F, 26.0F, 10.0F,
                        0.0F, -3.1415927F, 6.213372F));
        root.addOrReplaceChild("box89",
                CubeListBuilder.create().texOffs(1, 165)
                        .addBox(0.0F, 0.0F, 0.0F, 66.0F, 4.0F, 1.0F),
                PartPose.offsetAndRotation(
                        -33.0F, 14.0F, 10.0F,
                        0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("box9",
                CubeListBuilder.create().texOffs(223, 25)
                        .addBox(0.0F, 0.0F, 0.0F, 1.0F, 10.0F, 15.0F),
                PartPose.offsetAndRotation(
                        -26.0F, 11.0F, -5.0F,
                        0.0F, 0.0F, -6.195919F));
        root.addOrReplaceChild("box90",
                CubeListBuilder.create().texOffs(1, 172)
                        .addBox(0.0F, -6.0F, -1.0F, 66.0F, 6.0F, 1.0F),
                PartPose.offsetAndRotation(
                        -33.0F, 14.0F, 11.0F,
                        -6.195919F, 0.0F, 0.0F));
        root.addOrReplaceChild("box91",
                CubeListBuilder.create().texOffs(217, 71)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 15.0F),
                PartPose.offsetAndRotation(
                        -9.0F, 26.0F, -5.0F,
                        0.0F, 0.0F, -6.213372F));
        root.addOrReplaceChild("box92",
                CubeListBuilder.create().texOffs(217, 71)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 15.0F),
                PartPose.offsetAndRotation(
                        9.0F, 26.0F, 10.0F,
                        0.0F, -3.1415927F, 6.213372F));
        root.addOrReplaceChild("box93",
                CubeListBuilder.create().texOffs(217, 71)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 15.0F),
                PartPose.offsetAndRotation(
                        10.0F, 26.0F, -5.0F,
                        0.0F, 0.0F, -6.213372F));
        root.addOrReplaceChild("box94",
                CubeListBuilder.create().texOffs(217, 71)
                        .addBox(0.0F, 0.0F, 0.0F, 4.0F, 1.0F, 15.0F),
                PartPose.offsetAndRotation(
                        27.0F, 26.0F, 10.0F,
                        0.0F, -3.1415927F, 6.213372F));
        return LayerDefinition.create(mesh, 256, 256);
    }

    public static ModelPart root(ModelPart bakedRoot) {
        return bakedRoot;
    }
}
