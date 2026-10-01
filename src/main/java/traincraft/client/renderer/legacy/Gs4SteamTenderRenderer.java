/* Step 8.4.3 Batch B4 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.tender.Gs4SteamTender;

public final class Gs4SteamTenderRenderer extends LegacyMeshRenderer<Gs4SteamTender> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_gs4.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_gs4.png");

    public Gs4SteamTenderRenderer(EntityRendererProvider.Context context) {
        super(context, "GS4 Tender", MESH, TEXTURE,
                0.000000D, 0.500313D, -0.050000D,
                0.000000F, 180.000000F, 180.000000F,
                0.800000F, 1.000000F, 0.800000F, 1.15F);
    }

    @Override
    protected float trainYaw(Gs4SteamTender entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
