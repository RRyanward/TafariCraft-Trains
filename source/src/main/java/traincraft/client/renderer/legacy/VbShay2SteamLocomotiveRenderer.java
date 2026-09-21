/* Step 8.4.7 Batch B8 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.VbShay2SteamLocomotive;

public final class VbShay2SteamLocomotiveRenderer extends LegacyMeshRenderer<VbShay2SteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_vb_shay_2.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_vb_shay_2.png");

    public VbShay2SteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "VB Shay 2 Steam Locomotive", MESH, TEXTURE,
                -0.400000D, -0.434994D, 0.000000D,
                0.000000F, 0.000000F, 0.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.05F);
    }

    @Override
    protected float trainYaw(VbShay2SteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
