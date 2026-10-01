/* Step 8.4.5 Batch B6 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.CoronationClassSteamLocomotive;

public final class CoronationClassSteamLocomotiveRenderer extends LegacyMeshRenderer<CoronationClassSteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_coronation_class.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_coronation_class.png");

    public CoronationClassSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "Coronation Class Steam Locomotive", MESH, TEXTURE,
                -3.800000D, 0.625000D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.35F);
    }

    @Override
    protected float trainYaw(CoronationClassSteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
