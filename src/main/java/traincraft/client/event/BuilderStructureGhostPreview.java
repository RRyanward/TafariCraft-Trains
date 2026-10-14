package traincraft.client.event;

// TC_DEV34_R1B2_TEXTURED_STRUCTURE_GHOST_PICKABLE

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;
import traincraft.blueprint.BuilderStructurePlan;
import traincraft.item.TrackBlueprintItem;

import java.util.List;

@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BuilderStructureGhostPreview {
    private static final Direction[] SIDES =
            Direction.values();

    private static final int FULL_LIGHT =
            LightTexture.FULL_BRIGHT;

    private static final float VALID_ALPHA =
            0.40F;

    private static final float SELECTED_ALPHA =
            0.70F;

    private static final float SAFETY_ALPHA =
            0.52F;

    private static String lastClearanceWarning =
            "";

    // TC_DEV35_R1C6J_LOCKED_PREVIEW_FREEZE
    private static final java.util.Map<String, PreviewTransform>
            TC_DEV35_LAST_LOCKED_STRUCTURE_TRANSFORM =
                    new java.util.HashMap<>();
    private BuilderStructureGhostPreview() {}

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

        ItemStack blueprint =
                heldBlueprint(mc);
if (blueprint.isEmpty()
                || !BuilderStructurePlan.hasTrackOrigin(
                        blueprint)) {
            lastClearanceWarning = "";
            return;
        }

        PreviewTransform transform =
                transform(
                        mc,
                        blueprint);

        BlockPos selectedWorld =
                selectedWorldPos(
                        mc,
                        blueprint);

        PoseStack pose =
                event.getPoseStack();

        Vec3 camera =
                event.getCamera()
                        .getPosition();

        MultiBufferSource.BufferSource buffers =
                mc.renderBuffers()
                        .bufferSource();

        String activeWarning = "";

        int count =
                BuilderStructurePlan.count(
                        blueprint);

        for (int i = 0;
             i < count;
             i++) {
            CompoundTag entry =
                    BuilderStructurePlan.entry(
                            blueprint,
                            i);

            BlockState state =
                    BuilderStructurePlan.blockState(
                            entry);

            if (state.isAir()) {
                continue;
            }

            BlockPos relative =
                    BuilderStructurePlan.relativePos(
                            entry);

            BlockPos worldPos =
                    transform.world(
                            relative);
            // TC_DEV35_R1C6_SAVED_STRUCTURE_STATE_ROTATION
            // Position and directional state must rotate together.
            state =
                    transform.state(
                            state);

            if (mc.level.getBlockState(
                    worldPos).equals(state)) {
                continue;
            }

            boolean tooClose =
                    BuilderStructurePlan.tooCloseRelative(
                            blueprint,
                            relative)
                            || BuilderStructurePlan.tooCloseToWorldTrack(
                                    mc.level,
                                    worldPos);

            boolean selected =
                    selectedWorld != null
                            && selectedWorld.equals(
                                    worldPos);

            float alpha =
                    tooClose
                            ? SAFETY_ALPHA
                            : selected
                                    ? SELECTED_ALPHA
                                    : VALID_ALPHA;

            renderTexturedBlock(
                    mc,
                    pose,
                    buffers,
                    state,
                    worldPos,
                    camera,
                    1.00F,
                    tooClose ? 0.18F : 1.00F,
                    tooClose ? 0.18F : 1.00F,
                    alpha);

            if (tooClose
                    || selected) {
                renderOutline(
                        pose,
                        buffers,
                        worldPos,
                        camera,
                        tooClose ? 1.00F : 0.25F,
                        tooClose ? 0.12F : 0.95F,
                        tooClose ? 0.12F : 1.00F,
                        tooClose ? 0.95F : 0.85F);
            }

            if (selected
                    && tooClose) {
                activeWarning =
                        "CLEAR:"
                                + worldPos.toShortString();
            }
        }

        // TC_DEV34_R1C4_LIVE_BLOCK_SNAP_NO_PHASE
        LiveCandidate live =
                liveCandidate(
                        mc,
                        blueprint);

        if (live != null) {
            boolean invalid =
                    live.tooClose()
                            || live.occupied();

            renderTexturedBlock(
                    mc,
                    pose,
                    buffers,
                    live.state(),
                    live.pos(),
                    camera,
                    1.00F,
                    invalid ? 0.12F : 1.00F,
                    invalid ? 0.12F : 1.00F,
                    invalid ? 0.62F : 0.46F);

            renderOutline(
                    pose,
                    buffers,
                    live.pos(),
                    camera,
                    invalid ? 1.00F : 0.25F,
                    invalid ? 0.08F : 0.95F,
                    invalid ? 0.08F : 1.00F,
                    invalid ? 1.00F : 0.88F);

            if (live.occupied()) {
                activeWarning =
                        "OCCUPIED:"
                                + live.pos().toShortString();
            }
            else if (live.tooClose()) {
                activeWarning =
                        "CLEAR:"
                                + live.pos().toShortString();
            }
        }
        // TC_DEV35_R1C6F_MULTI_STRUCTURE_HOTBAR
        renderAdditionalHotbarStructurePlans(
                mc,
                pose,
                buffers,
                blueprint,
                camera);

        buffers.endBatch();

        if (!activeWarning.isEmpty()
                && !activeWarning.equals(
                        lastClearanceWarning)) {
            lastClearanceWarning =
                    activeWarning;

            if (activeWarning.startsWith(
                    "OCCUPIED:")) {
                mc.player.displayClientMessage(
                        Component.literal(
                                "BLOCKED - SNAP TO AN OPEN FACE")
                                .withStyle(
                                        ChatFormatting.RED,
                                        ChatFormatting.BOLD),
                        true);
            }
            else {
                mc.player.displayClientMessage(
                        Component.literal(
                                "TOO CLOSE TO TRACK")
                                .withStyle(
                                        ChatFormatting.RED,
                                        ChatFormatting.BOLD)
                                .append(
                                        Component.literal(
                                                " - TRAIN CLEARANCE BLOCKED")
                                                .withStyle(
                                                        ChatFormatting.GOLD)),
                        true);
            }
        }
        else if (activeWarning.isEmpty()) {
            lastClearanceWarning = "";
        }
    }

    // TC_DEV34_R1C4_GHOST_FACE_SNAP_PARITY
    private static final class GhostPreviewBlockPlaceContext
            extends BlockPlaceContext {
        private GhostPreviewBlockPlaceContext(
                net.minecraft.world.level.Level level,
                net.minecraft.world.entity.player.Player player,
                InteractionHand hand,
                ItemStack stack,
                BlockHitResult hit) {
            super(level, player, hand, stack, hit);
            this.replaceClicked = false;
        }
    }
    private record LiveCandidate(BlockPos pos, BlockState state, boolean tooClose, boolean occupied) {}

    private static LiveCandidate liveCandidate(
            Minecraft mc,
            ItemStack blueprint) {
        if (mc.player == null
                || mc.level == null
                || TrackBlueprintItem.isLocked(blueprint)) {
            return null;
        }

        ItemStack main =
                mc.player.getMainHandItem();
        ItemStack off =
                mc.player.getOffhandItem();

        if (!(main.getItem()
                instanceof BlockItem blockItem)
                || TrackBlueprintItem.isTrackStack(main)
                || !(off.getItem()
                        instanceof TrackBlueprintItem)
                || TrackBlueprintItem.isLocked(off)) {
            return null;
        }

        Vec3 eye =
                mc.player.getEyePosition();

        Vec3 rayEnd;
        if (mc.hitResult
                instanceof BlockHitResult worldHit) {
            Vec3 toward =
                    worldHit.getLocation()
                            .subtract(eye);
            rayEnd =
                    toward.lengthSqr() > 1.0E-9D
                            ? worldHit.getLocation()
                                    .add(toward.normalize().scale(0.08D))
                            : worldHit.getLocation();
        }
        else {
            rayEnd =
                    eye.add(
                            mc.player.getViewVector(1.0F)
                                    .scale(6.0D));
        }

        BuilderStructurePlan.GhostHit ghostHit =
                BuilderStructurePlan.pickGhost(
                        mc.player,
                        blueprint,
                        rayEnd);

        BlockHitResult placementHit;
        BlockPlaceContext context;

        if (ghostHit != null) {
            placementHit =
                    new BlockHitResult(
                            ghostHit.location(),
                            ghostHit.face(),
                            ghostHit.pos(),
                            false);

            // Important: the support ghost is virtual/air in Level. Force
            // non-replacing semantics exactly like the server editor so the
            // candidate snaps OUTSIDE the clicked face instead of morphing
            // inside the planned block.
            context =
                    new GhostPreviewBlockPlaceContext(
                            mc.level,
                            mc.player,
                            InteractionHand.MAIN_HAND,
                            main,
                            placementHit);
        }
        else if (mc.hitResult
                instanceof BlockHitResult worldHit) {
            placementHit = worldHit;
            context =
                    new BlockPlaceContext(
                            mc.level,
                            mc.player,
                            InteractionHand.MAIN_HAND,
                            main,
                            placementHit);
        }
        else {
            return null;
        }

        BlockPos target =
                context.getClickedPos();

        BlockState state =
                blockItem.getBlock()
                        .getStateForPlacement(context);

        if (state == null) {
            return null;
        }

        boolean occupied =
                BuilderStructurePlan.findAt(
                        blueprint,
                        target) >= 0;

        boolean tooClose =
                BuilderStructurePlan.tooCloseToAnyTrack(
                        mc.level,
                        blueprint,
                        target);

        return new LiveCandidate(
                target,
                state,
                tooClose,
                occupied);
    }
    // TC_DEV35_R1C6F_MULTI_STRUCTURE_HOTBAR
    private static ItemStack heldBlueprint(
            Minecraft mc) {
        ItemStack main =
                mc.player
                        .getMainHandItem();

        if (main.getItem()
                instanceof TrackBlueprintItem) {
            return main;
        }

        ItemStack off =
                mc.player
                        .getOffhandItem();

        if (off.getItem()
                instanceof TrackBlueprintItem) {
            return off;
        }

        // Mirror TrackBlueprintGhostPreview: a saved Blueprint in the hotbar
        // remains a visible plan even when another hotbar slot is selected.
        for (int slot = 0;
             slot < 9;
             slot++) {
            ItemStack candidate =
                    mc.player.getInventory()
                            .getItem(slot);

            if (candidate.getItem()
                    instanceof TrackBlueprintItem) {
                return candidate;
            }
        }

        return ItemStack.EMPTY;
    }

    // TC_DEV35_R1C6_SAVED_STRUCTURE_LIVE_TRANSFORM
    // TC_DEV35_R1C6_SAVED_STRUCTURE_LIVE_TRANSFORM
    // TC_DEV35_R1C6_SAVED_STRUCTURE_LIVE_TRANSFORM
    // TC_DEV35_R1C6J_LOCKED_PREVIEW_FREEZE
    private static PreviewTransform transform(
            Minecraft mc,
            ItemStack blueprint) {
        boolean activeHand =
                sameBlueprint(
                        blueprint,
                        mc == null || mc.player == null
                                ? ItemStack.EMPTY
                                : mc.player.getMainHandItem())
                || sameBlueprint(
                        blueprint,
                        mc == null || mc.player == null
                                ? ItemStack.EMPTY
                                : mc.player.getOffhandItem());

        String id =
                blueprint.getItem()
                        instanceof TrackBlueprintItem
                                ? TrackBlueprintItem.blueprintId(
                                        blueprint)
                                : "";

        if (TrackBlueprintItem.isLocked(
                blueprint)) {
            if (TrackBlueprintItem.hasPreviewAnchor(
                    blueprint)) {
                PreviewTransform committed =
                        new PreviewTransform(
                                TrackBlueprintItem.previewAnchor(
                                        blueprint),
                                TrackBlueprintItem.previewRotationQuarter(
                                        blueprint));

                if (!id.isEmpty()) {
                    TC_DEV35_LAST_LOCKED_STRUCTURE_TRANSFORM.put(
                            id,
                            committed);
                }

                return committed;
            }

            if (activeHand
                    && mc != null
                    && mc.player != null
                    && mc.hitResult
                            instanceof BlockHitResult hit) {
                PreviewTransform live =
                        new PreviewTransform(
                                hit.getBlockPos()
                                        .relative(
                                                hit.getDirection()),
                                TrackBlueprintItem.previewRotationQuarter(
                                        blueprint,
                                        mc.player.getYRot()));

                if (!id.isEmpty()) {
                    TC_DEV35_LAST_LOCKED_STRUCTURE_TRANSFORM.put(
                            id,
                            live);
                }

                return live;
            }

            if (!id.isEmpty()) {
                PreviewTransform remembered =
                        TC_DEV35_LAST_LOCKED_STRUCTURE_TRANSFORM.get(
                                id);

                if (remembered != null) {
                    return remembered;
                }
            }
        }

        if (TrackBlueprintItem.hasOrigin(
                blueprint)) {
            return new PreviewTransform(
                    TrackBlueprintItem.origin(
                            blueprint),
                    0);
        }

        return null;
    }

    // TC_DEV35_R1C6F_MULTI_STRUCTURE_HOTBAR
    private static boolean sameBlueprint(
            ItemStack first,
            ItemStack second) {
        if (!(first.getItem()
                instanceof TrackBlueprintItem)
                || !(second.getItem()
                        instanceof TrackBlueprintItem)) {
            return false;
        }

        String firstId =
                TrackBlueprintItem.blueprintId(
                        first);
        String secondId =
                TrackBlueprintItem.blueprintId(
                        second);

        return !firstId.isEmpty()
                && firstId.equals(
                        secondId);
    }

    private static void renderSavedStructurePlan(
            Minecraft mc,
            PoseStack pose,
            MultiBufferSource.BufferSource buffers,
            ItemStack blueprint,
            Vec3 camera) {
        if (!(blueprint.getItem()
                instanceof TrackBlueprintItem)
                || !BuilderStructurePlan.hasTrackOrigin(
                        blueprint)) {
            return;
        }

        int count =
                BuilderStructurePlan.count(
                        blueprint);

        if (count <= 0) {
            return;
        }

        PreviewTransform transform =
                transform(
                        mc,
                        blueprint);

        if (transform == null) {
            return;
        }

        for (int i = 0;
             i < count;
             i++) {
            CompoundTag entry =
                    BuilderStructurePlan.entry(
                            blueprint,
                            i);

            BlockState state =
                    BuilderStructurePlan.blockState(
                            entry);

            if (state.isAir()) {
                continue;
            }

            BlockPos relative =
                    BuilderStructurePlan.relativePos(
                            entry);

            BlockPos worldPos =
                    transform.world(
                            relative);

            state =
                    transform.state(
                            state);

            if (mc.level.getBlockState(
                    worldPos).equals(
                            state)) {
                continue;
            }

            boolean tooClose =
                    BuilderStructurePlan.tooCloseRelative(
                            blueprint,
                            relative);

            renderTexturedBlock(
                    mc,
                    pose,
                    buffers,
                    state,
                    worldPos,
                    camera,
                    1.00F,
                    tooClose ? 0.18F : 1.00F,
                    tooClose ? 0.18F : 1.00F,
                    tooClose ? SAFETY_ALPHA : VALID_ALPHA);

            if (tooClose) {
                renderOutline(
                        pose,
                        buffers,
                        worldPos,
                        camera,
                        1.00F,
                        0.12F,
                        0.12F,
                        0.95F);
            }
        }
    }

    private static void renderAdditionalHotbarStructurePlans(
            Minecraft mc,
            PoseStack pose,
            MultiBufferSource.BufferSource buffers,
            ItemStack activeBlueprint,
            Vec3 camera) {
        for (int slot = 0;
             slot < 9;
             slot++) {
            ItemStack candidate =
                    mc.player.getInventory()
                            .getItem(slot);

            if (!(candidate.getItem()
                    instanceof TrackBlueprintItem)
                    || sameBlueprint(
                            candidate,
                            activeBlueprint)) {
                continue;
            }

            renderSavedStructurePlan(
                    mc,
                    pose,
                    buffers,
                    candidate,
                    camera);
        }

        ItemStack off =
                mc.player.getOffhandItem();

        if (off.getItem()
                instanceof TrackBlueprintItem
                && !sameBlueprint(
                        off,
                        activeBlueprint)) {
            renderSavedStructurePlan(
                    mc,
                    pose,
                    buffers,
                    off,
                    camera);
        }
    }
    private static BlockPos selectedWorldPos(
            Minecraft mc,
            ItemStack blueprint) {
        // Editing uses the real stored/origin cells. Locked rotated previews
        // remain display-only and use normal world hit highlighting.
        if (!TrackBlueprintItem
                .isLocked(
                        blueprint)) {
            Vec3 eye =
                    mc.player
                            .getEyePosition();

            Vec3 end;

            if (mc.hitResult
                    instanceof BlockHitResult hit) {
                Vec3 toward =
                        hit.getLocation()
                                .subtract(eye);

                end =
                        toward.lengthSqr()
                                        > 1.0E-9D
                                ? hit.getLocation()
                                        .add(
                                                toward.normalize()
                                                        .scale(0.08D))
                                : hit.getLocation();
            }
            else {
                end =
                        eye.add(
                                mc.player
                                        .getViewVector(1.0F)
                                        .scale(6.0D));
            }

            BuilderStructurePlan.GhostHit ghostHit =
                    BuilderStructurePlan
                            .pickGhost(
                                    mc.player,
                                    blueprint,
                                    end);

            if (ghostHit != null) {
                return ghostHit.pos();
            }
        }

        if (!(mc.hitResult
                instanceof BlockHitResult hit)) {
            return null;
        }

        return hit.getBlockPos()
                .relative(
                        hit.getDirection());
    }

    private static void renderTexturedBlock(
            Minecraft mc,
            PoseStack pose,
            MultiBufferSource buffers,
            BlockState state,
            BlockPos worldPos,
            Vec3 camera,
            float red,
            float green,
            float blue,
            float alpha) {
        BakedModel model =
                mc.getBlockRenderer()
                        .getBlockModel(
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
                        RenderType.translucent());

        pose.pushPose();

        pose.translate(
                worldPos.getX()
                        - camera.x,
                worldPos.getY()
                        - camera.y
                        + 0.002D,
                worldPos.getZ()
                        - camera.z);

        RandomSource random =
                RandomSource.create();

        random.setSeed(42L);

        emit(
                pose,
                consumer,
                model.getQuads(
                        state,
                        null,
                        random,
                        modelData,
                        null),
                red,
                green,
                blue,
                alpha);

        for (Direction side : SIDES) {
            random.setSeed(42L);

            emit(
                    pose,
                    consumer,
                    model.getQuads(
                            state,
                            side,
                            random,
                            modelData,
                            null),
                    red,
                    green,
                    blue,
                    alpha);
        }

        pose.popPose();
    }

    private static void emit(
            PoseStack pose,
            VertexConsumer consumer,
            List<BakedQuad> quads,
            float red,
            float green,
            float blue,
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
                    red,
                    green,
                    blue,
                    alpha,
                    light,
                    OverlayTexture.NO_OVERLAY,
                    false);
        }
    }

    private static void renderOutline(
            PoseStack pose,
            MultiBufferSource buffers,
            BlockPos worldPos,
            Vec3 camera,
            float red,
            float green,
            float blue,
            float alpha) {
        VertexConsumer lines =
                buffers.getBuffer(
                        RenderType.lines());

        AABB box =
                new AABB(
                        worldPos.getX()
                                + 0.02D
                                - camera.x,
                        worldPos.getY()
                                + 0.02D
                                - camera.y,
                        worldPos.getZ()
                                + 0.02D
                                - camera.z,
                        worldPos.getX()
                                + 0.98D
                                - camera.x,
                        worldPos.getY()
                                + 0.98D
                                - camera.y,
                        worldPos.getZ()
                                + 0.98D
                                - camera.z);

        LevelRenderer.renderLineBox(
                pose,
                lines,
                box,
                red,
                green,
                blue,
                alpha);
    }

    private record PreviewTransform(
            BlockPos anchor,
            int rotation) {
        BlockState state(
                BlockState source) {
            return switch (Math.floorMod(
                    rotation,
                    4)) {
                case 1 -> source.rotate(
                        net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
                case 2 -> source.rotate(
                        net.minecraft.world.level.block.Rotation.CLOCKWISE_180);
                case 3 -> source.rotate(
                        net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90);
                default -> source;
            };
        }
        BlockPos world(
                BlockPos relative) {
            int x =
                    relative.getX();
            int z =
                    relative.getZ();

            int rx;
            int rz;

            switch (Math.floorMod(
                    rotation,
                    4)) {
                case 1 -> {
                    rx = -z;
                    rz = x;
                }
                case 2 -> {
                    rx = -x;
                    rz = -z;
                }
                case 3 -> {
                    rx = z;
                    rz = -x;
                }
                default -> {
                    rx = x;
                    rz = z;
                }
            }

            return anchor.offset(
                    rx,
                    relative.getY(),
                    rz);
        }
    }
}
