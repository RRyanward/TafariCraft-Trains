/* Step 8.4.2 Batch B3 multi-part legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.C62SteamLocomotive;

public final class C62SteamLocomotiveRenderer extends LegacyMultiMeshRenderer<C62SteamLocomotive> {
    public C62SteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "C62 Steam Locomotive", new Part[] {
                part(
                        new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_c62_body.tcm"),
                        new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_c62_body.png"),
                        0.000000D, 0.000000D, 0.000000D,
                        0.000000F, 0.000000F, 0.000000F,
                        1.000000F, 1.000000F, 1.000000F),
                part(
                        new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_c62_front_bogie.tcm"),
                        new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_c62_front_bogie.png"),
                        -5.750000D, -0.150000D, 0.000000D,
                        0.000000F, 0.000000F, 0.000000F,
                        1.000000F, 1.000000F, 1.000000F),
                part(
                        new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_c62_rear_bogie.tcm"),
                        new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_c62_rear_bogie.png"),
                        0.000000D, 0.000000D, 0.000000D,
                        0.000000F, 0.000000F, 0.000000F,
                        1.000000F, 1.000000F, 1.000000F)
                },
                0.000000D, 0.025000D, 0.000000D,
                0.000000F, 0.000000F, 0.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.30F);
    }

    @Override
    protected float trainYaw(C62SteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
