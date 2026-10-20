package traincraft.worldgen;

import java.util.ArrayList;
import java.util.List;

/**
 * R30b: read-only, load-bounded, one-level corridor assessment. This class has
 * NO Forge dependencies and never authorizes world edits or train movements.
 */
public final class Dev51BEastCorridorR30b {
    private Dev51BEastCorridorR30b() {}
    public static final int ORIGINAL_STATION_OFFSET = 0;
    public static final int EXISTING_FIRST_EXTENSION = 9;
    public static final int DESTINATION_OFFSET = 32;
    public static final int DESTINATION_HALF_WIDTH = 4;
    public static final int LAST_OFFSET = DESTINATION_OFFSET + DESTINATION_HALF_WIDTH;
    public static final int REQUIRED_OBSERVATIONS = LAST_OFFSET + 1;

    public enum Finding {
        EXISTING_RAIL, EXISTING_RAIL_BLOCKED_ABOVE,
        EMPTY_WITH_SUPPORT, EMPTY_WITHOUT_SUPPORT, EMPTY_BLOCKED_ABOVE,
        FLUID, NON_STANDARD_RAIL, NATIVE_OR_BLOCKED, UNLOADED, ENTITY_PRESENT
    }
    public record Cell(Dev51BRailSurveyR30a.Kind kind,
                       boolean twoAboveClear, boolean sturdyFoundation,
                       boolean dry, boolean entitiesClear) {
        public Cell { if (kind == null) throw new IllegalArgumentException("kind"); }
    }
    public record Range(int first, int last, Finding finding) {}
    public record Report(boolean sourceValid, int existingStraight,
            int firstMissingOffset, int lastRequiredOffset, int observed,
            int existingRailCount, int buildableEmptyCount, int blockedCount,
            int unloadedCount, int firstIssueOffset, Finding firstIssue,
            boolean corridorGeometryReady, String reason,
            List<Range> ranges,
            boolean authorizesRailWrite, boolean authorizesTrainDrive,
            boolean certifiesDestinationRoute) {
        public Report { ranges=List.copyOf(ranges); }
    }

    private static boolean rail(Dev51BRailSurveyR30a.Kind k) {
        return k == Dev51BRailSurveyR30a.Kind.NORMAL_EW
                || k == Dev51BRailSurveyR30a.Kind.STATION_EW;
    }
    public static Finding classify(Cell cell) {
        if(cell.kind()==Dev51BRailSurveyR30a.Kind.UNLOADED)return Finding.UNLOADED;
        if(!cell.entitiesClear())return Finding.ENTITY_PRESENT;
        if(!cell.dry())return Finding.FLUID;
        if(rail(cell.kind()))return cell.twoAboveClear()?Finding.EXISTING_RAIL:Finding.EXISTING_RAIL_BLOCKED_ABOVE;
        if(cell.kind()==Dev51BRailSurveyR30a.Kind.AIR) {
            if(!cell.twoAboveClear())return Finding.EMPTY_BLOCKED_ABOVE;
            return cell.sturdyFoundation()?Finding.EMPTY_WITH_SUPPORT:Finding.EMPTY_WITHOUT_SUPPORT;
        }
        if(cell.kind()==Dev51BRailSurveyR30a.Kind.OTHER_EW_RAIL
            ||cell.kind()==Dev51BRailSurveyR30a.Kind.TURN_OR_SLOPE
            ||cell.kind()==Dev51BRailSurveyR30a.Kind.OFF_LEVEL_RAIL)
            return Finding.NON_STANDARD_RAIL;
        return Finding.NATIVE_OR_BLOCKED;
    }
    private static boolean acceptableExisting(Finding finding) {
        return finding==Finding.EXISTING_RAIL;
    }
    private static boolean acceptableFuture(Finding finding) {
        return finding==Finding.EXISTING_RAIL||finding==Finding.EMPTY_WITH_SUPPORT;
    }
    public static Report inspect(List<Cell> cells) {
        if(cells == null || cells.size()!=REQUIRED_OBSERVATIONS)
            throw new IllegalArgumentException("Requires offsets 0..36");
        List<Finding> labels = new ArrayList<>();
        List<Range> runs=new ArrayList<>();
        int existing=0, fillable=0, blocked=0, unloaded=0;
        int runStart=0;
        Finding previous=null;
        int firstIssue=-1;
        Finding issue=null;
        int contiguous=0;
        for(int i=0;i<cells.size();i++) {
            Finding f=classify(cells.get(i));
            labels.add(f);
            if(i>0 && f!=previous) {
                runs.add(new Range(runStart,i-1,previous));
                runStart=i;
            }
            previous=f;
            if(i == contiguous && acceptableExisting(f))contiguous++;
            if(i>=EXISTING_FIRST_EXTENSION) {
                if(f==Finding.EXISTING_RAIL)existing++;
                else if(f==Finding.EMPTY_WITH_SUPPORT)fillable++;
                else {
                    blocked++;
                    if(f==Finding.UNLOADED)unloaded++;
                }
            }
            boolean okay=i<EXISTING_FIRST_EXTENSION?acceptableExisting(f):acceptableFuture(f);
            if(!okay && firstIssue<0) {firstIssue=i;issue=f;}
        }
        runs.add(new Range(runStart,cells.size()-1,previous));
        boolean origin=acceptableExisting(labels.get(0));
        boolean ready=firstIssue<0;
        String reason;
        if(!origin)reason="SOURCE_RAIL_NOT_OBSERVED";
        else if(firstIssue>=0 && firstIssue<EXISTING_FIRST_EXTENSION)reason="EXISTING_APPROACH_INTERRUPTED";
        else if(firstIssue>=0 && issue==Finding.UNLOADED)reason="UNLOADED_CORRIDOR_CHUNK";
        else if(firstIssue>=0 && issue==Finding.ENTITY_PRESENT)reason="ENTITY_IN_CORRIDOR";
        else if(firstIssue>=0 && issue==Finding.FLUID)reason="FLUID_IN_CORRIDOR";
        else if(firstIssue>=0 && issue==Finding.EMPTY_WITHOUT_SUPPORT)reason="MISSING_FOUNDATION";
        else if(firstIssue>=0 && (issue==Finding.EMPTY_BLOCKED_ABOVE||issue==Finding.EXISTING_RAIL_BLOCKED_ABOVE))reason="BLOCKED_HEADROOM";
        else if(firstIssue>=0 && issue==Finding.NON_STANDARD_RAIL)reason="UNSUPPORTED_RAIL_GEOMETRY";
        else if(firstIssue>=0)reason="BLOCKED_CORRIDOR";
        else reason="GEOMETRY_ONLY_READY_FOR_MANUAL_REVIEW";
        return new Report(origin,Math.max(0,contiguous-1),EXISTING_FIRST_EXTENSION,LAST_OFFSET,
                cells.size(),existing,fillable,blocked,unloaded,firstIssue,issue,ready,
                reason,runs,false,false,false);
    }
    public static String compactRanges(List<Range> ranges,int max) {
        if(max<1)throw new IllegalArgumentException("max");
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<ranges.size();i++) {
            if(i==max){sb.append(";MORE=").append(ranges.size()-max);break;}
            if(i>0)sb.append(';');
            Range r=ranges.get(i);
            sb.append(r.first()).append("..").append(r.last()).append('=').append(r.finding());
        }
        return sb.toString();
    }
}
