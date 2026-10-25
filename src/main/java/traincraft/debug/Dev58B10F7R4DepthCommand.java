package traincraft.debug;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.util.*;
import traincraft.lighting.Dev58B10F7R4DepthSettings;

/** F7R4 creative-only in-game six-native lamp depth tuning. NOT /tclightfleet. */
@Mod.EventBusSubscriber(modid="traincraft",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev58B10F7R4DepthCommand {
    private Dev58B10F7R4DepthCommand(){}
    private static final Map<UUID,String> TARGET=new HashMap<>();
    private static final Map<String,String> ALIAS=Map.of(
        "zeroed","locomotive_electric_high_speed_zeroed",
        "vl10","locomotive_electric_vl10",
        "sd70m","locomotive_diesel_sd70",
        "cd742","locomotive_diesel_cd742",
        "chme3","locomotive_diesel_chme3",
        "sd40-2","locomotive_diesel_sd40");
    private static int reply(CommandSourceStack source,String msg){
        source.sendSuccess(()->Component.literal("[F7R4] "+msg),false);return 1;
    }
    private static ServerPlayer creative(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        if(!player.isCreative())throw new IllegalArgumentException("Creative mode only");
        if(!Files.isRegularFile(net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("DEV58B10F7-SIX-LIGHTS-TEST-ON.flag")))
            throw new IllegalArgumentException("F7 six-train lighting test OFF; no change made");
        return player;
    }
    private static String model(CommandSourceStack s) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p=creative(s);return TARGET.getOrDefault(p.getUUID(),"locomotive_electric_high_speed_zeroed");
    }
    private static int target(CommandSourceStack s,String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String found=ALIAS.get(name.toLowerCase(Locale.ROOT));
        if(found==null)return reply(s,"Unknown train. Use: zeroed vl10 sd70m cd742 chme3 sd40-2");
        TARGET.put(creative(s).getUUID(),found);
        return reply(s,"Target="+name+". Use /tcsixlights front 0.05 or rear -0.05 (blocks). "+
            "One lamp: /tcsixlights front 2 0.02");
    }
    private static int adjust(CommandSourceStack s,String end,int index,float step) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String id=model(s);
        try{
            float value=Dev58B10F7R4DepthSettings.adjust(id,end,index,step);
            return reply(s,(index==0?end+" ALL":end+" #"+index)+" adjusted "+
                String.format(Locale.ROOT,"%+.3f",step)+" blocks; stored offset="+
                String.format(Locale.ROOT,"%+.3f",value)+". "+Dev58B10F7R4DepthSettings.status(id,end));
        }catch(Exception ex){return reply(s,"HOLD: "+ex.getMessage());}
    }
    private static int reset(CommandSourceStack s,String end,int index) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String id=model(s);
        try{Dev58B10F7R4DepthSettings.reset(id,end,index);
            return reply(s,"RESET "+end+(index==0?" ALL":" #"+index)+". "+Dev58B10F7R4DepthSettings.status(id,end));
        }catch(Exception ex){return reply(s,"HOLD: "+ex.getMessage());}
    }
    private static int status(CommandSourceStack s) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String id=model(s);
        reply(s,Dev58B10F7R4DepthSettings.status(id,"front"));
        return reply(s,Dev58B10F7R4DepthSettings.status(id,"rear"));
    }
    private static int export(CommandSourceStack s) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        creative(s);
        try{return reply(s,"Saved 30-lamp export to "+Dev58B10F7R4DepthSettings.export());}
        catch(Exception ex){return reply(s,"EXPORT HOLD: "+ex.getMessage());}
    }
    @SubscribeEvent public static void register(RegisterCommandsEvent e){
        var root=Commands.literal("tcsixlights").requires(s->s.hasPermission(2))
            .then(Commands.literal("help").executes(c->reply(c.getSource(),
                "target zeroed|vl10|sd70m|cd742|chme3|sd40-2; front +/-0.05; rear +/-0.05; "+
                "front <number> +/-0.02; reset front|rear [number]; status; export")))
            .then(Commands.literal("status").executes(c->status(c.getSource())))
            .then(Commands.literal("export").executes(c->export(c.getSource())))
            .then(Commands.literal("target")
                .then(Commands.argument("train",StringArgumentType.word()).suggests((ctx,b)->{
                    for(String a:ALIAS.keySet())b.suggest(a);return b.buildFuture();
                }).executes(c->target(c.getSource(),StringArgumentType.getString(c,"train")))));
        for(String end:List.of("front","rear")){
            var side=Commands.literal(end)
                .then(Commands.argument("step",FloatArgumentType.floatArg(-0.25F,0.25F))
                    .executes(c->adjust(c.getSource(),end,0,FloatArgumentType.getFloat(c,"step"))))
                .then(Commands.argument("index",IntegerArgumentType.integer(1,5))
                    .then(Commands.argument("step",FloatArgumentType.floatArg(-0.25F,0.25F))
                        .executes(c->adjust(c.getSource(),end,
                            IntegerArgumentType.getInteger(c,"index"),FloatArgumentType.getFloat(c,"step")))));
            root.then(side);
            root.then(Commands.literal("reset").then(Commands.literal(end)
                .executes(c->reset(c.getSource(),end,0))
                .then(Commands.argument("index",IntegerArgumentType.integer(1,5))
                    .executes(c->reset(c.getSource(),end,IntegerArgumentType.getInteger(c,"index"))))));
        }
        e.getDispatcher().register(root);
    }
}
