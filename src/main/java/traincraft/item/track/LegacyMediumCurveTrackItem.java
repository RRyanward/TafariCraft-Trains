package traincraft.item.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import traincraft.block.track.LegacyMediumCurveRailBlock;
import traincraft.registry.TCBlocks;

/** One-click placement item for the original approximately 3x3 medium curve. */
public final class LegacyMediumCurveTrackItem extends Item {
    // TC_BATCH8_R4A3F_MEDIUM_CURVE_MATERIAL
    private final traincraft.block.track.TrackMaterial material;

    public LegacyMediumCurveTrackItem(Properties properties) {
        this(properties, traincraft.block.track.TrackMaterial.NORMAL);
    }

    public LegacyMediumCurveTrackItem(
            Properties properties,
            traincraft.block.track.TrackMaterial material) {
        super(properties);
        this.material = material == null
                ? traincraft.block.track.TrackMaterial.NORMAL
                : material;
    }

    private static final double ENDPOINT_TARGET_RADIUS = 0.90D;
    // TC_BATCH8_R3ZJ_MEDIUM_CROSS_FAMILY_CONTINUATION
    // Ground continuation uses actual open endpoints across simple track families.
    // TC_BATCH8_R3ZI_NEAR_END_CONTINUATION_CAPTURE
    // Wider acquisition only; still restricted to real open endpoint parts 0/4.

    private static final class SnapTarget {
        private final BlockPos pos;
        private final BlockState state;
        private final String mode;
        private final double horizontalDistance;

        private SnapTarget(BlockPos pos, BlockState state,
                           String mode, double horizontalDistance) {
            this.pos = pos;
            this.state = state;
            this.mode = mode;
            this.horizontalDistance = horizontalDistance;
        }
    }

    private static final class SnapPlacement {
        private final BlockPos root;
        private final Direction facing;
        private final int attachedPart;

        private SnapPlacement(BlockPos root, Direction facing, int attachedPart) {
            this.root = root;
            this.facing = facing;
            this.attachedPart = attachedPart;
        }
    }

    /**
     * The five-part NORTH canonical curve has two external connectors:
     * part 0 exits WEST (counter-clockwise from assembly facing),
     * part 4 exits SOUTH (opposite assembly facing).
     * Rotating those relationships gives the external connector direction
     * for every assembly facing.
     */
    private static Direction externalDirection(Direction facing, int endpointPart) {
        if (endpointPart == 0) {
            return facing.getCounterClockWise();
        }
        if (endpointPart == LegacyMediumCurveRailBlock.PART_COUNT - 1) {
            return facing.getOpposite();
        }
        throw new IllegalArgumentException("Not a medium-curve endpoint part: " + endpointPart);
    }

    private static BlockPos rootForExistingCurve(BlockPos segmentPos, BlockState segmentState) {
        Direction facing = segmentState.getValue(LegacyMediumCurveRailBlock.ASSEMBLY_FACING);
        int part = segmentState.getValue(LegacyMediumCurveRailBlock.PART);
        BlockPos offset = LegacyMediumCurveRailBlock.offsetForPart(BlockPos.ZERO, facing, part);
        return segmentPos.subtract(offset);
    }

    private static int nearestAssemblyEndpoint(Player player, BlockPos root, Direction facing,
                                               int clickedPart) {
        int lastPart = LegacyMediumCurveRailBlock.PART_COUNT - 1;
        if (clickedPart == 0 || clickedPart == lastPart) {
            return clickedPart;
        }

        BlockPos p0 = LegacyMediumCurveRailBlock.offsetForPart(root, facing, 0);
        BlockPos p4 = LegacyMediumCurveRailBlock.offsetForPart(root, facing, lastPart);

        double d0 = player.distanceToSqr(
                p0.getX() + 0.5D, p0.getY() + 0.5D, p0.getZ() + 0.5D);
        double d4 = player.distanceToSqr(
                p4.getX() + 0.5D, p4.getY() + 0.5D, p4.getZ() + 0.5D);
        return d0 <= d4 ? 0 : lastPart;
    }

    private static SnapPlacement candidateAttachedAt(BlockPos newEndpointSegment,
                                                     Direction existingOutward,
                                                     int newEndpointPart) {
        Direction desiredExternal = existingOutward.getOpposite();
        Direction newFacing;

        if (newEndpointPart == 0) {
            // part-0 external = facing.counterClockWise()
            // therefore facing = desiredExternal.clockWise()
            newFacing = desiredExternal.getClockWise();
        } else if (newEndpointPart == LegacyMediumCurveRailBlock.PART_COUNT - 1) {
            // part-4 external = facing.opposite()
            // therefore facing = desiredExternal.opposite() = existingOutward
            newFacing = existingOutward;
        } else {
            throw new IllegalArgumentException("Invalid snap endpoint part: " + newEndpointPart);
        }

        BlockPos offset = LegacyMediumCurveRailBlock.offsetForPart(
                BlockPos.ZERO, newFacing, newEndpointPart);
        return new SnapPlacement(newEndpointSegment.subtract(offset), newFacing, newEndpointPart);
    }

    private static boolean canPlaceAssembly(Level level, Player player, ItemStack stack,
                                            BlockPos root, Direction facing) {
        for (int part = 0; part < LegacyMediumCurveRailBlock.PART_COUNT; part++) {
            BlockPos railPos = LegacyMediumCurveRailBlock.offsetForPart(root, facing, part);
            BlockPos supportPos = railPos.below();

            if (!level.getBlockState(railPos).isAir()
                    || !level.getBlockState(supportPos).isFaceSturdy(level, supportPos, Direction.UP)
                    || !player.mayUseItemAt(railPos, Direction.UP, stack)) {
                return false;
            }
        }
        return true;
    }

