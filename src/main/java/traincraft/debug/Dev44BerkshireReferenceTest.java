package traincraft.debug;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraftforge.event.TickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.List;
import java.util.UUID;

/** R13ak: fixture-only vertical alignment calibration; no global animation changes. */
@Mod.EventBusSubscriber(modid="traincraft",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev44BerkshireReferenceTest {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static final ResourceLocation TYPE=new ResourceLocation("traincraft","locomotive_steam_berkshire_765");
    private static UUID spawned;
    private static ServerLevel world;
    private static boolean delegated;
    // Height in 1/16-block units, applied only to the temporary reference fixture.
    private static int height16=0;
    private static double fixtureX,fixtureZ,railRestY;
    private static boolean pinFixture=false;
    private static String lastResult="NOT_RUN";
    private Dev44BerkshireReferenceTest() {}

    @SubscribeEvent public static void register(RegisterCommandsEvent ev) {
        CommandDispatcher<CommandSourceStack> d=ev.getDispatcher();
        d.register(Commands.literal("tc_berkshire_ref").requires(s->s.hasPermission(2))
            .then(Commands.literal("start").executes(c->start(c.getSource())))
            .then(Commands.literal("retry").executes(c->retry(c.getSource())))
            .then(Commands.literal("height").then(Commands.argument("sixteenths",IntegerArgumentType.integer(-16,16))
                .executes(c->height(c.getSource(),IntegerArgumentType.getInteger(c,"sixteenths")))))
            .then(Commands.literal("status").executes(c->status(c.getSource())))
            .then(Commands.literal("cleanup").executes(c->cleanup(c.getSource()))));
    }
    private static void say(CommandSourceStack s,String msg) {
        LOG.info("[TC-DEV44-R13ak] {}",msg);
        s.sendSuccess(()->Component.literal("[DEV44 Berkshire R13ak] "+msg),false);
    }
    private static ResourceLocation blockId(ServerLevel level,BlockPos pos) {
        return ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock());
    }
    private static boolean rail(ServerLevel level, BlockPos pos) {
        ResourceLocation id=blockId(level,pos);
        return id!=null && "traincraft".equals(id.getNamespace()) &&
            (id.getPath().contains("track") || id.getPath().contains("rail"));
    }
    private static boolean clearAbove(ServerLevel level,BlockPos pos) {
        for(int h=1;h<=4;h++) if(!level.getBlockState(pos.above(h)).getCollisionShape(level,pos.above(h)).isEmpty()) return false;
        return true;
    }
    private static int start(CommandSourceStack s) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if(s.getServer().isDedicatedServer()) {say(s,"SAFETY_STOP: singleplayer only");return 0;}
        ServerPlayer p=s.getPlayerOrException(); ServerLevel level=p.serverLevel();
        if(spawned!=null) {say(s,"SAFETY_STOP: previous fixture exists; run cleanup");return 0;}
        // Find a straight seven-block horizontal rail corridor. No guessing at player Y.
        BlockPos base=p.blockPosition();
        BlockPos chosen=null; boolean alongX=false;
        int best=Integer.MAX_VALUE;
        for(int dx=-12;dx<=12;dx++) for(int dz=-12;dz<=12;dz++) for(int dy=-4;dy<=4;dy++) {
            BlockPos test=base.offset(dx,dy,dz);
            if(!rail(level,test) || !clearAbove(level,test)) continue;
            for(int axis=0;axis<2;axis++) {
                boolean good=true;
                for(int i=-3;i<=3;i++) {
                    BlockPos segment=test.offset(axis==0?i:0,0,axis==1?i:0);
                    if(!rail(level,segment)||!clearAbove(level,segment)) {good=false;break;}
                }
                if(good) {
                    int dist=dx*dx+dz*dz+4*dy*dy;
                    if(dist<best){best=dist;chosen=test;alongX=axis==0;}
                }
            }
        }
        if(chosen==null) {say(s,"SAFETY_STOP: no clear 7-block straight Traincraft rail corridor within 12 blocks horizontally / 4 vertically. Build straight tracks and retry.");return 0;}
        Vec3 center=Vec3.atBottomCenterOf(chosen);
        List<Entity> near=level.getEntitiesOfClass(Entity.class,new AABB(center,center).inflate(24),e->
            e.isAlive() && ForgeRegistries.ENTITY_TYPES.getKey(e.getType())!=null &&
            "traincraft".equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).getNamespace()) &&
            ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).getPath().startsWith("locomotive_"));
        if(!near.isEmpty()){say(s,"SAFETY_STOP: "+near.size()+" existing locomotive(s) within 24 blocks");return 0;}
        EntityType<?> type=ForgeRegistries.ENTITY_TYPES.getValue(TYPE);
        if(type==null){say(s,"SAFETY_STOP: Berkshire 765 type missing");return 0;}
        Entity e=type.create(level);
        if(e==null){say(s,"SAFETY_STOP: Berkshire entity creation failed");return 0;}
        // Entity's track-alignment system owns vertical final positioning; never use player Y.
        float yaw=alongX?90f:0f;
        e.moveTo(center.x,chosen.getY()+1.0,center.z,yaw,0f);
        e.setDeltaMovement(Vec3.ZERO);
        if(!level.addFreshEntity(e)){e.discard();say(s,"SAFETY_STOP: entity spawn rejected");return 0;}
        spawned=e.getUUID();world=level;delegated=false;lastResult="SPAWNED";
        // Keep normal rail physics for the first 10 ticks; height calibration must be explicitly requested.
        fixtureX=center.x;fixtureZ=center.z;railRestY=chosen.getY()+0.0625;pinFixture=false;height16=0;
        say(s,"TRACK_SPAWN uuid="+spawned+" rail="+chosen+" axis="+(alongX?"X":"Z")+" pos="+e.position());
        // Execute the legacy command at the spawned entity position, not the player's
        // potentially distant command origin. The old harness may still filter by
        // *player* or locomotive type; that must be reported, never concealed.
        return runCycle(s,e);
    }
    /** R13aj: start the exact retained Berkshire fixture, not a player-nearby search. */
    private static int runCycle(CommandSourceStack s,Entity e) {
        delegated=false;
        if(e==null || !e.isAlive()) {
            lastResult="MISSING_ENTITY";
            say(s,"FAIL: Berkshire fixture missing. Use start near tracks.");
            return 0;
        }
        int result;
        try {
            result=Dev38StationaryRunningGearTest.startExactDev44BerkshireReference(s,e);
        } catch(Exception ex) {
            lastResult="EXACT_START_EXCEPTION:"+ex.getClass().getSimpleName();
            say(s,"FAIL: exact-UUID wheel cycle threw "+ex.getClass().getSimpleName()
                    +". Fixture retained UUID="+spawned);
            return 0;
        }
        if(result<=0) {
            lastResult="EXACT_START_REJECTED";
            say(s,"FAIL: exact-UUID wheel cycle rejected. Fixture retained UUID="+spawned
                    +". Check the [TC-Y18C-FREEZE] safety-stop message; no wheel PASS claimed.");
            return 0;
        }
        String state=Dev38StationaryRunningGearTest.dev44BerkshireReferenceStatus(e.getUUID());
        if(!state.startsWith("ACTIVE")) {
            lastResult="SESSION_NOT_ACTIVE";
            say(s,"FAIL: start returned success but exact-UUID session is not active: "+state);
            return 0;
        }
        delegated=true;
        lastResult="EXACT_UUID_SESSION_ACTIVE";
        say(s,"EXACT_UUID_SESSION_STARTED UUID="+spawned+" "+state
                +". Train remains on tracks; 22-second virtual wheel cycle started. "
                +"Stage progression is logged; rendered rod and wheel motion still requires visual review.");
        return 1;
    }

    /** R13ak: fixture-only height adjustment. We never alter the shared render offset. */
    private static int height(CommandSourceStack s,int steps){
        Entity e=world==null||spawned==null?null:world.getEntity(spawned);
        if(e==null||!e.isAlive()){say(s,"FAIL: no reference fixture to calibrate");return 0;}
        if(Dev38StationaryRunningGearTest.dev44BerkshireReferenceStatus(spawned).startsWith("ACTIVE")){
            say(s,"SAFETY_STOP: wait for wheel cycle to complete before height calibration");return 0;
        }
        height16=steps;
        // Start from the actual settled Y, not a guessed renderer translation.
        if(!pinFixture){railRestY=e.getY();fixtureX=e.getX();fixtureZ=e.getZ();}
        pinFixture=true;
        e.setNoGravity(true);e.setDeltaMovement(Vec3.ZERO);
        e.setPos(fixtureX,railRestY+height16/16.0,fixtureZ);
        say(s,"HEIGHT_CALIBRATION step16="+height16+" offsetBlocks="+(height16/16.0)
                +" settledBaseY="+railRestY+" targetY="+e.getY()+" fixtureOnly=true");
        return 1;
    }
    @SubscribeEvent public static void onWorldTick(TickEvent.LevelTickEvent ev){
        if(ev.phase!=TickEvent.Phase.END || !pinFixture || world==null || ev.level!=world) return;
        Entity e=spawned==null?null:world.getEntity(spawned);
        if(e==null||!e.isAlive()){pinFixture=false;return;}
        // Do not interfere with the virtual wheel-phase test; only calibrate while inactive.
        if(Dev38StationaryRunningGearTest.dev44BerkshireReferenceStatus(spawned).startsWith("ACTIVE")) return;
        e.setNoGravity(true);e.setDeltaMovement(Vec3.ZERO);
        e.setPos(fixtureX,railRestY+height16/16.0,fixtureZ);
    }

    private static int retry(CommandSourceStack s){
        if(s.getServer().isDedicatedServer()){say(s,"SAFETY_STOP: singleplayer only");return 0;}
        Entity e=world==null||spawned==null?null:world.getEntity(spawned);
        if(e==null){say(s,"FAIL: no reference fixture. Use start near tracks.");return 0;}
        return runCycle(s,e);
    }
    private static void discard(){
        Entity e=world==null||spawned==null?null:world.getEntity(spawned);
        if(e!=null){e.setNoGravity(false);e.discard();} spawned=null;world=null;delegated=false;pinFixture=false;height16=0;lastResult="CLEANED";
    }
    private static int status(CommandSourceStack s){
        Entity e=world==null||spawned==null?null:world.getEntity(spawned);
        String phaseState=spawned==null?"INACTIVE":Dev38StationaryRunningGearTest.dev44BerkshireReferenceStatus(spawned);
        say(s,e==null?"INACTIVE":"SPAWNED uuid="+spawned+" pos="+e.position()
                +" lastResult="+lastResult+" height16="+height16+" pinFixture="+pinFixture+" wheelCycle="+phaseState
                +" (virtual phase is not a rendered-wheel PASS)");
        return e==null?0:1;
    }
    private static int cleanup(CommandSourceStack s){
        if(s.getServer().isDedicatedServer())return 0;
        String phaseState=spawned==null?"INACTIVE":Dev38StationaryRunningGearTest.dev44BerkshireReferenceStatus(spawned);
        if(phaseState.startsWith("ACTIVE")) {
            try{s.getServer().getCommands().getDispatcher().execute("tc_gearfreeze stop",s);}
            catch(Exception ex){say(s,"WARN: gearfreeze stop: "+ex.getMessage());}
        }
        discard();say(s,"CLEANUP complete");return 1;
    }
}
