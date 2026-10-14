package traincraft.event;

// TC_DEV34_R1B2_NORMAL_BLOCK_PLACEMENT

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;
import traincraft.blueprint.BuilderStructurePlan;
import traincraft.item.TrackBlueprintItem;

/**
 * Dev34 block Blueprint editor.
 *
 * The editor behaves like normal Minecraft block placement, but writes a
 * planned ghost entry instead of a real world block. Planned ghosts are also
 * ray-pickable so they can be used as support faces for stacking.
 */
@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BuilderStructureBlueprintEditor {
    private static final double GHOST_REACH =
            6.0D;

    private BuilderStructureBlueprintEditor() {}

    private record BuildPair(
            ItemStack blueprint,
            ItemStack material,
            InteractionHand materialHand,
            BlockItem blockItem) {}

    /**
     * Ghost context is deliberately "not replacing clicked block" because the
     * clicked support is virtual/air in the actual Level. That makes
     * getClickedPos() resolve to the adjacent cell exactly like clicking a real
     * support block face.
     */
    private static final class GhostBlockPlaceContext
            extends BlockPlaceContext {
        private GhostBlockPlaceContext(
                Level level,
                Player player,
                InteractionHand hand,
                ItemStack stack,
                BlockHitResult hit) {
            super(
                    level,
                    player,
                    hand,
                    stack,
                    hit);

            this.replaceClicked = false;
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand()
                != InteractionHand.MAIN_HAND) {
            return;
        }

        Player player =
                event.getEntity();

        BuildPair pair =
                resolvePair(player);

        if (pair == null) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(
                InteractionResult.SUCCESS);

        BlockHitResult worldHit =
                event.getHitVec();

        Vec3 eye =
                player.getEyePosition();

        Vec3 toward =
                worldHit.getLocation()
                        .subtract(eye);

        Vec3 ghostRayEnd =
                toward.lengthSqr() > 1.0E-9D
                        ? worldHit.getLocation()
                                .add(
                                        toward.normalize()
                                                .scale(0.08D))
                        : worldHit.getLocation();

        BuilderStructurePlan.GhostHit ghostHit =
                BuilderStructurePlan
                        .pickGhost(
                                player,
                                pair.blueprint(),
                                ghostRayEnd);

        if (event.getLevel()
                .isClientSide) {
            return;
        }

        handleBuildClick(
                event.getLevel(),
                player,
                pair,
                worldHit,
                ghostHit);
    }

    @SubscribeEvent
    public static void onRightClickItem(
            PlayerInteractEvent.RightClickItem event) {
        if (event.getHand()
                != InteractionHand.MAIN_HAND) {
            return;
        }

        Player player =
                event.getEntity();

        BuildPair pair =
                resolvePair(player);

        if (pair == null) {
            return;
        }

        // TC_DEV34_R1B3_FREEZE_TRACK_EDITOR
        // Once an ordinary BlockItem is paired with the unlocked Blueprint,
        // right-click-air belongs to structure Build Mode too. Cancel it even
        // when no ghost is hit so TrackBlueprintItem.use() cannot silently
        // advance the selected rail/category behind the user's back.
        event.setCanceled(true);
        event.setCancellationResult(
                InteractionResult.SUCCESS);

        Vec3 eye =
                player.getEyePosition();

        Vec3 end =
                eye.add(
                        player.getViewVector(1.0F)
                                .scale(GHOST_REACH));

        BuilderStructurePlan.GhostHit ghostHit =
                BuilderStructurePlan
                        .pickGhost(
                                player,
                                pair.blueprint(),
                                end);

        if (ghostHit == null) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(
                InteractionResult.SUCCESS);

        if (event.getLevel()
                .isClientSide) {
            return;
        }

        handleBuildClick(
                event.getLevel(),
                player,
                pair,
                null,
                ghostHit);
    }

    private static BuildPair resolvePair(
            Player player) {
        // TC_DEV34_R1C2_HAND_MODE_SPLIT
        //
        // Explicit mode ownership:
        //   BLUEPRINT MAIN = TRACK EDIT MODE. Never claim the click here,
        //   even when an ordinary block happens to be in OFFHAND.
        //
        //   ordinary BLOCK MAIN + unlocked BLUEPRINT OFFHAND =
        //   STRUCTURE BLOCK BUILD MODE.
        //
        //   BLUEPRINT OFFHAND + empty/non-block/track MAIN = inactive.
        ItemStack main =
                player.getMainHandItem();

        ItemStack off =
                player.getOffhandItem();

        boolean offBlueprint =
                off.getItem()
                        instanceof TrackBlueprintItem
                        && !TrackBlueprintItem
                                .isLocked(off);

        if (!offBlueprint) {
            return null;
        }

        if (!(main.getItem()
                instanceof BlockItem blockItem)
                || TrackBlueprintItem
                        .isTrackStack(main)) {
            return null;
        }

        return new BuildPair(
                off,
                main,
                InteractionHand.MAIN_HAND,
                blockItem);
    }

    private static void handleBuildClick(
            Level level,
            Player player,
            BuildPair pair,
            BlockHitResult worldHit,
            BuilderStructurePlan.GhostHit ghostHit) {
        ItemStack blueprint =
                pair.blueprint();

        if (!BuilderStructurePlan
                .hasTrackOrigin(blueprint)) {
            player.displayClientMessage(
                    Component.literal(
                            "BUILD MODE: record at least one track first.")
                            .withStyle(
                                    ChatFormatting.GOLD),
                    true);
            return;
        }

        if (player.isShiftKeyDown()) {
            BlockPos removePos =
                    ghostHit != null
                            ? ghostHit.pos()
                            : worldHit != null
                                    ? worldHit.getBlockPos()
                                            .relative(
                                                    worldHit.getDirection())
                                    : null;

            if (removePos != null
                    && BuilderStructurePlan
                            .removeAt(
                                    blueprint,
                                    removePos)) {
                player.displayClientMessage(
                        Component.literal(
                                "Build block removed.")
                                .withStyle(
                                        ChatFormatting.YELLOW),
                        true);
            }
            else {
                player.displayClientMessage(
                        Component.literal(
                                "No planned build block here.")
                                .withStyle(
                                        ChatFormatting.GRAY),
                        true);
            }

            return;
        }

        BlockHitResult placementHit;
        BlockPlaceContext context;

        if (ghostHit != null) {
            placementHit =
                    new BlockHitResult(
                            ghostHit.location(),
                            ghostHit.face(),
                            ghostHit.pos(),
                            false);

            context =
                    new GhostBlockPlaceContext(
                            level,
                            player,
                            pair.materialHand(),
                            pair.material(),
                            placementHit);
        }
        else if (worldHit != null) {
            placementHit =
                    worldHit;

            context =
                    new BlockPlaceContext(
                            level,
                            player,
                            pair.materialHand(),
                            pair.material(),
                            placementHit);
        }
        else {
            return;
        }

        BlockPos target =
                context.getClickedPos();
        // TC_DEV34_R1C4_NO_PHASE_PLANNED_BLOCK_GUARD
        if (BuilderStructurePlan.findAt(
                blueprint,
                target) >= 0) {
            player.displayClientMessage(
                    Component.literal(
                            "BLOCKED - SNAP TO AN OPEN FACE")
                            .withStyle(
                                    ChatFormatting.RED,
                                    ChatFormatting.BOLD),
                    true);
            return;
        }

        if (BuilderStructurePlan
                .tooCloseToAnyTrack(
                        level,
                        blueprint,
                        target)) {
            player.displayClientMessage(
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
            return;
        }

        BlockState state =
                pair.blockItem()
                        .getBlock()
                        .getStateForPlacement(
                                context);

        if (state == null) {
            player.displayClientMessage(
                    Component.literal(
                            "That block cannot be oriented here.")
                            .withStyle(
                                    ChatFormatting.RED),
                    true);
            return;
        }

        if (!BuilderStructurePlan
                .upsert(
                        blueprint,
                        target,
                        state)) {
            player.displayClientMessage(
                    Component.literal(
                            "Build block could not be recorded.")
                            .withStyle(
                                    ChatFormatting.RED),
                    true);
            return;
        }

        player.displayClientMessage(
                Component.literal(
                        "Build block: ")
                        .withStyle(
                                ChatFormatting.AQUA)
                        .append(
                                pair.material()
                                        .getHoverName())
                        .append(
                                Component.literal(
                                        "  ["
                                                + BuilderStructurePlan
                                                        .count(
                                                                blueprint)
                                                + "/"
                                                + BuilderStructurePlan
                                                        .MAX_BUILD_BLOCKS
                                                + "]")
                                        .withStyle(
                                                ChatFormatting.GRAY)),
                true);
    }
}