    private static int facingPenalty(Direction candidateFacing, Direction playerFacing) {
        if (candidateFacing == playerFacing) {
            return 0;
        }
        if (candidateFacing.getOpposite() == playerFacing) {
            return 2;
        }
        return 1;
    }

    private static boolean closesIntoExistingCurve(SnapPlacement candidate,
                                                    int attachedPart,
                                                    Level level) {
        int lastPart = LegacyMediumCurveRailBlock.PART_COUNT - 1;
        int freePart = attachedPart == 0 ? lastPart : 0;
        BlockPos freeSegment = LegacyMediumCurveRailBlock.offsetForPart(
                candidate.root, candidate.facing, freePart);
        Direction freeOut = externalDirection(candidate.facing, freePart);
        BlockPos neighborPos = freeSegment.relative(freeOut);
        BlockState neighbor = level.getBlockState(neighborPos);

        if (!(neighbor.getBlock() instanceof LegacyMediumCurveRailBlock)) {
            return false;
        }

        int neighborPart = neighbor.getValue(LegacyMediumCurveRailBlock.PART);
        if (neighborPart != 0 && neighborPart != lastPart) {
            return false;
        }

        Direction neighborFacing =
                neighbor.getValue(LegacyMediumCurveRailBlock.ASSEMBLY_FACING);
        return externalDirection(neighborFacing, neighborPart) == freeOut.getOpposite();
    }

