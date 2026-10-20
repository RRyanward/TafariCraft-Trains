package traincraft.worldgen;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * DEV51B R30f - immutable, deterministic engineering report for a NEW, proposed
 * AS_BUILT_V1 source record. NEVER mutates SavedData, world blocks, or trains.
 * The existing legacy Networks / WideNetworks markers must never be repurposed.
 */
public final class Dev51BAsBuiltRecoveryR30f {
    private Dev51BAsBuiltRecoveryR30f() {}
    public static final String SCHEMA="TC_DEV51B_AS_BUILT_V1_PROPOSAL_ONLY";
    public static final int SOURCE_X=61,Y=-60,Z=-3,SOURCE_LAST=8,NEW_FIRST=9,NEW_LAST=24;
    public static final int SECOND_X=41,STATION_OFFSET=20,EXPECTED_EXISTING=9,EXPECTED_UNBUILT=16;
    public enum Kind {
        STATION_EW, NORMAL_EW, PROPOSED_STATION_AIR, PROPOSED_NORMAL_AIR,
        UNLOADED, WRONG_RAIL, AIR_GAP, BLOCKED, MISSING_SUPPORT, FLUID,
        HEADROOM, SIDE_CLEARANCE, ENTITY, BLOCK_ENTITY
    }
    public record Cell(int offset,Kind kind,String observed,String below) {
        public Cell {
            if(offset<0||offset>NEW_LAST||kind==null||observed==null||below==null||
               observed.isBlank()||below.isBlank())throw new IllegalArgumentException("invalid R30f cell");
        }
    }
    public record Report(int existingRail,int proposedAir,int firstHold,
                         int sourceStationRails,int additionalStationRails,
                         boolean legacyNetworkRecorded,boolean legacyWideRecorded,
                         boolean hasLegacyOutbound,boolean secondStationRecorded,
                         boolean sourceStationRecorded,boolean isR30TestStop,
                         boolean recordExistsInNewSchema,
                         boolean mayPersistRecord,boolean mayBuildRails,
                         boolean mayMoveTrain,boolean mayDispatch,
                         String verdict,String sourceFingerprint,String fullFingerprint) {}
    private static String hash(String s) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                  .digest(s.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static Report inspect(List<Cell> input, boolean sourceStationRecorded,
            boolean isR30TestStop,boolean legacyNetworkRecorded,boolean legacyWideRecorded,
            boolean hasLegacyOutbound,boolean secondStationRecorded,boolean recordExistsInNewSchema) {
        if(input==null || input.size()!=NEW_LAST+1)throw new IllegalArgumentException("Need exactly 25 witnessed cells");
        if(legacyWideRecorded && !legacyNetworkRecorded)throw new IllegalArgumentException("inconsistent legacy wide marker");
        if(hasLegacyOutbound && !legacyWideRecorded)throw new IllegalArgumentException("orphaned legacy outbound marker");
        Cell[] ordered=new Cell[NEW_LAST+1];
        for(Cell c:input){if(c==null||ordered[c.offset()]!=null)throw new IllegalArgumentException("null/duplicate cell");ordered[c.offset()]=c;}
        StringBuilder source=new StringBuilder(SCHEMA+"|source=61,-60,-3|count=9");
        StringBuilder full=new StringBuilder(SCHEMA+"|extension=37..52|station=41,-60,-3|placed=false");
        int existing=0,proposal=0,first=-1,sourceStations=0,extraStations=0;
        for(int off=0;off<=NEW_LAST;off++){
            Cell c=ordered[off];if(c==null)throw new IllegalArgumentException("missing cell "+off);
            String line="|"+off+":"+c.kind()+":"+c.observed()+":"+c.below();
            full.append(line);
            if(off<=SOURCE_LAST){
                source.append(line);
                if(c.kind()==Kind.STATION_EW||c.kind()==Kind.NORMAL_EW){existing++;if(c.kind()==Kind.STATION_EW){sourceStations++;if(off!=0)extraStations++;}}
                else if(first<0)first=off;
            }else if((off==STATION_OFFSET&&c.kind()==Kind.PROPOSED_STATION_AIR)
                    ||(off!=STATION_OFFSET&&c.kind()==Kind.PROPOSED_NORMAL_AIR))proposal++;
            else if(first<0)first=off;
        }
        // This is NOT an operational/network save: existing/source geometry only.
        source.append("|sourceRecord=").append(sourceStationRecorded)
              .append("|legacyNetwork=").append(legacyNetworkRecorded)
              .append("|legacyWide=").append(legacyWideRecorded)
              .append("|legacyOutbound=").append(hasLegacyOutbound)
              .append("|testStop=").append(isR30TestStop);
        full.append("|sourceFingerprint=").append(hash(source.toString()))
            .append("|secondSaved=").append(secondStationRecorded)
            .append("|newSchemaRecordExists=").append(recordExistsInNewSchema);
        String verdict=!sourceStationRecorded?"HOLD_SOURCE_STATION_RECORD_MISSING":
                isR30TestStop?"HOLD_SOURCE_IS_TEST_STOP":
                legacyNetworkRecorded||legacyWideRecorded||hasLegacyOutbound?"HOLD_LEGACY_RECORD_CONFLICT_REVIEW":
                secondStationRecorded?"HOLD_SECOND_STATION_ALREADY_RECORDED":
                recordExistsInNewSchema?"HOLD_AS_BUILT_RECORD_ALREADY_EXISTS":
                first>=0&&first<=SOURCE_LAST?"HOLD_EXISTING_RAIL_MISMATCH":
                sourceStations!=1||ordered[0].kind()!=Kind.STATION_EW||extraStations!=0?
                    "HOLD_SOURCE_STATION_RAIL_PROVENANCE":
                first>=NEW_FIRST?"HOLD_UNBUILT_CORRIDOR_CHANGED":
                existing!=EXPECTED_EXISTING||proposal!=EXPECTED_UNBUILT?"HOLD_INCOMPLETE_WITNESS":
                "HOLD_AS_BUILT_RECORD_NOT_PERSISTED_REVIEW_ONLY";
        return new Report(existing,proposal,first,sourceStations,extraStations,
                legacyNetworkRecorded,legacyWideRecorded,hasLegacyOutbound,
                secondStationRecorded,sourceStationRecorded,isR30TestStop,recordExistsInNewSchema,
                false,false,false,false,verdict,hash(source.toString()),hash(full.toString()));
    }
}
