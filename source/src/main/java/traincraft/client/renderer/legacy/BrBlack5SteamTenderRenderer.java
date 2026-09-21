/* Step 8.4.4 Batch B5 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.tender.BrBlack5SteamTender;

public final class BrBlack5SteamTenderRenderer extends LegacyMeshRenderer<BrBlack5SteamTender> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_br_black_5.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_br_black_5.png");

    public BrBlack5SteamTenderRenderer(EntityRendererProvider.Context context) {
        super(context, "BR Black 5 Tender", MESH, TEXTURE,
                -0.050000D, 0.437500D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.10F);
    }

    @Override
    protected float trainYaw(BrBlack5SteamTender entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
