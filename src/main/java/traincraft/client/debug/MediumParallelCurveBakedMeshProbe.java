package traincraft.client.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.block.track.AbstractLegacyBatchRailBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

/**
 * DEV50b2o client-only observation of the ACTUAL runtime baked quads.
 * Runs at most one report per nearby assembly (maximum four reports),
 * changes no Minecraft states, baked models, meshes, or render settings.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MediumParallelCurveBakedMeshProbe {
    private static int ticks;
    private static int reports;
    private static final Set<String> reported = new HashSet<>();
    private MediumParallelCurveBakedMeshProbe() { }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || reports >= 4) return;
        if (++ticks % 160 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || mc.player == null) return;
        BlockPos center = mc.player.blockPosition();
        for (int dx = -16; dx <= 16; dx++) {
            for (int dz = -16; dz <= 16; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof AbstractLegacyBatchRailBlock rail)) continue;
                    ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
                    if (id == null || !Traincraft.MOD_ID.equals(id.getNamespace())) continue;
                    String name = id.getPath();
                    if (!"track_parallel_curve_medium_left_segment".equals(name)
                            && !"track_parallel_curve_medium_right_segment".equals(name)) continue;
                    BlockPos root = rail.getAssemblyRoot(pos, state);
                    Direction facing = rail.getAssemblyFacing(state);
                    String key = id + ":" + root + ":" + facing;
                    if (!reported.add(key)) continue;
                    report(mc, level, rail, root, facing, name);
                    reports++;
                    if (reports >= 4) return;
                }
            }
        }
    }

    private static void report(Minecraft mc, Level level, AbstractLegacyBatchRailBlock rail,
                               BlockPos root, Direction facing, String name) {
        Traincraft.LOGGER.warn("[TC-DEV50B2O-CLIENT] START model={} root={} facing={} parts={} (read-only baked quad bounds)",
                name, root, facing, rail.getSpec().partCount());
        for (int p = 0; p < rail.getSpec().partCount(); p++) {
            BlockPos pos = rail.getSpec().offsetForPart(root, facing, p);
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() != rail) {
                Traincraft.LOGGER.warn("[TC-DEV50B2O-CLIENT] PART={} pos={} NO_MATCH actual={}", p, pos, state);
                continue;
            }
            try {
                BakedModel model = mc.getBlockRenderer().getBlockModel(state);
                List<BakedQuad> all = new ArrayList<>();
                all.addAll(model.getQuads(state, null, RandomSource.create(0L)));
                int unculled = all.size();
                int directional = 0;
                for (Direction face : Direction.values()) {
                    List<BakedQuad> quads = model.getQuads(state, face, RandomSource.create(0L));
                    directional += quads.size();
                    all.addAll(quads);
                }
                double[] bounds = { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY };
                int validVertices = 0;
                for (BakedQuad q : all) {
                    int[] data = q.getVertices();
                    int stride = data.length / 4;
                    if (stride < 3) continue;
                    for (int v = 0; v < 4; v++) {
                        int i = v * stride;
                        double x = Float.intBitsToFloat(data[i]);
                        double y = Float.intBitsToFloat(data[i + 1]);
                        double z = Float.intBitsToFloat(data[i + 2]);
                        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) continue;
                        bounds[0] = Math.min(bounds[0], x); bounds[3] = Math.max(bounds[3], x);
                        bounds[1] = Math.min(bounds[1], y); bounds[4] = Math.max(bounds[4], y);
                        bounds[2] = Math.min(bounds[2], z); bounds[5] = Math.max(bounds[5], z);
                        validVertices++;
                    }
                }
                String box = validVertices == 0 ? "EMPTY" : String.format(Locale.ROOT,
                        "(%.3f,%.3f,%.3f)..(%.3f,%.3f,%.3f)",
                        bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
                Traincraft.LOGGER.warn("[TC-DEV50B2O-CLIENT] PART={} pos={} modelClass={} quadsUnculled={} quadsDirectional={} vertexCount={} localBounds={}",
                        p, pos, model.getClass().getSimpleName(), unculled, directional, validVertices, box);
            } catch (RuntimeException ex) {
                Traincraft.LOGGER.warn("[TC-DEV50B2O-CLIENT] PART={} pos={} QUAD_SAMPLE_ERROR {}", p, pos, ex.toString());
            }
        }
        Traincraft.LOGGER.warn("[TC-DEV50B2O-CLIENT] END model={} root={} -- no visual changes", name, root);
    }
}
