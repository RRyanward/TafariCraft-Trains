/* Step 8.4.6 Batch B7 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.J50SteamLocomotive;

public final class J50SteamLocomotiveRenderer extends LegacyMeshRenderer<J50SteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_j50.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_j50.png");

    public J50SteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "J50 Steam Locomotive", MESH, TEXTURE,
                -1.250000D, 0.625313D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.20F);
    }

    @Override
    protected float trainYaw(J50SteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
