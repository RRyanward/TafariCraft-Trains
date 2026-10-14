/*
 * TafariCraft - Trains DEV50c9R
 * MANUAL, READ-ONLY runtime diagnostic. No movement, rail edits, chunk tickets,
 * coupling repairs, POI ownership changes, villager trade generation or saves.
 * Physical entity AABBs and static block sweeps DO NOT certify rendered meshes,
 * wheelbase geometry, live turnout routing or complete coach-swept clearance.
 */
package traincraft.worldgen;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import traincraft.Traincraft;
import traincraft.entity.train.CouplingManager;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.entity.train.tender.SteamTender;
import traincraft.villager.RailwayVillagerEgg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** One-shot loaded-world evidence collection, NOT authority to start train movement. */
public final class RailwayServiceRuntimeDiagnostic {
    private static final Logger LOG = Traincraft.LOGGER;
    private static final String PREFIX = "[TC-DEV50C9R] ";
    private static final double HALF_BODY_WIDTH = 1.50D;
    private static final double SIDE_MARGIN = 0.35D;
    private static final double VERTICAL_HEIGHT = 4.25D;
    private static final int MAX_AHEAD = 24;
    private static final int MAX_SAMPLES = 8;

    private RailwayServiceRuntimeDiagnostic() {}

    private static final class Report {
        final CommandSourceStack src;
        int pass, fail, unverified;
        Report(CommandSourceStack src) { this.src = src; }
        void line(String value) { LOG.info(PREFIX + value); }
        void check(String label, String status, String detail) {
            if ("PASS".equals(status)) pass++;
            else if ("FAIL".equals(status)) fail++;
            else unverified++;
            line("CHECK " + label + "=" + status + " " + detail);
        }
        void finish(BlockPos station) {
            String summary = "SUMMARY PASS=" + pass + " FAIL=" + fail
                    + " UNVERIFIED=" + unverified + " ROUTE_CERTIFICATION=NOT_CERTIFIED"
                    + " WORLD_CHANGES=NONE MOVEMENT=NONE";
            line(summary);
            line("END station=" + station.toShortString());
            src.sendSuccess(() -> Component.literal("[TC route diagnostic] " + summary), false);
            src.sendSuccess(() -> Component.literal("[TC route diagnostic] Evidence in latest.log."
                    + " Close Minecraft; run MAKE-DEV50C9R-REPORT.cmd. Do not depart."), false);
        }
    }

    private static String fmt(double n) { return String.format(Locale.ROOT, "%.3f", n); }
    private static String id(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
    }
    private static String blockId(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
    }
    private static String shape(BlockState s) {
        return s.hasProperty(RailBlock.SHAPE) ? s.getValue(RailBlock.SHAPE).toString() : "none";
    }
    private static String bounds(AABB a) {
        return "["+fmt(a.minX)+","+fmt(a.minY)+","+fmt(a.minZ)
                +"]..["+fmt(a.maxX)+","+fmt(a.maxY)+","+fmt(a.maxZ)+"]";
    }
    private static String role(Villager v) {
        return v.getPersistentData().getString(RailwayVillagerEgg.RAILWAY_ROLE_TAG);
    }

    public static int inspect(CommandSourceStack src, ServerLevel level, BlockPos station,
                              UUID locomotiveUuid, UUID coachUuid,
                              UUID chiefUuid, UUID conductorUuid, UUID engineerUuid,
                              boolean departureActive) {
        Report r = new Report(src);
        r.line("BEGIN station=" + station.toShortString()
                + " locomotive=" + locomotiveUuid + " coach=" + coachUuid);
        try {
            inspectInternals(r,level,station,locomotiveUuid,coachUuid,
                    chiefUuid,conductorUuid,engineerUuid,departureActive);
        } catch (Exception error) {
            r.check("unexpected_exception", "FAIL", error.getClass().getSimpleName()
                    + ": " + String.valueOf(error.getMessage()).replace('\n',' '));
            LOG.error(PREFIX + "DIAGNOSTIC_EXCEPTION (read-only; no auto repair)",error);
        } finally {
            r.finish(station);
        }
        return r.fail==0 ? 1 : 0;
    }

