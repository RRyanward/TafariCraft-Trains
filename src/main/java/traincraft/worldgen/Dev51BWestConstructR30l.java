package traincraft.worldgen;
import java.util.*;
/** Source-only west construction safety gates; never grants locomotive/dispatch authority. */
public final class Dev51BWestConstructR30l {
  private Dev51BWestConstructR30l(){}
  public static final String PLAN="5594db3ecfc31d207e14233de238561c14451fc0185da43b32c2db4c6ce5520d";
  public static final String SOURCE="e6a172d6337927d7ee6f28a8789eed05f18fefe6f0492f3a842d661751b9666d";
  public static final int WEST_START=37, WEST_END=52, STATION_X=41, BUFFER_X=36;
  public enum Cell { CLEAR, NORMAL_EW, STATION_EW, BUFFER_EAST, UNLOADED, OBSTRUCTED, ENTITY, FLUID, BAD_SUPPORT, BAD_HEADROOM, BAD_FLANK, BLOCK_ENTITY }
  public record Result(String status,boolean safeToCommit,boolean built,boolean mayDrive,boolean mayDispatch){}
  public static Result inspect(boolean persisted,boolean backupValid,boolean registries,boolean sourceNine,
       Cell[] west,Cell buffer,Cell future1,Cell future2,int sideClear,int villagers,int stations,boolean legacyMarked) {
    if(west==null||west.length!=16||buffer==null||future1==null||future2==null)throw new IllegalArgumentException("Incomplete rail cells");
    for(Cell c:west)if(c==null)throw new IllegalArgumentException("Null rail cell");
    if(sideClear<0||sideClear>54||villagers < -1||stations<0)throw new IllegalArgumentException("Bad reservation census");
    if(!persisted)return new Result("HOLD_DISK_LOADED_R30I_SOURCE",false,false,false,false);
    if(!sourceNine||legacyMarked)return new Result("HOLD_SOURCE_OR_LEGACY_MARKER_MISMATCH",false,false,false,false);
    if(!registries)return new Result("HOLD_BLOCK_REGISTRY_OR_FACING",false,false,false,false);
    boolean built=buffer==Cell.BUFFER_EAST;
    for(int n=0;n<16;n++)built &= west[n] == (WEST_END-n==STATION_X?Cell.STATION_EW:Cell.NORMAL_EW);
    if(built)return new Result("BUILT_HOLD_STOP_COLLISION_UNCERTIFIED",false,true,false,false);
    if(buffer==Cell.BUFFER_EAST||Arrays.stream(west).anyMatch(c->c==Cell.NORMAL_EW||c==Cell.STATION_EW))
       return new Result("HOLD_PARTIAL_CONSTRUCTION",false,false,false,false);
    if(Arrays.stream(west).anyMatch(c->c!=Cell.CLEAR)||buffer!=Cell.CLEAR)
       return new Result("HOLD_PROPOSED_BLOCKS_NOT_CLEAR",false,false,false,false);
    if(future1!=Cell.CLEAR||future2!=Cell.CLEAR)return new Result("HOLD_FUTURE_WEST_EXTENT",false,false,false,false);
    if(sideClear!=54||villagers!=0||stations!=0)return new Result("HOLD_STATION_SIDE_OR_VILLAGER",false,false,false,false);
    if(!backupValid)return new Result("HOLD_NEW_OFFLINE_BACKUP_REQUIRED",false,false,false,false);
    return new Result("READY_EXPLICIT_PLAN_CONFIRM_REQUIRED",true,false,false,false);
  }
}
