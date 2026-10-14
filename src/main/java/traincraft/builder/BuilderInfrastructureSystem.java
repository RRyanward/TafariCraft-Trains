package traincraft.builder;

// TC_DEV34_R1C1A_BUILDER_INFRASTRUCTURE

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.blueprint.BuilderStructurePlan;
import traincraft.blueprint.BuilderTrackClearanceGeometry;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.item.TrackBlueprintItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dev34 r1c1a Tracks Builder infrastructure pass.
 *
 * The accepted r1b6 train-clearance geometry remains the authority for the
 * horizontal route. This class adds Builder construction immediately around
 * that protected volume:
 *
 *  - seven roadbed/foundation material bands:
 *      L3 L2 L1 CENTER R1 R2 R3
 *  - optional LEFT/RIGHT wall lining
 *  - optional LEFT/CENTER/RIGHT roof lining
 *  - excavation of the train-clearance interior before track placement
 *  - material preflight from Builder rack slots 20..51
 *  - normal block drops returned to rack slots 20..51
 *
 * Pattern slots are one-item filters only and are never consumed.
 */
public final class BuilderInfrastructureSystem {
    public static final int PATTERN_COUNT = 12;

    public static final int FOUNDATION_L3 = 0;
    public static final int FOUNDATION_L2 = 1;
    public static final int FOUNDATION_L1 = 2;
    public static final int FOUNDATION_CENTER = 3;
    public static final int FOUNDATION_R1 = 4;
    public static final int FOUNDATION_R2 = 5;
    public static final int FOUNDATION_R3 = 6;
    public static final int WALL_LEFT = 7;
    public static final int WALL_RIGHT = 8;
    public static final int ROOF_LEFT = 9;
    public static final int ROOF_CENTER = 10;
    public static final int ROOF_RIGHT = 11;

    // r1b6 full-block horizontal envelope.
    private static final double CLEARANCE_RADIUS =
            BuilderStructurePlan.CLEARANCE_RADIUS_XZ
                    + Math.sqrt(0.5D);

    // r1c1a keeps the accepted r1b6 upper profile unchanged.
    // Base 4.25 + existing full-block half-height 0.50 = effective 4.75.
    // Tunnel lining is placed immediately outside that protected interior.
    private static final double TUNNEL_ABOVE =
            BuilderStructurePlan.CLEARANCE_ABOVE
                    + 0.5D;

    // Infrastructure never excavates below the rail path. The foundation is
    // an explicit Builder exception one block beneath the path.
    private static final double TUNNEL_BELOW = 0.0D;

    // Foundation footprint probes only the rail-height slice. A candidate
    // support block is accepted one block beneath any cell whose block above
    // lies inside this narrow vertical slice.
    private static final double FOUNDATION_SLICE_ABOVE = 0.50D;

    private static final int HORIZONTAL_MARGIN =
            (int) Math.ceil(CLEARANCE_RADIUS) + 2;

    private static final int VERTICAL_MARGIN_ABOVE =
            (int) Math.ceil(TUNNEL_ABOVE) + 1;

    private BuilderInfrastructureSystem() {}

    private enum Kind {
        FOUNDATION,
        WALL_LEFT,
        WALL_RIGHT,
        ROOF_LEFT,
        ROOF_CENTER,
        ROOF_RIGHT
    }

    private record Placement(
            BlockPos pos,
            Kind kind,
            ItemStack template) {}

    private record PathInfo(
            double distance,
            double signedSide) {}

    private record Plan(
            String itemId,
            BlockPos root,
            Direction facing,
            List<BlockPos> guide) {}

    public static boolean isEnabled(
            LegacyPoweredLocomotive builder) {
        if (builder == null
                || !builder.isBuilder()) {
            return false;
        }

        IItemHandler patterns =
                builder.getBuilderStructurePatterns();

        if (patterns == null
                || patterns.getSlots()
                        < PATTERN_COUNT) {
            return false;
        }

        return isBlockTemplate(
                patterns.getStackInSlot(
                        FOUNDATION_CENTER));
    }

