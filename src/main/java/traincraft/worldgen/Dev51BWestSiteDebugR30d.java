package traincraft.worldgen;

import java.util.List;

/**
 * R30d: immutable read-only observation interpreter for the westward site.
 * No world API, rail placement, network writes, entity movement or dispatch.
 * A clean survey is NEVER construction or operational authorization.
 */
public final class Dev51BWestSiteDebugR30d {
    private Dev51BWestSiteDebugR30d() {}
    public static final int STATION_OFFSET = 20;
    public static final int WEST_END_OFFSET = 24;
    public static final int ORIGINAL_RAIL_END = 8;
    public static final int FIRST_NEW_RAIL = 9;
    public static final int RAIL_COUNT = WEST_END_OFFSET - FIRST_NEW_RAIL + 1;
    public static final int SITE_WIDTH = 9;
    public static final int SITE_DEPTH = 7;
    public static final int SITE_SIDE_COUNT = SITE_WIDTH * (SITE_DEPTH - 1);

    public enum RouteKind {
        EXISTING_EW_RAIL, PLACE_NORMAL_EW_CANDIDATE,
        PLACE_STATION_EW_CANDIDATE, BAD_ORIGINAL_RAIL,
        BLOCKED_TRACK_CELL, FOUNDATION_MISSING, HEADROOM_BLOCKED,
        SIDE_CLEARANCE_BLOCKED, ENTITY_IN_SWEEP, FLUID, UNLOADED
    }
    public enum SideKind {
        CLEAR_SUPPORTED, SUPPORT_MISSING, BLOCKED, ENTITY, FLUID, UNLOADED, BLOCK_ENTITY
    }
    public record RouteCell(int offset, RouteKind kind, String id) {
        public RouteCell { if(kind==null||id==null||offset<0||offset>WEST_END_OFFSET)
            throw new IllegalArgumentException("route cell"); }
    }
    public record SideCell(int along,int lateral,SideKind kind,String id) {
        public SideCell { if(kind==null||id==null||along < -4||along>4||lateral==0||lateral < -3||lateral>3)
            throw new IllegalArgumentException("site side cell"); }
    }
    public record Report(int originalRail, int plannedNormal, int plannedStation,
            int routeIssues, int footprintClear, int footprintIssues,
            int firstProblemOffset, RouteKind firstProblem,
            int nearbyVillagers, int adjacentStations,
            boolean sourceSpanVerified, boolean geometryObservedClean,
            boolean authorizesConstruction, boolean authorizesMovement,
            boolean certifiesConnectedRoute, String result) {}
    public static Report evaluate(List<RouteCell> route,List<SideCell> sides,
                                  int nearbyVillagers,int adjacentStations) {
        if(route==null||sides==null||route.size()!=WEST_END_OFFSET+1||sides.size()!=SITE_SIDE_COUNT
                ||nearbyVillagers<0||adjacentStations<0)
            throw new IllegalArgumentException("exact observations required");
        int original=0,normal=0,station=0,routeIssues=0,first=-1;
        RouteKind firstKind=null;
        boolean[] offsets=new boolean[WEST_END_OFFSET+1];
        for(RouteCell cell:route) {
            if(offsets[cell.offset()])throw new IllegalArgumentException("duplicate offset");
            offsets[cell.offset()]=true;
            boolean okay;
            if(cell.offset()<=ORIGINAL_RAIL_END) {
                okay=cell.kind()==RouteKind.EXISTING_EW_RAIL;
                if(okay)original++;
            } else {
                RouteKind expected=cell.offset()==STATION_OFFSET
                        ?RouteKind.PLACE_STATION_EW_CANDIDATE
                        :RouteKind.PLACE_NORMAL_EW_CANDIDATE;
                okay=cell.kind()==expected;
                if(okay && cell.offset()==STATION_OFFSET)station++;
                else if(okay)normal++;
            }
            if(!okay){routeIssues++;if(first<0){first=cell.offset();firstKind=cell.kind();}}
        }
        for(boolean b:offsets)if(!b)throw new IllegalArgumentException("missing offset");
        boolean[][] footprint=new boolean[SITE_WIDTH][SITE_DEPTH];
        int clear=0,problems=0;
        for(SideCell cell:sides){
            int a=cell.along()+4,lat=cell.lateral()+3;
            if(footprint[a][lat])throw new IllegalArgumentException("duplicate footprint cell");
            footprint[a][lat]=true;
            if(cell.kind()==SideKind.CLEAR_SUPPORTED)clear++;else problems++;
        }
        for(int a=0;a<SITE_WIDTH;a++)for(int lat=0;lat<SITE_DEPTH;lat++)
            if(lat!=3 && !footprint[a][lat])throw new IllegalArgumentException("missing footprint cell");
        boolean clean=routeIssues==0&&problems==0&&nearbyVillagers==0&&adjacentStations==0;
        String result=clean?"GEOMETRY_OBSERVED_REVIEW_ONLY":"HOLD_DEBUG_OBSTRUCTIONS";
        return new Report(original,normal,station,routeIssues,clear,problems,first,firstKind,
                nearbyVillagers,adjacentStations,original==9,clean,false,false,false,result);
    }
    public static int worldX(int originX,int offset) {
        if(offset<0||offset>WEST_END_OFFSET)throw new IllegalArgumentException("offset");
        return originX-offset;
    }
    public static int plannedRailCount() {return RAIL_COUNT;}
}
