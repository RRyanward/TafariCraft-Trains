package traincraft.dev;

import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.block.integration.CargoInterfaceBlockEntity;
import traincraft.entity.train.freight.FreightCart;
import traincraft.registry.TCBlocks;
import traincraft.registry.TCEntities;

/**
 * DEV38 r1f2g TEMPORARY external-cargo I/O proof harness.
 *
 * GOLD:
 *   Freight -> Unload Track -> TC Cargo Interface -> Hopper -> Chest
 *
 * PURPLE:
 *   Freight -> Unload Track -> TC Cargo Interface -> Hopper injector
 *   -> Mekanism Basic Logistical Transporter chain -> Chest
 *
 * No production cargo code is changed by this class.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev38R1f2dIntegrationTestCommand {
    private static final String ROLE = "TCDev38R1f2gRole";
    private static final int SCAN = 32;

    private Dev38R1f2dIntegrationTestCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc_r1f2g_test")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> help(ctx.getSource()))
                        .then(Commands.literal("course")
                                .executes(ctx -> course(ctx.getSource())))
                        .then(Commands.literal("io_course")
                                .executes(ctx -> course(ctx.getSource())))
                        .then(Commands.literal("status")
                                .executes(ctx -> status(ctx.getSource())))
                        .then(Commands.literal("io_status")
                                .executes(ctx -> status(ctx.getSource()))));
    }

    private static int help(CommandSourceStack source) {
        msg(source, "[TC r1f2g] External cargo I/O proof.");
        msg(source, "/tc_r1f2g_test course");
        msg(source, "  GOLD   = Cargo Interface -> Hopper -> Chest");
        msg(source, "  PURPLE = Cargo Interface -> Hopper -> Mekanism pipe -> Chest");
        msg(source, "Wait about 10-15 seconds, then /tc_r1f2g_test status.");
        return 1;
    }

    private static int course(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        Direction forward = player.getDirection();
        Direction right = forward.getClockWise();
        RailShape straight = forward.getAxis() == Direction.Axis.X
                ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;

        BlockPos origin = player.blockPosition()
                .relative(forward, 4)
                .relative(right, -4);

        // Two lanes with a three-block-deep utility trench.
        for (int f = -2; f <= 14; f++) {
            for (int r = -2; r <= 13; r++) {
                BlockPos base = lanePos(origin, forward, right, f, r, -3);
                level.setBlock(base, Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                for (int dy = -2; dy <= 4; dy++) {
                    level.setBlock(
                            lanePos(origin, forward, right, f, r, dy),
                            Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        // --------------------------------------------------------------
        // GOLD lane: Cargo Interface -> Hopper -> Chest
        // --------------------------------------------------------------
        int hopperLane = 0;
        for (int f = 0; f <= 10; f++) {
            BlockPos rail = lanePos(origin, forward, right, f, hopperLane, 0);
            BlockPos support = rail.below();
            level.setBlock(support, Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            placeNormalTrack(level, rail, straight);
        }

        BlockPos hopperRail = lanePos(origin, forward, right, 5, hopperLane, 0);
        BlockPos hopperIf = hopperRail.below();
        BlockPos hopper = hopperIf.below();
        BlockPos hopperChest = hopper.relative(right);

        level.setBlock(hopperIf, TCBlocks.CARGO_INTERFACE.get().defaultBlockState(), 3);
        level.setBlock(hopperRail,
                TCBlocks.CARGO_UNLOAD_TRACK.get().defaultBlockState()
                        .setValue(RailBlock.SHAPE, straight), 3);

        level.setBlock(hopper,
                Blocks.HOPPER.defaultBlockState()
                        .setValue(HopperBlock.FACING, right)
                        .setValue(HopperBlock.ENABLED, true), 3);
        level.setBlock(hopperChest, Blocks.CHEST.defaultBlockState(), 3);

        FreightCart hopperCart = TCEntities.FREIGHT_CART.get().create(level);
        if (hopperCart != null) {
            hopperCart.getPersistentData().putString(ROLE, "hopper");
            ItemHandlerHelper.insertItemStacked(
                    hopperCart.getCargoInventory(),
                    new ItemStack(Items.EMERALD, 16), false);
            spawn(level, hopperCart, hopperRail, forward);
        }

        level.setBlock(
                lanePos(origin, forward, right, -1, hopperLane, 0),
                Blocks.YELLOW_CONCRETE.defaultBlockState(), 3);

        // --------------------------------------------------------------
        // PURPLE lane: Cargo Interface -> Hopper injector -> Mek pipe -> Chest
        // --------------------------------------------------------------
        int pipeLane = 7;
        for (int f = 0; f <= 10; f++) {
            BlockPos rail = lanePos(origin, forward, right, f, pipeLane, 0);
            BlockPos support = rail.below();
            level.setBlock(support, Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            placeNormalTrack(level, rail, straight);
        }

        BlockPos pipeRail = lanePos(origin, forward, right, 5, pipeLane, 0);
        BlockPos pipeIf = pipeRail.below();
        BlockPos injector = pipeIf.below();

        level.setBlock(pipeIf, TCBlocks.CARGO_INTERFACE.get().defaultBlockState(), 3);
        level.setBlock(pipeRail,
                TCBlocks.CARGO_UNLOAD_TRACK.get().defaultBlockState()
                        .setValue(RailBlock.SHAPE, straight), 3);

        level.setBlock(injector,
                Blocks.HOPPER.defaultBlockState()
                        .setValue(HopperBlock.FACING, right)
                        .setValue(HopperBlock.ENABLED, true), 3);

        Block pipeBlock = ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation("mekanism", "basic_logistical_transporter"));

        if (pipeBlock == null || pipeBlock == Blocks.AIR) {
            msg(source, "[TC r1f2g] ERROR: mekanism:basic_logistical_transporter is missing.");
        } else {
            BlockPos p1 = injector.relative(right);
            BlockPos p2 = p1.relative(right);
            BlockPos p3 = p2.relative(right);
            BlockPos p4 = p3.above();
            BlockPos pipeChest = p4.relative(right);

            level.setBlock(p1, pipeBlock.defaultBlockState(), 3);
            level.setBlock(p2, pipeBlock.defaultBlockState(), 3);
            level.setBlock(p3, pipeBlock.defaultBlockState(), 3);
            level.setBlock(p4, pipeBlock.defaultBlockState(), 3);
            level.setBlock(pipeChest, Blocks.CHEST.defaultBlockState(), 3);
        }

        FreightCart pipeCart = TCEntities.FREIGHT_CART.get().create(level);
        if (pipeCart != null) {
            pipeCart.getPersistentData().putString(ROLE, "pipe");
            ItemHandlerHelper.insertItemStacked(
                    pipeCart.getCargoInventory(),
                    new ItemStack(Items.DIAMOND, 16), false);
            spawn(level, pipeCart, pipeRail, forward);
        }

        level.setBlock(
                lanePos(origin, forward, right, -1, pipeLane, 0),
                Blocks.PURPLE_CONCRETE.defaultBlockState(), 3);

        msg(source, "[TC r1f2g] EXTERNAL CARGO COURSE BUILT.");
        msg(source, "[TC r1f2g] GOLD: 16 emeralds -> Cargo IF -> Hopper -> Chest.");
        msg(source, "[TC r1f2g] PURPLE: 16 diamonds -> Cargo IF -> Hopper -> Mek pipe -> Chest.");
        msg(source, "[TC r1f2g] Wait 10-15 seconds, then /tc_r1f2g_test status.");
        return 1;
    }

    private static void placeNormalTrack(
            ServerLevel level, BlockPos pos, RailShape shape) {
        level.setBlock(
                pos,
                TCBlocks.TRACK_NORMAL.get().defaultBlockState()
                        .setValue(RailBlock.SHAPE, shape),
                3);
    }

    private static void spawn(
            ServerLevel level,
            Entity entity,
            BlockPos railPos,
            Direction forward) {
        if (entity == null) return;
        entity.moveTo(
                railPos.getX() + 0.5D,
                railPos.getY() + 0.0625D,
                railPos.getZ() + 0.5D,
                forward.toYRot(),
                0.0F);
        level.addFreshEntity(entity);
    }

    private static BlockPos lanePos(
            BlockPos origin,
            Direction forward,
            Direction right,
            int f,
            int r,
            int dy) {
        return origin.relative(forward, f)
                .relative(right, r)
                .offset(0, dy, 0);
    }

    private static int status(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        BlockPos center = player.blockPosition();

        AABB area = new AABB(
                center.getX() - SCAN,
                center.getY() - 8,
                center.getZ() - SCAN,
                center.getX() + SCAN + 1,
                center.getY() + 9,
                center.getZ() + SCAN + 1);

        int hopperCart = -1;
        int pipeCart = -1;

        List<FreightCart> carts =
                level.getEntitiesOfClass(FreightCart.class, area);

        for (FreightCart cart : carts) {
            String role = cart.getPersistentData().getString(ROLE);
            if ("hopper".equals(role)) {
                hopperCart = countItem(cart.getCargoInventory(), Items.EMERALD);
            } else if ("pipe".equals(role)) {
                pipeCart = countItem(cart.getCargoInventory(), Items.DIAMOND);
            }
        }

        int emeraldChest = 0;
        int diamondChest = 0;
        int emeraldInterfaces = 0;
        int diamondInterfaces = 0;
        int emeraldHoppers = 0;
        int diamondHoppers = 0;
        int pipeBlocks = 0;

        Block mekanismPipe = ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation("mekanism", "basic_logistical_transporter"));

        for (int dx = -SCAN; dx <= SCAN; dx++) {
            for (int dy = -8; dy <= 8; dy++) {
                for (int dz = -SCAN; dz <= SCAN; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    BlockEntity be = level.getBlockEntity(pos);

                    if (state.is(Blocks.CHEST) && be instanceof Container c) {
                        emeraldChest += countContainer(c, Items.EMERALD);
                        diamondChest += countContainer(c, Items.DIAMOND);
                    }

                    if (state.is(Blocks.HOPPER) && be instanceof Container c) {
                        emeraldHoppers += countContainer(c, Items.EMERALD);
                        diamondHoppers += countContainer(c, Items.DIAMOND);
                    }

                    if (be instanceof CargoInterfaceBlockEntity cargo) {
                        emeraldInterfaces += countItem(cargo.getItems(), Items.EMERALD);
                        diamondInterfaces += countItem(cargo.getItems(), Items.DIAMOND);
                    }

                    if (mekanismPipe != null && state.is(mekanismPipe)) {
                        pipeBlocks++;
                    }
                }
            }
        }

        boolean hopperProof =
                hopperCart >= 0
                && hopperCart < 16
                && emeraldChest > 0;

        boolean pipeProof =
                pipeCart >= 0
                && pipeCart < 16
                && diamondChest > 0
                && pipeBlocks >= 4;

        msg(source, "[TC r1f2g] HOPPER lane:"
                + " cartEmeralds=" + hopperCart
                + " interface=" + emeraldInterfaces
                + " hopper=" + emeraldHoppers
                + " chest=" + emeraldChest
                + " movedToChest=" + hopperProof);

        msg(source, "[TC r1f2g] PIPE lane:"
                + " cartDiamonds=" + pipeCart
                + " interface=" + diamondInterfaces
                + " injectorHopper=" + diamondHoppers
                + " pipeBlocks=" + pipeBlocks
                + " chest=" + diamondChest
                + " movedThroughPipeToChest=" + pipeProof);

        if (hopperProof && pipeProof) {
            msg(source,
                    "[TC r1f2g] EXTERNAL CARGO I/O PASS: Hopper->Chest and Mekanism Pipe->Chest both received train cargo.");
            return 1;
        }

        msg(source,
                "[TC r1f2g] NOT COMPLETE YET. If carts/interfaces/hoppers still contain items, wait a few seconds and run status again.");
        return 0;
    }

    private static int countItem(IItemHandler handler, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static int countContainer(Container container, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static void msg(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }
}
