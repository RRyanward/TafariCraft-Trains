/* Step 8.4.3 Batch B4 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.tender.BerkshireSteamTender;

public final class BerkshireSteamTenderRenderer extends LegacyMeshRenderer<BerkshireSteamTender> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_berkshire.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_berkshire.png");

    public BerkshireSteamTenderRenderer(EntityRendererProvider.Context context) {
        super(context, "Berkshire Tender", MESH, TEXTURE,
                2.750000D, -0.187187D, 0.062500D,
                0.000000F, 0.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.15F);
    }

    @Override
    protected float trainYaw(BerkshireSteamTender entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
