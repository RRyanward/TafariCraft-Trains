/* Step 8.4.3 Batch B4 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.passenger.Gs4ObservatoryCar;

public final class Gs4ObservatoryCarRenderer extends LegacyMeshRenderer<Gs4ObservatoryCar> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/gs4_observatory_car.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/gs4_observatory_car.png");

    public Gs4ObservatoryCarRenderer(EntityRendererProvider.Context context) {
        super(context, "GS4 Observatory Car", MESH, TEXTURE,
                0.000000D, 0.187500D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                0.800000F, 1.000000F, 0.800000F, 1.15F);
    }

    @Override
    protected float trainYaw(Gs4ObservatoryCar entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
