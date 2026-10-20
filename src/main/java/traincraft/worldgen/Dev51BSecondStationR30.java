package traincraft.worldgen;

/** R30 recorded test-stop site assessor. Never authorizes train travel or dispatch. */
public final class Dev51BSecondStationR30 {
    private Dev51BSecondStationR30() {}
    public static final int EAST_OFFSET=32;
    public static final int STRAIGHT_HALF_WIDTH=4;
    public static final int MAX_NEARBY_ENTITY_X=10;
    public static final int MAX_NEARBY_ENTITY_Z=3;
    public record Result(String verdict,String reason,boolean mayPlace,boolean driveAuthorized) {}
    public static Result check(boolean disposable,boolean overworld,boolean player,
            int stationCount,boolean existingR30Stop,boolean sourceRecorded,
            boolean networkRecorded,boolean stationRailVerified,boolean routeWithinR29,
            boolean siteLoaded,boolean allNineNormalEW,boolean airColumnClear,
            boolean foundationSolid,boolean entityFree,boolean noOtherStationNearby) {
        if(!disposable)return hold("DISPOSABLE_MARKER_REQUIRED");
        if(!overworld)return hold("OVERWORLD_ONLY");
        if(!player)return hold("PLAYER_REQUIRED");
        if(existingR30Stop)return hold("TEST_STOP_ALREADY_RECORDED");
        if(stationCount!=1)return hold("REQUIRES_EXACTLY_ONE_RECORDED_STATION");
        if(!sourceRecorded)return hold("SOURCE_NOT_RECORDED");
        if(!networkRecorded)return hold("SOURCE_NETWORK_NOT_RECORDED");
        if(!stationRailVerified)return hold("SOURCE_STATION_RAIL_MISMATCH");
        if(!routeWithinR29)return hold("DESTINATION_OUTSIDE_R29_SCAN");
        if(!siteLoaded)return hold("SITE_CHUNKS_NOT_LOADED");
        if(!allNineNormalEW)return hold("SITE_NOT_NINE_STRAIGHT_NORMAL_RAILS");
        if(!airColumnClear)return hold("SITE_COLUMN_OBSTRUCTED");
        if(!foundationSolid)return hold("SITE_FOUNDATION_MISSING");
        if(!entityFree)return hold("SITE_ENTITY_PRESENT");
        if(!noOtherStationNearby)return hold("NEAR_OTHER_STATION");
        // Physical marker permission is not route approval. R29 must be rerun.
        return new Result("READY_FOR_SINGLE_RAIL_TEST_STOP","MANUAL_CONFIRM_REQUIRED",true,false);
    }
    public static Result hold(String reason){return new Result("HOLD",reason,false,false);}
    public static boolean canUndo(boolean disposable,boolean overworld,boolean player,
            boolean provenanceRecorded,boolean testStopStillRecorded,boolean exactEWStationRail,
            boolean noServiceOrStaff,boolean noTrainOrEntityNearby,boolean siteLoaded) {
        return disposable && overworld && player && provenanceRecorded && testStopStillRecorded
                && exactEWStationRail && noServiceOrStaff && noTrainOrEntityNearby && siteLoaded;
    }
}
