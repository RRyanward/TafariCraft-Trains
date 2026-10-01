/*
 * Traincraft 1.20.1 rolling-stock coupling helper.
 * Distributed under LGPL-v3.0.
 */
package traincraft.entity.train;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.entity.train.tender.SteamTender;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative coupling helpers.
 *
 * Step 7.2.1 keeps the proven locomotive/tender primary connection unchanged and
 * activates the tender's secondary coupler so a second tender can follow the
 * first. Step 7.2.1f also aligns a newly linked follower tender to the physical
 * consist direction so a tender placed backwards is flipped automatically.
 * Step 7.2.1l additionally settles the follower to the measured classic coupler
 * nub-to-nub distance at connector time, even when the locomotive handbrake is on.
 * The resulting topology is:
 *
 * locomotive PRIMARY <-> PRIMARY tender #1 SECONDARY <-> PRIMARY tender #2
 *
 * Additional tenders can use the same secondary->primary chain, but this step is
 * intentionally tested first as a three-vehicle consist.
 */
public final class CouplingManager {
    public static final double MAX_LINK_DISTANCE = 8.0D;
    private static final int MAX_LEADER_SEARCH_HOPS = 16;

    private enum CouplerSlot {
        PRIMARY,
        SECONDARY
    }

    private static final class LinkPlan {
        private final CouplerSlot firstSlot;
        private final CouplerSlot secondSlot;

        private LinkPlan(CouplerSlot firstSlot, CouplerSlot secondSlot) {
            this.firstSlot = firstSlot;
            this.secondSlot = secondSlot;
        }
    }

    private CouplingManager() {
    }

    /** Resolves the primary/leader-side connection used by rolling-stock physics. */
    @Nullable
    public static Entity resolve(Entity entity, CoupleableRollingStock stock) {
        return resolveUuid(entity, stock.getCoupledRollingStockUuid());
    }

    /** Resolves the secondary/rear connection when it is currently loaded. */
    @Nullable
    public static Entity resolveSecondary(Entity entity, CoupleableRollingStock stock) {
        return resolveUuid(entity, stock.getSecondaryCoupledRollingStockUuid());
    }