    public static String modeName(
            LegacyPoweredLocomotive builder) {
        if (!isEnabled(builder)) {
            return "LEGACY";
        }

        IItemHandler patterns =
                builder.getBuilderStructurePatterns();

        boolean walls =
                isBlockTemplate(
                                patterns.getStackInSlot(
                                        WALL_LEFT))
                        || isBlockTemplate(
                                patterns.getStackInSlot(
                                        WALL_RIGHT));

        boolean roof =
                isBlockTemplate(
                                patterns.getStackInSlot(
                                        ROOF_LEFT))
                        || isBlockTemplate(
                                patterns.getStackInSlot(
                                        ROOF_CENTER))
                        || isBlockTemplate(
                                patterns.getStackInSlot(
                                        ROOF_RIGHT));

        if (roof) {
            return "TUNNEL";
        }

        if (walls) {
            return "WALLS";
        }

        return "ROADBED";
    }

    /**
     * Runs before the real track Item.useOn().
     *
     * Returns "" when construction can continue, otherwise the returned text is
     * suitable for BuilderBlueprintNeed and the Builder must stop before
     * advancing the Blueprint step.
     */
    public static String prepare(
            ServerLevel server,
            LegacyPoweredLocomotive builder,
            ItemStack plannedTrackStack,
            BlockPos root,
            float placementYaw) {
        if (!isEnabled(builder)) {
            return "";
        }

        if (server == null
                || plannedTrackStack == null
                || plannedTrackStack.isEmpty()
                || root == null) {
            return "INFRASTRUCTURE INVALID";
        }

        IItemHandler patterns =
                builder.getBuilderStructurePatterns();

        ItemStack centerTemplate =
                patterns.getStackInSlot(
                        FOUNDATION_CENTER);

        if (!isBlockTemplate(centerTemplate)) {
            return "NEED FOUNDATION CENTER";
        }

        for (int slot = 0;
             slot < PATTERN_COUNT;
             slot++) {
            ItemStack template =
                    patterns.getStackInSlot(slot);

            if (!template.isEmpty()
                    && !isBlockTemplate(template)) {
                return "INVALID STRUCTURE FILTER";
            }
        }

        Direction facing =
                Direction.fromYRot(
                        placementYaw);

        Plan plan =
                createPlan(
                        plannedTrackStack,
                        root,
                        facing,
                        placementYaw);

        if (plan.guide().isEmpty()) {
            return "INFRASTRUCTURE NO PATH";
        }

        int minX =
                Integer.MAX_VALUE;
        int maxX =
                Integer.MIN_VALUE;
        int minY =
                Integer.MAX_VALUE;
        int maxY =
                Integer.MIN_VALUE;
        int minZ =
                Integer.MAX_VALUE;
        int maxZ =
                Integer.MIN_VALUE;

        for (BlockPos cell :
                plan.guide()) {
            minX =
                    Math.min(
                            minX,
                            cell.getX());
            maxX =
                    Math.max(
                            maxX,
                            cell.getX());
            minY =
                    Math.min(
                            minY,
                            cell.getY());
            maxY =
                    Math.max(
                            maxY,
                            cell.getY());
            minZ =
                    Math.min(
                            minZ,
                            cell.getZ());
            maxZ =
                    Math.max(
                            maxZ,
                            cell.getZ());
        }

        int scanMinX =
                minX - HORIZONTAL_MARGIN;
        int scanMaxX =
                maxX + HORIZONTAL_MARGIN;
        int scanMinZ =
                minZ - HORIZONTAL_MARGIN;
        int scanMaxZ =
                maxZ + HORIZONTAL_MARGIN;

        int scanMinY =
                minY - 1;
        int scanMaxY =
                maxY + VERTICAL_MARGIN_ABOVE;

        Set<BlockPos> interior =
                new LinkedHashSet<>();

        Map<BlockPos, Placement> placements =
                new LinkedHashMap<>();

        // TC_DEV36_R1K29U_MANUAL_OPEN_ENDS
        // Blueprint construction remains unchanged.  Manual Tunnel is a
        // continuous straight bore, so its longitudinal front/back faces must
        // stay open instead of becoming a one-cell segment end cap.
        int tcDev36ManualEndWallsSuppressed =
                0;

        for (int y = scanMinY;
             y <= scanMaxY;
             y++) {
            for (int x = scanMinX;
                 x <= scanMaxX;
                 x++) {
                for (int z = scanMinZ;
                     z <= scanMaxZ;
                     z++) {
                    BlockPos pos =
                            new BlockPos(
                                    x,
                                    y,
                                    z);

                    boolean inside =
                            y >= minY
                                    && insideTunnel(
                                            plan,
                                            pos);

                    if (inside) {
                        interior.add(
                                pos);
                        continue;
                    }

                    // Full-width roadbed immediately beneath the accepted
                    // horizontal train-clearance footprint.
                    if (foundationCell(
                            plan,
                            pos)) {
                        ItemStack template =
                                foundationTemplate(
                                        patterns,
                                        plan,
                                        pos,
                                        centerTemplate);

                        placements.put(
                                pos,
                                new Placement(
                                        pos,
                                        Kind.FOUNDATION,
                                        template));
                        continue;
                    }

                    // First safe vertical block above the accepted 4.75-block interior.
                    if (y > minY
                            && insideTunnel(
                                    plan,
                                    pos.below())) {
                        PathInfo info =
                                nearestPathInfo(
                                        plan,
                                        pos);

                        ItemStack template =
                                roofTemplate(
                                        patterns,
                                        info);

                        if (isBlockTemplate(template)) {
                            Kind kind =
                                    roofKind(
                                            info);

                            placements.put(
                                    pos,
                                    new Placement(
                                            pos,
                                            kind,
                                            template));
                        }
                        continue;
                    }

                    // First safe block immediately outside the horizontal
                    // clearance perimeter becomes the optional tunnel/retaining
                    // wall shell.
                    Direction touching =
                            touchingInteriorDirection(
                                    plan,
                                    pos);

                    if (touching != null) {
                        if (builder.isBuilderManualTunnelMode()
                                && (touching == plan.facing()
                                        || touching
                                                == plan.facing()
                                                        .getOpposite())) {
                            tcDev36ManualEndWallsSuppressed++;
                            continue;
                        }
                        PathInfo info =
                                nearestPathInfo(
                                        plan,
                                        pos);

                        boolean left =
                                info.signedSide()
                                        >= 0.0D;

                        int slot =
                                left
                                        ? WALL_LEFT
                                        : WALL_RIGHT;

                        ItemStack template =
                                patterns.getStackInSlot(
                                        slot);

                        if (isBlockTemplate(template)) {
                            placements.put(
                                    pos,
                                    new Placement(
                                            pos,
                                            left
                                                    ? Kind.WALL_LEFT
                                                    : Kind.WALL_RIGHT,
                                            template.copy()));
                        }
                    }
                }
            }
        }

        if (builder.isBuilderManualTunnelMode()
                && tcDev36ManualEndWallsSuppressed > 0) {
            org.slf4j.LoggerFactory
                    .getLogger(
                            "TafariCraftBuilderInfrastructure")
                    .info(
                            "[TC-DEV36-R1K29U][TUNNEL] event=OPEN_ENDS entity={} root={} facing={} suppressedEndWalls={}",
                            builder.getId(),
                            root,
                            plan.facing(),
                            tcDev36ManualEndWallsSuppressed);
        }
        // Never excavate existing track infrastructure. This allows a Builder
        // route to connect to already-laid railway without destroying it.
        interior.removeIf(
                pos ->
                        isTrackInfrastructure(
                                server.getBlockState(
                                        pos)));

        // A shell must never overwrite track infrastructure either.
        placements.entrySet()
                .removeIf(
                        entry ->
                                isTrackInfrastructure(
                                        server.getBlockState(
                                                entry.getKey())));

        Map<Item, Integer> required =
                new LinkedHashMap<>();

        Set<BlockPos> destructive =
                new LinkedHashSet<>();

        List<ItemStack> drops =
                new ArrayList<>();

        // Preflight excavation.
        for (BlockPos pos :
                interior) {
            BlockState state =
                    server.getBlockState(
                            pos);

            String problem =
                    preflightRemoval(
                            server,
                            builder,
                            pos,
                            state,
                            drops,
                            destructive);

            if (!problem.isEmpty()) {
                return problem;
            }
        }

        int foundationPlacements =
                0;
        int wallPlacements =
                0;
        int roofPlacements =
                0;

        // Preflight roadbed + tunnel shell.
        for (Placement placement :
                placements.values()) {
            BlockPos pos =
                    placement.pos();

            BlockState current =
                    server.getBlockState(
                            pos);

            BlockState wanted =
                    templateState(
                            placement.template());

            if (wanted == null) {
                return "INVALID STRUCTURE FILTER";
            }

            if (current.is(
                    wanted.getBlock())) {
                continue;
            }

            // DEV45: protect immutable terrain before any material is consumed.
            if (isBuilderProtected(current)) {
                return "PROTECTED BEDROCK/OBSIDIAN " + pos.toShortString();
            }

            if (placement.kind()
                    == Kind.FOUNDATION) {
                // Preserve existing solid terrain as an acceptable foundation.
                if (!current.isAir()
                        && !current.canBeReplaced()
                        && current.getFluidState()
                                .isEmpty()) {
                    continue;
                }
            }
            else if (!current.isAir()
                    && !current.canBeReplaced()
                    && current.getFluidState()
                            .isEmpty()) {
                String problem =
                        preflightRemoval(
                                server,
                                builder,
                                pos,
                                current,
                                drops,
                                destructive);

                if (!problem.isEmpty()) {
                    return problem;
                }
            }

            required.merge(
                    placement.template()
                            .getItem(),
                    1,
                    Integer::sum);

            switch (placement.kind()) {
                case FOUNDATION ->
                        foundationPlacements++;
                case WALL_LEFT, WALL_RIGHT ->
                        wallPlacements++;
                case ROOF_LEFT, ROOF_CENTER, ROOF_RIGHT ->
                        roofPlacements++;
            }
        }

        IItemHandler storage =
                builder.getPowerInventory();

        List<ItemStack> shadow =
                snapshotRack(
                        storage);

        for (Map.Entry<Item, Integer> need :
                required.entrySet()) {
            if (!consumeShadow(
                    shadow,
                    need.getKey(),
                    need.getValue())) {
                ResourceLocation key =
                        ForgeRegistries.ITEMS
                                .getKey(
                                        need.getKey());

                String name =
                        key == null
                                ? "material"
                                : key.getPath();

                return "NEED "
                        + name
                        + " x"
                        + need.getValue();
            }
        }

        for (ItemStack drop :
                drops) {
            if (!insertShadow(
                    shadow,
                    drop.copy())) {
                return "INVENTORY FULL";
            }
        }

        // DEV45: recheck every proposed excavation just before the commit.
        for (BlockPos pos : destructive) {
            if (isBuilderProtected(server.getBlockState(pos))) {
                return "PROTECTED BEDROCK/OBSIDIAN " + pos.toShortString();
            }
        }

        // ---------- COMMIT ----------
        // Material is consumed first because the preflight deliberately counts
        // that freed rack space when proving all mined drops will fit.
        for (Map.Entry<Item, Integer> need :
                required.entrySet()) {
            if (!consumeActual(
                    storage,
                    need.getKey(),
                    need.getValue())) {
                return "NEED MATERIAL";
            }
        }

        for (BlockPos pos :
                destructive) {
            server.setBlock(
                    pos,
                    Blocks.AIR.defaultBlockState(),
                    3);
        }

        for (ItemStack drop :
                drops) {
            ItemStack remainder =
                    insertActual(
                            storage,
                            drop.copy());

            if (!remainder.isEmpty()) {
                // Preflight should make this unreachable. Never spill items
                // into the world: stop the Builder and report the invariant.
                return "INVENTORY FULL";
            }
        }

        int actuallyPlaced =
                0;

        for (Placement placement :
                placements.values()) {
            BlockPos pos =
                    placement.pos();

            BlockState current =
                    server.getBlockState(
                            pos);

            BlockState wanted =
                    templateState(
                            placement.template());

            if (wanted == null
                    || current.is(
                            wanted.getBlock())) {
                continue;
            }

            // Guard again at commit in case the world changed since preflight.
            if (isBuilderProtected(current)) {
                return "PROTECTED BEDROCK/OBSIDIAN " + pos.toShortString();
            }

            if (placement.kind()
                    == Kind.FOUNDATION
                    && !current.isAir()
                    && !current.canBeReplaced()
                    && current.getFluidState()
                            .isEmpty()) {
                continue;
            }

            server.setBlock(
                    pos,
                    wanted,
                    3);
            actuallyPlaced++;
        }

        org.slf4j.LoggerFactory
                .getLogger(
                        "TafariCraftBuilderInfrastructure")
                .info(
                        "[TC-BUILDER-INFRA] entity={} stepRoot={} item={} facing={} mode={} interior={} mined={} roadbed={} walls={} roof={} placed={} drops={}",
                        builder.getId(),
                        root,
                        plan.itemId(),
                        facing,
                        modeName(builder),
                        interior.size(),
                        destructive.size(),
                        foundationPlacements,
                        wallPlacements,
                        roofPlacements,
                        actuallyPlaced,
                        drops.size());

        return "";
    }

