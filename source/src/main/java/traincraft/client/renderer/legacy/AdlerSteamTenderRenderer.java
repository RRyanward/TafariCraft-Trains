/* Step 8.4.0 Batch B1 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.tender.AdlerSteamTender;

public final class AdlerSteamTenderRenderer extends LegacyMeshRenderer<AdlerSteamTender> {
    private static final ResourceLocation MESH = new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_adler.tcm");
    private static final ResourceLocation TEXTURE = new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_adler.png");

    public AdlerSteamTenderRenderer(EntityRendererProvider.Context context) {
        super(context, "Adler Tender", MESH, TEXTURE,
                0.000000D, 1.500000D, 0.000000D,
                180.000000F, -90.000000F, 0.000000F,
                1.000000F, 1.000000F, 1.000000F, 0.90F);
    }

    @Override protected float trainYaw(AdlerSteamTender entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
