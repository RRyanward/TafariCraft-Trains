package traincraft.client.event;

// TC_BATCH8_R3ZZX1_REAL_TRACK_WHITE_PREVIEW
// TC_FINAL_RELEASE_R1AB_REAL_GHOST_VISUAL_OWNERSHIP
// TC_BATCH8_R3ZZX2_NORMAL_STRAIGHT_WHITE_PREVIEW

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.block.track.AbstractLegacyBatchRailBlock;
import traincraft.block.track.LegacyBatchTrackSpec;
import traincraft.block.track.LegacyMediumCurveRailBlock;
import traincraft.block.track.LegacyStraightAssemblyRailBlock;
import traincraft.item.TrackBlueprintItem;
import traincraft.item.track.LegacyBatchTrackItem;
import traincraft.item.track.LegacyMediumCurveTrackItem;
import traincraft.item.track.LegacyStraightTrackItem;
import traincraft.registry.TCBlocks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

/**
 * Placement ghost for REAL held track items: WHITE when clear, RED when
 * the intended footprint encounters an obstruction. This is read-only and
 * never overwrites tracks or changes native placement/crossing rules.
 */
@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RealTrackPlacementGhostPreview {
    private static final Direction[] SIDES =
            Direction.values();

    private static final int FULL_LIGHT =
            LightTexture.FULL_BRIGHT;

    private static final float WHITE_ALPHA = 0.66F;
    private static final float BLOCKED_ALPHA = 0.86F;

    // G12R4R4: only the track ghost turns red; no per-cell wireframe boxes.
    // Click warnings are emitted once per placement attempt, never every frame.
    private static final int MAX_OBSTRUCTION_CELLS = 128;

    // TC_FINAL_RELEASE_R1F_MEDIUM_RUNTIME_FINGERPRINT
    private static int tcMediumHeldFingerprintTick =
            Integer.MIN_VALUE;
    private static int tcMediumBranchFingerprintTick =
            Integer.MIN_VALUE;

    // DEV50B2J -- limit geometry telemetry to 12 snapshots/session, 2 seconds
    // apart, and ONLY while holding a Medium Diagonal Straight track item.
    private static int tcDiagLastDebugTick = Integer.MIN_VALUE;
    private static int tcDiagDebugSnapshots = 0;
    // DEV50B2M -- log at most once per 6 seconds of active preview.
    private static int tcDiagSeamLastLogTick = Integer.MIN_VALUE;

    private static final ResourceLocation WHITE_TEXTURE =
            new ResourceLocation(
                    "traincraft",
                    "textures/misc/blueprint_ghost_white.png");

    private static final Field BATCH_SPEC =
            field(
                    LegacyBatchTrackItem.class,
                    "spec");

    private static final Field BATCH_BLOCK_SUPPLIER =
            field(
                    LegacyBatchTrackItem.class,
                    "blockSupplier");

    private static final Method BATCH_STATE_FOR_PLACEMENT =
            method(
                    LegacyBatchTrackItem.class,
                    "stateForPlacement",
                    AbstractLegacyBatchRailBlock.class,
                    Direction.class,
                    int.class,
                    boolean.class,
                    boolean.class);

    // TC_FINAL_RELEASE_R1AG_GHOST_HAND_MODE
    // Read-only client reflection: use the same selected-hand state as the
    // actual Batch item without restoring LEFT/RIGHT to item hover names.
    private static final Method BATCH_IS_DIRECTIONAL_HAND_FAMILY =
            method(
                    LegacyBatchTrackItem.class,
                    "tcIsDirectionalHandFamily",
                    String.class);

    private static final Method BATCH_UNIFIED_SELECTED_LEFT =
            method(
                    LegacyBatchTrackItem.class,
                    "tcUnifiedSelectedLeft",
                    ItemStack.class);

    private static String TC_LAST_GHOST_HAND_MODE = null;
    private static long TC_LAST_GHOST_HAND_TICK = Long.MIN_VALUE;
    private static final Field STRAIGHT_LENGTH =
            field(
                    LegacyStraightTrackItem.class,
                    "length");

    private record HeldTrack(
            ItemStack stack,
            InteractionHand hand) {}

    // DEV50B2M: standard incoming ghost, or a *seam-only* extra extension
    // on a physically connected existing diagonal. Never preview the whole
    // existing track again: that was the rejected DEV50B2K regression.
    private record WorldPreviewPart(
            BlockPos pos,
            BlockState state,
            boolean seamOnly,
            int seamAxisX,
            int seamAxisZ,
            double seamCutoff,
            boolean seamAtStart) {
        private WorldPreviewPart(BlockPos pos, BlockState state) {
            this(pos, state, false, 0, 0, 0.0D, false);
        }
    }

    private RealTrackPlacementGhostPreview() {}

    /** Block-only hit target, immune to cows/villagers/player entities walking
     * in front of the crosshair. Do not change native server collisions. */
    private static BlockHitResult trackGroundTarget(Minecraft mc) {
        if (mc.level == null || mc.player == null) return null;
        // Entity#pick traces blocks only (not entities), unlike mc.hitResult.
        double reach = mc.player.getAbilities().instabuild ? 5.0D : 4.5D;
        HitResult result = mc.player.pick(reach, mc.getFrameTime(), false);
        if (result instanceof BlockHitResult blockHit
                && result.getType() == HitResult.Type.BLOCK) {
            return blockHit;
        }
        return null;
    }

    private static List<BlockPos> collisionCells(Minecraft mc, HeldTrack held,
            List<WorldPreviewPart> parts) {
        LinkedHashSet<BlockPos> blocked = new LinkedHashSet<>();
        for (WorldPreviewPart part : parts) {
            if (part.seamOnly()) continue;
            if (tcGhostCellBlocked(mc, held, part)) {
                blocked.add(part.pos().immutable());
                if (blocked.size() >= MAX_OBSTRUCTION_CELLS) break;
            }
        }
        return List.copyOf(blocked);
    }

    /** Fired on an actual right-click, not continuously while looking. */
    @SubscribeEvent
    public static void onTrackUseAttempt(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) return;
        HeldTrack held = heldRealTrack(mc);
        if (held == null || held.hand() != event.getHand()) return;
        BlockHitResult hit = trackGroundTarget(mc);
        if (hit == null) return;

        // Right-clicking while a mob walks between the player and target
        // should reach the block, exactly as the stable ghost does. Restrict
        // this override to a real held track: ordinary tools, entity clicks,
        // and unrelated items keep vanilla targeting.
        if (mc.hitResult != null
                && mc.hitResult.getType() == HitResult.Type.ENTITY) {
            mc.hitResult = hit;
        }

        List<WorldPreviewPart> parts = previewParts(mc, held, hit);
        if (parts.isEmpty()) return;
        List<BlockPos> collided = collisionCells(mc, held, parts);
        if (!collided.isEmpty()) {
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("COLLISION DETECTED")
                            .withStyle(net.minecraft.ChatFormatting.RED,
                                    net.minecraft.ChatFormatting.BOLD), true);
            // Native server placement still rejects overlaps, as before.
            // No forced crossings, entity removal, or world edits.
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(
            RenderLevelStageEvent event) {
        if (event.getStage()
                != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            return;
        }

        Minecraft mc =
                Minecraft.getInstance();

        if (mc.level == null
                || mc.player == null) {
            return;
        }
        int tcFingerprintTick =
                mc.player.tickCount;
        if (tcFingerprintTick != tcMediumHeldFingerprintTick
                && tcFingerprintTick % 20 == 0) {
            tcMediumHeldFingerprintTick =
                    tcFingerprintTick;
            tcLogMediumHeldFingerprint(
                    mc,
                    "MAIN",
                    mc.player.getMainHandItem());
            tcLogMediumHeldFingerprint(
                    mc,
                    "OFF",
                    mc.player.getOffhandItem());
        }

        // Blueprint keeps its own accepted colored/white route language.
        // This event is ONLY for actual held track items.
        if (mc.player.getMainHandItem().getItem()
                        instanceof TrackBlueprintItem
                || mc.player.getOffhandItem().getItem()
                        instanceof TrackBlueprintItem) {
            return;
        }

        HeldTrack held = heldRealTrack(mc);
        if (held == null) return;
        // A mob crossing the sight line can change mc.hitResult to ENTITY.
        // A fresh block-only ray keeps preview location/facing stable instead
        // of flickering on/off based on entity targeting.
        BlockHitResult hit = trackGroundTarget(mc);
        if (hit == null) return;

        List<WorldPreviewPart> parts =
                previewParts(
                        mc,
                        held,
                        hit);

        PoseStack pose =
                event.getPoseStack();

        Vec3 camera =
                event.getCamera().getPosition();

        MultiBufferSource.BufferSource buffers =
                mc.renderBuffers().bufferSource();

        // G12R3: use the full proposed assembly for the obstruction test.
        // Even when it intersects existing rails the ghost stays visible;
        // this is a visual inspection aid, NOT permission to replace rails.
        List<BlockPos> obstructingCells = collisionCells(mc, held, parts);
        boolean obstructed = !obstructingCells.isEmpty();
        for (WorldPreviewPart part : parts) {
            renderWhiteBlockState(mc, pose, buffers, part, camera, obstructed);
        }

        // No red block outline. Only the actual proposed track is red.
        buffers.endBatch();
    }

    private static List<WorldPreviewPart> previewParts(
            Minecraft mc,
            HeldTrack held,
            BlockHitResult hit) {
        ItemStack stack =
                held.stack();

        Item item =
                stack.getItem();
                // TC_FINAL_RELEASE_R1K_RAIL_SIDE_CLICK_PARITY
        // TC_FINAL_RELEASE_R1M_BATCH_8WAY_REAL_SNAP_PARITY
        boolean tcRailSideSnapHit =
                (item instanceof LegacyMediumCurveTrackItem
                        || item instanceof LegacyStraightTrackItem
                        || item instanceof LegacyBatchTrackItem)
                        && net.minecraft.world.level.block.BaseRailBlock.isRail(
                                mc.level.getBlockState(hit.getBlockPos()));

        boolean tcVegetationHit =
                traincraft.item.track.TrackPlacementVegetation.isPlant(
                        mc.level.getBlockState(hit.getBlockPos()));
        if (hit.getDirection() != Direction.UP
                && !tcRailSideSnapHit
                && !tcVegetationHit) {
            return List.of();
        }

        if (item
                instanceof LegacyBatchTrackItem batch) {
            LegacyBatchTrackItem.PlacementPreview placement =
                    batch.previewPlacement(
                            mc.level,
                            mc.player,
                            stack,
                            held.hand(),
                            hit);

            if (placement == null) {
                return List.of();
            }

            String tcGhostHandMode =
                    batchGhostHandMode(
                            batch,
                            stack,
                            mc.player.isShiftKeyDown());

            if (tcGhostHandMode != null) {
                long tcNow =
                        mc.level.getGameTime();

                if (!tcGhostHandMode.equals(
                                TC_LAST_GHOST_HAND_MODE)
                        || tcNow
                                - TC_LAST_GHOST_HAND_TICK
                                >= 20L) {
                    mc.player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(
                                    "Track ghost: "
                                            + tcGhostHandMode),
                            true);

                    TC_LAST_GHOST_HAND_MODE =
                            tcGhostHandMode;
                    TC_LAST_GHOST_HAND_TICK =
                            tcNow;
                }
            }

            // DEV50B2J -- read-only capped preview/placement parity capture.
            // We DO NOT change the ghost, placed rails, movement, or shaders.
            List<WorldPreviewPart> batchPreview =
                    batchPartsAtRoot(
                            placement.item(),
                            placement.root(),
                            placement.facing(),
                            placement.originalVisualRootOwnership());
            tcDebugMediumDiagonalGhost(
                    mc, stack, hit, placement, batchPreview);
            // DEV50B2M: add ONLY the connecting extension in unoccupied
            // space. No entire-source overlay, block writes or move of the
            // incoming ghost; native placement and snapped root stay exact.
            return tcPreviewMediumDiagonalMissingSeam(mc, placement, batchPreview);
        }

        // r3zzx2: the N / track_normal item is a BlockItem, not a
        // LegacyStraightTrackItem. Ask its real block for the exact placement
        // state so endpoint-aware rail orientation is reflected in the ghost.
        if (isNormalOneBlockTrack(item)) {
            return normalStraightParts(
                    mc,
                    held,
                    hit);
        }
        // Dedicated Straight and Medium placement source files remain frozen.
        // Their preview is directional/manual: enough to show orientation
        // without changing accepted world-placement behavior.
        if (item instanceof LegacyStraightTrackItem straight) {
            LegacyStraightTrackItem.PlacementPreview placement = straight.previewPlacement(
                    mc.level, mc.player, stack, held.hand(), hit);
            return placement == null
                    ? List.of()
                    : straightParts(straight, placement.start(), placement.forward());
        }

        if (item
                instanceof LegacyMediumCurveTrackItem) {
            // TC_FINAL_RELEASE_R1D_MEDIUM_REAL_PREVIEW_PARITY
            LegacyMediumCurveTrackItem.PlacementPreview placement =
                    LegacyMediumCurveTrackItem.previewPlacement(
                            mc.level,
                            mc.player,
                            stack,
                            hit);

            int tcBranchTick =
                    mc.player.tickCount;
            if (tcBranchTick != tcMediumBranchFingerprintTick
                    && tcBranchTick % 20 == 0) {
                tcMediumBranchFingerprintTick =
                        tcBranchTick;
                Traincraft.LOGGER.info(
                        "[TC-FINAL-MEDIUM-PREVIEW-BRANCH] id={} class={} hit={} hitDir={} placement={} root={} facing={}",
                        ForgeRegistries.ITEMS.getKey(item),
                        item.getClass().getName(),
                        hit.getBlockPos(),
                        hit.getDirection(),
                        placement != null,
                        placement == null ? null : placement.root(),
                        placement == null ? null : placement.facing());
            }

            if (placement == null) {
                return List.of();
            }
            List<WorldPreviewPart> snapped = mediumCurveParts(
                    placement.root(), placement.facing());
            // R7: never invent a second, manually reoriented ghost when
            // obstructed. The native item's aim-side choice is authoritative;
            // its actual placement uses the same endpoint solver.
            return snapped;
        }

        return List.of();
    }

    // DEV50B2J -- DIAGONAL GHOST / PLACEMENT READ-ONLY DIAGNOSTIC.
    // Snapshot logs are prefixed [TC-DEV50B2J-DIAG] and intentionally
    // limited to 12 reports per client session to avoid flooding latest.log.
    private static void tcDebugMediumDiagonalGhost(
            Minecraft mc,
            ItemStack heldStack,
            BlockHitResult hit,
            LegacyBatchTrackItem.PlacementPreview placement,
            List<WorldPreviewPart> parts) {
        if (mc.player == null || mc.level == null || parts.isEmpty()) {
            return;
        }
        int tick = mc.player.tickCount;
        if (tick % 40 != 0
                || tick == tcDiagLastDebugTick
                || tcDiagDebugSnapshots >= 12) {
            return;
        }
        try {
            LegacyBatchTrackItem actualItem = placement.item();
            LegacyBatchTrackSpec spec = (LegacyBatchTrackSpec)
                    BATCH_SPEC.get(actualItem);
            if (!"track_diagonal_straight_medium".equals(spec.id())) {
                return;
            }
            tcDiagLastDebugTick = tick;
            tcDiagDebugSnapshots++;
            Direction facing = placement.facing();
            BlockPos root = placement.root();
            boolean nativeOwner = placement.originalVisualRootOwnership();
            ResourceLocation heldId = ForgeRegistries.ITEMS.getKey(
                    heldStack.getItem());
            Traincraft.LOGGER.info(
                    "[TC-DEV50B2J-DIAG] SNAP {}/12 tick={} held={} selected={} hit={} side={} click={} root={} facing={} "
                            + "nativeVisualOwner={} forcedGhostVisualOwner=true parts={} shift={}",
                    tcDiagDebugSnapshots, tick, heldId,
                    ForgeRegistries.ITEMS.getKey(actualItem),
                    hit.getBlockPos(), hit.getDirection(),
                    hit.getLocation(), root, facing, nativeOwner,
                    parts.size(), mc.player.isShiftKeyDown());
            for (LegacyBatchTrackSpec.Endpoint endpoint : spec.endpoints()) {
                Traincraft.LOGGER.info(
                        "[TC-DEV50B2J-DIAG] ENDPOINT part={} physical={} logical={} outgoing={}",
                        endpoint.part(),
                        spec.offsetForPart(root, facing, endpoint.part()),
                        spec.endpointPositionForPart(root, facing, endpoint.part()),
                        spec.rotateDirection(facing, endpoint.outward()));
            }
            AbstractLegacyBatchRailBlock block =
                    (AbstractLegacyBatchRailBlock) parts.get(0).state().getBlock();
            for (int i = 0; i < parts.size(); i++) {
                WorldPreviewPart preview = parts.get(i);
                BlockState nativeState = (BlockState)
                        BATCH_STATE_FOR_PLACEMENT.invoke(
                                null, block, facing, i, false, nativeOwner);
                BlockState currentWorld = mc.level.getBlockState(preview.pos());
                Traincraft.LOGGER.info(
                        "[TC-DEV50B2J-DIAG] PART={} cell={} ghostState={} nativeState={} "
                                + "statesEqual={} worldState={} ghostMesh={} nativeMesh={}",
                        i, preview.pos(), preview.state(), nativeState,
                        preview.state().equals(nativeState), currentWorld,
                        tcDiagMeshBounds(mc, preview.pos(), preview.state()),
                        tcDiagMeshBounds(mc, preview.pos(), nativeState));
            }
            int nearby = 0;
            BlockPos target = hit.getBlockPos();
            // Only the local rail neighborhood, not arbitrary world contents.
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos pos = target.offset(dx, dy, dz);
                        BlockState state = mc.level.getBlockState(pos);
                        if (!(state.getBlock()
                                instanceof AbstractLegacyBatchRailBlock batchBlock)
                                || !"track_diagonal_straight_medium".equals(
                                        batchBlock.getSpec().id())) {
                            continue;
                        }
                        if (++nearby <= 18) {
                            Traincraft.LOGGER.info(
                                    "[TC-DEV50B2J-DIAG] NEARBY_PLACED cell={} "
                                            + "state={} visualMesh={}",
                                    pos, state,
                                    tcDiagMeshBounds(mc, pos, state));
                        }
                    }
                }
            }
            Traincraft.LOGGER.info(
                    "[TC-DEV50B2J-DIAG] END_SNAPSHOT {} nearbyDiagonalCells={} "
                            + "(this diagnostic never writes world blocks)",
                    tcDiagDebugSnapshots, nearby);
        }
        catch (ReflectiveOperationException | RuntimeException ex) {
            Traincraft.LOGGER.warn(
                    "[TC-DEV50B2J-DIAG] Read-only snapshot failed", ex);
        }
    }

    // Computes the world-space AABB of the SAME baked model quads used
    // by the white ghost. This distinguishes a wrong snapped root from a
    // mesh whose geometry lives beyond its owning block.
    private static String tcDiagMeshBounds(
            Minecraft mc, BlockPos pos, BlockState state) {
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        if (model == null
                || model == mc.getModelManager().getMissingModel()) {
            return "MISSING_MODEL";
        }
        ModelData data = model.getModelData(
                mc.level, pos, state, ModelData.EMPTY);
        RandomSource random = RandomSource.create();
        java.util.ArrayList<BakedQuad> quads = new java.util.ArrayList<>();
        random.setSeed(42L);
        quads.addAll(model.getQuads(state, null, random, data, null));
        for (Direction side : SIDES) {
            random.setSeed(42L);
            quads.addAll(model.getQuads(state, side, random, data, null));
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        int vertices = 0;
        for (BakedQuad quad : quads) {
            int[] packed = quad.getVertices();
            if (packed.length % 4 != 0) continue;
            int stride = packed.length / 4;
            if (stride < 3) continue;
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * stride;
                double x = Float.intBitsToFloat(packed[offset]) + pos.getX();
                double y = Float.intBitsToFloat(packed[offset + 1]) + pos.getY();
                double z = Float.intBitsToFloat(packed[offset + 2]) + pos.getZ();
                if (!Double.isFinite(x) || !Double.isFinite(y)
                        || !Double.isFinite(z)) continue;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
                vertices++;
            }
        }
        if (vertices == 0) return "quads=" + quads.size() + " noVertices";
        return String.format(java.util.Locale.ROOT,
                "quads=%d bounds=[%.2f,%.2f,%.2f]..[%.2f,%.2f,%.2f]",
                quads.size(), minX, minY, minZ, maxX, maxY, maxZ);
    }

    // DEV50B2M -- medium diagonal join: the existing shortened track mesh
    // (part 0, visual_root=false) has a 3.6-block diagonal footprint. Native
    // placement promotes it to the 4.6-block connected model (part 6,
    // visual_root=true). Show ONLY the previously missing half-block of that
    // promoted mesh which lies OUTSIDE the shortened source's world-space
    // longitudinal bounds, leaving every existing dark rail visible.
    // This is purely an in-memory render list, not a source block update.
    private static List<WorldPreviewPart> tcPreviewMediumDiagonalMissingSeam(
            Minecraft mc,
            LegacyBatchTrackItem.PlacementPreview placement,
            List<WorldPreviewPart> incomingParts) {
        if (mc.level == null || incomingParts.isEmpty()) {
            return incomingParts;
        }
        try {
            LegacyBatchTrackSpec spec = (LegacyBatchTrackSpec)
                    BATCH_SPEC.get(placement.item());
            if (!"track_diagonal_straight_medium".equals(spec.id())) {
                return incomingParts;
            }
            ArrayList<WorldPreviewPart> combined = null;
            java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
            for (LegacyBatchTrackSpec.Endpoint incomingEnd : spec.endpoints()) {
                BlockPos incomingEndpoint = spec.endpointPosition(
                        placement.root(), placement.facing(), incomingEnd);
                Direction incomingOut = spec.rotateDirection(
                        placement.facing(), incomingEnd.outward());
                for (int dx : new int[] {-1, 1}) {
                    for (int dz : new int[] {-1, 1}) {
                        BlockPos neighborPos = incomingEndpoint.offset(dx, 0, dz);
                        if (!mc.level.hasChunkAt(neighborPos)) {
                            continue;
                        }
                        BlockState nearState = mc.level.getBlockState(neighborPos);
                        if (!(nearState.getBlock() instanceof AbstractLegacyBatchRailBlock sourceBlock)
                                || sourceBlock != incomingParts.get(0).state().getBlock()
                                || !"track_diagonal_straight_medium".equals(sourceBlock.getSpec().id())
                                || !nearState.hasProperty(traincraft.block.track.LegacyBatchRailBlock.ORIGINAL_VISUAL_ROOT)
                                || nearState.getValue(traincraft.block.track.LegacyBatchRailBlock.ORIGINAL_VISUAL_ROOT)) {
                            continue;
                        }
                        LegacyBatchTrackSpec sourceSpec = sourceBlock.getSpec();
                        Direction sourceFacing = sourceBlock.getAssemblyFacing(nearState);
                        BlockPos sourceRoot = sourceBlock.getAssemblyRoot(neighborPos, nearState);
                        int connectedPart = -1;
                        for (LegacyBatchTrackSpec.Endpoint sourceEnd : sourceSpec.endpoints()) {
                            if (sourceSpec.endpointPosition(sourceRoot, sourceFacing, sourceEnd)
                                        .equals(neighborPos)
                                    && sourceSpec.rotateDirection(sourceFacing, sourceEnd.outward())
                                        == incomingOut.getOpposite()) {
                                connectedPart = sourceEnd.part();
                                break;
                            }
                        }
                        if (connectedPart < 0) {
                            continue;
                        }
                        boolean complete = true;
                        for (int i = 0; i < sourceSpec.partCount(); i++) {
                            BlockPos cell = sourceSpec.offsetForPart(sourceRoot, sourceFacing, i);
                            if (!mc.level.hasChunkAt(cell)) {
                                complete = false;
                                break;
                            }
                            BlockState state = mc.level.getBlockState(cell);
                            if (state.getBlock() != sourceBlock
                                    || sourceBlock.getPart(state) != i
                                    || sourceBlock.getAssemblyFacing(state) != sourceFacing) {
                                complete = false;
                                break;
                            }
                        }
                        if (!complete) {
                            continue;
                        }
                        BlockPos visualCell = sourceSpec.offsetForPart(sourceRoot, sourceFacing, 6);
                        if (!seen.add(visualCell)) {
                            continue;
                        }
                        BlockState visualState = mc.level.getBlockState(visualCell);
                        if (visualState.getBlock() != sourceBlock
                                || !visualState.hasProperty(traincraft.block.track.LegacyBatchRailBlock.ORIGINAL_VISUAL_ROOT)
                                || visualState.getValue(traincraft.block.track.LegacyBatchRailBlock.ORIGINAL_VISUAL_ROOT)) {
                            continue;
                        }
                        BlockPos tail = sourceSpec.offsetForPart(sourceRoot, sourceFacing, 6);
                        int axisX = Integer.signum(tail.getX() - sourceRoot.getX());
                        int axisZ = Integer.signum(tail.getZ() - sourceRoot.getZ());
                        if (axisX == 0 || axisZ == 0) {
                            continue;
                        }
                        boolean atStart = connectedPart == 0;
                        BlockState originalRootState = mc.level.getBlockState(sourceRoot);
                        double cutoff = tcMediumDiagLongitudinalBound(
                                mc, sourceRoot, originalRootState, axisX, axisZ, atStart);
                        if (!Double.isFinite(cutoff)) {
                            continue;
                        }
                        if (combined == null) {
                            combined = new ArrayList<>(incomingParts);
                        }
                        combined.add(new WorldPreviewPart(
                                visualCell,
                                visualState.setValue(
                                    traincraft.block.track.LegacyBatchRailBlock.ORIGINAL_VISUAL_ROOT, true),
                                true, axisX, axisZ, cutoff, atStart));
                        int now = mc.player == null ? 0 : mc.player.tickCount;
                        if (now % 120 == 0 && now != tcDiagSeamLastLogTick) {
                            tcDiagSeamLastLogTick = now;
                            Traincraft.LOGGER.info(
                                    "[TC-DEV50B2M] SEAM_ONLY existingSource={} joint={} renderedPart={} axis=({}, {}) "
                                    + "start={} boundary={} incomingRoot={} noWorldWrites=true",
                                    sourceRoot, neighborPos, visualCell, axisX, axisZ,
                                    atStart, cutoff, placement.root());
                        }
                    }
                }
            }
            return combined == null ? incomingParts : combined;
        }
        catch (ReflectiveOperationException | RuntimeException ex) {
            Traincraft.LOGGER.warn("[TC-DEV50B2M] Read-only seam lookup failed", ex);
            return incomingParts;
        }
    }

    // The cutoff is taken from the EXISTING source's real visible baked
    // model, not a hardcoded number or a guessed Minecraft block anchor.
    private static double tcMediumDiagLongitudinalBound(
            Minecraft mc, BlockPos pos, BlockState state,
            int axisX, int axisZ, boolean start) {
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        if (model == null || model == mc.getModelManager().getMissingModel()) {
            return Double.NaN;
        }
        ModelData data = model.getModelData(mc.level, pos, state, ModelData.EMPTY);
        RandomSource random = RandomSource.create();
        double extreme = start ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        int count = 0;
        for (int face = -1; face < SIDES.length; face++) {
            random.setSeed(42L);
            Direction cull = face == -1 ? null : SIDES[face];
            for (BakedQuad quad : model.getQuads(state, cull, random, data, null)) {
                int[] packed = quad.getVertices();
                int stride = packed.length / 4;
                if (stride < 3) {
                    continue;
                }
                for (int i = 0; i < 4; i++) {
                    int v = stride * i;
                    double x = Float.intBitsToFloat(packed[v]) + pos.getX();
                    double z = Float.intBitsToFloat(packed[v + 2]) + pos.getZ();
                    double projected = x * axisX + z * axisZ;
                    if (!Double.isFinite(projected)) {
                        continue;
                    }
                    extreme = start ? Math.min(extreme, projected)
                                    : Math.max(extreme, projected);
                    count++;
                }
            }
        }
        return count == 0 ? Double.NaN : extreme;
    }

    // Drop every quad which touches the existing shortened-model footprint.
    // Entire quads only: no UV changes, vertex shifts, new polygons or culling
    // state edits. The medium diagonal seam models contain whole end quads
    // strictly beyond the shorter source's physical AABB.
    private static List<BakedQuad> tcMediumDiagOutsideSourceOnly(
            List<BakedQuad> quads, WorldPreviewPart part) {
        ArrayList<BakedQuad> outside = new ArrayList<>();
        for (BakedQuad quad : quads) {
            int[] packed = quad.getVertices();
            int stride = packed.length / 4;
            if (stride < 3) {
                continue;
            }
            boolean entirelyOutside = true;
            for (int i = 0; i < 4; i++) {
                int v = i * stride;
                double x = Float.intBitsToFloat(packed[v]) + part.pos().getX();
                double z = Float.intBitsToFloat(packed[v + 2]) + part.pos().getZ();
                double coordinate = x * part.seamAxisX() + z * part.seamAxisZ();
                if (!Double.isFinite(coordinate)
                        || (part.seamAtStart()
                             ? coordinate >= part.seamCutoff() - 0.01D
                             : coordinate <= part.seamCutoff() + 0.01D)) {
                    entirelyOutside = false;
                    break;
                }
            }
            if (entirelyOutside) {
                outside.add(quad);
            }
        }
        return outside;
    }

    private static String batchGhostHandMode(
            LegacyBatchTrackItem item,
            ItemStack stack,
            boolean shiftMirror) {
        try {
            LegacyBatchTrackSpec spec =
                    (LegacyBatchTrackSpec)
                            BATCH_SPEC.get(
                                    item);

            boolean directional =
                    (boolean)
                            BATCH_IS_DIRECTIONAL_HAND_FAMILY.invoke(
                                    null,
                                    spec.id());

            if (!directional) {
                return null;
            }

            boolean selectedLeft =
                    (boolean)
                            BATCH_UNIFIED_SELECTED_LEFT.invoke(
                                    item,
                                    stack);

            // previewPlacement() applies the held Shift mirror live.
            if (shiftMirror
                    && item == stack.getItem()) {
                selectedLeft =
                        !selectedLeft;
            }

            return selectedLeft
                    ? "LEFT"
                    : "RIGHT";
        }
        catch (ReflectiveOperationException ex) {
            return null;
        }
    }
    private static List<WorldPreviewPart> batchPartsAtRoot(
            LegacyBatchTrackItem item,
            BlockPos root,
            Direction facing,
            boolean originalVisualRootOwnership) {
        try {
            LegacyBatchTrackSpec spec =
                    (LegacyBatchTrackSpec)
                            BATCH_SPEC.get(
                                    item);

            @SuppressWarnings("unchecked")
            Supplier<? extends AbstractLegacyBatchRailBlock> supplier =
                    (Supplier<? extends AbstractLegacyBatchRailBlock>)
                            BATCH_BLOCK_SUPPLIER.get(
                                    item);

            AbstractLegacyBatchRailBlock block =
                    supplier.get();

            // DEV50B2I -- MEDIUM_DIAGONAL_HELD_GHOST_VISUAL_PARITY
            // The saved Blueprint ghost uses the original TC4.5 medium
            // diagonal visual owner (part 6). Apply that identical render-
            // only ownership for the real held-item ghost, including Energy
            // items (which share this spec). Never alter placement, pathing,
            // blockstates in the world, or other track families.
            boolean ghostOriginalVisualRoot =
                    originalVisualRootOwnership
                    || "track_diagonal_straight_medium".equals(spec.id());

            ArrayList<WorldPreviewPart> parts =
                    new ArrayList<>(
                            spec.partCount());

            for (int part = 0;
                 part < spec.partCount();
                 part++) {
                BlockPos worldPos =
                        spec.offsetForPart(
                                root,
                                facing,
                                part);

                BlockState state =
                        (BlockState)
                                BATCH_STATE_FOR_PLACEMENT.invoke(
                                        null,
                                        block,
                                        facing,
                                        part,
                                        false,
                                        ghostOriginalVisualRoot);

                parts.add(
                        new WorldPreviewPart(
                                worldPos,
                                state));
            }

            return parts;
        }
        catch (ReflectiveOperationException ex) {
            Traincraft.LOGGER.warn(
                    "[TC-R3ZZX1-REAL-GHOST] Batch preview reflection failed for {}",
                    ForgeRegistries.ITEMS.getKey(
                            item),
                    ex);

            return List.of();
        }
    }

    private static boolean isNormalOneBlockTrack(
            Item item) {
        return item instanceof BlockItem blockItem
                && blockItem.getBlock()
                        == TCBlocks.TRACK_NORMAL.get();
    }

    private static List<WorldPreviewPart> normalStraightParts(
            Minecraft mc,
            HeldTrack held,
            BlockHitResult hit) {
        if (net.minecraft.world.level.block.BaseRailBlock.isRail(
                mc.level.getBlockState(hit.getBlockPos()))) return List.of();
        Item item =
                held.stack().getItem();

        if (!(item instanceof BlockItem blockItem)
                || blockItem.getBlock()
                        != TCBlocks.TRACK_NORMAL.get()) {
            return List.of();
        }

        BlockPlaceContext placeContext =
                new BlockPlaceContext(
                        mc.player,
                        held.hand(),
                        held.stack(),
                        hit);

        BlockState state =
                blockItem.getBlock()
                        .getStateForPlacement(
                                placeContext);

        if (state == null) {
            return List.of();
        }

        return List.of(
                new WorldPreviewPart(
                        placeContext.getClickedPos(),
                        state));
    }
    private static List<WorldPreviewPart> straightParts(
            LegacyStraightTrackItem item,
            BlockPos anchor,
            Direction facing) {
        try {
            int length =
                    STRAIGHT_LENGTH.getInt(
                            item);

            LegacyStraightAssemblyRailBlock block;

            if (length == 3) {
                block =
                        (LegacyStraightAssemblyRailBlock)
                                TCBlocks.TRACK_MEDIUM_STRAIGHT_SEGMENT.get();
            }
            else if (length == 6) {
                block =
                        (LegacyStraightAssemblyRailBlock)
                                TCBlocks.TRACK_LONG_STRAIGHT_SEGMENT.get();
            }
            else if (length == 12) {
                block =
                        (LegacyStraightAssemblyRailBlock)
                                TCBlocks.TRACK_VERY_LONG_STRAIGHT_SEGMENT.get();
            }
            else {
                return List.of();
            }

            RailShape shape =
                    facing.getAxis()
                                    == Direction.Axis.X
                            ? RailShape.EAST_WEST
                            : RailShape.NORTH_SOUTH;

            ArrayList<WorldPreviewPart> parts =
                    new ArrayList<>(
                            length);

            for (int part = 0;
                 part < length;
                 part++) {
                BlockState state =
                        block.defaultBlockState()
                                .setValue(
                                        RailBlock.SHAPE,
                                        shape)
                                .setValue(
                                        LegacyStraightAssemblyRailBlock.ASSEMBLY_FACING,
                                        facing)
                                .setValue(
                                        LegacyStraightAssemblyRailBlock.PART,
                                        part);

                parts.add(
                        new WorldPreviewPart(
                                anchor.relative(
                                        facing,
                                        part),
                                state));
            }

            return parts;
        }
        catch (IllegalAccessException ex) {
            Traincraft.LOGGER.warn(
                    "[TC-R3ZZX1-REAL-GHOST] Straight preview reflection failed for {}",
                    ForgeRegistries.ITEMS.getKey(
                            item),
                    ex);

            return List.of();
        }
    }

    private static List<WorldPreviewPart> mediumCurveParts(
            BlockPos anchor,
            Direction facing) {
        LegacyMediumCurveRailBlock block =
                (LegacyMediumCurveRailBlock)
                        TCBlocks.TRACK_MEDIUM_CURVE_SEGMENT.get();

        ArrayList<WorldPreviewPart> parts =
                new ArrayList<>(
                        LegacyMediumCurveRailBlock.PART_COUNT);

        for (int part = 0;
             part < LegacyMediumCurveRailBlock.PART_COUNT;
             part++) {
            BlockPos offset =
                    LegacyMediumCurveRailBlock.offsetForPart(
                            BlockPos.ZERO,
                            facing,
                            part);

            BlockState state =
                    block.defaultBlockState()
                            .setValue(
                                    RailBlock.SHAPE,
                                    LegacyMediumCurveRailBlock.shapeForPart(
                                            facing,
                                            part))
                            .setValue(
                                    LegacyMediumCurveRailBlock.ASSEMBLY_FACING,
                                    facing)
                            .setValue(
                                    LegacyMediumCurveRailBlock.PART,
                                    part);

            parts.add(
                    new WorldPreviewPart(
                            anchor.offset(
                                    offset),
                            state));
        }

        return parts;
    }

    private static boolean tcGhostCellBlocked(Minecraft mc,
            HeldTrack held, WorldPreviewPart part) {
        BlockPos pos = part.pos();
        BlockState occupied = mc.level.getBlockState(pos);
        // All normal/energy rail blocks are occupied, even if a different
        // rail shape intersects at a right angle. No silent replacement.
        // R7: identical predicate to native medium, straight and batch
        // rail placement. A Torchflower is clearable; a crop/rail isn't.
        boolean occupiedCell =
                !traincraft.item.track.TrackPlacementVegetation.canReplace(occupied);
        boolean fluid = !occupied.getFluidState().isEmpty();
        BlockPos below = pos.below();
        // Batch ramps use spec-defined support positions, not necessarily
        // the block directly below every visual part. Do not flag them RED
        // solely from a naive below-cell probe.
        boolean unsupported = !(held.stack().getItem() instanceof LegacyBatchTrackItem)
                && !mc.level.getBlockState(below).isFaceSturdy(
                        mc.level, below, Direction.UP);
        boolean denied = !mc.player.mayUseItemAt(pos, Direction.UP, held.stack());
        return occupiedCell || fluid || unsupported || denied;
    }

    private static void renderWhiteBlockState(
            Minecraft mc,
            PoseStack pose,
            MultiBufferSource buffers,
            WorldPreviewPart part,
            Vec3 camera,
            boolean obstructed) {
        BlockState state = part.state();
        BlockPos worldPos = part.pos();
        BakedModel model =
                mc.getBlockRenderer().getBlockModel(
                        state);

        if (model == null
                || model
                        == mc.getModelManager()
                                .getMissingModel()) {
            return;
        }

        ModelData modelData =
                model.getModelData(
                        mc.level,
                        worldPos,
                        state,
                        ModelData.EMPTY);

        VertexConsumer consumer =
                buffers.getBuffer(
                        RenderType.entityTranslucent(
                                WHITE_TEXTURE));

        pose.pushPose();

        pose.translate(
                worldPos.getX()
                        - camera.x,
                worldPos.getY()
                        - camera.y
                        + 0.003D,
                worldPos.getZ()
                        - camera.z);

        RandomSource random =
                RandomSource.create();

        random.setSeed(
                42L);

        List<BakedQuad> unculledQuads = model.getQuads(
                state, null, random, modelData, null);
        emitWhiteQuads(pose, consumer, part.seamOnly()
                ? tcMediumDiagOutsideSourceOnly(unculledQuads, part)
                : unculledQuads, obstructed);

        for (Direction side
                : SIDES) {
            random.setSeed(
                    42L);

            List<BakedQuad> faceQuads = model.getQuads(
                    state, side, random, modelData, null);
            emitWhiteQuads(pose, consumer, part.seamOnly()
                    ? tcMediumDiagOutsideSourceOnly(faceQuads, part)
                    : faceQuads, obstructed);
        }

        pose.popPose();
    }

    private static void emitWhiteQuads(
            PoseStack pose,
            VertexConsumer consumer,
            List<BakedQuad> quads,
            boolean obstructed) {
        int[] light = {
                FULL_LIGHT,
                FULL_LIGHT,
                FULL_LIGHT,
                FULL_LIGHT
        };

        float[] shade = {
                1.0F,
                1.0F,
                1.0F,
                1.0F
        };

        for (BakedQuad quad
                : quads) {
            consumer.putBulkData(
                    pose.last(),
                    quad,
                    shade,
                    1.0F,
                    obstructed ? 0.15F : 1.0F,
                    obstructed ? 0.15F : 1.0F,
                    obstructed ? BLOCKED_ALPHA : WHITE_ALPHA,
                    light,
                    OverlayTexture.NO_OVERLAY,
                    false);
        }
    }

    private static HeldTrack heldRealTrack(
            Minecraft mc) {
        ItemStack main =
                mc.player.getMainHandItem();

        if (isPreviewTrack(
                main)) {
            return new HeldTrack(
                    main,
                    InteractionHand.MAIN_HAND);
        }

        ItemStack off =
                mc.player.getOffhandItem();

        if (isPreviewTrack(
                off)) {
            return new HeldTrack(
                    off,
                    InteractionHand.OFF_HAND);
        }

        return null;
    }

    private static void tcLogMediumHeldFingerprint(
            Minecraft mc,
            String hand,
            ItemStack stack) {
        if (stack == null
                || stack.isEmpty()) {
            return;
        }

        Item item =
                stack.getItem();
        ResourceLocation id =
                ForgeRegistries.ITEMS.getKey(
                        item);
        String idText =
                id == null
                        ? "<unregistered>"
                        : id.toString();
        String className =
                item.getClass().getName();

        if (!idText.contains("medium_curve")
                && !className.contains("MediumCurve")) {
            return;
        }

        BlockHitResult hit =
                mc.hitResult instanceof BlockHitResult blockHit
                        ? blockHit
                        : null;

        Traincraft.LOGGER.info(
                "[TC-FINAL-MEDIUM-HELD-FINGERPRINT] hand={} id={} class={} previewEligible={} hit={} hitDir={}",
                hand,
                idText,
                className,
                isPreviewTrack(stack),
                hit == null ? null : hit.getBlockPos(),
                hit == null ? null : hit.getDirection());
    }
    private static boolean isPreviewTrack(
            ItemStack stack) {
        if (stack == null
                || stack.isEmpty()) {
            return false;
        }

        Item item =
                stack.getItem();

        return item
                        instanceof LegacyBatchTrackItem
                || item
                        instanceof LegacyStraightTrackItem
                || item
                        instanceof LegacyMediumCurveTrackItem
                || isNormalOneBlockTrack(
                        item);
    }

    private static Field field(
            Class<?> owner,
            String name) {
        try {
            Field field =
                    owner.getDeclaredField(
                            name);

            field.setAccessible(
                    true);

            return field;
        }
        catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(
                    "Real track preview field unavailable: "
                            + owner.getName()
                            + "."
                            + name,
                    ex);
        }
    }

    private static Method method(
            Class<?> owner,
            String name,
            Class<?>... parameters) {
        try {
            Method method =
                    owner.getDeclaredMethod(
                            name,
                            parameters);

            method.setAccessible(
                    true);

            return method;
        }
        catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(
                    "Real track preview method unavailable: "
                            + owner.getName()
                            + "."
                            + name,
                    ex);
        }
    }
}
