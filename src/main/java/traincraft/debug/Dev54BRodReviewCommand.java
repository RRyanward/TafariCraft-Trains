package traincraft.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import traincraft.Traincraft;

/**
 * DEV54B: ADDED debug-only locomotive rod/piston visual review harness.
 * Runs only on 19 existing, straight vanilla rails in a disposable Creative world.
 * No block placement, production renderer/animation changes or calibration edits.
 * A forced minecart delta is only a diagnostic stimulus, NOT native drive proof.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev54BRodReviewCommand {
    private static final String TAG = "tc_dev54b_rod_review_owned";
    private static final List<String> ROSTER = List.of(
        "locomotive_steam_262t",
        "locomotive_steam_4_4_0",
        "locomotive_steam_a4_mallard",
        "locomotive_steam_adler",
        "locomotive_steam_alco_sc4",
        "locomotive_steam_alice_0_4_0",
        "locomotive_steam_berkshire_1225",
        "locomotive_steam_br_black_5",
        "locomotive_steam_br_britannia",
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
        "locomotive_steam_pannier",
        "locomotive_steam_pe",
        "locomotive_steam_rw_type_2",
        "locomotive_steam_rw_type_3",
        "locomotive_steam_s100_uk",
        "locomotive_steam_s100_us",
        "locomotive_steam_sentinel_y3",
        "locomotive_steam_shay_3_truck",
        "locomotive_steam_skookum",
        "locomotive_steam_small",
        "locomotive_steam_southern1102",
        "locomotive_steam_star_class",
        "locomotive_steam_u57",
        "locomotive_steam_vb_0_4_0",
        "locomotive_steam_vb_shay",
        "locomotive_steam_wwcp_062t"
    );
    private static final Set<String> ROSTER_SET = Set.copyOf(ROSTER);
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final int CYCLE = 20 + 42 + 16 + 42 + 20;
    private static final double TEST_SPEED = 0.070D;
    private Dev54BRodReviewCommand() {}

    private static final class Session {
        final UUID owner;
        final BlockPos rail;
        final String type;
        final ResourceLocation dimension;
        int entityId;
        int tick;
        boolean halted;
        String lastPhase = "";
        Session(UUID owner, BlockPos rail, String type, ResourceLocation dimension, int entityId) {
            this.owner=owner; this.rail=rail; this.type=type; this.dimension=dimension;
            this.entityId=entityId;
        }
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> dispatcher=evt.getDispatcher();
        dispatcher.register(Commands.literal("tc_rods")
            .requires(s -> s.hasPermission(2))
            .executes(c -> help(c.getSource()))
            .then(Commands.literal("start").executes(c -> start(c.getSource(), null)))
            .then(Commands.literal("pass").executes(c -> result(c.getSource(), "PASS")))
            .then(Commands.literal("fail_rods").executes(c -> result(c.getSource(), "FAIL_RODS")))
            .then(Commands.literal("fail_pistons").executes(c -> result(c.getSource(), "FAIL_PISTONS")))
            .then(Commands.literal("fail_wrongpart").executes(c -> result(c.getSource(), "FAIL_WRONG_PART")))
            .then(Commands.literal("fail_other").executes(c -> result(c.getSource(), "FAIL_OTHER")))
            .then(Commands.literal("no_rods").executes(c -> result(c.getSource(), "NO_CONVENTIONAL_RODS")))
            .then(Commands.literal("skip").executes(c -> result(c.getSource(), "SKIP_UNTESTED")))
            .then(Commands.literal("revisit")
                .then(Commands.argument("vehicle", StringArgumentType.word())
                    .executes(c -> start(c.getSource(), StringArgumentType.getString(c,"vehicle")))))
            .then(Commands.literal("status").executes(c -> status(c.getSource())))
            .then(Commands.literal("stop").executes(c -> stop(c.getSource()))));
    }

    private static int help(CommandSourceStack source) {
        msg(source,"[DEV54B] Make 19 straight NORTH/SOUTH vanilla rails with open clearance in a disposable Creative Superflat world.");
        msg(source,"[DEV54B] Stand within 10 blocks of track middle, then /tc_rods start");
        msg(source,"[DEV54B] Observe repeated REST/FORWARD/STOP/REVERSE/STOP, then enter /tc_rods pass | fail_rods | fail_pistons | fail_wrongpart | fail_other | no_rods | skip");
        msg(source,"[DEV54B] /tc_rods stop pauses; /tc_rods start resumes using the saved report; /tc_rods revisit locomotive_steam_...");
        msg(source,"[DEV54B] Prior accepted 8 are excluded; this harness NEVER changes source rods, wheels, controls or DEV53 calibration.");
        return 1;
    }
    private static Path report() {
        return FMLPaths.GAMEDIR.get().resolve("_DEV54B_RODS").resolve("reports").resolve("DEV54B-RODS-VISUAL-RESULTS.csv");
    }
    private static Map<String, String> completed() {
        Map<String,String> results = new HashMap<>();
        Path path=report();
        if (!Files.isRegularFile(path)) return results;
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String[] row = line.trim().split(",", -1);
                if (row.length>=3 && ROSTER_SET.contains(row[1])) results.put(row[1], row[2]);
            }
        } catch (IOException ex) { throw new IllegalStateException("Cannot read DEV54B review results",ex); }
        return results;
    }
    private static void append(String vehicle, String verdict) {
        Path path=report();
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) Files.writeString(path,"epoch_millis,registry_id,verdict\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            Files.writeString(path, Long.toString(System.currentTimeMillis())+","+vehicle+","+verdict+"\n",StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException ex) { throw new IllegalStateException("Cannot save DEV54B rod review result",ex); }
    }
    private static String nextUnreviewed() {
        Map<String,String> results = completed();
        for (String id : ROSTER) if (!results.containsKey(id)) return id;
        return null;
    }
    private static BlockPos chooseRails(ServerPlayer player) {
        ServerLevel level=player.serverLevel(); BlockPos p=player.blockPosition();
        BlockPos best=null; double bestDist=Double.MAX_VALUE;
        for(int y=p.getY()-2;y<=p.getY()+1;y++)
          for(int x=p.getX()-10;x<=p.getX()+10;x++)
            for(int z=p.getZ()-10;z<=p.getZ()+10;z++) {
                double d=(x-p.getX())*(double)(x-p.getX())+(z-p.getZ())*(double)(z-p.getZ());
                if (d>=bestDist || d>100) continue;
                BlockPos c=new BlockPos(x,y,z);
                if (!railSafe(level,c)) continue;
                best=c; bestDist=d;
            }
        return best;
    }
    private static boolean railSafe(ServerLevel level,BlockPos rail) {
        for(int dz=-9;dz<=9;dz++) {
            BlockPos p=rail.offset(0,0,dz);
            if(!level.isLoaded(p)) return false;
            BlockState state=level.getBlockState(p);
            // Only vanilla straight rails, never switches, crossings, ghost tracks or bends.
            if(!state.is(Blocks.RAIL) || state.getValue(RailBlock.SHAPE)!=RailShape.NORTH_SOUTH) return false;
            for(int dy=1;dy<=4;dy++) if(!level.getBlockState(p.above(dy)).isAir()) return false;
        }
        AABB protectedSpace = new AABB(rail.getX()-1.7,rail.getY(),rail.getZ()-9.5,
                                       rail.getX()+2.7,rail.getY()+4.5,rail.getZ()+10.5);
        return level.getEntitiesOfClass(AbstractMinecart.class,protectedSpace,e -> !e.getTags().contains(TAG)).isEmpty();
    }
    private static String ownerTag(UUID uuid){return "tc_dev54b_owner_"+uuid.toString().replace("-","");}
    private static void clearOwned(ServerPlayer player) {
        MinecraftServer server=ServerLifecycleHooks.getCurrentServer();
        if(server==null)return;
        String owner=ownerTag(player.getUUID());
        for(ServerLevel level:server.getAllLevels()) {
            List<Entity> delete=new ArrayList<>();
            for(Entity e:level.getAllEntities())
                if(e.getTags().contains(TAG) && e.getTags().contains(owner)) delete.add(e);
            for(Entity e:delete) e.discard();
        }
    }
    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) {
            SESSIONS.remove(player.getUUID());
            clearOwned(player);
        }
    }
    private static int start(CommandSourceStack source,String requested) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        if(!player.getAbilities().instabuild) {msg(source,"[DEV54B STOP] Creative only.");return 0;}
        String vehicle=requested==null?nextUnreviewed():requested;
        if(vehicle==null) {msg(source,"[DEV54B COMPLETE] All 50 unclassified candidates have saved review decisions. Upload " + report().toAbsolutePath());return 1;}
        if(!ROSTER_SET.contains(vehicle)) {msg(source,"[DEV54B STOP] Unknown/non-reviewable vehicle; previously accepted locomotives are protected.");return 0;}
        BlockPos rail=chooseRails(player);
        if(rail==null) {msg(source,"[DEV54B HOLD] Build 19 VANILLA rails NORTH/SOUTH, with 4 air blocks above; stand within 10 blocks of center. Nearby minecarts must be removed. No world changed.");return 0;}
        EntityType<?> type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(Traincraft.MOD_ID,vehicle));
        if(type==null){msg(source,"[DEV54B STOP] Registry missing "+vehicle);return 0;}
        Entity raw;
        try {raw=type.create(player.serverLevel());}catch(Exception ex){msg(source,"[DEV54B HOLD] Constructor error "+ex.getClass().getSimpleName());return 0;}
        if(!(raw instanceof AbstractMinecart cart)) {if(raw!=null)raw.discard();msg(source,"[DEV54B STOP] Not a minecart: "+vehicle);return 0;}
        // Only clean our own debug stock after all gates above have passed.
        SESSIONS.remove(player.getUUID());clearOwned(player);
        cart.moveTo(rail.getX()+0.5,rail.getY()+0.2,rail.getZ()+0.5,180f,0f);
        cart.setDeltaMovement(Vec3.ZERO);
        cart.setInvulnerable(true);
        cart.setGlowingTag(false);
        cart.addTag(TAG);cart.addTag(ownerTag(player.getUUID()));
        cart.setCustomName(Component.literal("[DEV54B "+(ROSTER.indexOf(vehicle)+1)+"/"+ROSTER.size()+"] "+vehicle));
        cart.setCustomNameVisible(true);
        if(!player.serverLevel().addFreshEntity(cart)){msg(source,"[DEV54B HOLD] Entity not added; no result recorded.");return 0;}
        Session session=new Session(player.getUUID(),rail,vehicle,player.serverLevel().dimension().location(),cart.getId());
        SESSIONS.put(player.getUUID(),session);
        msg(source,"[DEV54B REVIEW] "+vehicle+" ("+(ROSTER.indexOf(vehicle)+1)+"/"+ROSTER.size()+") . Watch both sides for repeated motion. Then choose PASS/FAIL.");
        msg(source,"[DEV54B NOTE] Artificial motion stimulus is visual diagnostic, not proof of native train driving or mechanical load.");
        return 1;
    }
    private static int result(CommandSourceStack source,String verdict) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        Session s=SESSIONS.get(player.getUUID());
        if(s==null){msg(source,"[DEV54B HOLD] No active review; use /tc_rods start");return 0;}
        if (!s.dimension.equals(player.serverLevel().dimension().location())) {msg(source,"[DEV54B HOLD] Wrong dimension. Return to test location.");return 0;}
        append(s.type,verdict);
        msg(source,"[DEV54B SAVED] "+s.type+" = "+verdict);
        SESSIONS.remove(player.getUUID());clearOwned(player);
        return start(source,null);
    }
    private static int stop(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        SESSIONS.remove(player.getUUID());clearOwned(player);
        msg(source,"[DEV54B STOPPED] Current test stopped; saved results retained. /tc_rods start resumes.");return 1;
    }
    private static int status(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        Map<String,String> done=completed();
        Session s=SESSIONS.get(player.getUUID());
        msg(source,"[DEV54B STATUS] reviewed="+done.size()+"/"+ROSTER.size()+" current="+(s==null?"none":s.type)+" next="+nextUnreviewed());
        msg(source,"[DEV54B FILE] "+report().toAbsolutePath());return 1;
    }
    private static void msg(CommandSourceStack source,String message) {
        source.sendSuccess(() -> Component.literal(message),false);
    }
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || SESSIONS.isEmpty())return;
        MinecraftServer server=ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        for(Session s:new ArrayList<>(SESSIONS.values())) {
            ServerPlayer player=server.getPlayerList().getPlayer(s.owner);
            if(player==null)continue;
            if(!player.serverLevel().dimension().location().equals(s.dimension)) {
                SESSIONS.remove(s.owner);
                clearOwned(player);
                continue;
            }
            Entity e=player.serverLevel().getEntity(s.entityId);
            if(!(e instanceof AbstractMinecart cart) || cart.isRemoved() || !cart.getTags().contains(TAG)) {
                if(!s.halted){s.halted=true;player.displayClientMessage(Component.literal("[DEV54B HOLD] Test locomotive missing. /tc_rods start to retry; no verdict recorded."),false);}
                continue;
            }
            if(Math.abs(cart.getZ()-(s.rail.getZ()+0.5))>7.0 || Math.abs(cart.getX()-(s.rail.getX()+0.5))>1.5 || !player.serverLevel().isLoaded(cart.blockPosition())) {
                cart.setDeltaMovement(Vec3.ZERO);
                if(!s.halted){s.halted=true;player.displayClientMessage(Component.literal("[DEV54B SAFETY HOLD] Vehicle left the 19-rail review corridor. /tc_rods stop; no verdict recorded."),false);}
                continue;
            }
            if(s.halted)continue;
            int cycle=s.tick++%CYCLE;
            String phase; double z=0;
            if(cycle<20){phase="REST";}
            else if(cycle<62){phase="FORWARD";z=-TEST_SPEED;}
            else if(cycle<78){phase="STOP";}
            else if(cycle<120){phase="REVERSE";z=TEST_SPEED;}
            else{phase="STOP";}
            cart.setDeltaMovement(0.0,0.0,z);
            if(!phase.equals(s.lastPhase)){
                s.lastPhase=phase;
                player.displayClientMessage(Component.literal("[DEV54B "+(ROSTER.indexOf(s.type)+1)+"/"+ROSTER.size()+"] "+s.type+" "+phase),true);
            }
        }
    }
}