    private static boolean isBuilderProtected(BlockState state) {
        return state.is(Blocks.BEDROCK) || state.is(Blocks.OBSIDIAN);
    }

    private static String preflightRemoval(
            ServerLevel server,
            LegacyPoweredLocomotive builder,
            BlockPos pos,
            BlockState state,
            List<ItemStack> drops,
            Set<BlockPos> destructive) {
        if (state.isAir()) {
            return "";
        }

        if (isBuilderProtected(state)) {
            return "PROTECTED BEDROCK/OBSIDIAN " + pos.toShortString();
        }

        if (isTrackInfrastructure(state)) {
            return "";
        }

        // Fluids/replaceable vegetation are cleared without fake tool drops.
        if (!state.getFluidState()
                        .isEmpty()
                || state.canBeReplaced()) {
            destructive.add(
                    pos);
            return "";
        }

        BlockEntity blockEntity =
                server.getBlockEntity(
                        pos);

        if (blockEntity != null) {
            return "BLOCKED BLOCK ENTITY "
                    + pos.getX()
                    + " "
                    + pos.getY()
                    + " "
                    + pos.getZ();
        }

        if (state.getDestroySpeed(
                        server,
                        pos)
                < 0.0F) {
            return "BLOCKED UNBREAKABLE "
                    + pos.getX()
                    + " "
                    + pos.getY()
                    + " "
                    + pos.getZ();
        }

        List<ItemStack> blockDrops =
                Block.getDrops(
                        state,
                        server,
                        pos,
                        null,
                        builder,
                        ItemStack.EMPTY);

        for (ItemStack drop :
                blockDrops) {
            if (!drop.isEmpty()) {
                drops.add(
                        drop.copy());
            }
        }

        destructive.add(
                pos);
        return "";
    }

