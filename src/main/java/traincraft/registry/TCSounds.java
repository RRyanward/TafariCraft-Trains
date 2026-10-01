/* Traincraft 1.20.1 sound registry. */
package traincraft.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import traincraft.Traincraft;

/** Registers the original Traincraft sound assets for network-safe playback. */
public final class TCSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Traincraft.MOD_ID);

    public static final RegistryObject<SoundEvent> STEAM_IDLE = register("steam_idle");
    public static final RegistryObject<SoundEvent> STEAM_RUN = register("steam_run");
    public static final RegistryObject<SoundEvent> STEAM_HORN = register("steam_horn");

    public static final RegistryObject<SoundEvent> DIESEL_742_HORN = register("742_horn");
    public static final RegistryObject<SoundEvent> DIESEL_742_MOTOR = register("742_motor");
    public static final RegistryObject<SoundEvent> DIESEL_742_SLOW = register("742_motor_slow");
    public static final RegistryObject<SoundEvent> CHME3_HORN = register("chme3_horn");
    public static final RegistryObject<SoundEvent> CHME3_IDLE = register("chme3_idle");
    public static final RegistryObject<SoundEvent> CHME3_RUN = register("chme3_run");
    public static final RegistryObject<SoundEvent> GP_HORN = register("gp_horn");
    public static final RegistryObject<SoundEvent> SD70_HORN = register("sd70_horn");
    public static final RegistryObject<SoundEvent> SHUNTER_HORN = register("shunter_horn");
    public static final RegistryObject<SoundEvent> BRITISH_TWO_TONE = register("british_two_tone");
    public static final RegistryObject<SoundEvent> V60_HORN = register("v60_horn");
    public static final RegistryObject<SoundEvent> VL10_HORN = register("vl10_horn");
    public static final RegistryObject<SoundEvent> VL10_IDLE = register("vl10_idle");
    public static final RegistryObject<SoundEvent> VL10_RUN = register("vl10_run");
    public static final RegistryObject<SoundEvent> EU07_HORN = register("eu07_horn");
    public static final RegistryObject<SoundEvent> TRAM_HORN = register("tram_horn");
    public static final RegistryObject<SoundEvent> HIGH_SPEED_HORN = register("high_speed_horn");
    public static final RegistryObject<SoundEvent> SUBWAY_HORN = register("subway_horn");
    public static final RegistryObject<SoundEvent> CROSSING_BELL = register("crossing_bell");

    public static final RegistryObject<SoundEvent> MODULAR_CROSSING_BELL = register("modular_crossing_bell");


    // TC_BATCH7_R4A_ACTUAL_AUDIO_EVENTS
    public static final RegistryObject<SoundEvent> B7_HORN_LESLIE_S3L = register("b7_horn_leslie_s3l");
    public static final RegistryObject<SoundEvent> B7_HORN_NATHAN_P5 = register("b7_horn_nathan_p5");
    public static final RegistryObject<SoundEvent> B7_HORN_AIRCHIME_K5LA = register("b7_horn_airchime_k5la");
    public static final RegistryObject<SoundEvent> B7_HORN_NATHAN_M5 = register("b7_horn_nathan_m5");
    public static final RegistryObject<SoundEvent> B7_HORN_WABCO_E2 = register("b7_horn_wabco_e2");
    public static final RegistryObject<SoundEvent> B7_HORN_WABCO_E2_B1 = register("b7_horn_wabco_e2_b1");
    public static final RegistryObject<SoundEvent> B7_HORN_LESLIE_A200_156 = register("b7_horn_leslie_a200_156");
    public static final RegistryObject<SoundEvent> B7_HORN_PRIME_PM920 = register("b7_horn_prime_pm920");
    public static final RegistryObject<SoundEvent> B7_HORN_DB_KLEINLOK = register("b7_horn_db_kleinlok");
    public static final RegistryObject<SoundEvent> B7_HORN_SOVIET_DIESEL = register("b7_horn_soviet_diesel");

    public static final RegistryObject<SoundEvent> B7_WHISTLE_PRUSSIAN_P8 = register("b7_whistle_prussian_p8");
    public static final RegistryObject<SoundEvent> B7_WHISTLE_RUSSIAN_STEAM = register("b7_whistle_russian_steam");
    public static final RegistryObject<SoundEvent> B7_WHISTLE_AMERICAN_STEAM = register("b7_whistle_american_steam");

    public static final RegistryObject<SoundEvent> B7_STEAM_RUN_ANNA6 = register("b7_steam_run_anna6");
    public static final RegistryObject<SoundEvent> B7_STEAM_RELEASE = register("b7_steam_release");
    public static final RegistryObject<SoundEvent> B7_STEAM_AIR_PUMP = register("b7_steam_air_pump");

    public static final RegistryObject<SoundEvent> B7_DIESEL_DB_KLEINLOK_MOTOR = register("b7_diesel_db_kleinlok_motor");
    public static final RegistryObject<SoundEvent> B7_DIESEL_INDUSTRIAL_IDLE = register("b7_diesel_industrial_idle");
    public static final RegistryObject<SoundEvent> B7_DIESEL_INDUSTRIAL_RUN = register("b7_diesel_industrial_run");

    public static final RegistryObject<SoundEvent> B7_TRAM_CLASSIC = register("b7_tram_classic");
    public static final RegistryObject<SoundEvent> B7_TRAM_102N = register("b7_tram_102n");
    public static final RegistryObject<SoundEvent> B7_ELECTRIC_ACCELERATION = register("b7_electric_acceleration");
    public static final RegistryObject<SoundEvent> B7_ELECTRIC_DECELERATION = register("b7_electric_deceleration");
    public static final RegistryObject<SoundEvent> B7_ELECTRIC_RUN = register("b7_electric_run");
    public static final RegistryObject<SoundEvent> B7_RAIL_WHEELS = register("b7_rail_wheels");

    // Existing original Traincraft sounds that were already packaged/json-defined
    // but had not been exposed through TCSounds.
    public static final RegistryObject<SoundEvent> ADLER_WHISTLE = register("adler_whistle");
    public static final RegistryObject<SoundEvent> SHAY_HORN = register("shay_horn");
    private TCSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        ResourceLocation id = new ResourceLocation(Traincraft.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus modBus) {
        SOUND_EVENTS.register(modBus);
    }
}
