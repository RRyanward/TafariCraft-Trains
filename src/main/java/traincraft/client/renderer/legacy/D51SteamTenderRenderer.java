/* Step 8.4.2 Batch B3 multi-part legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.tender.D51SteamTender;

public final class D51SteamTenderRenderer extends LegacyMultiMeshRenderer<D51SteamTender> {
    public D51SteamTenderRenderer(EntityRendererProvider.Context context) {
        super(context, "D51 Tender", new Part[] {
                part(
                        new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_d51_body.tcm"),
                        new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_d51_body.png"),
                        0.000000D, 0.000000D, 0.000000D,
                        0.000000F, 0.000000F, 0.000000F,
                        1.000000F, 1.000000F, 1.000000F),
                part(
                        new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_c62_bogie.tcm"),
                        new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_c62_bogie.png"),
                        5.250000D, 0.800000D, 0.000000D,
                        0.000000F, 0.000000F, 0.000000F,
                        1.000000F, -1.000000F, 1.000000F),
                part(
                        new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_c62_bogie.tcm"),
                        new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_c62_bogie.png"),
                        3.250000D, 0.800000D, 0.000000D,
                        0.000000F, 0.000000F, 0.000000F,
                        1.000000F, -1.000000F, 1.000000F)
                },
                -3.000000D, 0.800000D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.10F);
    }

    @Override
    protected float trainYaw(D51SteamTender entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
