package traincraft.registry;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import traincraft.Traincraft;
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

    private TCBlockEntities() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}