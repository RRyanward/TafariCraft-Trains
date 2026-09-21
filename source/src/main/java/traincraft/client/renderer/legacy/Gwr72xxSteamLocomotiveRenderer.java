/* Step 8.4.5 Batch B6 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.Gwr72xxSteamLocomotive;

public final class Gwr72xxSteamLocomotiveRenderer extends LegacyMeshRenderer<Gwr72xxSteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_gwr_72xx.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_gwr_72xx.png");

    public Gwr72xxSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "GWR 72xx Steam Locomotive", MESH, TEXTURE,
                -2.500000D, 0.625313D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.35F);
    }

    @Override
    protected float trainYaw(Gwr72xxSteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
