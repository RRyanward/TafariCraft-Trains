/* Step 8.4.5 Batch B6 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.SkookumSteamLocomotive;

public final class SkookumSteamLocomotiveRenderer extends LegacyMeshRenderer<SkookumSteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_skookum.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_skookum.png");

    public SkookumSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "Skookum Steam Locomotive", MESH, TEXTURE,
                0.000000D, 0.594063D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.30F);
    }

    @Override
    protected float trainYaw(SkookumSteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