    private static boolean foundationCell(
            Plan plan,
            BlockPos pos) {
        // Explicit Builder foundation exception: one block directly beneath
        // the accepted horizontal train envelope is legal/required even though
        // the general player block-clearance profile remains conservative.
        return insideWith(
                plan,
                pos.above(),
                0.0D,
                FOUNDATION_SLICE_ABOVE);
    }

    private static boolean insideTunnel(
            Plan plan,
            BlockPos pos) {
        return insideWith(
                plan,
                pos,
                TUNNEL_BELOW,
                TUNNEL_ABOVE);
    }

    private static boolean insideWith(
            Plan plan,
            BlockPos pos,
            double below,
            double above) {
        double px =
                pos.getX() + 0.5D;
        double py =
                pos.getY() + 0.5D;
        double pz =
                pos.getZ() + 0.5D;

        if (BuilderTrackClearanceGeometry
                .supportsPlannedTrack(
                        plan.itemId())) {
            return BuilderTrackClearanceGeometry
                    .plannedTrackTooClose(
                            plan.itemId(),
                            false,
                            plan.root(),
                            plan.facing(),
                            px,
                            py,
                            pz,
                            CLEARANCE_RADIUS,
                            below,
                            above);
        }

        PathInfo info =
                nearestPathInfo(
                        plan,
                        pos);

        if (info.distance()
                > CLEARANCE_RADIUS) {
            return false;
        }

        double railY =
                nearestPathY(
                        plan,
                        px,
                        pz)
                        + 0.0625D;

        return py >= railY - below
                && py <= railY + above;
    }

