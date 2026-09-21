/* Step 8.4.4 Batch B5 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.Shay3TruckSteamLocomotive;

public final class Shay3TruckSteamLocomotiveRenderer extends LegacyMeshRenderer<Shay3TruckSteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_shay_3_truck.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_shay_3_truck.png");

    public Shay3TruckSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "Shay 3-Truck Steam Locomotive", MESH, TEXTURE,
                0.000000D, 0.625313D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.20F);
    }

    @Override
    protected float trainYaw(Shay3TruckSteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
