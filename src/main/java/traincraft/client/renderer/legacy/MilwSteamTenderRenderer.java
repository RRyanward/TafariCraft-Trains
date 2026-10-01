/* Step 8.4.1 Batch B2 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.tender.MilwSteamTender;

public final class MilwSteamTenderRenderer extends LegacyMeshRenderer<MilwSteamTender> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_milw.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_milw.png");

    public MilwSteamTenderRenderer(EntityRendererProvider.Context context) {
        super(context, "MILW Tender", MESH, TEXTURE,
                0.150000D, 0.506531D, 0.025000D,
                0.000000F, 180.000000F, 180.000000F,
                0.900000F, 0.900000F, 0.900000F, 0.90F);
    }

    @Override
    protected float trainYaw(MilwSteamTender entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
