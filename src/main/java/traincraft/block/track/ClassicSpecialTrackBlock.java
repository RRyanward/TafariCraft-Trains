package traincraft.block.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.block.decor.ModularCrossingBlock;

import traincraft.entity.train.CoupleableRollingStock;
import traincraft.entity.train.CouplingManager;
import traincraft.entity.train.caboose.ClassicCaboose;
import traincraft.entity.train.freight.FreightCart;
import traincraft.entity.train.freight.LivestockCar;
import traincraft.entity.train.freight.MobTransportCar;
import traincraft.entity.train.passenger.PassengerCoachBlue;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * v1j-r4 classic one-block Traincraft specialty rails.
 *
 * They deliberately reuse vanilla RailBlock geometry/pathing so they line up
 * with the already accepted TafariCraft rail math. No LegacyBatchTrackSpec or
 * LegacyContinuousTrackPath geometry is changed here.
 */
public final class ClassicSpecialTrackBlock extends RailBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public enum Type {
        DETECTOR_FREIGHT,
        DETECTOR_PASSENGER,
        DETECTOR_LOCOMOTIVE,
        DETECTOR_STEAM,
        DETECTOR_DIESEL,
        DETECTOR_TANK,
        CROSSING_APPROACH,
        COUPLER,
        COPPER,
        STEEL,
        BOARDING_ANIMAL,
        BOARDING_MOB,
        DISEMBARK,
        SPEED_CONTROLLER,
        HOLDING,
        STATION,
        HORN,
        SNOW,
        SNOW_STEEL,
        SNOW_COPPER
    }

    private final Type type;

    public ClassicSpecialTrackBlock(BlockBehaviour.Properties properties, Type type) {
        super(properties);
        this.type = type;
        this.registerDefaultState(this.defaultBlockState().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    private boolean pulseType() {
        return switch (type) {
            case DETECTOR_FREIGHT, DETECTOR_PASSENGER, DETECTOR_LOCOMOTIVE,
                 DETECTOR_STEAM, DETECTOR_DIESEL, DETECTOR_TANK, CROSSING_APPROACH,
                 BOARDING_ANIMAL, BOARDING_MOB, DISEMBARK, HORN -> true;
            default -> false;
        };
    }

    private boolean externalPowerVisual() {
        return switch (type) {
            case COUPLER, SPEED_CONTROLLER, HOLDING, STATION -> true;
            default -> false;
        };
    }

    private boolean emitsSignal() {
        return pulseType();
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return emitsSignal();
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return emitsSignal() && state.getValue(ACTIVE) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getSignal(state, level, pos, side);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide || !externalPowerVisual()) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (state.getValue(ACTIVE) != powered) {
            level.setBlock(pos, state.setValue(ACTIVE, powered), 3);
        }
    }

    private void pulse(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server)) return;
        BlockState current = server.getBlockState(pos);
        if (current.getBlock() != this) return;
        if (!current.getValue(ACTIVE)) {
            server.setBlock(pos, current.setValue(ACTIVE, true), 3);
            server.updateNeighborsAt(pos, this);
            server.updateNeighborsAt(pos.below(), this);
        }
        server.scheduleTick(pos, this, 4);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (pulseType() && state.getValue(ACTIVE)) {
            level.setBlock(pos, state.setValue(ACTIVE, false), 3);
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(pos.below(), this);
        }
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (level.isClientSide || !(entity instanceof AbstractMinecart cart)) return;

        switch (type) {
            case DETECTOR_FREIGHT -> {
                if (isFreightStock(entity)) pulse(level, pos, state);
            }
            case DETECTOR_PASSENGER -> {
                if (isPassengerStock(entity)) pulse(level, pos, state);
            }
            case DETECTOR_LOCOMOTIVE -> {
                if (isLocomotive(entity)) pulse(level, pos, state);
            }
            case DETECTOR_STEAM -> {
                if (entity instanceof SmallSteamLocomotive && !(entity instanceof LegacyPoweredLocomotive)) pulse(level, pos, state);
            }
            case DETECTOR_DIESEL -> {
                if (entity instanceof LegacyPoweredLocomotive powered && powered.isDiesel()) pulse(level, pos, state);
            }
            case DETECTOR_TANK -> {
                String id = entityId(entity);
                if (!isLocomotive(entity) && id.contains("tank")) pulse(level, pos, state);
            }
            case CROSSING_APPROACH -> {
                // First contact with this rail activates the nearest crossing gate cluster immediately.
                if (!state.getValue(ACTIVE) && level instanceof ServerLevel server) {
                    ModularCrossingBlock.triggerNearbyBases(server, pos);
                }
                pulse(level, pos, state);
            }
            case COUPLER -> handleCoupler(level, pos, entity);
            case COPPER -> capHorizontal(cart, 0.22D);
            case STEEL -> capHorizontal(cart, 0.55D);
            case BOARDING_ANIMAL -> {
                if (entity instanceof LivestockCar car && car.getPassengers().size() < 4) {
                    Animal animal = level.getEntitiesOfClass(
                                    Animal.class,
                                    car.getBoundingBox().inflate(4.0D),
                                    e -> e.isAlive() && !e.isPassenger())
                            .stream()
                            .min(Comparator.comparingDouble(e -> car.distanceToSqr(e)))
                            .orElse(null);
                    if (animal != null && animal.startRiding(car, true)) pulse(level, pos, state);
                }
            }
            case BOARDING_MOB -> {
                if (entity instanceof MobTransportCar car && car.getPassengers().size() < 4) {
                    Mob mob = level.getEntitiesOfClass(
                                    Mob.class,
                                    car.getBoundingBox().inflate(4.0D),
                                    e -> !(e instanceof Animal) && e.isAlive() && !e.isPassenger())
                            .stream()
                            .min(Comparator.comparingDouble(e -> car.distanceToSqr(e)))
                            .orElse(null);
                    if (mob != null && mob.startRiding(car, true)) pulse(level, pos, state);
                }
            }
            case DISEMBARK -> {
                if (entity instanceof LivestockCar || entity instanceof MobTransportCar) {
                    boolean hadPassengers = !entity.getPassengers().isEmpty();
                    for (Entity passenger : new ArrayList<>(entity.getPassengers())) passenger.stopRiding();
                    if (hadPassengers) pulse(level, pos, state);
                }
            }
            case SPEED_CONTROLLER -> {
                if (isLocomotive(entity)) {
                    int signal = level.getBestNeighborSignal(pos);
                    double cap = 0.10D + (0.45D * (signal / 15.0D));
                    capHorizontal(cart, cap);
                }
            }
            case HOLDING -> {
                if (isLocomotive(entity) && !level.hasNeighborSignal(pos)) stopHorizontal(cart);
            }
            case STATION -> {
                if (!level.hasNeighborSignal(pos)) stopHorizontal(cart);
            }
            case HORN -> {
                if (isLocomotive(entity)) {
                    if (entity instanceof LegacyPoweredLocomotive powered) powered.playAutomaticHorn(); else if (entity instanceof SmallSteamLocomotive steam) steam.playWhistle(null);
                    pulse(level, pos, state);
                }
            }
            case SNOW -> capHorizontal(cart, 0.35D);
            case SNOW_STEEL -> capHorizontal(cart, 0.52D);
            case SNOW_COPPER -> capHorizontal(cart, 0.20D);
        }
    }

    private void handleCoupler(Level level, BlockPos pos, Entity entity) {
        if (!(entity instanceof CoupleableRollingStock stock)) return;

        if (!level.hasNeighborSignal(pos)) {
            CouplingManager.unlinkAll(entity, stock);
            return;
        }

        AABB box = entity.getBoundingBox().inflate(3.0D, 1.5D, 3.0D);
        List<Entity> candidates = level.getEntities(
                entity,
                box,
                e -> e instanceof CoupleableRollingStock && e.isAlive() && e != entity);
        candidates.sort(Comparator.comparingDouble(entity::distanceToSqr));
        for (Entity other : candidates) {
            CoupleableRollingStock otherStock = (CoupleableRollingStock) other;
            if (stock.isCoupledTo(other.getUUID())) return;
            if (CouplingManager.link(entity, stock, other, otherStock)) return;
        }
    }

    // DEV50-TC15: Detect ALL registered compatible freight and passenger stock,
    // not only the original sample cart/blue coach. Do not classify locomotives.
    private static boolean isFreightStock(Entity e) {
        if (!(e instanceof AbstractMinecart) || isLocomotive(e)) return false;
        String id=entityId(e);
        return id.contains("freight") || id.contains("baggage") || id.contains("tanker")
                || id.contains("tank") || id.contains("livestock") || id.contains("mob_transport");
    }

    private static boolean isPassengerStock(Entity e) {
        if (!(e instanceof AbstractMinecart) || isLocomotive(e)) return false;
        String id=entityId(e);
        return id.contains("passenger") || id.contains("observatory")
                || id.contains("tail_car") || id.contains("caboose");
    }
    private static boolean isLocomotive(Entity entity) {
        return entity instanceof SmallSteamLocomotive;
    }

    private static String entityId(Entity entity) {
        var key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return key == null ? "" : key.getPath();
    }

    private static void stopHorizontal(AbstractMinecart cart) {
        Vec3 v = cart.getDeltaMovement();
        cart.setDeltaMovement(0.0D, v.y, 0.0D);
    }

    private static void capHorizontal(AbstractMinecart cart, double cap) {
        Vec3 v = cart.getDeltaMovement();
        double horizontal = Math.hypot(v.x, v.z);
        if (horizontal <= cap || horizontal < 1.0E-8D) return;
        double scale = cap / horizontal;
        cart.setDeltaMovement(v.x * scale, v.y, v.z * scale);
    }
}