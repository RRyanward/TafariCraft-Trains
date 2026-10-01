/* Step 8.4.7 Batch B8 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.TwoSixTwoTSteamLocomotive;

public final class TwoSixTwoTSteamLocomotiveRenderer extends LegacyMeshRenderer<TwoSixTwoTSteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_262t.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_262t.png");

    public TwoSixTwoTSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "2-6-2T Steam Locomotive", MESH, TEXTURE,
                0.000000D, 0.625313D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                0.900000F, 1.000000F, 0.900000F, 1.10F);
    }

    @Override
    protected float trainYaw(TwoSixTwoTSteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
