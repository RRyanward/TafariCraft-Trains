package traincraft.debug;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DEV55L1: 41-entry review roster ONLY, using real Minecraft entity renderers.
 * No terrain/rails/saves are edited. Preview entities hover and are temporary;
 * use only a disposable Creative test world and `/tclightreview stop` when done.
 */
@Mod.EventBusSubscriber(modid="traincraft")
public final class Dev55FleetLightReviewCommand {
    private Dev55FleetLightReviewCommand(){}
    private record Entry(String name,String entity){}
    private static final List<Entry> FLEET=List.of(
        new Entry("262t","locomotive_steam_262t"),
        new Entry("4_4_0","locomotive_steam_4_4_0"),
        new Entry("adler","locomotive_steam_adler"),
        new Entry("berkshire_1225","locomotive_steam_berkshire_1225"),
        new Entry("berkshire_765","locomotive_steam_berkshire_765"),
        new Entry("br80","locomotive_steam_br80"),
        new Entry("britannia","locomotive_steam_br_britannia"),
        new Entry("c11","locomotive_steam_c11"),
        new Entry("c41_080","locomotive_steam_c41_080"),
        new Entry("c41t","locomotive_steam_c41t"),
        new Entry("cherepanov","locomotive_steam_cherepanov"),
        new Entry("climax","locomotive_steam_climax"),
        new Entry("climax_2","locomotive_steam_climax_2"),
        new Entry("fowler","locomotive_steam_fowler"),
        new Entry("glyn_042t","locomotive_steam_glyn_042t"),
        new Entry("gs4","locomotive_steam_gs4"),
        new Entry("gwr_101_class","locomotive_steam_gwr_101_class"),
        new Entry("gwr_42xx","locomotive_steam_gwr_42xx"),
        new Entry("gwr_72xx","locomotive_steam_gwr_72xx"),
        new Entry("j50","locomotive_steam_j50"),
        new Entry("lssp7","locomotive_steam_lssp7"),
        new Entry("pe","locomotive_steam_pe"),
        new Entry("rw_type_2","locomotive_steam_rw_type_2"),
        new Entry("rw_type_3","locomotive_steam_rw_type_3"),
        new Entry("s100_uk","locomotive_steam_s100_uk"),
        new Entry("s100_us","locomotive_steam_s100_us"),
        new Entry("shay_3_truck","locomotive_steam_shay_3_truck"),
        new Entry("skookum","locomotive_steam_skookum"),
        new Entry("snow_plow","locomotive_steam_snow_plow"),
        new Entry("southern1102","locomotive_steam_southern1102"),
        new Entry("star_class","locomotive_steam_star_class"),
        new Entry("u57","locomotive_steam_u57"),
        new Entry("vb_shay","locomotive_steam_vb_shay"),
        new Entry("wwcp_062t","locomotive_steam_wwcp_062t"),
        new Entry("br_e69","locomotive_electric_br_e69"),
        new Entry("gp7","locomotive_diesel_gp7"),
        new Entry("kof","locomotive_diesel_kof"),
        new Entry("minetrain","locomotive_electric_minetrain"),
        new Entry("shunter","locomotive_diesel_shunter"),
        new Entry("wooden_tram","locomotive_electric_tram"),
        new Entry("ny_tram","locomotive_electric_tram_ny")
    );
    private static final class Review {
        int index=0;
        final List<UUID> spawned=new ArrayList<>();
        final Map<UUID,Vec3> anchors=new LinkedHashMap<>();
    }
    private static final Map<UUID,Review> REVIEWS=new HashMap<>();
    private static final int PAGE_SIZE=6;
    /** DEV55L4: read the selected viewer ID without changing lineup/spawning. */
    public static String dev55L4SelectedModel(UUID owner) {
        Review current=REVIEWS.get(owner);
        return current == null ? null : FLEET.get(current.index).entity();
    }

