package traincraft.block.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

/**
 * Keeps AE2 an optional runtime dependency.
 *
 * The common TC block-entity types never directly reference AE2 classes.
 * When AE2 is present, reflection creates the native AE2 host subclass so ME
 * cables render a real connection. Without AE2 the ordinary Forge-capability
 * block entity is used.
 */
public final class IntegrationBlockEntityFactory {
    private IntegrationBlockEntityFactory() {
    }

    public static RailPowerInterfaceBlockEntity createRailPowerInterface(BlockPos pos, BlockState state) {
        if (ModList.get().isLoaded("ae2")) {
            return create(
                    "traincraft.compat.ae2.AE2RailPowerInterfaceBlockEntity",
                    RailPowerInterfaceBlockEntity.class,
                    pos,
                    state);
        }
        return new RailPowerInterfaceBlockEntity(pos, state);
    }

    public static CargoInterfaceBlockEntity createCargoInterface(BlockPos pos, BlockState state) {
        if (ModList.get().isLoaded("ae2")) {
            return create(
                    "traincraft.compat.ae2.AE2CargoInterfaceBlockEntity",
                    CargoInterfaceBlockEntity.class,
                    pos,
                    state);
        }
        return new CargoInterfaceBlockEntity(pos, state);
    }

    private static <T> T create(
            String className,
            Class<T> type,
            BlockPos pos,
            BlockState state) {
        try {
            Class<?> impl = Class.forName(className);
            Object value = impl.getConstructor(BlockPos.class, BlockState.class)
                    .newInstance(pos, state);
            return type.cast(value);
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException(
                    "Optional AE2 integration is loaded but could not create " + className,
                    e);
        }
    }
}
