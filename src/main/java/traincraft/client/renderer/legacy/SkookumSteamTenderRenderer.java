/* Step 8.4.5 Batch B6 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.tender.SkookumSteamTender;

public final class SkookumSteamTenderRenderer extends LegacyMeshRenderer<SkookumSteamTender> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_skookum.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_skookum.png");

    public SkookumSteamTenderRenderer(EntityRendererProvider.Context context) {
        super(context, "Skookum Tender", MESH, TEXTURE,
                0.000000D, 0.406563D, 0.000000D,
                0.000000F, 0.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.05F);
    }

    @Override
    protected float trainYaw(SkookumSteamTender entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
