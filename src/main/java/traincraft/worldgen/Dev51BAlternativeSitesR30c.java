package traincraft.worldgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * DEV51B R30c: pure Java 17 interpretation of as-built survey observations.
 * Every candidate is REVIEW_ONLY; the engine has no world access or write APIs.
 * ONLY the existing east/west straight axes are assessed. No turnouts, curved
 * connection, structures, train clearance or live destination is certified.
 */
public final class Dev51BAlternativeSitesR30c {
    private Dev51BAlternativeSitesR30c() {}
    public static final int LAST_OFFSET = 44;
    public static final int EXISTING_LAST = 8;
    public static final int STATION_HALF_LENGTH = 4;
    public static final List<Integer> CENTERS = List.of(20, 32, 40);

    public enum Direction {
        EAST(1), WEST(-1);
        public final int sign;
        Direction(int sign) { this.sign = sign; }
    }
    public enum SiteRisk { CLEAR_OBSERVED, NEARBY_VILLAGERS, SIDE_OCCUPIED, UNLOADED, OUT_OF_BOUNDS }
    public record Cell(Dev51BEastCorridorR30b.Finding finding) {
        public Cell { if (finding==null) throw new IllegalArgumentException("finding"); }
    }
    public record Site(SiteRisk risk, int nearbyVillagers, int sideOccupiedCells) {
        public Site {
            if (risk==null || nearbyVillagers<0 || sideOccupiedCells<0) throw new IllegalArgumentException("site");
        }
    }
    public record Candidate(Direction direction,int centerOffset,int endOffset,
            int existingRail,int emptySupported,int missingSupport,
            int entities,int overhead,int occupied,int fluids,int unloaded,
            int firstIssueOffset,Dev51BEastCorridorR30b.Finding firstIssue,
            Site site,int priorityScore,String reviewStatus,
            boolean authorizesWorldEdit,boolean authorizesMovement,
            boolean certifiesConnectedRoute) {}
    public record Report(List<Candidate> ranked,boolean authorizesWorldEdit,
            boolean authorizesMovement,boolean certifiesConnectedRoute) {
        public Report { ranked=List.copyOf(ranked); }
    }
    public static Report evaluate(List<Cell> east,List<Cell> west,
            Map<Direction,Map<Integer,Site>> sites) {
        if(east==null||west==null||east.size()!=LAST_OFFSET+1||west.size()!=LAST_OFFSET+1||sites==null)
            throw new IllegalArgumentException("exact 45 observed cells per direction required");
        List<Candidate> candidates=new ArrayList<>();
        for(Direction dir:Direction.values()) {
            List<Cell> cells=dir==Direction.EAST?east:west;
            Map<Integer,Site> siteMap=sites.get(dir);
            if(siteMap==null)throw new IllegalArgumentException("site map required for "+dir);
            for(int center:CENTERS) {
                Site site=siteMap.get(center);
                if(site==null) throw new IllegalArgumentException("site missing at "+dir+" "+center);
                int rails=0,empty=0,foundation=0,entities=0,overhead=0,occupied=0,fluids=0,unloaded=0;
                int first=-1;Dev51BEastCorridorR30b.Finding issue=null;
                for(int i=0;i<=center+STATION_HALF_LENGTH;i++) {
                    var f=cells.get(i).finding();
                    boolean valid=i<=EXISTING_LAST
                            ? f==Dev51BEastCorridorR30b.Finding.EXISTING_RAIL
                            : f==Dev51BEastCorridorR30b.Finding.EXISTING_RAIL
                              ||f==Dev51BEastCorridorR30b.Finding.EMPTY_WITH_SUPPORT;
                    if(!valid && first<0){first=i;issue=f;}
                    if(i<=EXISTING_LAST)continue;
                    switch(f) {
                        case EXISTING_RAIL -> rails++;
                        case EMPTY_WITH_SUPPORT -> empty++;
                        case EMPTY_WITHOUT_SUPPORT -> foundation++;
                        case ENTITY_PRESENT -> entities++;
                        case EMPTY_BLOCKED_ABOVE,EXISTING_RAIL_BLOCKED_ABOVE -> overhead++;
                        case FLUID -> fluids++;
                        case UNLOADED -> unloaded++;
                        default -> occupied++;
                    }
                }
                // Bounded, conservative comparison metric. Never represents
                // build approval or movement safety.
                int score=empty + 8*foundation + 15*overhead + 25*occupied
                    + 50*entities + 50*fluids + 100*unloaded
                    + 12*site.sideOccupiedCells() + 100*site.nearbyVillagers();
                if(first>=0 && first<=EXISTING_LAST) score+=5000;
                if(site.risk()==SiteRisk.UNLOADED || site.risk()==SiteRisk.OUT_OF_BOUNDS) score+=10000;
                if(site.risk()==SiteRisk.SIDE_OCCUPIED) score+=200;
                boolean geometryOnly=first<0 && site.risk()==SiteRisk.CLEAR_OBSERVED;
                String status=geometryOnly?"GEOMETRY_ONLY_REVIEW":"HOLD_OBSTRUCTION_OR_SITE_RISK";
                candidates.add(new Candidate(dir,center,center+STATION_HALF_LENGTH,
                    rails,empty,foundation,entities,overhead,occupied,fluids,unloaded,
                    first,issue,site,score,status,false,false,false));
            }
        }
        candidates.sort(Comparator.comparingInt(Candidate::priorityScore)
            .thenComparingInt(Candidate::centerOffset)
            .thenComparing(c->c.direction().name()));
        return new Report(candidates,false,false,false);
    }
    public static int worldX(int sourceX, Direction direction,int offset) {
        if(offset<0||offset>LAST_OFFSET)throw new IllegalArgumentException("offset");
        return sourceX+direction.sign*offset;
    }
}
