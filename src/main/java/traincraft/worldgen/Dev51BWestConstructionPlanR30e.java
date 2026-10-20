package traincraft.worldgen;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * DEV51B R30e - immutable, read-only planning interpreter.
 * Interprets observed cells only; no Minecraft APIs, blocks, saved data or dispatch.
 * A preview is NEVER construction, route, or train movement authority.
 */
public final class Dev51BWestConstructionPlanR30e {
    private Dev51BWestConstructionPlanR30e() {}
    public static final int ORIGINAL_END=8, FIRST_NEW=9, STATION_OFFSET=20, LAST_NEW=24;
    public static final int NORMAL_COUNT=15, STATION_COUNT=1, NEW_COUNT=16;
    public static final int RESERVE_ALONG=9, RESERVE_LATERAL=7, RESERVED_SIDE_CELLS=54;

    public enum RailStatus {
        EXISTING_EW, AIR_NORMAL_EW, AIR_STATION_EW,
        UNLOADED, ORIGINAL_RAIL_MISMATCH, BLOCKED,
        SUPPORT_MISSING, HEADROOM_BLOCKED, SIDE_BLOCKED,
        FLUID, ENTITY, BLOCK_ENTITY
    }
    public enum SiteStatus { CLEAR_SUPPORTED, UNLOADED, BLOCKED, SUPPORT_MISSING, FLUID, ENTITY, BLOCK_ENTITY }
    public enum WestEnd { OPEN_AIR, EXISTING_EW_RAIL, BLOCKED, UNLOADED }

    public record Rail(int offset,RailStatus status,String observed,String support,String entityTypes) {
        public Rail {
            if(offset<0||offset>LAST_NEW||status==null||observed==null||support==null||entityTypes==null)
                throw new IllegalArgumentException("invalid R30e rail witness");
        }
    }
    public record Site(int along,int lateral,SiteStatus status,String observed) {
        public Site {
            if(along < -4||along>4||lateral < -3||lateral>3||lateral==0||status==null||observed==null)
                throw new IllegalArgumentException("invalid R30e site witness");
        }
    }
    public record Report(int originalRail,int normalRail,int stationRail,int routeHolds,
                         int clearSide,int sideHolds,int firstHoldOffset,
                         int nearbyVillagers,int otherStations,WestEnd westEnd,
                         boolean networkRecorded,boolean railRegistryReady,
                         boolean cleanRailGeometry,boolean stationReserveClear,
                         boolean permitsConstruction,boolean permitsMovement,
                         boolean permitsDispatch,boolean certifiesNativeRoute,
                         String verdict,String fingerprint) {}

    public static int x(int origin,int offset) {
        if(offset<0||offset>LAST_NEW)throw new IllegalArgumentException("offset");
        return origin-offset;
    }
    private static byte[] sha256(String in){
        try {return MessageDigest.getInstance("SHA-256").digest(in.getBytes(StandardCharsets.UTF_8));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static Report evaluate(List<Rail> route,List<Site> footprint,
                                  int nearbyVillagers,int otherStations,
                                  boolean networkRecorded,boolean railRegistryReady,WestEnd westEnd){
        if(route==null||footprint==null||westEnd==null||route.size()!=LAST_NEW+1
                ||footprint.size()!=RESERVED_SIDE_CELLS||nearbyVillagers < -1||otherStations<0)
            throw new IllegalArgumentException("complete observations required");
        boolean[] seen=new boolean[LAST_NEW+1];
        int old=0,normal=0,station=0,issues=0,first=-1;
        StringBuilder witness=new StringBuilder("R30e-v1|").append(westEnd).append('|')
                .append(networkRecorded).append('|').append(railRegistryReady)
                .append('|').append(nearbyVillagers).append('|').append(otherStations);
        Rail[] sorted=new Rail[LAST_NEW+1];
        for(Rail r:route){
            if(seen[r.offset()])throw new IllegalArgumentException("duplicate route offset");
            seen[r.offset()]=true;sorted[r.offset()]=r;
        }
        for(int off=0;off<=LAST_NEW;off++) {
            if(!seen[off])throw new IllegalArgumentException("missing route offset");
            Rail r=sorted[off];
            witness.append("|R:").append(off).append(':').append(r.status()).append(':')
                .append(r.observed()).append(':').append(r.support()).append(':').append(r.entityTypes());
            RailStatus expected=off<=ORIGINAL_END?RailStatus.EXISTING_EW:
                    off==STATION_OFFSET?RailStatus.AIR_STATION_EW:RailStatus.AIR_NORMAL_EW;
            if(r.status()!=expected){issues++;if(first<0)first=off;}
            else if(off<=ORIGINAL_END)old++;
            else if(off==STATION_OFFSET)station++;
            else normal++;
        }
        boolean[][] used=new boolean[RESERVE_ALONG][RESERVE_LATERAL];
        Site[][] side=new Site[RESERVE_ALONG][RESERVE_LATERAL];
        for(Site s:footprint) {
            int i=s.along()+4,j=s.lateral()+3;
            if(used[i][j])throw new IllegalArgumentException("duplicate station reserve cell");
            used[i][j]=true;side[i][j]=s;
        }
        int clear=0,sideIssues=0;
        for(int along=0;along<RESERVE_ALONG;along++)for(int lateral=0;lateral<RESERVE_LATERAL;lateral++) {
            if(lateral==3)continue;
            if(!used[along][lateral])throw new IllegalArgumentException("missing station reserve cell");
            Site s=side[along][lateral];
            witness.append("|S:").append(s.along()).append(',').append(s.lateral())
                .append(':').append(s.status()).append(':').append(s.observed());
            if(s.status()==SiteStatus.CLEAR_SUPPORTED)clear++;else sideIssues++;
        }
        boolean railClean=old==9&&normal==NORMAL_COUNT&&station==STATION_COUNT&&issues==0;
        boolean reserveClean=sideIssues==0&&nearbyVillagers==0&&otherStations==0;
        // An open terminal is NOT a protected stop; no buffer, braking, or speed authority.
        String verdict=!railRegistryReady?"HOLD_RAIL_REGISTRY_UNVERIFIED":
                issues>0?"HOLD_RAIL_GEOMETRY":
                !reserveClean?"HOLD_STATION_RESERVE":
                !networkRecorded?"HOLD_SOURCE_NETWORK_NOT_RECORDED":
                westEnd==WestEnd.UNLOADED?"HOLD_WEST_END_UNLOADED":
                "REVIEW_ONLY_LAYOUT_OBSERVED";
        String id=HexFormat.of().formatHex(sha256(witness.toString()));
        return new Report(old,normal,station,issues,clear,sideIssues,first,nearbyVillagers,
                otherStations,westEnd,networkRecorded,railRegistryReady,
                railClean,reserveClean,false,false,false,false,verdict,id);
    }
}
