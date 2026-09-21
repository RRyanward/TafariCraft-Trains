/* Step 8.4.6 Batch B7 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.SentinelY3SteamLocomotive;

public final class SentinelY3SteamLocomotiveRenderer extends LegacyMeshRenderer<SentinelY3SteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_sentinel_y3.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_sentinel_y3.png");

    public SentinelY3SteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "Sentinel Y3 Steam Locomotive", MESH, TEXTURE,
                -0.500000D, 0.625313D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.05F);
    }

    @Override
    protected float trainYaw(SentinelY3SteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
