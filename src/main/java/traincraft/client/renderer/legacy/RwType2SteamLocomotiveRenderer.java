/* Step 8.4.5 Batch B6 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.RwType2SteamLocomotive;

public final class RwType2SteamLocomotiveRenderer extends LegacyMeshRenderer<RwType2SteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_rw_type_2.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_rw_type_2.png");

    public RwType2SteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "RW Type 2 Steam Locomotive", MESH, TEXTURE,
                -1.250000D, 0.625313D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.20F);
    }

    @Override
    protected float trainYaw(RwType2SteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
