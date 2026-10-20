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

/** Live, review-world-only angle controls. No production lighting changes. */
@Mod.EventBusSubscriber(modid="traincraft")
public final class Dev55L4AimCommand {
    private Dev55L4AimCommand() { }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tclightaim")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("show").executes(ctx -> show(ctx.getSource())))
            .then(Commands.literal("up").executes(ctx -> step(ctx.getSource(),-1.5F)))
            .then(Commands.literal("down").executes(ctx -> step(ctx.getSource(),1.5F)))
            .then(Commands.literal("reset").executes(ctx -> adjust(ctx.getSource(),0F)))
            .then(Commands.literal("set")
                .then(Commands.argument("model",StringArgumentType.word())
                    .then(Commands.argument("degrees",DoubleArgumentType.doubleArg(-20D,20D))
                        .executes(ctx -> set(ctx.getSource(),StringArgumentType.getString(ctx,"model"),
                            (float)DoubleArgumentType.getDouble(ctx,"degrees"))))))
        );
    }
    private static void say(CommandSourceStack source,String message) {
        source.sendSuccess(() -> Component.literal("[DEV55L4 AIM] " + message),false);
    }
    private static String current(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p=source.getPlayerOrException();
        if (!p.isCreative()) {say(source,"Creative-only test mode.");return null;}
        String id=Dev55FleetLightReviewCommand.dev55L4SelectedModel(p.getUUID());
        if (id==null) say(source,"Start the fleet viewer first: /tclightreview start");
        return id;
    }
    private static int show(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String id=current(source);if(id==null)return 0;
        float angle=Dev55L4AimTuner.get(id);
        say(source,"Selected="+id+" downwardDegrees="+angle+"; calibration CSV=run/DEV55L4-LIGHT-AIM-TEST.csv");
        if(Dev55L4AimTuner.frozen(id))say(source,"Frozen PASS: no angle changes permitted.");
        else if(!Dev55L4AimTuner.supported(id))say(source,"No tunable steam headlamp (HOLD). Use /tclightaim set <model> <degrees> for a supported one.");
        return 1;
    }
    private static int step(CommandSourceStack source,float delta) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String id=current(source);if(id==null)return 0;
        return adjust(source,Math.max(-20F,Math.min(20F,Dev55L4AimTuner.get(id)+delta)));
    }
    private static int adjust(CommandSourceStack source,float degrees) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String id=current(source);if(id==null)return 0;
        return apply(source,id,degrees);
    }
    private static int set(CommandSourceStack source,String model,float degrees) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (current(source)==null)return 0;
        return apply(source,model,degrees);
    }
    private static int apply(CommandSourceStack source,String model,float degrees) {
        String id=Dev55L4AimTuner.normalize(model);
        if(Dev55L4AimTuner.frozen(id)) {say(source,"FROZEN PASS; refused changes to "+id);return 0;}
        if(!Dev55L4AimTuner.supported(id)) {say(source,"HOLD: unsupported headlamp ID "+id);return 0;}
        if(Dev55L4AimTuner.set(id,degrees)) {
            say(source,id+" downwardDegrees="+degrees+" saved. Move your view slightly to refresh its beam.");return 1;
        }
        say(source,"Could not save calibration; see latest.log.");return 0;
    }
}
