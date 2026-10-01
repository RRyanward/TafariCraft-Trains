package traincraft.client.event;

// TC_BATCH8_R3O_TRUE_PLACED_BLOCKSTATE_TRACK_PREVIEW

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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
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

@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackBlueprintGhostPreview {
    private static final Direction[] SIDES = Direction.values();
    private static final int FULL_LIGHT = LightTexture.FULL_BRIGHT;

    // TC_BATCH8_R3ZZ_TRUE_SOLID_GHOST_COLOR
    // Baked track UVs are reused against a solid white texture. The status
    // vertex color therefore becomes the visible ghost color instead of merely
    // multiplying the original brown/steel rail texture.
    private static final ResourceLocation BLUEPRINT_GHOST_WHITE_TEXTURE =
            new ResourceLocation(
                    "traincraft",
                    "textures/misc/blueprint_ghost_white.png");

    // TC_BATCH8_R3ZZB_OVERLAP_VISUALS
    private static boolean OVERLAP_PREVIEW_ACTIVE = false;

    public static boolean isOverlapPreviewActive() {
        return OVERLAP_PREVIEW_ACTIVE;
    }
    private static final Field BATCH_SPEC =
            field(LegacyBatchTrackItem.class, "spec");
    private static final Field BATCH_BLOCK_SUPPLIER =
            field(LegacyBatchTrackItem.class, "blockSupplier");
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
            field(LegacyStraightTrackItem.class, "length");

    private record PreviewPart(BlockPos offset, BlockState state) {}

    private TrackBlueprintGhostPreview() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            return;
        }

        OVERLAP_PREVIEW_ACTIVE = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        ItemStack blueprint = heldBlueprint(mc);
        if (blueprint.isEmpty()) return;

        int total = TrackBlueprintItem.segmentCount(blueprint);
        BlockHitResult hit =
                mc.hitResult instanceof BlockHitResult blockHit ? blockHit : null;

        BlockPos anchor = null;
        int rotation = 0;

        if (TrackBlueprintItem.isLocked(blueprint)) {
            if (TrackBlueprintItem.hasPreviewAnchor(blueprint)) {
                anchor = TrackBlueprintItem.previewAnchor(blueprint);
                rotation = TrackBlueprintItem.previewRotationQuarter(blueprint);
            } else if (hit != null) {
                anchor = hit.getBlockPos().relative(hit.getDirection());
                rotation = TrackBlueprintItem.previewRotationQuarter(
                        blueprint,
                        mc.player.getYRot());
            }
        } else if (TrackBlueprintItem.hasOrigin(blueprint)) {
            anchor = TrackBlueprintItem.origin(blueprint);
        }

        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers =
                mc.renderBuffers().bufferSource();

        // IMPORTANT:
        // r3o does NOT draw the item's inventory model.
        // It reconstructs the SAME BlockStates that hand placement writes into
        // the world and renders those placed block models at their real cells.
        if (anchor != null && total > 0) {
            int stride = Math.max(1, total / 384);

            for (int i = 0; i < total; i += stride) {
                CompoundTag segment =
                        TrackBlueprintItem.segment(blueprint, i);

                int[] xz = rotate(
                        segment.getInt("X"),
                        segment.getInt("Z"),
                        rotation);

                BlockPos manualAnchor =
                        anchor.offset(
                                xz[0],
                                segment.getInt("Y"),
                                xz[1]);

                ItemStack track = stackFromSegment(segment);
                float yaw =
                        segment.getFloat("Yaw")
                                + rotation * 90.0F;

                // TC_BATCH8_R3ZY_BRIGHT_WHITE_BLUEPRINT_ROUTE
                // Completed route body: bright white. Start/end overrides
                // below intentionally remain the accepted green/red colors.
                float red = 1.00F;
                float green = 1.00F;
                float blue = 1.00F;
                float alpha = 0.78F;

                if (i == 0) {
                    red = 0.45F;
                    green = 1.00F;
                    blue = 0.50F;
                    alpha = 0.48F;
                } else if (i + stride >= total) {
                    // r3zzb: BLUE is reserved for the saved route end.
                    red = 0.20F;
                    green = 0.55F;
                    blue = 1.00F;
                    alpha = 0.78F;
                }

                renderRealPlacement(
                        mc,
                        pose,
                        buffers,
                        track,
                        manualAnchor,
                        Direction.fromYRot(yaw),
                        camera,
                        red,
                        green,
                        blue,
                        alpha);
            }
        }

        // Orange cursor preview = the exact placed BlockStates that the selected
        // track item would write if the player clicked this support block now.
        if (!TrackBlueprintItem.isLocked(blueprint) && hit != null) {
            ItemStack selected =
                    TrackBlueprintItem.selectedTrackStack(blueprint);

            if (!selected.isEmpty()) {
                BlockPos requested =
                        hit.getBlockPos().relative(hit.getDirection());

                TrackBlueprintItem.BlueprintPlacement tcPlacement =
                        TrackBlueprintItem.previewPlacement(
                                blueprint,
                                selected,
                                requested,
                                mc.player.getYRot());

                // TC_BATCH8_R3ZX_BLUEPRINT_SNAP_TELEMETRY
                TrackBlueprintItem.debugPreviewPlacement(
                        blueprint,
                        selected,
                        requested,
                        mc.player.getYRot(),
                        tcPlacement);
                // TC_BATCH8_R3ZZM2_NULL_PLACEMENT_CONTRACT_FIX
                // No legal continuation means no live cursor ghost. Flush all
                // stored Blueprint ghost geometry already queued this frame,
                // then leave before tcPlacement is dereferenced.
                if (tcPlacement == null) {
                    buffers.endBatch();
                    return;
                }

                TrackBlueprintItem.OverlapResult tcOverlap =
                        TrackBlueprintItem.placementOverlap(
                                mc.level,
                                blueprint,
                                selected,
                                tcPlacement.anchor(),
                                tcPlacement.yaw());

                boolean tcBlocked =
                        tcOverlap.blocked();

                OVERLAP_PREVIEW_ACTIVE =
                        tcBlocked;

                renderRealPlacement(

                        mc,
                        pose,
                        buffers,
                        selected,
                        tcPlacement.anchor(),
                        Direction.fromYRot(tcPlacement.yaw()),
                        camera,
                        tcBlocked ? 1.00F : 1.00F,
                        tcBlocked ? 0.12F : 1.00F,
                        tcBlocked ? 0.10F : 1.00F,
                        tcBlocked ? 0.88F : 0.74F);
            }
        }

        buffers.endBatch();
    }

    @SubscribeEvent
    public static void onRenderGui(
            RenderGuiEvent.Post event) {
        if (!OVERLAP_PREVIEW_ACTIVE) {
            return;
        }

        Minecraft mc =
                Minecraft.getInstance();

        if (mc.player == null
                || mc.options.hideGui) {
            return;
        }

        String text =
                "CAN'T PLACE - OVERLAP DETECTED";

        int screenWidth =
                mc.getWindow().getGuiScaledWidth();
        int screenHeight =
                mc.getWindow().getGuiScaledHeight();

        int textWidth =
                mc.font.width(text);

        int x =
                (screenWidth - textWidth) / 2;
        int y =
                screenHeight / 2 - 42;

        event.getGuiGraphics().fill(
                x - 7,
                y - 5,
                x + textWidth + 7,
                y + mc.font.lineHeight + 5,
                0xB0000000);

        event.getGuiGraphics().drawString(
                mc.font,
                text,
                x,
                y,
                0xFFFF5555,
                true);
    }
    private static void renderRealPlacement(
            Minecraft mc,
            PoseStack pose,
            MultiBufferSource buffers,
            ItemStack stack,
            BlockPos manualAnchor,
            Direction facing,
            Vec3 camera,
            float red,
            float green,
            float blue,
            float alpha) {
        List<PreviewPart> parts =
                placementParts(stack, facing);

        for (PreviewPart part : parts) {
            BlockPos worldPos =
                    manualAnchor.offset(part.offset());

            renderPlacedBlockState(
                    mc,
                    pose,
                    buffers,
                    part.state(),
                    worldPos,
                    camera,
                    red,
                    green,
                    blue,
                    alpha);
        }
    }

    private static List<PreviewPart> placementParts(
            ItemStack stack,
            Direction facing) {
        if (stack.isEmpty()) return List.of();

        // Resolve canonical Blueprint stack + stored hand back to the actual
        // registered LEFT/RIGHT implementation before reflecting placement
        // geometry. This keeps the ghost identical to real r3zq placement.
        stack = TrackBlueprintItem.previewPlacementStack(stack);

        Item item = stack.getItem();

        if (item instanceof LegacyBatchTrackItem batch) {
            List<PreviewPart> result =
                    batchPlacementParts(batch, facing);
            if (!result.isEmpty()) return result;
        }

        if (item instanceof LegacyStraightTrackItem straight) {
            List<PreviewPart> result =
                    straightPlacementParts(straight, facing);
            if (!result.isEmpty()) return result;
        }

        if (item instanceof LegacyMediumCurveTrackItem) {
            List<PreviewPart> result =
                    mediumCurvePlacementParts(facing);
            if (!result.isEmpty()) return result;
        }

        if (item instanceof BlockItem blockItem) {
            BlockState state =
                    blockItem.getBlock().defaultBlockState();

            if (state.hasProperty(RailBlock.SHAPE)) {
                RailShape shape =
                        facing.getAxis() == Direction.Axis.X
                                ? RailShape.EAST_WEST
                                : RailShape.NORTH_SOUTH;
                state = state.setValue(RailBlock.SHAPE, shape);
            }

            return List.of(
                    new PreviewPart(BlockPos.ZERO, state));
        }

        return List.of();
    }

    private static List<PreviewPart> batchPlacementParts(
            LegacyBatchTrackItem item,
            Direction facing) {
        try {
            LegacyBatchTrackSpec spec =
                    (LegacyBatchTrackSpec) BATCH_SPEC.get(item);

            @SuppressWarnings("unchecked")
            Supplier<? extends AbstractLegacyBatchRailBlock> supplier =
                    (Supplier<? extends AbstractLegacyBatchRailBlock>)
                            BATCH_BLOCK_SUPPLIER.get(item);

            AbstractLegacyBatchRailBlock block = supplier.get();

            BlockPos manualAnchor = BlockPos.ZERO;
            BlockPos root;

            // Match the current real manual-placement anchor rule.
            if ("track_diagonal_crossing".equals(spec.id())) {
                root = manualAnchor;
            } else {
                int primaryPart =
                        spec.endpoints().get(0).part();

                root =
                        manualAnchor.subtract(
                                spec.offsetForPart(
                                        BlockPos.ZERO,
                                        facing,
                                        primaryPart));
            }

            ArrayList<PreviewPart> parts =
                    new ArrayList<>(spec.partCount());

            for (int part = 0;
                 part < spec.partCount();
                 part++) {
                BlockPos offset =
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
                        new PreviewPart(offset, state));
            }

            return parts;
        } catch (ReflectiveOperationException ex) {
            Traincraft.LOGGER.warn(
                    "[TC_BATCH8_R3O] Could not build real batch-track placement preview for {}",
                    ForgeRegistries.ITEMS.getKey(item),
                    ex);
            return List.of();
        }
    }

    private static List<PreviewPart> straightPlacementParts(
            LegacyStraightTrackItem item,
            Direction facing) {
        try {
            int length = STRAIGHT_LENGTH.getInt(item);

            LegacyStraightAssemblyRailBlock block;

            if (length == 3) {
                block =
                        (LegacyStraightAssemblyRailBlock)
                                TCBlocks.TRACK_MEDIUM_STRAIGHT_SEGMENT.get();
            } else if (length == 6) {
                block =
                        (LegacyStraightAssemblyRailBlock)
                                TCBlocks.TRACK_LONG_STRAIGHT_SEGMENT.get();
            } else if (length == 12) {
                block =
                        (LegacyStraightAssemblyRailBlock)
                                TCBlocks.TRACK_VERY_LONG_STRAIGHT_SEGMENT.get();
            } else {
                return List.of();
            }

            RailShape shape =
                    facing.getAxis() == Direction.Axis.X
                            ? RailShape.EAST_WEST
                            : RailShape.NORTH_SOUTH;

            ArrayList<PreviewPart> parts =
                    new ArrayList<>(length);

            for (int part = 0;
                 part < length;
                 part++) {
                BlockState state =
                        block.defaultBlockState()
                                .setValue(RailBlock.SHAPE, shape)
                                .setValue(
                                        LegacyStraightAssemblyRailBlock.ASSEMBLY_FACING,
                                        facing)
                                .setValue(
                                        LegacyStraightAssemblyRailBlock.PART,
                                        part);

                parts.add(
                        new PreviewPart(
                                BlockPos.ZERO.relative(
                                        facing,
                                        part),
                                state));
            }

            return parts;
        } catch (IllegalAccessException ex) {
            Traincraft.LOGGER.warn(
                    "[TC_BATCH8_R3O] Could not build real straight-track placement preview for {}",
                    ForgeRegistries.ITEMS.getKey(item),
                    ex);
            return List.of();
        }
    }

    private static List<PreviewPart> mediumCurvePlacementParts(
            Direction facing) {
        LegacyMediumCurveRailBlock block =
                (LegacyMediumCurveRailBlock)
                        TCBlocks.TRACK_MEDIUM_CURVE_SEGMENT.get();

        ArrayList<PreviewPart> parts =
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
                    new PreviewPart(offset, state));
        }

        return parts;
    }

    private static void renderPlacedBlockState(
            Minecraft mc,
            PoseStack pose,
            MultiBufferSource buffers,
            BlockState state,
            BlockPos worldPos,
            Vec3 camera,
            float statusRed,
            float statusGreen,
            float statusBlue,
            float alpha) {
        BakedModel model =
                mc.getBlockRenderer().getBlockModel(state);

        if (model == null
                || model == mc.getModelManager().getMissingModel()) {
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
                                BLUEPRINT_GHOST_WHITE_TEXTURE));

        pose.pushPose();
        pose.translate(
                worldPos.getX() - camera.x,
                worldPos.getY() - camera.y + 0.002D,
                worldPos.getZ() - camera.z);

        RandomSource random = RandomSource.create();

        random.setSeed(42L);
        emitPlacedQuads(
                mc,
                pose,
                consumer,
                state,
                worldPos,
                model.getQuads(
                        state,
                        null,
                        random,
                        modelData,
                        null),
                statusRed,
                statusGreen,
                statusBlue,
                alpha);

        for (Direction side : SIDES) {
            random.setSeed(42L);
            emitPlacedQuads(
                    mc,
                    pose,
                    consumer,
                    state,
                    worldPos,
                    model.getQuads(
                            state,
                            side,
                            random,
                            modelData,
                            null),
                    statusRed,
                    statusGreen,
                    statusBlue,
                    alpha);
        }

        pose.popPose();
    }

    private static void emitPlacedQuads(
            Minecraft mc,
            PoseStack pose,
            VertexConsumer consumer,
            BlockState state,
            BlockPos pos,
            List<BakedQuad> quads,
            float statusRed,
            float statusGreen,
            float statusBlue,
            float alpha) {
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

        for (BakedQuad quad : quads) {
            consumer.putBulkData(
                    pose.last(),
                    quad,
                    shade,
                    statusRed,
                    statusGreen,
                    statusBlue,
                    alpha,
                    light,
                    OverlayTexture.NO_OVERLAY,
                    false);
        }
    }

    private static Field field(
            Class<?> owner,
            String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(
                    "Track preview field unavailable: "
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
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(
                    "Track preview method unavailable: "
                            + owner.getName()
                            + "."
                            + name,
                    ex);
        }
    }

    private static ItemStack stackFromSegment(
            CompoundTag segment) {
        // TC_BATCH8_R3ZR_BLUEPRINT2_GHOST_HAND
        return TrackBlueprintItem.segmentTrackStack(segment);
    }

    private static ItemStack heldBlueprint(
            Minecraft mc) {
        ItemStack main =
                mc.player.getMainHandItem();

        if (main.getItem()
                instanceof TrackBlueprintItem) {
            return main;
        }

        ItemStack off =
                mc.player.getOffhandItem();

        return off.getItem()
                instanceof TrackBlueprintItem
                ? off
                : ItemStack.EMPTY;
    }

    private static int[] rotate(
            int x,
            int z,
            int quarter) {
        return switch (Math.floorMod(quarter, 4)) {
            case 1 -> new int[] { -z, x };
            case 2 -> new int[] { -x, -z };
            case 3 -> new int[] { z, -x };
            default -> new int[] { x, z };
        };
    }
}
