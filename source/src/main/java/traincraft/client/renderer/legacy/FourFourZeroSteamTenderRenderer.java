/* Step 8.4.6 Batch B7 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.tender.FourFourZeroSteamTender;

public final class FourFourZeroSteamTenderRenderer extends LegacyMeshRenderer<FourFourZeroSteamTender> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_4_4_0.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_4_4_0.png");

    public FourFourZeroSteamTenderRenderer(EntityRendererProvider.Context context) {
        super(context, "4-4-0 Steam Tender", MESH, TEXTURE,
                0.000000D, -0.000000D, 0.000000D,
                0.000000F, 90.000000F, 0.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.00F);
    }

    @Override
    protected float trainYaw(FourFourZeroSteamTender entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