    @Nullable
    private static Entity resolveUuid(Entity entity, @Nullable UUID uuid) {
        if (uuid == null || !(entity.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getEntity(uuid);
    }

    public static boolean isSupportedPair(Entity first, Entity second) {
        if ((first instanceof SmallSteamLocomotive && second instanceof SteamTender)
                || (first instanceof SteamTender && second instanceof SmallSteamLocomotive)) {
            return true;
        }
        return first instanceof SteamTender && second instanceof SteamTender;
    }

    /**
     * Clears dead or one-way UUIDs before connector decisions are made.
     */
    public static boolean cleanupStaleLinks(Entity entity, CoupleableRollingStock stock) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        boolean changed = false;
        changed |= cleanupSlot(serverLevel, entity, stock, CouplerSlot.PRIMARY);
        changed |= cleanupSlot(serverLevel, entity, stock, CouplerSlot.SECONDARY);
        return changed;
    }

    private static boolean cleanupSlot(ServerLevel serverLevel, Entity entity,
                                       CoupleableRollingStock stock, CouplerSlot slot) {
        UUID partnerUuid = getSlot(stock, slot);
        if (partnerUuid == null) {
            return false;
        }

        Entity partner = serverLevel.getEntity(partnerUuid);
        boolean valid = partner instanceof CoupleableRollingStock partnerStock
                && !partner.isRemoved()
                && partnerStock.isCoupledTo(entity.getUUID());
        if (valid) {
            return false;
        }

        setSlot(stock, slot, null);
        return true;
    }

    public static boolean areMutuallyLinked(Entity first, CoupleableRollingStock firstStock,
                                            Entity second, CoupleableRollingStock secondStock) {
        return firstStock.isCoupledTo(second.getUUID())
                && secondStock.isCoupledTo(first.getUUID());
    }

    /** Clears only the links that point directly between this exact pair. */
    public static boolean unlinkPair(Entity first, CoupleableRollingStock firstStock,
                                     Entity second, CoupleableRollingStock secondStock) {
        boolean changed = false;
        changed |= clearLinkTo(firstStock, second.getUUID());
        changed |= clearLinkTo(secondStock, first.getUUID());
        return changed;
    }

    private static boolean clearLinkTo(CoupleableRollingStock stock, UUID partnerUuid) {
        boolean changed = false;
        if (partnerUuid.equals(stock.getCoupledRollingStockUuid())) {
            stock.setCoupledRollingStockUuid(null);
            changed = true;
        }
        if (partnerUuid.equals(stock.getSecondaryCoupledRollingStockUuid())) {
            stock.setSecondaryCoupledRollingStockUuid(null);
            changed = true;
        }
        return changed;
    }

    public static boolean canLink(Entity first, CoupleableRollingStock firstStock,
                                  Entity second, CoupleableRollingStock secondStock) {
        if (first == second || first.isRemoved() || second.isRemoved()) {
            return false;
        }
        if (!isSupportedPair(first, second)) {
            return false;
        }
        if (first.distanceToSqr(second) > MAX_LINK_DISTANCE * MAX_LINK_DISTANCE) {
            return false;
        }
        return chooseLinkPlan(first, firstStock, second, secondStock) != null;
    }

    public static boolean link(Entity first, CoupleableRollingStock firstStock,
                               Entity second, CoupleableRollingStock secondStock) {
        if (first == second || first.isRemoved() || second.isRemoved()) {
            return false;
        }
        if (!isSupportedPair(first, second)
                || first.distanceToSqr(second) > MAX_LINK_DISTANCE * MAX_LINK_DISTANCE) {
            return false;
        }

        LinkPlan plan = chooseLinkPlan(first, firstStock, second, secondStock);
        if (plan == null) {
            return false;
        }

        setSlot(firstStock, plan.firstSlot, second.getUUID());
        setSlot(secondStock, plan.secondSlot, first.getUUID());
        alignFollowerTender(first, plan.firstSlot, second, plan.secondSlot);
        return true;
    }

    /**
     * Normalizes tender orientation at connector time.  The PRIMARY side of a
     * tender-to-tender link is the follower; the SECONDARY side is the leader.
     * For locomotive/tender links the tender always follows the locomotive.
     */
    private static void alignFollowerTender(Entity first, CouplerSlot firstSlot,
                                            Entity second, CouplerSlot secondSlot) {
        if (first instanceof SmallSteamLocomotive && second instanceof SteamTender tender) {
            tender.alignFacingToCouplingLeader(first);
            tender.alignCouplerSpacingToLeader(first);
            return;
        }
        if (second instanceof SmallSteamLocomotive && first instanceof SteamTender tender) {
            tender.alignFacingToCouplingLeader(second);
            tender.alignCouplerSpacingToLeader(second);
            return;
        }

        if (first instanceof SteamTender firstTender && second instanceof SteamTender secondTender) {
            if (firstSlot == CouplerSlot.PRIMARY && secondSlot == CouplerSlot.SECONDARY) {
                firstTender.alignFacingToCouplingLeader(secondTender);
                firstTender.alignCouplerSpacingToLeader(secondTender);
            } else if (secondSlot == CouplerSlot.PRIMARY && firstSlot == CouplerSlot.SECONDARY) {
                secondTender.alignFacingToCouplingLeader(firstTender);
                secondTender.alignCouplerSpacingToLeader(firstTender);
            }
        }
    }

    /**
     * Preserve the original locomotive/tender primary-primary link exactly.
     * Tender-to-tender links always use one rear/secondary coupler and one
     * primary/follower coupler.  This gives the trailing tender an unambiguous
     * leader for the proven rail-following constraint.
     */
    @Nullable
    private static LinkPlan chooseLinkPlan(Entity first, CoupleableRollingStock firstStock,
                                           Entity second, CoupleableRollingStock secondStock) {
        if (firstStock.isCoupledTo(second.getUUID()) || secondStock.isCoupledTo(first.getUUID())) {
            return null;
        }

        boolean locomotiveTender = (first instanceof SmallSteamLocomotive && second instanceof SteamTender)
                || (first instanceof SteamTender && second instanceof SmallSteamLocomotive);
        if (locomotiveTender) {
            if (getSlot(firstStock, CouplerSlot.PRIMARY) != null
                    || getSlot(secondStock, CouplerSlot.PRIMARY) != null) {
                return null;
            }
            return new LinkPlan(CouplerSlot.PRIMARY, CouplerSlot.PRIMARY);
        }

        if (first instanceof SteamTender && second instanceof SteamTender) {
            // Preferred chain direction: selected/first tender leads from its rear
            // coupler; clicked/second tender follows through its primary coupler.
            if (getSlot(firstStock, CouplerSlot.SECONDARY) == null
                    && getSlot(secondStock, CouplerSlot.PRIMARY) == null) {
                return new LinkPlan(CouplerSlot.SECONDARY, CouplerSlot.PRIMARY);
            }

            // Reverse click order: the second tender is already the leader and the
            // first tender becomes the follower.
            if (getSlot(firstStock, CouplerSlot.PRIMARY) == null
                    && getSlot(secondStock, CouplerSlot.SECONDARY) == null) {
                return new LinkPlan(CouplerSlot.PRIMARY, CouplerSlot.SECONDARY);
            }
        }

        return null;
    }

    /**
     * Unlinks only this stock's primary leader connection.  This is used when a
     * follower exceeds the emergency break distance; a middle tender's rear link
     * is intentionally left alone so only the failed coupler breaks.
     */
    public static boolean unlink(Entity entity, CoupleableRollingStock stock) {
        UUID partnerUuid = stock.getCoupledRollingStockUuid();
        if (partnerUuid == null) {
            return false;
        }

        stock.setCoupledRollingStockUuid(null);
        clearLoadedPartnerBackLink(entity, partnerUuid);
        return true;
    }

    /** Disconnects both couplers on a vehicle, used by shift-click and removal. */
    public static boolean unlinkAll(Entity entity, CoupleableRollingStock stock) {
        UUID primary = stock.getCoupledRollingStockUuid();
        UUID secondary = stock.getSecondaryCoupledRollingStockUuid();
        if (primary == null && secondary == null) {
            return false;
        }

        stock.setCoupledRollingStockUuid(null);
        stock.setSecondaryCoupledRollingStockUuid(null);

        if (primary != null) {
            clearLoadedPartnerBackLink(entity, primary);
        }
        if (secondary != null && !secondary.equals(primary)) {
            clearLoadedPartnerBackLink(entity, secondary);
        }
        return true;
    }

    private static void clearLoadedPartnerBackLink(Entity entity, UUID partnerUuid) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity partner = serverLevel.getEntity(partnerUuid);
        if (partner instanceof CoupleableRollingStock partnerStock) {
            clearLinkTo(partnerStock, entity.getUUID());
        }
    }

