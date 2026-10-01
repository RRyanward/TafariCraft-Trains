/* Step 8.4.0 Batch B1 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.AdlerSteamLocomotive;

public final class AdlerSteamLocomotiveRenderer extends LegacyMeshRenderer<AdlerSteamLocomotive> {
    private static final ResourceLocation MESH = new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_adler.tcm");
    private static final ResourceLocation TEXTURE = new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_adler.png");

    public AdlerSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "Adler Steam Locomotive", MESH, TEXTURE,
                -0.800000D, 1.500000D, 0.000000D,
                180.000000F, -90.000000F, 0.000000F,
                0.900000F, 1.000000F, 0.900000F, 1.15F);
    }

    @Override protected float trainYaw(AdlerSteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
