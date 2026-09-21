/* Step 8.4.4 Batch B5 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.tender.MidlandCompoundSteamTender;

public final class MidlandCompoundSteamTenderRenderer extends LegacyMeshRenderer<MidlandCompoundSteamTender> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/steam_tender_midland_compound.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/steam_tender_midland_compound.png");

    public MidlandCompoundSteamTenderRenderer(EntityRendererProvider.Context context) {
        super(context, "Midland Compound Tender", MESH, TEXTURE,
                0.000000D, 0.625313D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.10F);
    }

    @Override
    protected float trainYaw(MidlandCompoundSteamTender entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