    private static void inspectInternals(Report r,ServerLevel level,BlockPos c,
                                         UUID locomotiveUuid,UUID coachUuid,
                                         UUID chiefUuid,UUID conductorUuid,UUID engineerUuid,
                                         boolean departureActive) {
        boolean chunks=level.hasChunkAt(c);
        r.check("station_chunk_loaded",chunks?"PASS":"UNVERIFIED", "station="+c.toShortString());
        if (chunks) {
            BlockState stationRail=level.getBlockState(c);
            boolean match="traincraft:track_station".equals(blockId(stationRail));
            r.check("station_rail",match?"PASS":"FAIL", "id="+blockId(stationRail)
                    +" railShape="+shape(stationRail));
        }
        r.check("manual_departure_idle",departureActive?"FAIL":"PASS",
                "active="+departureActive+" (no control changes made)");

        Entity locoEntity=level.getEntity(locomotiveUuid);
        Entity coachEntity=level.getEntity(coachUuid);
        if (!(locoEntity instanceof LegacyPoweredLocomotive loco)
                || !(coachEntity instanceof SteamTender coach)
                || locoEntity.isRemoved() || coachEntity.isRemoved()) {
            r.check("saved_pair_loaded","UNVERIFIED","locoLoaded="+(locoEntity!=null)
                    +" coachLoaded="+(coachEntity!=null)+" (no chunk loading)");
        } else {
            boolean types="traincraft:locomotive_diesel_gp7".equals(id(locoEntity))
                    && "traincraft:passenger_coach_blue".equals(id(coachEntity));
            r.check("saved_pair_types",types?"PASS":"FAIL",
                    "loco="+id(locoEntity)+" coach="+id(coachEntity));
            boolean owner=loco.getPersistentData().getLong("TC_RAILWAY_SERVICE_STATION")==c.asLong()
                    && coach.getPersistentData().getLong("TC_RAILWAY_SERVICE_STATION")==c.asLong()
                    && "locomotive".equals(loco.getPersistentData().getString("TC_RAILWAY_SERVICE_ROLE"))
                    && "coach_1".equals(coach.getPersistentData().getString("TC_RAILWAY_SERVICE_ROLE"));
            r.check("vehicle_station_ownership",owner?"PASS":"FAIL","saved_station="+c.asLong());
            boolean coupled=CouplingManager.areMutuallyLinked(loco,loco,coach,coach);
            double gap=Math.sqrt(loco.distanceToSqr(coach));
            r.check("mutual_coupling",coupled && gap>=2 && gap<=4 ?"PASS":"FAIL",
                    "mutual="+coupled+" gap="+fmt(gap));
            double lspeed=loco.getDeltaMovement().horizontalDistance();
            double cspeed=coach.getDeltaMovement().horizontalDistance();
            boolean stopped=lspeed<0.01 && cspeed<0.01 && loco.isHandBrakeApplied();
            r.check("parked_state",stopped?"PASS":"FAIL",
                    "locoMotion="+fmt(lspeed)+" coachMotion="+fmt(cspeed)
                    +" handbrake="+loco.isHandBrakeApplied());
            r.line("VEHICLE locoPos="+loco.position()+" coachPos="+coach.position()
                    +" locoYaw="+fmt(loco.getYRot())+" coachYaw="+fmt(coach.getYRot())
                    +" locoPhysicalAABB="+bounds(loco.getBoundingBox())
                    +" coachPhysicalAABB="+bounds(coach.getBoundingBox())
                    +" fuelReserve="+fmt(loco.getPowerAmount()));
            r.check("rendered_mesh_swept_bounds","UNVERIFIED",
                    "Server entity AABB is NOT GP7/coach rendered mesh or turning swept volume");
            double locoRoom=c.getX()+6.2D-loco.getX();
            double coachRoom=c.getX()+4.0D-coach.getX();
            double room=Math.min(locoRoom,coachRoom);
            r.check("c8_no_room_refusal",room<0.750001D?"PASS":"UNVERIFIED",
                    "available="+fmt(room)+" required=0.750 (predicted, no start command invoked)");
            r.line("TRAIN_CENTERLINE locoX="+fmt(loco.getX())+" coachX="+fmt(coach.getX())
                    +" stationRailY="+c.getY()+" horizontalGap="+fmt(gap));
            scanRollingStock(r,level,c,loco,coach);
        }
        inspectVillagers(r,level,c,chiefUuid,conductorUuid,engineerUuid);
        scanRailRoute(r,level,c,locoEntity);
        inspectDev50d0(r,level,c,locoEntity,coachEntity,conductorUuid);
        r.check("actual_turnout_path_and_live_braking","UNVERIFIED",
                "No train moved; branch selection, dynamic coach path and braking remain untested");
    }

