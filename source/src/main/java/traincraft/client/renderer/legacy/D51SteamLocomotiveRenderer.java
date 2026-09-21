/* Step 8.4.2 Batch B3 multi-part legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.D51SteamLocomotive;

public final class D51SteamLocomotiveRenderer extends LegacyMultiMeshRenderer<D51SteamLocomotive> {
    public D51SteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "D51 Steam Locomotive", new Part[] {
                part(
                        new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_d51_body.tcm"),
                        new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_d51_body.png"),
                        0.000000D, 0.000000D, 0.000000D,
                        0.000000F, 0.000000F, 0.000000F,
                        1.000000F, 1.000000F, 1.000000F),
                part(
                        new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_d51_front_bogie.tcm"),
                        new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_d51_front_bogie.png"),
                        0.000000D, 0.000000D, 0.000000D,
                        0.000000F, 0.000000F, 0.000000F,
                        1.000000F, 1.000000F, 1.000000F),
                part(
                        new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_d51_rear_bogie.tcm"),
                        new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_d51_rear_bogie.png"),
                        0.000000D, -0.050000D, 0.000000D,
                        0.000000F, 0.000000F, 0.000000F,
                        1.000000F, 1.000000F, 1.000000F)
                },
                0.000000D, -0.062500D, 0.000000D,
                0.000000F, 0.000000F, 0.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.30F);
    }

    @Override
    protected float trainYaw(D51SteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