    private static SnapTarget resolveCurveSnapTarget(Level level,
                                                     UseOnContext context,
                                                     BlockPos clickedPos,
                                                     BlockState clickedState) {
        if (clickedState.getBlock() instanceof LegacyMediumCurveRailBlock) {
            return new SnapTarget(clickedPos, clickedState,
                    "direct_curve_block", 0.0D);
        }

        Vec3 click = context.getClickLocation();
        int lastPart = LegacyMediumCurveRailBlock.PART_COUNT - 1;
        double maxDistanceSq = ENDPOINT_TARGET_RADIUS * ENDPOINT_TARGET_RADIUS;
        SnapTarget best = null;
        double bestDistanceSq = Double.POSITIVE_INFINITY;

        // Only endpoint parts are eligible. Interior guide blocks must never
        // steal a click intended for an exposed curve connector.
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos pos = clickedPos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof LegacyMediumCurveRailBlock)) {
                        continue;
                    }

                    int part = state.getValue(LegacyMediumCurveRailBlock.PART);
                    if (part != 0 && part != lastPart) {
                        continue;
                    }

                    Direction facing =
                            state.getValue(LegacyMediumCurveRailBlock.ASSEMBLY_FACING);
                    Direction outward = externalDirection(facing, part);

                    // True logical connector point: center of the endpoint
                    // RailBlock face that the curve exits through.
                    double connectorX = pos.getX() + 0.5D + outward.getStepX() * 0.5D;
                    double connectorZ = pos.getZ() + 0.5D + outward.getStepZ() * 0.5D;

                    double ddx = click.x - connectorX;
                    double ddz = click.z - connectorZ;
                    double distanceSq = ddx * ddx + ddz * ddz;

                    if (distanceSq <= maxDistanceSq
                            && distanceSq < bestDistanceSq) {
                        bestDistanceSq = distanceSq;
                        best = new SnapTarget(pos, state,
                                "nearby_endpoint_connector",
                                Math.sqrt(distanceSq));
                    }
                }
            }
        }

        return best;
    }

    private static SnapPlacement findCurveEndpointSnap(Level level, Player player,
                                                       ItemStack stack, BlockPos clickedPos,
                                                       BlockState clickedState) {
        if (!(clickedState.getBlock() instanceof LegacyMediumCurveRailBlock)) {
            return null;
        }

        Direction existingFacing =
                clickedState.getValue(LegacyMediumCurveRailBlock.ASSEMBLY_FACING);
        int clickedPart = clickedState.getValue(LegacyMediumCurveRailBlock.PART);
        BlockPos existingRoot = rootForExistingCurve(clickedPos, clickedState);

        int existingEndpointPart = nearestAssemblyEndpoint(
                player, existingRoot, existingFacing, clickedPart);
        BlockPos existingEndpointSegment = LegacyMediumCurveRailBlock.offsetForPart(
                existingRoot, existingFacing, existingEndpointPart);
        Direction outward = externalDirection(existingFacing, existingEndpointPart);

        // The new curve's endpoint segment belongs in the block immediately
        // outside the existing endpoint. Two mathematically valid bends exist:
        // attach the new curve by its part 0 or by its part 4.
        BlockPos newEndpointSegment = existingEndpointSegment.relative(outward);

        SnapPlacement byPart0 = candidateAttachedAt(
                newEndpointSegment, outward, 0);
        SnapPlacement byPart4 = candidateAttachedAt(
                newEndpointSegment, outward, LegacyMediumCurveRailBlock.PART_COUNT - 1);

        boolean part0Valid = canPlaceAssembly(
                level, player, stack, byPart0.root, byPart0.facing);
        boolean part4Valid = canPlaceAssembly(
                level, player, stack, byPart4.root, byPart4.facing);

        if (!part0Valid && !part4Valid) {
            return null;
        }
        if (part0Valid && !part4Valid) {
            return byPart0;
        }
        if (part4Valid && !part0Valid) {
            return byPart4;
        }

        // When closing a loop, prefer the candidate whose OTHER free endpoint
        // also lands on an existing medium-curve endpoint.
        boolean part0Closes = closesIntoExistingCurve(byPart0, byPart0.attachedPart, level);
        boolean part4Closes = closesIntoExistingCurve(byPart4, byPart4.attachedPart, level);

        SnapPlacement preferred;
        SnapPlacement alternate;

        if (part0Closes != part4Closes) {
            preferred = part0Closes ? byPart0 : byPart4;
            alternate = part0Closes ? byPart4 : byPart0;
        } else {
            int p0 = facingPenalty(byPart0.facing, player.getDirection());
            int p4 = facingPenalty(byPart4.facing, player.getDirection());
            if (p0 <= p4) {
                preferred = byPart0;
                alternate = byPart4;
            } else {
                preferred = byPart4;
                alternate = byPart0;
            }
        }

        // Sneak gives the player an explicit way to choose the other valid bend.
        return player.isShiftKeyDown() ? alternate : preferred;
    }

    private static final DateTimeFormatter SNAP_DEBUG_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private static String posString(BlockPos pos) {
        return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    private static String vecString(Vec3 vec) {
        return String.format(java.util.Locale.ROOT, "(%.4f,%.4f,%.4f)",
                vec.x, vec.y, vec.z);
    }

    private static void addCandidateDetails(List<String> lines, String label,
                                            SnapPlacement candidate,
                                            int attachedPart,
                                            Level level, Player player,
                                            ItemStack stack) {
        if (candidate == null) {
            lines.add(label + "=null");
            return;
        }

        int lastPart = LegacyMediumCurveRailBlock.PART_COUNT - 1;
        int freePart = attachedPart == 0 ? lastPart : 0;
        BlockPos attachedSegment = LegacyMediumCurveRailBlock.offsetForPart(
                candidate.root, candidate.facing, attachedPart);
        BlockPos freeSegment = LegacyMediumCurveRailBlock.offsetForPart(
                candidate.root, candidate.facing, freePart);
        Direction attachedOut = externalDirection(candidate.facing, attachedPart);
        Direction freeOut = externalDirection(candidate.facing, freePart);
        BlockPos attachedNeighbor = attachedSegment.relative(attachedOut);
        BlockPos freeNeighbor = freeSegment.relative(freeOut);

        lines.add(label + ".root=" + posString(candidate.root));
        lines.add(label + ".facing=" + candidate.facing);
        lines.add(label + ".attachedPart=" + attachedPart);
        lines.add(label + ".attachedSegment=" + posString(attachedSegment));
        lines.add(label + ".attachedOutward=" + attachedOut);
        lines.add(label + ".attachedNeighbor=" + posString(attachedNeighbor)
                + " state=" + level.getBlockState(attachedNeighbor));
        lines.add(label + ".freePart=" + freePart);
        lines.add(label + ".freeSegment=" + posString(freeSegment));
        lines.add(label + ".freeOutward=" + freeOut);
        lines.add(label + ".freeNeighbor=" + posString(freeNeighbor)
                + " state=" + level.getBlockState(freeNeighbor));
        lines.add(label + ".canPlace=" + canPlaceAssembly(
                level, player, stack, candidate.root, candidate.facing));
        lines.add(label + ".closesIntoExistingCurve=" + closesIntoExistingCurve(
                candidate, attachedPart, level));
        lines.add(label + ".facingPenalty=" + facingPenalty(
                candidate.facing, player.getDirection()));
    }

    private static void writeSnapAttemptDebug(Level level, Player player,
                                              ItemStack stack, UseOnContext context,
                                              BlockPos clickedPos,
                                              BlockState clickedState,
                                              SnapTarget snapTarget,
                                              SnapPlacement chosenSnap,
                                              BlockPos finalRoot,
                                              Direction finalFacing) {
        if (level.isClientSide) {
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add("TRAINCRAFT STEP 9.2b-r3k-d1 CURVE SNAP ATTEMPT");
        lines.add("time=" + LocalDateTime.now());
        lines.add("clickedPos=" + posString(clickedPos));
        lines.add("clickedFace=" + context.getClickedFace());
        lines.add("clickLocation=" + vecString(context.getClickLocation()));
        lines.add("clickedState=" + clickedState);
        lines.add("clickedIsMediumCurve="
                + (clickedState.getBlock() instanceof LegacyMediumCurveRailBlock));
        lines.add("snapTargetMode=" + (snapTarget == null ? "none" : snapTarget.mode));
        if (snapTarget != null) {
            lines.add("snapTargetPos=" + posString(snapTarget.pos));
            lines.add("snapTargetState=" + snapTarget.state);
            lines.add(String.format(java.util.Locale.ROOT,
                    "snapTargetHorizontalDistance=%.6f",
                    snapTarget.horizontalDistance));
        }
        lines.add("playerPos=" + vecString(player.position()));
        lines.add("playerFacing=" + player.getDirection());
        lines.add("playerSneaking=" + player.isShiftKeyDown());
        lines.add("finalRoot=" + posString(finalRoot));
        lines.add("finalFacing=" + finalFacing);
        lines.add("");

        if (snapTarget != null) {
            BlockPos targetPos = snapTarget.pos;
            BlockState targetState = snapTarget.state;
            Direction existingFacing =
                    targetState.getValue(LegacyMediumCurveRailBlock.ASSEMBLY_FACING);
            int clickedPart = targetState.getValue(LegacyMediumCurveRailBlock.PART);
            BlockPos existingRoot = rootForExistingCurve(targetPos, targetState);
            int selectedEndpoint = nearestAssemblyEndpoint(
                    player, existingRoot, existingFacing, clickedPart);
            BlockPos selectedSegment = LegacyMediumCurveRailBlock.offsetForPart(
                    existingRoot, existingFacing, selectedEndpoint);
            Direction outward = externalDirection(existingFacing, selectedEndpoint);
            BlockPos newEndpointSegment = selectedSegment.relative(outward);

            BlockPos p0 = LegacyMediumCurveRailBlock.offsetForPart(
                    existingRoot, existingFacing, 0);
            int lastPart = LegacyMediumCurveRailBlock.PART_COUNT - 1;
            BlockPos p4 = LegacyMediumCurveRailBlock.offsetForPart(
                    existingRoot, existingFacing, lastPart);

            double d0 = player.distanceToSqr(
                    p0.getX() + 0.5D, p0.getY() + 0.5D, p0.getZ() + 0.5D);
            double d4 = player.distanceToSqr(
                    p4.getX() + 0.5D, p4.getY() + 0.5D, p4.getZ() + 0.5D);

            lines.add("EXISTING_CURVE");
            lines.add("existingRoot=" + posString(existingRoot));
            lines.add("existingFacing=" + existingFacing);
            lines.add("clickedPart=" + clickedPart);
            lines.add("part0Segment=" + posString(p0) + " playerDistSq=" + d0);
            lines.add("part4Segment=" + posString(p4) + " playerDistSq=" + d4);
            lines.add("selectedEndpointPart=" + selectedEndpoint);
            lines.add("selectedEndpointSegment=" + posString(selectedSegment));
            lines.add("selectedEndpointOutward=" + outward);
            lines.add("requiredNewEndpointSegment=" + posString(newEndpointSegment));
            lines.add("");

            SnapPlacement byPart0 = candidateAttachedAt(
                    newEndpointSegment, outward, 0);
            SnapPlacement byPart4 = candidateAttachedAt(
                    newEndpointSegment, outward, lastPart);

            addCandidateDetails(lines, "candidatePart0",
                    byPart0, 0, level, player, stack);
            lines.add("");
            addCandidateDetails(lines, "candidatePart4",
                    byPart4, lastPart, level, player, stack);
            lines.add("");

            if (chosenSnap == null) {
                lines.add("CHOSEN_SNAP=null");
            } else {
                lines.add("CHOSEN_SNAP.root=" + posString(chosenSnap.root));
                lines.add("CHOSEN_SNAP.facing=" + chosenSnap.facing);
                lines.add("CHOSEN_SNAP.attachedPart=" + chosenSnap.attachedPart);
            }
        } else {
            lines.add("MANUAL_FIRST_PLACEMENT");
            lines.add("Reason: click was not on a medium-curve guide block and no");
            lines.add("endpoint connector was within ENDPOINT_TARGET_RADIUS="
                    + ENDPOINT_TARGET_RADIUS + " block horizontally.");
            lines.add("The clicked block now anchors assembly PART 0 exactly at");
            lines.add("clickedPos.above(); the hidden root is solved with offsetForPart.");
            lines.add("");
        }

        lines.add("NEARBY_MEDIUM_CURVE_SEGMENTS radius=6");
        final Vec3 click = context.getClickLocation();
        final class Nearby {
            private final BlockPos pos;
            private final BlockState state;
            private final double distSq;

            private Nearby(BlockPos pos, BlockState state, double distSq) {
                this.pos = pos;
                this.state = state;
                this.distSq = distSq;
            }
        }

        List<Nearby> nearby = new ArrayList<>();
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    BlockPos p = clickedPos.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);
                    if (!(s.getBlock() instanceof LegacyMediumCurveRailBlock)) {
                        continue;
                    }
                    double cx = p.getX() + 0.5D;
                    double cy = p.getY() + 0.5D;
                    double cz = p.getZ() + 0.5D;
                    double ds = click.distanceToSqr(cx, cy, cz);
                    nearby.add(new Nearby(p, s, ds));
                }
            }
        }

        nearby.sort(Comparator.comparingDouble(n -> n.distSq));
        int shown = Math.min(20, nearby.size());
        for (int i = 0; i < shown; i++) {
            Nearby n = nearby.get(i);
            lines.add(String.format(java.util.Locale.ROOT,
                    "%02d pos=%s dist=%.4f part=%d facing=%s rawShape=%s",
                    i,
                    posString(n.pos),
                    Math.sqrt(n.distSq),
                    n.state.getValue(LegacyMediumCurveRailBlock.PART),
                    n.state.getValue(LegacyMediumCurveRailBlock.ASSEMBLY_FACING),
                    n.state.getValue(RailBlock.SHAPE)));
        }
        if (nearby.isEmpty()) {
            lines.add("NONE");
        }

        Path dir = FMLPaths.GAMEDIR.get()
                .resolve("logs")
                .resolve("traincraft-track-debug");
        try {
            Files.createDirectories(dir);
            String name = "curve-snap-"
                    + LocalDateTime.now().format(SNAP_DEBUG_TIME)
                    + ".txt";
            Path report = dir.resolve(name);
            Files.write(report, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            player.displayClientMessage(
                    Component.literal("Curve snap debug: " + report.toAbsolutePath()),
                    false);
        } catch (IOException e) {
            player.displayClientMessage(
                    Component.literal("Curve snap debug write FAILED: " + e.getMessage()),
                    false);
        }
    }

    
    // TC_FINAL_RELEASE_R1G_R3ZO_REAL_PREVIEW_PARITY
    // Read-only: shares the accepted r3zo manual continuation resolver with
    // useOn(); it never changes the world.
    public record PlacementPreview(
            BlockPos root,
            Direction facing) {}

    public static PlacementPreview previewPlacement(
            Level level,
            Player player,
            ItemStack stack,
            net.minecraft.world.phys.BlockHitResult hit) {
                boolean clickedRail =
                level != null
                        && hit != null
                        && net.minecraft.world.level.block.BaseRailBlock.isRail(
                                level.getBlockState(hit.getBlockPos()));

        if (level == null
                || player == null
                || stack == null
                || stack.isEmpty()
                || hit == null
                || (hit.getDirection() != Direction.UP
                    && !clickedRail)) {
            return null;
        }

        BlockPos manualRoot =
                hit.getBlockPos().above();
        Direction manualFacing =
                player.getDirection();

        // TC_FINAL_RELEASE_R1H_DIRECT_RAIL_REQUEST_CELL
        BlockPos requestedCell =
                tcRequestedContinuationCell(
                        level,
                        hit.getBlockPos());

        TcContinuationPlacement continuation =
                tcFindMediumContinuation(
                        level,
                        requestedCell,
                        hit.getLocation(),
                        player,
                        stack);

        if (continuation != null) {
            return new PlacementPreview(
                    continuation.root,
                    continuation.facing);
        }

        if (!tcCanPlaceMediumCurve(
                level,
                player,
                stack,
                manualRoot,
                manualFacing)) {
            return null;
        }

        return new PlacementPreview(
                manualRoot,
                manualFacing);
    }