    private static void scanRollingStock(Report r,ServerLevel level,BlockPos c,
                                         LegacyPoweredLocomotive loco,SteamTender coach) {
        AABB forward=new AABB(loco.getX()+0.65D,loco.getY()-0.35D,loco.getZ()-0.48D,
                loco.getX()+3.15D,loco.getY()+1.90D,loco.getZ()+0.48D);
        List<LivingEntity> living=level.getEntitiesOfClass(LivingEntity.class,forward,
                e -> !e.isRemoved());
        r.check("immediate_locomotive_forward_lane",living.isEmpty()?"PASS":"UNVERIFIED",
                "loadedLivingObstructions="+living.size()+" AABB="+bounds(forward));
        AABB local=new AABB(c.offset(-9,-2,-2),c.offset(27,5,3));
        int otherRollingStock=0;
        for (Entity e:level.getEntitiesOfClass(Entity.class,local,
                entity->entity!=loco && entity!=coach && !entity.isRemoved())) {
            if (e instanceof traincraft.entity.train.CoupleableRollingStock
                    || e instanceof net.minecraft.world.entity.vehicle.AbstractMinecart) {
                otherRollingStock++;
                if (otherRollingStock<=MAX_SAMPLES)
                    r.line("OTHER_STOCK id="+id(e)+" at="+e.position());
            }
        }
        r.check("other_loaded_rolling_stock",otherRollingStock==0?"PASS":"UNVERIFIED",
                "count="+otherRollingStock+" (loaded entity screen only)");
    }

    private static void inspectVillagers(Report r,ServerLevel level,BlockPos c,
                                         UUID chiefUuid,UUID conductorUuid,UUID engineerUuid) {
        AABB area=new AABB(c.offset(-20,-4,-20),c.offset(21,8,21));
        List<Villager> nearby=level.getEntitiesOfClass(Villager.class,area,v->!v.isRemoved());
        r.line("WORKERS loadedVillagersInStationArea="+nearby.size()
                +" recordedChief="+chiefUuid+" recordedConductor="+conductorUuid
                +" recordedEngineer="+engineerUuid);
        for (String expected:new String[]{"station_chief","train_conductor","railway_engineer"}) {
            UUID recorded="station_chief".equals(expected)?chiefUuid:
                    "train_conductor".equals(expected)?conductorUuid:engineerUuid;
            Villager v=null;
            if (recorded!=null && level.getEntity(recorded) instanceof Villager loaded)
                v=loaded;
            if (v==null) for (Villager match:nearby) {
                if (expected.equals(role(match))
                        && (!match.getPersistentData().contains("TC_RAILWAY_WORLDGEN_STATION",Tag.TAG_LONG)
                         || match.getPersistentData().getLong("TC_RAILWAY_WORLDGEN_STATION")==c.asLong())) {
                    v=match;break;
                }
            }
            if (v==null) {
                r.check("worker_"+expected,"UNVERIFIED","not loaded/found; NOT a death diagnosis");
                continue;
            }
            String profession=BuiltInRegistries.VILLAGER_PROFESSION.getKey(
                    v.getVillagerData().getProfession()).toString();
            CompoundTag saved=new CompoundTag();
            v.saveWithoutId(saved); // serialization snapshot; DO NOT call getOffers() (may lazy-create trades)
            CompoundTag offers=saved.getCompound("Offers");
            int count=offers.getList("Recipes",Tag.TAG_COMPOUND).size();
            boolean valid=("traincraft:"+expected).equals(profession) && expected.equals(role(v))
                    && count>=2;
            String job=v.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                    .map(p -> p.pos().toShortString()+" dimension="+p.dimension().location())
                    .orElse("NONE");
            r.check("worker_"+expected,valid?"PASS":"UNVERIFIED",
                    "uuid="+v.getUUID()+" profession="+profession+" nbtOffers="+count
                    +" jobSite="+job+" role="+role(v));
        }
    }

