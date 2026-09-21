/* Step 8.4.2 Batch B3 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.A4MallardSteamLocomotive;

public final class A4MallardSteamLocomotiveRenderer extends LegacyMeshRenderer<A4MallardSteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_a4_mallard.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_a4_mallard.png");

    public A4MallardSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "A4 Mallard Steam Locomotive", MESH, TEXTURE,
                -3.000000D, 0.609375D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                0.975000F, 0.975000F, 0.975000F, 1.30F);
    }

    @Override
    protected float trainYaw(A4MallardSteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
