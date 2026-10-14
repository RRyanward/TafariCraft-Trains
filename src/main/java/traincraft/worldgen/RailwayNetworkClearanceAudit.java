/*
 * TafariCraft - Trains DEV50b2d
 * READ-ONLY surface railway clearance SCREEN. Does not certify a fleet-wide
 * pass until actual rendered model bounds and switch swept paths are measured.
 * Does not modify track, movement, coupling, saved world data or entities.
 */
package traincraft.worldgen;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import traincraft.Traincraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Conservative screening bounds, NOT a substitute for per-model swept-mesh testing. */
public final class RailwayNetworkClearanceAudit {
    private static final Logger LOG = Traincraft.LOGGER;
    private static final String PREFIX = "[TC-DEV50B2D] ";

    // Provisional world-space rolling-stock envelope, deliberately larger
    // than the EntityType.sized(...) hitbox. Model-space widths, bogie yaw,
    // pantographs and locomotive-specific overhangs still require calibration.
    private static final double HALF_BODY_WIDTH = 1.50D;
    private static final double LATERAL_MARGIN = 0.35D;
    private static final double TURNOUT_SWING_RESERVE = 0.80D;
    private static final double VERTICAL_SCREEN_HEIGHT = 4.25D;
    private static final double MIN_TWO_TRACK_CENTER_GAP = 2.0D * (HALF_BODY_WIDTH + LATERAL_MARGIN);
    private static final int MAX_EXAMPLES = 8;

    private RailwayNetworkClearanceAudit() {}

