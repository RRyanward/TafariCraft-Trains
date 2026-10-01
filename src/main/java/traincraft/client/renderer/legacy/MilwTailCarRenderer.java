/* Step 8.4.2 Batch B3 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.passenger.MilwTailCar;

public final class MilwTailCarRenderer extends LegacyMeshRenderer<MilwTailCar> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/milw_tail_car.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/milw_tail_car.png");

    public MilwTailCarRenderer(EntityRendererProvider.Context context) {
        super(context, "MILW Tail Car", MESH, TEXTURE,
                0.100000D, 0.562500D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                0.900000F, 0.900000F, 0.900000F, 1.20F);
    }

    @Override
    protected float trainYaw(MilwTailCar entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
