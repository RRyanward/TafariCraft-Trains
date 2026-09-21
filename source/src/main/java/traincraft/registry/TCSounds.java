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
