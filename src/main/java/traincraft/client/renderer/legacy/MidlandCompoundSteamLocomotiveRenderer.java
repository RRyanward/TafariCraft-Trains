/* Step 8.4.4 Batch B5 legacy renderer; debug intentionally enabled. */
package traincraft.client.renderer.legacy;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import traincraft.Traincraft;
import traincraft.entity.train.steam.legacy.MidlandCompoundSteamLocomotive;

public final class MidlandCompoundSteamLocomotiveRenderer extends LegacyMeshRenderer<MidlandCompoundSteamLocomotive> {
    private static final ResourceLocation MESH =
            new ResourceLocation(Traincraft.MOD_ID, "legacy_meshes/locomotive_steam_midland_compound.tcm");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Traincraft.MOD_ID, "textures/trains/legacy/locomotive_steam_midland_compound.png");

    public MidlandCompoundSteamLocomotiveRenderer(EntityRendererProvider.Context context) {
        super(context, "Midland Compound Steam Locomotive", MESH, TEXTURE,
                -1.950000D, 0.562813D, 0.000000D,
                0.000000F, 180.000000F, 180.000000F,
                1.000000F, 1.000000F, 1.000000F, 1.30F);
    }

    @Override
    protected float trainYaw(MidlandCompoundSteamLocomotive entity, float partialTicks) {
        return entity.getRenderTrainFacingYaw(partialTicks);
    }
}
