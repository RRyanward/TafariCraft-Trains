/* Step 8.4.0 Batch B1 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.U57SteamLocomotive;

public final class U57SteamLocomotiveRenderer extends LegacyMeshRenderer<U57SteamLocomotive> {
    private static final ResourceLocation MESH = new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_u57.tcm");
    private static final ResourceLocation TEXTURE = new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_u57.png");

    public U57SteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "U57 Steam Locomotive", MESH, TEXTURE,
                2.000000D, -0.062187D, 0.000000D,
                0.000000F, -90.000000F, 0.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.15F);
    }

    @Override protected float trainYaw(U57SteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
