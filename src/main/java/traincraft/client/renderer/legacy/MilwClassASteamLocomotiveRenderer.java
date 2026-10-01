/* Step 8.4.1 Batch B2 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.MilwClassASteamLocomotive;

public final class MilwClassASteamLocomotiveRenderer extends LegacyMeshRenderer<MilwClassASteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_milw_class_a.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_milw_class_a.png");

    public MilwClassASteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "MILW Class A Steam Locomotive", MESH, TEXTURE,
                0.000000D, 0.562781D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                0.900000F, 0.900000F, 0.900000F, 1.15F);
    }

    @Override
    protected float trainYaw(MilwClassASteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
