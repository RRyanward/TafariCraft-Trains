/* Step 8.4.5-B6-r1: PE multi-part rail-alignment correction. Debug intentionally remains enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.PeSteamLocomotive;

/**
 * Restores the CE 7.1 PE locomotive's separately rendered leading/trailing trucks.
 * The CE 7.1 multi-part PE render path is preserved, with the vertical origin corrected for the modern renderer.
 */
public final class PeSteamLocomotiveRenderer extends LegacyMultiMeshRenderer<PeSteamLocomotive> {
    public PeSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "PE Steam Locomotive", new Part[] {
                part(
                        new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_pe.tcm"),
                        new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_pe.png"),
                        0.000000D, 0.000000D, 0.000000D,
                        0.000000F, 0.000000F, 0.000000F,
                        1.000000F, 1.000000F, 1.000000F),
                part(
                        new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_pe_front_truck.tcm"),
                        new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_pe_front_truck.png"),
                        -1.800000D, -0.030000D, 0.000000D,
                        0.000000F, 0.000000F, 0.000000F,
                        1.000000F, 1.000000F, 1.000000F),
                part(
                        new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_pe_rear_truck.tcm"),
                        new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_pe_rear_truck.png"),
                        1.700000D, -0.030000D, 0.000000D,
                        0.000000F, 0.000000F, 0.000000F,
                        1.000000F, 1.000000F, 1.000000F)
                },
                -1.500000D, 0.590000D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.25F);
    }

    @Override
    protected float trainYaw(PeSteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
