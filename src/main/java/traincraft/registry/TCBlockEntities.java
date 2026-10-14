package traincraft.registry;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import traincraft.Traincraft;
import traincraft.block.machine.AssemblyTableBlockEntity;
import traincraft.block.machine.TraincraftProcessBlockEntity;

public final class TCBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Traincraft.MOD_ID);

    public static final RegistryObject<BlockEntityType<TraincraftProcessBlockEntity>> PROCESS_MACHINE =
            BLOCK_ENTITIES.register("process_machine", () ->
                    BlockEntityType.Builder.of(
                            TraincraftProcessBlockEntity::new,
                            TCBlocks.DISTILLERY.get(),
                            TCBlocks.OPEN_HEARTH_FURNACE.get())
                            .build(null));


    public static final RegistryObject<BlockEntityType<AssemblyTableBlockEntity>> ASSEMBLY_TABLE =
            BLOCK_ENTITIES.register("assembly_table", () ->
                    BlockEntityType.Builder.of(
                            AssemblyTableBlockEntity::new,
                            TCBlocks.ASSEMBLY_TABLE_I.get(),
                            TCBlocks.ASSEMBLY_TABLE_II.get(),
                            TCBlocks.ASSEMBLY_TABLE_III.get())
                            .build(null));
    // TC_DEV38_R1F1B_INTEGRATION_BE
    public static final net.minecraftforge.registries.RegistryObject<
            net.minecraft.world.level.block.entity.BlockEntityType<traincraft.block.integration.TCBatteryBlockEntity>> TC_BATTERY =
            BLOCK_ENTITIES.register("tc_battery", () ->
                    net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
                            traincraft.block.integration.TCBatteryBlockEntity::new,
                            TCBlocks.BATTERY.get()).build(null));

    public static final net.minecraftforge.registries.RegistryObject<
            net.minecraft.world.level.block.entity.BlockEntityType<traincraft.block.integration.RailPowerInterfaceBlockEntity>> RAIL_POWER_INTERFACE =
            BLOCK_ENTITIES.register("rail_power_interface", () ->
                    net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
                            traincraft.block.integration.IntegrationBlockEntityFactory::createRailPowerInterface,
                            TCBlocks.RAIL_POWER_INTERFACE.get()).build(null));

    public static final net.minecraftforge.registries.RegistryObject<
            net.minecraft.world.level.block.entity.BlockEntityType<traincraft.block.integration.CargoInterfaceBlockEntity>> CARGO_INTERFACE =
            BLOCK_ENTITIES.register("cargo_interface", () ->
                    net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
                            traincraft.block.integration.IntegrationBlockEntityFactory::createCargoInterface,
                            TCBlocks.CARGO_INTERFACE.get()).build(null));
    // DEV47_TCT_FLUID_INTERFACE
    public static final RegistryObject<BlockEntityType<traincraft.block.integration.FluidInterfaceBlockEntity>> FLUID_INTERFACE =
            BLOCK_ENTITIES.register("fluid_interface", () ->
                    BlockEntityType.Builder.of(
                            traincraft.block.integration.FluidInterfaceBlockEntity::new,
                            TCBlocks.FLUID_INTERFACE.get()).build(null));

    private TCBlockEntities() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}