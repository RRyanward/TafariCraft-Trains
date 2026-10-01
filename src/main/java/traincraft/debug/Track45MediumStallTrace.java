package traincraft.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import traincraft.Traincraft;
import traincraft.block.track.AbstractLegacyBatchRailBlock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * r2c focused trace for the one remaining runtime report:
 * locomotive enters a 45 Medium switch and stalls.
 *
 * Diagnostic only. No motion/state is changed.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Track45MediumStallTrace {
    private static final Logger LOGGER = LogManager.getLogger("TC45MediumStall");
    private static final Map<UUID, Integer> LOW_SPEED_TICKS = new HashMap<>();

    private Track45MediumStallTrace() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.level.isClientSide
                || !(event.level instanceof ServerLevel level)) {
            return;
        }

        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof AbstractMinecart cart)) {
                continue;
            }

            TrackContext context = find45Context(level, cart.blockPosition());
            if (context == null) {
                LOW_SPEED_TICKS.remove(cart.getUUID());
                continue;
            }

            double speed = cart.getDeltaMovement().horizontalDistance();
            int low = speed < 0.015D
                    ? LOW_SPEED_TICKS.merge(cart.getUUID(), 1, Integer::sum)
                    : 0;

            if (speed >= 0.015D) {
                LOW_SPEED_TICKS.remove(cart.getUUID());
            }

            if (low == 1 || low == 10 || low == 20 || low == 40) {
                ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(cart.getType());
                LOGGER.warn(
                        "[TC-45-STALL] lowTicks={} entity={} uuid={} spec={} railPos={} state={} "
                                + "cartPos=({},{},{}) motion=({},{},{}) speed={} yaw={}",
                        low,
                        entityId,
                        cart.getUUID(),
                        context.specId,
                        context.pos,
                        context.state,
                        cart.getX(), cart.getY(), cart.getZ(),
                        cart.getDeltaMovement().x,
                        cart.getDeltaMovement().y,
                        cart.getDeltaMovement().z,
                        speed,
                        cart.getYRot());
            }
        }
    }

    private static TrackContext find45Context(ServerLevel level, BlockPos center) {
        for (int dy = -1; dy <= 0; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof AbstractLegacyBatchRailBlock batch)) {
                        continue;
                    }

                    String id = batch.getSpec().id();
                    if ("track_switch_45_medium_right".equals(id)
                            || "track_switch_45_medium_left".equals(id)) {
                        return new TrackContext(id, pos.immutable(), state);
                    }
                }
            }
        }
        return null;
    }

    private record TrackContext(String specId, BlockPos pos, BlockState state) {
    }
}
