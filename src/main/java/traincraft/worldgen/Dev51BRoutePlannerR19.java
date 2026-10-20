/*
 * TafariCraft - Trains / DEV51B R19
 * Pure Java 17, deterministic READ-ONLY candidate routing on R18's observed graph.
 * Not a native turnout router, train dispatcher, or proof of traversability.
 */
package traincraft.worldgen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Dev51BRoutePlannerR19 {
    private Dev51BRoutePlannerR19() { }
    public static final int MAX_ANCHORS = 16;
    public static final int MAX_PAIRS = 32;
    public static final int MAX_CHECKPOINTS = 12;

    public record Anchor(String id, Dev51BTrackGraphR18.Pos center, int horizontalReach, int verticalReach) {
        public Anchor {
            if (id == null || id.isBlank() || id.length() > 100 || center == null ||
                    horizontalReach < 0 || horizontalReach > 9 || verticalReach < 0 || verticalReach > 3)
                throw new IllegalArgumentException("Unsafe station anchor");
        }
    }
    public record Match(String id, Dev51BTrackGraphR18.Pos station, Dev51BTrackGraphR18.Pos rail,
                        String matchStatus) { }
    public record Route(String from, String to, int railLinks, int turns, int slopes,
                        long pathFingerprint, List<Dev51BTrackGraphR18.Pos> checkpoints,
                        String verdict) { }
    public record Plan(int scannedNodes, long scannedEdges, int inputAnchors, int matchedAnchors,
                       int unresolvedAnchors, boolean anchorLimitReached, boolean pairLimitReached,
                       boolean graphConsistent, boolean partialNetwork, String verdict,
                       List<Match> matches, List<Route> routes) { }

    private static final Comparator<Dev51BTrackGraphR18.Pos> ORDER = Comparator
            .comparingInt(Dev51BTrackGraphR18.Pos::x)
            .thenComparingInt(Dev51BTrackGraphR18.Pos::y)
            .thenComparingInt(Dev51BTrackGraphR18.Pos::z);
    private static long distance(Dev51BTrackGraphR18.Pos a, Dev51BTrackGraphR18.Pos b) {
        long x = (long)a.x() - b.x(), y = (long)a.y() - b.y(), z = (long)a.z() - b.z();
        return x*x + y*y*4 + z*z;
    }

    // The same reciprocal port and +/-1 elevation rules as R18. A second valid
    // neighbor for one port is ambiguous: never choose between competing rails.
    private static List<Dev51BTrackGraphR18.Pos> neighbors(
            Dev51BTrackGraphR18.Reader reader, Dev51BTrackGraphR18.Pos pos,
            Set<Dev51BTrackGraphR18.Pos> observed) {
        Dev51BTrackGraphR18.Cell cell = reader.read(pos);
        if (cell == null || cell.kind() != Dev51BTrackGraphR18.Kind.PLAIN) return List.of();
        LinkedHashSet<Dev51BTrackGraphR18.Pos> connected = new LinkedHashSet<>();
        for (Dev51BTrackGraphR18.Dir dir : Dev51BTrackGraphR18.Dir.values()) {
            if (!Dev51BTrackGraphR18.hasPort(cell.shape(), dir)) continue;
            ArrayList<Dev51BTrackGraphR18.Pos> choices = new ArrayList<>();
            for (int dy = -1; dy <= 1; dy++) {
                Dev51BTrackGraphR18.Pos candidate = pos.step(dir, dy);
                if (!observed.contains(candidate)) continue;
                Dev51BTrackGraphR18.Cell other = reader.read(candidate);
                if (other == null || other.kind() != Dev51BTrackGraphR18.Kind.PLAIN ||
                        !Dev51BTrackGraphR18.hasPort(other.shape(), dir.reverse())) continue;
                int here = pos.y() + Dev51BTrackGraphR18.portRise(cell.shape(), dir);
                int there = candidate.y() + Dev51BTrackGraphR18.portRise(other.shape(), dir.reverse());
                if (here == there) choices.add(candidate);
            }
            if (choices.size() == 1) connected.add(choices.get(0));
        }
        ArrayList<Dev51BTrackGraphR18.Pos> out = new ArrayList<>(connected);
        out.sort(ORDER);
        return out;
    }
    private static List<Dev51BTrackGraphR18.Pos> path(
            Map<Dev51BTrackGraphR18.Pos, List<Dev51BTrackGraphR18.Pos>> adjacency,
            Dev51BTrackGraphR18.Pos from, Dev51BTrackGraphR18.Pos to) {
        ArrayDeque<Dev51BTrackGraphR18.Pos> todo = new ArrayDeque<>();
        Map<Dev51BTrackGraphR18.Pos, Dev51BTrackGraphR18.Pos> previous = new HashMap<>();
        todo.add(from); previous.put(from, from);
        while (!todo.isEmpty()) {
            Dev51BTrackGraphR18.Pos current = todo.removeFirst();
            if (current.equals(to)) break;
            for (Dev51BTrackGraphR18.Pos next : adjacency.getOrDefault(current, List.of())) {
                if (!previous.containsKey(next)) {previous.put(next, current);todo.addLast(next);}
            }
        }
        if (!previous.containsKey(to)) return List.of();
        ArrayList<Dev51BTrackGraphR18.Pos> rev = new ArrayList<>();
        Dev51BTrackGraphR18.Pos at = to;
        while (!at.equals(from)) {rev.add(at);at=previous.get(at);}
        rev.add(from);Collections.reverse(rev);return rev;
    }
    private static List<Dev51BTrackGraphR18.Pos> checkpoints(List<Dev51BTrackGraphR18.Pos> entire) {
        if (entire.size() <= MAX_CHECKPOINTS) return List.copyOf(entire);
        LinkedHashSet<Dev51BTrackGraphR18.Pos> sample = new LinkedHashSet<>();
        for (int i=0;i<MAX_CHECKPOINTS;i++)
            sample.add(entire.get((int)((long)i*(entire.size()-1)/(MAX_CHECKPOINTS-1))));
        return List.copyOf(sample);
    }
    private static long fingerprint(List<Dev51BTrackGraphR18.Pos> path) {
        long h=0xcbf29ce484222325L;
        for (Dev51BTrackGraphR18.Pos p:path) {
            h=(h^p.x())*0x100000001b3L;
            h=(h^p.y())*0x100000001b3L;
            h=(h^p.z())*0x100000001b3L;
        }
        return h;
    }
    public static Plan plan(Dev51BTrackGraphR18.Reader reader, Dev51BTrackGraphR18.Result scanned,
                            List<Anchor> proposedAnchors) {
        if (reader == null || scanned == null || proposedAnchors == null ||
                proposedAnchors.size() > 512) throw new IllegalArgumentException("Unsafe routeplan inputs");
        Set<Dev51BTrackGraphR18.Pos> observed = scanned.observedNodes();
        if (observed == null || observed.size() > Dev51BTrackGraphR18.MAX_NODES ||
                observed.size() != scanned.nodes()) throw new IllegalArgumentException("Untrusted R18 scan");
        Map<Dev51BTrackGraphR18.Pos,List<Dev51BTrackGraphR18.Pos>> adjacency=new LinkedHashMap<>();
        ArrayList<Dev51BTrackGraphR18.Pos> ordered = new ArrayList<>(observed);
        ordered.sort(ORDER);
        for (Dev51BTrackGraphR18.Pos at:ordered) adjacency.put(at, neighbors(reader,at,observed));
        // Require both ends to agree. This includes the second-choice ambiguity guard.
        long degreeSum=0;
        boolean symmetric=true;
        for (Dev51BTrackGraphR18.Pos at:ordered)
            for (Dev51BTrackGraphR18.Pos next:adjacency.get(at)) {
                if (!adjacency.getOrDefault(next,List.of()).contains(at)) symmetric=false;
                else degreeSum++;
            }
        boolean consistent=symmetric && degreeSum%2==0 && degreeSum/2==scanned.edges();
        boolean partial=scanned.capReached()||scanned.clippedPorts()>0||scanned.unloadedBoundaries()>0||
                scanned.complexBoundaries()>0||scanned.ambiguousPorts()>0;
        ArrayList<Anchor> anchorList=new ArrayList<>(proposedAnchors);
        anchorList.sort(Comparator.comparing(Anchor::id)
                .thenComparing(a->a.center(),ORDER));
        int acceptedInput=Math.min(MAX_ANCHORS,anchorList.size());
        boolean anchorCap=anchorList.size()>MAX_ANCHORS;
        ArrayList<Match> matches=new ArrayList<>();
        Set<String> usedIds=new HashSet<>();
        int unresolved=0;
        for (int i=0;i<acceptedInput;i++) {
            Anchor anchor=anchorList.get(i);
            if(!usedIds.add(anchor.id())) {unresolved++;matches.add(new Match(anchor.id(),anchor.center(),null,"DUPLICATE_ID"));continue;}
            Dev51BTrackGraphR18.Pos best=null;
            long bestDistance=Long.MAX_VALUE;
            int tied=0;
            for(Dev51BTrackGraphR18.Pos at:ordered) {
                if(Math.abs((long)anchor.center().x()-at.x())>anchor.horizontalReach()||
                        Math.abs((long)anchor.center().z()-at.z())>anchor.horizontalReach()||
                        Math.abs((long)anchor.center().y()-at.y())>anchor.verticalReach())continue;
                long d=distance(anchor.center(),at);
                if(d<bestDistance){best=at;bestDistance=d;tied=1;}
                else if(d==bestDistance)tied++;
            }
            if(best==null){unresolved++;matches.add(new Match(anchor.id(),anchor.center(),null,"NO_CONNECTED_RAIL_IN_REACH"));}
            else if(tied>1){unresolved++;matches.add(new Match(anchor.id(),anchor.center(),null,"AMBIGUOUS_NEAREST_RAIL"));}
            else matches.add(new Match(anchor.id(),anchor.center(),best,
                    anchor.horizontalReach()==0&&anchor.verticalReach()==0?"EXACT_STATION_RAIL":"NEARBY_MARKER_PROVISIONAL"));
        }
        ArrayList<Route> routes=new ArrayList<>();
        int matched=0;
        for(Match m:matches)if(m.rail()!=null)matched++;
        boolean pairCap=false;
        for(int a=0;a<matches.size();a++)for(int b=a+1;b<matches.size();b++){
            Match from=matches.get(a),to=matches.get(b);
            if(from.rail()==null||to.rail()==null)continue;
            if(routes.size()>=MAX_PAIRS){pairCap=true;break;}
            List<Dev51BTrackGraphR18.Pos> walked=consistent?
                    path(adjacency,from.rail(),to.rail()):List.of();
            if(walked.isEmpty())continue;
            if(walked.size()==1){
                routes.add(new Route(from.id(),to.id(),0,0,0,fingerprint(walked),
                        List.copyOf(walked),"SAME_RAIL_NOT_A_ROUTE"));continue;
            }
            int bends=0,slopes=0;
            for(Dev51BTrackGraphR18.Pos node:walked){
                Dev51BTrackGraphR18.Cell c=reader.read(node);
                if(c!=null&&c.kind()==Dev51BTrackGraphR18.Kind.PLAIN){
                    switch(c.shape()){
                        case NORTH_EAST,NORTH_WEST,SOUTH_EAST,SOUTH_WEST->bends++;
                        case ASCENDING_EAST,ASCENDING_WEST,ASCENDING_NORTH,ASCENDING_SOUTH->slopes++;
                        default->{}
                    }
                }
            }
            routes.add(new Route(from.id(),to.id(),walked.size()-1,bends,slopes,
                    fingerprint(walked),checkpoints(walked),
                    partial?"PARTIAL_TOPOLOGY_REVIEW_ONLY":"CANDIDATE_TOPOLOGY_NOT_DRIVE_CERTIFIED"));
        }
        String verdict=!consistent?"GRAPH_INCONSISTENT_REJECTED":matched<2?"NO_STATION_PAIR":
                routes.isEmpty()?"NO_CONNECTED_STATION_PAIR":partial?"PARTIAL_NETWORK_REVIEW_ONLY":
                "CANDIDATE_ROUTES_REVIEW_ONLY";
        return new Plan(scanned.nodes(),scanned.edges(),proposedAnchors.size(),matched,unresolved,anchorCap,
                pairCap,consistent,partial,verdict,List.copyOf(matches),List.copyOf(routes));
    }
}
