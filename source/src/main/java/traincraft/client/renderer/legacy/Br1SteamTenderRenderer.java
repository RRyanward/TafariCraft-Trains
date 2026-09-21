/* Step 8.4.4 Batch B5 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.tender.Br1SteamTender;

public final class Br1SteamTenderRenderer extends LegacyMeshRenderer<Br1SteamTender> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_br1.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_br1.png");

    public Br1SteamTenderRenderer(EntityRendererProvider.Context context) {
        super(context, "BR1 Tender", MESH, TEXTURE,
                0.000000D, 0.437500D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.10F);
    }

    @Override
    protected float trainYaw(Br1SteamTender entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
