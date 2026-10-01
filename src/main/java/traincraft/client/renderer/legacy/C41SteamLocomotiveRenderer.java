/* Step 8.4.0 Batch B1 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.C41SteamLocomotive;

public final class C41SteamLocomotiveRenderer extends LegacyMeshRenderer<C41SteamLocomotive> {
    private static final ResourceLocation MESH = new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_c41.tcm");
    private static final ResourceLocation TEXTURE = new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_c41.png");

    public C41SteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "C41 Steam Locomotive", MESH, TEXTURE,
                -3.500000D, -0.062187D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.15F);
    }

    @Override protected float trainYaw(C41SteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
