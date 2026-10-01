/* Step 8.4.0 Batch B1 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.tender.C41SteamTender;

public final class C41SteamTenderRenderer extends LegacyMeshRenderer<C41SteamTender> {
    private static final ResourceLocation MESH = new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_c41.tcm");
    private static final ResourceLocation TEXTURE = new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_c41.png");

    public C41SteamTenderRenderer(EntityRendererProvider.Context context) {
        super(context, "C41 Tender", MESH, TEXTURE,
                0.100000D, 0.625313D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 0.90F);
    }

    @Override protected float trainYaw(C41SteamTender entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