    /**
     * Walks the primary leader chain until it reaches the small steam locomotive.
     * The visited set protects against corrupted/cyclic saves.
     */
    @Nullable
    public static SmallSteamLocomotive findLeadLocomotive(Entity start) {
        if (!(start.level() instanceof ServerLevel serverLevel)) {
            return start instanceof SmallSteamLocomotive locomotive ? locomotive : null;
        }

        Entity current = start;
        Set<UUID> visited = new HashSet<>();
        for (int hop = 0; hop < MAX_LEADER_SEARCH_HOPS && current != null; hop++) {
            if (current instanceof SmallSteamLocomotive locomotive) {
                return locomotive;
            }
            if (!(current instanceof CoupleableRollingStock stock)
                    || !visited.add(current.getUUID())) {
                return null;
            }

            UUID leaderUuid = stock.getCoupledRollingStockUuid();
            if (leaderUuid == null) {
                return null;
            }
            current = serverLevel.getEntity(leaderUuid);
        }
        return null;
    }

    public static boolean isTrainHandBrakeApplied(Entity start) {
        SmallSteamLocomotive locomotive = findLeadLocomotive(start);
        return locomotive != null && locomotive.isHandBrakeApplied();
    }

    @Nullable
    private static UUID getSlot(CoupleableRollingStock stock, CouplerSlot slot) {
        return slot == CouplerSlot.PRIMARY
                ? stock.getCoupledRollingStockUuid()
                : stock.getSecondaryCoupledRollingStockUuid();
    }

    private static void setSlot(CoupleableRollingStock stock, CouplerSlot slot, @Nullable UUID uuid) {
        if (slot == CouplerSlot.PRIMARY) {
            stock.setCoupledRollingStockUuid(uuid);
        } else {
            stock.setSecondaryCoupledRollingStockUuid(uuid);
        }
    }
}
