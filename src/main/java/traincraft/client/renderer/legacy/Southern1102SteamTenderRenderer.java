/* Step 8.4.1 Batch B2 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.tender.Southern1102SteamTender;

public final class Southern1102SteamTenderRenderer extends LegacyMeshRenderer<Southern1102SteamTender> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_southern1102.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_southern1102.png");

    public Southern1102SteamTenderRenderer(EntityRendererProvider.Context context) {
        super(context, "Southern 1102 Tender", MESH, TEXTURE,
                -5.250000D, 0.000000D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 0.90F);
    }

    @Override
    protected float trainYaw(Southern1102SteamTender entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
