/* Step 8.4.7 Batch B8 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.AlcoSc4SteamLocomotive;

public final class AlcoSc4SteamLocomotiveRenderer extends LegacyMeshRenderer<AlcoSc4SteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_alco_sc4.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_alco_sc4.png");

    public AlcoSc4SteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "Alco SC4 Steam Locomotive", MESH, TEXTURE,
                -3.500000D, -0.200000D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.30F);
    }

    @Override
    protected float trainYaw(AlcoSc4SteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}

