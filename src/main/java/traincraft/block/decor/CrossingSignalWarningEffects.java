package traincraft.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * TC_R2C_CROSSING_WARNING_EFFECTS
 *
 * Visual warning layer kept separate from the block model:
 * - two mast heads alternate red
 * - gate-arm marker lamps remain solid red while ACTIVE
 * - bell repeats during lowering/down/raising
 *
 * This avoids adding more multipart/model-state combinations.
 */
public final class CrossingSignalWarningEffects {
    private static final DustParticleOptions RED =
            new DustParticleOptions(new Vector3f(1.0F, 0.03F, 0.03F), 1.15F);

    private CrossingSignalWarningEffects() {
    }

    public static void emit(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.hasProperty(RailroadCrossingSignalBlock.ACTIVE)
                || !state.getValue(RailroadCrossingSignalBlock.ACTIVE)) {
            return;
        }

        Direction facing = state.getValue(RailroadCrossingSignalBlock.FACING);
        Direction right = facing.getClockWise();

        // Main warning heads. Alternate about twice per second.
        boolean leftOn = ((level.getGameTime() / 6L) & 1L) == 0L;
        double centerX = pos.getX() + 0.5D;
        double centerY = pos.getY() + 1.72D;
        double centerZ = pos.getZ() + 0.5D;

        double lateral = 0.22D;
        double forward = 0.16D;

        double headX = centerX
                + facing.getStepX() * forward
                + right.getStepX() * (leftOn ? -lateral : lateral);
        double headZ = centerZ
                + facing.getStepZ() * forward
                + right.getStepZ() * (leftOn ? -lateral : lateral);

        level.sendParticles(
                RED,
                headX, centerY, headZ,
                5,
                0.025D, 0.025D, 0.025D,
                0.0D);

        // Gate-arm lights stay SOLID while warning is active.
        int arm = state.getValue(RailroadCrossingSignalBlock.ARM);

        // ARM: 3=up, 2=45, 1=22.5, 0=down.
        double angleDeg = switch (arm) {
            case 3 -> 88.0D;
            case 2 -> 45.0D;
            case 1 -> 22.5D;
            default -> 0.0D;
        };
        double angle = Math.toRadians(angleDeg);

        // Existing model extends the arm to the signal's right.
        double horizontalScale = Math.cos(angle);
        double verticalScale = Math.sin(angle);

        for (int i = 1; i <= 5; i++) {
            double d = 0.55D + i * 0.48D;
            double x = centerX + right.getStepX() * d * horizontalScale;
            double y = pos.getY() + 1.28D + d * verticalScale;
            double z = centerZ + right.getStepZ() * d * horizontalScale;

            level.sendParticles(
                    RED,
                    x, y, z,
                    2,
                    0.018D, 0.018D, 0.018D,
                    0.0D);
        }

        // Strong, unmistakable crossing bell. The block ticks every 4 ticks;
        // strike every 12 game ticks (~1.7 strikes/sec).
        if ((level.getGameTime() % 12L) < 4L) {
            level.playSound(
                    null,
                    pos,
                    SoundEvents.NOTE_BLOCK_BELL.value(),
                    SoundSource.BLOCKS,
                    1.35F,
                    0.82F);
        }
    }
}