    private static Direction touchingInteriorDirection(
            Plan plan,
            BlockPos pos) {
        Direction[] dirs = {
                Direction.NORTH,
                Direction.SOUTH,
                Direction.WEST,
                Direction.EAST
        };

        for (Direction dir :
                dirs) {
            if (insideTunnel(
                    plan,
                    pos.relative(dir))) {
                return dir;
            }
        }

        return null;
    }

    private static ItemStack foundationTemplate(
            IItemHandler patterns,
            Plan plan,
            BlockPos foundationPos,
            ItemStack centerTemplate) {
        PathInfo info =
                nearestPathInfo(
                        plan,
                        foundationPos.above());

        if (info.distance()
                < 0.75D) {
            return centerTemplate.copy();
        }

        int band =
                Math.max(
                        1,
                        Math.min(
                                3,
                                (int) Math.ceil(
                                        info.distance())));

        int slot;

        if (info.signedSide()
                >= 0.0D) {
            slot =
                    FOUNDATION_CENTER
                            - band;
        }
        else {
            slot =
                    FOUNDATION_CENTER
                            + band;
        }

        ItemStack selected =
                patterns.getStackInSlot(
                        slot);

        return isBlockTemplate(selected)
                ? selected.copy()
                : centerTemplate.copy();
    }

