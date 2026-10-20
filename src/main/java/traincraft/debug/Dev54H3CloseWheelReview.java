package traincraft.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * DEV54H4: block line-of-sight guarded framing, three-model focus mode.
 * DEV54H3: focused, reversible in-game camera + stationary pose review.
 * Only seven existing source-qualified wheel-replacement models. One temporary
 * minecart at a time on EMPTY straight vanilla rail, no track/world changes.
 * Both EAST/WEST camera placements are checked for collision before each move.
 * H2's 59-roster controller and saved progress are NOT modified by this test.
 * No automatic visual PASS claim: look at captured video and H1/H2 render logs.
 */
public final class Dev54H3CloseWheelReview {
    private static final String OWNED = "tc_dev54h3_close_owned";
    private static final List<String> IDS = List.of(
        "locomotive_steam_262t",
        "locomotive_steam_a4_mallard",
        "locomotive_steam_alice_0_4_0",
        "locomotive_steam_berkshire_1225",
        "locomotive_steam_berkshire_765",
        "locomotive_steam_king_class",
        "locomotive_steam_vb_shay_2");
    /** DEV54H4: revisit only the three candidates obstructed in H3 video. */
    private static final List<String> FOCUS = List.of(
        "locomotive_steam_262t", "locomotive_steam_king_class", "locomotive_steam_vb_shay_2");
    private static final List<String> H2_MARKER = IDS.subList(1, IDS.size());
    private static final int INITIAL_WAIT = 200, SIDE_WAIT = 40;
    private static final int REST = 40, FORWARD = 160, STOP = 40, REVERSE = 160, FINISH = 40;
    private static final int TOTAL = REST+FORWARD+STOP+REVERSE+FINISH;
    private static final double PHASE_STEP = Math.PI * 2.0d / 40.0d;
    private static Session active;
    private static volatile Frame frame;
    private record Frame(UUID uuid, double radians) {}
    private record View(Vec3 feet, float yaw, float pitch, double blocks,
                        double lift, double alongZ) {}
    private static final class Session {
        final ServerLevel level;
        final UUID owner;
        final Vec3 anchor;
        final Vec3 before;
        final float beforeYaw, beforePitch;
        final boolean beforeFlying;
        final List<String> roster;
        View eastView, westView;
        int index = 0, side = 0, tick = -INITIAL_WAIT, lastStage = -99;
        double cameraBlocks = 6.5d;
        int manualDistance = -1;
        boolean paused = false;
        boolean temporaryNightVision = false;
        UUID owned;
        Session(ServerPlayer p, BlockPos rail, boolean focusOnly) {
            level=p.serverLevel(); owner=p.getUUID();
            roster=focusOnly?FOCUS:IDS;
            anchor=new Vec3(rail.getX()+0.5d,rail.getY()+0.2d,rail.getZ()+0.5d);
            before=p.position();beforeYaw=p.getYRot();beforePitch=p.getXRot();
            beforeFlying=p.getAbilities().flying;
        }
    }
    private Dev54H3CloseWheelReview() {}
    static boolean isActive() {return active != null;}
    static double sampleOverrideOrNaN(Entity e, float partialTick) {
        Frame f=frame;
        return e!=null && f!=null && f.uuid().equals(e.getUUID()) ? f.radians() : Double.NaN;
    }
    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d=event.getDispatcher();
        d.register(Commands.literal("tc_wheelclose").requires(s->s.hasPermission(2))
            .then(Commands.literal("start").executes(c->start(c.getSource(),false)))
            .then(Commands.literal("focus").executes(c->start(c.getSource(),true)))
            .then(Commands.literal("status").executes(c->status(c.getSource())))
            .then(Commands.literal("pause").executes(c->pause(c.getSource(),true)))
            .then(Commands.literal("resume").executes(c->pause(c.getSource(),false)))
            .then(Commands.literal("next").executes(c->seek(c.getSource(),1)))
            .then(Commands.literal("previous").executes(c->seek(c.getSource(),-1)))
            .then(Commands.literal("distance")
                .then(Commands.argument("blocks",IntegerArgumentType.integer(5,12))
                .executes(c->distance(c.getSource(),IntegerArgumentType.getInteger(c,"blocks")))))
            .then(Commands.literal("stop").executes(c->stop(c.getSource())))
            .executes(c->status(c.getSource())));
    }
    private static void msg(CommandSourceStack c,String message) {
        c.sendSuccess(()->Component.literal("[DEV54H3 CLOSE] "+message),false);
    }
    private static boolean markerOn(String name) {
        try {
            Path p=FMLPaths.GAMEDIR.get().resolve(name);
            return Files.isRegularFile(p) && "ON".equalsIgnoreCase(
                Files.readString(p,StandardCharsets.UTF_8).replace("\ufeff","").trim());
        } catch (Exception e) {return false;}
    }
    private static boolean h2MarkerExact() {
        try {
            Path p=FMLPaths.GAMEDIR.get().resolve("DEV54H2-REAL-WHEELS-ON.txt");
            if(!Files.isRegularFile(p))return false;
            String v=Files.readString(p,StandardCharsets.UTF_8).replace("\ufeff","")
                .replace("\r","").trim();
            return v.equals(String.join("\n",H2_MARKER));
        } catch (Exception e) {return false;}
    }
    private static BlockPos findRail(ServerPlayer p) {
        ServerLevel l=p.serverLevel();BlockPos origin=p.blockPosition();
        BlockPos best=null;double nearest=Double.POSITIVE_INFINITY;
        for(int dy=-3;dy<=3;dy++)for(int dx=-18;dx<=18;dx++)for(int dz=-18;dz<=18;dz++) {
            BlockPos q=origin.offset(dx,dy,dz);
            if(!l.isLoaded(q)||!l.getBlockState(q).is(Blocks.RAIL))continue;
            if(l.getBlockState(q).getValue(RailBlock.SHAPE)!=RailShape.NORTH_SOUTH)continue;
            if(!l.isLoaded(q.north())||!l.isLoaded(q.south())
                ||!l.getBlockState(q.north()).is(Blocks.RAIL)
                ||!l.getBlockState(q.south()).is(Blocks.RAIL))continue;
            if(!l.getEntitiesOfClass(AbstractMinecart.class,new AABB(q).inflate(4,3,7),Entity::isAlive).isEmpty())continue;
            double ds=origin.distSqr(q);if(ds<nearest){best=q;nearest=ds;}
        }
        return best;
    }
    // DEV54H4: H3 checked only player collision; a stone platform could still
    // fill the entire view. Require BOTH standability and block line of sight
    // to at least two lower-running-gear target points before teleports.
    private static double[] preferredDistances(Session s) {
        if(s.manualDistance>=5)return new double[]{s.manualDistance};
        String model=s.roster.get(s.index);
        if(model.equals("locomotive_steam_vb_shay_2"))
            return new double[]{12.0,14.0,16.0,10.0,8.5};
        if(model.equals("locomotive_steam_king_class"))
            return new double[]{4.5,5.5,6.5,8.0,9.5,11.0};
        if(model.equals("locomotive_steam_262t"))
            return new double[]{5.5,6.5,7.5,9.0,11.0};
        return new double[]{6.5,7.5,8.5,9.5,11.5};
    }
    private static boolean clearView(Session s,ServerPlayer p,View v) {
        Vec3 q=v.feet();
        BlockPos low=BlockPos.containing(q);
        BlockPos high=BlockPos.containing(q.add(0,2,0));
        if(!s.level.isLoaded(low)||!s.level.isLoaded(high))return false;
        AABB box=p.getBoundingBox().move(q.x-p.getX(),q.y-p.getY(),q.z-p.getZ());
        if(!s.level.noCollision(p,box))return false;
        Vec3 eye=q.add(0,p.getEyeHeight(),0);
        int visible=0;
        for(double along:new double[]{0.0,-1.2,1.2}) {
            Vec3 target=new Vec3(s.anchor.x,s.anchor.y+0.72,s.anchor.z+along);
            HitResult hit=s.level.clip(new ClipContext(eye,target,
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if(hit.getType()==HitResult.Type.MISS ||
                hit.getLocation().distanceToSqr(target)<0.12)visible++;
        }
        return visible>=2;
    }
    private static View findView(Session s,ServerPlayer p,int side) {
        int sign=side==0?1:-1;
        for(double d:preferredDistances(s))
            for(double lift:new double[]{1.05,1.55,2.20,0.55})
                for(double zShift:new double[]{0.0,-1.5,1.5,-3.0,3.0}) {
                    Vec3 feet=new Vec3(s.anchor.x+sign*d,s.anchor.y+lift,
                                      s.anchor.z+zShift);
                    Vec3 eye=feet.add(0,p.getEyeHeight(),0);
                    Vec3 target=new Vec3(s.anchor.x,s.anchor.y+0.72,s.anchor.z);
                    double dx=target.x-eye.x,dz=target.z-eye.z;
                    float yaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90.0d);
                    float pitch=(float)Math.toDegrees(Math.atan2(eye.y-target.y,
                        Math.sqrt(dx*dx+dz*dz)));
                    View v=new View(feet,yaw,pitch,d,lift,zShift);
                    if(clearView(s,p,v))return v;
                }
        return null;
    }
    private static boolean bothClear(Session s,ServerPlayer p) {
        View east=findView(s,p,0),west=findView(s,p,1);
        if(east==null||west==null)return false;
        s.eastView=east;s.westView=west;
        return true;
    }
    private static boolean moveCamera(Session s,ServerPlayer p) {
        View view=s.side==0?s.eastView:s.westView;
        if(view==null || !clearView(s,p,view)) {
            view=findView(s,p,s.side);
            if(view==null)return false;
            if(s.side==0)s.eastView=view;else s.westView=view;
        }
        p.getAbilities().flying=true;p.onUpdateAbilities();
        Vec3 q=view.feet();
        p.connection.teleport(q.x,q.y,q.z,view.yaw(),view.pitch());
        s.cameraBlocks=view.blocks();
        return true;
    }
    private static String cameraInfo(Session s) {
        View v=s.side==0?s.eastView:s.westView;
        return v==null?"unselected":String.format(Locale.ROOT,
            "distance=%.1f lift=%.2f offsetZ=%+.1f yaw=%.1f pitch=%.1f",
            v.blocks(),v.lift(),v.alongZ(),v.yaw(),v.pitch());
    }
    private static void restore(Session s,ServerPlayer p) {
        if(p==null||p.serverLevel()!=s.level)return;
        if(s.temporaryNightVision){p.removeEffect(MobEffects.NIGHT_VISION);s.temporaryNightVision=false;}
        p.connection.teleport(s.before.x,s.before.y,s.before.z,s.beforeYaw,s.beforePitch);
        p.getAbilities().flying=s.beforeFlying;p.onUpdateAbilities();
    }
    private static int start(CommandSourceStack src,boolean focusOnly) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p=src.getPlayerOrException();MinecraftServer server=p.getServer();
        if(server==null||server.isDedicatedServer()||!p.getAbilities().instabuild
            ||!(p.serverLevel().getChunkSource().getGenerator() instanceof FlatLevelSource)) {
            msg(src,"SAFETY HOLD: local Creative Superflat only.");return 0;
        }
        if(active!=null||Dev54H2StationaryFleet.isActive()||Dev54FCaptureCommand.dev54h2CaptureActive()) {
            msg(src,"SAFETY HOLD: another /tc_stationary59, /tc_capture or close review is active.");return 0;
        }
        if(markerOn("DEV54F-RODS-HIDDEN-ON.txt")||markerOn("DEV44-R12-HIDE-ON.txt")) {
            msg(src,"SAFETY HOLD: old DEV54F rod or DEV44 geometry mask is ON. Use DEV54H2A first.");return 0;
        }
        if(!markerOn("DEV54H1-REAL-WHEELS-ON.txt")) {
            msg(src,"SAFETY HOLD: 2-6-2T H1A wheel marker is OFF; run DEV54H3 enable-262T launcher, then restart Minecraft.");return 0;
        }
        if(!h2MarkerExact()) {
            msg(src,"SAFETY HOLD: H2's six-wheel selector OFF or modified; enable H2 safely before running.");return 0;
        }
        for(String id:IDS)if(ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("traincraft",id))==null){
            msg(src,"SAFETY HOLD: missing traincraft entity "+id);return 0;
        }
        BlockPos rail=findRail(p);
        if(rail==null){msg(src,"SAFETY HOLD: stand near EMPTY straight north/south vanilla rails; keep ordinary trains away.");return 0;}
        Session s=new Session(p,rail,focusOnly);
        // H4 makes no terrain edits. If the platform blocks both-side wheel
        // sight lines even at elevated viewpoints, hold before spawning.
        if(!bothClear(s,p)){
            msg(src,"SAFETY HOLD: no collision-free camera with clear wheel line-of-sight on BOTH sides. Choose an open level track. No blocks changed.");return 0;
        }
        if(!spawn(s)) {msg(src,"SAFETY HOLD: locomotive could not spawn; no entities retained.");return 0;}
        if(!moveCamera(s,p)) {clear(s);msg(src,"SAFETY HOLD: camera obstructed; no changes retained.");return 0;}
        s.tick=-INITIAL_WAIT;
        active=s;
        // Brighten dark wheel faces ONLY during the focused inspection.
        // Preserve any pre-existing Night Vision effect unchanged.
        if(focusOnly && !p.hasEffect(MobEffects.NIGHT_VISION)){
            s.temporaryNightVision=p.addEffect(new MobEffectInstance(
                MobEffects.NIGHT_VISION,12000,0,false,false,false));
        }
        msg(src,"START "+s.roster.size()+" source-qualified models, both sides. 10s initial countdown. "+
            "H1A marker ON, H2 marker VERIFIED; renderer active=true must still be checked in latest.log.");
        msg(src,"Camera line-of-sight VERIFIED both sides. EAST "+cameraInfo(s)+
            "; WEST calculated separately. Camera and flight restore on completion or stop.");
        return 1;
    }
    private static boolean spawn(Session s) {
        EntityType<?> type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("traincraft",s.roster.get(s.index)));
        if(type==null)return false;
        Entity e;try{e=type.create(s.level);}catch(Throwable err){return false;}
        if(!(e instanceof AbstractMinecart cart)){if(e!=null)e.discard();return false;}
        cart.moveTo(s.anchor.x,s.anchor.y,s.anchor.z,180.0f,0.0f);
        cart.setDeltaMovement(Vec3.ZERO);
        cart.addTag(OWNED);
        cart.setCustomName(Component.literal("DEV54H3 "+(s.index+1)+"/"+s.roster.size()+" "+s.roster.get(s.index)));
        cart.setCustomNameVisible(true);
        if(!s.level.addFreshEntity(cart)){cart.discard();return false;}
        s.owned=cart.getUUID();s.tick=-SIDE_WAIT;s.lastStage=-99;
        frame=new Frame(s.owned,0.0d);
        return true;
    }
    private static void clear(Session s) {
        frame=null;
        if(s==null||s.owned==null)return;
        Entity e=s.level.getEntity(s.owned);
        if(e!=null&&e.getTags().contains(OWNED)){
            e.setDeltaMovement(Vec3.ZERO);e.discard();
        }
        s.owned=null;
    }
    private static boolean owns(CommandSourceStack c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return active!=null&&active.owner.equals(c.getPlayerOrException().getUUID());
    }
    private static int status(CommandSourceStack c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Session s=active;
        msg(c,s==null?"INACTIVE; /tc_wheelclose focus = three visibility blockers; /tc_wheelclose start = seven. H2 progress unchanged":
            "ACTIVE "+(s.index+1)+"/"+s.roster.size()+" "+s.roster.get(s.index)+" camera="+(s.side==0?"EAST":"WEST")+
                " stage="+stageName(stage(s.tick))+" paused="+s.paused+
                " "+cameraInfo(s));
        return 1;
    }
    private static int pause(CommandSourceStack c,boolean pause) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if(!owns(c)){msg(c,"No owned close-review session.");return 0;}
        active.paused=pause;msg(c,pause?"PAUSED stationary animation":"RESUMED stationary animation");return 1;
    }
    private static int distance(CommandSourceStack c,int blocks) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if(!owns(c)){msg(c,"Start /tc_wheelclose start first.");return 0;}
        Session s=active;ServerPlayer p=c.getPlayerOrException();
        int previous=s.manualDistance;
        View oldEast=s.eastView,oldWest=s.westView;
        s.manualDistance=blocks;
        if(!bothClear(s,p)||!moveCamera(s,p)){
            s.manualDistance=previous;s.eastView=oldEast;s.westView=oldWest;
            msg(c,"SAFETY HOLD: chosen distance has blocked wheel view; previous camera retained.");return 0;
        }
        msg(c,"Camera distance="+blocks+" blocks; collision and wheel line-of-sight verified on both sides.");return 1;
    }
    private static int seek(CommandSourceStack c,int step) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if(!owns(c)){msg(c,"No owned close-review session.");return 0;}
        Session s=active;ServerPlayer p=c.getPlayerOrException();
        int previous=s.index;
        int target=Math.max(0,Math.min(s.roster.size()-1,previous+step));
        if(target==previous){msg(c,"At roster boundary.");return 1;}
        clear(s);s.index=target;s.side=0;
        if(!bothClear(s,p)){
            s.paused=true;msg(c,"SAFETY HOLD: no clear camera on both sides for next model. Use stop to restore.");return 0;
        }
        if(!spawn(s)){s.paused=true;msg(c,"SAFETY HOLD: target locomotive missing; stop test.");return 0;}
        if(!moveCamera(s,p)){s.paused=true;msg(c,"SAFETY HOLD: camera path blocked; stop test.");return 0;}
        s.paused=false;msg(c,"SELECTED "+(target+1)+"/"+s.roster.size()+" "+s.roster.get(target));return 1;
    }
    private static int stop(CommandSourceStack c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if(!owns(c)){msg(c,"No owned close-review session.");return 0;}
        Session s=active;clear(s);active=null;restore(s,c.getPlayerOrException());
        msg(c,"STOPPED and restored original camera and flight. H2 59/59 progress untouched.");return 1;
    }
    private static int stage(int tick){
        if(tick<0)return -1;
        if(tick<REST)return 0;
        if(tick<REST+FORWARD)return 1;
        if(tick<REST+FORWARD+STOP)return 2;
        if(tick<REST+FORWARD+STOP+REVERSE)return 3;
        return 4;
    }
    private static String stageName(int n) {
        return switch(n){case -1->"COUNTDOWN";case 0->"REST";case 1->"FORWARD 4 REV";
            case 2->"STOP";case 3->"REVERSE 4 REV";default->"FINAL STOP";};
    }
    private static double phase(int tick){
        if(tick<REST)return 0.0d;
        int t=tick-REST;
        if(t<FORWARD)return t*PHASE_STEP;
        if(t<FORWARD+STOP)return FORWARD*PHASE_STEP;
        int reverse=t-FORWARD-STOP;
        if(reverse<REVERSE)return (FORWARD-reverse)*PHASE_STEP;
        return 0.0d;
    }
    static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        Session s=active;if(s==null||s.paused)return;
        MinecraftServer server=ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        ServerPlayer p=server.getPlayerList().getPlayer(s.owner);
        if(p==null||p.serverLevel()!=s.level){clear(s);active=null;return;}
        Entity e=s.owned==null?null:s.level.getEntity(s.owned);
        if(!(e instanceof AbstractMinecart cart)||!e.getTags().contains(OWNED)||!e.isAlive()) {
            frame=null;s.paused=true;msg(p.createCommandSourceStack(),"SAFETY HOLD: test locomotive disappeared; stopped automatic progression.");return;
        }
        if(e.isPassenger()||!e.getPassengers().isEmpty()){
            frame=null;s.paused=true;msg(p.createCommandSourceStack(),"SAFETY HOLD: locomotive occupied; test paused.");return;
        }
        cart.setDeltaMovement(Vec3.ZERO);
        if(e.position().distanceToSqr(s.anchor)>0.000001d)
            e.moveTo(s.anchor.x,s.anchor.y,s.anchor.z,180.0f,0.0f);
        frame=new Frame(s.owned,phase(s.tick));
        int n=stage(s.tick);
        if(n!=s.lastStage){
            s.lastStage=n;
            msg(p.createCommandSourceStack(),(s.index+1)+"/"+s.roster.size()+" "+s.roster.get(s.index)+
                " camera "+(s.side==0?"EAST":"WEST")+" | "+stageName(n));
        }
        if(s.tick%20==0)p.displayClientMessage(Component.literal(
            "[DEV54H3 "+(s.index+1)+"/"+s.roster.size()+" "+(s.side==0?"EAST":"WEST")+" "+stageName(n)+"] "+s.roster.get(s.index)),true);
        s.tick++;
        if(s.tick<TOTAL)return;
        if(s.side==0){
            s.side=1;s.tick=-SIDE_WAIT;s.lastStage=-99;frame=new Frame(s.owned,0);
            if(!moveCamera(s,p)){
                s.paused=true;msg(p.createCommandSourceStack(),"SAFETY HOLD: west camera blocked; automatic switching paused.");
            } else {
                msg(p.createCommandSourceStack(),"CAMERA "+s.roster.get(s.index)+" WEST "+cameraInfo(s));
            }
            return;
        }
        clear(s);s.index++;s.side=0;
        if(s.index>=s.roster.size()) {
            active=null;restore(s,p);
            msg(p.createCommandSourceStack(),"COMPLETE "+s.roster.size()+"/"+s.roster.size()+" x both sides. Mechanical accuracy requires video acceptance; H2 59/59 progress unchanged.");
            return;
        }
        if(!bothClear(s,p)){
            s.paused=true;msg(p.createCommandSourceStack(),"SAFETY HOLD: no clear camera on BOTH sides for next model. Use stop; no automatic skip.");return;
        }
        if(!spawn(s)){
            s.paused=true;msg(p.createCommandSourceStack(),"SAFETY HOLD: next model failed to spawn; no automatic skip.");return;
        }
        if(!moveCamera(s,p)){
            s.paused=true;msg(p.createCommandSourceStack(),"SAFETY HOLD: new model camera obstructed; use stop to restore.");
        } else {
            msg(p.createCommandSourceStack(),"CAMERA "+s.roster.get(s.index)+" EAST "+cameraInfo(s));
        }
    }
    static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        Session s=active;
        if(s!=null&&s.owner.equals(event.getEntity().getUUID())){
            clear(s);
            if(s.temporaryNightVision && event.getEntity() instanceof ServerPlayer p)
                p.removeEffect(MobEffects.NIGHT_VISION);
            active=null;
        }
    }
    static void shutdown(){Session s=active;if(s!=null)clear(s);active=null;}
}
