package traincraft.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.Traincraft;
import traincraft.registry.TCItems;

/** DEV51b: each server-side player receives exactly one guide across rejoins and deaths. */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TafariCraftGuideFirstJoinEvents {
    private static final String KEY = "TafariCraftGuideGivenDEV51b";
    private TafariCraftGuideFirstJoinEvents() { }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(KEY)) return;
        persisted.putBoolean(KEY, true);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
        ItemStack guide = new ItemStack(TCItems.GUIDE.get());
        if (!player.getInventory().add(guide) && !guide.isEmpty()) {
            player.drop(guide, false);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag source = event.getOriginal().getPersistentData()
                .getCompound(Player.PERSISTED_NBT_TAG);
        if (source.getBoolean(KEY)) {
            CompoundTag destination = event.getEntity().getPersistentData();
            CompoundTag saved = destination.getCompound(Player.PERSISTED_NBT_TAG);
            saved.putBoolean(KEY, true);
            destination.put(Player.PERSISTED_NBT_TAG, saved);
        }
    }
}
