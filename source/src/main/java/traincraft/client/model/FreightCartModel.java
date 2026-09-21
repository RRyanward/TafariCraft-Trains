/* Traincraft classic ModelFreightCart2 converted for Minecraft 1.20.1. */
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

/** Geometry ported box-for-box from Traincraft 1.7.10 ModelFreightCart2. */
public final class FreightCartModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(Traincraft.MOD_ID, "freight_cart"), "main");

    private FreightCartModel() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        CubeListBuilder body = CubeListBuilder.create()
                .texOffs(1, 24).addBox(-5.0F, 1.0F, -9.0F, 9.0F, 4.0F, 18.0F)
                .texOffs(2, 66).addBox(-20.0F, -1.0F, -7.0F, 6.0F, 6.0F, 1.0F)
                .texOffs(2, 66).addBox(-13.0F, -1.0F, -7.0F, 6.0F, 6.0F, 1.0F)
                .texOffs(1, 124).addBox(-22.0F, 5.0F, -10.0F, 43.0F, 27.0F, 20.0F)
                .texOffs(64, 26).addBox(-5.0F, 10.0F, -11.0F, 12.0F, 17.0F, 1.0F)
                .texOffs(64, 26).addBox(-5.0F, 10.0F, 10.0F, 12.0F, 17.0F, 1.0F)
                .texOffs(41, 26).addBox(-18.0F, 15.0F, 10.0F, 10.0F, 10.0F, 1.0F)
                .texOffs(63, 49).addBox(-6.0F, 7.0F, -11.0F, 25.0F, 3.0F, 1.0F)
                .texOffs(1, 96).addBox(-21.0F, 1.0F, -6.0F, 15.0F, 4.0F, 12.0F)
                .texOffs(3, 118).addBox(-18.0F, 1.0F, -8.0F, 9.0F, 2.0F, 1.0F)
                .texOffs(3, 118).addBox(-18.0F, 1.0F, 7.0F, 9.0F, 2.0F, 1.0F)
                .texOffs(3, 118).addBox(8.0F, 1.0F, 7.0F, 9.0F, 2.0F, 1.0F)
                .texOffs(3, 118).addBox(8.0F, 1.0F, -8.0F, 9.0F, 2.0F, 1.0F)
                .texOffs(2, 66).addBox(-13.0F, -1.0F, 6.0F, 6.0F, 6.0F, 1.0F)
                .texOffs(63, 49).addBox(-6.0F, 27.0F, -11.0F, 25.0F, 3.0F, 1.0F)
                .texOffs(7, 77).addBox(20.0F, 2.0F, -1.0F, 3.0F, 2.0F, 2.0F)
                .texOffs(63, 49).addBox(-6.0F, 7.0F, 10.0F, 25.0F, 3.0F, 1.0F)
                .texOffs(7, 77).addBox(-24.0F, 2.0F, -1.0F, 3.0F, 2.0F, 2.0F)
                .texOffs(41, 26).addBox(-18.0F, 15.0F, -11.0F, 10.0F, 10.0F, 1.0F)
                .texOffs(2, 66).addBox(-20.0F, -1.0F, 6.0F, 6.0F, 6.0F, 1.0F)
                .texOffs(2, 66).addBox(13.0F, -1.0F, -7.0F, 6.0F, 6.0F, 1.0F)
                .texOffs(2, 66).addBox(6.0F, -1.0F, -7.0F, 6.0F, 6.0F, 1.0F)
                .texOffs(2, 66).addBox(6.0F, -1.0F, 6.0F, 6.0F, 6.0F, 1.0F)
                .texOffs(2, 66).addBox(13.0F, -1.0F, 6.0F, 6.0F, 6.0F, 1.0F)
                .texOffs(1, 96).addBox(5.0F, 1.0F, -6.0F, 15.0F, 4.0F, 12.0F)
                .texOffs(63, 49).addBox(-6.0F, 27.0F, 10.0F, 25.0F, 3.0F, 1.0F);

        root.addOrReplaceChild("body", body, PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 256);
    }

    public static ModelPart root(ModelPart bakedRoot) {
        return bakedRoot;
    }
}
