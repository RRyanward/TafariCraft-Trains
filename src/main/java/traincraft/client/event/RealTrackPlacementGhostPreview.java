package traincraft.client.event;

// TC_BATCH8_R3ZZX1_REAL_TRACK_WHITE_PREVIEW
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
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
import java.util.List;
import java.util.function.Supplier;

/**
 * White-only placement ghost for REAL held track items.
 *
 * This intentionally has no green/red/blue status language. It is only a
 * direction/orientation preview so the player can see where the real track
 * will go before clicking.
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

    private static final float WHITE_ALPHA =
            0.66F;
    // TC_FINAL_RELEASE_R1F_MEDIUM_RUNTIME_FINGERPRINT
    private static int tcMediumHeldFingerprintTick =
            Integer.MIN_VALUE;
    private static int tcMediumBranchFingerprintTick =
            Integer.MIN_VALUE;

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

    private static final Field STRAIGHT_LENGTH =
            field(
                    LegacyStraightTrackItem.class,
                    "length");

    private record HeldTrack(
            ItemStack stack,
            InteractionHand hand) {}

    private record WorldPreviewPart(
            BlockPos pos,
            BlockState state) {}

    private RealTrackPlacementGhostPreview() {}

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

        HeldTrack held =
                heldRealTrack(
                        mc);

                if (held == null
                || !(mc.hitResult
                        instanceof BlockHitResult hit)) {
            return;
        }

        List<WorldPreviewPart> parts =
                previewParts(
                        mc,
                        held,
                        hit);

        if (parts.isEmpty()) {
            return;
        }

        PoseStack pose =
                event.getPoseStack();

        Vec3 camera =
                event.getCamera().getPosition();

        MultiBufferSource.BufferSource buffers =
                mc.renderBuffers().bufferSource();

        for (WorldPreviewPart part
                : parts) {
            renderWhiteBlockState(
                    mc,
                    pose,
                    buffers,
                    part.state(),
                    part.pos(),
                    camera);
        }

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
                        || item instanceof LegacyBatchTrackItem)
                        && net.minecraft.world.level.block.BaseRailBlock.isRail(
                                mc.level.getBlockState(hit.getBlockPos()));

        if (hit.getDirection() != Direction.UP
                && !tcRailSideSnapHit) {
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

            return batchPartsAtRoot(
                    placement.item(),
                    placement.root(),
                    placement.facing());
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
        BlockPos manualAnchor =
                hit.getBlockPos().above();

        Direction facing =
                mc.player.getDirection();

        if (item
                instanceof LegacyStraightTrackItem straight) {
            return straightParts(
                    straight,
                    manualAnchor,
                    facing);
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
            return mediumCurveParts(
                    placement.root(),
                    placement.facing());
        }

        return List.of();
    }

    private static List<WorldPreviewPart> batchPartsAtRoot(
            LegacyBatchTrackItem item,
            BlockPos root,
            Direction facing) {
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
                                        false);

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

    private static void renderWhiteBlockState(
            Minecraft mc,
            PoseStack pose,
            MultiBufferSource buffers,
            BlockState state,
            BlockPos worldPos,
            Vec3 camera) {
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

        emitWhiteQuads(
                pose,
                consumer,
                model.getQuads(
                        state,
                        null,
                        random,
                        modelData,
                        null));

        for (Direction side
                : SIDES) {
            random.setSeed(
                    42L);

            emitWhiteQuads(
                    pose,
                    consumer,
                    model.getQuads(
                            state,
                            side,
                            random,
                            modelData,
                            null));
        }

        pose.popPose();
    }

    private static void emitWhiteQuads(
            PoseStack pose,
            VertexConsumer consumer,
            List<BakedQuad> quads) {
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
                    1.0F,
                    1.0F,
                    WHITE_ALPHA,
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
