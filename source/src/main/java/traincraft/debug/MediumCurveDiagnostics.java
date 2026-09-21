package traincraft.debug;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.block.track.LegacyMediumCurveRailBlock;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Opt-in server-side observations only: never writes blocks or entity motion. */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MediumCurveDiagnostics {
    private MediumCurveDiagnostics() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tc_curve_debug")
                .requires(s -> s.hasPermission(2))
                .executes(c -> inspect(c.getSource())));
    }

    private static int inspect(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos center = BlockPos.containing(source.getPosition());
        Set<BlockPos> roots = new HashSet<>();
        List<String> report = new ArrayList<>();
        report.add("TRAINCRAFT r3e DIAGNOSTICS ONLY; dimension=" + level.dimension().location()
                + " scanCenter=" + center + " radius=12 horizontal,4 vertical");
        report.add("ENDPOINTS below are rail-shape sampling geometry, NOT measured locomotive motion.");
        report.add("OBJ bounds are raw asset coordinates translated by model anchor; not client baked-model measurements.");
        report.add("Particle markers are temporary END_ROD samples of guide paths; no blocks are changed.");
        for (BlockPos scan : BlockPos.betweenClosed(center.offset(-12,-4,-12), center.offset(12,4,12))) {
            if (!level.hasChunkAt(scan)) continue;
            BlockState state = level.getBlockState(scan);
            if (!(state.getBlock() instanceof LegacyMediumCurveRailBlock)) continue;
            Direction facing = state.getValue(LegacyMediumCurveRailBlock.ASSEMBLY_FACING);
            BlockPos root = scan.subtract(LegacyMediumCurveRailBlock.offsetForPart(BlockPos.ZERO, facing,
                    state.getValue(LegacyMediumCurveRailBlock.PART)));
            if (!roots.add(root)) continue;
            BlockPos anchor = LegacyMediumCurveRailBlock.offsetForPart(root, facing, 0);
            report.add("ASSEMBLY root=" + root + " facing=" + facing + " modelAnchor(part0)=" + anchor);
            bounds(report, facing, anchor);
            for (int part=0; part<LegacyMediumCurveRailBlock.PART_COUNT; part++) {
                BlockPos pos = LegacyMediumCurveRailBlock.offsetForPart(root, facing, part);
                if (!level.hasChunkAt(pos)) { report.add("UNLOADED part=" + part + " pos=" + pos); continue; }
                BlockState actual = level.getBlockState(pos);
                RailShape expected = LegacyMediumCurveRailBlock.shapeForPart(facing, part);
                report.add("PART " + part + " pos=" + pos + " expectedShape=" + expected + " actual=" + actual);
                if (!(actual.getBlock() instanceof BaseRailBlock rail)) continue;
                RailShape sampled = rail.getRailDirection(actual, level, pos, null);
                boolean owned = actual.getBlock() instanceof LegacyMediumCurveRailBlock
                        && actual.getValue(LegacyMediumCurveRailBlock.PART) == part
                        && actual.getValue(LegacyMediumCurveRailBlock.ASSEMBLY_FACING) == facing;
                Vec3[] ends = endpoints(pos, sampled);
                report.add("  owned=" + owned + " sampledShape=" + sampled + " shapeMatches=" + (sampled == expected)
                        + " endpoints=" + Arrays.toString(ends));
                if (ends == null) continue;
                for (int step=0; step<=12; step++) {
                    Vec3 p = ends[0].lerp(ends[1], step/12.0);
                    level.sendParticles(ParticleTypes.END_ROD,p.x,p.y+0.15,p.z,1,0,0,0,0);
                }
                for (Vec3 endpoint : ends) {
                    double nearest = Double.POSITIVE_INFINITY;
                    String match = "none";
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        BlockPos neighbor = pos.relative(d);
                        if (!level.hasChunkAt(neighbor)) continue;
                        BlockState ns = level.getBlockState(neighbor);
                        if (!(ns.getBlock() instanceof BaseRailBlock nr)) continue;
                        RailShape shape = nr.getRailDirection(ns,level,neighbor,null);
                        report.add("  NEIGHBOR pos=" + neighbor + " sampled=" + shape + " state=" + ns);
                        Vec3[] ne = endpoints(neighbor,shape);
                        if (ne == null) continue;
                        for (Vec3 v : ne) {
                            double gap=endpoint.distanceTo(v);
                            if (gap<nearest) { nearest=gap; match=neighbor + " " + shape; }
                        }
                    }
                    report.add("  endpoint=" + endpoint + " nearestNeighborEndpointGap=" + nearest
                            + " neighbor=" + match + " coincident=" + (nearest<0.0001));
                }
            }
        }
        report.add("ASSEMBLY_COUNT=" + roots.size());
        if (roots.isEmpty()) source.sendFailure(Component.literal("No medium curves within 12 blocks. Move closer and repeat."));
        try {
            Path dir=Path.of("logs","traincraft-track-debug");
            Files.createDirectories(dir);
            String stamp=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
            Path file=dir.resolve("curve-"+stamp+"-"+UUID.randomUUID().toString().substring(0,8)+".txt");
            Files.write(file,report,StandardCharsets.UTF_8);
            source.sendSuccess(() -> Component.literal("Curve debug: " + roots.size() + " assemblies. Report: " + file.toAbsolutePath()),false);
            for (String line:report) LogUtils.getLogger().info("[TC-CURVE-DEBUG] {}",line);
            return roots.size();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Could not write curve report: " + e.getMessage()));
            return 0;
        }
    }

    private static Vec3[] endpoints(BlockPos p, RailShape shape) {
        Direction[] dirs = switch(shape) {
            case NORTH_SOUTH -> new Direction[]{Direction.NORTH,Direction.SOUTH};
            case EAST_WEST -> new Direction[]{Direction.WEST,Direction.EAST};
            case SOUTH_EAST -> new Direction[]{Direction.SOUTH,Direction.EAST};
            case SOUTH_WEST -> new Direction[]{Direction.SOUTH,Direction.WEST};
            case NORTH_EAST -> new Direction[]{Direction.NORTH,Direction.EAST};
            case NORTH_WEST -> new Direction[]{Direction.NORTH,Direction.WEST};
            default -> null;
        };
        if(dirs==null) return null; // Slope endpoints deliberately not inferred by this flat-curve diagnostic.
        Vec3 c = new Vec3(p.getX()+0.5,p.getY()+0.0625,p.getZ()+0.5);
        return new Vec3[]{c.add(dirs[0].getStepX()*0.5,0,dirs[0].getStepZ()*0.5),
                c.add(dirs[1].getStepX()*0.5,0,dirs[1].getStepZ()*0.5)};
    }

    private static void bounds(List<String> report, Direction facing, BlockPos anchor) {
        String resource="/assets/traincraft/models/block/legacy_track_curve_medium_"+facing.getName()+".obj";
        try (var stream=MediumCurveDiagnostics.class.getResourceAsStream(resource)) {
            if(stream==null) { report.add("OBJ missing: "+resource); return; }
            double[] min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY};
            double[] max={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
            int count=0;
            try(var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
                String line;
                while((line=reader.readLine())!=null) {
                    if(!line.startsWith("v "))continue;
                    String[] fields=line.trim().split("\\s+"); count++;
                    for(int i=0;i<3;i++) { double v=Double.parseDouble(fields[i+1]); min[i]=Math.min(min[i],v);max[i]=Math.max(max[i],v); }
                }
            }
            report.add("OBJ="+resource+" vertices="+count+" rawMin="+Arrays.toString(min)+" rawMax="+Arrays.toString(max));
            report.add("OBJ translated bounds min="+new Vec3(anchor.getX()+min[0],anchor.getY()+min[1],anchor.getZ()+min[2])
                    +" max="+new Vec3(anchor.getX()+max[0],anchor.getY()+max[1],anchor.getZ()+max[2]));
            report.add("Bounds include sleepers; bounds are NOT rail endpoints. Use guide particles and screenshots for visual comparison.");
        } catch(Exception e) { report.add("OBJ read error: "+e); }
    }
}