    private static ItemStack roofTemplate(
            IItemHandler patterns,
            PathInfo info) {
        int slot =
                Math.abs(
                                info.signedSide())
                        < 1.0D
                        ? ROOF_CENTER
                        : (info.signedSide()
                                        >= 0.0D
                                ? ROOF_LEFT
                                : ROOF_RIGHT);

        return patterns.getStackInSlot(
                        slot)
                .copy();
    }

    private static Kind roofKind(
            PathInfo info) {
        if (Math.abs(
                        info.signedSide())
                < 1.0D) {
            return Kind.ROOF_CENTER;
        }

        return info.signedSide()
                        >= 0.0D
                ? Kind.ROOF_LEFT
                : Kind.ROOF_RIGHT;
    }

    private static Plan createPlan(
            ItemStack plannedTrackStack,
            BlockPos root,
            Direction facing,
            float placementYaw) {
        // Resolve the handed preview item before asking the accepted r1b6
        // geometry helper for its physical family id. Actual placement still
        // uses the original plannedTrackStack.
        ItemStack geometryStack =
                TrackBlueprintItem
                        .previewPlacementStack(
                                plannedTrackStack.copy());

        ResourceLocation key =
                ForgeRegistries.ITEMS
                        .getKey(
                                geometryStack
                                        .getItem());

        String itemId =
                key == null
                        ? ""
                        : key.toString();

        List<BlockPos> guide =
                new ArrayList<>(
                        TrackBlueprintItem
                                .placementCells(
                                        geometryStack,
                                        root,
                                        placementYaw));

        if (guide.isEmpty()) {
            guide.add(
                    root);
        }

        return new Plan(
                itemId,
                root,
                facing,
                List.copyOf(
                        guide));
    }

