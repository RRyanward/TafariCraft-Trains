/* Step 8.4.1 Batch B2 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.passenger.MilwPassengerCar;

public final class MilwPassengerCarRenderer extends LegacyMeshRenderer<MilwPassengerCar> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/milw_passenger_car.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/milw_passenger_car.png");

    public MilwPassengerCarRenderer(EntityRendererProvider.Context context) {
        super(context, "MILW Passenger Car", MESH, TEXTURE,
                -0.100000D, 0.450281D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                0.900000F, 0.900000F, 0.900000F, 1.00F);
    }

    @Override
    protected float trainYaw(MilwPassengerCar entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
