/* Step 8.4.4 Batch B5 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.StarClassSteamLocomotive;

public final class StarClassSteamLocomotiveRenderer extends LegacyMeshRenderer<StarClassSteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_star_class.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_star_class.png");

    public StarClassSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "Star Class Steam Locomotive", MESH, TEXTURE,
                -0.800000D, 0.687813D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.25F);
    }

    @Override
    protected float trainYaw(StarClassSteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
