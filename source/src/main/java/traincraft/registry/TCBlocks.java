/*
 * Traincraft
 * Copyright (c) 2011-2020.
 *
 * Minecraft 1.20.1 port registry layer.
 * Original project: https://github.com/Traincraft/Traincraft
 * Distributed under LGPL-v3.0.
 */
package traincraft.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import traincraft.Traincraft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Modern 1.20.1 block registry.
 *
 * Machine blocks are deliberately registered as safe base blocks in this phase.
 * Their block entities, menus and machine behavior are ported in later phases.
 */
public final class TCBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Traincraft.MOD_ID);

    /** Block items are kept here so the creative tab can enumerate them. */
    public static final Map<String, RegistryObject<Item>> BLOCK_ITEMS = new LinkedHashMap<>();

    public static final RegistryObject<Block> DISTILLERY = register("distillery", TCBlocks::machineBlock);
    public static final RegistryObject<Block> ASSEMBLY_TABLE_I = register("assembly_table_1", TCBlocks::machineBlock);
    public static final RegistryObject<Block> ASSEMBLY_TABLE_II = register("assembly_table_2", TCBlocks::machineBlock);
    public static final RegistryObject<Block> ASSEMBLY_TABLE_III = register("assembly_table_3", TCBlocks::machineBlock);
    public static final RegistryObject<Block> TRAIN_WORKBENCH = register("train_workbench", TCBlocks::machineBlock);
    public static final RegistryObject<Block> OPEN_HEARTH_FURNACE = register("hearth_furnace", TCBlocks::machineBlock);

    public static final RegistryObject<Block> WATER_WHEEL = register("water_wheel", TCBlocks::woodMachineBlock);
    public static final RegistryObject<Block> WIND_MILL = register("wind_mill", TCBlocks::woodMachineBlock);
    public static final RegistryObject<Block> GENERATOR_DIESEL = register("generator_diesel", TCBlocks::machineBlock);
    public static final RegistryObject<Block> BATTERY = register("battery", TCBlocks::machineBlock);

    public static final RegistryObject<Block> STOPPER = register("stopper", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.7F).sound(SoundType.METAL)));
    public static final RegistryObject<Block> BRIDGE_PILLAR = register("bridge_pillar", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(3.5F).sound(SoundType.WOOD)));
    public static final RegistryObject<Block> LANTERN = register("lantern", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.7F).sound(SoundType.METAL)
                    .lightLevel(state -> 15)));
    public static final RegistryObject<Block> BALLAST = register("ballast", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0F, 15.0F)
                    .requiresCorrectToolForDrops().sound(SoundType.STONE)));

    /**
     * Step 9.1 - first functional Traincraft track foundation.
     *
     * Uses vanilla RailBlock connectivity, curves, slopes and minecart path
     * resolution so the frozen Traincraft rolling-stock physics can recognize
     * it immediately through BaseRailBlock. Legacy 3D OBJ rendering is ported
     * separately after the rail behavior is runtime-confirmed.
     */
    public static final RegistryObject<Block> TRACK_NORMAL = register("track_normal",
            () -> new net.minecraft.world.level.block.RailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL)));

    public static final RegistryObject<Block> OIL_SAND = register("ore_oil_sand", () -> new FallingBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(3.0F).sound(SoundType.SAND)));
    public static final RegistryObject<Block> PETROL_ORE = register("petrol_ore", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0F, 5.0F)
                    .requiresCorrectToolForDrops().sound(SoundType.STONE)));
    public static final RegistryObject<Block> COPPER_ORE = register("copper_ore", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0F, 5.0F)
                    .requiresCorrectToolForDrops().sound(SoundType.STONE)));

    private TCBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        TCItems.ITEMS.register(modBus);
    }

    private static RegistryObject<Block> register(String name, Supplier<? extends Block> supplier) {
        RegistryObject<Block> block = BLOCKS.register(name, supplier);
        RegistryObject<Item> item = TCItems.ITEMS.register(name,
                () -> new BlockItem(block.get(), new Item.Properties()));
        BLOCK_ITEMS.put(name, item);
        return block;
    }

    private static Block machineBlock() {
        return new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                .strength(3.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL));
    }

    private static Block woodMachineBlock() {
        return new Block(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                .strength(3.5F).sound(SoundType.WOOD));
    }

    // Step 9.2a-r2 - internal RailBlock segments for legacy straight assemblies.
    // Intentionally registered without BlockItems; the player-facing items live in TCItems.
    public static final RegistryObject<Block> TRACK_MEDIUM_STRAIGHT_SEGMENT = BLOCKS.register("track_medium_straight_segment",
            () -> new traincraft.block.track.LegacyStraightAssemblyRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.RAIL),
                    3, "traincraft:track_medium_straight"));
    public static final RegistryObject<Block> TRACK_LONG_STRAIGHT_SEGMENT = BLOCKS.register("track_long_straight_segment",
            () -> new traincraft.block.track.LegacyStraightAssemblyRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.RAIL),
                    6, "traincraft:track_long_straight"));
    public static final RegistryObject<Block> TRACK_VERY_LONG_STRAIGHT_SEGMENT = BLOCKS.register("track_very_long_straight_segment",
            () -> new traincraft.block.track.LegacyStraightAssemblyRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.RAIL),
                    12, "traincraft:track_very_long_straight"));

    // Step 9.2b - internal guide segments for the original medium multi-block curve.
    public static final RegistryObject<Block> TRACK_MEDIUM_CURVE_SEGMENT = BLOCKS.register("track_medium_curve_segment",
            () -> new traincraft.block.track.LegacyMediumCurveRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.RAIL)));

    // Step 9.3a - batched original Traincraft dedicated track families.
    // Internal guide blocks have no BlockItems; player-facing placement items live in TCItems.
    public static final RegistryObject<Block> CURVE_BIG_SEGMENT = BLOCKS.register("track_curve_big_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_BIG,
                    "traincraft:track_curve_big"));
    public static final RegistryObject<Block> CURVE_VERY_BIG_SEGMENT = BLOCKS.register("track_curve_very_big_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_VERY_BIG,
                    "traincraft:track_curve_very_big"));
    public static final RegistryObject<Block> CURVE_SUPER_BIG_SEGMENT = BLOCKS.register("track_curve_super_big_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_SUPER_BIG,
                    "traincraft:track_curve_super_big"));
    public static final RegistryObject<Block> CURVE_29X_SEGMENT = BLOCKS.register("track_curve_29x_segment",
            () -> new traincraft.block.track.LegacyLongBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_29X,
                    "traincraft:track_curve_29x"));
    public static final RegistryObject<Block> CURVE_32X_SEGMENT = BLOCKS.register("track_curve_32x_segment",
            () -> new traincraft.block.track.LegacyLongBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_32X,
                    "traincraft:track_curve_32x"));
    public static final RegistryObject<Block> CURVE_45_MEDIUM_RIGHT_SEGMENT = BLOCKS.register("track_curve_45_medium_right_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_45_MEDIUM_RIGHT,
                    "traincraft:track_curve_45_medium_right"));
    public static final RegistryObject<Block> CURVE_45_MEDIUM_LEFT_SEGMENT = BLOCKS.register("track_curve_45_medium_left_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_45_MEDIUM_LEFT,
                    "traincraft:track_curve_45_medium_left"));
    public static final RegistryObject<Block> CURVE_45_LARGE_RIGHT_SEGMENT = BLOCKS.register("track_curve_45_large_right_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_45_LARGE_RIGHT,
                    "traincraft:track_curve_45_large_right"));
    public static final RegistryObject<Block> CURVE_45_LARGE_LEFT_SEGMENT = BLOCKS.register("track_curve_45_large_left_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_45_LARGE_LEFT,
                    "traincraft:track_curve_45_large_left"));
    public static final RegistryObject<Block> CURVE_45_VERY_LARGE_RIGHT_SEGMENT = BLOCKS.register("track_curve_45_very_large_right_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_45_VERY_LARGE_RIGHT,
                    "traincraft:track_curve_45_very_large_right"));
    public static final RegistryObject<Block> CURVE_45_VERY_LARGE_LEFT_SEGMENT = BLOCKS.register("track_curve_45_very_large_left_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_45_VERY_LARGE_LEFT,
                    "traincraft:track_curve_45_very_large_left"));
    public static final RegistryObject<Block> CURVE_45_SUPER_LARGE_RIGHT_SEGMENT = BLOCKS.register("track_curve_45_super_large_right_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_45_SUPER_LARGE_RIGHT,
                    "traincraft:track_curve_45_super_large_right"));
    public static final RegistryObject<Block> CURVE_45_SUPER_LARGE_LEFT_SEGMENT = BLOCKS.register("track_curve_45_super_large_left_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.CURVE_45_SUPER_LARGE_LEFT,
                    "traincraft:track_curve_45_super_large_left"));
    public static final RegistryObject<Block> PARALLEL_CURVE_SMALL_RIGHT_SEGMENT = BLOCKS.register("track_parallel_curve_small_right_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.PARALLEL_CURVE_SMALL_RIGHT,
                    "traincraft:track_parallel_curve_small_right"));
    public static final RegistryObject<Block> PARALLEL_CURVE_SMALL_LEFT_SEGMENT = BLOCKS.register("track_parallel_curve_small_left_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.PARALLEL_CURVE_SMALL_LEFT,
                    "traincraft:track_parallel_curve_small_left"));
    public static final RegistryObject<Block> PARALLEL_CURVE_MEDIUM_RIGHT_SEGMENT = BLOCKS.register("track_parallel_curve_medium_right_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.PARALLEL_CURVE_MEDIUM_RIGHT,
                    "traincraft:track_parallel_curve_medium_right"));
    public static final RegistryObject<Block> PARALLEL_CURVE_MEDIUM_LEFT_SEGMENT = BLOCKS.register("track_parallel_curve_medium_left_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.PARALLEL_CURVE_MEDIUM_LEFT,
                    "traincraft:track_parallel_curve_medium_left"));
    public static final RegistryObject<Block> PARALLEL_CURVE_LARGE_RIGHT_SEGMENT = BLOCKS.register("track_parallel_curve_large_right_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.PARALLEL_CURVE_LARGE_RIGHT,
                    "traincraft:track_parallel_curve_large_right"));
    public static final RegistryObject<Block> PARALLEL_CURVE_LARGE_LEFT_SEGMENT = BLOCKS.register("track_parallel_curve_large_left_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.PARALLEL_CURVE_LARGE_LEFT,
                    "traincraft:track_parallel_curve_large_left"));
    public static final RegistryObject<Block> SLOPE_SMALL_SEGMENT = BLOCKS.register("track_slope_small_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SLOPE_SMALL,
                    "traincraft:track_slope_small"));
    public static final RegistryObject<Block> SLOPE_LONG_SEGMENT = BLOCKS.register("track_slope_long_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SLOPE_LONG,
                    "traincraft:track_slope_long"));
    public static final RegistryObject<Block> SLOPE_VERY_LONG_SEGMENT = BLOCKS.register("track_slope_very_long_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SLOPE_VERY_LONG,
                    "traincraft:track_slope_very_long"));
    public static final RegistryObject<Block> SLOPE_CURVE_LARGE_RIGHT_SEGMENT = BLOCKS.register("track_slope_curve_large_right_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SLOPE_CURVE_LARGE_RIGHT,
                    "traincraft:track_slope_curve_large_right"));
    public static final RegistryObject<Block> SLOPE_CURVE_LARGE_LEFT_SEGMENT = BLOCKS.register("track_slope_curve_large_left_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SLOPE_CURVE_LARGE_LEFT,
                    "traincraft:track_slope_curve_large_left"));
    public static final RegistryObject<Block> SLOPE_CURVE_VERY_LARGE_RIGHT_SEGMENT = BLOCKS.register("track_slope_curve_very_large_right_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SLOPE_CURVE_VERY_LARGE_RIGHT,
                    "traincraft:track_slope_curve_very_large_right"));
    public static final RegistryObject<Block> SLOPE_CURVE_VERY_LARGE_LEFT_SEGMENT = BLOCKS.register("track_slope_curve_very_large_left_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SLOPE_CURVE_VERY_LARGE_LEFT,
                    "traincraft:track_slope_curve_very_large_left"));
    public static final RegistryObject<Block> SLOPE_CURVE_SUPER_LARGE_RIGHT_SEGMENT = BLOCKS.register("track_slope_curve_super_large_right_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SLOPE_CURVE_SUPER_LARGE_RIGHT,
                    "traincraft:track_slope_curve_super_large_right"));
    public static final RegistryObject<Block> SLOPE_CURVE_SUPER_LARGE_LEFT_SEGMENT = BLOCKS.register("track_slope_curve_super_large_left_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SLOPE_CURVE_SUPER_LARGE_LEFT,
                    "traincraft:track_slope_curve_super_large_left"));
    public static final RegistryObject<Block> CROSSING_X_SEGMENT = BLOCKS.register("track_crossing_x_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.CROSSING_X,
                    "traincraft:track_crossing_x"));
    public static final RegistryObject<Block> DIAMOND_CROSSING_SEGMENT = BLOCKS.register("track_diamond_crossing_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.DIAMOND_CROSSING,
                    "traincraft:track_diamond_crossing"));
    public static final RegistryObject<Block> DIAMOND_CROSSING_LEFT_SEGMENT = BLOCKS.register("track_diamond_crossing_left_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.DIAMOND_CROSSING_LEFT,
                    "traincraft:track_diamond_crossing_left"));
    public static final RegistryObject<Block> DOUBLE_DIAMOND_CROSSING_SEGMENT = BLOCKS.register("track_double_diamond_crossing_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.DOUBLE_DIAMOND_CROSSING,
                    "traincraft:track_double_diamond_crossing"));
    public static final RegistryObject<Block> DIAGONAL_CROSSING_SEGMENT = BLOCKS.register("track_diagonal_crossing_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.DIAGONAL_CROSSING,
                    "traincraft:track_diagonal_crossing"));
    public static final RegistryObject<Block> DIAGONAL_TWO_WAYS_CROSSING_SEGMENT = BLOCKS.register("track_diagonal_two_ways_crossing_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.DIAGONAL_TWO_WAYS_CROSSING,
                    "traincraft:track_diagonal_two_ways_crossing"));
    public static final RegistryObject<Block> DIAGONAL_FOUR_WAYS_CROSSING_SEGMENT = BLOCKS.register("track_diagonal_four_ways_crossing_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.DIAGONAL_FOUR_WAYS_CROSSING,
                    "traincraft:track_diagonal_four_ways_crossing"));
    public static final RegistryObject<Block> UNIVERSAL_CROSSING_SEGMENT = BLOCKS.register("track_universal_crossing_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.UNIVERSAL_CROSSING,
                    "traincraft:track_universal_crossing"));
    public static final RegistryObject<Block> SWITCH_SMALL_RIGHT_SEGMENT = BLOCKS.register("track_switch_small_right_segment",
            () -> new traincraft.block.track.LegacyBatchSwitchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_SMALL_RIGHT,
                    "traincraft:track_switch_small_right"));
    public static final RegistryObject<Block> SWITCH_SMALL_LEFT_SEGMENT = BLOCKS.register("track_switch_small_left_segment",
            () -> new traincraft.block.track.LegacyBatchSwitchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_SMALL_LEFT,
                    "traincraft:track_switch_small_left"));
    public static final RegistryObject<Block> SWITCH_MEDIUM_RIGHT_SEGMENT = BLOCKS.register("track_switch_medium_right_segment",
            () -> new traincraft.block.track.LegacyBatchSwitchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_MEDIUM_RIGHT,
                    "traincraft:track_switch_medium_right"));
    public static final RegistryObject<Block> SWITCH_MEDIUM_LEFT_SEGMENT = BLOCKS.register("track_switch_medium_left_segment",
            () -> new traincraft.block.track.LegacyBatchSwitchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_MEDIUM_LEFT,
                    "traincraft:track_switch_medium_left"));
    public static final RegistryObject<Block> SWITCH_LARGE_RIGHT_SEGMENT = BLOCKS.register("track_switch_large_right_segment",
            () -> new traincraft.block.track.LegacyBatchSwitchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_LARGE_RIGHT,
                    "traincraft:track_switch_large_right"));
    public static final RegistryObject<Block> SWITCH_LARGE_LEFT_SEGMENT = BLOCKS.register("track_switch_large_left_segment",
            () -> new traincraft.block.track.LegacyBatchSwitchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_LARGE_LEFT,
                    "traincraft:track_switch_large_left"));
    public static final RegistryObject<Block> SWITCH_VERY_LARGE_RIGHT_SEGMENT = BLOCKS.register("track_switch_very_large_right_segment",
            () -> new traincraft.block.track.LegacyBatchSwitchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_VERY_LARGE_RIGHT,
                    "traincraft:track_switch_very_large_right"));
    public static final RegistryObject<Block> SWITCH_VERY_LARGE_LEFT_SEGMENT = BLOCKS.register("track_switch_very_large_left_segment",
            () -> new traincraft.block.track.LegacyBatchSwitchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_VERY_LARGE_LEFT,
                    "traincraft:track_switch_very_large_left"));
    public static final RegistryObject<Block> SWITCH_45_MEDIUM_RIGHT_SEGMENT = BLOCKS.register("track_switch_45_medium_right_segment",
            () -> new traincraft.block.track.LegacyBatchSwitchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_45_MEDIUM_RIGHT,
                    "traincraft:track_switch_45_medium_right"));
    public static final RegistryObject<Block> SWITCH_45_MEDIUM_LEFT_SEGMENT = BLOCKS.register("track_switch_45_medium_left_segment",
            () -> new traincraft.block.track.LegacyBatchSwitchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_45_MEDIUM_LEFT,
                    "traincraft:track_switch_45_medium_left"));
    public static final RegistryObject<Block> SWITCH_PARALLEL_RIGHT_SEGMENT = BLOCKS.register("track_switch_parallel_right_segment",
            () -> new traincraft.block.track.LegacyBatchSwitchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_PARALLEL_RIGHT,
                    "traincraft:track_switch_parallel_right"));
    public static final RegistryObject<Block> SWITCH_PARALLEL_LEFT_SEGMENT = BLOCKS.register("track_switch_parallel_left_segment",
            () -> new traincraft.block.track.LegacyBatchSwitchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.SWITCH_PARALLEL_LEFT,
                    "traincraft:track_switch_parallel_left"));
    public static final RegistryObject<Block> DIAGONAL_STRAIGHT_SMALL_SEGMENT = BLOCKS.register("track_diagonal_straight_small_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.DIAGONAL_STRAIGHT_SMALL,
                    "traincraft:track_diagonal_straight_small"));
    public static final RegistryObject<Block> DIAGONAL_STRAIGHT_MEDIUM_SEGMENT = BLOCKS.register("track_diagonal_straight_medium_segment",
            () -> new traincraft.block.track.LegacyBatchRailBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                            net.minecraft.world.level.block.Blocks.RAIL),
                    traincraft.block.track.LegacyBatchTrackSpecs.DIAGONAL_STRAIGHT_MEDIUM,
                    "traincraft:track_diagonal_straight_medium"));
}
