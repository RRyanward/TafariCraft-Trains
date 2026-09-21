/* Step 8.4.5 Batch B6 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.Gwr42xxSteamLocomotive;

public final class Gwr42xxSteamLocomotiveRenderer extends LegacyMeshRenderer<Gwr42xxSteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_gwr_42xx.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_gwr_42xx.png");

    public Gwr42xxSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "GWR 42xx Steam Locomotive", MESH, TEXTURE,
                -2.100000D, 0.625313D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.30F);
    }

    @Override
    protected float trainYaw(Gwr42xxSteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
