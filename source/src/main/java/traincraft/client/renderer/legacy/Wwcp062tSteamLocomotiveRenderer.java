/* Step 8.4.6 Batch B7 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.Wwcp062tSteamLocomotive;

public final class Wwcp062tSteamLocomotiveRenderer extends LegacyMeshRenderer<Wwcp062tSteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_wwcp_062t.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_wwcp_062t.png");

    public Wwcp062tSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "WWCP Class 0-6-2T Steam Locomotive", MESH, TEXTURE,
                -2.500000D, 0.625300D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.30F);
    }

    @Override
    protected float trainYaw(Wwcp062tSteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
