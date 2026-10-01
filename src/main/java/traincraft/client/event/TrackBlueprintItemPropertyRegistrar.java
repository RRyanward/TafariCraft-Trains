package traincraft.client.event;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.item.TrackBlueprintItem;

@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class TrackBlueprintItemPropertyRegistrar {

    private static final ResourceLocation FILLED_ID =
            new ResourceLocation(Traincraft.MOD_ID, "filled");

    private TrackBlueprintItemPropertyRegistrar() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(
                () -> {
                    for (ResourceLocation key : ForgeRegistries.ITEMS.getKeys()) {
                        if (!Traincraft.MOD_ID.equals(key.getNamespace())
                                || !key.getPath().contains("blueprint")) {
                            continue;
                        }
                        Item item = ForgeRegistries.ITEMS.getValue(key);
                        if (!(item instanceof TrackBlueprintItem)) {
                            continue;
                        }
                        ItemProperties.register(
                                item,
                                FILLED_ID,
                                (stack, level, entity, seed) ->
                                        TrackBlueprintItem.isLocked(stack)
                                                ? 1.0F
                                                : 0.0F);
                    }
                });
    }
}