@Override
    public InteractionResult useOn(UseOnContext context) {
        // TC_FINAL_RELEASE_R1K_RAIL_SIDE_CLICK_PARITY
        Level level =
                context.getLevel();
        BlockPos tcEntryClickedPos =
                context.getClickedPos();
        boolean tcEntryClickedRail =
                net.minecraft.world.level.block.BaseRailBlock.isRail(
                        level.getBlockState(tcEntryClickedPos));
        ItemStack tcEntryStack =
                context.getItemInHand();

        traincraft.Traincraft.LOGGER.info(
                "[TC-FINAL-MEDIUM-R1K-ENTER] side={} face={} clicked={} clickedRail={} item={} class={}",
                level.isClientSide ? "CLIENT" : "SERVER",
                context.getClickedFace(),
                tcEntryClickedPos,
                tcEntryClickedRail,
                net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(
                        tcEntryStack.getItem()),
                tcEntryStack.getItem().getClass().getName());

        // Normal ground/support placement stays top-face-only.
        // Direct interaction with an existing rail may arrive through a side
        // face because rails are thin; allow that rail click to reach the same
        // accepted r3zo endpoint resolver.
        if (context.getClickedFace() != Direction.UP
                && !tcEntryClickedRail) {
            return InteractionResult.PASS;
        }

        Player player =
                context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = context.getItemInHand();
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);

        Direction facing = player.getDirection();

        // Manual/first placement: the block the player clicks is the visible
        // START of the curve, not the hidden assembly root.
        //
        // Anchor part 0 exactly at clickedPos.above(), then solve the root from
        // the same assembly geometry used everywhere else. This removes the
        // several-block visual jump without introducing any magic offset.
        BlockPos manualAnchor = clickedPos.above();
        BlockPos part0Offset = LegacyMediumCurveRailBlock.offsetForPart(
                BlockPos.ZERO, facing, 0);
        BlockPos root = manualAnchor.subtract(part0Offset);

        SnapTarget snapTarget = resolveCurveSnapTarget(
                level, context, clickedPos, clickedState);

        SnapPlacement chosenSnap = null;
        if (snapTarget != null) {
            chosenSnap = findCurveEndpointSnap(
                    level, player, stack, snapTarget.pos, snapTarget.state);
            if (chosenSnap == null) {
                writeSnapAttemptDebug(level, player, stack, context,
                        clickedPos, clickedState, snapTarget,
                        null, root, facing);
                return InteractionResult.FAIL;
            }
            root = chosenSnap.root;
            facing = chosenSnap.facing;
        }

        writeSnapAttemptDebug(level, player, stack, context,
                clickedPos, clickedState, snapTarget,
                chosenSnap, root, facing);

        if (!canPlaceAssembly(level, player, stack, root, facing)) {
            return InteractionResult.FAIL;
        }

        LegacyMediumCurveRailBlock block =
                (LegacyMediumCurveRailBlock) TCBlocks.TRACK_MEDIUM_CURVE_SEGMENT.get();
        // TC_FINAL_RELEASE_R1G_R3ZO_MANUAL_SNAP_RESTORE
        // Keep the runtime-accepted r3zj/r3zm/r3zo endpoint + C/S-hand solver
        // authoritative for real held Medium Curve placement. The only release
        // correction here is to enter that proven solver when the player clicks
        // the endpoint rail itself as well as when clicking adjacent ground.
        BlockPos tcRequestedCell =
                tcRequestedContinuationCell(
                        level,
                        context.getClickedPos());

        traincraft.Traincraft.LOGGER.info(
                "[TC-FINAL-MEDIUM-R1H-REQUEST] clicked={} clickedIsRail={} requested={} click={}",
                context.getClickedPos(),
                net.minecraft.world.level.block.BaseRailBlock.isRail(
                        level.getBlockState(context.getClickedPos())),
                tcRequestedCell,
                context.getClickLocation());

        TcContinuationPlacement tcContinuation =
                tcFindMediumContinuation(
                        level,
                        tcRequestedCell,
                        context.getClickLocation(),
                        player,
                        stack);
        if (tcContinuation != null) {
            root = tcContinuation.root;
            facing = tcContinuation.facing;
            traincraft.Traincraft.LOGGER.info(
                    "[TC-R3ZJ-MEDIUM-ENDPOINT] requested={} source={} sourceSegment={} sourceOut={} sourcePart={} attachPart={} root={} facing={} handoff={}",
                    context.getClickedPos().above(),
                    tcContinuation.sourceId,
                    tcContinuation.sourceSegment,
                    tcContinuation.sourceOutward,
                    tcContinuation.sourceEndpointPart,
                    tcContinuation.attachedPart,
                    root,
                    facing,
                    tcContinuation.handoff);
            traincraft.Traincraft.LOGGER.info(
                    "[TC-FINAL-MEDIUM-R3ZO-RESTORE] source={} root={} facing={} attachPart={} shift={}",
                    tcContinuation.sourceId,
                    root,
                    facing,
                    tcContinuation.attachedPart,
                    player.isShiftKeyDown());
        }
