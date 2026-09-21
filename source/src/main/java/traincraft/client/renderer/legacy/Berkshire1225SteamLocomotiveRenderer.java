/* Step 8.4.3 Batch B4 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.Berkshire1225SteamLocomotive;

public final class Berkshire1225SteamLocomotiveRenderer extends LegacyMeshRenderer<Berkshire1225SteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_berkshire_1225.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_berkshire_1225.png");

    public Berkshire1225SteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "Berkshire 1225 Steam Locomotive", MESH, TEXTURE,
                -2.000000D, -0.187187D, 0.062500D,
                0.000000F, 0.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.35F);
    }

    @Override
    protected float trainYaw(Berkshire1225SteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
