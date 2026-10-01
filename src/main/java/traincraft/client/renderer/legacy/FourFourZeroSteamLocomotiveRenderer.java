/* Step 8.4.6 Batch B7 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.FourFourZeroSteamLocomotive;

public final class FourFourZeroSteamLocomotiveRenderer extends LegacyMeshRenderer<FourFourZeroSteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_4_4_0.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_4_4_0.png");

    public FourFourZeroSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "4-4-0 Steam Locomotive", MESH, TEXTURE,
                -0.660000D, -0.000000D, 0.000000D,
                0.000000F, 90.000000F, 0.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.20F);
    }

    @Override
    protected float trainYaw(FourFourZeroSteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