for (int part = 0; part < LegacyMediumCurveRailBlock.PART_COUNT; part++) {
            BlockPos railPos = LegacyMediumCurveRailBlock.offsetForPart(root, facing, part);
            BlockState state = block.defaultBlockState()
                    .setValue(RailBlock.SHAPE, LegacyMediumCurveRailBlock.shapeForPart(facing, part))
                    .setValue(LegacyMediumCurveRailBlock.ASSEMBLY_FACING, facing)
                    .setValue(LegacyMediumCurveRailBlock.PART, part);
            // First pass notifies neighboring rails so normal track at either end
            // can discover the dedicated curve assembly.
            level.setBlock(railPos, state, 3);
        }

        // Vanilla rail negotiation can rewrite the internal corner states while the
        // five blocks are being inserted one-by-one. Finalize the exact assembly path
        // without another neighbor cascade; getRailDirection() also locks movement to
        // these canonical shapes if a later neighbor update rewrites SHAPE visually.
        for (int part = 0; part < LegacyMediumCurveRailBlock.PART_COUNT; part++) {
            BlockPos railPos = LegacyMediumCurveRailBlock.offsetForPart(root, facing, part);
            BlockState current = level.getBlockState(railPos);
            if (current.getBlock() == block) {
                level.setBlock(railPos, current
                        .setValue(RailBlock.SHAPE, LegacyMediumCurveRailBlock.shapeForPart(facing, part))
                        .setValue(LegacyMediumCurveRailBlock.ASSEMBLY_FACING, facing)
                        .setValue(LegacyMediumCurveRailBlock.PART, part), 2);
            }
        }

        // TC_BATCH8_R4A3F_MEDIUM_CURVE_MATERIAL
        // Placement geometry above is unchanged; assign only the root material record.
        net.minecraft.world.level.block.entity.BlockEntity materialEntity =
                level.getBlockEntity(root);
        if (materialEntity instanceof traincraft.block.track.TrackMaterialBlockEntity materialData) {
            materialData.setMaterial(material);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, root, SoundEvents.METAL_PLACE,
                SoundSource.BLOCKS, 0.8F, 1.0F);
        return InteractionResult.CONSUME;
    }

    private static final class TcSourceEndpoint {
        private final BlockPos segment;
        private final Direction outward;
        private final String sourceId;
        private final int sourceEndpointPart;
        private final int handoff;
        private final double distanceSq;

        private TcSourceEndpoint(BlockPos segment, Direction outward,
                                 String sourceId, int sourceEndpointPart,
                                 int handoff, double distanceSq) {
            this.segment = segment;
            this.outward = outward;
            this.sourceId = sourceId;
            this.sourceEndpointPart = sourceEndpointPart;
            this.handoff = handoff;
            this.distanceSq = distanceSq;
        }
    }

    private static final class TcContinuationPlacement {
        private final BlockPos root;
        private final Direction facing;
        private final String sourceId;
        private final BlockPos sourceSegment;
        private final Direction sourceOutward;
        private final int sourceEndpointPart;
        private final int attachedPart;
        private final int handoff;
        private final int score;

        private TcContinuationPlacement(BlockPos root, Direction facing,
                                        TcSourceEndpoint source,
                                        int attachedPart, int score) {
            this.root = root;
            this.facing = facing;
            this.sourceId = source.sourceId;
            this.sourceSegment = source.segment;
            this.sourceOutward = source.outward;
            this.sourceEndpointPart = source.sourceEndpointPart;
            this.attachedPart = attachedPart;
            this.handoff = source.handoff;
            this.score = score;
        }
    }

    // TC_BATCH8_R3ZM_MEDIUM_STRAIGHT_HAND
    // Preserve r3zj local endpoint acquisition. For a straight/normal/batch
    // source, Shift chooses the alternate valid bend so both C directions are
    // placeable. Medium->Medium keeps its accepted endpoint-role preference.
    // TC_FINAL_RELEASE_R1H_DIRECT_RAIL_REQUEST_CELL
    // r3zj's accepted acquisition was authored for the rail cell that should
    // receive/continue the route. For a ground click that remains above().
    // For a direct click on an existing rail endpoint, using above() shifts
    // acquisition one Y-cell away from the source. Keep the clicked rail cell.
    private static BlockPos tcRequestedContinuationCell(
            Level level,
            BlockPos clickedPos) {
        if (level != null
                && clickedPos != null
                && net.minecraft.world.level.block.BaseRailBlock.isRail(
                        level.getBlockState(clickedPos))) {
            return clickedPos;
        }
        return clickedPos == null
                ? BlockPos.ZERO
                : clickedPos.above();
    }
    private static TcContinuationPlacement tcFindMediumContinuation(
            Level level, BlockPos requestedCell,
            net.minecraft.world.phys.Vec3 click,
            Player player, ItemStack stack) {

        TcSourceEndpoint source = tcFindLocalOpenSource(level, requestedCell, click);
        if (source == null) return null;

        BlockPos requiredNewEndpoint = source.segment.relative(source.outward);
        int lastPart = LegacyMediumCurveRailBlock.PART_COUNT - 1;
        java.util.ArrayList<TcContinuationPlacement> placementCandidates =
                new java.util.ArrayList<>();

        for (Direction candidateFacing : Direction.Plane.HORIZONTAL) {
            for (int attachedPart : new int[]{0, lastPart}) {
                Direction attachedOutward = attachedPart == 0
                        ? candidateFacing.getCounterClockWise()
                        : candidateFacing.getOpposite();
                if (attachedOutward != source.outward.getOpposite()) continue;

                BlockPos candidateRoot = requiredNewEndpoint.subtract(
                        LegacyMediumCurveRailBlock.offsetForPart(
                                BlockPos.ZERO, candidateFacing, attachedPart));
                if (!tcCanPlaceMediumCurve(level, player, stack,
                        candidateRoot, candidateFacing)) continue;

                int rolePenalty = 0;
                if ("legacy_medium_curve".equals(source.sourceId)
                        && source.sourceEndpointPart >= 0) {
                    int preferredAttached = source.sourceEndpointPart == 0
                            ? lastPart : 0;
                    rolePenalty = attachedPart == preferredAttached ? 0 : 100;
                }

                int facingPenalty =
                        tcFacingPenalty(candidateFacing, player.getDirection());
                int score = rolePenalty + source.handoff * 10 + facingPenalty;
                placementCandidates.add(new TcContinuationPlacement(
                        candidateRoot, candidateFacing, source,
                        attachedPart, score));
            }
        }

        if (placementCandidates.isEmpty()) return null;

        placementCandidates.sort(java.util.Comparator
                .comparingInt((TcContinuationPlacement c) -> c.score)
                .thenComparingInt(c -> c.attachedPart)
                .thenComparingInt(c -> c.facing.get2DDataValue()));

        // TC_BATCH8_R3ZO_MEDIUM_S_BEND_HAND
        // Default remains the preferred same-hand continuation (C bend).
        // Holding Shift selects the alternate valid continuation even when the
        // source is another Medium Curve, allowing mirrored S bends from either
        // a right-going or left-going first curve.
        boolean alternateHand = player.isShiftKeyDown()
                && placementCandidates.size() > 1;

        TcContinuationPlacement selected = alternateHand
                ? placementCandidates.get(1)
                : placementCandidates.get(0);

        traincraft.Traincraft.LOGGER.info(
                "[TC-R3ZM-MEDIUM-HAND] source={} shiftAlternate={} candidateCount={} attachPart={} root={} facing={}",
                source.sourceId,
                alternateHand,
                placementCandidates.size(),
                selected.attachedPart,
                selected.root,
                selected.facing);

        return selected;
    }

    private static TcSourceEndpoint tcFindLocalOpenSource(
            Level level, BlockPos requestedCell,
            net.minecraft.world.phys.Vec3 click) {

        java.util.ArrayList<TcSourceEndpoint> candidates = new java.util.ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        final int scan = 8;

        for (int dy=-1; dy<=1; dy++) {
            for (int dx=-scan; dx<=scan; dx++) {
                for (int dz=-scan; dz<=scan; dz++) {
                    BlockPos probe=requestedCell.offset(dx,dy,dz);
                    BlockState state=level.getBlockState(probe);
                    if (!net.minecraft.world.level.block.BaseRailBlock.isRail(state)) continue;

                    if (state.getBlock() instanceof traincraft.block.track.AbstractLegacyBatchRailBlock batch) {
                        traincraft.block.track.LegacyBatchTrackSpec spec=batch.getSpec();
                        if (spec.endpoints().size()!=2) continue;
                        BlockPos assemblyRoot=batch.getAssemblyRoot(probe,state);
                        Direction assemblyFacing=batch.getAssemblyFacing(state);
                        String key="batch|"+spec.id()+"|"+assemblyRoot.asLong()+"|"+assemblyFacing.ordinal();
                        if (!seen.add(key)) continue;
                        for (traincraft.block.track.LegacyBatchTrackSpec.Endpoint endpoint:spec.endpoints()) {
                            BlockPos segment=spec.endpointPosition(assemblyRoot,assemblyFacing,endpoint);
                            Direction outward=spec.rotateDirection(assemblyFacing,endpoint.outward());
                            tcCollectSource(level,requestedCell,click,candidates,segment,outward,spec.id(),endpoint.part());
                        }
                        continue;
                    }

                    if (state.getBlock() instanceof LegacyMediumCurveRailBlock) {
                        int part=state.getValue(LegacyMediumCurveRailBlock.PART);
                        Direction assemblyFacing=state.getValue(LegacyMediumCurveRailBlock.ASSEMBLY_FACING);
                        BlockPos assemblyRoot=probe.subtract(LegacyMediumCurveRailBlock.offsetForPart(BlockPos.ZERO,assemblyFacing,part));
                        String key="medium|"+assemblyRoot.asLong()+"|"+assemblyFacing.ordinal();
                        if (!seen.add(key)) continue;
                        int last=LegacyMediumCurveRailBlock.PART_COUNT-1;
                        tcCollectSource(level,requestedCell,click,candidates,
                                LegacyMediumCurveRailBlock.offsetForPart(assemblyRoot,assemblyFacing,0),
                                assemblyFacing.getCounterClockWise(),"legacy_medium_curve",0);
                        tcCollectSource(level,requestedCell,click,candidates,
                                LegacyMediumCurveRailBlock.offsetForPart(assemblyRoot,assemblyFacing,last),
                                assemblyFacing.getOpposite(),"legacy_medium_curve",last);
                        continue;
                    }

                    if (state.getBlock() instanceof traincraft.block.track.LegacyStraightAssemblyRailBlock straight) {
                        int part=state.getValue(traincraft.block.track.LegacyStraightAssemblyRailBlock.PART);
                        Direction assemblyFacing=state.getValue(traincraft.block.track.LegacyStraightAssemblyRailBlock.ASSEMBLY_FACING);
                        BlockPos assemblyRoot=probe.relative(assemblyFacing.getOpposite(),part);
                        String key="straight|"+assemblyRoot.asLong()+"|"+assemblyFacing.ordinal()+"|"+straight.getAssemblyLength();
                        if (!seen.add(key)) continue;
                        tcCollectSource(level,requestedCell,click,candidates,assemblyRoot,
                                assemblyFacing.getOpposite(),"legacy_straight",0);
                        BlockPos last=assemblyRoot.relative(assemblyFacing,straight.getAssemblyLength()-1);
                        tcCollectSource(level,requestedCell,click,candidates,last,
                                assemblyFacing,"legacy_straight",straight.getAssemblyLength()-1);
                        continue;
                    }

                    if (state.getBlock() instanceof net.minecraft.world.level.block.BaseRailBlock rail) {
                        net.minecraft.world.level.block.state.properties.RailShape shape;
                        try { shape=rail.getRailDirection(state,level,probe,null); }
                        catch(RuntimeException ex){ continue; }
                        for(Direction outward:tcDirectionsFor(shape)) {
                            tcCollectSource(level,requestedCell,click,candidates,probe,outward,"vanilla_rail",-1);
                        }
                    }
                }
            }
        }

        if(candidates.isEmpty()) return null;
        candidates.sort(java.util.Comparator
                .comparingInt((TcSourceEndpoint c)->c.handoff)
                .thenComparingDouble(c->c.distanceSq));
        TcSourceEndpoint best=candidates.get(0);
        if(candidates.size()>1){
            TcSourceEndpoint second=candidates.get(1);
            if(second.handoff==best.handoff
                    && Math.abs(second.distanceSq-best.distanceSq)<0.0001D
                    && (!second.segment.equals(best.segment)||second.outward!=best.outward)) return null;
        }
        return best;
    }

    private static void tcCollectSource(Level level, BlockPos requestedCell,
            net.minecraft.world.phys.Vec3 click,
            java.util.List<TcSourceEndpoint> out,
            BlockPos segment, Direction outward,
            String sourceId, int sourceEndpointPart) {

        if(tcHasRailNeighbor(level,segment,outward)) return;
        BlockPos required=segment.relative(outward);
        int dy=Math.abs(required.getY()-requestedCell.getY());
        int horizontal=Math.abs(required.getX()-requestedCell.getX())
                +Math.abs(required.getZ()-requestedCell.getZ());
        if(dy>1||horizontal>1) return;

        double cx=segment.getX()+0.5D+outward.getStepX()*0.5D;
        double cz=segment.getZ()+0.5D+outward.getStepZ()*0.5D;
        double ddx=click.x-cx;
        double ddz=click.z-cz;
        out.add(new TcSourceEndpoint(segment,outward,sourceId,sourceEndpointPart,
                horizontal+dy,ddx*ddx+ddz*ddz));
    }

    private static boolean tcCanPlaceMediumCurve(Level level, Player player,
            ItemStack stack, BlockPos root, Direction facing) {
        for(int part=0;part<LegacyMediumCurveRailBlock.PART_COUNT;part++){
            BlockPos railPos=LegacyMediumCurveRailBlock.offsetForPart(root,facing,part);
            BlockPos supportPos=railPos.below();
            if(!level.getBlockState(railPos).isAir()
                    ||!level.getBlockState(supportPos).isFaceSturdy(level,supportPos,Direction.UP)
                    ||!player.mayUseItemAt(railPos,Direction.UP,stack)) return false;
        }
        return true;
    }

    private static boolean tcHasRailNeighbor(Level level, BlockPos pos, Direction direction) {
        BlockPos n=pos.relative(direction);
        return net.minecraft.world.level.block.BaseRailBlock.isRail(level.getBlockState(n))
                ||net.minecraft.world.level.block.BaseRailBlock.isRail(level.getBlockState(n.above()))
                ||net.minecraft.world.level.block.BaseRailBlock.isRail(level.getBlockState(n.below()));
    }

    private static java.util.List<Direction> tcDirectionsFor(
            net.minecraft.world.level.block.state.properties.RailShape shape) {
        return switch(shape){
            case NORTH_SOUTH,ASCENDING_NORTH,ASCENDING_SOUTH -> java.util.List.of(Direction.NORTH,Direction.SOUTH);
            case EAST_WEST,ASCENDING_EAST,ASCENDING_WEST -> java.util.List.of(Direction.WEST,Direction.EAST);
            case NORTH_EAST -> java.util.List.of(Direction.NORTH,Direction.EAST);
            case NORTH_WEST -> java.util.List.of(Direction.NORTH,Direction.WEST);
            case SOUTH_EAST -> java.util.List.of(Direction.SOUTH,Direction.EAST);
            case SOUTH_WEST -> java.util.List.of(Direction.SOUTH,Direction.WEST);
        };
    }

    private static int tcFacingPenalty(Direction candidate, Direction playerFacing) {
        if(candidate==playerFacing) return 0;
        if(candidate.getOpposite()==playerFacing) return 2;
        return 1;
    }}
