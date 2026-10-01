package traincraft.item;

// TC_BATCH8_R3H_BLUEPRINT_SIMPLE_HELP_TEXTURE_TRACE

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TrackBlueprintItem extends Item {
    private static final String ROOT = "TafariTrackBlueprint";
    private static final String ID = "Id";
    private static final String LOCKED = "Locked";
    private static final String ORIGIN = "Origin";
    private static final String BASE_YAW = "BaseYaw";
    private static final String SEGMENTS = "Segments";
    private static final String SELECTED_TRACK = "SelectedTrack";
    private static final String PREVIEW_ANCHOR = "PreviewAnchor";
    private static final String PREVIEW_ROTATION = "PreviewRotation";
    // TC_BATCH8_R3ZR_BLUEPRINT2_UNIFIED_HANDS
    private static final String SELECTED_LEFT = "SelectedLeft";
    private static final String SEGMENT_LEFT = "Left";
    // TC_BATCH8_R3ZV_BLUEPRINT_VIRTUAL_CONTINUATION
    private static final String ROUTE_EXIT_X = "RouteExitX";
    private static final String ROUTE_EXIT_Y = "RouteExitY";
    private static final String ROUTE_EXIT_Z = "RouteExitZ";
    private static final String ROUTE_EXIT_DIRECTION = "RouteExitDirection";
    private static final String ROUTE_EXIT_PART = "RouteExitPart";
    private static final String UNIFIED_LEFT_NBT = "TafariCraftUnifiedTrackLeft";

    // Canonical visible item first, hidden legacy LEFT registry item second.
    // Material aliases keep the same suffix (_steel/_copper/_snow/_energy).
    private static final String[][] UNIFIED_TRACK_PAIRS = {
            {"track_curve_45_medium_right", "track_curve_45_medium_left"},
            {"track_curve_45_large_right", "track_curve_45_large_left"},
            {"track_curve_45_very_large_right", "track_curve_45_very_large_left"},
            {"track_curve_45_super_large_right", "track_curve_45_super_large_left"},
            {"track_parallel_curve_small_right", "track_parallel_curve_small_left"},
            {"track_parallel_curve_medium_right", "track_parallel_curve_medium_left"},
            {"track_parallel_curve_large_right", "track_parallel_curve_large_left"},
            {"track_slope_curve_large_right", "track_slope_curve_large_left"},
            {"track_slope_curve_very_large_right", "track_slope_curve_very_large_left"},
            {"track_slope_curve_super_large_right", "track_slope_curve_super_large_left"},
            {"track_switch_small_right", "track_switch_small_left"},
            {"track_switch_medium_right", "track_switch_medium_left"},
            {"track_switch_large_right", "track_switch_large_left"},
            {"track_switch_very_large_right", "track_switch_very_large_left"},
            {"track_switch_45_medium_right", "track_switch_45_medium_left"},
            {"track_switch_parallel_right", "track_switch_parallel_left"},
            {"track_diamond_crossing", "track_diamond_crossing_left"}
    };
    private static final int MAX_SEGMENTS = 512;

    public TrackBlueprintItem(Properties properties) {
        super(properties);
    }

    private static CompoundTag data(ItemStack stack) {
        CompoundTag root = stack.getOrCreateTag();
        if (!root.contains(ROOT, CompoundTag.TAG_COMPOUND)) {
            root.put(ROOT, new CompoundTag());
        }

        CompoundTag data = root.getCompound(ROOT);
        if (!data.contains(ID)) {
            data.putString(ID, UUID.randomUUID().toString());
        }
        if (!data.contains(SEGMENTS, CompoundTag.TAG_LIST)) {
            data.put(SEGMENTS, new ListTag());
        }

        root.put(ROOT, data);
        return data;
    }

    @Nullable
    private static CompoundTag existingData(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(ROOT, CompoundTag.TAG_COMPOUND)) {
            return null;
        }
        return root.getCompound(ROOT);
    }

    private static void updateVisualModel(ItemStack stack) {
        CompoundTag root = stack.getOrCreateTag();
        CompoundTag data = existingData(stack);

        boolean completed = data != null
                && data.getBoolean(LOCKED)
                && !data.getList(SEGMENTS, CompoundTag.TAG_COMPOUND).isEmpty();

        if (completed) {
            root.putInt("CustomModelData", 1);
        } else {
            root.remove("CustomModelData");
        }
    }

    public static boolean isLocked(ItemStack stack) {
        CompoundTag data = existingData(stack);
        return data != null && data.getBoolean(LOCKED);
    }

    public static int segmentCount(ItemStack stack) {
        CompoundTag data = existingData(stack);
        return data == null
                ? 0
                : data.getList(SEGMENTS, CompoundTag.TAG_COMPOUND).size();
    }

    public static String blueprintId(ItemStack stack) {
        return data(stack).getString(ID);
    }

    public static float baseYaw(ItemStack stack) {
        CompoundTag data = existingData(stack);
        return data == null ? 0.0F : data.getFloat(BASE_YAW);
    }

    public static CompoundTag segment(ItemStack stack, int index) {
        CompoundTag data = existingData(stack);
        if (data == null) return new CompoundTag();

        ListTag segments = data.getList(SEGMENTS, CompoundTag.TAG_COMPOUND);
        return index >= 0 && index < segments.size()
                ? segments.getCompound(index)
                : new CompoundTag();
    }

    public static boolean hasOrigin(ItemStack stack) {
        CompoundTag data = existingData(stack);
        return data != null && data.contains(ORIGIN);
    }

    public static BlockPos origin(ItemStack stack) {
        CompoundTag data = existingData(stack);
        return data == null || !data.contains(ORIGIN)
                ? BlockPos.ZERO
                : BlockPos.of(data.getLong(ORIGIN));
    }

    public static boolean hasPreviewAnchor(ItemStack stack) {
        CompoundTag data = existingData(stack);
        return data != null && data.contains(PREVIEW_ANCHOR);
    }

    public static BlockPos previewAnchor(ItemStack stack) {
        CompoundTag data = existingData(stack);
        return data == null || !data.contains(PREVIEW_ANCHOR)
                ? BlockPos.ZERO
                : BlockPos.of(data.getLong(PREVIEW_ANCHOR));
    }

    public static int previewRotationQuarter(ItemStack stack) {
        CompoundTag data = existingData(stack);
        return data == null
                ? 0
                : Math.floorMod(data.getInt(PREVIEW_ROTATION), 4);
    }

    public static int previewRotationQuarter(ItemStack stack, float playerYaw) {
        return Math.floorMod(
                quarterYaw(playerYaw) - quarterYaw(baseYaw(stack)),
                4);
    }

    private static int quarterYaw(float yaw) {
        return Math.floorMod(Math.round(yaw / 90.0F), 4);
    }

    private static String acceptedMaterialSuffix(String path, String base) {
        if (path.equals(base)) return "";

        if (!path.startsWith(base + "_")) {
            return null;
        }

        String suffix = path.substring(base.length());

        return "_steel".equals(suffix)
                        || "_copper".equals(suffix)
                        || "_snow".equals(suffix)
                        || "_energy".equals(suffix)
                ? suffix
                : null;
    }

    public static boolean isUnifiedPairId(String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !"traincraft".equals(key.getNamespace())) {
            return false;
        }

        String path = key.getPath();

        for (String[] pair : UNIFIED_TRACK_PAIRS) {
            if (acceptedMaterialSuffix(path, pair[0]) != null
                    || acceptedMaterialSuffix(path, pair[1]) != null) {
                return true;
            }
        }

        return false;
    }

    public static boolean isLeftTrackId(String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !"traincraft".equals(key.getNamespace())) {
            return false;
        }

        String path = key.getPath();

        for (String[] pair : UNIFIED_TRACK_PAIRS) {
            if (acceptedMaterialSuffix(path, pair[1]) != null) {
                return true;
            }
        }

        return false;
    }

    public static String canonicalTrackId(String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !"traincraft".equals(key.getNamespace())) {
            return itemId;
        }

        String path = key.getPath();

        for (String[] pair : UNIFIED_TRACK_PAIRS) {
            String suffix = acceptedMaterialSuffix(path, pair[0]);
            if (suffix != null) {
                return new ResourceLocation(
                                key.getNamespace(),
                                pair[0] + suffix)
                        .toString();
            }

            suffix = acceptedMaterialSuffix(path, pair[1]);
            if (suffix != null) {
                return new ResourceLocation(
                                key.getNamespace(),
                                pair[0] + suffix)
                        .toString();
            }
        }

        return itemId;
    }

    private static String leftTrackIdForCanonical(String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(
                canonicalTrackId(itemId));

        if (key == null || !"traincraft".equals(key.getNamespace())) {
            return itemId;
        }

        String path = key.getPath();

        for (String[] pair : UNIFIED_TRACK_PAIRS) {
            String suffix = acceptedMaterialSuffix(path, pair[0]);
            if (suffix != null) {
                return new ResourceLocation(
                                key.getNamespace(),
                                pair[1] + suffix)
                        .toString();
            }
        }

        return itemId;
    }

    private static boolean stackUnifiedLeft(ItemStack stack) {
        if (stack.isEmpty()) return false;

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(UNIFIED_LEFT_NBT)) {
            return tag.getBoolean(UNIFIED_LEFT_NBT);
        }

        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && isLeftTrackId(key.toString());
    }

    public static void applyUnifiedDirection(
            ItemStack stack,
            boolean left) {
        if (stack.isEmpty()) return;

        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null || !isUnifiedPairId(key.toString())) {
            return;
        }

        stack.getOrCreateTag().putBoolean(
                UNIFIED_LEFT_NBT,
                left);
    }

    private static boolean selectedDirectionLeft(
            ItemStack blueprint) {
        CompoundTag data = existingData(blueprint);
        if (data == null) return false;

        if (data.contains(SELECTED_LEFT)) {
            return data.getBoolean(SELECTED_LEFT);
        }

        return data.contains(SELECTED_TRACK)
                && isLeftTrackId(data.getString(SELECTED_TRACK));
    }

    public static boolean segmentIsLeft(
            CompoundTag segment) {
        if (segment == null) return false;

        if (segment.contains(SEGMENT_LEFT)) {
            return segment.getBoolean(SEGMENT_LEFT);
        }

        return isLeftTrackId(segment.getString("Item"));
    }

    public static String segmentCanonicalItemId(
            CompoundTag segment) {
        if (segment == null) return "";
        return canonicalTrackId(segment.getString("Item"));
    }

    public static ItemStack segmentTrackStack(
            CompoundTag segment) {
        String itemId = segmentCanonicalItemId(segment);
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null) return ItemStack.EMPTY;

        Item item = ForgeRegistries.ITEMS.getValue(key);
        if (item == null) return ItemStack.EMPTY;

        ItemStack stack = new ItemStack(item);
        applyUnifiedDirection(stack, segmentIsLeft(segment));
        return stack;
    }

    public static ItemStack previewPlacementStack(
            ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        ResourceLocation originalKey =
                ForgeRegistries.ITEMS.getKey(stack.getItem());

        if (originalKey == null
                || !isUnifiedPairId(originalKey.toString())) {
            return stack;
        }

        boolean left = stackUnifiedLeft(stack);
        String id = canonicalTrackId(originalKey.toString());

        if (left) {
            id = leftTrackIdForCanonical(id);
        }

        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key == null
                ? null
                : ForgeRegistries.ITEMS.getValue(key);

        return item == null
                ? stack
                : new ItemStack(item);
    }

    public static String displayTrackName(String itemId) {
        String canonical = canonicalTrackId(itemId);
        ResourceLocation key = ResourceLocation.tryParse(canonical);
        Item item = key == null
                ? null
                : ForgeRegistries.ITEMS.getValue(key);

        return item == null
                ? canonical
                : new ItemStack(item).getHoverName().getString();
    }
    public static final class BlueprintPlacement {
        private final BlockPos anchor;
        private final float yaw;
        private final BlockPos exitSegment;
        private final net.minecraft.core.Direction exitOutward;

        private BlueprintPlacement(
                BlockPos anchor,
                float yaw,
                BlockPos exitSegment,
                net.minecraft.core.Direction exitOutward) {
            this.anchor = anchor;
            this.yaw = yaw;
            this.exitSegment = exitSegment;
            this.exitOutward = exitOutward;
        }

        public BlockPos anchor() {
            return anchor;
        }

        public float yaw() {
            return yaw;
        }

        public BlockPos exitSegment() {
            return exitSegment;
        }

        public net.minecraft.core.Direction exitOutward() {
            return exitOutward;
        }
    }

    private static final class RouteEndpoint {
        private final BlockPos segment;
        private final net.minecraft.core.Direction outward;

        private RouteEndpoint(
                BlockPos segment,
                net.minecraft.core.Direction outward) {
            this.segment = segment;
            this.outward = outward;
        }
    }

    // TC_BATCH8_R3ZZA_SINGLE_RAIL_BLUEPRINT_SPINE
    // r3zv only recognized the assembled 3/6/12-block classic straight items.
    // A normal one-cell rail (track_normal and any other BlockItem backed by a
    // BaseRailBlock) therefore had no virtual route exit. The next standalone
    // Medium Curve fell back to free/crosshair placement, leaving the visible
    // gap even though later Medium->Medium endpoint math reported gap=0.
    //
    // Treat any ordinary rail BlockItem as a one-cell straight spine segment.
    // Legacy batch, classic straight, and standalone Medium items keep their
    // existing specialized paths and are not changed here.
    private static int straightLength(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1;
        }

        if (stack.getItem()
                instanceof traincraft.item.track.LegacyStraightTrackItem) {
            try {
                java.lang.reflect.Field lengthField =
                        traincraft.item.track.LegacyStraightTrackItem.class
                                .getDeclaredField("length");
                lengthField.setAccessible(true);
                return lengthField.getInt(stack.getItem());
            } catch (ReflectiveOperationException ex) {
                traincraft.Traincraft.LOGGER.warn(
                        "[TC-R3ZV-BLUEPRINT-SNAP] Could not read classic straight length",
                        ex);
                return -1;
            }
        }

        if (stack.getItem()
                instanceof net.minecraft.world.item.BlockItem blockItem
                && blockItem.getBlock()
                        instanceof net.minecraft.world.level.block.BaseRailBlock) {
            return 1;
        }

        return -1;
    }
    // TC_BATCH8_R3ZW_BLUEPRINT_MEDIUM_TRUE_TANGENT
    // Derive the two open Medium Curve tangents from the actual part layout in
    // LegacyMediumCurveRailBlock.offsetForPart(), rather than the older virtual
    // quarter-turn approximation. This keeps Blueprint-only ghost/route math on
    // the visible rail endpoints without changing accepted world placement.
    // TC_BATCH8_R3ZZ_MEDIUM_MANUAL_PARITY
    // These are the endpoint directions used by the accepted r3zo manual
    // Medium placement solver. Blueprint planning must use the same endpoint
    // contract rather than a visual-mesh tangent approximation.
    private static net.minecraft.core.Direction mediumEndpointOutward(
            net.minecraft.core.Direction facing,
            int part) {
        int last =
                traincraft.block.track.LegacyMediumCurveRailBlock
                        .PART_COUNT
                        - 1;

        if (part == 0) {
            return facing.getCounterClockWise();
        }

        if (part == last) {
            return facing.getOpposite();
        }

        throw new IllegalArgumentException(
                "Medium endpoint part required: " + part);
    }

    private static RouteEndpoint routeExitFromStored(
            CompoundTag segment,
            BlockPos origin) {
        ItemStack stack =
                segmentTrackStack(segment);

        BlockPos anchor =
                origin.offset(
                        segment.getInt("X"),
                        segment.getInt("Y"),
                        segment.getInt("Z"));

        net.minecraft.core.Direction facing =
                net.minecraft.core.Direction.fromYRot(
                        segment.getFloat("Yaw"));

        // New r3zz Medium segments store the exact chosen outgoing endpoint
        // role. For older r3zv/r3zw Blueprints there is no RouteExitPart, so
        // repair them from the old saved hand + root/yaw instead of trusting
        // the r3zw visual-tangent RouteExitDirection.
        if (stack.getItem()
                instanceof traincraft.item.track.LegacyMediumCurveTrackItem
                && !segment.contains(ROUTE_EXIT_PART)) {
            int last =
                    traincraft.block.track.LegacyMediumCurveRailBlock
                            .PART_COUNT
                            - 1;

            int exitPart =
                    segmentIsLeft(segment)
                            ? 0
                            : last;

            BlockPos exit =
                    traincraft.block.track.LegacyMediumCurveRailBlock
                            .offsetForPart(
                                    anchor,
                                    facing,
                                    exitPart);

            return new RouteEndpoint(
                    exit,
                    mediumEndpointOutward(
                            facing,
                            exitPart));
        }

        if (segment.contains(ROUTE_EXIT_X)
                && segment.contains(ROUTE_EXIT_Y)
                && segment.contains(ROUTE_EXIT_Z)
                && segment.contains(ROUTE_EXIT_DIRECTION)) {
            BlockPos exit =
                    origin.offset(
                            segment.getInt(ROUTE_EXIT_X),
                            segment.getInt(ROUTE_EXIT_Y),
                            segment.getInt(ROUTE_EXIT_Z));

            net.minecraft.core.Direction outward =
                    net.minecraft.core.Direction.from3DDataValue(
                            segment.getInt(ROUTE_EXIT_DIRECTION));

            return new RouteEndpoint(
                    exit,
                    outward);
        }

        int length =
                straightLength(stack);

        if (length > 0) {
            return new RouteEndpoint(
                    anchor.relative(
                            facing,
                            length - 1),
                    facing);
        }

        if (stack.getItem()
                instanceof traincraft.item.track.LegacyMediumCurveTrackItem) {
            int last =
                    traincraft.block.track.LegacyMediumCurveRailBlock
                            .PART_COUNT
                            - 1;

            int exitPart =
                    segmentIsLeft(segment)
                            ? 0
                            : last;

            BlockPos exit =
                    traincraft.block.track.LegacyMediumCurveRailBlock
                            .offsetForPart(
                                    anchor,
                                    facing,
                                    exitPart);

            return new RouteEndpoint(
                    exit,
                    mediumEndpointOutward(
                            facing,
                            exitPart));
        }

        // r3zzc compatibility: old Batch Blueprint entries had no virtual
        // route exit. Reconstruct a deterministic through-route from the
        // accepted Batch spec instead of dropping back to crosshair placement.
        ItemStack resolved =
                blueprintResolvedBatchStack(
                        stack);

        if (resolved.getItem()
                instanceof traincraft.item.track.LegacyBatchTrackItem batch) {
            traincraft.block.track.LegacyBatchTrackSpec spec =
                    blueprintBatchSpec(batch);

            if (spec != null
                    && !spec.endpoints().isEmpty()) {
                BlockPos root =
                        blueprintBatchRootFromAnchor(
                                spec,
                                anchor,
                                facing);

                traincraft.block.track.LegacyBatchTrackSpec.Endpoint attached =
                        spec.endpoints().get(0);

                traincraft.block.track.LegacyBatchTrackSpec.Endpoint outgoing =
                        blueprintBatchOutgoingEndpoint(
                                spec,
                                attached,
                                root,
                                facing,
                                facing,
                                facing);

                if (outgoing != null) {
                    return new RouteEndpoint(
                            spec.endpointPosition(
                                    root,
                                    facing,
                                    outgoing),
                            spec.rotateDirection(
                                    facing,
                                    outgoing.outward()));
                }
            }
        }

        return null;
    }

    private static BlueprintPlacement freePlacement(
            ItemStack blueprint,
            ItemStack selected,
            BlockPos requested,
            float playerYaw) {
        net.minecraft.core.Direction facing =
                net.minecraft.core.Direction.fromYRot(playerYaw);

        int length = straightLength(selected);
        if (length > 0) {
            return new BlueprintPlacement(
                    requested,
                    facing.toYRot(),
                    requested.relative(facing, length - 1),
                    facing);
        }

        if (selected.getItem()
                instanceof traincraft.item.track.LegacyMediumCurveTrackItem) {
            boolean left =
                    selectedDirectionLeft(blueprint);

            int last =
                    traincraft.block.track.LegacyMediumCurveRailBlock
                            .PART_COUNT
                            - 1;

            BlockPos exit;
            net.minecraft.core.Direction outward;

            if (left) {
                exit =
                        traincraft.block.track.LegacyMediumCurveRailBlock
                                .offsetForPart(
                                        requested,
                                        facing,
                                        0);
                outward = facing.getCounterClockWise();
            } else {
                exit =
                        traincraft.block.track.LegacyMediumCurveRailBlock
                                .offsetForPart(
                                        requested,
                                        facing,
                                        last);
                outward = facing.getOpposite();
            }

            return new BlueprintPlacement(
                    requested,
                    facing.toYRot(),
                    exit,
                    outward);
        }

                BlueprintPlacement quarterCurveStart =
                blueprintQuarterCurveForwardStart(
                        blueprint,
                        selected,
                        requested,
                        playerYaw);

        if (quarterCurveStart != null) {
            return quarterCurveStart;
        }

BlueprintPlacement batchPlacement =
                blueprintBatchFreePlacement(
                        selected,
                        requested,
                        playerYaw);

        if (batchPlacement != null) {
            return batchPlacement;
        }

        return new BlueprintPlacement(
                requested,
                playerYaw,
                null,
                null);
    }

    // TC_BATCH8_R3ZX_BLUEPRINT_SNAP_TELEMETRY
    // Client ghost calls this after previewPlacement(). It logs only when the
    // computed snap state changes, so moving the crosshair does not flood logs
    // while the virtual continuation remains the same.
    private static String tcLastPreviewDebugKey = "";
    // TC_BATCH8_R3ZZN_BRANCH_ALIGNMENT_DEEP_DEBUG
    private static String tcLastBranchAlignmentDebugKey = "";

    private static BlockPos tcBranchDiagonalProbe(
            RouteEndpoint source,
            BlueprintRouteHeading8 heading) {
        if (source == null || heading == null) {
            return null;
        }

        int dx = 0;
        int dz = 0;

        switch (heading) {
            case NORTH_EAST -> {
                dx = 1;
                dz = -1;
            }
            case SOUTH_EAST -> {
                dx = 1;
                dz = 1;
            }
            case SOUTH_WEST -> {
                dx = -1;
                dz = 1;
            }
            case NORTH_WEST -> {
                dx = -1;
                dz = -1;
            }
            default -> {
                return null;
            }
        }

        return source.segment.offset(
                dx,
                0,
                dz);
    }

    private static void tcDeepLogMedium45BranchAlignment(
            ItemStack blueprint,
            ItemStack selected,
            BlockPos requested,
            RouteEndpoint source,
            BlockPos cardinalRequired,
            BlueprintRouteHeading8 sourceHeading) {
        if (blueprint == null
                || selected == null
                || selected.isEmpty()
                || source == null
                || cardinalRequired == null
                || sourceHeading == null
                || !sourceHeading.isDiagonal()) {
            return;
        }

        BlockPos diagonalProbe =
                tcBranchDiagonalProbe(
                        source,
                        sourceHeading);

        if (diagonalProbe == null) {
            return;
        }

        String debugKey =
                source.segment
                        + "|"
                        + source.outward
                        + "|"
                        + sourceHeading
                        + "|"
                        + requested
                        + "|"
                        + selectedDirectionLeft(
                                blueprint);

        if (debugKey.equals(
                tcLastBranchAlignmentDebugKey)) {
            return;
        }

        tcLastBranchAlignmentDebugKey =
                debugKey;

        java.util.Set<BlockPos> occupied =
                recordedPlacementCells(
                        blueprint);

        int last =
                traincraft.block.track.LegacyMediumCurveRailBlock
                        .PART_COUNT
                        - 1;

        traincraft.Traincraft.LOGGER.info(
                "[TC-BP-BRANCH-ALIGN] source={} sourceOut={} sourceHeading={} cardinalRequired={} diagonalProbe={} requested={} selectedLeft={} deltaCardinal=({}, {}) deltaDiagonal=({}, {})",
                source.segment,
                source.outward,
                sourceHeading,
                cardinalRequired,
                diagonalProbe,
                requested,
                selectedDirectionLeft(
                        blueprint),
                cardinalRequired.getX()
                        - source.segment.getX(),
                cardinalRequired.getZ()
                        - source.segment.getZ(),
                diagonalProbe.getX()
                        - source.segment.getX(),
                diagonalProbe.getZ()
                        - source.segment.getZ());

        for (int targetMode = 0;
             targetMode < 2;
             targetMode++) {
            BlockPos target =
                    targetMode == 0
                            ? cardinalRequired
                            : diagonalProbe;

            String targetName =
                    targetMode == 0
                            ? "CARDINAL"
                            : "DIAGONAL";

            traincraft.Traincraft.LOGGER.info(
                    "[TC-BP-BRANCH-ALIGN-MATRIX] targetMode={} target={} lastPart={}",
                    targetName,
                    target,
                    last);

            for (net.minecraft.core.Direction candidateFacing
                    : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                for (int attachedPart
                        : new int[]{0, last}) {
                    net.minecraft.core.Direction attachedOutward =
                            mediumEndpointOutward(
                                    candidateFacing,
                                    attachedPart);

                    BlockPos root =
                            target.subtract(
                                    traincraft.block.track.LegacyMediumCurveRailBlock
                                            .offsetForPart(
                                                    BlockPos.ZERO,
                                                    candidateFacing,
                                                    attachedPart));

                    BlockPos attached =
                            traincraft.block.track.LegacyMediumCurveRailBlock
                                    .offsetForPart(
                                            root,
                                            candidateFacing,
                                            attachedPart);

                    int otherPart =
                            attachedPart == 0
                                    ? last
                                    : 0;

                    BlockPos otherEndpoint =
                            traincraft.block.track.LegacyMediumCurveRailBlock
                                    .offsetForPart(
                                            root,
                                            candidateFacing,
                                            otherPart);

                    net.minecraft.core.Direction otherOutward =
                            mediumEndpointOutward(
                                    candidateFacing,
                                    otherPart);

                    BlockPos modelOwnerPart0 =
                            traincraft.block.track.LegacyMediumCurveRailBlock
                                    .offsetForPart(
                                            root,
                                            candidateFacing,
                                            0);

                    java.util.List<BlockPos> cells =
                            placementCells(
                                    selected,
                                    root,
                                    candidateFacing.toYRot());

                    traincraft.Traincraft.LOGGER.info(
                            "[TC-BP-BRANCH-ALIGN-CANDIDATE] targetMode={} facing={} attachPart={} attachOut={} faceToFaceCardinal={} root={} modelOwnerPart0={} attached={} otherPart={} otherEndpoint={} otherOut={} targetGap={} aimDist={} recordedOverlap={} containsCardinal={} containsDiagonal={} nearestCardinal={} nearestDiagonal={} cells={}",
                            targetName,
                            candidateFacing,
                            attachedPart,
                            attachedOutward,
                            attachedOutward
                                    == source.outward.getOpposite(),
                            root,
                            modelOwnerPart0,
                            attached,
                            otherPart,
                            otherEndpoint,
                            otherOutward,
                            tcDeepDistance(
                                    attached,
                                    target),
                            tcDeepNearestCellDistance(
                                    requested,
                                    cells),
                            tcDeepOverlapCount(
                                    cells,
                                    occupied),
                            cells.contains(
                                    cardinalRequired),
                            cells.contains(
                                    diagonalProbe),
                            tcDeepNearestCellDistance(
                                    cardinalRequired,
                                    cells),
                            tcDeepNearestCellDistance(
                                    diagonalProbe,
                                    cells),
                            cells);
                }
            }
        }
    }

    // TC_BATCH8_R3ZZG_BLUEPRINT_DEEP_SNAP_DEBUG
    //
    // Diagnostic only. No placement decision is changed by this block.
    // It runs after the existing change-only preview key accepts a new state,
    // so the matrix is emitted once per selected/chosen snap change instead of
    // every render frame.

    private static int tcDeepDistance(
            BlockPos a,
            BlockPos b) {
        return Math.abs(a.getX() - b.getX())
                + Math.abs(a.getY() - b.getY())
                + Math.abs(a.getZ() - b.getZ());
    }

    private static int tcDeepNearestCellDistance(
            BlockPos requested,
            java.util.Collection<BlockPos> cells) {
        int best =
                Integer.MAX_VALUE;

        for (BlockPos cell : cells) {
            best =
                    Math.min(
                            best,
                            tcDeepDistance(
                                    requested,
                                    cell));
        }

        return best == Integer.MAX_VALUE
                ? -1
                : best;
    }

    private static int tcDeepOverlapCount(
            java.util.Collection<BlockPos> candidate,
            java.util.Set<BlockPos> occupied) {
        int count =
                0;

        for (BlockPos cell : candidate) {
            if (occupied.contains(cell)) {
                count++;
            }
        }

        return count;
    }

    private static String tcDeepEndpointRole(
            traincraft.block.track.LegacyBatchTrackSpec spec,
            traincraft.block.track.LegacyBatchTrackSpec.Endpoint endpoint) {
        if (spec != null
                && spec.id() != null
                && spec.id().startsWith("track_switch_45_medium_")) {
            if (endpoint.part() == 0) {
                return "ENTRY";
            }

            if (endpoint.part() == 2) {
                return "STRAIGHT";
            }

            if (endpoint.part() == 4) {
                return "BRANCH";
            }
        }

        return "END";
    }

    private static String tcDeepItemId(
            ItemStack stack) {
        if (stack == null
                || stack.isEmpty()) {
            return "<empty>";
        }

        net.minecraft.resources.ResourceLocation key =
                net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getKey(
                                stack.getItem());

        return key == null
                ? "<unregistered>"
                : key.toString();
    }

    private static void tcDeepLogPreviousGeometry(
            ItemStack previousStack,
            BlockPos previousAnchor,
            float previousYaw) {
        net.minecraft.core.Direction facing =
                net.minecraft.core.Direction.fromYRot(
                        previousYaw);

        ItemStack resolved =
                blueprintResolvedBatchStack(
                        previousStack);

        if (resolved.getItem()
                instanceof traincraft.item.track.LegacyBatchTrackItem batch) {
            traincraft.block.track.LegacyBatchTrackSpec spec =
                    blueprintBatchSpec(
                            batch);

            if (spec == null) {
                return;
            }

            BlockPos root =
                    blueprintBatchRootFromAnchor(
                            spec,
                            previousAnchor,
                            facing);

            traincraft.Traincraft.LOGGER.info(
                    "[TC-BP-DEEP-PREV-BATCH] id={} anchor={} root={} facing={} partCount={} guidePart0={} endpoints={}",
                    spec.id(),
                    previousAnchor,
                    root,
                    facing,
                    spec.partCount(),
                    spec.offsetForPart(
                            root,
                            facing,
                            0),
                    spec.endpoints().size());

            for (traincraft.block.track.LegacyBatchTrackSpec.Endpoint endpoint
                    : spec.endpoints()) {
                BlockPos logical =
                        spec.endpointPosition(
                                root,
                                facing,
                                endpoint);

                BlockPos physical =
                        spec.offsetForPart(
                                root,
                                facing,
                                endpoint.part());

                net.minecraft.core.Direction outward =
                        spec.rotateDirection(
                                facing,
                                endpoint.outward());

                traincraft.Traincraft.LOGGER.info(
                        "[TC-BP-DEEP-PREV-ENDPOINT] id={} role={} part={} logical={} physical={} outward={} connector={}",
                        spec.id(),
                        tcDeepEndpointRole(
                                spec,
                                endpoint),
                        endpoint.part(),
                        logical,
                        physical,
                        outward,
                        logical.relative(
                                outward));
            }

            return;
        }

        if (previousStack.getItem()
                instanceof traincraft.item.track.LegacyMediumCurveTrackItem) {
            int last =
                    traincraft.block.track.LegacyMediumCurveRailBlock
                            .PART_COUNT
                            - 1;

            BlockPos p0 =
                    traincraft.block.track.LegacyMediumCurveRailBlock
                            .offsetForPart(
                                    previousAnchor,
                                    facing,
                                    0);

            BlockPos pLast =
                    traincraft.block.track.LegacyMediumCurveRailBlock
                            .offsetForPart(
                                    previousAnchor,
                                    facing,
                                    last);

            traincraft.Traincraft.LOGGER.info(
                    "[TC-BP-DEEP-PREV-MEDIUM] root={} facing={} modelOwnerPart0={} p0Out={} pLast={} pLastOut={}",
                    previousAnchor,
                    facing,
                    p0,
                    mediumEndpointOutward(
                            facing,
                            0),
                    pLast,
                    mediumEndpointOutward(
                            facing,
                            last));
        }
    }

    private static void tcDeepLogChosenEndpoints(
            ItemStack selected,
            BlueprintPlacement placement) {
        net.minecraft.core.Direction facing =
                net.minecraft.core.Direction.fromYRot(
                        placement.yaw());

        ItemStack resolved =
                blueprintResolvedBatchStack(
                        selected);

        if (resolved.getItem()
                instanceof traincraft.item.track.LegacyBatchTrackItem batch) {
            traincraft.block.track.LegacyBatchTrackSpec spec =
                    blueprintBatchSpec(
                            batch);

            if (spec == null) {
                return;
            }

            BlockPos root =
                    blueprintBatchRootFromAnchor(
                            spec,
                            placement.anchor(),
                            facing);

            traincraft.Traincraft.LOGGER.info(
                    "[TC-BP-DEEP-CHOSEN-BATCH] id={} anchor={} root={} facing={} guidePart0={} partCount={}",
                    spec.id(),
                    placement.anchor(),
                    root,
                    facing,
                    spec.offsetForPart(
                            root,
                            facing,
                            0),
                    spec.partCount());

            for (traincraft.block.track.LegacyBatchTrackSpec.Endpoint endpoint
                    : spec.endpoints()) {
                BlockPos logical =
                        spec.endpointPosition(
                                root,
                                facing,
                                endpoint);

                BlockPos physical =
                        spec.offsetForPart(
                                root,
                                facing,
                                endpoint.part());

                net.minecraft.core.Direction outward =
                        spec.rotateDirection(
                                facing,
                                endpoint.outward());

                traincraft.Traincraft.LOGGER.info(
                        "[TC-BP-DEEP-CHOSEN-ENDPOINT] id={} role={} part={} logical={} physical={} outward={} connector={}",
                        spec.id(),
                        tcDeepEndpointRole(
                                spec,
                                endpoint),
                        endpoint.part(),
                        logical,
                        physical,
                        outward,
                        logical.relative(
                                outward));
            }

            return;
        }

        if (selected.getItem()
                instanceof traincraft.item.track.LegacyMediumCurveTrackItem) {
            int last =
                    traincraft.block.track.LegacyMediumCurveRailBlock
                            .PART_COUNT
                            - 1;

            traincraft.Traincraft.LOGGER.info(
                    "[TC-BP-DEEP-CHOSEN-MEDIUM] root={} facing={} modelOwnerPart0={} p0Out={} pLast={} pLastOut={}",
                    placement.anchor(),
                    facing,
                    traincraft.block.track.LegacyMediumCurveRailBlock
                            .offsetForPart(
                                    placement.anchor(),
                                    facing,
                                    0),
                    mediumEndpointOutward(
                            facing,
                            0),
                    traincraft.block.track.LegacyMediumCurveRailBlock
                            .offsetForPart(
                                    placement.anchor(),
                                    facing,
                                    last),
                    mediumEndpointOutward(
                            facing,
                            last));
        }
    }

    private static void tcDeepBlueprintSnapDebug(
            ItemStack blueprint,
            ItemStack selected,
            BlockPos requested,
            float playerYaw,
            BlueprintPlacement placement) {
        CompoundTag data =
                existingData(
                        blueprint);

        if (data == null
                || !data.contains(ORIGIN)) {
            return;
        }

        ListTag segments =
                data.getList(
                        SEGMENTS,
                        CompoundTag.TAG_COMPOUND);

        if (segments.isEmpty()) {
            return;
        }

        BlockPos origin =
                BlockPos.of(
                        data.getLong(ORIGIN));

        int previousIndex =
                segments.size() - 1;

        CompoundTag previous =
                segments.getCompound(
                        previousIndex);

        ItemStack previousStack =
                segmentTrackStack(
                        previous);

        BlockPos previousAnchor =
                origin.offset(
                        previous.getInt("X"),
                        previous.getInt("Y"),
                        previous.getInt("Z"));

        float previousYaw =
                previous.getFloat("Yaw");

        RouteEndpoint source =
                blueprintPreviewSourceEndpoint(
                        segments,
                        previousIndex,
                        origin,
                        requested,
                        playerYaw,
                        selected);

        if (source == null) {
            source =
                    routeExitFromStored(
                            previous,
                            origin);
        }

        if (source == null) {
            traincraft.Traincraft.LOGGER.info(
                    "[TC-BP-DEEP] NO_SOURCE prev={} selected={} requested={} playerYaw={}",
                    tcDeepItemId(
                            previousStack),
                    tcDeepItemId(
                            selected),
                    requested,
                    playerYaw);
            return;
        }

        BlockPos required =
                source.segment.relative(
                        source.outward);

        // TC_BATCH8_R3ZZT_45CURVE_DIAGONAL_HANDOFF_DEEP_DEBUG
        //
        // A 45-degree curve's far endpoint has a true diagonal RouteHeading8
        // tangent even though its legacy guide-cell outward is cardinal. When
        // another 45-degree curve follows that diagonal endpoint, advancing
        // only by source.outward keeps endpointGap at zero in guide-cell terms
        // but leaves the railheads one cell sideways in the rendered geometry.
        //
        // Scope this correction ONLY to 45-curve -> 45-curve handoffs and ONLY
        // when the actual source tangent is diagonal. Medium45 switch routing
        // stays on the frozen r3zzs contract and is deliberately untouched.
        String tc45PreviousSpec =
                blueprintBatchSpecId(
                        segmentTrackStack(
                                previous));

        String tc45SelectedSpec =
                blueprintBatchSpecId(
                        selected);

        BlueprintRouteHeading8 tc45SourceHeading =
                blueprintRouteHeadingForSource(
                        segments,
                        segments.size() - 1,
                        origin,
                        source);

        if (tc45SourceHeading != null
                && tc45SourceHeading.isDiagonal()
                && blueprintUsesTrueDiagonalCellHandoff(
                        tc45PreviousSpec,
                        tc45SelectedSpec)) {
            BlockPos tc45DiagonalRequired =
                    tcBranchDiagonalProbe(
                            source,
                            tc45SourceHeading);

            if (tc45DiagonalRequired != null) {
                required =
                        tc45DiagonalRequired;
            }
        }
        BlueprintRouteHeading8 deepSourceHeading =
                blueprintRouteHeadingForSource(
                        segments,
                        previousIndex,
                        origin,
                        source);

        String deepPreviousSpec =
                blueprintBatchSpecId(
                        previousStack);

        String deepSelectedSpec =
                blueprintBatchSpecId(
                        selected);

        traincraft.Traincraft.LOGGER.info(
                "[TC-BP-8WAY] prevSpec={} selectedSpec={} sourceSegment={} sourceOut={} sourceHeading={} exactPair={}",
                deepPreviousSpec,
                deepSelectedSpec,
                source.segment,
                source.outward,
                deepSourceHeading,
                deepSourceHeading != null
                        && blueprintIsEightWaySnapFamily(
                                deepPreviousSpec)
                        && blueprintIsEightWaySnapFamily(
                                deepSelectedSpec));

        net.minecraft.core.Direction chosenFacing =
                net.minecraft.core.Direction.fromYRot(
                        placement.yaw());

        java.util.List<BlockPos> previousCells =
                placementCells(
                        previousStack,
                        previousAnchor,
                        previousYaw);

        java.util.List<BlockPos> chosenCells =
                placementCells(
                        selected,
                        placement.anchor(),
                        placement.yaw());

        java.util.Set<BlockPos> occupied =
                recordedPlacementCells(
                        blueprint);

        int chosenRecordedOverlap =
                tcDeepOverlapCount(
                        chosenCells,
                        occupied);

        traincraft.Traincraft.LOGGER.info(
                "[TC-BP-DEEP] BEGIN prev={} prevAnchor={} prevFacing={} prevLeft={} selected={} selectedLeft={} requested={} playerYaw={} source={} sourceOut={} required={} chosenAnchor={} chosenFacing={} chosenExit={} chosenExitOut={} chosenAimDist={} chosenRecordedOverlap={}",
                tcDeepItemId(
                        previousStack),
                previousAnchor,
                net.minecraft.core.Direction.fromYRot(
                        previousYaw),
                segmentIsLeft(
                        previous),
                tcDeepItemId(
                        selected),
                selected.getItem()
                                instanceof traincraft.item.track.LegacyMediumCurveTrackItem
                        ? selectedDirectionLeft(
                                blueprint)
                        : stackUnifiedLeft(
                                selected),
                requested,
                playerYaw,
                source.segment,
                source.outward,
                required,
                placement.anchor(),
                chosenFacing,
                placement.exitSegment(),
                placement.exitOutward(),
                tcDeepNearestCellDistance(
                        requested,
                        chosenCells),
                chosenRecordedOverlap);

        traincraft.Traincraft.LOGGER.info(
                "[TC-BP-DEEP-PREV-FOOTPRINT] cells={}",
                previousCells);

        traincraft.Traincraft.LOGGER.info(
                "[TC-BP-DEEP-CHOSEN-FOOTPRINT] cells={}",
                chosenCells);

        tcDeepLogPreviousGeometry(
                previousStack,
                previousAnchor,
                previousYaw);

        tcDeepLogChosenEndpoints(
                selected,
                placement);

        if (selected.getItem()
                instanceof traincraft.item.track.LegacyMediumCurveTrackItem) {
            int last =
                    traincraft.block.track.LegacyMediumCurveRailBlock
                            .PART_COUNT
                            - 1;

            traincraft.Traincraft.LOGGER.info(
                    "[TC-BP-DEEP-MEDIUM-MATRIX] required={} requested={} lastPart={}",
                    required,
                    requested,
                    last);

            for (net.minecraft.core.Direction candidateFacing
                    : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                for (int attachedPart
                        : new int[]{0, last}) {
                    net.minecraft.core.Direction attachedOutward =
                            mediumEndpointOutward(
                                    candidateFacing,
                                    attachedPart);

                    BlockPos root =
                            required.subtract(
                                    traincraft.block.track.LegacyMediumCurveRailBlock
                                            .offsetForPart(
                                                    BlockPos.ZERO,
                                                    candidateFacing,
                                                    attachedPart));

                    BlockPos attachedLogical =
                            traincraft.block.track.LegacyMediumCurveRailBlock
                                    .offsetForPart(
                                            root,
                                            candidateFacing,
                                            attachedPart);

                    java.util.List<BlockPos> cells =
                            placementCells(
                                    selected,
                                    root,
                                    candidateFacing.toYRot());

                    boolean chosen =
                            placement.anchor().equals(
                                    root)
                                    && chosenFacing
                                            == candidateFacing;

                    traincraft.Traincraft.LOGGER.info(
                            "[TC-BP-DEEP-MEDIUM-CANDIDATE] facing={} attachPart={} attachOut={} faceToFace={} root={} modelOwnerPart0={} attached={} endpointGap={} aimDist={} recordedOverlap={} chosen={} cells={}",
                            candidateFacing,
                            attachedPart,
                            attachedOutward,
                            attachedOutward
                                    == source.outward.getOpposite(),
                            root,
                            traincraft.block.track.LegacyMediumCurveRailBlock
                                    .offsetForPart(
                                            root,
                                            candidateFacing,
                                            0),
                            attachedLogical,
                            tcDeepDistance(
                                    attachedLogical,
                                    required),
                            tcDeepNearestCellDistance(
                                    requested,
                                    cells),
                            tcDeepOverlapCount(
                                    cells,
                                    occupied),
                            chosen,
                            cells);
                }
            }
        } else {
            ItemStack resolved =
                    blueprintResolvedBatchStack(
                            selected);

            if (resolved.getItem()
                    instanceof traincraft.item.track.LegacyBatchTrackItem batch) {
                traincraft.block.track.LegacyBatchTrackSpec spec =
                        blueprintBatchSpec(
                                batch);

                if (spec != null
                        && !spec.endpoints().isEmpty()) {
                    String previousBatchId =
                            null;

                    ItemStack previousResolved =
                            blueprintResolvedBatchStack(
                                    previousStack);

                    if (previousResolved.getItem()
                            instanceof traincraft.item.track.LegacyBatchTrackItem previousBatch) {
                        traincraft.block.track.LegacyBatchTrackSpec previousSpec =
                                blueprintBatchSpec(
                                        previousBatch);

                        previousBatchId =
                                previousSpec == null
                                        ? null
                                        : previousSpec.id();
                    }

                    boolean diagonalFamily =
                            blueprintDiagonalConnectorFamily(
                                    spec.id())
                                    || blueprintDiagonalConnectorFamily(
                                            previousBatchId);

                    traincraft.Traincraft.LOGGER.info(
                            "[TC-BP-DEEP-BATCH-MATRIX] id={} required={} requested={} endpoints={} partCount={} diagonalFamily={}",
                            spec.id(),
                            required,
                            requested,
                            spec.endpoints().size(),
                            spec.partCount(),
                            diagonalFamily);

                    for (net.minecraft.core.Direction candidateFacing
                            : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                        for (traincraft.block.track.LegacyBatchTrackSpec.Endpoint endpoint
                                : spec.endpoints()) {
                            BlockPos logicalOffset =
                                    spec.endpointPosition(
                                            BlockPos.ZERO,
                                            candidateFacing,
                                            endpoint);

                            BlockPos root =
                                    required.subtract(
                                            logicalOffset);

                            BlockPos anchor =
                                    blueprintBatchAnchorFromRoot(
                                            spec,
                                            root,
                                            candidateFacing);

                            BlockPos logical =
                                    spec.endpointPosition(
                                            root,
                                            candidateFacing,
                                            endpoint);

                            BlockPos physical =
                                    spec.offsetForPart(
                                            root,
                                            candidateFacing,
                                            endpoint.part());

                            net.minecraft.core.Direction outward =
                                    spec.rotateDirection(
                                            candidateFacing,
                                            endpoint.outward());

                            boolean faceToFace =
                                    outward
                                            == source.outward.getOpposite();

                            boolean cornerHandoff =
                                    diagonalFamily
                                            && outward.getAxis()
                                                    != source.outward.getAxis();

                            java.util.List<BlockPos> cells =
                                    placementCells(
                                            selected,
                                            anchor,
                                            candidateFacing.toYRot());

                            boolean chosen =
                                    placement.anchor().equals(
                                            anchor)
                                            && chosenFacing
                                                    == candidateFacing;

                            traincraft.Traincraft.LOGGER.info(
                                    "[TC-BP-DEEP-BATCH-CANDIDATE] id={} role={} facing={} attachPart={} declaredOut={} worldOut={} faceToFace={} cornerHandoff={} root={} anchor={} guidePart0={} logical={} physical={} endpointGap={} aimDist={} recordedOverlap={} chosen={} cells={}",
                                    spec.id(),
                                    tcDeepEndpointRole(
                                            spec,
                                            endpoint),
                                    candidateFacing,
                                    endpoint.part(),
                                    endpoint.outward(),
                                    outward,
                                    faceToFace,
                                    cornerHandoff,
                                    root,
                                    anchor,
                                    spec.offsetForPart(
                                            root,
                                            candidateFacing,
                                            0),
                                    logical,
                                    physical,
                                    tcDeepDistance(
                                            logical,
                                            required),
                                    tcDeepNearestCellDistance(
                                            requested,
                                            cells),
                                    tcDeepOverlapCount(
                                            cells,
                                            occupied),
                                    chosen,
                                    cells);
                        }
                    }
                }
            } else {
                int length =
                        straightLength(
                                selected);

                if (length > 0) {
                    traincraft.Traincraft.LOGGER.info(
                            "[TC-BP-DEEP-STRAIGHT] length={} required={} chosenAnchor={} facing={} exit={} cells={}",
                            length,
                            required,
                            placement.anchor(),
                            chosenFacing,
                            placement.exitSegment(),
                            chosenCells);
                } else {
                    traincraft.Traincraft.LOGGER.info(
                            "[TC-BP-DEEP-UNKNOWN] selected={} required={} chosenAnchor={} facing={} cells={}",
                            tcDeepItemId(
                                    selected),
                            required,
                            placement.anchor(),
                            chosenFacing,
                            chosenCells);
                }
            }
        }

        traincraft.Traincraft.LOGGER.info(
                "[TC-BP-DEEP] END selected={} chosenAnchor={} chosenFacing={}",
                tcDeepItemId(
                        selected),
                placement.anchor(),
                chosenFacing);
    }
    public static void debugPreviewPlacement(
            ItemStack blueprint,
            ItemStack selected,
            BlockPos requested,
            float playerYaw,
            BlueprintPlacement placement) {
        if (blueprint == null
                || selected == null
                || selected.isEmpty()
                || placement == null) {
            return;
        }

        CompoundTag data =
                existingData(blueprint);

        if (data == null
                || !data.contains(ORIGIN)) {
            return;
        }

        ListTag segments =
                data.getList(
                        SEGMENTS,
                        CompoundTag.TAG_COMPOUND);

        if (segments.isEmpty()) {
            return;
        }

        BlockPos origin =
                BlockPos.of(
                        data.getLong(ORIGIN));

        CompoundTag previous =
                segments.getCompound(
                        segments.size() - 1);

        RouteEndpoint source =
                blueprintPreviewSourceEndpoint(
                        segments,
                        segments.size() - 1,
                        origin,
                        requested,
                        playerYaw,
                        selected);

        if (source == null) {
            return;
        }

        BlockPos required =
                source.segment.relative(
                        source.outward);
        // TC_BATCH8_R4A5D_DIAMOND_DIAGONAL_PROBE_DEBUG
        BlueprintRouteHeading8 tcDebugSourceHeading =
                blueprintRouteHeadingForSource(
                        segments,
                        segments.size() - 1,
                        origin,
                        source);

        traincraft.block.track.LegacyBatchTrackSpec tcDebugSelectedSpec =
                blueprintBatchSpecFromStack(
                        selected);

        if (tcDebugSelectedSpec != null
                && blueprintIsDiagonalCrossing8WayFamily(
                        tcDebugSelectedSpec.id())
                && tcDebugSourceHeading != null
                && tcDebugSourceHeading.isDiagonal()) {
            required =
                    source.segment.offset(
                            tcDebugSourceHeading.dx,
                            0,
                            tcDebugSourceHeading.dz);
        }

        // TC_BATCH8_R3ZZT_45CURVE_DIAGONAL_HANDOFF_DEBUG
        //
        // A 45-degree curve's far endpoint has a true diagonal RouteHeading8
        // tangent even though its legacy guide-cell outward is cardinal. When
        // another 45-degree curve follows that diagonal endpoint, advancing
        // only by source.outward keeps endpointGap at zero in guide-cell terms
        // but leaves the railheads one cell sideways in the rendered geometry.
        //
        // Scope this correction ONLY to 45-curve -> 45-curve handoffs and ONLY
        // when the actual source tangent is diagonal. Medium45 switch routing
        // stays on the frozen r3zzs contract and is deliberately untouched.
        String tc45PreviousSpec =
                blueprintBatchSpecId(
                        segmentTrackStack(
                                previous));

        String tc45SelectedSpec =
                blueprintBatchSpecId(
                        selected);

        BlueprintRouteHeading8 tc45SourceHeading =
                blueprintRouteHeadingForSource(
                        segments,
                        segments.size() - 1,
                        origin,
                        source);

        if (tc45SourceHeading != null
                && tc45SourceHeading.isDiagonal()
                && blueprintUsesTrueDiagonalCellHandoff(
                        tc45PreviousSpec,
                        tc45SelectedSpec)) {
            BlockPos tc45DiagonalRequired =
                    tcBranchDiagonalProbe(
                            source,
                            tc45SourceHeading);

            if (tc45DiagonalRequired != null) {
                required =
                        tc45DiagonalRequired;
            }
        }

        net.minecraft.resources.ResourceLocation selectedKey =
                net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getKey(selected.getItem());

        String selectedId =
                selectedKey == null
                        ? "<unregistered>"
                        : selectedKey.toString();

        String previousId =
                segmentCanonicalItemId(
                        previous);

        boolean selectedLeft =
                selected.getItem()
                                instanceof traincraft.item.track.LegacyMediumCurveTrackItem
                        ? selectedDirectionLeft(blueprint)
                        : stackUnifiedLeft(selected);

        boolean previousLeft =
                segmentIsLeft(
                        previous);

        net.minecraft.core.Direction resultFacing =
                net.minecraft.core.Direction.fromYRot(
                        placement.yaw());

        int attachedPart = -1;
        BlockPos attachedSegment =
                placement.anchor();

        if (selected.getItem()
                instanceof traincraft.item.track.LegacyMediumCurveTrackItem) {
            int last =
                    traincraft.block.track.LegacyMediumCurveRailBlock
                            .PART_COUNT
                            - 1;

            BlockPos p0 =
                    traincraft.block.track.LegacyMediumCurveRailBlock
                            .offsetForPart(
                                    placement.anchor(),
                                    resultFacing,
                                    0);

            BlockPos pLast =
                    traincraft.block.track.LegacyMediumCurveRailBlock
                            .offsetForPart(
                                    placement.anchor(),
                                    resultFacing,
                                    last);

            int d0 =
                    Math.abs(p0.getX() - required.getX())
                            + Math.abs(p0.getY() - required.getY())
                            + Math.abs(p0.getZ() - required.getZ());

            int dLast =
                    Math.abs(pLast.getX() - required.getX())
                            + Math.abs(pLast.getY() - required.getY())
                            + Math.abs(pLast.getZ() - required.getZ());

            if (d0 <= dLast) {
                attachedPart = 0;
                attachedSegment = p0;
            } else {
                attachedPart = last;
                attachedSegment = pLast;
            }
        } else if (straightLength(selected) > 0) {
            attachedPart = 0;
            attachedSegment = placement.anchor();
        } else {
            ItemStack resolved =
                    blueprintResolvedBatchStack(
                            selected);

            if (resolved.getItem()
                    instanceof traincraft.item.track.LegacyBatchTrackItem batch) {
                traincraft.block.track.LegacyBatchTrackSpec spec =
                        blueprintBatchSpec(batch);

                if (spec != null
                        && !spec.endpoints().isEmpty()) {
                    BlockPos root =
                            blueprintBatchRootFromAnchor(
                                    spec,
                                    placement.anchor(),
                                    resultFacing);

                    int bestDistance =
                            Integer.MAX_VALUE;

                    for (traincraft.block.track.LegacyBatchTrackSpec.Endpoint endpoint
                            : spec.endpoints()) {
                        BlockPos logical =
                                spec.endpointPosition(
                                        root,
                                        resultFacing,
                                        endpoint);

                        int distance =
                                Math.abs(logical.getX() - required.getX())
                                        + Math.abs(logical.getY() - required.getY())
                                        + Math.abs(logical.getZ() - required.getZ());

                        if (distance < bestDistance) {
                            bestDistance = distance;
                            attachedPart = endpoint.part();
                            attachedSegment = logical;
                        }
                    }
                }
            }
        }

        int endpointGap =
                Math.abs(attachedSegment.getX() - required.getX())
                        + Math.abs(attachedSegment.getY() - required.getY())
                        + Math.abs(attachedSegment.getZ() - required.getZ());

        String debugKey =
                previousId
                        + "|" + previousLeft
                        + "|" + source.segment.asLong()
                        + "|" + source.outward
                        + "|" + selectedId
                        + "|" + selectedLeft
                        + "|" + placement.anchor().asLong()
                        + "|" + placement.yaw()
                        + "|" + attachedPart
                        + "|" + attachedSegment.asLong()
                        + "|" + endpointGap
                        + "|" + (placement.exitSegment() == null
                                ? "null"
                                : placement.exitSegment().asLong())
                        + "|" + placement.exitOutward();

        if (debugKey.equals(tcLastPreviewDebugKey)) {
            return;
        }

        tcLastPreviewDebugKey = debugKey;

        traincraft.Traincraft.LOGGER.info(
                "[TC-R3ZX-BP-PREVIEW] prev={} prevLeft={} sourceSegment={} sourceOut={} required={} selected={} selectedLeft={} requested={} resultAnchor={} resultFacing={} attachedPart={} attachedSegment={} endpointGap={} resultExit={} resultExitOut={} playerYaw={}",
                previousId,
                previousLeft,
                source.segment,
                source.outward,
                required,
                selectedId,
                selectedLeft,
                requested,
                placement.anchor(),
                resultFacing,
                attachedPart,
                attachedSegment,
                endpointGap,
                placement.exitSegment(),
                placement.exitOutward(),
                playerYaw);
    
        tcDeepBlueprintSnapDebug(
                blueprint,
                selected,
                requested,
                playerYaw,
                placement);}
    // TC_BATCH8_R3ZZD_MEDIUM45_DUAL_EXIT
    //
    // A Medium 45 switch is a three-endpoint turnout: one entry plus straight
    // and diverging exits. r3zzc correctly snapped the switch INTO the route,
    // but then collapsed both outgoing choices into one stored RouteExit.
    //
    // Keep the stored exit as the stable/default route, but while the switch
    // is the previous Blueprint piece select either legal outgoing endpoint
    // from the player's requested cell. This mirrors how the accepted manual
    // placement lets the user approach the straight or diagonal branch.

    private static boolean blueprintIsMedium45Switch(
            traincraft.block.track.LegacyBatchTrackSpec spec) {
        return spec != null
                && spec.id() != null
                && spec.id().startsWith(
                        "track_switch_45_medium_");
    }

    private static int blueprintCellDistance(
            BlockPos a,
            BlockPos b) {
        return Math.abs(a.getX() - b.getX())
                + Math.abs(a.getY() - b.getY())
                + Math.abs(a.getZ() - b.getZ());
    }

    private static traincraft.block.track.LegacyBatchTrackSpec.Endpoint
            blueprintMedium45IncomingEndpoint(
                    ListTag segments,
                    int switchIndex,
                    BlockPos origin,
                    traincraft.block.track.LegacyBatchTrackSpec spec,
                    BlockPos root,
                    net.minecraft.core.Direction facing) {
        if (switchIndex > 0) {
            CompoundTag beforeSwitch =
                    segments.getCompound(
                            switchIndex - 1);

            RouteEndpoint beforeExit =
                    routeExitFromStored(
                            beforeSwitch,
                            origin);

            if (beforeExit != null) {
                BlockPos requiredEntry =
                        beforeExit.segment.relative(
                                beforeExit.outward);

                net.minecraft.core.Direction requiredOutward =
                        beforeExit.outward.getOpposite();

                traincraft.block.track.LegacyBatchTrackSpec.Endpoint best =
                        null;
                int bestDistance =
                        Integer.MAX_VALUE;

                for (traincraft.block.track.LegacyBatchTrackSpec.Endpoint endpoint
                        : spec.endpoints()) {
                    BlockPos logical =
                            spec.endpointPosition(
                                    root,
                                    facing,
                                    endpoint);

                    net.minecraft.core.Direction outward =
                            spec.rotateDirection(
                                    facing,
                                    endpoint.outward());

                    int distance =
                            blueprintCellDistance(
                                    logical,
                                    requiredEntry);

                    if (outward != requiredOutward) {
                        distance += 1000;
                    }

                    if (distance < bestDistance) {
                        best = endpoint;
                        bestDistance = distance;
                    }
                }

                if (best != null) {
                    return best;
                }
            }
        }

        // Legacy/fallback Blueprints use the primary manual-placement endpoint
        // as their entry anchor.
        return spec.endpoints().isEmpty()
                ? null
                : spec.endpoints().get(0);
    }

    // TC_BATCH8_R3ZZE_MEDIUM45_ROUTE_COMPAT
    //
    // The Medium 45 switch has a CARDINAL straight outlet (part 2) and a
    // TRUE-45 diverging outlet (part 4). r3zzd made both aim-selectable, but
    // that also allowed a cardinal-only piece such as the dedicated Medium
    // Curve to be attached to the true-45 branch. The guide endpoints still
    // report endpointGap=0, yet the rendered rails cannot share a tangent.
    //
    // Match the accepted world-placement family contract instead:
    //   - Normal/straight/90-degree curves -> straight outlet;
    //   - 45/diagonal/crossing family      -> diverging outlet.
    //
    // This changes Blueprint outlet selection only. Real track placement,
    // meshes, blockstates and path math stay frozen.
    private static boolean blueprintMedium45IncomingPrefersBranch(
            ItemStack selected) {
        if (selected == null
                || selected.isEmpty()) {
            return false;
        }

        ItemStack resolved =
                blueprintResolvedBatchStack(
                        selected);

        if (!(resolved.getItem()
                instanceof traincraft.item.track.LegacyBatchTrackItem batch)) {
            return false;
        }

        traincraft.block.track.LegacyBatchTrackSpec spec =
                blueprintBatchSpec(batch);

        return spec != null
                && blueprintDiagonalConnectorFamily(
                        spec.id());
    }
    // TC_BATCH8_R3ZZJ_BLUEPRINT_8WAY_TANGENT_PARITY
    //
    // Port of the runtime-accepted LegacyBatchTrackItem RouteHeading8
    // placement contract. The physical multipart guide footprint remains on
    // integer/cardinal BlockPos cells, while the real rail tangent may be one
    // of the four diagonal headings.
    private enum BlueprintRouteHeading8 {
        NORTH(0, -1),
        NORTH_EAST(1, -1),
        EAST(1, 0),
        SOUTH_EAST(1, 1),
        SOUTH(0, 1),
        SOUTH_WEST(-1, 1),
        WEST(-1, 0),
        NORTH_WEST(-1, -1);

        private final int dx;
        private final int dz;

        BlueprintRouteHeading8(
                int dx,
                int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        private BlueprintRouteHeading8 opposite() {
            return fromStep(
                    -dx,
                    -dz);
        }

        private boolean isDiagonal() {
            return dx != 0
                    && dz != 0;
        }

        private BlueprintRouteHeading8 rotateClockwise() {
            return fromStep(
                    -dz,
                    dx);
        }

        private static BlueprintRouteHeading8 fromDirection(
                net.minecraft.core.Direction direction) {
            return switch (direction) {
                case NORTH -> NORTH;
                case EAST -> EAST;
                case SOUTH -> SOUTH;
                case WEST -> WEST;
                default -> throw new IllegalArgumentException(
                        "Horizontal direction required: " + direction);
            };
        }

        private static BlueprintRouteHeading8 fromStep(
                int dx,
                int dz) {
            int sx =
                    Integer.compare(
                            dx,
                            0);

            int sz =
                    Integer.compare(
                            dz,
                            0);

            for (BlueprintRouteHeading8 heading
                    : values()) {
                if (heading.dx == sx
                        && heading.dz == sz) {
                    return heading;
                }
            }

            throw new IllegalArgumentException(
                    "Non-zero horizontal route step required: dx="
                            + dx
                            + " dz="
                            + dz);
        }
    }

    private static boolean blueprintIsFortyFiveCurveSnapFamily(
            String id) {
        return id != null
                && id.startsWith(
                        "track_curve_45_")
                && (id.endsWith("_left")
                        || id.endsWith("_right"));
    }

    private static boolean blueprintIsDiagonalStraight8WayFamily(
            String id) {
        return "track_diagonal_straight_small".equals(id)
                || "track_diagonal_straight_medium".equals(id);
    }

    private static boolean blueprintIsMedium45Switch8WayFamily(
            String id) {
        return "track_switch_45_medium_left".equals(id)
                || "track_switch_45_medium_right".equals(id);
    }

    private static boolean blueprintIsDiagonalCrossing8WayFamily(
            String id) {
        if (id == null) {
            return false;
        }

        return switch (id) {
            case "track_diagonal_crossing",
                 "track_diagonal_two_ways_crossing",
                 "track_diagonal_four_ways_crossing",
                 "track_diamond_crossing",
                 "track_diamond_crossing_left",
                 "track_double_diamond_crossing",
                 "track_universal_crossing" -> true;
            default -> false;
        };
    }

    // TC_BATCH8_R3ZZU_TRUE_DIAGONAL_PAIR_MATRIX
    // TC_BATCH8_R3ZZU2_SWITCH45_CURVE45_CROSSPAIR
    //
    // Runtime evidence now proves four true-diagonal pair classes:
    //   45 curve        -> 45 curve
    //   Medium45 switch -> Medium45 switch
    //   Medium45 switch -> 45 curve
    //   45 curve        -> Medium45 switch
    //
    // This remains an explicit pair matrix rather than a blanket
    // diagonal-heading rule, so unrelated 8-way families are unchanged
    // until their physical guide-cell geometry is runtime-proven.
    private static boolean blueprintUsesTrueDiagonalCellHandoff(
            String previousSpecId,
            String selectedSpecId) {
        boolean previousFortyFiveCurve =
                blueprintIsFortyFiveCurveSnapFamily(
                        previousSpecId);

        boolean selectedFortyFiveCurve =
                blueprintIsFortyFiveCurveSnapFamily(
                        selectedSpecId);

        boolean previousMedium45Switch =
                blueprintIsMedium45Switch8WayFamily(
                        previousSpecId);

        boolean selectedMedium45Switch =
                blueprintIsMedium45Switch8WayFamily(
                        selectedSpecId);

        boolean fortyFiveCurveChain =
                previousFortyFiveCurve
                        && selectedFortyFiveCurve;

        boolean medium45SwitchChain =
                previousMedium45Switch
                        && selectedMedium45Switch;

        boolean medium45SwitchToCurve =
                previousMedium45Switch
                        && selectedFortyFiveCurve;

        boolean fortyFiveCurveToMedium45Switch =
                previousFortyFiveCurve
                        && selectedMedium45Switch;

        return fortyFiveCurveChain
                || medium45SwitchChain
                || medium45SwitchToCurve
                || fortyFiveCurveToMedium45Switch;
    }
    private static boolean blueprintIsEightWaySnapFamily(
            String id) {
        return blueprintIsDiagonalStraight8WayFamily(
                        id)
                || blueprintIsMedium45Switch8WayFamily(
                        id)
                || blueprintIsFortyFiveCurveSnapFamily(
                        id)
                || blueprintIsDiagonalCrossing8WayFamily(
                        id);
    }

    private static BlueprintRouteHeading8 blueprintRotateRouteHeading(
            BlueprintRouteHeading8 canonical,
            net.minecraft.core.Direction facing) {
        int turns =
                switch (facing) {
                    case NORTH -> 0;
                    case EAST -> 1;
                    case SOUTH -> 2;
                    case WEST -> 3;
                    default -> throw new IllegalArgumentException(
                            "Horizontal facing required: " + facing);
                };

        BlueprintRouteHeading8 result =
                canonical;

        for (int i = 0;
             i < turns;
             i++) {
            result =
                    result.rotateClockwise();
        }

        return result;
    }

    private static int blueprintDiagonalCrossingRouteIndex(
            traincraft.block.track.LegacyBatchTrackSpec crossingSpec,
            int endpointPart) {
        int minX =
                Integer.MAX_VALUE;
        int maxX =
                Integer.MIN_VALUE;
        int minZ =
                Integer.MAX_VALUE;
        int maxZ =
                Integer.MIN_VALUE;

        for (int part = 0;
             part < crossingSpec.partCount();
             part++) {
            traincraft.block.track.LegacyBatchTrackSpec.Point point =
                    crossingSpec.pointForPart(
                            part);

            minX =
                    Math.min(
                            minX,
                            point.x);

            maxX =
                    Math.max(
                            maxX,
                            point.x);

            minZ =
                    Math.min(
                            minZ,
                            point.z);

            maxZ =
                    Math.max(
                            maxZ,
                            point.z);
        }

        traincraft.block.track.LegacyBatchTrackSpec.Point point =
                crossingSpec.pointForPart(
                        endpointPart);

        if ((point.x == minX
                        && point.z == maxZ)
                || (point.x == maxX
                        && point.z == minZ)) {
            return 0;
        }

        if ((point.x == maxX
                        && point.z == maxZ)
                || (point.x == minX
                        && point.z == minZ)) {
            return 1;
        }

        return -1;
    }

    private static BlueprintRouteHeading8
            blueprintDiagonalCrossingEndpointHeading(
                    traincraft.block.track.LegacyBatchTrackSpec crossingSpec,
                    BlockPos root,
                    net.minecraft.core.Direction facing,
                    traincraft.block.track.LegacyBatchTrackSpec.Endpoint endpoint) {
        int routeIndex =
                blueprintDiagonalCrossingRouteIndex(
                        crossingSpec,
                        endpoint.part());

        if (routeIndex < 0) {
            return null;
        }

        traincraft.block.track.LegacyBatchTrackSpec.Endpoint partner =
                null;

        for (traincraft.block.track.LegacyBatchTrackSpec.Endpoint candidate
                : crossingSpec.endpoints()) {
            if (candidate.part()
                    == endpoint.part()) {
                continue;
            }

            if (blueprintDiagonalCrossingRouteIndex(
                            crossingSpec,
                            candidate.part())
                    == routeIndex) {
                partner =
                        candidate;
                break;
            }
        }

        if (partner == null) {
            return null;
        }

        BlockPos current =
                crossingSpec.endpointPosition(
                        root,
                        facing,
                        endpoint);

        BlockPos opposite =
                crossingSpec.endpointPosition(
                        root,
                        facing,
                        partner);

        int dx =
                current.getX()
                        - opposite.getX();

        int dz =
                current.getZ()
                        - opposite.getZ();

        if (dx == 0
                || dz == 0) {
            return null;
        }

        return BlueprintRouteHeading8.fromStep(
                dx,
                dz);
    }

    private static BlueprintRouteHeading8 blueprintRouteHeadingForEndpoint(
            traincraft.block.track.LegacyBatchTrackSpec endpointSpec,
            BlockPos root,
            net.minecraft.core.Direction facing,
            traincraft.block.track.LegacyBatchTrackSpec.Endpoint endpoint) {
        // Medium 45 switch: p4 is the real diagonal branch tangent even
        // though the Manhattan guide-cell outward is cardinal.
        if (endpoint.part() == 4) {
            if ("track_switch_45_medium_left".equals(
                    endpointSpec.id())) {
                return blueprintRotateRouteHeading(
                        BlueprintRouteHeading8.NORTH_WEST,
                        facing);
            }

            if ("track_switch_45_medium_right".equals(
                    endpointSpec.id())) {
                return blueprintRotateRouteHeading(
                        BlueprintRouteHeading8.NORTH_EAST,
                        facing);
            }
        }

        // Classic 45-degree curves start cardinal at p0 and leave their far
        // endpoint on a true diagonal tangent.
        if (blueprintIsFortyFiveCurveSnapFamily(
                        endpointSpec.id())
                && endpoint.part()
                        == endpointSpec.partCount() - 1) {
            BlueprintRouteHeading8 canonical =
                    endpointSpec.id().endsWith(
                                    "_left")
                            ? BlueprintRouteHeading8.NORTH_WEST
                            : BlueprintRouteHeading8.NORTH_EAST;

            return blueprintRotateRouteHeading(
                    canonical,
                    facing);
        }

        if (blueprintIsDiagonalCrossing8WayFamily(
                endpointSpec.id())) {
            BlueprintRouteHeading8 crossingHeading =
                    blueprintDiagonalCrossingEndpointHeading(
                            endpointSpec,
                            root,
                            facing,
                            endpoint);

            if (crossingHeading != null) {
                return crossingHeading;
            }
        }

        // Restored original Small Diagonal metadata.
        if ("track_diagonal_straight_small".equals(
                endpointSpec.id())) {
            if (endpoint.part() == 0) {
                return blueprintRotateRouteHeading(
                        BlueprintRouteHeading8.SOUTH_WEST,
                        facing);
            }

            if (endpoint.part() == 2) {
                return blueprintRotateRouteHeading(
                        BlueprintRouteHeading8.NORTH_EAST,
                        facing);
            }
        }

        // Medium diagonal derives its true tangent from its logical ends.
        if (blueprintIsDiagonalStraight8WayFamily(
                        endpointSpec.id())
                && endpointSpec.endpoints().size()
                        == 2) {
            traincraft.block.track.LegacyBatchTrackSpec.Endpoint other =
                    endpointSpec.endpoints()
                            .get(0);

            if (other.part()
                    == endpoint.part()) {
                other =
                        endpointSpec.endpoints()
                                .get(1);
            }

            BlockPos current =
                    endpointSpec.endpointPosition(
                            root,
                            facing,
                            endpoint);

            BlockPos inside =
                    endpointSpec.endpointPosition(
                            root,
                            facing,
                            other);

            int outwardDx =
                    current.getX()
                            - inside.getX();

            int outwardDz =
                    current.getZ()
                            - inside.getZ();

            if (outwardDx != 0
                    || outwardDz != 0) {
                return BlueprintRouteHeading8.fromStep(
                        outwardDx,
                        outwardDz);
            }
        }

        return BlueprintRouteHeading8.fromDirection(
                endpointSpec.rotateDirection(
                        facing,
                        endpoint.outward()));
    }

    private static BlueprintRouteHeading8 blueprintRouteHeadingForSource(
            ListTag segments,
            int previousIndex,
            BlockPos origin,
            RouteEndpoint source) {
        if (segments == null
                || previousIndex < 0
                || previousIndex >= segments.size()
                || source == null) {
            return null;
        }

        CompoundTag previous =
                segments.getCompound(
                        previousIndex);

        ItemStack previousStack =
                segmentTrackStack(
                        previous);

        ItemStack resolved =
                blueprintResolvedBatchStack(
                        previousStack);

        if (resolved.getItem()
                instanceof traincraft.item.track.LegacyBatchTrackItem batch) {
            traincraft.block.track.LegacyBatchTrackSpec spec =
                    blueprintBatchSpec(
                            batch);

            if (spec != null) {
                BlockPos anchor =
                        origin.offset(
                                previous.getInt("X"),
                                previous.getInt("Y"),
                                previous.getInt("Z"));

                net.minecraft.core.Direction facing =
                        net.minecraft.core.Direction.fromYRot(
                                previous.getFloat("Yaw"));

                BlockPos root =
                        blueprintBatchRootFromAnchor(
                                spec,
                                anchor,
                                facing);

                for (traincraft.block.track.LegacyBatchTrackSpec.Endpoint endpoint
                        : spec.endpoints()) {
                    BlockPos logical =
                            spec.endpointPosition(
                                    root,
                                    facing,
                                    endpoint);

                    net.minecraft.core.Direction outward =
                            spec.rotateDirection(
                                    facing,
                                    endpoint.outward());

                    if (logical.equals(
                                    source.segment)
                            && outward
                                    == source.outward) {
                        return blueprintRouteHeadingForEndpoint(
                                spec,
                                root,
                                facing,
                                endpoint);
                    }
                }
            }
        }

        // Medium Curve, Normal/Straight and all non-Batch fallbacks use their
        // ordinary cardinal endpoint tangent.
        return BlueprintRouteHeading8.fromDirection(
                source.outward);
    }
    private static RouteEndpoint blueprintMedium45AimSelectedExit(
            ListTag segments,
            int switchIndex,
            BlockPos origin,
            BlockPos requested,
            float playerYaw,
            ItemStack selected) {
        if (switchIndex < 0
                || switchIndex >= segments.size()) {
            return null;
        }

        CompoundTag segment =
                segments.getCompound(
                        switchIndex);

        ItemStack stack =
                segmentTrackStack(
                        segment);

        ItemStack resolved =
                blueprintResolvedBatchStack(
                        stack);

        if (!(resolved.getItem()
                instanceof traincraft.item.track.LegacyBatchTrackItem batch)) {
            return null;
        }

        traincraft.block.track.LegacyBatchTrackSpec spec =
                blueprintBatchSpec(
                        batch);

        if (!blueprintIsMedium45Switch(
                        spec)
                || spec.endpoints().size() < 3) {
            return null;
        }

        BlockPos anchor =
                origin.offset(
                        segment.getInt("X"),
                        segment.getInt("Y"),
                        segment.getInt("Z"));

        net.minecraft.core.Direction facing =
                net.minecraft.core.Direction.fromYRot(
                        segment.getFloat("Yaw"));

        BlockPos root =
                blueprintBatchRootFromAnchor(
                        spec,
                        anchor,
                        facing);

        traincraft.block.track.LegacyBatchTrackSpec.Endpoint incoming =
                blueprintMedium45IncomingEndpoint(
                        segments,
                        switchIndex,
                        origin,
                        spec,
                        root,
                        facing);

        if (incoming == null) {
            return null;
        }

        // Entering from one of the far ends has only one legal topological
        // continuation: through the common throat.
        if (incoming.part() != 0) {
            for (traincraft.block.track.LegacyBatchTrackSpec.Endpoint endpoint
                    : spec.endpoints()) {
                if (endpoint.part() != 0) {
                    continue;
                }

                return new RouteEndpoint(
                        spec.endpointPosition(
                                root,
                                facing,
                                endpoint),
                        spec.rotateDirection(
                                facing,
                                endpoint.outward()));
            }

            return null;
        }

        boolean mediumCurveSelected =
                selected != null
                        && !selected.isEmpty()
                        && selected.getItem()
                                instanceof traincraft.item.track.LegacyMediumCurveTrackItem;

        if (mediumCurveSelected) {
            // TC_BATCH8_R3ZZK_MEDIUM45_MEDIUM_DUAL_OUTLET
            // TC_BATCH8_R3ZZS_MEDIUM45_ROUTE_COMPAT_RESTORE
            //
            // Runtime r3zzr1 proof established the actual topology:
            //
            //   Medium45 STRAIGHT p2 -> dedicated Medium Curve
            //   Medium45 BRANCH   p4 -> 45-degree / diagonal curve family
            //
            // The branch carries a true diagonal RouteHeading8 tangent. The
            // dedicated Medium Curve has cardinal endpoint tangents, so a
            // guide-cell endpointGap=0 there is not enough to make the rails
            // geometrically continuous. The real track_curve_45_medium_right
            // is an exact 8-way pair and is the correct branch continuation.
            //
            // Keep the r3zzk physical-endpoint scoring, but filter the dedicated
            // Medium Curve to cardinal Medium45 outlets only. This restores the
            // route-compatible contract without touching world placement,
            // models, path math, or the working 45-degree curve branch.
            traincraft.block.track.LegacyBatchTrackSpec.Endpoint best =
                    null;
            BlockPos bestExit =
                    null;
            net.minecraft.core.Direction bestOutward =
                    null;
            int bestScore =
                    Integer.MAX_VALUE;
            int bestPart =
                    Integer.MAX_VALUE;

            for (traincraft.block.track.LegacyBatchTrackSpec.Endpoint endpoint
                    : spec.endpoints()) {
                if (endpoint == incoming) {
                    continue;
                }

                BlueprintRouteHeading8 heading =
                        blueprintRouteHeadingForEndpoint(
                                spec,
                                root,
                                facing,
                                endpoint);

                // Dedicated Medium Curve is cardinal-only. Leave the true-45
                // branch to track_curve_45_* / diagonal 8-way families.
                if (heading == null
                        || heading.isDiagonal()) {
                    continue;
                }

                BlockPos exit =
                        spec.endpointPosition(
                                root,
                                facing,
                                endpoint);

                net.minecraft.core.Direction outward =
                        spec.rotateDirection(
                                facing,
                                endpoint.outward());

                BlockPos connector =
                        exit.relative(
                                outward);

                int score =
                        tcDeepDistance(
                                requested,
                                connector);

                if (score < bestScore
                        || (score == bestScore
                                && endpoint.part() < bestPart)) {
                    best =
                            endpoint;
                    bestExit =
                            exit;
                    bestOutward =
                            outward;
                    bestScore =
                            score;
                    bestPart =
                            endpoint.part();
                }
            }

            if (best != null
                    && bestExit != null
                    && bestOutward != null) {
                return new RouteEndpoint(
                        bestExit,
                        bestOutward);
            }

            return null;
        }
        // Preserve r3zze/r3zzd behavior for all other track families:
        // diagonal/45 families use the branch, cardinal families use straight.
        boolean preferBranch =
                blueprintMedium45IncomingPrefersBranch(
                        selected);

        int desiredPart =
                preferBranch
                        ? 4
                        : 2;

        for (traincraft.block.track.LegacyBatchTrackSpec.Endpoint endpoint
                : spec.endpoints()) {
            if (endpoint.part()
                    != desiredPart) {
                continue;
            }

            return new RouteEndpoint(
                    spec.endpointPosition(
                            root,
                            facing,
                            endpoint),
                    spec.rotateDirection(
                            facing,
                            endpoint.outward()));
        }

        return null;
    }

    // TC_BATCH8_R3ZZV1_COMPATIBLE_ALL_ENDPOINT_ROUTER
    //
    // Test whether the currently selected next track owns an endpoint tangent
    // that can face the requested route heading. This is deliberately about
    // TRUE rail tangent compatibility, not merely neighboring guide cells.
    private static boolean blueprintSelectedCanAcceptRouteHeading(
            ItemStack selected,
            BlueprintRouteHeading8 requiredIncomingHeading) {
        if (selected == null
                || selected.isEmpty()
                || requiredIncomingHeading == null) {
            return true;
        }

        ItemStack resolved =
                blueprintResolvedBatchStack(
                        selected);

        if (resolved.getItem()
                instanceof traincraft.item.track.LegacyBatchTrackItem batch) {
            traincraft.block.track.LegacyBatchTrackSpec selectedSpec =
                    blueprintBatchSpec(
                            batch);

            if (selectedSpec == null
                    || selectedSpec.endpoints() == null
                    || selectedSpec.endpoints().isEmpty()) {
                return true;
            }

            for (net.minecraft.core.Direction candidateFacing
                    : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                for (traincraft.block.track.LegacyBatchTrackSpec.Endpoint endpoint
                        : selectedSpec.endpoints()) {
                    BlueprintRouteHeading8 heading =
                            blueprintRouteHeadingForEndpoint(
                                    selectedSpec,
                                    BlockPos.ZERO,
                                    candidateFacing,
                                    endpoint);

                    if (heading == requiredIncomingHeading) {
                        return true;
                    }
                }
            }

            return false;
        }

        // The dedicated legacy Medium Curve and ordinary straight families
        // expose cardinal endpoint tangents only.
        if (selected.getItem()
                instanceof traincraft.item.track.LegacyMediumCurveTrackItem) {
            return !requiredIncomingHeading.isDiagonal();
        }

        if (straightLength(
                        selected)
                > 0) {
            return !requiredIncomingHeading.isDiagonal();
        }

        // Unknown/custom track family: preserve legacy behavior rather than
        // hiding it. Its normal placement solver still decides validity.
        return true;
    }
    // TC_BATCH8_R3ZZV_ALL_ENDPOINT_ROUTER
    //
    // Blueprint route navigation is user-directed, not hard-wired by the
    // currently selected next-track family.
    //
    // For any LegacyBatchTrackItem with 2+ exposed endpoints:
    //   * every endpoint is aim-selectable;
    //   * the endpoint used to enter the piece is NOT excluded;
    //   * aiming back at it is therefore a deliberate reverse/back-build;
    //   * switches/crossings can be traversed toward any aimed endpoint;
    //   * exact tangent/placement compatibility is still decided later by
    //     the existing placement solver and the r3zzu2 true-diagonal matrix.
    //
    // This removes the old Medium45-only p2/p4 family lock and the old
    // "far-end must go back through p0" restriction from preview routing.
    // TC_BATCH8_R3ZZV_ALL_ENDPOINT_ROUTER
    // TC_BATCH8_R3ZZV1_COMPATIBLE_ALL_ENDPOINT_ROUTER
    //
    // Every exposed Batch endpoint remains available by aim, INCLUDING the
    // entry endpoint for reverse/back-build. The only new filter is physical
    // tangent compatibility with the selected next track.
    //
    // Example: a normal 29x/cardinal curve can use a Medium45 switch's
    // cardinal outlet, but it cannot be forced onto that switch's true-45
    // diagonal branch. A true 45-degree curve CAN use that diagonal branch.
    private static RouteEndpoint blueprintPreviewSourceEndpoint(
            ListTag segments,
            int previousIndex,
            BlockPos origin,
            BlockPos requested,
            float playerYaw,
            ItemStack selected) {
        if (segments == null
                || previousIndex < 0
                || previousIndex >= segments.size()) {
            return null;
        }

        CompoundTag previous =
                segments.getCompound(
                        previousIndex);

        ItemStack previousStack =
                segmentTrackStack(
                        previous);

        ItemStack resolved =
                blueprintResolvedBatchStack(
                        previousStack);

        if (resolved.getItem()
                instanceof traincraft.item.track.LegacyBatchTrackItem batch) {
            traincraft.block.track.LegacyBatchTrackSpec spec =
                    blueprintBatchSpec(
                            batch);

            if (spec != null
                    && spec.endpoints() != null
                    && spec.endpoints().size() >= 2) {
                BlockPos anchor =
                        origin.offset(
                                previous.getInt("X"),
                                previous.getInt("Y"),
                                previous.getInt("Z"));

                net.minecraft.core.Direction facing =
                        net.minecraft.core.Direction.fromYRot(
                                previous.getFloat("Yaw"));

                BlockPos root =
                        blueprintBatchRootFromAnchor(
                                spec,
                                anchor,
                                facing);

                RouteEndpoint stored =
                        routeExitFromStored(
                                previous,
                                origin);

                traincraft.block.track.LegacyBatchTrackSpec.Endpoint best =
                        null;

                BlockPos bestExit =
                        null;

                net.minecraft.core.Direction bestOutward =
                        null;

                int bestScore =
                        Integer.MAX_VALUE;

                int bestPart =
                        Integer.MAX_VALUE;

                net.minecraft.core.Direction look =
                        net.minecraft.core.Direction.fromYRot(
                                playerYaw);

                for (traincraft.block.track.LegacyBatchTrackSpec.Endpoint endpoint
                        : spec.endpoints()) {
                    BlockPos exit =
                            spec.endpointPosition(
                                    root,
                                    facing,
                                    endpoint);

                    net.minecraft.core.Direction outward =
                            spec.rotateDirection(
                                    facing,
                                    endpoint.outward());

                    BlueprintRouteHeading8 sourceHeading =
                            blueprintRouteHeadingForEndpoint(
                                    spec,
                                    root,
                                    facing,
                                    endpoint);

                    if (sourceHeading != null
                            && !blueprintSelectedCanAcceptRouteHeading(
                                    selected,
                                    sourceHeading.opposite())) {
                        continue;
                    }

                    BlockPos connector =
                            exit.relative(
                                    outward);

                    int score =
                            Math.min(
                                    blueprintCellDistance(
                                            requested,
                                            exit),
                                    blueprintCellDistance(
                                            requested,
                                            connector))
                                    * 100;

                    // Stored route and yaw are tie-breakers only. Aim is still
                    // what selects among all physically compatible endpoints.
                    if (stored != null
                            && stored.segment.equals(
                                    exit)
                            && stored.outward
                                    == outward) {
                        score -= 4;
                    }

                    if (outward == look) {
                        score -= 1;
                    }

                    if (score < bestScore
                            || (score == bestScore
                                    && endpoint.part() < bestPart)) {
                        best =
                                endpoint;
                        bestExit =
                                exit;
                        bestOutward =
                                outward;
                        bestScore =
                                score;
                        bestPart =
                                endpoint.part();
                    }
                }

                if (best != null
                        && bestExit != null
                        && bestOutward != null) {
                    return new RouteEndpoint(
                            bestExit,
                            bestOutward);
                }

                // The selected track has no physical tangent match on any
                // endpoint of this previous piece. Do not create a guide-cell
                // "connection" that is visibly disconnected in the rail mesh.
                return null;
            }
        }

        return routeExitFromStored(
                previous,
                origin);
    }
    public static BlueprintPlacement previewPlacement(
            ItemStack blueprint,
            ItemStack selected,
            BlockPos requested,
            float playerYaw) {
        CompoundTag data =
                existingData(blueprint);

        if (data == null
                || !data.contains(ORIGIN)) {
            return freePlacement(
                    blueprint,
                    selected,
                    requested,
                    playerYaw);
        }

        ListTag segments =
                data.getList(
                        SEGMENTS,
                        CompoundTag.TAG_COMPOUND);

        if (segments.isEmpty()) {
            return freePlacement(
                    blueprint,
                    selected,
                    requested,
                    playerYaw);
        }

        BlockPos origin =
                BlockPos.of(
                        data.getLong(ORIGIN));

        CompoundTag previous =
                segments.getCompound(
                        segments.size() - 1);

        RouteEndpoint source =
                blueprintPreviewSourceEndpoint(
                        segments,
                        segments.size() - 1,
                        origin,
                        requested,
                        playerYaw,
                        selected);

        if (source == null) {
            return freePlacement(
                    blueprint,
                    selected,
                    requested,
                    playerYaw);
        }

        BlockPos required =
                source.segment.relative(
                        source.outward);
        // TC_BATCH8_R4A5C_DIAGONAL_TRUE_TANGENT_GUARD
        // Physical multipart connector cells stay cardinal BlockPos cells.
        // The rail tangent may still be NE/NW/SE/SW. Keep that distinction
        // authoritative before any family-specific continuation is accepted.
        BlueprintRouteHeading8 tcContinuationSourceHeading =
                blueprintRouteHeadingForSource(
                        segments,
                        segments.size() - 1,
                        origin,
                        source);

        // TC_BATCH8_R3ZZT_45CURVE_DIAGONAL_HANDOFF
        //
        // A 45-degree curve's far endpoint has a true diagonal RouteHeading8
        // tangent even though its legacy guide-cell outward is cardinal. When
        // another 45-degree curve follows that diagonal endpoint, advancing
        // only by source.outward keeps endpointGap at zero in guide-cell terms
        // but leaves the railheads one cell sideways in the rendered geometry.
        //
        // Scope this correction ONLY to 45-curve -> 45-curve handoffs and ONLY
        // when the actual source tangent is diagonal. Medium45 switch routing
        // stays on the frozen r3zzs contract and is deliberately untouched.
        String tc45PreviousSpec =
                blueprintBatchSpecId(
                        segmentTrackStack(
                                previous));

        String tc45SelectedSpec =
                blueprintBatchSpecId(
                        selected);

        BlueprintRouteHeading8 tc45SourceHeading =
                blueprintRouteHeadingForSource(
                        segments,
                        segments.size() - 1,
                        origin,
                        source);

        if (tc45SourceHeading != null
                && tc45SourceHeading.isDiagonal()
                && blueprintUsesTrueDiagonalCellHandoff(
                        tc45PreviousSpec,
                        tc45SelectedSpec)) {
            BlockPos tc45DiagonalRequired =
                    tcBranchDiagonalProbe(
                            source,
                            tc45SourceHeading);

            if (tc45DiagonalRequired != null) {
                required =
                        tc45DiagonalRequired;
            }
        }
        // TC_BATCH8_R3ZZP3_MEDIUM45_PHYSICAL_CONNECTOR
        //
        // r3zzo runtime proof:
        //   - the Medium45 branch itself is correctly identified by its true
        //     NORTH_WEST / NORTH_EAST / SOUTH_* RouteHeading8 metadata;
        //   - BUT the Medium Curve's physical guide endpoint is cardinal.
        // Moving the Medium attachment to the diagonal probe created an exact
        // one-cell endpoint gap. Therefore Medium keeps the physical connector
        // already computed as:
        //
        //     required = source.segment.relative(source.outward)
        //
        // Aim still chooses p2 versus p4 through r3zzk. RouteHeading8 remains
        // metadata for the switch route; it does not move a cardinal Medium
        // Curve endpoint off the switch's physical connector cell.

        int length =
                straightLength(selected);

        if (length > 0) {
            if (tcContinuationSourceHeading != null
                    && tcContinuationSourceHeading.isDiagonal()) {
                traincraft.Traincraft.LOGGER.info(
                        "[TC-BP-DIAGONAL-GUARD] selected={} source={} sourceOut={} sourceHeading={} requested={} reason=DIAGONAL_SOURCE_TO_CARDINAL_STRAIGHT",
                        tcDeepItemId(selected),
                        source.segment,
                        source.outward,
                        tcContinuationSourceHeading,
                        requested);

                return null;
            }
            net.minecraft.core.Direction facing =
                    source.outward;

            return new BlueprintPlacement(
                    required,
                    facing.toYRot(),
                    required.relative(
                            facing,
                            length - 1),
                    facing);
        }

        if (selected.getItem()
                instanceof traincraft.item.track.LegacyMediumCurveTrackItem) {
            int last =
                    traincraft.block.track.LegacyMediumCurveRailBlock
                            .PART_COUNT
                            - 1;

            record TcMediumCandidate(
                    BlockPos root,
                    net.minecraft.core.Direction facing,
                    int attachedPart,
                    int score) {}

            java.util.ArrayList<TcMediumCandidate> candidates =
                    new java.util.ArrayList<>();

            ItemStack previousStack =
                    segmentTrackStack(previous);

            boolean previousIsMedium =
                    previousStack.getItem()
                            instanceof traincraft.item.track.LegacyMediumCurveTrackItem;
            traincraft.block.track.LegacyBatchTrackSpec previousBatchSpec =
                    blueprintBatchSpecFromStack(
                            previousStack);

            boolean previousIsMedium45Switch =
                    blueprintIsMedium45Switch(
                            previousBatchSpec);

            java.util.Set<BlockPos> tcMediumRecordedCells =
                    previousIsMedium45Switch
                            ? recordedPlacementCells(
                                    blueprint)
                            : java.util.Set.of();

            int previousExitPart = -1;

            if (previousIsMedium) {
                if (previous.contains(ROUTE_EXIT_PART)) {
                    previousExitPart =
                            previous.getInt(
                                    ROUTE_EXIT_PART);
                } else {
                    previousExitPart =
                            segmentIsLeft(previous)
                                    ? 0
                                    : last;
                }
            }

            net.minecraft.core.Direction playerFacing =
                    net.minecraft.core.Direction.fromYRot(
                            playerYaw);

            for (net.minecraft.core.Direction candidateFacing
                    : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                for (int attachedPart
                        : new int[]{0, last}) {
                    net.minecraft.core.Direction attachedOutward =
                            mediumEndpointOutward(
                                    candidateFacing,
                                    attachedPart);

                    boolean tcFaceToFace =
                            attachedOutward
                                    == source.outward.getOpposite();

                    // r3zzj: Medium Curve has cardinal endpoint tangents.
                    // The old world-placement 8-way fix applies to the 45 /
                    // diagonal families, not by pretending Medium is diagonal.
                    if (!tcFaceToFace) {
                        continue;
                    }

                    BlockPos root =
                            required.subtract(
                                    traincraft.block.track.LegacyMediumCurveRailBlock
                                            .offsetForPart(
                                                    BlockPos.ZERO,
                                                    candidateFacing,
                                                    attachedPart));

                    int rolePenalty = 0;

                    if (previousIsMedium
                            && previousExitPart >= 0) {
                        int preferredAttached =
                                previousExitPart == 0
                                        ? last
                                        : 0;

                        rolePenalty =
                                attachedPart == preferredAttached
                                        ? 0
                                        : 100;
                    }

                    int facingPenalty;

                    if (candidateFacing == playerFacing) {
                        facingPenalty = 0;
                    } else if (candidateFacing.getOpposite()
                            == playerFacing) {
                        facingPenalty = 2;
                    } else {
                        facingPenalty = 1;
                    }

                    candidates.add(
                            new TcMediumCandidate(
                                    root,
                                    candidateFacing,
                                    attachedPart,
                                    previousIsMedium45Switch
                                            ? (
                                                    tcDeepOverlapCount(
                                                                    placementCells(
                                                                            selected,
                                                                            root,
                                                                            candidateFacing.toYRot()),
                                                                    tcMediumRecordedCells)
                                                            * 10000
                                                    + tcDeepNearestCellDistance(
                                                                    requested,
                                                                    placementCells(
                                                                            selected,
                                                                            root,
                                                                            candidateFacing.toYRot()))
                                                            * 100
                                                    + 0 /* TC_BATCH8_R3ZZJ_MEDIUM_CARDINAL_AIM */
                                                    + facingPenalty)
                                            : rolePenalty
                                                    + facingPenalty));
                }
            }

            if (candidates.isEmpty()) {
                return freePlacement(
                        blueprint,
                        selected,
                        requested,
                        playerYaw);
            }

            candidates.sort(
                    java.util.Comparator
                            .comparingInt(
                                    TcMediumCandidate::score)
                            .thenComparingInt(
                                    TcMediumCandidate::attachedPart)
                            .thenComparingInt(
                                    candidate ->
                                            candidate.facing()
                                                    .get2DDataValue()));

            boolean alternateHand =
                    selectedDirectionLeft(
                            blueprint);

                        // TC_BATCH8_R3ZZR1_MEDIUM45_DETERMINISTIC_HAND
            //
            // The Medium45 source has two legal face-to-face Medium Curve
            // continuations at the same physical connector. r3zzq3 proved
            // that the old code ignored the Blueprint LEFT/RIGHT state and
            // always took candidates.get(0), so crosshair/player-facing score
            // could silently choose either bend.
            //
            // Preserve the Medium Curve's existing hand contract:
            //   LEFT  => outgoing endpoint is p0, therefore attach pLast.
            //   RIGHT => outgoing endpoint is pLast, therefore attach p0.
            //
            // This mirrors freePlacement() and keeps the physical connector
            // from r3zzp3 exactly unchanged.
            TcMediumCandidate chosen;

            if (previousIsMedium45Switch) {
                int desiredAttachedPart =
                        alternateHand
                                ? last
                                : 0;

                chosen =
                        candidates.stream()
                                .filter(candidate ->
                                        candidate.attachedPart()
                                                == desiredAttachedPart)
                                .findFirst()
                                .orElse(candidates.get(0));
            } else {
                chosen =
                        alternateHand
                                        && candidates.size() > 1
                                ? candidates.get(1)
                                : candidates.get(0);
            }

            int outgoingPart =
                    chosen.attachedPart() == 0
                            ? last
                            : 0;

            BlockPos exit =
                    traincraft.block.track.LegacyMediumCurveRailBlock
                            .offsetForPart(
                                    chosen.root(),
                                    chosen.facing(),
                                    outgoingPart);

            return new BlueprintPlacement(
                    chosen.root(),
                    chosen.facing().toYRot(),
                    exit,
                    mediumEndpointOutward(
                            chosen.facing(),
                            outgoingPart));
        }

        // r3zzc: mirror the accepted r3zq LegacyBatchTrackItem connector
        // candidate contract for all remaining multipart pieces. This is the
        // missing path that previously left 45-degree pieces, large curves,
        // switches, crossings and curved slopes with resultExit=null.
        ItemStack resolved =
                blueprintResolvedBatchStack(
                        selected);

        if (resolved.getItem()
                instanceof traincraft.item.track.LegacyBatchTrackItem batch) {
            traincraft.block.track.LegacyBatchTrackSpec spec =
                    blueprintBatchSpec(batch);

            if (spec != null
                    && !spec.endpoints().isEmpty()) {
                record TcBatchCandidate(
                        BlockPos root,
                        BlockPos anchor,
                        net.minecraft.core.Direction facing,
                        traincraft.block.track.LegacyBatchTrackSpec.Endpoint attached,
                        int score) {}

                java.util.ArrayList<TcBatchCandidate> candidates =
                        new java.util.ArrayList<>();

                net.minecraft.core.Direction requiredNewOutward =
                        source.outward.getOpposite();

                net.minecraft.core.Direction playerFacing =
                        net.minecraft.core.Direction.fromYRot(
                                playerYaw);

                ItemStack previousStack =
                        segmentTrackStack(
                                previous);

                String previousSpecId =
                        blueprintBatchSpecId(
                                previousStack);

                boolean diagonalFamily =
                        blueprintDiagonalConnectorFamily(
                                spec.id())
                                || blueprintDiagonalConnectorFamily(
                                        previousSpecId);

                BlueprintRouteHeading8 sourceRouteHeading =
                        blueprintRouteHeadingForSource(
                                segments,
                                segments.size() - 1,
                                origin,
                                source);
                // TC_BATCH8_R4A5D_DIAMOND_DIAGONAL_PROBE
                //
                // A Diamond Crossing is itself a true diagonal route family.
                // When the selected source outlet is diagonal, solve the
                // crossing endpoint against the diagonal rail step, not the
                // source endpoint's cardinal proxy/guide cell.
                //
                // IMPORTANT: this deliberately does NOT apply to Medium Curve.
                // r3zzp3 proved Medium Curve must keep the cardinal physical
                // connector even when the source tangent metadata is diagonal.
                BlockPos tcBatchRequired =
                        required;

                        // TC_BATCH8_R4A5E_ALL_DIAGONAL_CROSSING_PROBE
        boolean tcDiagonalCrossingProbe =
                blueprintIsDiagonalCrossing8WayFamily(
                        spec.id())
                        && sourceRouteHeading != null
                        && sourceRouteHeading.isDiagonal();
                // TC_BATCH8_R4A5F_DIAGONAL_STRAIGHT_MEDIUM45_PROBE
                boolean tcDiagonalStraightMedium45Probe =
                        blueprintIsDiagonalStraight8WayFamily(spec.id())
                                && ("track_switch_45_medium_left".equals(previousSpecId)
                                        || "track_switch_45_medium_right".equals(previousSpecId))
                                && sourceRouteHeading != null
                                && sourceRouteHeading.isDiagonal();

                boolean tcTrueDiagonalProbe =
                        tcDiagonalCrossingProbe || tcDiagonalStraightMedium45Probe;

                if (tcDiagonalStraightMedium45Probe) {
                    traincraft.Traincraft.LOGGER.info(
                            "[TC-BP-DIAGONAL-STRAIGHT-PROBE] source={} sourceOut={} sourceHeading={} cardinalRequired={} diagonalRequired={} selectedSpec={} previousSpec={}",
                            source.segment, source.outward, sourceRouteHeading, required,
                            source.segment.offset(sourceRouteHeading.dx, 0, sourceRouteHeading.dz),
                            spec.id(), previousSpecId);
                }

        if (tcTrueDiagonalProbe) {
                    tcBatchRequired =
                            source.segment.offset(
                                    sourceRouteHeading.dx,
                                    0,
                                    sourceRouteHeading.dz);

                    traincraft.Traincraft.LOGGER.info(
                            "[TC-BP-CROSSING-PROBE] source={} sourceOut={} sourceHeading={} cardinalRequired={} diagonalRequired={} selectedSpec={} requested={}",
                            source.segment,
                            source.outward,
                            sourceRouteHeading,
                            required,
                            tcBatchRequired,
                            spec.id(),
                            requested);
                }

                boolean exactEightWayPair =
                        sourceRouteHeading != null
                                && blueprintIsEightWaySnapFamily(
                                        spec.id())
                                && blueprintIsEightWaySnapFamily(
                                        previousSpecId);
                boolean tcSelectedAlwaysDiagonal =
                        blueprintIsDiagonalStraight8WayFamily(
                                spec.id())
                                || blueprintIsDiagonalCrossing8WayFamily(
                                        spec.id());

                boolean tcStrictTrueTangent =
                        sourceRouteHeading != null
                                && (exactEightWayPair
                                        || sourceRouteHeading.isDiagonal()
                                        || tcSelectedAlwaysDiagonal);

                for (net.minecraft.core.Direction candidateFacing
                        : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    for (traincraft.block.track.LegacyBatchTrackSpec.Endpoint endpoint
                            : spec.endpoints()) {
                        net.minecraft.core.Direction newOutward =
                                spec.rotateDirection(
                                        candidateFacing,
                                        endpoint.outward());

                        boolean faceToFace =
                                newOutward
                                        == requiredNewOutward;

                        boolean cornerHandoff =
                                !tcStrictTrueTangent
                                        && diagonalFamily
                                        && newOutward.getAxis()
                                                != source.outward.getAxis();

                        BlockPos endpointOffset =
                                spec.endpointPosition(
                                        BlockPos.ZERO,
                                        candidateFacing,
                                        endpoint);

                        BlockPos root =
                                tcBatchRequired.subtract(
                                        endpointOffset);

                        BlockPos placedEndpoint =
                                spec.endpointPosition(
                                        root,
                                        candidateFacing,
                                        endpoint);

                        if (!placedEndpoint.equals(
                                tcBatchRequired)) {
                            continue;
                        }

                        BlueprintRouteHeading8 candidateRouteHeading =
                                blueprintRouteHeadingForEndpoint(
                                        spec,
                                        root,
                                        candidateFacing,
                                        endpoint);

                        boolean tangentFaceToFace =
                                tcStrictTrueTangent
                                        && candidateRouteHeading
                                                == sourceRouteHeading.opposite();

                        boolean relationAllowed =
                                tcStrictTrueTangent
                                        ? tangentFaceToFace
                                        : (faceToFace
                                                || cornerHandoff);

                        if (!relationAllowed) {
                            continue;
                        }

                        BlockPos anchor =
                                blueprintBatchAnchorFromRoot(
                                        spec,
                                        root,
                                        candidateFacing);

                        int connectorPenalty =
                                exactEightWayPair
                                                || faceToFace
                                        ? 0
                                        : 10;

                        int score =
                                connectorPenalty
                                        + blueprintFacingPenalty(
                                                candidateFacing,
                                                playerFacing);

                        candidates.add(
                                new TcBatchCandidate(
                                        root,
                                        anchor,
                                        candidateFacing,
                                        endpoint,
                                        score));
                    }
                }
                                if (tcStrictTrueTangent
                        && candidates.isEmpty()) {
                    String tcReason =
                            tcSelectedAlwaysDiagonal
                                            && !sourceRouteHeading.isDiagonal()
                                    ? "CARDINAL_SOURCE_TO_DIAGONAL_FAMILY"
                                    : "NO_TRUE_TANGENT_MATCH";

                    traincraft.Traincraft.LOGGER.info(
                            "[TC-BP-DIAGONAL-GUARD] previous={} selected={} source={} sourceOut={} sourceHeading={} selectedSpec={} requested={} reason={}",
                            tcDeepItemId(previousStack),
                            tcDeepItemId(selected),
                            source.segment,
                            source.outward,
                            sourceRouteHeading,
                            spec.id(),
                            requested,
                            tcReason);

                    return null;
                }
if (!candidates.isEmpty()) {
                    candidates.sort(
                            java.util.Comparator
                                    .comparingInt(
                                            TcBatchCandidate::score)
                                    .thenComparingInt(
                                            candidate ->
                                                    candidate.attached()
                                                            .part())
                                    .thenComparingInt(
                                            candidate ->
                                                    candidate.facing()
                                                            .get2DDataValue()));

                                        // TC_BATCH8_R3ZZV2C_29X32X_DETERMINISTIC_HAND
                    //
                    // 29x / 32x are single-ID 90-degree curves. Reorder the
                    // legal candidates before 'chosen' is declared so the
                    // Blueprint hand is deterministic and compile-safe.
                    if (blueprintIsSingleIdQuarterCurve(
                                    selected)
                            && source.outward != null
                            && source.outward.getAxis()
                                    != net.minecraft.core.Direction.Axis.Y) {
                        boolean selectedLeft =
                                selectedDirectionLeft(
                                        blueprint);

                        net.minecraft.core.Direction desiredExitOutward =
                                selectedLeft
                                        ? source.outward.getCounterClockWise()
                                        : source.outward.getClockWise();

                        int handCandidateIndex =
                                -1;

                        for (int candidateIndex = 0;
                             candidateIndex < candidates.size();
                             candidateIndex++) {
                            TcBatchCandidate candidate =
                                    candidates.get(
                                            candidateIndex);

                            traincraft.block.track.LegacyBatchTrackSpec.Endpoint
                                    candidateOutgoing =
                                            blueprintBatchOutgoingEndpoint(
                                                    spec,
                                                    candidate.attached(),
                                                    candidate.root(),
                                                    candidate.facing(),
                                                    source.outward,
                                                    playerFacing);

                            if (candidateOutgoing == null) {
                                continue;
                            }

                            net.minecraft.core.Direction candidateExitOutward =
                                    spec.rotateDirection(
                                            candidate.facing(),
                                            candidateOutgoing.outward());

                            if (candidateExitOutward
                                    == desiredExitOutward) {
                                handCandidateIndex =
                                        candidateIndex;
                                break;
                            }
                        }

                        if (handCandidateIndex > 0) {
                            TcBatchCandidate handCandidate =
                                    candidates.remove(
                                            handCandidateIndex);

                            candidates.add(
                                    0,
                                    handCandidate);
                        }
                    }
TcBatchCandidate chosen =
                            candidates.get(0);

                    traincraft.block.track.LegacyBatchTrackSpec.Endpoint outgoing =
                            blueprintBatchOutgoingEndpoint(
                                    spec,
                                    chosen.attached(),
                                    chosen.root(),
                                    chosen.facing(),
                                    source.outward,
                                    playerFacing);

                    BlockPos exit =
                            outgoing == null
                                    ? null
                                    : spec.endpointPosition(
                                            chosen.root(),
                                            chosen.facing(),
                                            outgoing);

                    net.minecraft.core.Direction exitOutward =
                            outgoing == null
                                    ? null
                                    : spec.rotateDirection(
                                            chosen.facing(),
                                            outgoing.outward());

                    return new BlueprintPlacement(
                            chosen.anchor(),
                            chosen.facing().toYRot(),
                            exit,
                            exitOutward);
                }
            }
        }

        return freePlacement(
                blueprint,
                selected,
                requested,
                playerYaw);
    }

    private static void storeRouteExit(
            CompoundTag entry,
            BlueprintPlacement placement,
            BlockPos origin) {
        if (placement == null
                || placement.exitSegment == null
                || placement.exitOutward == null) {
            return;
        }

        entry.putInt(
                ROUTE_EXIT_X,
                placement.exitSegment.getX()
                        - origin.getX());
        entry.putInt(
                ROUTE_EXIT_Y,
                placement.exitSegment.getY()
                        - origin.getY());
        entry.putInt(
                ROUTE_EXIT_Z,
                placement.exitSegment.getZ()
                        - origin.getZ());
        entry.putInt(
                ROUTE_EXIT_DIRECTION,
                placement.exitOutward
                        .get3DDataValue());

        ItemStack routeStack =
                segmentTrackStack(entry);

        if (routeStack.getItem()
                instanceof traincraft.item.track.LegacyMediumCurveTrackItem) {
            net.minecraft.core.Direction facing =
                    net.minecraft.core.Direction.fromYRot(
                            placement.yaw);

            int last =
                    traincraft.block.track.LegacyMediumCurveRailBlock
                            .PART_COUNT
                            - 1;

            BlockPos p0 =
                    traincraft.block.track.LegacyMediumCurveRailBlock
                            .offsetForPart(
                                    placement.anchor,
                                    facing,
                                    0);

            BlockPos pLast =
                    traincraft.block.track.LegacyMediumCurveRailBlock
                            .offsetForPart(
                                    placement.anchor,
                                    facing,
                                    last);

            if (placement.exitSegment.equals(p0)) {
                entry.putInt(
                        ROUTE_EXIT_PART,
                        0);
            } else if (placement.exitSegment.equals(pLast)) {
                entry.putInt(
                        ROUTE_EXIT_PART,
                        last);
            }
        }

        traincraft.Traincraft.LOGGER.info(
                "[TC-R3ZZ-BP-ROUTE] item={} anchor={} yaw={} exitSegment={} exitOut={} exitPart={} origin={}",
                entry.getString("Item"),
                placement.anchor,
                placement.yaw,
                placement.exitSegment,
                placement.exitOutward,
                entry.contains(ROUTE_EXIT_PART)
                        ? entry.getInt(ROUTE_EXIT_PART)
                        : -1,
                origin);
    }
    // TC_BATCH8_R3ZZB_BLUEPRINT_OVERLAP_GUARD
    // Physical-cell overlap is rejected. Endpoint adjacency is still legal
    // because connected pieces occupy neighboring cells rather than sharing
    // the same physical rail cell.
    public record OverlapResult(
            boolean blocked,
            BlockPos cell,
            String source) {
        public static OverlapResult clear() {
            return new OverlapResult(
                    false,
                    null,
                    "");
        }
    }

    private static traincraft.block.track.LegacyBatchTrackSpec
            blueprintBatchSpec(
                    traincraft.item.track.LegacyBatchTrackItem item) {
        try {
            java.lang.reflect.Field field =
                    traincraft.item.track.LegacyBatchTrackItem.class
                            .getDeclaredField("spec");
            field.setAccessible(true);
            return (traincraft.block.track.LegacyBatchTrackSpec)
                    field.get(item);
        } catch (ReflectiveOperationException ex) {
            traincraft.Traincraft.LOGGER.warn(
                    "[TC-R3ZZB-BP-OVERLAP] Could not read batch spec for {}",
                    net.minecraftforge.registries.ForgeRegistries.ITEMS
                            .getKey(item),
                    ex);
            return null;
        }
    }

    // TC_BATCH8_R3ZZC_BATCH_ENDPOINT_PARITY
    // Blueprint batch-piece planning now consumes the exact accepted
    // LegacyBatchTrackSpec endpoint contract instead of treating large/45/
    // switch/crossing/slope families as free crosshair placement.

    private static ItemStack blueprintResolvedBatchStack(
            ItemStack stack) {
        if (stack == null
                || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack resolved =
                previewPlacementStack(stack);

        return resolved.isEmpty()
                ? stack
                : resolved;
    }

    private static traincraft.block.track.LegacyBatchTrackSpec
            blueprintBatchSpecFromStack(
                    ItemStack stack) {
        ItemStack resolved =
                blueprintResolvedBatchStack(stack);

        if (resolved.getItem()
                instanceof traincraft.item.track.LegacyBatchTrackItem batch) {
            return blueprintBatchSpec(batch);
        }

        return null;
    }

    private static BlockPos blueprintBatchRootFromAnchor(
            traincraft.block.track.LegacyBatchTrackSpec spec,
            BlockPos anchor,
            net.minecraft.core.Direction facing) {
        if ("track_diagonal_crossing".equals(spec.id())) {
            return anchor;
        }

        int primaryPart =
                spec.endpoints()
                        .get(0)
                        .part();

        return anchor.subtract(
                spec.offsetForPart(
                        BlockPos.ZERO,
                        facing,
                        primaryPart));
    }

    private static BlockPos blueprintBatchAnchorFromRoot(
            traincraft.block.track.LegacyBatchTrackSpec spec,
            BlockPos root,
            net.minecraft.core.Direction facing) {
        if ("track_diagonal_crossing".equals(spec.id())) {
            return root;
        }

        int primaryPart =
                spec.endpoints()
                        .get(0)
                        .part();

        return spec.offsetForPart(
                root,
                facing,
                primaryPart);
    }

    private static boolean blueprintDiagonalConnectorFamily(
            String id) {
        if (id == null) {
            return false;
        }

        return id.contains("diagonal")
                || id.contains("diamond")
                || id.contains("universal")
                || id.contains("_45_")
                || id.startsWith("track_curve_45");
    }

    private static int blueprintFacingPenalty(
            net.minecraft.core.Direction candidate,
            net.minecraft.core.Direction playerFacing) {
        if (candidate == playerFacing) {
            return 0;
        }

        if (candidate.getOpposite()
                == playerFacing) {
            return 2;
        }

        return 1;
    }

    private static String blueprintBatchSpecId(
            ItemStack stack) {
        traincraft.block.track.LegacyBatchTrackSpec spec =
                blueprintBatchSpecFromStack(
                        stack);

        return spec == null
                ? null
                : spec.id();
    }

    private static traincraft.block.track.LegacyBatchTrackSpec.Endpoint
            blueprintBatchOutgoingEndpoint(
                    traincraft.block.track.LegacyBatchTrackSpec spec,
                    traincraft.block.track.LegacyBatchTrackSpec.Endpoint attached,
                    BlockPos root,
                    net.minecraft.core.Direction facing,
                    net.minecraft.core.Direction routeOutward,
                    net.minecraft.core.Direction playerFacing) {
        traincraft.block.track.LegacyBatchTrackSpec.Endpoint best =
                null;

        int bestScore =
                Integer.MAX_VALUE;

        int bestIndex =
                Integer.MAX_VALUE;

        java.util.List<traincraft.block.track.LegacyBatchTrackSpec.Endpoint>
                endpoints =
                        spec.endpoints();

        boolean exactCrossingRoute =
                blueprintIsDiagonalCrossing8WayFamily(
                        spec.id());

        BlueprintRouteHeading8 attachedHeading =
                exactCrossingRoute
                        ? blueprintRouteHeadingForEndpoint(
                                spec,
                                root,
                                facing,
                                attached)
                        : null;

        for (int i = 0;
             i < endpoints.size();
             i++) {
            traincraft.block.track.LegacyBatchTrackSpec.Endpoint endpoint =
                    endpoints.get(i);

            if (endpoint == attached) {
                continue;
            }

            net.minecraft.core.Direction outward =
                    spec.rotateDirection(
                            facing,
                            endpoint.outward());

            int routePenalty;

            if (exactCrossingRoute
                    && attachedHeading != null) {
                BlueprintRouteHeading8 candidateHeading =
                        blueprintRouteHeadingForEndpoint(
                                spec,
                                root,
                                facing,
                                endpoint);

                routePenalty =
                        candidateHeading
                                        == attachedHeading.opposite()
                                ? 0
                                : 20;
            } else if (routeOutward != null
                    && outward == routeOutward) {
                routePenalty =
                        0;
            } else if (routeOutward != null
                    && outward.getAxis()
                            == routeOutward.getAxis()) {
                routePenalty =
                        2;
            } else {
                routePenalty =
                        4;
            }

            int score =
                    routePenalty * 100
                            + blueprintFacingPenalty(
                                    outward,
                                    playerFacing);

            if (score < bestScore
                    || (score == bestScore
                            && i < bestIndex)) {
                best =
                        endpoint;
                bestScore =
                        score;
                bestIndex =
                        i;
            }
        }

        return best;
    }

    // TC_BATCH8_R3ZZW2_QUARTER_FIRST_FORWARD
    //
    // A 29x/32x used as segment #1 has no previous rail to define travel.
    // Treat the requested cell as though a straight approaches it from behind
    // the player.  Evaluate both physical curve ends and keep the candidate
    // whose footprint progresses forward while honoring the explicit hand.
    private static BlueprintPlacement blueprintQuarterCurveForwardStart(
            ItemStack blueprint,
            ItemStack selected,
            BlockPos requested,
            float playerYaw) {
        if (!blueprintIsSingleIdQuarterCurve(
                        selected)) {
            return null;
        }

        traincraft.block.track.LegacyBatchTrackSpec spec =
                blueprintBatchSpecFromStack(
                        selected);

        if (spec == null
                || spec.endpoints().size() < 2) {
            return null;
        }

        net.minecraft.core.Direction travel =
                net.minecraft.core.Direction.fromYRot(
                        playerYaw);

        net.minecraft.core.Direction right =
                travel.getClockWise();

        boolean selectedLeft =
                selectedDirectionLeft(
                        blueprint);

        BlockPos bestRoot =
                null;
        net.minecraft.core.Direction bestFacing =
                null;
        traincraft.block.track.LegacyBatchTrackSpec.Endpoint bestOutgoing =
                null;
        int bestScore =
                Integer.MAX_VALUE;

        for (net.minecraft.core.Direction candidateFacing
                : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            for (traincraft.block.track.LegacyBatchTrackSpec.Endpoint attached
                    : spec.endpoints()) {
                net.minecraft.core.Direction attachedOutward =
                        spec.rotateDirection(
                                candidateFacing,
                                attached.outward());

                if (attachedOutward
                        != travel.getOpposite()) {
                    continue;
                }

                BlockPos root =
                        requested.subtract(
                                spec.endpointPosition(
                                        BlockPos.ZERO,
                                        candidateFacing,
                                        attached));

                for (traincraft.block.track.LegacyBatchTrackSpec.Endpoint outgoing
                        : spec.endpoints()) {
                    if (outgoing == attached) {
                        continue;
                    }

                    BlockPos exit =
                            spec.endpointPosition(
                                    root,
                                    candidateFacing,
                                    outgoing);

                    int dx =
                            exit.getX()
                                    - requested.getX();
                    int dz =
                            exit.getZ()
                                    - requested.getZ();

                    int forwardProgress =
                            dx * travel.getStepX()
                                    + dz * travel.getStepZ();

                    int lateralProgress =
                            dx * right.getStepX()
                                    + dz * right.getStepZ();

                    boolean handMatches =
                            selectedLeft
                                    ? lateralProgress < 0
                                    : lateralProgress > 0;

                    int score =
                            (forwardProgress < 0
                                    ? 10000
                                    : 0)
                                    + (forwardProgress == 0
                                            ? 100
                                            : 0)
                                    + (handMatches
                                            ? 0
                                            : 2000)
                                    - forwardProgress * 100
                                    + blueprintFacingPenalty(
                                            candidateFacing,
                                            travel);

                    if (score < bestScore) {
                        bestScore =
                                score;
                        bestRoot =
                                root;
                        bestFacing =
                                candidateFacing;
                        bestOutgoing =
                                outgoing;
                    }
                }
            }
        }

        if (bestRoot == null
                || bestFacing == null
                || bestOutgoing == null) {
            return null;
        }

        BlockPos anchor =
                blueprintBatchAnchorFromRoot(
                        spec,
                        bestRoot,
                        bestFacing);

        BlockPos exit =
                spec.endpointPosition(
                        bestRoot,
                        bestFacing,
                        bestOutgoing);

        net.minecraft.core.Direction exitOutward =
                spec.rotateDirection(
                        bestFacing,
                        bestOutgoing.outward());

        return new BlueprintPlacement(
                anchor,
                bestFacing.toYRot(),
                exit,
                exitOutward);
    }
    private static BlueprintPlacement blueprintBatchFreePlacement(
            ItemStack selected,
            BlockPos requested,
            float playerYaw) {
        ItemStack resolved =
                blueprintResolvedBatchStack(
                        selected);

        if (!(resolved.getItem()
                instanceof traincraft.item.track.LegacyBatchTrackItem batch)) {
            return null;
        }

        traincraft.block.track.LegacyBatchTrackSpec spec =
                blueprintBatchSpec(batch);

        if (spec == null
                || spec.endpoints().isEmpty()) {
            return null;
        }

        net.minecraft.core.Direction facing =
                net.minecraft.core.Direction.fromYRot(
                        playerYaw);

        BlockPos root =
                blueprintBatchRootFromAnchor(
                        spec,
                        requested,
                        facing);

        traincraft.block.track.LegacyBatchTrackSpec.Endpoint attached =
                spec.endpoints().get(0);

        traincraft.block.track.LegacyBatchTrackSpec.Endpoint outgoing =
                blueprintBatchOutgoingEndpoint(
                        spec,
                        attached,
                        root,
                        facing,
                        facing,
                        facing);

        BlockPos exit =
                outgoing == null
                        ? null
                        : spec.endpointPosition(
                                root,
                                facing,
                                outgoing);

        net.minecraft.core.Direction exitOutward =
                outgoing == null
                        ? null
                        : spec.rotateDirection(
                                facing,
                                outgoing.outward());

        return new BlueprintPlacement(
                requested,
                facing.toYRot(),
                exit,
                exitOutward);
    }
    public static java.util.List<BlockPos> placementCells(
            ItemStack stack,
            BlockPos anchor,
            float yaw) {
        if (stack == null
                || stack.isEmpty()
                || anchor == null) {
            return java.util.List.of();
        }

        ItemStack resolved =
                previewPlacementStack(stack);

        if (resolved.isEmpty()) {
            resolved = stack;
        }

        net.minecraft.core.Direction facing =
                net.minecraft.core.Direction.fromYRot(yaw);

        int length =
                straightLength(resolved);

        if (length > 0) {
            java.util.ArrayList<BlockPos> cells =
                    new java.util.ArrayList<>(length);

            for (int part = 0;
                 part < length;
                 part++) {
                cells.add(
                        anchor.relative(
                                facing,
                                part));
            }

            return java.util.List.copyOf(cells);
        }

        if (resolved.getItem()
                instanceof traincraft.item.track.LegacyMediumCurveTrackItem) {
            java.util.ArrayList<BlockPos> cells =
                    new java.util.ArrayList<>(
                            traincraft.block.track.LegacyMediumCurveRailBlock
                                    .PART_COUNT);

            for (int part = 0;
                 part < traincraft.block.track.LegacyMediumCurveRailBlock
                         .PART_COUNT;
                 part++) {
                cells.add(
                        traincraft.block.track.LegacyMediumCurveRailBlock
                                .offsetForPart(
                                        anchor,
                                        facing,
                                        part));
            }

            return java.util.List.copyOf(cells);
        }

        if (resolved.getItem()
                instanceof traincraft.item.track.LegacyBatchTrackItem batch) {
            traincraft.block.track.LegacyBatchTrackSpec spec =
                    blueprintBatchSpec(batch);

            if (spec != null) {
                BlockPos root;

                if ("track_diagonal_crossing".equals(spec.id())) {
                    root = anchor;
                } else {
                    int primaryPart =
                            spec.endpoints()
                                    .get(0)
                                    .part();

                    root =
                            anchor.subtract(
                                    spec.offsetForPart(
                                            BlockPos.ZERO,
                                            facing,
                                            primaryPart));
                }

                java.util.ArrayList<BlockPos> cells =
                        new java.util.ArrayList<>(
                                spec.partCount());

                for (int part = 0;
                     part < spec.partCount();
                     part++) {
                    cells.add(
                            spec.offsetForPart(
                                    root,
                                    facing,
                                    part));
                }

                return java.util.List.copyOf(cells);
            }
        }

        // Conservative fallback for any Traincraft track item not represented
        // by one of the dedicated placement families above.
        return java.util.List.of(anchor);
    }

    private static boolean worldTrackAt(
            Level level,
            BlockPos pos) {
        if (level == null
                || pos == null) {
            return false;
        }

        net.minecraft.world.level.block.state.BlockState state =
                level.getBlockState(pos);

        if (state.getBlock()
                instanceof net.minecraft.world.level.block.BaseRailBlock) {
            return true;
        }

        ResourceLocation key =
                net.minecraftforge.registries.ForgeRegistries.BLOCKS
                        .getKey(state.getBlock());

        return key != null
                && "traincraft".equals(key.getNamespace())
                && key.getPath().startsWith("track_");
    }

    private static java.util.Set<BlockPos> recordedPlacementCells(
            ItemStack blueprint) {
        java.util.LinkedHashSet<BlockPos> occupied =
                new java.util.LinkedHashSet<>();

        CompoundTag data =
                existingData(blueprint);

        if (data == null
                || !data.contains(ORIGIN)) {
            return occupied;
        }

        BlockPos origin =
                BlockPos.of(
                        data.getLong(ORIGIN));

        ListTag segments =
                data.getList(
                        SEGMENTS,
                        CompoundTag.TAG_COMPOUND);

        for (int i = 0;
             i < segments.size();
             i++) {
            CompoundTag segment =
                    segments.getCompound(i);

            BlockPos anchor =
                    origin.offset(
                            segment.getInt("X"),
                            segment.getInt("Y"),
                            segment.getInt("Z"));

            ItemStack stack =
                    segmentTrackStack(segment);

            occupied.addAll(
                    placementCells(
                            stack,
                            anchor,
                            segment.getFloat("Yaw")));
        }

        return occupied;
    }

    public static OverlapResult placementOverlap(
            Level level,
            ItemStack blueprint,
            ItemStack selected,
            BlockPos anchor,
            float yaw) {
        java.util.List<BlockPos> candidate =
                placementCells(
                        selected,
                        anchor,
                        yaw);

        if (candidate.isEmpty()) {
            return OverlapResult.clear();
        }

        java.util.Set<BlockPos> recorded =
                recordedPlacementCells(blueprint);

        for (BlockPos pos : candidate) {
            if (recorded.contains(pos)) {
                return new OverlapResult(
                        true,
                        pos,
                        "BLUEPRINT");
            }

            if (worldTrackAt(level, pos)) {
                return new OverlapResult(
                        true,
                        pos,
                        "WORLD");
            }
        }

        return OverlapResult.clear();
    }
    public static boolean isTrackStack(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (stack.is(Items.RAIL)
                || stack.is(Items.POWERED_RAIL)
                || stack.is(Items.DETECTOR_RAIL)
                || stack.is(Items.ACTIVATOR_RAIL)) {
            return true;
        }

        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null || !"traincraft".equals(key.getNamespace())) {
            return false;
        }

        String path = key.getPath();
        return path.startsWith("track_")
                && !"track_blueprint".equals(path)
                && !path.endsWith("_segment");
    }

    private static List<Item> trackChoices() {
        List<Item> result = new ArrayList<>();

        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            ItemStack stack = new ItemStack(item);
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);

            // Blueprint 2.0 mirrors the Creative tab:
            // hidden LEFT registry aliases remain registered for compatibility,
            // but only the neutral/canonical family item is selectable.
            if (isTrackStack(stack)
                    && (key == null
                            || !isLeftTrackId(key.toString()))) {
                result.add(item);
            }
        }

        result.sort(Comparator.comparing(item -> {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key == null) return "zzzz:unknown";

            if ("traincraft".equals(key.getNamespace())
                    && "track_normal".equals(key.getPath())) {
                return "0000";
            }

            return ("traincraft".equals(key.getNamespace())
                            ? "1000:"
                            : "9000:")
                    + key;
        }));

        return result;
    }

    @Nullable
    private static Item selectedTrack(ItemStack blueprint) {
        List<Item> choices = trackChoices();
        if (choices.isEmpty()) return null;

        CompoundTag data = existingData(blueprint);

        if (data != null && data.contains(SELECTED_TRACK)) {
            String canonical =
                    canonicalTrackId(
                            data.getString(SELECTED_TRACK));

            ResourceLocation id =
                    ResourceLocation.tryParse(canonical);

            if (id != null) {
                Item selected = ForgeRegistries.ITEMS.getValue(id);

                if (selected != null
                        && isTrackStack(new ItemStack(selected))) {
                    return selected;
                }
            }
        }

        return choices.get(0);
    }

    public static ItemStack selectedTrackStack(ItemStack blueprint) {
        Item selected = selectedTrack(blueprint);

        if (selected == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(selected);
        applyUnifiedDirection(
                stack,
                selectedDirectionLeft(blueprint));
        return stack;
    }

    // TC_BATCH8_R3P_BLUEPRINT_SCROLL_FAMILY_NAV
    // TC_BATCH8_R3ZZY_BLUEPRINT_FAMILY_COLORS
    public static String familyFor(Item item) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        if (key == null) return "Other";

        if ("minecraft".equals(key.getNamespace())) {
            return "Vanilla";
        }

        String path = key.getPath();

        if (path.contains("slope")) {
            return "Slopes";
        }
        if (path.contains("switch")) {
            return "Switches";
        }
        if (path.contains("crossing")
                || path.contains("diamond")
                || path.contains("parallel")) {
            return "Crossings";
        }
        if (path.contains("diagonal")) {
            return "Diagonal";
        }
        if (path.contains("curve") || path.contains("turn")) {
            return "Curves";
        }
        if (path.contains("straight")
                || "track_normal".equals(path)) {
            return "Straight";
        }

        return "Other";
    }

    public static ChatFormatting familyColor(
            String family) {
        return switch (family) {
            case "Straight" -> ChatFormatting.GREEN;
            case "Curves" -> ChatFormatting.GOLD;
            case "Slopes" -> ChatFormatting.LIGHT_PURPLE;
            case "Switches" -> ChatFormatting.YELLOW;
            case "Diagonal" -> ChatFormatting.AQUA;
            case "Crossings" -> ChatFormatting.RED;
            case "Vanilla" -> ChatFormatting.BLUE;
            default -> ChatFormatting.GRAY;
        };
    }
    private static List<String> availableFamilies(List<Item> choices) {
        List<String> result = new ArrayList<>();
        List<String> order = List.of(
                "Straight",
                "Curves",
                "Slopes",
                "Switches",
                "Diagonal",
                "Crossings",
                "Vanilla",
                "Other");

        for (String family : order) {
            for (Item item : choices) {
                if (family.equals(familyFor(item))) {
                    result.add(family);
                    break;
                }
            }
        }

        return result;
    }

    private static List<Item> familyTracks(List<Item> choices, String family) {
        List<Item> result = new ArrayList<>();

        for (Item item : choices) {
            if (family.equals(familyFor(item))) {
                result.add(item);
            }
        }

        return result;
    }

    public static void cycleSelection(
            ItemStack blueprint,
            Player player,
            int direction,
            boolean familyOnly) {
        if (isLocked(blueprint)) {
            return;
        }

        List<Item> choices = trackChoices();

        if (choices.isEmpty()) {
            player.displayClientMessage(
                    Component.literal(
                            "Blueprint: no track items are registered."),
                    true);
            return;
        }

        Item current = selectedTrack(blueprint);
        if (current == null) {
            current = choices.get(0);
        }

        String currentFamily = familyFor(current);

        if (familyOnly) {
            List<String> families =
                    availableFamilies(choices);
            if (families.isEmpty()) return;

            int familyIndex =
                    Math.max(
                            0,
                            families.indexOf(currentFamily));

            int nextFamilyIndex =
                    Math.floorMod(
                            familyIndex
                                    + Integer.signum(direction),
                            families.size());

            String nextFamily =
                    families.get(nextFamilyIndex);

            List<Item> nextFamilyTracks =
                    familyTracks(
                            choices,
                            nextFamily);

            if (nextFamilyTracks.isEmpty()) return;

            Item next = nextFamilyTracks.get(0);
            ResourceLocation id =
                    ForgeRegistries.ITEMS.getKey(next);
            if (id == null) return;

            CompoundTag data = data(blueprint);
            data.putString(
                    SELECTED_TRACK,
                    canonicalTrackId(id.toString()));
            data.putBoolean(SELECTED_LEFT, false);

            player.displayClientMessage(
                    Component.literal("Family ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(
                                    Component.literal(
                                                    (nextFamilyIndex + 1)
                                                            + "/"
                                                            + families.size()
                                                            + " ["
                                                            + nextFamily
                                                            + "]")
                                            .withStyle(
                                                    familyColor(
                                                            nextFamily)))
                            .append(
                                    Component.literal(": ")
                                            .withStyle(
                                                    ChatFormatting.GRAY))
                            .append(
                                    new ItemStack(next)
                                            .getHoverName()),
                    true);
            return;
        }

        List<Item> familyTracks =
                familyTracks(
                        choices,
                        currentFamily);

        if (familyTracks.isEmpty()) return;

        int currentIndex =
                Math.max(
                        0,
                        familyTracks.indexOf(current));

        int nextIndex =
                Math.floorMod(
                        currentIndex
                                + Integer.signum(direction),
                        familyTracks.size());

        Item next = familyTracks.get(nextIndex);
        ResourceLocation id =
                ForgeRegistries.ITEMS.getKey(next);
        if (id == null) return;

        CompoundTag data = data(blueprint);
        data.putString(
                SELECTED_TRACK,
                canonicalTrackId(id.toString()));
        data.putBoolean(SELECTED_LEFT, false);

        player.displayClientMessage(
                Component.literal("[")
                        .withStyle(ChatFormatting.GRAY)
                        .append(
                                Component.literal(
                                                currentFamily)
                                        .withStyle(
                                                familyColor(
                                                        currentFamily)))
                        .append(
                                Component.literal("] ")
                                        .withStyle(
                                                ChatFormatting.GRAY))
                        .append(
                                Component.literal(
                                                (nextIndex + 1)
                                                        + "/"
                                                        + familyTracks.size()
                                                        + ": ")
                                        .withStyle(
                                                ChatFormatting.GRAY))
                        .append(
                                new ItemStack(next)
                                        .getHoverName()),
                true);
    }

    // TC_BATCH8_R3ZZV2C_29X32X_DETERMINISTIC_HAND
    //
    // 29x and 32x are oneRoute specs with one registry item each. They still
    // have two valid geometric hands when snapped to an incoming cardinal
    // route; the hand is produced by choosing the corresponding rotated /
    // reversed endpoint placement rather than by swapping to a LEFT item ID.
    private static boolean blueprintIsSingleIdQuarterCurve(
            ItemStack stack) {
        if (stack == null
                || stack.isEmpty()) {
            return false;
        }

        String specId =
                blueprintBatchSpecId(
                        stack);

        return "track_curve_29x".equals(
                        specId)
                || "track_curve_32x".equals(
                        specId);
    }
    private static void cycleTrack(
            ItemStack blueprint,
            Player player) {
        Item selected =
                selectedTrack(
                        blueprint);

        ResourceLocation key =
                selected == null
                        ? null
                        : ForgeRegistries.ITEMS.getKey(
                                selected);

        boolean directionalTrack =
                key != null
                        && (isUnifiedPairId(
                                        key.toString())
                                || selected
                                        instanceof traincraft.item.track.LegacyMediumCurveTrackItem
                                || blueprintIsSingleIdQuarterCurve(
                                        new ItemStack(
                                                selected)));

        if (directionalTrack) {
            boolean left =
                    !selectedDirectionLeft(
                            blueprint);

            data(blueprint).putBoolean(
                    SELECTED_LEFT,
                    left);

            player.displayClientMessage(
                    Component.literal(
                            "Blueprint direction: "
                                    + (left
                                            ? "LEFT"
                                            : "RIGHT")),
                    true);
            return;
        }

        cycleSelection(
                blueprint,
                player,
                1,
                false);
    }

    public static Map<String, Integer> requiredMaterials(ItemStack blueprint) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        CompoundTag data = existingData(blueprint);
        if (data == null) return counts;

        ListTag segments =
                data.getList(
                        SEGMENTS,
                        CompoundTag.TAG_COMPOUND);

        for (int i = 0; i < segments.size(); i++) {
            CompoundTag segment = segments.getCompound(i);
            String id =
                    segmentCanonicalItemId(segment);

            if (!id.isEmpty()) {
                // LEFT and RIGHT now consume the same canonical visible item.
                counts.merge(id, 1, Integer::sum);
            }
        }

        return counts;
    }

    private static void showMaterials(ItemStack blueprint, Player player) {
        Map<String, Integer> counts = requiredMaterials(blueprint);

        player.displayClientMessage(
                Component.literal("Blueprint Materials (" + segmentCount(blueprint) + " placements)")
                        .withStyle(ChatFormatting.AQUA),
                false);

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            Component name = item == null ? Component.literal(entry.getKey()) : new ItemStack(item).getHoverName();

            player.displayClientMessage(
                    Component.literal("  " + entry.getValue() + " x ").append(name),
                    false);
        }

        player.displayClientMessage(
                Component.literal("  Support/ballast: up to " + segmentCount(blueprint) + " where needed")
                        .withStyle(ChatFormatting.GRAY),
                false);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack blueprint = context.getItemInHand();
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        CompoundTag data = data(blueprint);
        ListTag segments = data.getList(SEGMENTS, CompoundTag.TAG_COMPOUND);
        BlockPos target = context.getClickedPos().relative(context.getClickedFace());

        if (data.getBoolean(LOCKED)) {
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(
                        Component.literal("Blueprint complete. Sneak + right-click air to edit."),
                        true);
                return InteractionResult.CONSUME;
            }

            data.putLong(PREVIEW_ANCHOR, target.asLong());
            data.putInt(PREVIEW_ROTATION, previewRotationQuarter(blueprint, player.getYRot()));

            player.displayClientMessage(
                    Component.literal("Build Start set: " + target.getX() + ", " + target.getY() + ", " + target.getZ()),
                    true);

            return InteractionResult.CONSUME;
        }

        if (player.isShiftKeyDown()) {
            if (!segments.isEmpty()) {
                segments.remove(segments.size() - 1);
                data.put(SEGMENTS, segments);

                if (segments.isEmpty()) {
                    data.remove(ORIGIN);
                    data.remove(BASE_YAW);
                    data.remove(PREVIEW_ANCHOR);
                    data.remove(PREVIEW_ROTATION);
                }

                updateVisualModel(blueprint);

                player.displayClientMessage(
                        Component.literal("Undo: " + segments.size() + " track(s) remain."),
                        true);
            } else {
                player.displayClientMessage(Component.literal("Blueprint is empty."), true);
            }

            return InteractionResult.CONSUME;
        }

        ItemStack track = selectedTrackStack(blueprint);
        if (track.isEmpty()) {
            player.displayClientMessage(Component.literal("Blueprint: no selectable track found."), true);
            return InteractionResult.CONSUME;
        }

        if (segments.size() >= MAX_SEGMENTS) {
            player.displayClientMessage(
                    Component.literal("Blueprint limit reached: " + MAX_SEGMENTS + " tracks."),
                    true);
            return InteractionResult.CONSUME;
        }

        BlueprintPlacement tcPlacement =
                previewPlacement(
                        blueprint,
                        track,
                        target,
                        player.getYRot());

        // TC_BATCH8_R3ZZM2_NULL_PLACEMENT_CONTRACT_FIX
        // r3zzl intentionally returns null for an incompatible route tangent.
        if (tcPlacement == null) {
            return InteractionResult.FAIL;
        }

        target = tcPlacement.anchor();
        float tcPlacementYaw =
                tcPlacement.yaw();

        OverlapResult tcOverlap =
                placementOverlap(
                        context.getLevel(),
                        blueprint,
                        track,
                        target,
                        tcPlacementYaw);

        if (tcOverlap.blocked()) {
            // A rejected first click must leave a truly blank Blueprint.
            if (segments.isEmpty()) {
                data.remove(ORIGIN);
                data.remove(BASE_YAW);
            }


            // TC_BATCH8_R3ZZY_OVERLAP_CENTER_ONLY
            // Large centered RED overlap warning is the only player-facing warning.

            traincraft.Traincraft.LOGGER.info(
                    "[TC-R3ZZB-BP-OVERLAP] blocked source={} cell={} item={} anchor={} yaw={}",
                    tcOverlap.source(),
                    tcOverlap.cell(),
                    ForgeRegistries.ITEMS.getKey(
                            track.getItem()),
                    target,
                    tcPlacementYaw);

            return InteractionResult.CONSUME;
        }

        if (segments.isEmpty()) {
            data.putLong(ORIGIN, target.asLong());
            data.putFloat(BASE_YAW, player.getYRot());
        }

        BlockPos origin = BlockPos.of(data.getLong(ORIGIN));
        int dx = target.getX() - origin.getX();
        int dy = target.getY() - origin.getY();
        int dz = target.getZ() - origin.getZ();

        for (int i = 0; i < segments.size(); i++) {
            CompoundTag old = segments.getCompound(i);
            if (old.getInt("X") == dx && old.getInt("Y") == dy && old.getInt("Z") == dz) {
                player.displayClientMessage(
                        Component.literal("Blueprint already has a track there."),
                        true);
                return InteractionResult.CONSUME;
            }
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(track.getItem());
        if (itemId == null) return InteractionResult.FAIL;

        CompoundTag entry = new CompoundTag();
        entry.putInt("X", dx);
        entry.putInt("Y", dy);
        entry.putInt("Z", dz);
        entry.putString(
                "Item",
                canonicalTrackId(itemId.toString()));
        entry.putBoolean(
                SEGMENT_LEFT,
                (track.getItem()
                                instanceof traincraft.item.track.LegacyMediumCurveTrackItem
                        || blueprintIsSingleIdQuarterCurve(
                                track))
                        ? selectedDirectionLeft(
                                blueprint)
                        : stackUnifiedLeft(
                                track));
        entry.putFloat(
                "Yaw",
                tcPlacementYaw);
        storeRouteExit(
                entry,
                tcPlacement,
                origin);
        segments.add(entry);
        data.put(SEGMENTS, segments);

        player.displayClientMessage(
                Component.literal("Added ").append(track.getHoverName())
                        .append(Component.literal(" (" + segments.size() + "/" + MAX_SEGMENTS + ")")),
                true);

        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // TC_BATCH8_R3ZU_BLUEPRINT_NAMING_POPUP
        // When an editable, non-empty Blueprint is completed, open a small
        // client naming dialog. The server still owns the actual lock state
        // and applies the submitted name through the Blueprint network.
        if (level.isClientSide
                && player.isShiftKeyDown()
                && !isLocked(stack)
                && segmentCount(stack) > 0) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () ->
                            traincraft.client.screen.TrackBlueprintNameClient.open());
        }

        if (!level.isClientSide) {
            CompoundTag data = data(stack);
            int count = data.getList(SEGMENTS, CompoundTag.TAG_COMPOUND).size();

            if (player.isShiftKeyDown()) {
                if (count == 0) {
                    player.displayClientMessage(
                            Component.literal("Add at least one track before completing the blueprint."),
                            true);
                } else {
                    boolean locked = !data.getBoolean(LOCKED);
                    data.putBoolean(LOCKED, locked);

                    if (!locked) {
                        data.remove(PREVIEW_ANCHOR);
                        data.remove(PREVIEW_ROTATION);
                    }

                    updateVisualModel(stack);

                    player.displayClientMessage(
                            Component.literal(locked ? "Blueprint complete." : "Blueprint unlocked for editing."),
                            true);

                    if (locked) {
                        showMaterials(stack, player);
                    }
                }
            } else if (data.getBoolean(LOCKED)) {
                showMaterials(stack, player);
            } else {
                cycleTrack(stack, player);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }


    @Override
    public Component getName(ItemStack stack) {
        // Default item naming follows the visual state. A user-supplied
        // custom hover name automatically takes precedence at ItemStack level.
        return Component.literal(
                        isLocked(stack)
                                ? "Blueprint"
                                : "Blank Blueprint")
                .withStyle(ChatFormatting.AQUA);
    }    @Override
    public boolean isFoil(ItemStack stack) {
        // Completed Blueprints use their filled track-plan icon; no extra
        // enchantment-style completion glint is needed.
        return super.isFoil(stack);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        int count = segmentCount(stack);

        tooltip.add(
                Component.literal(isLocked(stack) ? "Completed Track Blueprint" : "Blank Track Blueprint")
                        .withStyle(isLocked(stack) ? ChatFormatting.AQUA : ChatFormatting.WHITE));

        tooltip.add(Component.literal(count + " recorded track" + (count == 1 ? "" : "s")));

        if (!isLocked(stack)) {
            ItemStack selected = selectedTrackStack(stack);
            if (!selected.isEmpty()) {
                tooltip.add(
                        Component.literal("Current track: ").append(selected.getHoverName())
                                .withStyle(ChatFormatting.YELLOW));
            }

            Item selectedItem = selected.isEmpty() ? null : selected.getItem();
            String family = selectedItem == null ? "Other" : familyFor(selectedItem);

            tooltip.add(
                    Component.literal("Family: " + family)
                            .withStyle(familyColor(family)));
            tooltip.add(Component.literal("Wheel = previous / next track").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Shift + wheel = previous / next family").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Right-click block = add track").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Sneak + block = undo").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Sneak + air = finish").withStyle(ChatFormatting.GRAY));
            ResourceLocation selectedKey =
                    selectedItem == null
                            ? null
                            : ForgeRegistries.ITEMS.getKey(selectedItem);

            if (selectedKey != null
                    && (isUnifiedPairId(selectedKey.toString())
                            || selectedItem
                                    instanceof traincraft.item.track.LegacyMediumCurveTrackItem)) {
                tooltip.add(
                        Component.literal(
                                        "Right-click air = "
                                                + (selectedDirectionLeft(stack)
                                                        ? "LEFT"
                                                        : "RIGHT")
                                                + " / toggle hand")
                                .withStyle(ChatFormatting.YELLOW));
            } else {
                tooltip.add(
                        Component.literal(
                                        "Right-click air = next track fallback")
                                .withStyle(ChatFormatting.DARK_GRAY));
            }
            return;
        }

        tooltip.add(Component.literal("READY TO BUILD").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("1) Block = set Build Start").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("2) Air = show materials").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("3) Sneak + air = edit again").withStyle(ChatFormatting.GRAY));

        Map<String, Integer> counts = requiredMaterials(stack);
        if (!counts.isEmpty()) {
            tooltip.add(Component.literal("Materials:").withStyle(ChatFormatting.GOLD));

            int shown = 0;
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (shown >= 6) {
                    tooltip.add(Component.literal("  +" + (counts.size() - shown) + " more...").withStyle(ChatFormatting.DARK_GRAY));
                    break;
                }

                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
                Component name = item == null ? Component.literal(entry.getKey()) : new ItemStack(item).getHoverName();

                tooltip.add(
                        Component.literal("  " + entry.getValue() + " x ").append(name)
                                .withStyle(ChatFormatting.GRAY));

                shown++;
            }

            tooltip.add(Component.literal("Support/ballast: up to " + count).withStyle(ChatFormatting.DARK_GRAY));
        }

        if (hasPreviewAnchor(stack)) {
            BlockPos start = previewAnchor(stack);
            tooltip.add(Component.literal("Build Start: " + start.getX() + ", " + start.getY() + ", " + start.getZ())
                    .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.literal("Build Start: not set").withStyle(ChatFormatting.YELLOW));
        }
    }
}
