/* Step 8.4.0 Batch B1 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.Br80SteamLocomotive;

public final class Br80SteamLocomotiveRenderer extends LegacyMeshRenderer<Br80SteamLocomotive> {
    private static final ResourceLocation MESH = new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_br80.tcm");
    private static final ResourceLocation TEXTURE = new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_br80.png");

    public Br80SteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "BR80 Steam Locomotive", MESH, TEXTURE,
                -0.750000D, 0.000312D, 0.000000D,
                0.000000F, 0.000000F, 0.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.15F);
    }

    @Override protected float trainYaw(Br80SteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
