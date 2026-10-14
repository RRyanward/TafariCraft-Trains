package traincraft.block.track;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import traincraft.block.decor.ModularCrossingBlock;
import traincraft.entity.train.CoupleableRollingStock;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Isolated, permission-gated handler integration checks. No new rail or terrain
 * is placed. The test can spawn disposable entities only inside the existing
 * TC15 elevated course, and always discards them in a finally block.
 * A handler PASS does not assert client sound perception or a complete train run.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TC15SpecialtyRailAutoTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int X0 = -744, STEP = 12, Y = 180, Z = 2275;
    private static final String WORLD = "DEV50E6 Terrain Profile Test";
    private static final String[] IDS = {
        "track_detector_freight", "track_detector_passenger", "track_detector_locomotive",
        "track_detector_steam", "track_detector_diesel", "track_detector_tank",
        "track_coupler", "track_boarding_animal", "track_boarding_mob", "track_disembark",
        "track_locomotive_speed_controller", "track_locomotive_holding", "track_station",
        "track_horn_locomotive", "track_crossing_approach"
    };
    private static final String[] NAMES = {
        "FREIGHT_DETECTOR", "PASSENGER_DETECTOR", "LOCOMOTIVE_DETECTOR",
        "STEAM_DETECTOR", "DIESEL_DETECTOR", "TANK_DETECTOR", "COUPLER",
        "ANIMAL_BOARDING", "MOB_BOARDING", "DISEMBARK", "SPEED_CONTROLLER",
        "LOCOMOTIVE_HOLDING", "STATION", "HORN", "CROSSING_APPROACH"
    };
    private static final String[] RESULTS = new String[15];
    private static final String[] REASONS = new String[15];
    private static long lastTime = -1L;

    private TC15SpecialtyRailAutoTest() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("tcrailtest").requires(s -> s.hasPermission(2))
            .then(Commands.literal("run").executes(ctx -> run(ctx.getSource())))
            .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
            .then(Commands.literal("diagnose").executes(ctx -> diagnose(ctx.getSource()))));
    }

    // DEV50-TC15G: A backup restore may preserve the displayed world name while
    // changing the save-folder name. Never use folder basename as world identity.
    // Keep the exact project saves-root gate AND deny all Recovery-named folders.
    // The runner additionally requires all 15 exact in-world rail IDs and controls
    // at fixed coordinates BEFORE spawning its temporary test entities.
    private static final Path SAFE_SAVES = Paths.get(
            "D:\\TafariCraft - Reborn Development\\Traincraft", "run", "saves")
            .toAbsolutePath().normalize();

    private static boolean allowed(ServerLevel level) {
        Path root = level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        String name = level.getServer().getWorldData().getLevelName();
        String leaf = root.getFileName() == null ? "" : root.getFileName().toString();
        boolean underSaves = root.startsWith(SAFE_SAVES) && !root.equals(SAFE_SAVES);
        boolean recovery = leaf.toLowerCase(Locale.ROOT).contains("recovery")
                || root.toString().toLowerCase(Locale.ROOT).contains("railway recovery");
        boolean ok = level.dimension() == Level.OVERWORLD
                && WORLD.equals(name) && underSaves && !recovery;
        if (!ok) {
            LOG.error("[TC15-AUTO] WORLD_GUARD_FAIL displayName={} saveFolder={} savesRootMatch={} "
                    + "recoveryDenied={} dimension={} expectedDisplayName={} root={} NO_CHANGES",
                    name, leaf, underSaves, recovery, level.dimension().location(), WORLD, root);
        }
        return ok;
    }

    private static int diagnose(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        boolean ok = allowed(level);
        Path root = level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        String name = level.getServer().getWorldData().getLevelName();
        LOG.warn("[TC15-AUTO] DIAGNOSE allowed={} displayName={} saveFolder={} expectedWorld={} "
                + "worldPath={} noChanges=true", ok, name,
                root.getFileName(), WORLD, root);
        source.sendSuccess(() -> Component.literal("[TC15-AUTO] DIAGNOSE world=" + name
                + " guard=" + (ok ? "PASS" : "STOP") + "; see latest.log"), false);
        return ok ? 1 : 0;
    }

    private static BlockPos pos(int index) { return new BlockPos(X0 + STEP * index, Y, Z); }

    private static int status(CommandSourceStack source) {
        if (!allowed(source.getLevel())) {
            LOG.warn("[TC15-AUTO] SAFETY_STOP status wrong world; no access to protected saves");
            source.sendFailure(Component.literal("[TC15-AUTO] SAFETY STOP: wrong world or save location. Run /tcrailtest diagnose."));
            return 0;
        }
        if (lastTime < 0) {
            LOG.warn("[TC15-AUTO] NO_TEST_RUN");
            source.sendFailure(Component.literal("[TC15-AUTO] No completed test yet. Run /tcrailtest run."));
            return 0;
        }
        for (int i=0; i<15; i++)
            LOG.info("[TC15-AUTO] RESULT lane={} rail={} verdict={} reason={}",
                i+1, NAMES[i], RESULTS[i], REASONS[i]);
        source.sendSuccess(() -> Component.literal("[TC15-AUTO] Results reprinted to latest.log for all 15 lanes."), false);
        return 1;
    }

    private static int run(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!allowed(level)) {
            LOG.error("[TC15-AUTO] SAFETY_STOP exact disposable-world guard failed. NO CHANGES.");
            source.sendFailure(Component.literal("[TC15-AUTO] SAFETY STOP: world guard. Run /tcrailtest diagnose; see latest.log."));
            return 0;
        }
        // All rails and adjacent controls must be intact; reject user stock at the test points.
        for (int i=0; i<15; i++) {
            BlockPos at = pos(i);
            if (!level.hasChunkAt(at) || !level.hasChunkAt(at.offset(4,0,2))) {
                LOG.error("[TC15-AUTO] SAFETY_STOP lane={} unloaded test chunk. NO CHUNK FORCING.",i+1);
                source.sendFailure(Component.literal("[TC15-AUTO] SAFETY STOP: unloaded test lane " + (i+1) + ". Teleport above the course first."));
                return 0;
            }
            BlockState s = level.getBlockState(at);
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(s.getBlock());
            if (id == null || !id.toString().equals("traincraft:" + IDS[i])
                    || !(s.getBlock() instanceof ClassicSpecialTrackBlock)) {
                LOG.error("[TC15-AUTO] SAFETY_STOP lane={} expected=traincraft:{} actual={}. NO TEST ENTITIES SPAWNED.",
                    i+1, IDS[i], id);
                source.sendFailure(Component.literal("[TC15-AUTO] SAFETY STOP: missing/wrong specialty track at lane " + (i+1) + "."));
                return 0;
            }
            if (!level.getBlockState(at.offset(2,0,0)).is(Blocks.LEVER)) {
                LOG.error("[TC15-AUTO] SAFETY_STOP lane={} control lever missing. NO CHANGES.", i+1);
                source.sendFailure(Component.literal("[TC15-AUTO] SAFETY STOP: control lever missing at lane " + (i+1) + "."));
                return 0;
            }
            AABB near = new AABB(at).inflate(3.3D, 2.0D, 3.3D);
            if (!level.getEntities((Entity)null, near, e -> e instanceof AbstractMinecart).isEmpty()) {
                LOG.error("[TC15-AUTO] SAFETY_STOP lane={} existing train detected; remove it before testing. NO CHANGES.",i+1);
                source.sendFailure(Component.literal("[TC15-AUTO] SAFETY STOP: existing rolling stock at lane " + (i+1) + "."));
                return 0;
            }
        }
        source.sendSuccess(() -> Component.literal("[TC15-AUTO] Starting 15 handler tests; results will appear in latest.log."), false);
        LOG.info("[TC15-AUTO] BEGIN version=2 world={} lanes=15 realHandlerCalls=true construction=false", WORLD);
        int pass=0, fail=0, unver=0;
        for (int i=0;i<15;i++) {
            String result, detail;
            try {
                detail = test(level, i);
                result = detail.startsWith("UNVERIFIED:") ? "UNVERIFIED" : "PASS";
            } catch (Throwable t) {
                result = "FAIL";
                detail = t.getClass().getSimpleName() + ":" + safe(t.getMessage());
                LOG.error("[TC15-AUTO] LANE_EXCEPTION lane={} rail={}",i+1,NAMES[i],t);
            }
            RESULTS[i]=result; REASONS[i]=detail;
            if (result.equals("PASS"))pass++;
            else if (result.equals("FAIL"))fail++;
            else unver++;
            LOG.info("[TC15-AUTO] RESULT lane={} rail={} verdict={} reason={}", i+1,NAMES[i],result,detail);
        }
        lastTime=level.getGameTime();
        LOG.warn("[TC15-AUTO] SUMMARY handlerPass={} handlerFail={} unverified={} total=15 worldChangedRails=0 endToEndDrive=UNVERIFIED soundsAudible=UNVERIFIED automaticWorldgen=DISABLED",
            pass,fail,unver);
        final int p = pass, f = fail, u = unver;
        source.sendSuccess(() -> Component.literal("[TC15-AUTO] FINISHED: PASS=" + p
                + " FAIL=" + f + " UNVERIFIED=" + u + "/15. See latest.log for every lane."), false);
        return fail == 0 && unver == 0 ? 1 : 0;
    }

    private static String safe(String s) { return s == null ? "unknown" : s.replaceAll("[\\r\\n]", " "); }
    private static void require(boolean good, String reason) { if (!good) throw new IllegalStateException(reason); }

    private static Entity create(ServerLevel level, String key, BlockPos at) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(key));
        if (type == null) throw new IllegalStateException("ENTITY_NOT_REGISTERED_"+key);
        Entity e = type.create(level);
        if (e == null) throw new IllegalStateException("ENTITY_CREATE_FAILED_"+key);
        e.setPos(at.getX()+0.5D, at.getY()+0.0625D, at.getZ()+0.5D);
        return e;
    }

    private static boolean pulse(ServerLevel level, int lane, Entity e) {
        BlockPos at=pos(lane);
        BlockState state=level.getBlockState(at);
        ClassicSpecialTrackBlock block=(ClassicSpecialTrackBlock)state.getBlock();
        // Restore exactly the original ACTIVE state. Only changed state is the course rail's pulse.
        try {
            if (state.getValue(ClassicSpecialTrackBlock.ACTIVE))
                level.setBlock(at,state.setValue(ClassicSpecialTrackBlock.ACTIVE,false),3);
            block.entityInside(level.getBlockState(at),level,at,e);
            BlockState after=level.getBlockState(at);
            return after.getBlock()==block && after.getValue(ClassicSpecialTrackBlock.ACTIVE)
                && block.getSignal(after,level,at,net.minecraft.core.Direction.NORTH)==15;
        } finally {
            BlockState now=level.getBlockState(at);
            if (now.getBlock()==block && now.getValue(ClassicSpecialTrackBlock.ACTIVE)!=state.getValue(ClassicSpecialTrackBlock.ACTIVE))
                level.setBlock(at, now.setValue(ClassicSpecialTrackBlock.ACTIVE,state.getValue(ClassicSpecialTrackBlock.ACTIVE)),3);
        }
    }

    private static boolean pulseId(ServerLevel level,int lane,String entityId) {
        Entity e=create(level, entityId, pos(lane));
        try {return pulse(level,lane,e);} finally { e.discard(); }
    }

    private static void requirePulse(ServerLevel l,int n,String positive,String negative) {
        require(pulseId(l,n,positive),"MISSING_PULSE_"+positive);
        require(!pulseId(l,n,negative),"FALSE_POSITIVE_"+negative);
    }

    private static boolean powered(ServerLevel l,BlockPos p,boolean wanted) {
        return l.hasNeighborSignal(p)==wanted;
    }

    private static <T> T withPower(ServerLevel l,BlockPos p,boolean on,Supplier<T> action) {
        BlockPos lever=p.offset(2,0,0);
        BlockState prior=l.getBlockState(lever);
        require(prior.is(Blocks.LEVER),"LEVER_MISSING");
        try {
            BlockState control = on ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
            l.setBlock(lever, control,3);
            require(powered(l,p,on),"REDSTONE_POWER_"+on+"_NOT_REACHED");
            return action.get();
        } finally {l.setBlock(lever, prior,3);}
    }

    private static Entity chooseTank(ServerLevel l,BlockPos p) {
        for (EntityType<?> type: ForgeRegistries.ENTITY_TYPES.getValues()) {
            ResourceLocation key=ForgeRegistries.ENTITY_TYPES.getKey(type);
            if(key==null || !"traincraft".equals(key.getNamespace()) || !key.getPath().contains("tank"))continue;
            Entity e=type.create(l);
            if (e instanceof AbstractMinecart && !(e instanceof traincraft.entity.train.steam.small.SmallSteamLocomotive)) {
                e.setPos(p.getX()+0.5,p.getY()+0.0625,p.getZ()+0.5);
                return e;
            }
            if(e!=null)e.discard();
        }
        return null;
    }

    private static String test(ServerLevel level,int i) {
        BlockPos p=pos(i);
        switch(i) {
            case 0:
                requirePulse(level,i,"traincraft:freight_cart","traincraft:passenger_coach_blue");
                require(pulseId(level,i,"traincraft:milw_baggage_car"),"BAGGAGE_NOT_DETECTED");
                return "freight_and_baggage_positive_passenger_negative";
            case 1:
                requirePulse(level,i,"traincraft:passenger_coach_blue","traincraft:freight_cart");
                require(pulseId(level,i,"traincraft:milw_passenger_car"),"MILW_PASSENGER_NOT_DETECTED");
                return "blue_and_milw_positive_freight_negative";
            case 2:
                requirePulse(level,i,"traincraft:locomotive_steam_small","traincraft:freight_cart");
                require(pulseId(level,i,"traincraft:locomotive_diesel_gp7"),"DIESEL_LOCO_NOT_DETECTED");
                return "steam_and_diesel_positive_freight_negative";
            case 3:
                requirePulse(level,i,"traincraft:locomotive_steam_small","traincraft:locomotive_diesel_gp7");
                return "steam_positive_diesel_negative";
            case 4:
                requirePulse(level,i,"traincraft:locomotive_diesel_gp7","traincraft:locomotive_steam_small");
                return "diesel_positive_steam_negative";
            case 5: {
                Entity tank=chooseTank(level,p);
                if(tank==null)return "UNVERIFIED:no_nonlocomotive_tank_entity_in_registry";
                try{ require(pulse(level,i,tank),"TANK_NOT_DETECTED"); }finally{tank.discard();}
                require(!pulseId(level,i,"traincraft:locomotive_steam_small"),"STEAM_FALSE_TANK");
                return "registered_tank_positive_steam_negative";
            }
            case 6: return testCoupler(level,p);
            case 7: return testBoard(level,p,true);
            case 8: return testBoard(level,p,false);
            case 9: return testDisembark(level,p);
            case 10: return testSpeed(level,p);
            case 11: return testHold(level,p,false);
            case 12: return testHold(level,p,true);
            case 13:
                requirePulse(level,i,"traincraft:locomotive_steam_small","traincraft:freight_cart");
                require(pulseId(level,i,"traincraft:locomotive_diesel_gp7"),"DIESEL_HORN_PULSE_MISSING");
                return "both_horn_dispatches_called_and_pulsed_audio_perception_unverified";
            case 14: return testCrossing(level,p);
            default: throw new IllegalStateException("invalid_lane");
        }
    }

    private static void add(ServerLevel level, List<Entity> spawned,Entity e) {
        require(level.addFreshEntity(e),"TEMP_ENTITY_SPAWN_REJECTED");
        spawned.add(e);
    }
    private static void clear(List<Entity> spawned) {
        for(Entity e:spawned){
            for(Entity rider:new ArrayList<>(e.getPassengers())) rider.stopRiding();
            if(e.isPassenger())e.stopRiding();
            e.discard();
        }
    }
    private static String testCoupler(ServerLevel level,BlockPos p) {
        List<Entity> all=new ArrayList<>();
        try {
            Entity first=create(level,"traincraft:freight_cart",p);
            Entity second=create(level,"traincraft:passenger_coach_blue",p.offset(0,0,2));
            require(first instanceof CoupleableRollingStock && second instanceof CoupleableRollingStock,"COUPLEABLE_TYPE_MISSING");
            add(level,all,first);add(level,all,second);
            withPower(level,p,true,()->{
                ((ClassicSpecialTrackBlock)level.getBlockState(p).getBlock()).entityInside(level.getBlockState(p),level,p,first);
                return true;
            });
            require(((CoupleableRollingStock)first).isCoupledTo(second.getUUID()),"POWERED_COUPLER_DID_NOT_LINK");
            withPower(level,p,false,()->{
                ((ClassicSpecialTrackBlock)level.getBlockState(p).getBlock()).entityInside(level.getBlockState(p),level,p,first);
                return true;
            });
            require(!((CoupleableRollingStock)first).isCoupledTo(second.getUUID()),"UNPOWERED_COUPLER_DID_NOT_UNLINK");
            return "powered_link_unpowered_unlink";
        }finally{clear(all);}
    }
    private static String testBoard(ServerLevel level,BlockPos p,boolean animal) {
        List<Entity> all=new ArrayList<>();
        try {
            Entity car=create(level,animal?"traincraft:livestock_car":"traincraft:mob_transport_car",p);
            Entity rider=create(level,animal?"minecraft:cow":"minecraft:zombie",p.offset(1,0,0));
            require(rider instanceof Mob,"RIDER_TYPE_MISSING");
            if (rider instanceof Mob m)m.setNoAi(true);
            add(level,all,car);add(level,all,rider);
            require(pulse(level,animal?7:8,car),"BOARDING_PULSE_MISSING");
            require(rider.getVehicle()==car,"RIDER_DID_NOT_BOARD");
            return animal?"animal_boarded_and_pulsed":"mob_boarded_and_pulsed";
        }finally{clear(all);}
    }
    private static String testDisembark(ServerLevel level,BlockPos p) {
        List<Entity> all=new ArrayList<>();
        try {
            Entity car=create(level,"traincraft:livestock_car",p);
            Entity animal=create(level,"minecraft:cow",p);
            add(level,all,car);add(level,all,animal);
            require(animal.startRiding(car,true),"FIXTURE_MOUNT_FAILED");
            require(pulse(level,9,car),"DISEMBARK_PULSE_MISSING");
            require(!animal.isPassenger(),"RIDER_STILL_MOUNTED");
            return "passenger_dismounted_and_pulsed";
        }finally{clear(all);}
    }
    private static double speed(ServerLevel level,BlockPos p,boolean on) {
        return withPower(level,p,on,()->{
            Entity e=create(level,"traincraft:locomotive_steam_small",p);
            try {
                require(e instanceof AbstractMinecart,"LOCO_NOT_MINECART");
                e.setDeltaMovement(1.0D,0,0);
                ((ClassicSpecialTrackBlock)level.getBlockState(p).getBlock()).entityInside(level.getBlockState(p),level,p,e);
                Vec3 v=e.getDeltaMovement();
                return Math.hypot(v.x,v.z);
            }finally{e.discard();}
        });
    }
    private static String testSpeed(ServerLevel l,BlockPos p) {
        double off=speed(l,p,false), on=speed(l,p,true);
        require(off<=0.101D && on>0.45D && on<=0.551D,"REDSTONE_CAPS_OFF_"+off+"_ON_"+on);
        return String.format(Locale.ROOT,"cap_off=%.3f_cap_on=%.3f",off,on);
    }
    private static String testHold(ServerLevel l,BlockPos p,boolean station) {
        String type=station?"traincraft:freight_cart":"traincraft:locomotive_steam_small";
        double off=withPower(l,p,false,()->contactSpeed(l,p,type));
        double on=withPower(l,p,true,()->contactSpeed(l,p,type));
        require(off<1e-5 && on>0.9D,"HOLD_RESULT_OFF_"+off+"_ON_"+on);
        return station?"station_unpowered_stop_powered_pass":"holding_unpowered_stop_powered_pass";
    }
    private static double contactSpeed(ServerLevel l,BlockPos p,String type) {
        Entity e=create(l,type,p);
        try {
            e.setDeltaMovement(1.0D,0,0);
            ((ClassicSpecialTrackBlock)l.getBlockState(p).getBlock()).entityInside(l.getBlockState(p),l,p,e);
            return Math.hypot(e.getDeltaMovement().x,e.getDeltaMovement().z);
        }finally{e.discard();}
    }
    private static String testCrossing(ServerLevel l,BlockPos p) {
        BlockPos base=p.offset(4,0,2);
        BlockState state=l.getBlockState(base);
        require(state.getBlock() instanceof ModularCrossingBlock,"CROSSING_BASE_MISSING");
        require(pulseId(l,14,"traincraft:freight_cart"),"APPROACH_PULSE_MISSING");
        ModularCrossingBlock block=(ModularCrossingBlock)state.getBlock();
        block.tick(l.getBlockState(base),l,base,l.random);
        require(l.getBlockState(base).getValue(ModularCrossingBlock.ACTIVE),"CROSSING_BASE_DID_NOT_ACTIVATE");
        return "approach_pulse_and_gate_base_activation";
    }
}