    private static PathInfo nearestPathInfo(
            Plan plan,
            BlockPos pos) {
        double px =
                pos.getX() + 0.5D;
        double pz =
                pos.getZ() + 0.5D;

        List<BlockPos> guide =
                plan.guide();

        if (guide.size() == 1) {
            BlockPos only =
                    guide.get(0);

            double qx =
                    only.getX() + 0.5D;
            double qz =
                    only.getZ() + 0.5D;

            double vx =
                    px - qx;
            double vz =
                    pz - qz;

            double tx =
                    plan.facing()
                            .getStepX();
            double tz =
                    plan.facing()
                            .getStepZ();

            double signed =
                    tx * vz
                            - tz * vx;

            return new PathInfo(
                    Math.sqrt(
                            vx * vx
                                    + vz * vz),
                    signed);
        }

        double bestDistanceSq =
                Double.POSITIVE_INFINITY;
        double bestSigned =
                0.0D;

        for (int i = 0;
             i + 1 < guide.size();
             i++) {
            BlockPos a =
                    guide.get(i);
            BlockPos b =
                    guide.get(i + 1);

            double ax =
                    a.getX() + 0.5D;
            double az =
                    a.getZ() + 0.5D;
            double bx =
                    b.getX() + 0.5D;
            double bz =
                    b.getZ() + 0.5D;

            double dx =
                    bx - ax;
            double dz =
                    bz - az;

            double lenSq =
                    dx * dx
                            + dz * dz;

            if (lenSq
                    < 1.0E-8D) {
                continue;
            }

            double t =
                    ((px - ax) * dx
                                    + (pz - az) * dz)
                            / lenSq;

            t =
                    Math.max(
                            0.0D,
                            Math.min(
                                    1.0D,
                                    t));

            double qx =
                    ax + dx * t;
            double qz =
                    az + dz * t;

            double vx =
                    px - qx;
            double vz =
                    pz - qz;

            double distanceSq =
                    vx * vx
                            + vz * vz;

            if (distanceSq
                    < bestDistanceSq) {
                bestDistanceSq =
                        distanceSq;

                double len =
                        Math.sqrt(
                                lenSq);

                double tx =
                        dx / len;
                double tz =
                        dz / len;

                bestSigned =
                        tx * vz
                                - tz * vx;
            }
        }

        if (!Double.isFinite(
                bestDistanceSq)) {
            BlockPos only =
                    guide.get(0);

            double dx =
                    px
                            - (only.getX()
                                    + 0.5D);
            double dz =
                    pz
                            - (only.getZ()
                                    + 0.5D);

            bestDistanceSq =
                    dx * dx
                            + dz * dz;
        }

        return new PathInfo(
                Math.sqrt(
                        bestDistanceSq),
                bestSigned);
    }

    private static double nearestPathY(
            Plan plan,
            double px,
            double pz) {
        List<BlockPos> guide =
                plan.guide();

        if (guide.size() == 1) {
            return guide.get(0)
                    .getY();
        }

        double bestDistanceSq =
                Double.POSITIVE_INFINITY;
        double bestY =
                guide.get(0)
                        .getY();

        for (int i = 0;
             i + 1 < guide.size();
             i++) {
            BlockPos a =
                    guide.get(i);
            BlockPos b =
                    guide.get(i + 1);

            double ax =
                    a.getX() + 0.5D;
            double az =
                    a.getZ() + 0.5D;
            double bx =
                    b.getX() + 0.5D;
            double bz =
                    b.getZ() + 0.5D;

            double dx =
                    bx - ax;
            double dz =
                    bz - az;

            double lenSq =
                    dx * dx
                            + dz * dz;

            double t;

            if (lenSq
                    < 1.0E-8D) {
                t = 0.0D;
            }
            else {
                t =
                        ((px - ax) * dx
                                        + (pz - az) * dz)
                                / lenSq;

                t =
                        Math.max(
                                0.0D,
                                Math.min(
                                        1.0D,
                                        t));
            }

            double qx =
                    ax + dx * t;
            double qz =
                    az + dz * t;

            double vx =
                    px - qx;
            double vz =
                    pz - qz;

            double distanceSq =
                    vx * vx
                            + vz * vz;

            if (distanceSq
                    < bestDistanceSq) {
                bestDistanceSq =
                        distanceSq;

                bestY =
                        a.getY()
                                + (b.getY()
                                        - a.getY())
                                        * t;
            }
        }

        return bestY;
    }

    private static boolean isBlockTemplate(
            ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.getItem()
                        instanceof BlockItem;
    }

    private static BlockState templateState(
            ItemStack template) {
        if (!(template.getItem()
                instanceof BlockItem blockItem)) {
            return null;
        }

        return blockItem.getBlock()
                .defaultBlockState();
    }

