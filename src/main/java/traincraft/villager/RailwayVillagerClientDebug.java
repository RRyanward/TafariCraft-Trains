package traincraft.villager;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;

/** DEV50a3d client-side resource existence probe (never loaded on servers). */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RailwayVillagerClientDebug {
    private static final String[] ROLES = {"station_chief", "railway_engineer", "track_worker",
            "train_conductor", "fuel_depot_operator", "freight_yard_clerk"};
    private RailwayVillagerClientDebug() {}

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tcvillagerassets").executes(ctx -> {
            int present = 0;
            var assets = Minecraft.getInstance().getResourceManager();
            for (String role : ROLES) {
                ResourceLocation png = new ResourceLocation(Traincraft.MOD_ID,
                        "textures/entity/villager/profession/" + role + ".png");
                ResourceLocation meta = new ResourceLocation(Traincraft.MOD_ID,
                        "textures/entity/villager/profession/" + role + ".png.mcmeta");
                boolean p = assets.getResource(png).isPresent();
                boolean m = assets.getResource(meta).isPresent();
                if (p && m) present++;
                ctx.getSource().sendSuccess(() -> Component.literal("[TC villager assets] "
                        + role + " png=" + p + " mcmeta=" + m), false);
            }
            int total = present;
            ctx.getSource().sendSuccess(() -> Component.literal("[TC villager assets] "
                    + total + "/6 complete. Asset presence does not prove a layer rendered."), false);
            return total;
        }));
    }
}
