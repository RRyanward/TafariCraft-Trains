/* Step 8.4.1 Batch B2 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.tender.FowlerSteamTender;

public final class FowlerSteamTenderRenderer extends LegacyMeshRenderer<FowlerSteamTender> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_fowler.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_fowler.png");

    public FowlerSteamTenderRenderer(EntityRendererProvider.Context context) {
        super(context, "Fowler 4F Tender", MESH, TEXTURE,
                -5.250000D, -0.062188D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 0.90F);
    }

    @Override
    protected float trainYaw(FowlerSteamTender entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
