/*
 * Traincraft 1.20.1 coupling contract.
 * Distributed under LGPL-v3.0.
 */
package traincraft.entity.train;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Persistent coupling state shared by Traincraft rolling stock.
 *
 * Step 7.2.0 keeps the original Step 7.0 primary coupler completely intact and
 * adds a second persisted coupler slot for longer consists. Existing movement
 * code continues to use the primary connection only until the multi-car
 * constraint is enabled in the next consist step.
 */
public interface CoupleableRollingStock {
    @Nullable
    UUID getCoupledRollingStockUuid();

    void setCoupledRollingStockUuid(@Nullable UUID uuid);

    default boolean hasCoupledRollingStock() {
        return this.getCoupledRollingStockUuid() != null;
    }

    /**
     * Second coupler introduced by Step 7.2.0. Implementations that have not yet
     * opted into longer consists remain source/binary compatible through these
     * defaults.
     */
    @Nullable
    default UUID getSecondaryCoupledRollingStockUuid() {
        return null;
    }

    default void setSecondaryCoupledRollingStockUuid(@Nullable UUID uuid) {
        // Legacy rolling stock may intentionally expose only one coupler.
    }

    default boolean hasSecondaryCoupledRollingStock() {
        return this.getSecondaryCoupledRollingStockUuid() != null;
    }

    default boolean hasAnyCoupling() {
        return this.hasCoupledRollingStock() || this.hasSecondaryCoupledRollingStock();
    }

    default boolean isCoupledTo(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        UUID primary = this.getCoupledRollingStockUuid();
        UUID secondary = this.getSecondaryCoupledRollingStockUuid();
        return uuid.equals(primary) || uuid.equals(secondary);
    }
}
