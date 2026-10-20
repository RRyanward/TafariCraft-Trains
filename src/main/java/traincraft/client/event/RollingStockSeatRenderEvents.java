package traincraft.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.config.RollingStockCalibrationConfig;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.entity.train.tender.SteamTender;
import traincraft.event.RollingStockSeatCalibrationEvents;

/**
 * r1f12g: camera-safe client render-position bridge.
 *
 * r1f12f translated only the rendered player model. That made third-person
 * look correct, but first-person still used Minecraft's real player/camera
 * position. This bridge temporarily moves ONLY the client player for the
 * render tick, before the camera is computed, then restores the exact original
 * position at the end of the same render tick.
 *
 * Server movement, coupling, collision, and frozen rolling-stock entity code
 * are untouched.
 */
@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RollingStockSeatRenderEvents {

    private static Player shiftedPlayer;

    private static double oldX;
    private static double oldY;
    private static double oldZ;

    private static double oldXo;
    private static double oldYo;
    private static double oldZo;

    private static double oldXOld;
    private static double oldYOld;
    private static double oldZOld;

    private RollingStockSeatRenderEvents() {}

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            applyRenderSeat(event.renderTickTime);
        } else {
            restoreRenderSeat();
        }
    }

    private static void applyRenderSeat(float partialTick) {
        // Defensive restore in case a prior render was interrupted.
        restoreRenderSeat();

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;

        Entity vehicle = player.getVehicle();
        if (vehicle == null || !isRollingStock(vehicle)) return;

        // Powered locomotives keep the accepted synchronized physical seat path.
        // Their real rider position already drives both first- and third-person.
        // DEV38 r1c2:
        // Normal powered riding keeps the accepted physical Seat 1 path.
        // During the temporary DEV38 seat-perfect-pass preview, however, use
        // this already-accepted camera/render bridge so live config edits are
        // immediately visible just like Steam/non-powered stock.
        // TC_DEV38_R1F2C_POWERED_NORMAL_CAMERA
        // The server-side powered physical seat remains frozen. At render time,
        // use the same canonical final seat calibration as Steam/non-powered
        // stock so first-person camera and third-person rider agree with the
        // accepted BODY + SEATS TOML during normal gameplay too.

        // DEV53E3E: Stop the double-offset race. For powered stock with an
        // authoritative physical seat, vanilla camera and rider follow the
        // real mounted passenger instead of a second render-only Y offset.
        // Every other powered/steam/passenger render path stays unchanged.
        // DEV53E3G: Keep physical rider coordinates in normal gameplay.
        // During an explicit /tc visual seat 1|2|3 preview on a wooden tram,
        // temporarily show the SELECTED seat instead of skipping visual edits.
        // GP7/CHME3 (and ordinary pinned powered seats) remain protected.
        if (vehicle instanceof LegacyPoweredLocomotive powered
                && powered.tcDev53E3EHasAuthoritativePhysicalSeat()
                && !(powered.isWoodenPassengerTram()
                    && RollingStockSeatCalibrationEvents.hasPreviewSeat(player.getUUID()))) {
            return;
        }
        String id = registryId(vehicle);

        int passengerIndex = vehicle.getPassengers().indexOf(player);
        int normalSlot = Math.max(1, Math.min(3, passengerIndex + 1));

        int slot = RollingStockSeatCalibrationEvents.previewSeatFor(
                player.getUUID(),
                normalSlot);

        if (!RollingStockCalibrationConfig.hasSeat(id, slot)) return;

        double longitudinal = RollingStockCalibrationConfig.seatLong(
                id, slot, 0.0D);
        double seatY = RollingStockCalibrationConfig.seatY(
                id, slot, 0.0D);
        double side = RollingStockCalibrationConfig.seatSide(
                id, slot, 0.0D);
        double modelY = RollingStockCalibrationConfig.modelY(
                id, 0.0D);

        double yaw = Math.toRadians(facingYaw(vehicle, partialTick));
        double frontX = Math.sin(yaw);
        double frontZ = -Math.cos(yaw);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);

        /*
         * TC_RENDER_SEAT_INTERPOLATION_R1F16D
         *
         * Minecraft renders the vehicle at its partial-tick interpolated position.
         * r1f12g used vehicle.getX/Y/Z() (the newest full-tick position) for the
         * temporary camera seat. While riding, that made the camera and rendered
         * locomotive disagree by up to one movement tick and visually "pop" back
         * and forth even though the entity movement itself was smooth.
         *
         * Use xOld/yOld/zOld -> current with the SAME render partial tick as the
         * entity renderer, then place the calibrated seat from that visual base.
         */
        double baseX = Mth.lerp((double) partialTick, vehicle.xOld, vehicle.getX());
        double baseY = Mth.lerp((double) partialTick, vehicle.yOld, vehicle.getY());
        double baseZ = Mth.lerp((double) partialTick, vehicle.zOld, vehicle.getZ());

        double desiredX =
                baseX
                        + frontX * longitudinal
                        + rightX * side;
        double desiredY =
                baseY
                        + modelY
                        + seatY;
        double desiredZ =
                baseZ
                        + frontZ * longitudinal
                        + rightZ * side;

        shiftedPlayer = player;

        oldX = player.getX();
        oldY = player.getY();
        oldZ = player.getZ();

        oldXo = player.xo;
        oldYo = player.yo;
        oldZo = player.zo;

        oldXOld = player.xOld;
        oldYOld = player.yOld;
        oldZOld = player.zOld;

        // Put current AND interpolation positions on the calibrated seat so the
        // camera and third-person player use the exact same coordinates.
        player.setPos(desiredX, desiredY, desiredZ);

        player.xo = desiredX;
        player.yo = desiredY;
        player.zo = desiredZ;

        player.xOld = desiredX;
        player.yOld = desiredY;
        player.zOld = desiredZ;
    }

    private static void restoreRenderSeat() {
        if (shiftedPlayer == null) return;

        Player player = shiftedPlayer;
        shiftedPlayer = null;

        player.setPos(oldX, oldY, oldZ);

        player.xo = oldXo;
        player.yo = oldYo;
        player.zo = oldZo;

        player.xOld = oldXOld;
        player.yOld = oldYOld;
        player.zOld = oldZOld;
    }

    private static boolean isRollingStock(Entity entity) {
        if (!(entity instanceof AbstractMinecart)) return false;

        ResourceLocation key =
                ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());

        return key != null
                && Traincraft.MOD_ID.equals(key.getNamespace())
                && !"tracks_builder".equals(key.getPath());
    }

    private static String registryId(Entity entity) {
        ResourceLocation key =
                ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());

        return key == null ? "unknown" : key.getPath();
    }

    private static double facingYaw(Entity vehicle, float partialTick) {
        if (vehicle instanceof SmallSteamLocomotive locomotive) {
            return locomotive.getRenderTrainFacingYaw(partialTick);
        }
        if (vehicle instanceof SteamTender tender) {
            return tender.getRenderTrainFacingYaw(partialTick);
        }
        return Mth.rotLerp(partialTick, vehicle.yRotO, vehicle.getYRot());
    }
}