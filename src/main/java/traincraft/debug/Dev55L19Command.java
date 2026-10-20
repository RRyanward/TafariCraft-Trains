package traincraft.debug;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Narrow creative-only tuner. Commands take effect in the existing live review viewer. */
@Mod.EventBusSubscriber(modid="traincraft")
public final class Dev55L19Command {
    private Dev55L19Command(){}
    private static final Map<UUID,State> USER=new HashMap<>();
    private static final class State {String model="locomotive_steam_262t",beam="center";}
    private static State state(ServerPlayer p){return USER.computeIfAbsent(p.getUUID(),k->new State());}
    private static int say(CommandSourceStack src,String message){src.sendSuccess(()->Component.literal("[DEV55L19] "+message),false);return 1;}
    private static ServerPlayer require(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p=src.getPlayerOrException();if(!p.isCreative())throw new IllegalArgumentException("Creative mode only");return p;
    }
    private static int target(CommandSourceStack src,String value) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        State s=state(require(src));String m=Dev55L19Settings.model(value);if(!Dev55L19Settings.supported(m))return say(src,"Not a calibration candidate: "+m+" (protected/unregistered models remain unchanged)");s.model=m;return show(src);
    }
    private static int beam(CommandSourceStack src,String side) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        State s=state(require(src));s.beam=side;return show(src);
    }
    private static int mode(CommandSourceStack src,String value) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return edit(src,"mode",0F,value);
    }
    private static int edit(CommandSourceStack src,String field,float n,String label) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        State s=state(require(src));Dev55L19Settings.Beam b=Dev55L19Settings.get(s.model,s.beam);
        // DEV55L26: red tail lights have only a depth control, not beam steering.
        if(s.beam.equals("tail") && !field.equals("depth"))
            return say(src,"Tail lights support DEPTH only; all other settings unchanged.");
        if(s.beam.equals("tail") && (!Float.isFinite(n)||Math.abs(n)>0.25F))
            return say(src,"Tail depth limited to -0.25 to +0.25 blocks; use -0.04 to sink slightly.");
        Dev55L19Settings.Beam next=switch(field){
            case "mode" -> new Dev55L19Settings.Beam(label,0F,b.depth(),b.sideways(),b.height(),label.equals("angled")?10.8F:12F,b.face());
            case "angle" -> new Dev55L19Settings.Beam(b.mode(),n,b.depth(),b.sideways(),b.height(),b.reach(),b.face());
            case "depth" -> new Dev55L19Settings.Beam(b.mode(),b.angle(),n,b.sideways(),b.height(),b.reach(),b.face());
            case "sideways" -> new Dev55L19Settings.Beam(b.mode(),b.angle(),b.depth(),n,b.height(),b.reach(),b.face());
            case "height" -> new Dev55L19Settings.Beam(b.mode(),b.angle(),b.depth(),b.sideways(),n,b.reach(),b.face());
            case "reach" -> new Dev55L19Settings.Beam(b.mode(),b.angle(),b.depth(),b.sideways(),b.height(),n,b.face());
            case "face" -> new Dev55L19Settings.Beam(b.mode(),b.angle(),b.depth(),b.sideways(),b.height(),b.reach(),label);
            default -> b;
        };
        if(!Dev55L19Settings.set(s.model,s.beam,next))return say(src,"Rejected invalid value. Saved calibration unchanged.");return show(src);
    }
    private static int show(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        State s=state(require(src));Dev55L19Settings.Beam b=Dev55L19Settings.get(s.model,s.beam);
        if(s.beam.equals("tail"))return say(src,s.model+" / tail RED lens depth="+b.depth()+" blocks (negative=sink) [preview-only; no ray or ground changes]");
        float preset=b.mode().equals("angled")?(s.beam.equals("left")?-27F:s.beam.equals("right")?27F:0F):0F;
        float effective=preset+b.angle();
        return say(src,s.model+" / "+s.beam+" mode="+b.mode()+" steering="+b.angle()+" degrees effectiveHorizontal="+effective+" degrees depth="+b.depth()+" blocks sideways="+b.sideways()+" height="+b.height()+" reach="+b.reach()+" blocks facing="+b.face()+" pilotEnabled="+java.nio.file.Files.isRegularFile(Dev55L19Settings.switchPath())+" [use /tclightfleet diagnose to check the visible preview]");
    }
    /** DEV55L21R2: don't assume changing a profile also changes the spawned train. */
    private static int diagnose(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=require(src);
        State state=state(player);
        boolean pilot=java.nio.file.Files.isRegularFile(Dev55L19Settings.switchPath());
        boolean debug=java.nio.file.Files.isRegularFile(Dev55L19Settings.debugPath());
        int previews=0, matching=0;
        for(net.minecraft.world.entity.Entity e : player.serverLevel().getEntities(player,
                player.getBoundingBox().inflate(72D), ent -> ent.getCustomName()!=null
                    && Dev55L19Settings.TAG.equals(ent.getCustomName().getString()))) {
            previews++;
            net.minecraft.resources.ResourceLocation registryId=
                net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
            String actual=registryId==null?"unknown":registryId.getPath();
            if(actual.equals(state.model)) {
                matching++;
                say(src,"Matching Creative preview: "+actual+" entity="+e.getId()
                    +" distance="+String.format(java.util.Locale.ROOT,"%.1f",player.distanceTo(e))
                    +" blocks; pilotActive="+Dev55L19Settings.enabled(e));
                if((actual.equals("locomotive_electric_tram") || actual.equals("locomotive_electric_tram_ny"))
                       && e instanceof traincraft.entity.train.power.LegacyPoweredLocomotive electric)
                    say(src,"Tram: lighting preview ignores traction power; movement still requires energized Energy track or Redstone in power slot 0. linePower="
                        +electric.isOnPoweredEnergyTrack()+" backup="+electric.hasPortableBackupRedstone());
            }
        }
        Dev55L19Settings.Beam left=Dev55L19Settings.get(state.model,"left");
        Dev55L19Settings.Beam right=Dev55L19Settings.get(state.model,"right");
        say(src,"Target="+state.model+" nearbyReviewEntities="+previews+" matching="+matching
            +" pilotFlag="+pilot+" newOnlyCutover="+Dev55L19Settings.newOnly()+" diagnosticsFlag="+debug
            +" leftMode="+left.mode()+" leftSteering="+left.angle()
            +" rightMode="+right.mode()+" rightSteering="+right.angle());
        if(!pilot) say(src,"PILOT OFF: run RUN-DEV55L19-ON.cmd in the project root.");
        if(matching==0) say(src,"NO MATCHING PREVIEW: /tclightfleet target only edits the saved profile. Use /tclightreview start then /tclightreview next twice to display Adler (third entry), or step to the matching vehicle.");
        if(matching>0 && pilot && state.model.equals("locomotive_steam_adler"))
            say(src,"Adler preview present. Enable RUN-DEV55L19-DEBUG-ON.cmd for [DEV55L21-ADLER-RAY] evidence in latest.log.");
        return 1;
    }
    @SubscribeEvent public static void onRegister(RegisterCommandsEvent e){
        var root=Commands.literal("tclightfleet").requires(src->src.hasPermission(2))
            .then(Commands.literal("show").executes(ctx->show(ctx.getSource())))
            .then(Commands.literal("diagnose").executes(ctx->diagnose(ctx.getSource())));
        var target=Commands.literal("target");
        // Canonical model IDs and shorthand aliases. No free-form path input.
        target.then(Commands.argument("model",StringArgumentType.word()).suggests((ctx,b)->{
            for(String s: new String[]{"262t","adler","berkshire_1225","berkshire_765","br80","c11","c41_080","c41t","climax","climax_2","fowler","glyn_042t","gs4","gwr_101_class","gwr_42xx","gwr_72xx","j50","lssp7","pe","rw_type_2","rw_type_3","s100_uk","s100_us","shay_3_truck","skookum","snow_plow","southern1102","star_class","u57","wwcp_062t","gp7","kof","shunter","br_e69","minetrain","wooden_tram","ny_tram"})b.suggest(s);
            return b.buildFuture();}).executes(ctx->target(ctx.getSource(),StringArgumentType.getString(ctx,"model"))));
        root.then(target);
        var select=Commands.literal("select");for(String s:new String[]{"center","left","right","tail"})select.then(Commands.literal(s).executes(ctx->beam(ctx.getSource(),s)));root.then(select);
        var mode=Commands.literal("mode");for(String s:new String[]{"straight","angled"})mode.then(Commands.literal(s).executes(ctx->mode(ctx.getSource(),s)));root.then(mode);
        var face=Commands.literal("face");for(String s:new String[]{"auto","+x","-x","+z","-z"})face.then(Commands.literal(s).executes(ctx->edit(ctx.getSource(),"face",0F,s)));root.then(face);
        for(String field:new String[]{"angle","depth","sideways","height","reach"}) {
            var lit=Commands.literal(field);
            lit.then(Commands.argument("value",FloatArgumentType.floatArg(-85F,85F))
                .executes(ctx->edit(ctx.getSource(),field,FloatArgumentType.getFloat(ctx,"value"),"")));
            root.then(lit);
        }
        e.getDispatcher().register(root);
    }
}
