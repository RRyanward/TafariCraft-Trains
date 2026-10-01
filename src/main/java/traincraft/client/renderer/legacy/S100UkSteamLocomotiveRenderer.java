/* Step 8.4.2 Batch B3 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.S100UkSteamLocomotive;

public final class S100UkSteamLocomotiveRenderer extends LegacyMeshRenderer<S100UkSteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_s100_uk.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_s100_uk.png");

    public S100UkSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "USATC S100 UK Steam Locomotive", MESH, TEXTURE,
                -2.000000D, 0.250000D, 0.837500D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.10F);
    }

    @Override
    protected float trainYaw(S100UkSteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
