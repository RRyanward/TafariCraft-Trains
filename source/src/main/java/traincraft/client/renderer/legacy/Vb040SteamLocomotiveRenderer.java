/* Step 8.4.6 Batch B7 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.Vb040SteamLocomotive;

public final class Vb040SteamLocomotiveRenderer extends LegacyMeshRenderer<Vb040SteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_vb_0_4_0.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_vb_0_4_0.png");

    public Vb040SteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "VB 0-4-0 Steam Locomotive", MESH, TEXTURE,
                0.000000D, 0.625313D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                0.900000F, 1.000000F, 0.900000F, 1.05F);
    }

    @Override
    protected float trainYaw(Vb040SteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
