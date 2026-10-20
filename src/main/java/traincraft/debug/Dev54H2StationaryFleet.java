package traincraft.debug;

import com.mojang.brigadier.CommandDispatcher;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * DEV54H2: Creative Superflat, integrated-server ONLY. Visual stimulus, not gameplay.
 * One temporary tagged locomotive on an existing EMPTY straight vanilla rail.
 * Engine stays parked; signed 4-revolution rendering phase is shared only
 * with the client of this integrated JVM. Original movement and meshes retained.
 * No blocks, configs, cameras, accepted wheels, or ordinary trains modified.
 */
public final class Dev54H2StationaryFleet {
    private static final String OWNED = "tc_dev54h2_stationary_owned";
    private static final int COUNTDOWN = 200;
    private static final int REST = 40, FORWARD = 160, PAUSE = 40, REVERSE = 160, FINISH = 40;
    private static final int TOTAL = REST+FORWARD+PAUSE+REVERSE+FINISH;
    private static final double STEP = Math.PI * 2.0d / 40.0d;
    private static volatile VisibleFrame visible;
    private static Session session;
    private record VisibleFrame(UUID uuid, double radians) {}
    private static final class Session {
        final ServerLevel level;
        final UUID owner;
        final Vec3 anchor;
        final Path progressFile;
        final List<String> roster;
        int index, ticks=-COUNTDOWN, lastStage=-1;
        boolean paused=false;
        UUID owned;
        Session(ServerPlayer p, BlockPos rail, int index) {
            level=p.serverLevel();owner=p.getUUID();
            anchor=new Vec3(rail.getX()+0.5d,rail.getY()+0.2d,rail.getZ()+0.5d);
            progressFile=progressPath();roster=List.copyOf(Dev54FCaptureCommand.dev54h2Roster());this.index=index;
        }
    }
    private Dev54H2StationaryFleet() {}
    public static boolean isActive() {return session != null || Dev54H3CloseWheelReview.isActive() || Dev54H12FleetReview.isActive();}
    public static double sampleOverrideOrNaN(Entity entity, float partialTick) {
        double h3 = Dev54H3CloseWheelReview.sampleOverrideOrNaN(entity, partialTick);
        if (!Double.isNaN(h3)) return h3;
        double h12 = Dev54H12FleetReview.sampleOverrideOrNaN(entity, partialTick);
        if (!Double.isNaN(h12)) return h12;
        VisibleFrame f=visible;
        return entity!=null && f!=null && f.uuid().equals(entity.getUUID()) ? f.radians() : Double.NaN;
    }
    public static void register(RegisterCommandsEvent ev) {
        Dev54H3CloseWheelReview.register(ev);
        Dev54H12FleetReview.register(ev);
        CommandDispatcher<CommandSourceStack> d=ev.getDispatcher();
        d.register(Commands.literal("tc_stationary59").requires(s->s.hasPermission(2))
            .then(Commands.literal("start").executes(c->start(c.getSource(),false)))
            .then(Commands.literal("restart").executes(c->start(c.getSource(),true)))
            .then(Commands.literal("pause").executes(c->pause(c.getSource(),true)))
            .then(Commands.literal("resume").executes(c->pause(c.getSource(),false)))
            .then(Commands.literal("next").executes(c->step(c.getSource(),1)))
            .then(Commands.literal("previous").executes(c->step(c.getSource(),-1)))
            .then(Commands.literal("status").executes(c->status(c.getSource())))
            .then(Commands.literal("stop").executes(c->stop(c.getSource())))
            .executes(c->status(c.getSource())));
    }
    private static void reply(CommandSourceStack src, String message) {
        src.sendSuccess(()->Component.literal("[DEV54H2 STATIONARY] "+message),false);
    }
    private static Path progressPath() {
        return FMLPaths.GAMEDIR.get().resolve("DEV54H2-STATIONARY-59-PROGRESS.txt");
    }
    private static int readProgress() {
        Path p=progressPath();
        if(!Files.exists(p))return 0;
        try {
            int n=Integer.parseInt(Files.readString(p,StandardCharsets.UTF_8).trim());
            return n>=0 && n<=59?n:-1;
        } catch (Exception e) {return -1;}
    }
    private static boolean writeProgress(Session s) {
        try {
            Files.writeString(s.progressFile,Integer.toString(s.index)+"\n",StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {return false;}
    }
    private static BlockPos findRail(ServerPlayer p) {
        ServerLevel l=p.serverLevel();BlockPos origin=p.blockPosition();
        BlockPos best=null;double bestSq=Double.POSITIVE_INFINITY;
        for(int y=-3;y<=3;y++)for(int x=-18;x<=18;x++)for(int z=-18;z<=18;z++) {
            BlockPos c=origin.offset(x,y,z);
            if(!l.isLoaded(c)||!l.getBlockState(c).is(Blocks.RAIL))continue;
            if(l.getBlockState(c).getValue(RailBlock.SHAPE)!=RailShape.NORTH_SOUTH)continue;
            if(!l.isLoaded(c.north())||!l.isLoaded(c.south())
                ||!l.getBlockState(c.north()).is(Blocks.RAIL)
                ||!l.getBlockState(c.south()).is(Blocks.RAIL))continue;
            // Preserve normal trains. Never move, despawn, or occupy their track region.
            AABB area=new AABB(c).inflate(4.0d,3.0d,7.0d);
            if(!l.getEntitiesOfClass(AbstractMinecart.class,area,Entity::isAlive).isEmpty())continue;
            double d=origin.distSqr(c);if(d<bestSq){bestSq=d;best=c;}
        }
        return best;
    }
    private static int start(CommandSourceStack src, boolean restart) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p=src.getPlayerOrException();MinecraftServer server=p.getServer();
        if(server==null||server.isDedicatedServer()||!p.getAbilities().instabuild
             ||!(p.serverLevel().getChunkSource().getGenerator() instanceof FlatLevelSource)) {
            reply(src,"SAFETY HOLD: local Creative Superflat only; no change made.");return 0;
        }
        if(Dev54FCaptureCommand.dev54h2CaptureActive() || session!=null || Dev54H3CloseWheelReview.isActive()) {
            reply(src,"SAFETY HOLD: /tc_capture or /tc_stationary59 already active. Stop that test first.");return 0;
        }
        List<String> ids=Dev54FCaptureCommand.dev54h2Roster();
        if(ids.size()!=59) {reply(src,"SAFETY HOLD: expected exact 59 roster, got "+ids.size());return 0;}
        for(String id:ids)if(ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("traincraft",id))==null){
            reply(src,"SAFETY HOLD: missing entity "+id);return 0;
        }
        int index=restart?0:readProgress();
        if(index<0) {reply(src,"SAFETY HOLD: unexpected progress file contents. Preserve it for investigation.");return 0;}
        if(index>=59){reply(src,"59/59 recorded. Use /tc_stationary59 restart for a new pass.");return 1;}
        BlockPos rail=findRail(p);
        if(rail==null){reply(src,"SAFETY HOLD: stand within 18 blocks of EMPTY straight vanilla rails with 2+ connected segments. Move the parked 2-6-2T away first.");return 0;}
        Session s=new Session(p,rail,index);
        if(!spawn(s)){reply(src,"SAFETY HOLD: could not spawn temporary locomotive; no permanent entities changed.");return 0;}
        session=s;
        if(!writeProgress(s)){clear(s);session=null;reply(src,"SAFETY HOLD: could not write progress. No test started.");return 0;}
        reply(src,"ARMED "+(index+1)+"/59 "+s.roster.get(index)+". Ten-second countdown; stand beside the stationary engine, record both sides in separate passes.");
        return 1;
    }
    private static boolean spawn(Session s) {
        if(s.index>=s.roster.size())return false;
        String id=s.roster.get(s.index);
        EntityType<?> type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("traincraft",id));
        if(type==null)return false;
        Entity e;
        try {e=type.create(s.level);}catch(Throwable t){return false;}
        if(!(e instanceof AbstractMinecart cart)) {if(e!=null)e.discard();return false;}
        cart.moveTo(s.anchor.x,s.anchor.y,s.anchor.z,180.0f,0.0f);
        cart.setDeltaMovement(Vec3.ZERO);
        cart.addTag(OWNED);
        cart.setCustomName(Component.literal("DEV54H2 "+(s.index+1)+"/59 "+id));
        cart.setCustomNameVisible(true);
        if(!s.level.addFreshEntity(cart))return false;
        s.owned=cart.getUUID();s.ticks=-COUNTDOWN;s.lastStage=-1;
        visible=new VisibleFrame(s.owned,0.0d);
        return true;
    }
    private static void clear(Session s) {
        visible=null;
        if(s==null||s.owned==null)return;
        Entity e=s.level.getEntity(s.owned);
        if(e!=null&&e.getTags().contains(OWNED)){
            e.setDeltaMovement(Vec3.ZERO);e.discard();
        }
        s.owned=null;
    }
    private static boolean owner(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return session!=null && session.owner.equals(src.getPlayerOrException().getUUID());
    }
    private static int status(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Session s=session;
        reply(src,s==null?"INACTIVE; saved completed index="+readProgress()+"/59":"ACTIVE "+(s.index+1)+"/59 " + s.roster.get(s.index)+" stage="+stage(s.ticks)+" paused="+s.paused+" pos="+s.anchor);
        return 1;
    }
    private static int pause(CommandSourceStack src,boolean pause) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if(!owner(src)){reply(src,"No owned test session.");return 0;}
        session.paused=pause;reply(src,pause?"PAUSED virtual phase and locomotive":"RESUMED");return 1;
    }
    private static int step(CommandSourceStack src,int direction) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if(!owner(src)){reply(src,"No owned test session.");return 0;}
        Session s=session;int target=Math.max(0,Math.min(58,s.index+direction));
        clear(s);s.index=target;
        if(!spawn(s)){s.paused=true;reply(src,"HOLD: could not spawn "+s.roster.get(target));return 0;}
        if(!writeProgress(s)){s.paused=true;reply(src,"HOLD: progress save failed");return 0;}
        reply(src,"SELECTED "+(target+1)+"/59 "+s.roster.get(target));return 1;
    }
    private static int stop(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if(!owner(src)){reply(src,"No owned test session.");return 0;}
        Session s=session;clear(s);session=null;
        reply(src,"STOPPED. Saved index="+s.index+"/59; no ordinary trains or blocks touched.");return 1;
    }
    private static int stage(int t) {
        if(t<0)return -1; if(t<REST)return 0;
        if(t<REST+FORWARD)return 1;
        if(t<REST+FORWARD+PAUSE)return 2;
        if(t<REST+FORWARD+PAUSE+REVERSE)return 3;
        return 4;
    }
    private static double phase(int t) {
        if(t<REST)return 0;
        int after=t-REST;
        if(after<FORWARD)return after*STEP;
        if(after<FORWARD+PAUSE)return FORWARD*STEP;
        int rev=after-FORWARD-PAUSE;
        if(rev<REVERSE)return (FORWARD-rev)*STEP;
        return 0;
    }
    private static String stageName(int n) {
        return switch(n){case -1->"COUNTDOWN";case 0->"REST";case 1->"FORWARD 4 REV";case 2->"STOP";case 3->"REVERSE 4 REV";default->"FINAL STOP";};
    }
    public static void tick(TickEvent.ServerTickEvent ev) {
        if(ev.phase!=TickEvent.Phase.END)return;
        Dev54H3CloseWheelReview.tick(ev);
        Dev54H12FleetReview.tick(ev);
        Session s=session;if(s==null||s.paused)return;
        MinecraftServer server=ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        ServerPlayer p=server.getPlayerList().getPlayer(s.owner);
        if(p==null||p.serverLevel()!=s.level){clear(s);session=null;return;}
        Entity e=s.owned==null?null:s.level.getEntity(s.owned);
        if(!(e instanceof AbstractMinecart cart)||!e.getTags().contains(OWNED)||!e.isAlive()){
            visible=null;s.paused=true;reply(p.createCommandSourceStack(),"SAFETY HOLD: temporary locomotive disappeared, no automatic skip.");return;
        }
        if(e.isPassenger()||!e.getPassengers().isEmpty()){
            visible=null;s.paused=true;reply(p.createCommandSourceStack(),"SAFETY HOLD: locomotive occupied. Test paused.");return;
        }
        // Pin each server tick and force zero true velocity; never propel the locomotive.
        cart.setDeltaMovement(Vec3.ZERO);
        if(e.position().distanceToSqr(s.anchor)>0.000001d)e.moveTo(s.anchor.x,s.anchor.y,s.anchor.z,180.0f,0.0f);
        double radians=phase(s.ticks);
        visible=new VisibleFrame(s.owned,radians);
        int st=stage(s.ticks);
        if(st!=s.lastStage) {
            s.lastStage=st;
            reply(p.createCommandSourceStack(),(s.index+1)+"/59 "+s.roster.get(s.index)+" | "+stageName(st));
        }
        if(s.ticks%20==0)p.displayClientMessage(Component.literal("[DEV54H2 "+(s.index+1)+"/59 "+stageName(st)+"] "+s.roster.get(s.index)),true);
        s.ticks++;
        if(s.ticks>=TOTAL) {
            clear(s);s.index++;
            if(!writeProgress(s)){s.paused=true;reply(p.createCommandSourceStack(),"SAFETY HOLD: progress write failed. Do not restart yet.");return;}
            if(s.index>=59){session=null;reply(p.createCommandSourceStack(),"COMPLETE 59/59 stationary subjects. Phase/geometry require video acceptance.");return;}
            if(!spawn(s)){s.paused=true;reply(p.createCommandSourceStack(),"SAFETY HOLD: could not spawn "+s.roster.get(s.index)+". Use /tc_stationary59 next or stop.");return;}
        }
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        Dev54H3CloseWheelReview.logout(event);
        Dev54H12FleetReview.logout(event);
        Session s=session;
        if(s!=null&&s.owner.equals(event.getEntity().getUUID())){clear(s);session=null;}
    }
    public static void shutdown(){Dev54H3CloseWheelReview.shutdown();Dev54H12FleetReview.shutdown();Session s=session;if(s!=null)clear(s);session=null;}
}