    private static boolean isEW(BlockState s) {
        return ("traincraft:track_normal".equals(blockId(s))
                 || "traincraft:track_station".equals(blockId(s)))
                && s.hasProperty(RailBlock.SHAPE)
                && s.getValue(RailBlock.SHAPE)==RailShape.EAST_WEST;
    }
    private static boolean railCandidate(BlockState s) {
        String name=blockId(s);
        return name.startsWith("traincraft:track_") || name.startsWith("minecraft:rail")
                || name.contains("switch_") || name.contains("rail_");
    }
    private static double cellDistance(double z,int blockZ) {
        return Math.max(0.0D,Math.max(blockZ-z,z-(blockZ+1.0D)));
    }
    private static void scanRailRoute(Report r,ServerLevel level,BlockPos c,Entity locomotive) {
        if (locomotive==null) {
            r.check("eastbound_route","UNVERIFIED","locomotive unloaded; no forward scan");return;
        }
        int firstX=(int)Math.floor(locomotive.getX());
        int firstBoundary=firstX+MAX_AHEAD;
        int straightCells=0;
        String reason="SCAN_LIMIT_24";
        for (int offset=0;offset<MAX_AHEAD;offset++) {
            BlockPos p=new BlockPos(firstX+offset,c.getY(),c.getZ());
            if (!level.hasChunkAt(p)) {firstBoundary=p.getX();reason="UNLOADED_CHUNK";break;}
            BlockState rail=level.getBlockState(p);
            r.line("RAIL x="+p.getX()+" y="+p.getY()+" z="+p.getZ()
                    +" id="+blockId(rail)+" shape="+shape(rail));
            if (!isEW(rail)) {firstBoundary=p.getX();reason="NON_STRAIGHT_OR_SWITCH="+blockId(rail);break;}
            straightCells++;
        }
        double ahead=Math.max(0.0D,firstBoundary-locomotive.getX());
        r.check("eastbound_centerline_scan",straightCells>0?"PASS":"UNVERIFIED",
                "straightCells="+straightCells+" firstX="+firstX+" firstBoundaryX="+firstBoundary
                +" reason="+reason+" centerlineDistance="+fmt(ahead));
        // Sample the real switch's surrounding *loaded* block states; do not
        // interpret custom track-segment branch physics as vanilla rail edges.
        int samples=0,unknownChunks=0,railCells=0;
        for(int x=firstBoundary-2;x<=firstBoundary+5;x++) {
            for(int z=c.getZ()-7;z<=c.getZ()+7;z++) {
                for(int y=c.getY()-1;y<=c.getY()+1;y++) {
                    BlockPos p=new BlockPos(x,y,z);
                    if (!level.hasChunkAt(p)) {unknownChunks++;continue;}
                    BlockState state=level.getBlockState(p);
                    if (!railCandidate(state)) continue;
                    railCells++;
                    if (samples<24) {
                        r.line("NEAR_BOUNDARY_TRACK pos="+p.toShortString()
                                +" id="+blockId(state)+" props="+state.getValues());
                        samples++;
                    }
                }
            }
        }
        r.check("turnout_local_block_inventory",railCells>0?"PASS":"UNVERIFIED",
                "trackCells="+railCells+" emitted="+samples+" unloadedCellSamples="+unknownChunks);
        // Conservative STATIC corridor screening, not a dynamic body sweep.
        // The same provisional half-width/vertical envelope as the frozen
        // railway clearance audit; possible platform collisions are warnings.
        double sideways=HALF_BODY_WIDTH+SIDE_MARGIN;
        int hit=0,unloaded=0;
        Set<BlockPos> seen=new HashSet<>();
        List<String> examples=new ArrayList<>();
        int limit=Math.min(firstBoundary,firstX+MAX_AHEAD);
        for(int x=firstX;x<limit;x++) {
            for(int z=c.getZ()-3;z<=c.getZ()+3;z++) {
                if(cellDistance(c.getZ()+0.5D,z)>sideways) continue;
                for(int dy=0;dy<=4;dy++) {
                    BlockPos p=new BlockPos(x,c.getY()+dy,z);
                    if(dy==0 && z==c.getZ()) continue; // existing track bed
                    if(!level.hasChunkAt(p)) {unloaded++;continue;}
                    BlockState state=level.getBlockState(p);
                    if(state.getCollisionShape(level,p).isEmpty() && state.getFluidState().isEmpty()) continue;
                    if(seen.add(p)) {
                        hit++;
                        if(examples.size()<MAX_SAMPLES)
                            examples.add(p.toShortString()+":"+blockId(state));
                    }
                }
            }
        }
        r.check("static_side_roof_clearance_screen",hit==0 && unloaded==0?"PASS":"UNVERIFIED",
                "provisionalHalfWidth="+fmt(HALF_BODY_WIDTH)+" sideMargin="+fmt(SIDE_MARGIN)
                +" verticalHeight="+fmt(VERTICAL_HEIGHT)+" obstacleCells="+hit
                +" unloadedCells="+unloaded+" examples="+examples);
        r.check("switch_branch_and_rendered_sweep","UNVERIFIED",
                "Track-state inventory only; switch route continuity, mesh bounds and overhang NOT CERTIFIED");
    }

