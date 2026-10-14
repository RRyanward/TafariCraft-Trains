package traincraft.compat.ae2;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Optional AE2 15.x adapter for TC rail power.
 *
 * r1f2a first uses the Rail Power Interface's own native AE2 node. The adjacent
 * node scan remains as a compatibility fallback for worlds created by the
 * earlier r1f1 development builds.
 */
public final class AE2RailPowerBridge {
    private AE2RailPowerBridge() {
    }

    public static boolean drawPower(Level level, BlockPos interfacePos, double amountAE) {
        if (level.isClientSide || amountAE <= 0.0D) return false;

        var host = GridHelper.getNodeHost(level, interfacePos);
        if (host != null) {
            for (Direction direction : Direction.values()) {
                IGridNode ownNode = host.getGridNode(direction);
                if (drawFromNode(ownNode, amountAE)) return true;
            }
        }

        for (Direction direction : Direction.values()) {
            IGridNode adjacent = GridHelper.getExposedNode(
                    level,
                    interfacePos.relative(direction),
                    direction.getOpposite());
            if (drawFromNode(adjacent, amountAE)) return true;
        }

        return false;
    }

    private static boolean drawFromNode(IGridNode node, double amountAE) {
        if (node == null || !node.isActive() || node.getGrid() == null) return false;

        var energy = node.getGrid().getEnergyService();
        double available = energy.extractAEPower(
                amountAE,
                Actionable.SIMULATE,
                PowerMultiplier.ONE);

        if (available + 1.0E-6D < amountAE) return false;

        double extracted = energy.extractAEPower(
                amountAE,
                Actionable.MODULATE,
                PowerMultiplier.ONE);

        return extracted + 1.0E-6D >= amountAE;
    }
}
