package traincraft.compat.ae2;

import java.util.EnumSet;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import traincraft.block.integration.CargoInterfaceBlockEntity;

/**
 * Native AE2 node for the TC Cargo Interface.
 *
 * ME cables visibly connect to this station endpoint. Item movement remains
 * exposed through Forge IItemHandler so AE2 Import/Export Buses, Mekanism
 * Logistical Transporters, hoppers and other pipes can all use the same block.
 */
public final class AE2CargoInterfaceBlockEntity
        extends CargoInterfaceBlockEntity
        implements IInWorldGridNodeHost {

    private final IManagedGridNode mainNode = GridHelper
            .createManagedNode(this, (owner, node) -> owner.setChanged())
            .setInWorldNode(true)
            .setExposedOnSides(EnumSet.allOf(Direction.class))
            .setIdlePowerUsage(0.0D);

    public AE2CargoInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public IGridNode getGridNode(Direction direction) {
        return mainNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.SMART;
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        GridHelper.onFirstTick(this,
                be -> be.mainNode.create(be.getLevel(), be.getBlockPos()));
    }

    @Override
    public void setRemoved() {
        mainNode.destroy();
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        mainNode.saveToNBT(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        mainNode.loadFromNBT(tag);
    }
}
