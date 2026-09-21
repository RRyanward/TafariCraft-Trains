/* Step 8.4.5 Batch B6 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.tender.RwType2SteamTender;

public final class RwType2SteamTenderRenderer extends LegacyMeshRenderer<RwType2SteamTender> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_rw_type_2.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_rw_type_2.png");

    public RwType2SteamTenderRenderer(EntityRendererProvider.Context context) {
        super(context, "RW Type 2 Tender", MESH, TEXTURE,
                0.000000D, 0.625313D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.05F);
    }

    @Override
    protected float trainYaw(RwType2SteamTender entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
