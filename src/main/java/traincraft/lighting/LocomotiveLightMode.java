package traincraft.lighting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.network.TCNetwork;
import traincraft.network.packet.LocomotiveLightSyncPacket;

/** G12R4R3: per-locomotive AUTO / ON / OFF, engine-gated, persistent and synced.
 * Never touches accepted lamp geometry, ray casting, rods or wheel code.
 * DEFAULT AUTO checks local ambient block + sky brightness, not clock alone.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LocomotiveLightMode {
    public static final int AUTO = 0, ON = 1, OFF = 2;
    private static final String NBT = "TafariCraftLightMode";
    private static final Map<UUID, Integer> CLIENT_MODE = new HashMap<>();
    private record Ambient(long lastTick, boolean dark) {}
    private static final Map<UUID, Ambient> CLIENT_AMBIENT = new HashMap<>();
    private static final Map<UUID, Ambient> SERVER_AMBIENT = new HashMap<>();
    private LocomotiveLightMode() {}

    public static boolean locomotive(Entity e) {
        return e instanceof SmallSteamLocomotive || e instanceof LegacyPoweredLocomotive;
    }
    public static int normalize(int mode) { return Math.max(AUTO, Math.min(OFF, mode)); }
    public static int mode(Entity e) {
        if (e == null || !locomotive(e)) return AUTO;
        return e.level().isClientSide
                ? CLIENT_MODE.getOrDefault(e.getUUID(), AUTO)
                : normalize(e.getPersistentData().getInt(NBT));
    }
    public static String label(Entity e) {
        return switch (mode(e)) { case ON -> "ON"; case OFF -> "OFF"; default -> "AUTO"; };
    }
    /** Client mirror is data only; client cannot authorize an engine or world edit. */
    public static void syncClient(UUID id, int mode) { CLIENT_MODE.put(id, normalize(mode)); }

    public static void change(ServerPlayer requester, Entity e, int requested) {
        if (requester == null || e == null || !locomotive(e)) return;
        // Only its current operator can change this locomotive's settings.
        if (requester.getVehicle() != e || e.getFirstPassenger() != requester) return;
        int value=normalize(requested);
        e.getPersistentData().putInt(NBT,value); // persisted with this entity on world save
        TCNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e),
                new LocomotiveLightSyncPacket(e.getUUID(),value));
    }
    @SubscribeEvent
    public static void onTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer watcher)) return;
        Entity e=event.getTarget();
        if (!locomotive(e)) return;
        TCNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> watcher),
                new LocomotiveLightSyncPacket(e.getUUID(), mode(e)));
    }

    /** Existing engine/fuel gates take precedence over all modes. */
    public static boolean engineRunning(Entity e) {
        if (e instanceof LegacyPoweredLocomotive p) {
            if (p.isBuilder()) return false;
            if (p.isDiesel()) return p.getPowerAmount() > 0;
            if (p.isElectric()) return p.isOnPoweredEnergyTrack() || p.hasPortableBackupRedstone();
            return false;
        }
        if (e instanceof SmallSteamLocomotive s)
            return s.getBurnTime() > 0 || s.getSteamAmount() > 0;
        return false;
    }
    /**
     * R6: sample day/night from the dimension clock on BOTH logical sides.
     * ClientLevel's getSkyDarken() can remain zero after /time set night
     * while the integrated server reports 11. Using that local client value
     * caused an AUTO-on server / AUTO-off renderer disagreement.
     * The sky-light sample still detects caves and tunnels independently.
     * BLOCK light (including locomotive ground lamps) is intentionally not
     * consulted, so lamps cannot extinguish their own AUTO decision.
     */
    private static int clockSkyDarken(Level level) {
        if (!level.dimensionType().hasSkyLight()) return 0;
        long time=Math.floorMod(level.getDayTime(), 24000L);
        if (time < 12000L) return 0;
        if (time < 13000L) return (int)((time-12000L)*11L/1000L);
        if (time < 23000L) return 11;
        return (int)((24000L-time)*11L/1000L);
    }

    /**
     * AUTO uses daylight-adjusted SKY light, not MAX(sky, block).
     * Our own temporary ground lamps contribute BLOCK light. Including them
     * in AUTO sensing causes self-extinguishing/flicker and suppresses night
     * lighting; SKY stays independent of those lamps.
     * In caves/tunnels SKY=0 even at noon. A 12-tick sample cache and
     * separate enter/exit thresholds avoid transitions flashing at twilight.
     */
    private static boolean dark(Entity e) {
        Level level=e.level();
        UUID uuid=e.getUUID();
        Map<UUID,Ambient> cache=level.isClientSide ? CLIENT_AMBIENT : SERVER_AMBIENT;
        Ambient previous=cache.get(uuid);
        long tick=level.getGameTime();
        if (previous!=null && tick>=previous.lastTick && tick-previous.lastTick<12L)
            return previous.dark;
        BlockPos sample=BlockPos.containing(e.getX(),e.getY()+2.6D,e.getZ());
        int rawSky=level.getBrightness(LightLayer.SKY,sample);
        int effectiveSky=Math.max(0,rawSky-clockSkyDarken(level));
        boolean low=previous==null ? effectiveSky <= 7
                : previous.dark ? effectiveSky <= 10 : effectiveSky <= 6;
        cache.put(uuid,new Ambient(tick,low));
        return low;
    }
    public static float brightness(Entity e) {
        if (e==null || !e.isAlive() || !engineRunning(e)) return 0F;
        return switch (mode(e)) { case ON -> 1F; case OFF -> 0F; default -> dark(e) ? 1F : 0F; };
    }
    public static boolean enabled(Entity e) { return brightness(e)>0.005F; }
}
