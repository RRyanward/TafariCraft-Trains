/* Step 8.4.0 Batch B1 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.PannierSteamLocomotive;

public final class PannierSteamLocomotiveRenderer extends LegacyMeshRenderer<PannierSteamLocomotive> {
    private static final ResourceLocation MESH = new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_pannier.tcm");
    private static final ResourceLocation TEXTURE = new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_pannier.png");

    public PannierSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "Pannier Steam Locomotive", MESH, TEXTURE,
                0.150000D, 0.675000D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                0.900000F, 0.900000F, 0.900000F, 1.15F);
    }

    @Override protected float trainYaw(PannierSteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
