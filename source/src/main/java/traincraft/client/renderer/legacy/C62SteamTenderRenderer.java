/* Step 8.4.2 Batch B3 multi-part legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.tender.C62SteamTender;

public final class C62SteamTenderRenderer extends LegacyMultiMeshRenderer<C62SteamTender> {
    public C62SteamTenderRenderer(EntityRendererProvider.Context context) {
        super(context, "C62 Tender", new Part[] {
                part(
                        new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_c62_body.tcm"),
                        new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_c62_body.png"),
                        0.000000D, 0.000000D, 0.000000D,
                        0.000000F, 0.000000F, 0.000000F,
                        1.000000F, 1.000000F, 1.000000F),
                part(
                        new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_c62_bogie.tcm"),
                        new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_c62_bogie.png"),
                        2.250000D, 0.000000D, 0.000000D,
                        0.000000F, 0.000000F, 0.000000F,
                        1.000000F, 1.000000F, 1.000000F),
                part(
                        new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_c62_bogie.tcm"),
                        new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_c62_bogie.png"),
                        0.100000D, 0.000000D, 0.000000D,
                        0.000000F, 0.000000F, 0.000000F,
                        1.000000F, 1.000000F, 1.000000F)
                },
                0.000000D, 0.000000D, 0.000000D,
                0.000000F, 0.000000F, 0.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.10F);
    }

    @Override
    protected float trainYaw(C62SteamTender entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
