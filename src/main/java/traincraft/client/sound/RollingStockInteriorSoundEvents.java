package traincraft.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;

/**
 * TC_BATCH7_R3A_INTERIOR_AND_CROSSING_SOUND_MUFFLING
 *
 * Local-listener interior/cabin acoustic effect.
 *
 * While the local player is riding TafariCraft rolling stock:
 *
 * - enclosed passenger/caboose/baggage stock hears outside world sounds at 42%
 * - locomotive cabs hear outside world sounds at 56%
 * - Tracks Builder cab hears outside world sounds at 58%
 * - open freight/tender/livestock stock hears outside world sounds at 72%
 *
 * Both TafariCraft crossing bell events use SoundSource.BLOCKS and therefore
 * receive the exact same cabin/interior attenuation:
 *
 *   traincraft:crossing_bell
 *   traincraft:modular_crossing_bell
 *
 * This changes only the local client's listener volume. The server still emits
 * sounds at their normal volume, so players standing outside hear full-volume
 * trains, horns, whistles, crossings, weather, mobs, etc.
 *
 * Music, records, voice chat category, and MASTER/UI sounds are deliberately
 * left untouched.
 *
 * This is volume attenuation only. It avoids invasive OpenAL filters and does
 * not alter movement, track pathing, coupling, sound timing, or sound pitch.
 */
@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RollingStockInteriorSoundEvents {

    private static final float ENCLOSED_CAR_VOLUME = 0.42F;
    private static final float LOCOMOTIVE_CAB_VOLUME = 0.56F;
    private static final float BUILDER_CAB_VOLUME = 0.58F;
    private static final float OPEN_STOCK_VOLUME = 0.72F;
    private static final float DEFAULT_ROLLING_STOCK_VOLUME = 0.60F;

    private static final ResourceLocation CROSSING_BELL =
            new ResourceLocation(Traincraft.MOD_ID, "crossing_bell");
    private static final ResourceLocation MODULAR_CROSSING_BELL =
            new ResourceLocation(Traincraft.MOD_ID, "modular_crossing_bell");

    private RollingStockInteriorSoundEvents() {
    }

    @SubscribeEvent
    public static void onPlayLevelSound(PlayLevelSoundEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        if (player == null || player.level() != event.getLevel()) {
            return;
        }

        Entity vehicle = player.getVehicle();
        if (vehicle == null) {
            return;
        }

        ResourceLocation vehicleId = ForgeRegistries.ENTITY_TYPES.getKey(vehicle.getType());
        if (!isTafariRollingStock(vehicleId)) {
            return;
        }

        SoundSource source = event.getSource();
        if (!isWorldSoundSource(source)) {
            return;
        }

        float multiplier = interiorMultiplier(vehicleId);

        /*
         * Crossing bells intentionally flow through the same multiplier as every
         * other exterior world sound. Keep the explicit identity check here so a
         * future category change cannot accidentally bypass the cabin effect.
         */
        Holder<SoundEvent> soundHolder = event.getSound();
        if (soundHolder != null) {
            ResourceLocation soundId = soundHolder.value().getLocation();
            if (CROSSING_BELL.equals(soundId) || MODULAR_CROSSING_BELL.equals(soundId)) {
                event.setNewVolume(event.getNewVolume() * multiplier);
                return;
            }
        }

        event.setNewVolume(event.getNewVolume() * multiplier);
    }

    private static boolean isWorldSoundSource(SoundSource source) {
        return switch (source) {
            case BLOCKS,
                 WEATHER,
                 HOSTILE,
                 NEUTRAL,
                 PLAYERS,
                 AMBIENT -> true;

            /*
             * MASTER includes many UI/menu sounds.
             * MUSIC/RECORDS/VOICE are player media/preferences and should not be
             * changed simply because the player boarded a train.
             */
            case MASTER,
                 MUSIC,
                 RECORDS,
                 VOICE -> false;
        };
    }

    private static boolean isTafariRollingStock(ResourceLocation id) {
        if (id == null || !Traincraft.MOD_ID.equals(id.getNamespace())) {
            return false;
        }

        String path = id.getPath();

        return path.startsWith("locomotive_")
                || path.startsWith("steam_tender")
                || path.equals("tracks_builder")
                || path.equals("freight_cart")
                || path.equals("caboose")
                || path.equals("passenger_coach_blue")
                || path.equals("livestock_car")
                || path.equals("mob_transport_car")
                || path.endsWith("_passenger_car")
                || path.endsWith("_baggage_car")
                || path.endsWith("_observatory_car")
                || path.endsWith("_tail_car")
                || path.endsWith("_coach")
                || path.endsWith("_car");
    }

    private static float interiorMultiplier(ResourceLocation id) {
        String path = id.getPath();

        if (path.equals("tracks_builder")) {
            return BUILDER_CAB_VOLUME;
        }

        if (path.startsWith("locomotive_")) {
            return LOCOMOTIVE_CAB_VOLUME;
        }

        if (isEnclosedCar(path)) {
            return ENCLOSED_CAR_VOLUME;
        }

        if (isOpenStock(path)) {
            return OPEN_STOCK_VOLUME;
        }

        return DEFAULT_ROLLING_STOCK_VOLUME;
    }

    private static boolean isEnclosedCar(String path) {
        return path.equals("caboose")
                || path.equals("passenger_coach_blue")
                || path.endsWith("_passenger_car")
                || path.endsWith("_baggage_car")
                || path.endsWith("_observatory_car")
                || path.endsWith("_tail_car")
                || path.endsWith("_coach");
    }

    private static boolean isOpenStock(String path) {
        return path.startsWith("steam_tender")
                || path.equals("freight_cart")
                || path.equals("livestock_car")
                || path.equals("mob_transport_car");
    }
}