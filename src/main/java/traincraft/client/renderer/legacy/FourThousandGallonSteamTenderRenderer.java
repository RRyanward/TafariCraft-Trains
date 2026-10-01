/* Step 8.4.3 Batch B4 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.tender.FourThousandGallonSteamTender;

public final class FourThousandGallonSteamTenderRenderer extends LegacyMeshRenderer<FourThousandGallonSteamTender> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_4000_gallon.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_4000_gallon.png");

    public FourThousandGallonSteamTenderRenderer(EntityRendererProvider.Context context) {
        super(context, "4000 Gallon Tender", MESH, TEXTURE,
                -4.000000D, 0.625313D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.15F);
    }

    @Override
    protected float trainYaw(FourThousandGallonSteamTender entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
