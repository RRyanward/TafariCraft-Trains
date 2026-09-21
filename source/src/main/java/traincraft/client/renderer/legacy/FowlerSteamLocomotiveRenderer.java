/* Step 8.4.1 Batch B2 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.FowlerSteamLocomotive;

public final class FowlerSteamLocomotiveRenderer extends LegacyMeshRenderer<FowlerSteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_fowler.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_fowler.png");

    public FowlerSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "Fowler 4F Steam Locomotive", MESH, TEXTURE,
                -3.000000D, -0.062187D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.15F);
    }

    @Override
    protected float trainYaw(FowlerSteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
