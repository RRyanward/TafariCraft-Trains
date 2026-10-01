/* Step 8.4.0 Batch B1 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.ClimaxSteamLocomotive;

public final class ClimaxSteamLocomotiveRenderer extends LegacyMeshRenderer<ClimaxSteamLocomotive> {
    private static final ResourceLocation MESH = new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_climax.tcm");
    private static final ResourceLocation TEXTURE = new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_climax.png");

    public ClimaxSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "Climax Steam Locomotive", MESH, TEXTURE,
                0.000000D, 0.625313D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                0.900000F, 1.000000F, 0.900000F, 1.15F);
    }

    @Override protected float trainYaw(ClimaxSteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
