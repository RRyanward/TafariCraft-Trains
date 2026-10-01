/* Step 8.4.7 Batch B8 C11 multi-part legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.C11SteamLocomotive;

public final class C11SteamLocomotiveRenderer extends LegacyMultiMeshRenderer<C11SteamLocomotive> {
    public C11SteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "C11 Steam Locomotive", new Part[] {
                part(
                        new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_c11.tcm"),
                        new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_c11.png"),
                        0.000000D, 0.000000D, 0.000000D,
                        0.000000F, 0.000000F, 0.000000F,
                        1.000000F, 1.000000F, 1.000000F),
                part(
                        new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_c11_front_truck.tcm"),
                        new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_c11_front_truck.png"),
                        -1.600000D, 0.000000D, 0.000000D,
                        0.000000F, 0.000000F, 0.000000F,
                        1.000000F, 1.000000F, 1.000000F),
                part(
                        new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_c11_rear_truck.tcm"),
                        new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_c11_rear_truck.png"),
                        1.500000D, 0.000000D, 0.000000D,
                        0.000000F, 0.000000F, 0.000000F,
                        1.000000F, 1.000000F, 1.000000F)
                },
                -1.500000D, 0.810316D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.20F);
    }

    @Override
    protected float trainYaw(C11SteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
