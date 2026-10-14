package traincraft.client.model.recovered;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.power.LegacyPoweredLocomotive;

/**
 * TC_DEV36_R1K29E_CLASSIC_BUILDER_ROTOR
 *
 * The seven rotor pieces are a direct modern ModelPart translation of
 * Traincraft 1.6.4 ModelBuilder.java's rotor1..rotor7 geometry. The accepted
 * recovered Builder body intentionally excluded every "rotor*" part so the
 * moving cutter can remain a render-only layer.
 */
public final class RecoveredBuilderRotorModel
        extends EntityModel<LegacyPoweredLocomotive> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(
                    new ResourceLocation(
                            Traincraft.MOD_ID,
                            "builder_rotor"),
                    "main");

    private final ModelPart rotor;

    public RecoveredBuilderRotorModel(
            ModelPart root) {
        this.rotor =
                root.getChild("rotor");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh =
                new MeshDefinition();

        PartDefinition root =
                mesh.getRoot();

        PartDefinition rotor =
                root.addOrReplaceChild(
                        "rotor",
                        CubeListBuilder.create(),
                        PartPose.offset(
                                -29.0F,
                                24.0F,
                                0.0F));

        rotor.addOrReplaceChild(
                "rotor1",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                0.0F,
                                -3.0F,
                                -19.0F,
                                3.0F,
                                6.0F,
                                38.0F),
                PartPose.ZERO);

        rotor.addOrReplaceChild(
                "rotor2",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                0.0F,
                                -3.0F,
                                -19.0F,
                                3.0F,
                                6.0F,
                                38.0F),
                PartPose.offsetAndRotation(
                        0.0F,
                        0.0F,
                        0.0F,
                        -1.5707964F,
                        0.0F,
                        0.0F));

        rotor.addOrReplaceChild(
                "rotor3",
                CubeListBuilder.create()
                        .texOffs(45, 11)
                        .addBox(
                                0.0F,
                                -3.0F,
                                -19.0F,
                                3.0F,
                                6.0F,
                                38.0F),
                PartPose.offsetAndRotation(
                        0.05F,
                        0.0F,
                        0.0F,
                        -0.5235988F,
                        0.0F,
                        0.0F));

        rotor.addOrReplaceChild(
                "rotor4",
                CubeListBuilder.create()
                        .texOffs(45, 11)
                        .addBox(
                                0.0F,
                                -3.0F,
                                -19.0F,
                                3.0F,
                                6.0F,
                                38.0F),
                PartPose.offsetAndRotation(
                        0.07F,
                        0.0F,
                        0.0F,
                        -1.0471976F,
                        0.0F,
                        0.0F));

        rotor.addOrReplaceChild(
                "rotor5",
                CubeListBuilder.create()
                        .texOffs(45, 11)
                        .addBox(
                                0.0F,
                                -3.0F,
                                -19.0F,
                                3.0F,
                                6.0F,
                                38.0F),
                PartPose.offsetAndRotation(
                        0.05F,
                        0.0F,
                        0.0F,
                        -5.7595863F,
                        0.0F,
                        0.0F));

        rotor.addOrReplaceChild(
                "rotor6",
                CubeListBuilder.create()
                        .texOffs(3, 4)
                        .addBox(
                                -1.0F,
                                -6.0F,
                                -6.0F,
                                4.0F,
                                12.0F,
                                12.0F),
                PartPose.ZERO);

        rotor.addOrReplaceChild(
                "rotor7",
                CubeListBuilder.create()
                        .texOffs(45, 11)
                        .addBox(
                                0.0F,
                                -3.0F,
                                -19.0F,
                                3.0F,
                                6.0F,
                                38.0F),
                PartPose.offsetAndRotation(
                        0.07F,
                        0.0F,
                        0.0F,
                        -5.2359877F,
                        0.0F,
                        0.0F));

        return LayerDefinition.create(
                mesh,
                128,
                64);
    }

    public void setRotorAngle(
            float angle) {
        this.rotor.xRot =
                angle;
    }

    @Override
    public void setupAnim(
            LegacyPoweredLocomotive entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        // Renderer owns elapsed-time rotor motion.
    }

    @Override
    public void renderToBuffer(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha) {

        rotor.render(
                poseStack,
                consumer,
                packedLight,
                packedOverlay,
                red,
                green,
                blue,
                alpha);
    }
}
