package traincraft.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import traincraft.Traincraft;
import traincraft.block.track.LightweightEnergyTrackNetwork;

@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LightweightEnergyTrackEffects {
    private static final DustParticleOptions BLUE =
            new DustParticleOptions(new Vector3f(0.12F, 0.48F, 1.00F), 1.35F);

    private LightweightEnergyTrackEffects() {}

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.level.isClientSide
                || !(event.level instanceof ServerLevel level)
                || (level.getGameTime() & 3L) != 0L) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            BlockPos center = player.blockPosition();
            for (int x = -14; x <= 14; x++) {
                for (int y = -3; y <= 3; y++) {
                    for (int z = -14; z <= 14; z++) {
                        if (((x * 31 + y * 17 + z * 13 + level.getGameTime()) & 15L) != 0L) {
                            continue;
                        }

                        BlockPos pos = center.offset(x, y, z);
                        if (!LightweightEnergyTrackNetwork.isPowered(level, pos)) {
                            continue;
                        }

                        double px = pos.getX() + 0.5D;
                        double py = pos.getY() + 0.12D;
                        double pz = pos.getZ() + 0.5D;

                        level.sendParticles(BLUE, px, py, pz,
                                2, 0.22D, 0.025D, 0.22D, 0.0D);

                        if ((level.getGameTime() + pos.asLong()) % 12L == 0L) {
                            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                                    px, py + 0.06D, pz,
                                    2, 0.20D, 0.04D, 0.20D, 0.015D);
                        }
                    }
                }
            }
        }
    }
}
