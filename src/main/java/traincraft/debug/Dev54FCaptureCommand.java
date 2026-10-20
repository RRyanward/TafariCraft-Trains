package traincraft.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import traincraft.Traincraft;

/**
 * DEV54F: Single-player, disposable-world-only, full roster recording harness.
 * Opt-in command, no start-on-load, no persistent renderer animation edits.
 * Recorded motion is a visual diagnostic stimulus, NOT proof of native driving.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev54FCaptureCommand {
    static boolean dev54h2CaptureActive() { return active != null; }
    static List<String> dev54h2Roster() { return ROSTER; }
    private static final String OWNED = "tc_dev54f_capture_owned";
    private static final int WAIT_RECORD = 200; // 10 seconds at 20 TPS
    private static final int REST = 25, FORWARD = 65, MIDSTOP = 20, REVERSE = 65, ENDSTOP = 25;
    private static final int SIDE_TICKS = REST + FORWARD + MIDSTOP + REVERSE + ENDSTOP;
    private static final double SPEED = 0.068;
    private static final long MIDDAY = 6000L;
    private static final List<String> ROSTER = List.of(
        "locomotive_diesel_v60",
        "locomotive_steam_262t",
        "locomotive_steam_4_4_0",
        "locomotive_steam_a4_mallard",
        "locomotive_steam_adler",
        "locomotive_steam_alco_sc4",
        "locomotive_steam_alice_0_4_0",
        "locomotive_steam_berkshire_1225",
        "locomotive_steam_berkshire_765",
        "locomotive_steam_br_black_5",
        "locomotive_steam_br_britannia",
        "locomotive_steam_br01",
        "locomotive_steam_br80",
        "locomotive_steam_c11",
        "locomotive_steam_c41",
        "locomotive_steam_c41_080",
        "locomotive_steam_c41t",
        "locomotive_steam_c62",
        "locomotive_steam_cherepanov",
        "locomotive_steam_climax",
        "locomotive_steam_climax_2",
        "locomotive_steam_coronation_class",
        "locomotive_steam_d51",
        "locomotive_steam_d51_long",
        "locomotive_steam_forney",
        "locomotive_steam_fowler",
        "locomotive_steam_glyn_042t",
        "locomotive_steam_gs4",
        "locomotive_steam_gwr_101_class",
        "locomotive_steam_gwr_42xx",
        "locomotive_steam_gwr_72xx",
        "locomotive_steam_hall_class",
        "locomotive_steam_heavy",
        "locomotive_steam_j50",
        "locomotive_steam_king_class",
        "locomotive_steam_lssp7",
        "locomotive_steam_midland_compound",
        "locomotive_steam_milw_class_a",
        "locomotive_steam_mogul",
        "locomotive_steam_pannier",
        "locomotive_steam_pe",
        "locomotive_steam_rw_type_2",
        "locomotive_steam_rw_type_3",
        "locomotive_steam_s100_uk",
        "locomotive_steam_s100_us",
        "locomotive_steam_sentinel_y3",
        "locomotive_steam_shay",
        "locomotive_steam_shay_3_truck",
        "locomotive_steam_skookum",
        "locomotive_steam_small",
        "locomotive_steam_snow_plow",
        "locomotive_steam_southern1102",
        "locomotive_steam_star_class",
        "locomotive_steam_u57",
        "locomotive_steam_ussr",
        "locomotive_steam_vb_0_4_0",
        "locomotive_steam_vb_shay",
        "locomotive_steam_vb_shay_2",
        "locomotive_steam_wwcp_062t"
    );

    // Read-only size references from the DEV54E viewer's original 48 offline TCM mesh bounds;
    // 11 missing meshes use a conservative 9.5-block initial distance. These are DRAFT
    // camera framing estimates, NOT in-game wheel/model dimensions. Operator can override.
    private static final double[] AUTO_CAMERA_BLOCKS = {
        7.0d, // 01 locomotive_diesel_v60 (small shunter; close view from recorded footage),
        8.4d, // 02 locomotive_steam_262t (offline mesh span 4.63),
        7.3d, // 03 locomotive_steam_4_4_0 (offline mesh span 3.33),
        11.1d, // 04 locomotive_steam_a4_mallard (offline mesh span 7.72),
        7.0d, // 05 locomotive_steam_adler (offline mesh span 2.32),
        9.3d, // 06 locomotive_steam_alco_sc4 (offline mesh span 5.68),
        7.7d, // 07 locomotive_steam_alice_0_4_0 (offline mesh span 3.81),
        11.0d, // 08 locomotive_steam_berkshire_1225 (offline mesh span 7.63),
        11.0d, // 09 locomotive_steam_berkshire_765 (offline mesh span 7.63),
        8.5d, // 10 locomotive_steam_br_black_5 (offline mesh span 4.66),
        9.1d, // 11 locomotive_steam_br_britannia (offline mesh span 5.42),
        9.5d, // 12 locomotive_steam_br01 (mesh unavailable, conservative default),
        7.5d, // 13 locomotive_steam_br80 (offline mesh span 3.50),
        8.6d, // 14 locomotive_steam_c11 (offline mesh span 4.84),
        9.0d, // 15 locomotive_steam_c41 (offline mesh span 5.32),
        9.1d, // 16 locomotive_steam_c41_080 (offline mesh span 5.43),
        9.3d, // 17 locomotive_steam_c41t (offline mesh span 5.68),
        9.5d, // 18 locomotive_steam_c62 (mesh unavailable, conservative default),
        7.0d, // 19 locomotive_steam_cherepanov (offline mesh span 2.38),
        8.5d, // 20 locomotive_steam_climax (offline mesh span 4.75),
        8.5d, // 21 locomotive_steam_climax_2 (offline mesh span 4.65),
        11.2d, // 22 locomotive_steam_coronation_class (offline mesh span 7.84),
        9.5d, // 23 locomotive_steam_d51 (mesh unavailable, conservative default),
        9.5d, // 24 locomotive_steam_d51_long (mesh unavailable, conservative default),
        9.5d, // 25 locomotive_steam_forney (mesh unavailable, conservative default),
        8.2d, // 26 locomotive_steam_fowler (offline mesh span 4.32),
        7.9d, // 27 locomotive_steam_glyn_042t (offline mesh span 4.00),
        11.2d, // 28 locomotive_steam_gs4 (offline mesh span 7.94),
        8.0d, // 29 locomotive_steam_gwr_101_class (offline mesh span 4.06),
        9.5d, // 30 locomotive_steam_gwr_42xx (offline mesh span 5.94),
        10.2d, // 31 locomotive_steam_gwr_72xx (offline mesh span 6.69),
        9.8d, // 32 locomotive_steam_hall_class (offline mesh span 6.19),
        9.5d, // 33 locomotive_steam_heavy (mesh unavailable, conservative default),
        8.6d, // 34 locomotive_steam_j50 (offline mesh span 4.84),
        10.4d, // 35 locomotive_steam_king_class (offline mesh span 6.91),
        7.0d, // 36 locomotive_steam_lssp7 (offline mesh span 2.13),
        9.1d, // 37 locomotive_steam_midland_compound (offline mesh span 5.44),
        10.3d, // 38 locomotive_steam_milw_class_a (offline mesh span 6.84),
        9.5d, // 39 locomotive_steam_mogul (mesh unavailable, conservative default),
        10.0d, // 40 locomotive_steam_pannier (offline mesh span 6.50),
        8.9d, // 41 locomotive_steam_pe (offline mesh span 5.13),
        7.3d, // 42 locomotive_steam_rw_type_2 (offline mesh span 3.31),
        8.1d, // 43 locomotive_steam_rw_type_3 (offline mesh span 4.25),
        7.9d, // 44 locomotive_steam_s100_uk (offline mesh span 4.01),
        7.9d, // 45 locomotive_steam_s100_us (offline mesh span 4.00),
        7.1d, // 46 locomotive_steam_sentinel_y3 (offline mesh span 3.06),
        9.5d, // 47 locomotive_steam_shay (mesh unavailable, conservative default),
        7.6d, // 48 locomotive_steam_shay_3_truck (offline mesh span 3.63),
        9.4d, // 49 locomotive_steam_skookum (offline mesh span 5.75),
        9.5d, // 50 locomotive_steam_small (mesh unavailable, conservative default),
        9.4d, // 51 locomotive_steam_snow_plow (offline mesh span 5.75),
        9.0d, // 52 locomotive_steam_southern1102 (offline mesh span 5.25),
        7.2d, // 53 locomotive_steam_star_class (offline mesh span 3.14),
        9.6d, // 54 locomotive_steam_u57 (offline mesh span 6.00),
        9.5d, // 55 locomotive_steam_ussr (mesh unavailable, conservative default),
        7.0d, // 56 locomotive_steam_vb_0_4_0 (offline mesh span 2.50),
        9.2d, // 57 locomotive_steam_vb_shay (offline mesh span 5.50),
        8.4d, // 58 locomotive_steam_vb_shay_2 (offline mesh span 4.58),
        9.4d, // 59 locomotive_steam_wwcp_062t (offline mesh span 5.81)
    };
    private static Session active;
    private Dev54FCaptureCommand() {}

    private static final class Session {
        final UUID owner;
        final ServerLevel level;
        final BlockPos center;
        final Vec3 before;
        final float yaw, pitch;
        final boolean wasFlying;
        final long oldDay;
        final int oldClear, oldRainTime, oldThunderTime;
        final boolean oldRain, oldThunder;
        int index;
        int side; // 0: camera east, 1: camera west
        int tick = -WAIT_RECORD;
        int entityId = -1;
        int heartbeat;
        String phase = "";
        boolean paused;
        boolean halted;
        boolean stationMode;
        boolean stationArmed;
        int stationSide; // 0 east or 1 west; manually chosen using the player position
        int manualCameraBlocks; // zero means safe per-train automatic framing
        Session(ServerPlayer p, BlockPos c, int resumeIndex) {
            owner = p.getUUID(); level = p.serverLevel(); center = c;
            before = p.position(); yaw = p.getYRot(); pitch = p.getXRot();
            wasFlying = p.getAbilities().flying;
            oldDay = level.getDayTime();
            ServerLevelData w = (ServerLevelData) level.getLevelData();
            oldClear = w.getClearWeatherTime(); oldRainTime = w.getRainTime();
            oldThunderTime = w.getThunderTime(); oldRain = w.isRaining(); oldThunder = w.isThundering();
            index = resumeIndex;
        }
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent evt) {
        Dev54H2StationaryFleet.register(evt);
        CommandDispatcher<CommandSourceStack> d = evt.getDispatcher();
        d.register(Commands.literal("tc_capture").requires(c -> c.hasPermission(2))
            .executes(c -> help(c.getSource()))
            .then(Commands.literal("course").executes(c -> course(c.getSource())))
            .then(Commands.literal("station")
                .executes(c -> stationSetup(c.getSource()))
                .then(Commands.literal("setup").executes(c -> stationSetup(c.getSource())))
                .then(Commands.literal("record").executes(c -> stationRecord(c.getSource(), false)))
                .then(Commands.literal("restart").executes(c -> stationRecord(c.getSource(), true))))
            .then(Commands.literal("start").executes(c -> start(c.getSource(), false)))
            .then(Commands.literal("restart").executes(c -> start(c.getSource(), true)))
            .then(Commands.literal("pause").executes(c -> pause(c.getSource(), true)))
            .then(Commands.literal("resume").executes(c -> pause(c.getSource(), false)))
            .then(Commands.literal("next").executes(c -> seek(c.getSource(), 1)))
            .then(Commands.literal("previous").executes(c -> seek(c.getSource(), -1)))
            .then(Commands.literal("distance")
                .then(Commands.literal("auto").executes(c -> setCameraDistance(c.getSource(),0)))
                .then(Commands.argument("blocks", IntegerArgumentType.integer(5,18))
                    .executes(c -> setCameraDistance(c.getSource(),IntegerArgumentType.getInteger(c,"blocks")))))
            .then(Commands.literal("status").executes(c -> status(c.getSource())))
            .then(Commands.literal("stop").executes(c -> stop(c.getSource()))));
    }
    private static int help(CommandSourceStack s) {
        message(s, "[DEV54F] Disposable Creative Superflat /tc_capture course builds safe vanilla rails and starts automated recording.");
        message(s, "[DEV54F4 STATION] /tc_capture station setup | station record | station restart");
        message(s, "[DEV54F4] STATION: two low platforms; YOU choose your camera angle, never auto-teleported during recording.");
        message(s, "[DEV54F4] Station passes one camera side at a time; each side has its own saved progress.");
        message(s, "[DEV54F3] /tc_capture distance 5..18 | distance auto  (COURSE mode only)");
        message(s, "[DEV54F] /tc_capture start | restart | pause | resume | previous | next | status | stop");
        message(s, "[DEV54F3] Close low eye-level EAST/WEST camera; smoothly tracks test cart. 10s countdown. Recording is external.");
        message(s, "[DEV54F] Separate renderer masks ONLY exact SHA-matched user-identified rod-only chunks; others remain visible.");
        return 1;
    }
    private static void message(CommandSourceStack s, String txt) { s.sendSuccess(() -> Component.literal(txt),false); }
    private static void notify(ServerPlayer p,String txt) { p.displayClientMessage(Component.literal(txt),false); }
    private static Path saved(ServerLevel l) { return l.getServer().getWorldPath(LevelResource.ROOT).resolve("DEV54F-CAPTURE-PROGRESS.txt"); }
    private static Path report(ServerLevel l) { return l.getServer().getWorldPath(LevelResource.ROOT).resolve("DEV54F-CAPTURE-SEGMENTS.csv"); }
    private static Path stationProgress(ServerLevel l,int side) {
        return l.getServer().getWorldPath(LevelResource.ROOT).resolve("DEV54F4-STATION-"+(side==0?"EAST":"WEST")+"-PROGRESS.txt");
    }
    private static Path stationReport(ServerLevel l) {
        return l.getServer().getWorldPath(LevelResource.ROOT).resolve("DEV54F4-STATION-CAPTURE-SEGMENTS.csv");
    }
    private static Path progressFile(Session s) {return s.stationMode?stationProgress(s.level,s.stationSide):saved(s.level);}
    private static Path reportFile(Session s) {return s.stationMode?stationReport(s.level):report(s.level);}
    private static Path marker() { return FMLPaths.GAMEDIR.get().resolve("DEV54F-RODS-HIDDEN-ON.txt"); }
    private static void markerRefresh() {
        try { Files.writeString(marker(),"ON " + (System.currentTimeMillis()+12000) + "\n",StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); }
        catch (IOException ex) { System.err.println("[DEV54F MASK] Unable to set temporary hide marker: "+ex); }
    }
    private static void markerOff() {
        try { Files.deleteIfExists(marker()); }
        catch(IOException ex) { System.err.println("[DEV54F MASK] Could not delete marker (expires in 12 seconds): "+ex); }
    }
    private static int readProgress(ServerLevel level) {return readProgressFile(saved(level));}
    private static int readStationProgress(ServerLevel level,int side) {return readProgressFile(stationProgress(level,side));}
    private static int readProgressFile(Path f) {
        try { if(!Files.isRegularFile(f))return 0;
            int v=Integer.parseInt(Files.readString(f,StandardCharsets.UTF_8).strip());
            return Math.max(0,Math.min(ROSTER.size(),v));
        } catch(Exception ex) { return 0; }
    }
    private static boolean writeProgress(Session s) {
        try { Files.writeString(progressFile(s),Integer.toString(s.index)+"\n",StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);return true; }
        catch(IOException ex) { System.err.println("[DEV54F SAVE HOLD] "+ex);return false; }
    }
    private static void log(Session s,String side,String phase,String result) {
        try {
            Path p=reportFile(s);
            if(!Files.exists(p))Files.writeString(p,"epoch_millis,index,registry_id,camera_side,phase,result\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            Files.writeString(p,System.currentTimeMillis()+","+(s.index+1)+","+ROSTER.get(s.index)+","+side+","+phase+","+result+"\n",StandardCharsets.UTF_8,StandardOpenOption.APPEND);
        } catch(IOException ex) {System.err.println("[DEV54F SEGMENT LOG HOLD] "+ex);s.halted=true;}
    }
    private static String owner(UUID u) {return "tc_dev54f_owner_"+u.toString().replace("-", "");}
    private static void discardMinecart(Session s) {
        if(s.entityId!=-1) {
            Entity e=s.level.getEntity(s.entityId);
            if(e!=null && e.getTags().contains(OWNED) && e.getTags().contains(owner(s.owner)))e.discard();
            s.entityId=-1;
        }
    }
    private static void cleanOwned(Session s) {
        discardMinecart(s);
        List<Entity> discard=new ArrayList<>();
        for(Entity e:s.level.getAllEntities()) if(e.getTags().contains(OWNED) && e.getTags().contains(owner(s.owner))) discard.add(e);
        for(Entity e:discard)e.discard();
    }
    private static void weatherMidday(Session s) {
        s.level.setDayTime(MIDDAY);
        s.level.setWeatherParameters(0,0,false,false);
    }
    private static void restore(Session s, boolean teleport) {
        cleanOwned(s);markerOff();
        s.level.setDayTime(s.oldDay);
        ServerLevelData w=(ServerLevelData) s.level.getLevelData();
        w.setClearWeatherTime(s.oldClear); w.setRainTime(s.oldRainTime);
        w.setThunderTime(s.oldThunderTime);w.setRaining(s.oldRain);w.setThundering(s.oldThunder);
        ServerPlayer p=s.level.getServer().getPlayerList().getPlayer(s.owner);
        if(p!=null && p.serverLevel()==s.level) {
            // Station view stays where operator positioned it; restore flight only.
            if(teleport && !s.stationMode)p.connection.teleport(s.before.x,s.before.y,s.before.z,s.yaw,s.pitch);
            p.getAbilities().flying=s.wasFlying; p.onUpdateAbilities();
        }
    }
    private static boolean railSafe(ServerLevel l, BlockPos c) {
        for(int dz=-9;dz<=9;dz++) {
            BlockPos p=c.offset(0,0,dz);
            if(!l.isLoaded(p))return false;
            BlockState b=l.getBlockState(p);
            if(!b.is(Blocks.RAIL) || b.getValue(RailBlock.SHAPE)!=RailShape.NORTH_SOUTH)return false;
            for(int dy=1;dy<=4;dy++) if(!l.getBlockState(p.above(dy)).isAir())return false;
        }
        AABB box=new AABB(c.getX()-2,c.getY(),c.getZ()-10,c.getX()+3,c.getY()+5,c.getZ()+11);
        return l.getEntitiesOfClass(AbstractMinecart.class,box,e->!e.getTags().contains(OWNED)).isEmpty();
    }
    // The player camera now flies lower; require a clear body/head volume at EVERY
    // allowed near/wide distance, on both sides, before starting in the disposable world.
    // We never clear/replace world blocks for the camera.
    private static boolean cameraClear(ServerLevel l,BlockPos c,int side) {
        int dir=side==0?1:-1;
        for(int d=5;d<=18;d++)for(int dy=0;dy<=3;dy++){
            BlockPos spot=c.offset(dir*d,dy,0);
            if(!l.isLoaded(spot)||!l.getBlockState(spot).isAir())return false;
        }
        return true;
    }
    private static boolean courseClear(ServerLevel l,BlockPos c) {
        if(c.getY()<l.getMinBuildHeight()+2 || c.getY()>l.getMaxBuildHeight()-6)return false;
        for(int dz=-9;dz<=9;dz++)for(int dx=-1;dx<=1;dx++) {
            BlockPos p=c.offset(dx,0,dz);BlockPos below=p.below();
            if(!l.isLoaded(p)||!l.isLoaded(below))return false;
            BlockState under=l.getBlockState(below);
            if(!under.isAir()&&!under.isFaceSturdy(l,below,Direction.UP))return false;
            for(int dy=0;dy<=4;dy++)if(!l.getBlockState(p.above(dy)).isAir())return false;
        }
        AABB box=new AABB(c.getX()-2,c.getY()-1,c.getZ()-10,c.getX()+3,c.getY()+5,c.getZ()+11);
        return l.getEntitiesOfClass(AbstractMinecart.class,box,e->true).isEmpty();
    }
    private static boolean buildCourse(ServerLevel l,BlockPos c) {
        List<BlockPos> placedSupport=new ArrayList<>(),placedRail=new ArrayList<>();boolean success=false;
        try {
            if(!courseClear(l,c))return false;
            for(int dz=-9;dz<=9;dz++)for(int dx=-1;dx<=1;dx++) {
                BlockPos b=c.offset(dx,-1,dz);
                if(l.getBlockState(b).isAir()){
                    if(!l.setBlock(b,Blocks.SMOOTH_STONE.defaultBlockState(),3))return false;
                    placedSupport.add(b);
                }
            }
            BlockState rail=Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE,RailShape.NORTH_SOUTH);
            for(int dz=-9;dz<=9;dz++){
                BlockPos p=c.offset(0,0,dz);
                if(!l.getBlockState(p).isAir()||!l.setBlock(p,rail,3))return false;
                placedRail.add(p);
            }
            success=railSafe(l,c);return success;
        } finally {
            if(!success) {
                for(BlockPos p:placedRail)if(l.getBlockState(p).is(Blocks.RAIL))l.setBlock(p,Blocks.AIR.defaultBlockState(),3);
                for(BlockPos p:placedSupport)if(l.getBlockState(p).is(Blocks.SMOOTH_STONE))l.setBlock(p,Blocks.AIR.defaultBlockState(),3);
            }
        }
    }
    private static BlockPos nearbyRail(ServerPlayer p) {
        ServerLevel l=p.serverLevel();BlockPos pos=p.blockPosition();BlockPos best=null;double dist=Double.MAX_VALUE;
        // The previous camera was 15 blocks out: allow finding the exact existing
        // safe track from that saved viewpoint when restarting a close-up recording.
        for(int y=pos.getY()-2;y<=pos.getY()+2;y++)for(int x=pos.getX()-20;x<=pos.getX()+20;x++)for(int z=pos.getZ()-20;z<=pos.getZ()+20;z++){
            double d=pos.distSqr(new BlockPos(x,y,z));
            if(d>425 || d>=dist)continue;
            BlockPos c=new BlockPos(x,y,z);if(!railSafe(l,c))continue;
            if(!cameraClear(l,c,0)||!cameraClear(l,c,1))continue;
            best=c;dist=d;
        }
        return best;
    }
    // STATION MODE: no force-replacement of existing blocks and no teleporting the camera.
    // Only disposable, local Creative Superflat worlds are allowed for station construction.
    private static boolean stationGate(ServerPlayer p, CommandSourceStack src) {
        if(!gate(p,src))return false;
        if(!(p.serverLevel().getChunkSource().getGenerator() instanceof FlatLevelSource)) {
            message(src,"[DEV54F4 SAFETY HOLD] Station building requires a disposable Creative Superflat world.");
            return false;
        }
        return true;
    }
    private static boolean platformCell(BlockState bs) {
        return bs.isAir() || bs.is(Blocks.SMOOTH_STONE_SLAB) || bs.is(Blocks.CUT_SANDSTONE_SLAB);
    }
    private static boolean stationPlatformClear(ServerLevel l,BlockPos c) {
        for(int dir:new int[]{-1,1})for(int d=5;d<=6;d++)for(int z=-8;z<=8;z++) {
            BlockPos p=c.offset(dir*d,0,z);
            if(!l.isLoaded(p)||!platformCell(l.getBlockState(p)))return false;
            for(int y=1;y<=3;y++)if(!l.getBlockState(p.above(y)).isAir())return false;
        }
        return true;
    }
    // Low, unrailed stone-slab viewing platforms, not raised barriers in front of wheels.
    // Only empty cells are placed. A partial failure removes ONLY the cells just placed.
    private static boolean buildStationPlatform(ServerLevel l,BlockPos c) {
        if(!stationPlatformClear(l,c))return false;
        List<BlockPos> added=new ArrayList<>();boolean success=false;
        try {
            for(int dir:new int[]{-1,1})for(int d=5;d<=6;d++)for(int z=-8;z<=8;z++) {
                BlockPos p=c.offset(dir*d,0,z);
                if(!l.getBlockState(p).isAir())continue;
                BlockState tile=(z==0||z==-8||z==8) && d==6
                    ? Blocks.CUT_SANDSTONE_SLAB.defaultBlockState()
                    : Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
                if(!l.setBlock(p,tile,3))return false;
                added.add(p);
            }
            success=true;return true;
        } finally {
            if(!success)for(BlockPos p:added)
                if(platformCell(l.getBlockState(p)))l.setBlock(p,Blocks.AIR.defaultBlockState(),3);
        }
    }
    // Find established 19-rail corridor from either platform; no F3 cameraClear, since
    // the new stone viewing platform intentionally occupies some of that air space.
    private static BlockPos stationRailNear(ServerPlayer p) {
        ServerLevel l=p.serverLevel(); BlockPos origin=p.blockPosition();
        BlockPos best=null;double nearest=Double.MAX_VALUE;
        for(int y=origin.getY()-3;y<=origin.getY()+3;y++)
            for(int x=origin.getX()-20;x<=origin.getX()+20;x++)
                for(int z=origin.getZ()-20;z<=origin.getZ()+20;z++) {
                    BlockPos c=new BlockPos(x,y,z);
                    double d=origin.distSqr(c);
                    if(d>500 || d>=nearest || !railSafe(l,c) || !stationPlatformClear(l,c))continue;
                    nearest=d;best=c;
                }
        return best;
    }
    private static int stationSetup(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p=src.getPlayerOrException();
        if(!stationGate(p,src))return 0;
        if(active!=null){message(src,"[DEV54F4 HOLD] /tc_capture stop before setting up a new station.");return 0;}
        ServerLevel l=p.serverLevel();
        BlockPos c=stationRailNear(p);
        if(c==null) {
            BlockPos origin=p.blockPosition();
            int[] xs={7,-7,9,-9,11,-11};int[] zs={0,2,-2};int[] ys={0,-1,1};
            outer: for(int dy:ys)for(int dz:zs)for(int dx:xs){
                BlockPos candidate=origin.offset(dx,dy,dz);
                if(!courseClear(l,candidate)||!stationPlatformClear(l,candidate))continue;
                if(buildCourse(l,candidate)){c=candidate;break outer;}
            }
            if(c==null){message(src,"[DEV54F4 HOLD] Need a clear 19-rail + two-platform site in disposable Superflat. Fly to empty flat ground and retry.");return 0;}
            message(src,"[DEV54F4] Built 19 vanilla rails on safe empty cells.");
        }
        if(!buildStationPlatform(l,c)){
            message(src,"[DEV54F4 HOLD] Platforms obstructed; nothing overwritten. Find another clear site.");return 0;
        }
        int side=p.getX()>=c.getX()+0.5?0:1;
        int idx=readStationProgress(l,side);
        if(idx>=ROSTER.size())idx=0; // setup-only visual preview; restart command controls save reset
        Session s=new Session(p,c,idx);
        s.stationMode=true;s.stationSide=side;s.side=side;s.paused=true;
        active=s;
        weatherMidday(s);markerRefresh();
        if(!spawn(s,p)) {
            active=null;restore(s,false);
            message(src,"[DEV54F4 HOLD] Could not spawn first preview train. World left untouched except the new station stage.");return 0;
        }
        p.getAbilities().flying=true;p.onUpdateAbilities();
        message(src,"[DEV54F4 STATION READY] Stand or fly on either stone viewing platform; aim at the parked locomotive yourself.");
        message(src,"[DEV54F4] When your camera looks right and recording is ready: /tc_capture station record");
        message(src,"[DEV54F4] To reset progress on the side you're standing on: /tc_capture station restart");
        message(src,"[DEV54F4] One side per pass; camera NEVER moved by station automation. /tc_capture stop to exit.");
        return 1;
    }
    private static int stationRecord(CommandSourceStack src,boolean restart) throws CommandSyntaxException {
        ServerPlayer p=src.getPlayerOrException();Session s=active;
        if(s==null||!s.stationMode||!s.owner.equals(p.getUUID())) {
            message(src,"[DEV54F4 HOLD] Run /tc_capture station setup first, then line up your camera.");return 0;
        }
        if(!s.paused && !s.halted){message(src,"[DEV54F4] Station recording is already in progress. /tc_capture pause to stop motion.");return 0;}
        // Don't put a train into motion with its camera operator standing on the rails.
        double lateral=p.getX()-(s.center.getX()+0.5);
        double along=p.getZ()-(s.center.getZ()+0.5);
        double vertical=p.getY()-s.center.getY();
        if(Math.abs(lateral)<4 || Math.abs(lateral)>20 || Math.abs(along)>11 || vertical< -1 || vertical>12){
            message(src,"[DEV54F4 CAMERA HOLD] Fly 4-20 blocks EAST or WEST of rails, within 11 blocks lengthwise and <=12 above track. YOU control aim/zoom.");return 0;
        }
        int side=lateral>0?0:1;
        int idx=restart?0:readStationProgress(s.level,side);
        if(idx>=ROSTER.size()){
            message(src,"[DEV54F4 COMPLETE] That side was already recorded. /tc_capture station restart resets ONLY this side.");return 0;
        }
        s.stationSide=side;s.side=side;s.index=idx;s.tick=-WAIT_RECORD;s.phase="";s.halted=false;s.paused=false;s.stationArmed=true;
        if(restart&&!writeProgress(s)){s.paused=true;return 0;}
        cleanOwned(s);
        if(!spawn(s,p)){s.paused=true;message(src,"[DEV54F4 HOLD] Preview locomotive failed to spawn. Try /tc_capture stop.");return 0;}
        notify(p,"[DEV54F4 STATION] "+(side==0?"EAST":"WEST")+" side recording in 10 seconds, "+(s.index+1)+"/"+ROSTER.size()+". Camera will NOT move. Start screen recording now!");
        return 1;
    }
    private static int course(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer p=source.getPlayerOrException();
        if(!gate(p,source))return 0;
        if(active!=null){message(source,"[DEV54F HOLD] Recording already active. /tc_capture stop first.");return 0;}
        BlockPos c=nearbyRail(p);
        if(c!=null){message(source,"[DEV54F] Reusing safe existing 19 rails.");return initiate(source,p,c,false);}
        ServerLevel l=p.serverLevel();BlockPos origin=p.blockPosition();
        int[] dx={6,-6,7,-7,8,-8};int[] dz={0,2,-2,3,-3};int[] dy={-1,0,-2,1};
        for(int y:dy)for(int z:dz)for(int x:dx){
            BlockPos candidate=origin.offset(x,y,z);
            if(!courseClear(l,candidate))continue;
            if(!cameraClear(l,candidate,0)||!cameraClear(l,candidate,1))continue;
            if(!buildCourse(l,candidate)){
                message(source,"[DEV54F HOLD] Course placement failed and newly placed cells were rolled back.");return 0;
            }
            message(source,"[DEV54F COURSE] Built 19 vanilla rails without replacing world blocks in your disposable world.");
            return initiate(source,p,candidate,false);
        }
        message(source,"[DEV54F HOLD] No 19-rail corridor with clear camera spots found. Fly to clear sky in a new Creative Superflat world and retry.");
        return 0;
    }
    private static boolean gate(ServerPlayer p,CommandSourceStack source) {
        if(Dev54H2StationaryFleet.isActive()){message(source,"[DEV54H2 HOLD] Stop /tc_stationary59 before /tc_capture.");return false;}
        if(!p.getAbilities().instabuild) {message(source,"[DEV54F STOP] Creative mode required.");return false;}
        MinecraftServer server=p.getServer();
        if(server==null || server.isDedicatedServer()) {message(source,"[DEV54F STOP] Local single-player world only; remote clients cannot share temporary renderer-mask marker.");return false;}
        return true;
    }
    private static int start(CommandSourceStack source, boolean restart) throws CommandSyntaxException {
        ServerPlayer p=source.getPlayerOrException();if(!gate(p,source))return 0;
        if(active!=null) {message(source,"[DEV54F HOLD] A recording is already active. /tc_capture stop first.");return 0;}
        BlockPos c=nearbyRail(p);
        if(c==null){message(source,"[DEV54F HOLD] No safe existing 19 vanilla rails and two clear camera spots. Use /tc_capture course near a clear area.");return 0;}
        return initiate(source,p,c,restart);
    }
    private static int initiate(CommandSourceStack source,ServerPlayer p,BlockPos c,boolean restart) {
        int index=restart?0:readProgress(p.serverLevel());
        if(index>=ROSTER.size()){message(source,"[DEV54F COMPLETE] Use /tc_capture restart for a fresh recording.");return 1;}
        if(!cameraClear(p.serverLevel(),c,0)||!cameraClear(p.serverLevel(),c,1)){
            message(source,"[DEV54F HOLD] Both camera positions must be loaded and clear.");return 0;
        }
        Session s=new Session(p,c,index);active=s;
        if(restart && !writeProgress(s)){active=null;return 0;}
        weatherMidday(s);markerRefresh();
        camera(s,p,0);p.getAbilities().flying=true;p.onUpdateAbilities();
        notify(p,"[DEV54F CAPTURE] Set recording NOW. Starting in 10 seconds; F1 for clean HUD is optional, but name labels use HUD.");
        return 1;
    }
    private static double cameraBlocks(Session s) {
        return s.manualCameraBlocks!=0?s.manualCameraBlocks:AUTO_CAMERA_BLOCKS[s.index];
    }
    private static void camera(Session s,ServerPlayer p,int side) {
        double dx=(side==0?1:-1)*cameraBlocks(s);
        // Only the temporary, named, OWNED capture cart is followed. Never track
        // unrelated minecarts/entities; the fixed route origin remains the fallback.
        double z=s.center.getZ()+0.5;
        Entity subject=s.level.getEntity(s.entityId);
        if(subject instanceof AbstractMinecart && !subject.isRemoved()
                && subject.getTags().contains(OWNED)
                && subject.getTags().contains(owner(s.owner))
                && Math.abs(subject.getZ()-z)<=7.0) z=subject.getZ();
        double x=s.center.getX()+0.5+dx;
        double y=s.center.getY()+0.70; // player FEET; eye height adds about 1.62 blocks
        p.setDeltaMovement(Vec3.ZERO);
        p.connection.teleport(x,y,z,side==0?90f:-90f,6f);
        s.side=side;
    }
    private static int setCameraDistance(CommandSourceStack src,int blocks)throws CommandSyntaxException{
        ServerPlayer p=src.getPlayerOrException();Session s=active;
        if(s==null||!s.owner.equals(p.getUUID())){
            message(src,"[DEV54F3] Start /tc_capture course first; distance control works during an active session.");return 0;
        }
        if(blocks!=0 && (blocks<5 || blocks>18))return 0;
        if(s.stationMode){message(src,"[DEV54F4] Station camera is manual: move/fly and aim freely. Distance command is COURSE-only.");return 0;}
        s.manualCameraBlocks=blocks;camera(s,p,s.side);
        message(src,"[DEV54F3 CAMERA] "+(blocks==0?
            "AUTO "+String.format(java.util.Locale.ROOT,"%.1f",cameraBlocks(s))+" blocks for current locomotive":
            blocks+" blocks (manual for remaining session)")+
            "; tracking locomotive during motion. /tc_capture distance auto resets.");
        return 1;
    }
    private static boolean spawn(Session s,ServerPlayer p) {
        cleanOwned(s);
        String id=ROSTER.get(s.index);
        EntityType<?> type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(Traincraft.MOD_ID,id));
        if(type==null){log(s,"BOTH","SPAWN","REGISTRY_MISSING");notify(p,"[DEV54F SKIP] Registry missing "+id);return false;}
        Entity entity;
        try {entity=type.create(s.level);}catch(Exception bad){log(s,"BOTH","SPAWN","CONSTRUCTOR_ERROR");return false;}
        if(!(entity instanceof AbstractMinecart cart)){
            if(entity!=null)entity.discard();log(s,"BOTH","SPAWN","NOT_MINECART");return false;
        }
        cart.moveTo(s.center.getX()+0.5,s.center.getY()+0.2,s.center.getZ()+0.5,180f,0f);
        cart.setDeltaMovement(Vec3.ZERO);cart.setInvulnerable(true);
        cart.addTag(OWNED);cart.addTag(owner(s.owner));
        cart.setCustomName(Component.literal("DEV54F "+(s.index+1)+"/"+ROSTER.size()+"  "+id));
        cart.setCustomNameVisible(true);
        if(!s.level.addFreshEntity(cart)){log(s,"BOTH","SPAWN","ADD_ENTITY_FAILED");return false;}
        s.entityId=cart.getId();return true;
    }
    private static int pause(CommandSourceStack src,boolean pause) throws CommandSyntaxException {
        ServerPlayer p=src.getPlayerOrException();Session s=active;
        if(s==null||!s.owner.equals(p.getUUID())){message(src,"[DEV54F HOLD] No owned recording session.");return 0;}
        if(s.stationMode && !pause && !s.stationArmed){
            message(src,"[DEV54F4] Use /tc_capture station record after aiming your camera; resume is for paused recordings.");return 0;
        }
        s.paused=pause;
        Entity e=s.level.getEntity(s.entityId);
        if(e instanceof AbstractMinecart c)c.setDeltaMovement(Vec3.ZERO);
        message(src,pause?"[DEV54F PAUSED] Weather/daytime remain locked. /tc_capture resume":"[DEV54F RESUMED]");return 1;
    }
    private static int seek(CommandSourceStack src,int delta)throws CommandSyntaxException {
        ServerPlayer p=src.getPlayerOrException();Session s=active;
        if(s==null||!s.owner.equals(p.getUUID())){message(src,"[DEV54F HOLD] No owned recording session.");return 0;}
        int newIndex=Math.max(0,Math.min(ROSTER.size()-1,s.index+delta));
        if(newIndex==s.index)return 1;
        boolean staged=s.stationMode && s.paused;
        cleanOwned(s);s.index=newIndex;s.side=s.stationMode?s.stationSide:0;s.tick=0;s.phase="";s.halted=false;
        if(!writeProgress(s)){s.halted=true;return 0;}
        if(s.stationMode) {
            if(!spawn(s,p)){s.halted=true;return 0;}
            s.paused=staged;
        } else camera(s,p,0);
        notify(p,"[DEV54F MANUAL] "+(s.index+1)+"/"+ROSTER.size()+" "+ROSTER.get(s.index));return 1;
    }
    private static int status(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p=src.getPlayerOrException();Session s=active;
        message(src,"[DEV54F] active="+(s!=null)+" index="+(s==null?readProgress(p.serverLevel())+1:s.index+1)+"/"+ROSTER.size()+" current="+(s==null?"none":ROSTER.get(s.index)));
        if(s!=null && s.stationMode) message(src,"[DEV54F4 STATION] manual camera; side="+(s.stationSide==0?"EAST":"WEST")+"; paused="+s.paused+"; next="+(s.index+1)+"/"+ROSTER.size());
        if(s!=null && !s.stationMode)message(src,"[DEV54F3 CAMERA] "+
            String.format(java.util.Locale.ROOT,"%.1f",cameraBlocks(s))+
            " blocks "+(s.manualCameraBlocks==0?"AUTO":"MANUAL"));
        message(src,"[DEV54F FILES] "+saved(p.serverLevel())+" | "+report(p.serverLevel()));
        message(src,"[DEV54F4 STATION FILES] "+stationProgress(p.serverLevel(),0)+" | "+stationProgress(p.serverLevel(),1)+" | "+stationReport(p.serverLevel()));return 1;
    }
    private static int stop(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p=src.getPlayerOrException();Session s=active;
        if(s==null||!s.owner.equals(p.getUUID())){message(src,"[DEV54F] No active recording.");return 0;}
        active=null;restore(s,true);
        message(src,s.stationMode?
            "[DEV54F4 STATION STOPPED] Camera POSITION preserved, flight/time/weather restored. Saved side progress remains.":
            "[DEV54F STOPPED] Camera, flight, world time and weather restored. Current locomotive retained for resume.");return 1;
    }
    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent evt) {
        Dev54H2StationaryFleet.logout(evt);
        if(evt.getEntity() instanceof ServerPlayer p && active!=null && active.owner.equals(p.getUUID())) {
            Session s=active;active=null;restore(s,false);
        }
    }
    @SubscribeEvent
    public static void shuttingDown(ServerStoppingEvent evt) {
        Dev54H2StationaryFleet.shutdown();
        Session s=active;active=null;if(s!=null)restore(s,false);else markerOff();
    }
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        Dev54H2StationaryFleet.tick(e);
        if(e.phase!=TickEvent.Phase.END)return;
        Session s=active;if(s==null)return;
        MinecraftServer server=ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        ServerPlayer p=server.getPlayerList().getPlayer(s.owner);
        if(p==null||p.serverLevel()!=s.level){active=null;restore(s,false);return;}
        if(s.heartbeat++%20==0)weatherMidday(s);
        if(s.heartbeat%80==0)markerRefresh();
        if(s.paused||s.halted)return;
        if(s.tick<0){
            if(s.tick%20==0) p.displayClientMessage(Component.literal("[DEV54F RECORDING STARTS IN "+(-s.tick/20)+"s] "+ROSTER.get(s.index)),true);
            s.tick++;return;
        }
        if(s.index>=ROSTER.size()){active=null;restore(s,true);notify(p,"[DEV54F COMPLETE] All "+ROSTER.size()+" entries processed; files saved.");return;}
        int pass=s.tick/SIDE_TICKS;
        int side=s.stationMode?s.stationSide:pass;
        int within=s.tick%SIDE_TICKS;
        if(pass>=(s.stationMode?1:2)){
            log(s,s.stationMode?(s.stationSide==0?"EAST":"WEST"):"BOTH","END","COMPLETE");
            cleanOwned(s);s.index++;
            if(!writeProgress(s)){s.halted=true;notify(p,"[DEV54F HOLD] Progress write failed; /tc_capture stop.");return;}
            if(s.index>=ROSTER.size()){
                active=null;restore(s,true);
                notify(p,"[DEV54F COMPLETE] Full roster complete. "+reportFile(s).toAbsolutePath());return;
            }
            s.tick=0;s.side=s.stationMode?s.stationSide:0;s.phase="";
            if(!s.stationMode)camera(s,p,0);
            return;
        }
        if(!s.stationMode && side!=s.side){
            camera(s,p,side);
            Entity ent=s.level.getEntity(s.entityId);
            if(ent instanceof AbstractMinecart car){
                car.moveTo(s.center.getX()+0.5,s.center.getY()+0.2,s.center.getZ()+0.5,180f,0f);
                car.setDeltaMovement(Vec3.ZERO);
                camera(s,p,side);
            }
            s.phase="";
        }
        if(s.entityId==-1){
            if(!spawn(s,p)){
                // Missing/non-minecart registry entry is explicitly logged and skipped.
                s.tick=(s.stationMode?1:2)*SIDE_TICKS;
                return;
            }
        }
        Entity entity=s.level.getEntity(s.entityId);
        if(!(entity instanceof AbstractMinecart cart)||cart.isRemoved()||!entity.getTags().contains(OWNED)){
            s.halted=true;notify(p,"[DEV54F HOLD] Owned locomotive disappeared. /tc_capture next or stop; no automatic skip.");return;
        }
        double x=s.center.getX()+0.5,z=s.center.getZ()+0.5;
        if(Math.abs(cart.getX()-x)>1.5||Math.abs(cart.getZ()-z)>7.0||!s.level.isLoaded(cart.blockPosition())){
            cart.setDeltaMovement(Vec3.ZERO);s.halted=true;
            notify(p,"[DEV54F SAFETY HOLD] Locomotive outside rail corridor. /tc_capture next or stop.");return;
        }
        String phase;double speed=0;
        if(within<REST){phase="REST";}
        else if(within<REST+FORWARD){phase="FORWARD";speed=-SPEED;}
        else if(within<REST+FORWARD+MIDSTOP){phase="STOP";}
        else if(within<REST+FORWARD+MIDSTOP+REVERSE){phase="REVERSE";speed=SPEED;}
        else{phase="FINAL_STOP";}
        cart.setDeltaMovement(0,0,speed);
        if(!phase.equals(s.phase)) {
            s.phase=phase;
            String sideName=side==0?"EAST":"WEST";
            log(s,sideName,phase,"CAPTURE");
            notify(p,"[DEV54F "+(s.index+1)+"/"+ROSTER.size()+"] "+ROSTER.get(s.index)+" | "+sideName+" | "+phase+" | MIDDAY CLEAR");
        }
        if(within%20==0)p.displayClientMessage(Component.literal((s.stationMode?"[DEV54F4 STATION ":"[DEV54F ")+(s.index+1)+"/"+ROSTER.size()+" "+(side==0?"EAST":"WEST")+" "+phase+"] "+ROSTER.get(s.index)),true);
        s.tick++;
    }
}
