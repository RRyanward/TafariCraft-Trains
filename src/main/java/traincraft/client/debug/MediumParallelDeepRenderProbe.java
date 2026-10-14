package traincraft.client.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DEV50b2q: passive client diagnostic of real baked track quads, including
 * all three station rows. No render-state changes, model rewriting or world edits.
 * Move between front/middle/rear tracks and wait 8 seconds in each position.
 */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MediumParallelDeepRenderProbe {
    private static int ticks;
    private static int reports;
    private static BlockPos prior;
    private static final int MAX_REPORTS = 6;
    private static final int MAX_BLOCKS = 170;
    private MediumParallelDeepRenderProbe() { }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % 160 != 0 || reports >= MAX_REPORTS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        BlockPos origin = mc.player.blockPosition();
        if (prior != null && Math.abs(prior.getX() - origin.getX()) + Math.abs(prior.getZ() - origin.getZ()) < 2) return;
        if (scan(mc, mc.level, origin)) {
            prior = origin;
            reports++;
        }
    }

    private static boolean track(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null || !Traincraft.MOD_ID.equals(id.getNamespace())) return false;
        String s = id.getPath();
        return s.startsWith("track_") || s.contains("rail_");
    }

    private static String id(BlockState state) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return key == null ? "unregistered" : key.toString();
    }

    private static List<BakedQuad> allQuads(BakedModel model, BlockState state, long seed, ModelData data, boolean forge) {
        List<BakedQuad> out = new ArrayList<>();
        if (forge) {
            out.addAll(model.getQuads(state, null, RandomSource.create(seed), data, null));
            for (Direction dir : Direction.values())
                out.addAll(model.getQuads(state, dir, RandomSource.create(seed), data, null));
        } else {
            out.addAll(model.getQuads(state, null, RandomSource.create(seed)));
            for (Direction dir : Direction.values())
                out.addAll(model.getQuads(state, dir, RandomSource.create(seed)));
        }
        return out;
    }

    private static boolean scan(Minecraft mc, Level level, BlockPos origin) {
        List<BlockPos> positions = new ArrayList<>();
        // Nearest first, includes adjacent station-side and front tracks.
        for (int dx = -15; dx <= 15; dx++) for (int dz = -9; dz <= 9; dz++)
            for (int dy = -2; dy <= 1; dy++) {
                BlockPos pos = origin.offset(dx, dy, dz);
                if (level.hasChunkAt(pos) && track(level.getBlockState(pos))) positions.add(pos);
            }
        if (positions.isEmpty()) return false;
        positions.sort(Comparator.comparingInt(p -> Math.abs(p.getX() - origin.getX()) + Math.abs(p.getZ() - origin.getZ()) + 3*Math.abs(p.getY() - origin.getY())));
        if (positions.size() > MAX_BLOCKS) positions = new ArrayList<>(positions.subList(0, MAX_BLOCKS));
        Traincraft.LOGGER.warn("[TC-DEV50B2Q-CLIENT] START sample={} pos={} trackCells={} region=+-15x9 READ_ONLY", reports + 1, origin, positions.size());
        Map<Integer, Row> rows = new LinkedHashMap<>();
        Map<String, QuadOwner> worldFaces = new HashMap<>();
        int duplicateFaces = 0;
        int sampledQuads = 0;
        int dataDifferent = 0;
        int outOfCell = 0;
        int tinted = 0;
        int failures = 0;
        int details = 0;
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            Row row = rows.computeIfAbsent(pos.getZ(), z -> new Row());
            row.blocks++;
            row.kinds.merge(id(state), 1, Integer::sum);
            try {
                BakedModel model = mc.getBlockRenderer().getBlockModel(state);
                List<BakedQuad> base = allQuads(model, state, 0L, ModelData.EMPTY, false);
                ModelData data = model.getModelData(level, pos, state, ModelData.EMPTY);
                List<BakedQuad> actual = allQuads(model, state, 0L, data, true);
                if (base.size() != actual.size()) {
                    dataDifferent++;
                    if (details++ < 24) Traincraft.LOGGER.warn("[TC-DEV50B2Q-CLIENT] MODEL_DATA_DIFF pos={} block={} baseQuads={} forgeQuads={} model={}", pos, id(state), base.size(), actual.size(), model.getClass().getName());
                }
                row.quads += actual.size();
                for (BakedQuad q : actual) {
                    sampledQuads++;
                    row.tint.merge(q.getTintIndex(), 1, Integer::sum);
                    if (q.getTintIndex() >= 0) tinted++;
                    if (q.getSprite() != null && q.getSprite().contents() != null)
                        row.sprite.merge(q.getSprite().contents().name().toString(), 1, Integer::sum);
                    int[] v = q.getVertices();
                    int stride = v.length / 4;
                    if (stride < 3) continue;
                    String[] corners = new String[4];
                    double minX=Double.POSITIVE_INFINITY, minY=Double.POSITIVE_INFINITY, minZ=Double.POSITIVE_INFINITY;
                    double maxX=Double.NEGATIVE_INFINITY, maxY=Double.NEGATIVE_INFINITY, maxZ=Double.NEGATIVE_INFINITY;
                    for (int i=0; i<4; i++) {
                        int k=i*stride;
                        double x=pos.getX()+Float.intBitsToFloat(v[k]);
                        double y=pos.getY()+Float.intBitsToFloat(v[k+1]);
                        double z=pos.getZ()+Float.intBitsToFloat(v[k+2]);
                        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) { corners = null; break; }
                        minX=Math.min(minX,x); maxX=Math.max(maxX,x);
                        minY=Math.min(minY,y); maxY=Math.max(maxY,y);
                        minZ=Math.min(minZ,z); maxZ=Math.max(maxZ,z);
                        corners[i]=Math.round(x*1000)+","+Math.round(y*1000)+","+Math.round(z*1000);
                    }
                    if (corners == null) continue;
                    if (minX < pos.getX()-0.003 || maxX > pos.getX()+1.003 || minZ < pos.getZ()-0.003 || maxZ > pos.getZ()+1.003) {
                        outOfCell++;
                        row.escaped++;
                    }
                    Arrays.sort(corners);
                    String key = String.join(";", corners);
                    QuadOwner priorOwner = worldFaces.putIfAbsent(key, new QuadOwner(pos, id(state)));
                    if (priorOwner != null && !priorOwner.pos.equals(pos)) {
                        duplicateFaces++;
                        row.duplicates++;
                        if (details++ < 24) Traincraft.LOGGER.warn("[TC-DEV50B2Q-CLIENT] CROSS_BLOCK_SAME_FACE first={} {} second={} {} sprite={} tint={}", priorOwner.pos, priorOwner.block, pos, id(state), q.getSprite() == null ? "null" : q.getSprite().contents().name(), q.getTintIndex());
                    }
                }
                // Forge uses model data/render layers; compare solid pass with aggregate without changing layers.
                if (row.blocks <= 2) {
                    try {
                        int solidCount = model.getQuads(state, null, RandomSource.create(0L), data, RenderType.solid()).size();
                        if (details++ < 24) Traincraft.LOGGER.warn("[TC-DEV50B2Q-CLIENT] RENDER_PASS pos={} model={} unculledSolid={} allQuads={} ambientOcclusion={} modelData={}", pos, model.getClass().getName(), solidCount, actual.size(), model.useAmbientOcclusion(), data);
                    } catch (RuntimeException renderError) {
                        Traincraft.LOGGER.warn("[TC-DEV50B2Q-CLIENT] RENDER_PASS_ERROR pos={} {}", pos, renderError.toString());
                    }
                }
            } catch (Exception err) {
                failures++;
                if (failures <= 10) Traincraft.LOGGER.warn("[TC-DEV50B2Q-CLIENT] SAMPLE_ERROR pos={} block={} cause={}", pos, id(state), err.toString());
            }
        }
        for (Map.Entry<Integer,Row> e : rows.entrySet()) {
            Row r=e.getValue();
            Traincraft.LOGGER.warn("[TC-DEV50B2Q-CLIENT] ROW z={} blocks={} quads={} outsideCellFaces={} identicalCrossBlockFaces={} types={} tintIndices={} topSprites={}", e.getKey(), r.blocks, r.quads, r.escaped, r.duplicates, r.kinds, r.tint, top(r.sprite, 8));
        }
        Traincraft.LOGGER.warn("[TC-DEV50B2Q-CLIENT] END sample={} blockCount={} faceCount={} crossBlockSameFace={} cellEscapes={} tintedFaces={} modelDataCountDiff={} errors={} -- NO WORLD OR RENDER CHANGES", reports+1, positions.size(), sampledQuads, duplicateFaces, outOfCell, tinted, dataDifferent, failures);
        return true;
    }

    private static String top(Map<String,Integer> counts, int max) {
        List<Map.Entry<String,Integer>> entries=new ArrayList<>(counts.entrySet());
        entries.sort((a,b)->Integer.compare(b.getValue(),a.getValue()));
        StringBuilder s=new StringBuilder();
        for (int i=0; i<Math.min(max,entries.size()); i++) {
            if (i>0) s.append('|');
            s.append(entries.get(i).getKey()).append(':').append(entries.get(i).getValue());
        }
        return s.toString();
    }
    private static final class Row {
        int blocks,quads,escaped,duplicates;
        final Map<String,Integer> kinds=new LinkedHashMap<>();
        final Map<Integer,Integer> tint=new LinkedHashMap<>();
        final Map<String,Integer> sprite=new HashMap<>();
    }
    private static final class QuadOwner {
        final BlockPos pos; final String block;
        QuadOwner(BlockPos p,String b){pos=p;block=b;}
    }
}