    private static boolean isTrackInfrastructure(
            BlockState state) {
        if (state.is(
                BlockTags.RAILS)) {
            return true;
        }

        ResourceLocation key =
                ForgeRegistries.BLOCKS
                        .getKey(
                                state.getBlock());

        return key != null
                && "traincraft".equals(
                        key.getNamespace())
                && key.getPath()
                        .startsWith(
                                "track_");
    }

    // TC_DEV36_R1K12_EXPANDED_INFRASTRUCTURE_RACK
    // TC_DEV36_R1K14_FINAL_MATERIAL_LAYOUT
    private static boolean tcDev36R1k12MaterialRackSlot(
            int slot) {
        return (slot >= 20
                        && slot <= 51)
                || (slot >= 54
                        && slot <= 77);
    }
    private static List<ItemStack> snapshotRack(
            IItemHandler handler) {
        List<ItemStack> out =
                new ArrayList<>();

        for (int slot = 20;
             slot <= 77;
             slot++) {
            if (!tcDev36R1k12MaterialRackSlot(
                    slot)) {
                continue;
            }

            out.add(
                    handler.getStackInSlot(
                                    slot)
                            .copy());
        }

        return out;
    }

    private static boolean consumeShadow(
            List<ItemStack> shadow,
            Item item,
            int count) {
        int remaining =
                count;

        for (ItemStack stack :
                shadow) {
            if (remaining <= 0) {
                break;
            }

            if (stack.isEmpty()
                    || stack.getItem()
                            != item) {
                continue;
            }

            int take =
                    Math.min(
                            remaining,
                            stack.getCount());

            stack.shrink(
                    take);
            remaining -=
                    take;
        }

        return remaining == 0;
    }

    private static boolean insertShadow(
            List<ItemStack> shadow,
            ItemStack input) {
        if (input.isEmpty()) {
            return true;
        }

        ItemStack remaining =
                input.copy();

        for (ItemStack stack :
                shadow) {
            if (remaining.isEmpty()) {
                return true;
            }

            if (stack.isEmpty()
                    || !ItemStack
                            .isSameItemSameTags(
                                    stack,
                                    remaining)) {
                continue;
            }

            int room =
                    Math.min(
                                    stack.getMaxStackSize(),
                                    64)
                            - stack.getCount();

            if (room <= 0) {
                continue;
            }

            int move =
                    Math.min(
                            room,
                            remaining.getCount());

            stack.grow(
                    move);
            remaining.shrink(
                    move);
        }

        for (int i = 0;
             i < shadow.size()
                     && !remaining.isEmpty();
             i++) {
            ItemStack stack =
                    shadow.get(i);

            if (!stack.isEmpty()) {
                continue;
            }

            int move =
                    Math.min(
                            remaining.getCount(),
                            remaining.getMaxStackSize());

            ItemStack placed =
                    remaining.copy();

            placed.setCount(
                    move);

            shadow.set(
                    i,
                    placed);

            remaining.shrink(
                    move);
        }

        return remaining.isEmpty();
    }

    private static boolean consumeActual(
            IItemHandler handler,
            Item item,
            int count) {
        int remaining =
                count;

        for (int slot = 20;
             slot <= 77
                     && remaining > 0;
             slot++) {
            if (!tcDev36R1k12MaterialRackSlot(
                    slot)) {
                continue;
            }

            ItemStack stack =
                    handler.getStackInSlot(
                            slot);

            if (stack.isEmpty()
                    || stack.getItem()
                            != item) {
                continue;
            }

            int take =
                    Math.min(
                            remaining,
                            stack.getCount());

            ItemStack extracted =
                    handler.extractItem(
                            slot,
                            take,
                            false);

            remaining -=
                    extracted.getCount();
        }

        return remaining == 0;
    }

    private static ItemStack insertActual(
            IItemHandler handler,
            ItemStack input) {
        ItemStack remaining =
                input;

        for (int slot = 20;
             slot <= 77
                     && !remaining.isEmpty();
             slot++) {
            if (!tcDev36R1k12MaterialRackSlot(
                    slot)) {
                continue;
            }

            remaining =
                    handler.insertItem(
                            slot,
                            remaining,
                            false);
        }

        return remaining;
    }
}