    @SubscribeEvent public static void register(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("tclightreview")
            .requires(source->source.hasPermission(2))
            .then(Commands.literal("start").executes(ctx->start(ctx.getSource())))
            .then(Commands.literal("next").executes(ctx->step(ctx.getSource(),1)))
            .then(Commands.literal("prev").executes(ctx->step(ctx.getSource(),-1)))
            .then(Commands.literal("show").executes(ctx->show(ctx.getSource())))
            .then(Commands.literal("lineup").executes(ctx->lineup(ctx.getSource())))
            .then(Commands.literal("pass").executes(ctx->record(ctx.getSource(),"PASS",""))
                .then(Commands.argument("note",StringArgumentType.greedyString())
                    .executes(ctx->record(ctx.getSource(),"PASS",StringArgumentType.getString(ctx,"note")))))
            .then(Commands.literal("missing").executes(ctx->record(ctx.getSource(),"MISSING",""))
                .then(Commands.argument("note",StringArgumentType.greedyString())
                    .executes(ctx->record(ctx.getSource(),"MISSING",StringArgumentType.getString(ctx,"note")))))
            .then(Commands.literal("report").executes(ctx->report(ctx.getSource())))
            .then(Commands.literal("stop").executes(ctx->stop(ctx.getSource())))
        );
    }
    private static ServerPlayer player(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return source.getPlayerOrException();
    }
    private static void say(CommandSourceStack source,String message){
        source.sendSuccess(()->Component.literal("[DEV55 LIGHT] "+message),false);
    }
    private static boolean creative(CommandSourceStack source,ServerPlayer p){
        if(p.isCreative())return true;
        say(source,"Review is Creative-only. Use a disposable Creative world.");
        return false;
    }
    private static Review review(ServerPlayer p){return REVIEWS.computeIfAbsent(p.getUUID(),id->new Review());}
    private static int purgeOwned(ServerLevel level,UUID owner){
        int count=0;
        for(Entity e:level.getAllEntities()){
            if(e.getPersistentData().getBoolean("DEV55LIGHT_REVIEW")
                && e.getPersistentData().hasUUID("DEV55LIGHT_REVIEW_OWNER")
                && owner.equals(e.getPersistentData().getUUID("DEV55LIGHT_REVIEW_OWNER"))){
                e.discard();count++;
            }
        }
        return count;
    }
    private static int start(CommandSourceStack s) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p=player(s);
        if(!creative(s,p))return 0;
        Review r=review(p);
        clear(p.serverLevel(),r);
        purgeOwned(p.serverLevel(),p.getUUID());
        r.index=0;
        say(s,"Viewer: "+FLEET.size()+" listed entries; test lamps ON at night without fuel or power. `next`, `prev`, `pass`, `missing`, `lineup`, `report`, `stop`.");
        return show(s);
    }
    private static int step(CommandSourceStack s,int delta) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p=player(s);if(!creative(s,p))return 0;
        Review r=review(p);r.index=Math.floorMod(r.index+delta,FLEET.size());return show(s);
    }
    private static void clear(ServerLevel level,Review r){
        for(UUID id:r.spawned){Entity e=level.getEntity(id);if(e!=null && e.getPersistentData().getBoolean("DEV55LIGHT_REVIEW"))e.discard();}
        r.spawned.clear();r.anchors.clear();
    }
    private static void spawn(ServerPlayer p,Review r,int index,Vec3 pos){
        Entry spec=FLEET.get(index);
        EntityType<?> type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("traincraft",spec.entity()));
        if(type==null || !new ResourceLocation("traincraft",spec.entity()).equals(
            ForgeRegistries.ENTITY_TYPES.getKey(type)))return;
        Entity e=type.create(p.serverLevel());if(e==null)return;
        e.setPos(pos.x,pos.y,pos.z);e.setYRot(p.getYRot()+180F);
        e.setNoGravity(true);e.setInvulnerable(true);
        e.getPersistentData().putBoolean("DEV55LIGHT_REVIEW",true);
        // DEV55L3: vanilla synced custom name identifies review previews on the client.
        // Hidden from players; ordinary entities never receive this marker.
        e.setCustomName(Component.literal("DEV55L3_TEST_LAMPS"));
        e.setCustomNameVisible(false);
        e.getPersistentData().putUUID("DEV55LIGHT_REVIEW_OWNER",p.getUUID());
        if(p.serverLevel().addFreshEntity(e)){r.spawned.add(e.getUUID());r.anchors.put(e.getUUID(),pos);}
    }
    private static int show(CommandSourceStack s) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p=player(s);if(!creative(s,p))return 0;
        Review r=review(p);clear(p.serverLevel(),r);
        Entry entry=FLEET.get(r.index);
        Vec3 f=p.getLookAngle();
        double horizontal=Math.hypot(f.x,f.z);if(horizontal<0.01D)horizontal=1.0D;
        Vec3 pos=p.position().add(f.x/horizontal*7.0D,0.55D,f.z/horizontal*7.0D);
        spawn(p,r,r.index,pos);
        boolean has=traincraft.lighting.Dev55FleetGroundLights.mapped(entry.entity());
        say(s,(r.index+1)+"/"+FLEET.size()+" "+entry.name()+" entity="+entry.entity()+" existingMarker="+(has?"YES":"MISSING/UNRESOLVED")
            +" previewEntities="+r.spawned.size()+". Inspect front and both sides; use PASS/MISSING then next.");
        return 1;
    }
    private static int lineup(CommandSourceStack s) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p=player(s);if(!creative(s,p))return 0;
        Review r=review(p);clear(p.serverLevel(),r);
        int begin=(r.index/PAGE_SIZE)*PAGE_SIZE;
        Vec3 f=p.getLookAngle();double h=Math.hypot(f.x,f.z);if(h<0.01D)h=1D;
        double fx=f.x/h,fz=f.z/h;double sx=-fz,sz=fx;
        for(int i=0;i<PAGE_SIZE&&begin+i<FLEET.size();i++){
            double lateral=(i-2.5D)*12.0D;
            spawn(p,r,begin+i,p.position().add(fx*13.0D+sx*lateral,0.45D,fz*13.0D+sz*lateral));
        }
        say(s,"Test row entries "+(begin+1)+"–"+Math.min(FLEET.size(),begin+PAGE_SIZE)
           +"; "+r.spawned.size()+" meshes spawned. Use next until the next group then lineup again.");
        return 1;
    }
    private static String cell(String s){return "\""+s.replace("\"","\"\"").replace("\r"," ").replace("\n"," ")+"\"";}
    private static int record(CommandSourceStack s,String status,String notes) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p=player(s);if(!creative(s,p))return 0;
        Review r=review(p);Entry item=FLEET.get(r.index);
        Path path=FMLPaths.GAMEDIR.get().resolve("DEV55L1-LIGHT-REVIEW.csv");
        String line=Instant.now()+","+(r.index+1)+","+cell(item.name())+","+cell(item.entity())+","+status+","+cell(notes)+"\r\n";
        try{
            if(!Files.exists(path))Files.writeString(path,"time,index,name,entity,status,note\r\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            Files.writeString(path,line,StandardCharsets.UTF_8,StandardOpenOption.APPEND);
            say(s,item.name()+" marked "+status+" (saved: "+path.getFileName()+").");
            return 1;
        }catch(Exception ex){say(s,"EXPORT FAILED: "+ex.getMessage());return 0;}
    }
    private static int report(CommandSourceStack s) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p=player(s);Review r=review(p);
        say(s,FLEET.size()+" entries; current="+(r.index+1)+" "+FLEET.get(r.index).name()
          +"; PASS/MISSING log at run/DEV55L1-LIGHT-REVIEW.csv (append-only).");return 1;
    }
    private static int stop(CommandSourceStack s) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p=player(s);Review r=REVIEWS.remove(p.getUUID());
        if(r!=null)clear(p.serverLevel(),r);
        int purged=purgeOwned(p.serverLevel(),p.getUUID());
        say(s,"Stopped; preview entities removed. Report preserved.");return 1;
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level)||level.getGameTime()%5L!=0L)return;
        for(Review r:REVIEWS.values()){
            for(Map.Entry<UUID,Vec3> e:r.anchors.entrySet()){
                Entity entity=level.getEntity(e.getKey());if(entity==null||!entity.isAlive())continue;
                if(!entity.getPersistentData().getBoolean("DEV55LIGHT_REVIEW"))continue;
                Vec3 pos=e.getValue();entity.setNoGravity(true);entity.setDeltaMovement(Vec3.ZERO);
                if(entity.position().distanceToSqr(pos)>0.0001D)entity.setPos(pos.x,pos.y,pos.z);
            }
        }
    }
}
