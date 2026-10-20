/* DEV55S1 -- audio only. No fluid or passenger state changes. */
package traincraft.audio;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import traincraft.entity.train.freight.TctTanker;
import traincraft.registry.TCSounds;

/** Server-authoritative, transfer-success-only operational sound cues. */
public final class Dev55OperationalSounds {
    private static final String LAST_PUMP_TICK = "TcDev55LastPumpSoundTick";
    private static final long MIN_PUMP_TICKS = 20L;

    private Dev55OperationalSounds() { }

    public static void pump(Level level, TctTanker tanker, boolean loading) {
        if (level.isClientSide || tanker == null || !tanker.isAlive()) return;
        // The two dock/interaction paths call only after a successful mB transfer.
        // Shared per-car cooldown prevents overlapping pulses if both paths fire.
        CompoundTag state = tanker.getPersistentData();
        long now = level.getGameTime();
        if (state.contains(LAST_PUMP_TICK)) {
            long last = state.getLong(LAST_PUMP_TICK);
            if (now >= last && now - last < MIN_PUMP_TICKS) return;
        }
        state.putLong(LAST_PUMP_TICK, now);
        level.playSound(null, tanker.blockPosition(),
                loading ? TCSounds.DEV55_TANKER_PUMP_LOAD.get()
                        : TCSounds.DEV55_TANKER_PUMP_UNLOAD.get(),
                SoundSource.BLOCKS, 0.63F, 1.0F);
    }
}
