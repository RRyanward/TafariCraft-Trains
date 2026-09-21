/* Step 8.4.7 Batch B8 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.Glyn042tSteamLocomotive;

public final class Glyn042tSteamLocomotiveRenderer extends LegacyMeshRenderer<Glyn042tSteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_glyn_042t.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_glyn_042t.png");

    public Glyn042tSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "GLYN 0-4-2T Steam Locomotive", MESH, TEXTURE,
                0.000000D, 0.625313D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                0.900000F, 1.000000F, 0.900000F, 1.05F);
    }

    @Override
    protected float trainYaw(Glyn042tSteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
