package traincraft.debug;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Explicit per-beam controls so a side lamp can be straightened without touching the other two. */
@Mod.EventBusSubscriber(modid="traincraft")
public final class Dev55L5OpticsCommand {
    private static final Map<UUID,String> SELECTED=new ConcurrentHashMap<>();
    private Dev55L5OpticsCommand(){}
    @SubscribeEvent public static void register(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("tclightoptics")
            .requires(src->src.hasPermission(2))
            .then(Commands.literal("show").executes(ctx->show(ctx.getSource())))
            .then(Commands.literal("select").then(Commands.argument("beam",StringArgumentType.word())
                .executes(ctx->select(ctx.getSource(),StringArgumentType.getString(ctx,"beam")))))
            .then(Commands.literal("straight").executes(ctx->change(ctx.getSource(),"straight",0F)))
            .then(Commands.literal("axisleft").executes(ctx->change(ctx.getSource(),"axis",-1F)))
            .then(Commands.literal("axisright").executes(ctx->change(ctx.getSource(),"axis",1F)))
            .then(Commands.literal("axisnormal").executes(ctx->change(ctx.getSource(),"axis",0F)))
            .then(Commands.literal("left").executes(ctx->change(ctx.getSource(),"angle",-3F)))
            .then(Commands.literal("right").executes(ctx->change(ctx.getSource(),"angle",3F)))
            .then(Commands.literal("forward").executes(ctx->change(ctx.getSource(),"forward",0.125F)))
            .then(Commands.literal("back").executes(ctx->change(ctx.getSource(),"forward",-0.125F)))
            .then(Commands.literal("sideleft").executes(ctx->change(ctx.getSource(),"side",-0.125F)))
            .then(Commands.literal("sideright").executes(ctx->change(ctx.getSource(),"side",0.125F)))
            .then(Commands.literal("angle").then(Commands.argument("degrees",DoubleArgumentType.doubleArg(-65,65))
                .executes(ctx->change(ctx.getSource(),"absoluteAngle",(float)DoubleArgumentType.getDouble(ctx,"degrees")))))
            .then(Commands.literal("reach").then(Commands.argument("blocks",DoubleArgumentType.doubleArg(2,16))
                .executes(ctx->change(ctx.getSource(),"reach",(float)DoubleArgumentType.getDouble(ctx,"blocks")))))
            .then(Commands.literal("reset").executes(ctx->change(ctx.getSource(),"reset",0F)))
        );
    }
    private static void say(CommandSourceStack src,String msg){src.sendSuccess(()->Component.literal("[DEV55L5 OPTICS] "+msg),false);}
    private static String model(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        ServerPlayer p=src.getPlayerOrException();
        if(!p.isCreative()){say(src,"Creative test mode only.");return null;}
        String id=Dev55FleetLightReviewCommand.dev55L4SelectedModel(p.getUUID());
        if(id==null){say(src,"Start with /tclightreview start");return null;}
        if(!Dev55L5Optics.supported(id)){say(src,"Frozen PASS or HOLD: "+id);return null;}
        return id;
    }
    private static String beam(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        return SELECTED.getOrDefault(src.getPlayerOrException().getUUID(),"center");
    }
    private static int select(CommandSourceStack src,String beam) throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        if(model(src)==null)return 0;
        beam=beam.toLowerCase(java.util.Locale.ROOT);
        if(!Dev55L5Optics.validBeam(beam)){say(src,"Choose center, left, or right");return 0;}
        SELECTED.put(src.getPlayerOrException().getUUID(),beam);
        return show(src);
    }
    private static int show(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        String id=model(src);if(id==null)return 0;
        String b=beam(src);Dev55L5Optics.Setting s=Dev55L5Optics.get(id,b);
        say(src,"model="+id+" beam="+b+" horizontal="+s.angle()+"deg forward="+s.forward()+" blocks sideways="+s.sideways()+" blocks reach="+s.reach()+" blocks axis90="+s.axisQuarterTurns()+" (axisleft=-1, axisright=+1). CSV=run/DEV55L5-LIGHT-GEOMETRY-TEST.csv");
        return 1;
    }
    private static int change(CommandSourceStack src,String action,float val) throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        String id=model(src);if(id==null)return 0;
        String b=beam(src);Dev55L5Optics.Setting old=Dev55L5Optics.get(id,b);
        float a=old.angle(), f=old.forward(),s=old.sideways(),r=old.reach(); int axis=old.axisQuarterTurns();
        switch(action){
            case "straight" -> a=0F;
            case "axis" -> axis=(int)val;
            case "angle" -> a=Math.max(-65F,Math.min(65F,a+val));
            case "absoluteAngle" -> a=val;
            case "forward" -> f=Math.max(-2F,Math.min(2F,f+val));
            case "side" -> s=Math.max(-2F,Math.min(2F,s+val));
            case "reach" -> r=val;
            case "reset" -> {a=Dev55L5Optics.defaultAngle(b);f=0F;s=0F;r=Dev55L5Optics.defaultReach(b);axis=0;}
            default -> {return 0;}
        }
        if(!Dev55L5Optics.set(id,b,new Dev55L5Optics.Setting(a,f,s,r,axis))){say(src,"Save failed, check latest.log");return 0;}
        return show(src);
    }
}
