package traincraft.worldgen;

import java.util.ArrayList;
import java.util.List;

/**
 * R30a: single-level, loaded-only, read-only rail-cell survey. This is NOT a
 * connectivity, native switch, train braking, or dispatch authority.
 * Input cells are already observed by the Forge adapter; no world access here.
 */
public final class Dev51BRailSurveyR30a {
    private Dev51BRailSurveyR30a() {}
    public static final int RADIUS = 96;
    public static final int MIN_SECOND_STOP_OFFSET = 24;
    public static final int NINE_RAIL_HALF_WIDTH = 4;
    public enum Kind {
        NORMAL_EW, STATION_EW, OTHER_EW_RAIL, TURN_OR_SLOPE,
        NATIVE_OR_SPECIAL, OFF_LEVEL_RAIL, AIR, BLOCKED, UNLOADED
    }
    public record Cell(Kind kind, boolean clearTwoAbove, boolean solidFoundation) {
        public Cell {
            if (kind == null) throw new IllegalArgumentException("null rail cell");
        }
    }
    public record Span(int firstOffset, int lastOffset, Kind kind) {}
    public record Summary(String side, int observedCount, int continuousStraight,
            int firstBarrierOffset, Kind firstBarrier, int firstGeometryCandidate,
            List<Span> spans, boolean authorizesDrive, boolean authorizesWrite) {
        public Summary { spans = List.copyOf(spans); }
    }
    private static boolean isUnbroken(Kind k) {
        return k == Kind.NORMAL_EW || k == Kind.STATION_EW;
    }
    public static Summary inspect(String side, List<Cell> cells) {
        if (!"EAST".equals(side) && !"WEST".equals(side))
            throw new IllegalArgumentException("Side must be EAST/WEST");
        if (cells == null || cells.isEmpty() || cells.size() > RADIUS + 1)
            throw new IllegalArgumentException("Cells must include origin, max 96 offsets");
        List<Span> spans = new ArrayList<>();
        Kind previous = cells.get(0).kind();
        int runStart=0;
        for (int i=1; i<cells.size(); i++) {
            Kind next=cells.get(i).kind();
            if (next != previous) {
                spans.add(new Span(runStart,i-1,previous));
                runStart=i;
                previous=next;
            }
        }
        spans.add(new Span(runStart,cells.size()-1,previous));
        int continuous=0;
        // Counts only rails after the source center at SAME Y and SAME Z.
        if (isUnbroken(cells.get(0).kind())) {
            while (continuous+1<cells.size() && isUnbroken(cells.get(continuous+1).kind()))
                continuous++;
        }
        int firstBarrier=continuous+1<cells.size()?continuous+1:-1;
        Kind why=firstBarrier<0?null:cells.get(firstBarrier).kind();
        int candidate=-1;
        for (int i=MIN_SECOND_STOP_OFFSET;
                i+NINE_RAIL_HALF_WIDTH<=continuous && i+NINE_RAIL_HALF_WIDTH<cells.size(); i++) {
            boolean possible=true;
            for (int j=i-NINE_RAIL_HALF_WIDTH;j<=i+NINE_RAIL_HALF_WIDTH;j++) {
                Cell c=cells.get(j);
                if(c.kind()!=Kind.NORMAL_EW || !c.clearTwoAbove() || !c.solidFoundation()) {
                    possible=false;
                    break;
                }
            }
            if (possible) { candidate=i; break; }
        }
        return new Summary(side,cells.size()-1,continuous,firstBarrier,why,candidate,
                spans,false,false);
    }
    public static String compactSpans(List<Span> spans, int limit) {
        if(limit < 1)throw new IllegalArgumentException("limit");
        StringBuilder s=new StringBuilder();
        int n=0;
        for(Span run:spans) {
            if(n++ >= limit) {s.append(";MORE=").append(spans.size()-limit); break;}
            if(s.length()>0)s.append(';');
            s.append(run.firstOffset()).append("..").append(run.lastOffset())
                .append('=').append(run.kind());
        }
        return s.toString();
    }
}
