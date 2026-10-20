package traincraft.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import traincraft.registry.TCBlocks;

/**
 * DEV51B R33. Strict READ-ONLY bridge to existing RailwayVillageWorldgen StationData.
 * Does not register a handler, create SavedData, set blocks, issue chunk tickets or move entities.
 * Its straight-rail audit is preliminary: specialty rails, turnouts, curved assemblies,
 * long village routes and normal-world automatic dispatch are NOT certified.
 */
public final class ReadOnlyRailwayRouteAudit {
  private ReadOnlyRailwayRouteAudit() {}
  private static final String RECORD_ID = "tafaricraft_railway_village_stations_v1";
  public static final int MAX_CELLS = 256;
  public enum Reason {
    OK_STRAIGHT_ONLY, NO_RECORD, UNRECORDED_STATION, UNRECORDED_NETWORK,
    SERVICE_NOT_ACTIVATED, STAGED_CONSIST_NOT_RECORDED, NOT_TWO_RECORDED_STATIONS,
    INVALID_ENDPOINTS, TOO_MANY_CELLS, UNLOADED_CHUNK, MISSING_SUPPORT,
    GAP_OR_UNCERTIFIED_RAIL, CURVE_OR_JUNCTION_UNCERTIFIED, OCCUPIED, INVALID_RECORD
  }
  public record Finding(Reason reason, int checked, BlockPos firstProblem,
                        boolean metadataRecorded, boolean routeStraightAndLoaded,
                        boolean occupied, boolean productionDispatchCertified) {
    public boolean preliminaryReadiness() { return reason == Reason.OK_STRAIGHT_ONLY
        && metadataRecorded && routeStraightAndLoaded && !occupied && !productionDispatchCertified; }
  }
  private static Finding fail(Reason reason,int n,BlockPos p,boolean recorded,boolean occupied){
    return new Finding(reason,n,p,recorded,false,occupied,false);
  }
  /** The fallback is a detached SavedData object only if the record already exists on disk.
   * get(), unlike computeIfAbsent(), never creates a record or marks it dirty.
   * If worldgen already cached its private StationData, we use its existing instance.
   * Worldgen StationData.save is a field-only serialization; we never mutate it.
   */
  private static final class DetachedSnapshot extends SavedData {
    private final CompoundTag copy;
    DetachedSnapshot(CompoundTag tag){ copy=tag.copy(); }
    @Override public CompoundTag save(CompoundTag unused){ return copy.copy(); }
  }
  public static CompoundTag existingWorldgenRecord(ServerLevel level) {
    if(level==null)return null;
    SavedData already=level.getDataStorage().get(DetachedSnapshot::new, RECORD_ID);
    return already==null ? null : already.save(new CompoundTag()).copy();
  }
  private static boolean hasStation(CompoundTag tag,BlockPos pos){
    if(!tag.contains("Stations", Tag.TAG_LONG_ARRAY))return false;
    for(long k:tag.getLongArray("Stations"))if(k==pos.asLong())return true;
    return false;
  }
  private static boolean inLongList(CompoundTag tag,String name,BlockPos pos){
    if(!tag.contains(name,Tag.TAG_LONG_ARRAY))return false;
    for(long k:tag.getLongArray(name))if(k==pos.asLong())return true;
    return false;
  }
  private static boolean inRecords(CompoundTag tag,String name,BlockPos pos){
    if(!tag.contains(name,Tag.TAG_LIST))return false;
    ListTag list=tag.getList(name,Tag.TAG_COMPOUND);
    for(int i=0;i<list.size();i++) if(list.getCompound(i).getLong("Station")==pos.asLong())return true;
    return false;
  }
  /** No force loading: hasChunkAt is always tested before querying block/entity state.
   * A station's persisted network flag is never interpreted as route connectivity.
   * Occupancy is checked for ALL entities except the explicitly identified test consist.
   */
  public static Finding inspect(ServerLevel level,CompoundTag record,BlockPos stationA,BlockPos stationB,
                                Set<UUID> permittedConsist){
    if(level==null || stationA==null || stationB==null || permittedConsist==null)
      return fail(Reason.INVALID_ENDPOINTS,0,null,false,false);
    if(record==null)return fail(Reason.NO_RECORD,0,stationA,false,false);
    if(!record.contains("Stations",Tag.TAG_LONG_ARRAY) || !record.contains("Networks",Tag.TAG_LONG_ARRAY)
       || !record.contains("WideNetworks",Tag.TAG_LONG_ARRAY))
      return fail(Reason.INVALID_RECORD,0,stationA,false,false);
    if(!hasStation(record,stationA)||!hasStation(record,stationB))
      return fail(Reason.NOT_TWO_RECORDED_STATIONS,0,stationA,false,false);
    if(!inLongList(record,"Networks",stationA)||!inLongList(record,"WideNetworks",stationA)
       ||!inLongList(record,"Networks",stationB)||!inLongList(record,"WideNetworks",stationB))
      return fail(Reason.UNRECORDED_NETWORK,0,stationA,false,false);
    if(!inRecords(record,"StationServices",stationA))
      return fail(Reason.SERVICE_NOT_ACTIVATED,0,stationA,true,false);
    if(!inRecords(record,"StationStagedConsists",stationA))
      return fail(Reason.STAGED_CONSIST_NOT_RECORDED,0,stationA,true,false);
    Set<UUID> recordedPair=new HashSet<>();
    for(int i=0;i<record.getList("StationStagedConsists",Tag.TAG_COMPOUND).size();i++){
      CompoundTag row=record.getList("StationStagedConsists",Tag.TAG_COMPOUND).getCompound(i);
      if(row.getLong("Station")==stationA.asLong()){
        if(!row.hasUUID("Locomotive")||!row.hasUUID("Coach"))
          return fail(Reason.INVALID_RECORD,0,stationA,true,false);
        recordedPair.add(row.getUUID("Locomotive"));
        recordedPair.add(row.getUUID("Coach"));
      }
    }
    if(recordedPair.size()!=2||!recordedPair.containsAll(permittedConsist))
      return fail(Reason.INVALID_RECORD,0,stationA,true,false);
    if(stationA.equals(stationB)||stationA.getY()!=stationB.getY()||stationA.getZ()!=stationB.getZ())
      return fail(Reason.INVALID_ENDPOINTS,0,stationA,true,false);
    long length=(long)Math.abs((long)stationA.getX()-stationB.getX())+1;
    if(length<5 ||length>MAX_CELLS)return fail(Reason.TOO_MANY_CELLS,0,stationA,true,false);
    int dir=Integer.compare(stationB.getX(),stationA.getX());
    Set<UUID> excluded=new HashSet<>(permittedConsist);
    for(int step=0;step<length;step++){
      BlockPos p=stationA.offset(dir*step,0,0);
      if(!level.hasChunkAt(p) || !level.hasChunkAt(p.below()) || !level.hasChunkAt(p.above()))
        return fail(Reason.UNLOADED_CHUNK,step,p,true,false);
      BlockState state=level.getBlockState(p);
      if((step==0 || step==length-1) && !state.is(TCBlocks.TRACK_STATION.get()))
        return fail(Reason.UNRECORDED_STATION,step,p,true,false);
      if(!(state.is(TCBlocks.TRACK_NORMAL.get()) || state.is(TCBlocks.TRACK_STATION.get())))
        return fail(Reason.GAP_OR_UNCERTIFIED_RAIL,step,p,true,false);
      if(!state.hasProperty(RailBlock.SHAPE) || state.getValue(RailBlock.SHAPE)!=RailShape.EAST_WEST)
        return fail(Reason.CURVE_OR_JUNCTION_UNCERTIFIED,step,p,true,false);
      if(!level.getBlockState(p.below()).isSolidRender(level,p.below()))
        return fail(Reason.MISSING_SUPPORT,step,p,true,false);
      AABB zone=new AABB(p).inflate(0.03,0.6,0.03);
      boolean busy=!level.getEntitiesOfClass(Entity.class,zone,e -> e.isAlive() &&
            !excluded.contains(e.getUUID())).isEmpty();
      if(busy)return fail(Reason.OCCUPIED,step,p,true,true);
    }
    return new Finding(Reason.OK_STRAIGHT_ONLY,(int)length,null,true,true,false,false);
  }
  public static Finding inspectExisting(ServerLevel level,BlockPos a,BlockPos b,Set<UUID> consist){
    return inspect(level,existingWorldgenRecord(level),a,b,consist);
  }
}
