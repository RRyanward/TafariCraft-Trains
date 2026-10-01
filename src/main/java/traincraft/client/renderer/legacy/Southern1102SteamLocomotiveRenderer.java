/* Step 8.4.1 Batch B2 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.Southern1102SteamLocomotive;

public final class Southern1102SteamLocomotiveRenderer extends LegacyMeshRenderer<Southern1102SteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_southern1102.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_southern1102.png");

    public Southern1102SteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "Southern 1102 Steam Locomotive", MESH, TEXTURE,
                -3.500000D, -0.062187D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.15F);
    }

    @Override
    protected float trainYaw(Southern1102SteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
