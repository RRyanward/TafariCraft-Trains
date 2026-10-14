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
import traincraft.block.integration.RailPowerInterfaceBlockEntity;

/**
 * Native AE2 node for the TC Rail Power Interface.
 *
 * This makes AE2 glass/covered/smart ME cables visibly terminate on the TC
 * interface. The accepted common power logic still performs the actual AE/t
 * draw, preserving the FE fallback and existing Energy Track behavior.
 */
public final class AE2RailPowerInterfaceBlockEntity
        extends RailPowerInterfaceBlockEntity
        implements IInWorldGridNodeHost {

    private final IManagedGridNode mainNode = GridHelper
            .createManagedNode(this, (owner, node) -> owner.setChanged())
            .setInWorldNode(true)
            .setExposedOnSides(EnumSet.allOf(Direction.class))
            .setIdlePowerUsage(0.0D);

    public AE2RailPowerInterfaceBlockEntity(BlockPos pos, BlockState state) {
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
