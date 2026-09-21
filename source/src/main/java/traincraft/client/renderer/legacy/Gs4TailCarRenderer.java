/* Step 8.4.3 Batch B4 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.passenger.Gs4TailCar;

public final class Gs4TailCarRenderer extends LegacyMeshRenderer<Gs4TailCar> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/gs4_tail_car.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/gs4_tail_car.png");

    public Gs4TailCarRenderer(EntityRendererProvider.Context context) {
        super(context, "GS4 Tail Car", MESH, TEXTURE,
                -0.200000D, 0.187500D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                0.800000F, 1.000000F, 0.800000F, 1.15F);
    }

    @Override
    protected float trainYaw(Gs4TailCar entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