    /** This method does not place blocks or load missing chunks. */
    public static int inspect(CommandSourceStack src, ServerLevel level, BlockPos center,
                              Set<BlockPos> tracks, int placementMismatches, boolean wide) {
        List<String> messages = new ArrayList<>();
        if (placementMismatches != 0) {
            messages.add("BLOCK_AUDIT_MISMATCH count=" + placementMismatches
                    + " (run /tcrailway network audit before trusting clearance data)");
        }

        // Separate *independent* parallel lanes from connected guide cells.
        // The switch throat itself is deliberately excluded from these pair
        // counts: a branching turnout is not a second train passing lane.
        int freightPairs = 0;
        int constructionPairs = 0;
        double shortestFreight = Double.POSITIVE_INFINITY;
        double shortestConstruction = Double.POSITIVE_INFINITY;
        for (int x = wide ? 2 : -10; x <= (wide ? 24 : 9); x++) {
            BlockPos main = center.offset(x, 0, 0);
            BlockPos siding = center.offset(x, 0, wide ? -5 : -3);
            if (tracks.contains(main) && tracks.contains(siding)) {
                freightPairs++;
                shortestFreight = Math.min(shortestFreight, horizontalDistance(main, siding));
            }
        }
        for (int x = wide ? 31 : 19; x <= (wide ? 36 : 24); x++) {
            BlockPos main = center.offset(x, 0, 0);
            BlockPos spur = center.offset(x, 0, wide ? 5 : 3);
            if (tracks.contains(main) && tracks.contains(spur)) {
                constructionPairs++;
                shortestConstruction = Math.min(shortestConstruction, horizontalDistance(main, spur));
            }
        }
        if (freightPairs == 0) messages.add("FREIGHT_PAIR_UNVERIFIED: expected parallel rail cells missing");
        if (constructionPairs == 0) messages.add("WIP_PAIR_UNVERIFIED: expected parallel rail cells missing");
        if (shortestFreight < MIN_TWO_TRACK_CENTER_GAP) {
            messages.add("FREIGHT_SPACING_UNSAFE centerGap=" + fmt(shortestFreight)
                    + " provisionalMin=" + fmt(MIN_TWO_TRACK_CENTER_GAP)
                    + " main vs freight siding (" + freightPairs + " sample pairs)");
        }
        if (shortestConstruction < MIN_TWO_TRACK_CENTER_GAP) {
            messages.add("WIP_SPACING_UNSAFE centerGap=" + fmt(shortestConstruction)
                    + " provisionalMin=" + fmt(MIN_TWO_TRACK_CENTER_GAP)
                    + " main vs construction spur (" + constructionPairs + " sample pairs)");
        }
        if (wide && shortestFreight >= MIN_TWO_TRACK_CENTER_GAP
                && shortestConstruction >= MIN_TWO_TRACK_CENTER_GAP) {
            messages.add("PARALLEL_LANE_SPACING_SCREEN_OK freight="+fmt(shortestFreight)
                    +" WIP="+fmt(shortestConstruction)
                    +" (turnout transition and complete train geometry NOT certified)");
        }

        int obstacleCells = 0;
        int intentionalBuffer = 0;
        int stationFenceFixtures = 0;
        int unloaded = 0;
        Set<BlockPos> seenObstacles = new HashSet<>();
        Set<BlockPos> seenUnloaded = new HashSet<>();
        List<String> examples = new ArrayList<>();
        // Audit the space around every designed track cell; do not just test
        // the center point. Turnout guide regions receive an extra swing
        // allowance. This deliberately errs on the side of warning.
        for (BlockPos rail : tracks) {
            int along = rail.getX() - center.getX();
            boolean throat = (along >= -21 && along <= -9)
                    || (along >= 8 && along <= (wide ? 32 : 20));
            double lateralLimit = HALF_BODY_WIDTH + LATERAL_MARGIN
                    + (throat ? TURNOUT_SWING_RESERVE : 0.0D);
            int radius = (int) Math.ceil(lateralLimit + 1.0D);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = 0; dy <= (int)Math.ceil(VERTICAL_SCREEN_HEIGHT); dy++) {
                        // The rail block itself and other planned guide/rail
                        // cells belong to the route, not trackside obstacles.
                        BlockPos p = rail.offset(dx, dy, dz);
                        if (tracks.contains(p)) continue;
                        if (p.getY() + 0.001D > rail.getY() + VERTICAL_SCREEN_HEIGHT) continue;
                        if (distanceToCell(rail.getX()+0.5D, rail.getZ()+0.5D,
                                p.getX(), p.getZ()) > lateralLimit) continue;
                        if (!level.hasChunkAt(p)) {
                            if (seenUnloaded.add(p)) {
                                unloaded++;
                                if (examples.size() < MAX_EXAMPLES)
                                    examples.add("UNLOADED " + p.toShortString());
                            }
                            continue;
                        }
                        BlockState state = level.getBlockState(p);
                        // Flowers and noncolliding decor do not occupy the
                        // physical swept volume. Liquids still count.
                        if (state.getCollisionShape(level, p).isEmpty()
                                && state.getFluidState().isEmpty()) continue;
                        if (seenObstacles.add(p)) {
                            boolean buffer = p.equals(center.offset(25,0,wide?-5:-3))
                                    && state.is(traincraft.registry.TCBlocks.STOPPER.get());
                            boolean fence = p.getX()==center.getX()+7
                                    && p.getZ()==center.getZ()+3
                                    && state.is(net.minecraft.world.level.block.Blocks.OAK_FENCE);
                            if(buffer) intentionalBuffer++;
                            else if(fence) stationFenceFixtures++;
                            else obstacleCells++;
                            if (examples.size() < MAX_EXAMPLES)
                                examples.add((buffer ? "INTENTIONAL_END_BUFFER "
                                        : fence ? "STATION_FENCE_SWEEP_UNVERIFIED " : "OBSTACLE ")
                                        + p.toShortString() + " " + state.getBlock());
                        }
                    }
                }
            }
        }
        if (unloaded > 0) messages.add("CHUNKS_UNLOADED checkedCells=" + unloaded);
        if (obstacleCells > 0) messages.add("NEAR_TRACK_OBSTACLES uniqueCells=" + obstacleCells);
        if (intentionalBuffer > 0) messages.add("INTENTIONAL_END_BUFFER count=" + intentionalBuffer
                + " (approach/stop distance still requires train testing)");
        if (stationFenceFixtures > 0) messages.add("STATION_FENCE_SWEEP_UNVERIFIED cells=" + stationFenceFixtures
                + " (do not infer safety from the fixture classification)");

        // There is currently no complete server-safe fleet mesh width/length
        // catalog. NEVER return a fleet-safe certification from a static
        // entity hitbox, a track-placement audit or this provisional screen.
        messages.add("FLEET_MODEL_SWEEP_UNVERIFIED: renderer mesh bounds, car length,"
                + " turnout overhang and roof clearance not yet certified");
        String header = "CLEARANCE NOT CERTIFIED station=" + center.toShortString()
                + " layout=" + (wide ? "WIDE_V2" : "LEGACY_V1")
                + " tracks=" + tracks.size()
                + " halfWidth=" + fmt(HALF_BODY_WIDTH)
                + " margin=" + fmt(LATERAL_MARGIN)
                + " curveReserve=" + fmt(TURNOUT_SWING_RESERVE)
                + " verticalScreen=" + fmt(VERTICAL_SCREEN_HEIGHT);
        LOG.warn(PREFIX + header);
        src.sendFailure(Component.literal(header));
        for (String issue : messages) {
            LOG.warn(PREFIX + issue);
            src.sendSuccess(() -> Component.literal("[rail clearance] " + issue), false);
        }
        for (String example : examples) {
            LOG.info(PREFIX + example);
            src.sendSuccess(() -> Component.literal("[rail clearance] " + example), false);
        }
        return 0; // Never claim an unmeasured model/switch is safe.
    }

    private static double horizontalDistance(BlockPos a, BlockPos b) {
        return Math.hypot(a.getX()-b.getX(), a.getZ()-b.getZ());
    }
    private static double distanceToCell(double centerX, double centerZ, int bx, int bz) {
        double dx = Math.max(0.0D, Math.max(bx - centerX, centerX - (bx+1.0D)));
        double dz = Math.max(0.0D, Math.max(bz - centerZ, centerZ - (bz+1.0D)));
        return Math.hypot(dx, dz);
    }
    private static String fmt(double x) { return String.format(Locale.ROOT, "%.2f", x); }
}
