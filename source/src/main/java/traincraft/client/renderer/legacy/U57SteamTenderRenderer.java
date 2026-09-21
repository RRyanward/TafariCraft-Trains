/* Step 8.4.0 Batch B1 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.tender.U57SteamTender;

public final class U57SteamTenderRenderer extends LegacyMeshRenderer<U57SteamTender> {
    private static final ResourceLocation MESH = new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_u57.tcm");
    private static final ResourceLocation TEXTURE = new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_u57.png");

    public U57SteamTenderRenderer(EntityRendererProvider.Context context) {
        super(context, "U57 Tender", MESH, TEXTURE,
                -0.093750D, -0.062187D, 0.000000D,
                0.000000F, -90.000000F, 0.000000F,
                1.000000F, 1.000000F, 1.000000F, 0.90F);
    }

    @Override protected float trainYaw(U57SteamTender entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
