/* Step 8.4.6 Batch B7 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.Gwr101ClassSteamLocomotive;

public final class Gwr101ClassSteamLocomotiveRenderer extends LegacyMeshRenderer<Gwr101ClassSteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_gwr_101_class.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_gwr_101_class.png");

    public Gwr101ClassSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "GWR 101 Class Steam Locomotive", MESH, TEXTURE,
                -1.250000D, 0.625313D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.20F);
    }

    @Override
    protected float trainYaw(Gwr101ClassSteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