    /* DEV50d0 additional read-only evidence. This adds NO movement permission.
     * Worldgen/source hooks and all accepted DEV50c8 controls remain frozen. */
    private static String prop(BlockState state, String name) {
        for (java.util.Map.Entry<net.minecraft.world.level.block.state.properties.Property<?>,Comparable<?>> e
                : state.getValues().entrySet()) {
            if (name.equals(e.getKey().getName())) return String.valueOf(e.getValue());
        }
        return "MISSING";
    }

    private static void inspectDev50d0(Report r,ServerLevel level,BlockPos station,
                                       Entity locomotive,Entity coach,UUID conductorUuid) {
        r.line("DEV50D0 BEGIN mode=READ_ONLY source=DEV50C9R "+
                "MOVEMENT=NONE CHUNK_LOADS=NONE WORLD_CHANGES=NONE");
        // Do not assume that a custom segment carrying vanilla EAST_WEST shape
        // necessarily follows the straight branch. 'active' semantics are not
        // inferred from the blockstate alone.
        int switchX=station.getX()+10;
        final int[][] cells={
                {0,0,0},{1,0,1},{2,0,2},{3,0,3},{4,0,4},{5,0,5},
                {2,1,9},{3,1,10},{4,1,11},{4,2,12},{5,2,13}};
        int matched=0,unloaded=0,mismatch=0;
        java.util.List<String> examples=new java.util.ArrayList<>();
        String activeState="NONE";
        for(int[] part:cells) {
            BlockPos pos=new BlockPos(switchX+part[0],station.getY(),station.getZ()+part[1]);
            if(!level.hasChunkAt(pos)) {unloaded++;continue;}
            BlockState state=level.getBlockState(pos);
            String block=blockId(state);
            String assembly=prop(state,"assembly_part");
            String facing=prop(state,"facing");
            String active=prop(state,"active");
            boolean ok="traincraft:track_switch_parallel_right_segment".equals(block)
                    && Integer.toString(part[2]).equals(assembly)
                    && "east".equals(facing)
                    && ("true".equals(active)||"false".equals(active));
            if ("NONE".equals(activeState)) activeState=active;
            if (!activeState.equals(active)) ok=false;
            if (ok) matched++;
            else {
                mismatch++;
                if(examples.size()<6)examples.add(pos.toShortString()+"="+block
                        +" part="+assembly+" facing="+facing+" active="+active);
            }
        }
        String turnoutState=(unloaded>0)?"UNVERIFIED":(mismatch>0?"FAIL":"PASS");
        r.check("d0_turnout_11_cell_saved_signature",turnoutState,
                "expected=11 matched="+matched+" wrong="+mismatch+" unloaded="+unloaded
                +" root="+switchX+","+station.getY()+","+station.getZ()
                +" active="+activeState+" examples="+examples);
        r.line("DEV50D0 SWITCH_STATE active="+activeState
                +" (branch mapping UNVERIFIED; segment shape is NOT track-path proof)");
        // The parallel running line is a distinct rail at stationZ-5. Do not
        // infer a crossover connection from proximity of adjacent rail beds.
        int parallel=0,missing=0,unloadedParallel=0;
        for(int x=switchX-2;x<=switchX+5;x++) {
            BlockPos p=new BlockPos(x,station.getY(),station.getZ()-5);
            if(!level.hasChunkAt(p)) {unloadedParallel++;continue;}
            if(isEW(level.getBlockState(p)))parallel++;else missing++;
        }
        r.check("d0_parallel_track_saved_continuity",
                unloadedParallel>0?"UNVERIFIED":missing>0?"UNVERIFIED":"PASS",
                "x="+(switchX-2)+".."+(switchX+5)+" z="+(station.getZ()-5)
                +" straightCells="+parallel+" otherCells="+missing
                +" unloaded="+unloadedParallel+" (no switch routing inference)");
        // Only inspect already-loaded block states. A voxel collision-shape
        // overlap with a TRANSLATED SERVER AABB is a provisional warning;
        // rendered mesh + bogie sweep require a separate genuine motion test.
        if(locomotive!=null && coach!=null) {
            int impacts=0,unknowns=0;
            java.util.List<String> hits=new java.util.ArrayList<>();
            for(Entity e:new Entity[]{locomotive,coach}) {
                AABB body=e.getBoundingBox();
                double advance=Math.max(0, switchX-0.25D-e.getX());
                AABB lane=new AABB(body.minX,body.minY,body.minZ,
                        body.maxX+advance,body.maxY,body.maxZ);
                int localHits=0;
                for(int x=(int)Math.floor(lane.minX);x<Math.ceil(lane.maxX);x++)
                    for(int y=(int)Math.floor(lane.minY);y<Math.ceil(lane.maxY);y++)
                        for(int z=(int)Math.floor(lane.minZ);z<Math.ceil(lane.maxZ);z++) {
                            BlockPos p=new BlockPos(x,y,z);
                            if(!level.hasChunkAt(p)) {unknowns++;continue;}
                            BlockState state=level.getBlockState(p);
                            if(railCandidate(state))continue; // rail/bed is expected here
                            net.minecraft.world.phys.shapes.VoxelShape shape=state.getCollisionShape(level,p);
                            if(shape.isEmpty())continue;
                            boolean overlap=false;
                            for(AABB piece:shape.toAabbs()) {
                                if(piece.move(p).intersects(lane)) {overlap=true;break;}
                            }
                            if(overlap) {
                                impacts++;localHits++;
                                if(hits.size()<6)hits.add(e.getUUID()+"@"+p.toShortString()+":"+blockId(state));
                            }
                        }
                r.line("DEV50D0 PHYSICAL_LANE entity="+e.getUUID()
                        +" start="+bounds(body)+" staticXAdvance="+fmt(advance)
                        +" sweptProxy="+bounds(lane)+" collisionSamples="+localHits);
            }
            String physical=unknowns>0||impacts>0?"UNVERIFIED":"PASS";
            r.check("d0_straight_server_box_corridor",physical,
                    "nonRailShapeOverlaps="+impacts+" unloadedSamples="+unknowns
                    +" sampleHits="+hits
                    +" (server physics BOX only; mesh/bogie overhang NOT CERTIFIED)");
            // Report the exact fence's lateral separation from the narrow
            // server collision box, without claiming that the mesh fits.
            int fenceX=station.getX()+7;
            int fenceZ=station.getZ()+2;
            BlockPos fence=new BlockPos(fenceX,station.getY()+2,fenceZ);
            if(level.hasChunkAt(fence)) {
                BlockState fs=level.getBlockState(fence);
                String id=blockId(fs);
                if("minecraft:oak_fence".equals(id)) {
                    double right=Math.max(locomotive.getBoundingBox().maxZ,
                            coach.getBoundingBox().maxZ);
                    double gap=Math.max(0,fenceZ-right);
                    r.check("d0_fence_physical_aabb_gap",gap>0?"PASS":"UNVERIFIED",
                            "fence="+fence.toShortString()+" block="+id
                            +" minimumFullBlockGapZ="+fmt(gap)
                            +" conservativeEnvelopeHalfWidth=1.850"
                            +" (actual fence collision shape could be narrower; rendered width UNKNOWN)");
                } else {
                    r.check("d0_fence_physical_aabb_gap","UNVERIFIED",
                            "expected oak_fence at "+fence.toShortString()+" actual="+id);
                }
            } else r.check("d0_fence_physical_aabb_gap","UNVERIFIED","fence chunk unloaded");
        } else r.check("d0_straight_server_box_corridor","UNVERIFIED","saved rolling stock not loaded");
        // The C9R station-area snapshot missed the conductor although the
        // earlier SPAWN_OR_LOAD log proved a correct load. Search LOADED
        // entities in a larger area only; do not teleport/respawn or force
        // chunks. Never generate trades by calling Villager.getOffers().
        AABB wide=new AABB(station.offset(-96,-16,-96),station.offset(97,32,97));
        java.util.List<Villager> villagers=level.getEntitiesOfClass(Villager.class,wide,v->!v.isRemoved());
        int conductors=0;int good=0;
        java.util.List<String> seen=new java.util.ArrayList<>();
        for(Villager villager:villagers) {
            if(!"train_conductor".equals(role(villager)))continue;
            conductors++;
            CompoundTag tag=new CompoundTag();
            villager.saveWithoutId(tag); // no lazy-trade initialization
            int offers=tag.getCompound("Offers").getList("Recipes",Tag.TAG_COMPOUND).size();
            String profession=BuiltInRegistries.VILLAGER_PROFESSION
                    .getKey(villager.getVillagerData().getProfession()).toString();
            String job=villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                    .map(p->p.pos().toShortString()).orElse("NONE");
            if("traincraft:train_conductor".equals(profession)&&offers>=2)good++;
            if(seen.size()<8)seen.add("uuid="+villager.getUUID()+" pos="+villager.position()
                    +" profession="+profession+" offers="+offers+" job="+job
                    +" tracked="+villager.getUUID().equals(conductorUuid));
        }
        String worker=good>0?"PASS":"UNVERIFIED";
        r.check("d0_conductor_wide_loaded_search",worker,
                "loadedVillagersScanned="+villagers.size()+" candidateConductor="+conductors
                +" validConductor="+good+" priorTrackedId="+conductorUuid
                +" evidence="+seen
                +" (loaded entities only; unloaded/death status NOT inferred)");
        r.check("d0_turnout_moving_branch_clearance","UNVERIFIED",
                "Saved switch signature and AABB proxies cannot certify rendered"
                +" train geometry, switched path, live braking or safe long departure");
        r.line("DEV50D0 END staticProofOnly=true noWorldEdits=true noMovement=true");
    }
}
