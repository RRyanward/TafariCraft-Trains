/* TafariCraft - Trains / DEV51B R21
 * Pure Java 17 read-only route-safety observation. No train movement certification.
 * R19 full path is reconstructed, every connection independently cross-checked.
 */
package traincraft.worldgen;

import java.util.*;

public final class Dev51BRouteSafetyR21 {
    private Dev51BRouteSafetyR21() { }
    public enum Witness { CLEAR, BLOCKED, UNKNOWN, NATIVE_OR_DYNAMIC }
    @FunctionalInterface public interface WitnessReader {
        Witness inspect(Dev51BTrackGraphR18.Pos rail);
    }
    public record CheckedRoute(String from,String to,int links,int turns,int slopes,
                               int observedClear,int blocked,int unknown,int nativeCells,
                               boolean sourceStopObserved,boolean destinationStopObserved,
                               String verdict,List<String> reasons) { }
    public record Report(int candidates,int routeReviewOnly,int blocked,int totalUnknown,
                         boolean graphConsistent,boolean networkPartial,
                         boolean driveCertified,String verdict,List<CheckedRoute> routes) { }
    private static final Comparator<Dev51BTrackGraphR18.Pos> POS_ORDER=Comparator
            .comparingInt(Dev51BTrackGraphR18.Pos::x)
            .thenComparingInt(Dev51BTrackGraphR18.Pos::y)
            .thenComparingInt(Dev51BTrackGraphR18.Pos::z);
    private static boolean straight(Dev51BTrackGraphR18.Shape shape){
        return shape==Dev51BTrackGraphR18.Shape.EAST_WEST || shape==Dev51BTrackGraphR18.Shape.NORTH_SOUTH;
    }
    private static List<Dev51BTrackGraphR18.Pos> neighbors(
            Dev51BTrackGraphR18.Reader reader,Dev51BTrackGraphR18.Pos at,
            Set<Dev51BTrackGraphR18.Pos> observed){
        Dev51BTrackGraphR18.Cell cell=reader.read(at);
        if(cell==null||cell.kind()!=Dev51BTrackGraphR18.Kind.PLAIN)return List.of();
        TreeSet<Dev51BTrackGraphR18.Pos> links=new TreeSet<>(POS_ORDER);
        for(Dev51BTrackGraphR18.Dir direction:Dev51BTrackGraphR18.Dir.values()){
            if(!Dev51BTrackGraphR18.hasPort(cell.shape(),direction))continue;
            Dev51BTrackGraphR18.Pos unique=null;
            int matched=0;
            for(int rise=-1;rise<=1;rise++){
                Dev51BTrackGraphR18.Pos otherPos=at.step(direction,rise);
                if(!observed.contains(otherPos))continue;
                Dev51BTrackGraphR18.Cell other=reader.read(otherPos);
                if(other==null||other.kind()!=Dev51BTrackGraphR18.Kind.PLAIN||
                        !Dev51BTrackGraphR18.hasPort(other.shape(),direction.reverse()))continue;
                if(at.y()+Dev51BTrackGraphR18.portRise(cell.shape(),direction)!=
                        otherPos.y()+Dev51BTrackGraphR18.portRise(other.shape(),direction.reverse()))continue;
                matched++;unique=otherPos;
            }
            if(matched==1)links.add(unique);
        }
        return List.copyOf(links);
    }
    private static List<Dev51BTrackGraphR18.Pos> shortest(
            Map<Dev51BTrackGraphR18.Pos,List<Dev51BTrackGraphR18.Pos>> adjacency,
            Dev51BTrackGraphR18.Pos from,Dev51BTrackGraphR18.Pos to){
        ArrayDeque<Dev51BTrackGraphR18.Pos> queue=new ArrayDeque<>();
        Map<Dev51BTrackGraphR18.Pos,Dev51BTrackGraphR18.Pos> parent=new HashMap<>();
        parent.put(from,from);queue.add(from);
        while(!queue.isEmpty()){
            var here=queue.removeFirst();
            if(here.equals(to))break;
            for(var next:adjacency.getOrDefault(here,List.of())){
                if(parent.putIfAbsent(next,here)==null)queue.addLast(next);
            }
        }
        if(!parent.containsKey(to))return List.of();
        ArrayList<Dev51BTrackGraphR18.Pos> reversed=new ArrayList<>();
        var here=to;
        while(!here.equals(from)){reversed.add(here);here=parent.get(here);}
        reversed.add(from);Collections.reverse(reversed);return reversed;
    }
    private static long fingerprint(List<Dev51BTrackGraphR18.Pos> path){
        long h=0xcbf29ce484222325L;
        for(var p:path){h=(h^p.x())*0x100000001b3L;
            h=(h^p.y())*0x100000001b3L;
            h=(h^p.z())*0x100000001b3L;}
        return h;
    }
    private static boolean approach(List<Dev51BTrackGraphR18.Pos> path,
                                    Dev51BTrackGraphR18.Reader reader,boolean first){
        // Require four consecutive flat, straight rail cells at each stopping end.
        if(path.size()<8)return false;
        for(int step=0;step<4;step++){
            var at=path.get(first?step:path.size()-1-step);
            var cell=reader.read(at);
            if(cell==null||cell.kind()!=Dev51BTrackGraphR18.Kind.PLAIN||!straight(cell.shape()))return false;
            if(step>0){
                var prior=path.get(first?step-1:path.size()-step);
                if(at.y()!=prior.y())return false;
                if(Math.abs(at.x()-prior.x())+Math.abs(at.z()-prior.z())!=1)return false;
                var priorCell=reader.read(prior);
                if(priorCell==null||priorCell.shape()!=cell.shape())return false;
            }
        }
        return true;
    }
    public static Report inspect(Dev51BTrackGraphR18.Reader reader,
                                 Dev51BTrackGraphR18.Result graph,
                                 Dev51BRoutePlannerR19.Plan plan,
                                 WitnessReader witness){
        if(reader==null||graph==null||plan==null||witness==null||graph.observedNodes()==null||
                graph.observedNodes().size()!=graph.nodes()||graph.nodes()>Dev51BTrackGraphR18.MAX_NODES||
                plan.routes()==null||plan.routes().size()>Dev51BRoutePlannerR19.MAX_PAIRS)
            throw new IllegalArgumentException("Unsafe R21 inputs");
        Set<Dev51BTrackGraphR18.Pos> observed=graph.observedNodes();
        Map<Dev51BTrackGraphR18.Pos,List<Dev51BTrackGraphR18.Pos>> adjacency=new HashMap<>();
        for(var pos:observed)adjacency.put(pos,neighbors(reader,pos,observed));
        long directed=0;
        boolean consistent=plan.graphConsistent()&&plan.scannedNodes()==graph.nodes()&&
                plan.scannedEdges()==graph.edges();
        for(var entry:adjacency.entrySet())for(var next:entry.getValue()){
            if(!adjacency.getOrDefault(next,List.of()).contains(entry.getKey()))consistent=false;
            else directed++;
        }
        if(directed%2!=0||directed/2!=graph.edges())consistent=false;
        boolean partial=plan.partialNetwork()||plan.anchorLimitReached()||plan.pairLimitReached()||
                plan.unresolvedAnchors()>0||graph.capReached()||graph.clippedPorts()>0||
                graph.unloadedBoundaries()>0||graph.complexBoundaries()>0||graph.ambiguousPorts()>0;
        Map<String,Dev51BRoutePlannerR19.Match> stations=new HashMap<>();
        for(var station:plan.matches())stations.put(station.id(),station);
        List<CheckedRoute> checked=new ArrayList<>();
        int review=0,blocked=0,totalUnknown=0;
        for(var route:plan.routes()){
            LinkedHashSet<String> issues=new LinkedHashSet<>();
            var from=stations.get(route.from());var to=stations.get(route.to());
            if(!consistent)issues.add("GRAPH_INCONSISTENT");
            if(partial)issues.add("NETWORK_PARTIAL_OR_AMBIGUOUS");
            if(from==null||to==null||from.rail()==null||to.rail()==null||
                    !observed.contains(from.rail())||!observed.contains(to.rail()))issues.add("UNRESOLVED_STATION_ANCHOR");
            if(from!=null&&!"EXACT_STATION_RAIL".equals(from.matchStatus()))issues.add("PROVISIONAL_SOURCE_MARKER");
            if(to!=null&&!"EXACT_STATION_RAIL".equals(to.matchStatus()))issues.add("PROVISIONAL_DESTINATION_MARKER");
            var path=from!=null&&to!=null&&from.rail()!=null&&to.rail()!=null&&
                    observed.contains(from.rail())&&observed.contains(to.rail())?
                    shortest(adjacency,from.rail(),to.rail()):List.<Dev51BTrackGraphR18.Pos>of();
            if(path.size()<2)issues.add("NO_DISTINCT_CONNECTED_ROUTE");
            if(!path.isEmpty()&&(path.size()-1!=route.railLinks()||fingerprint(path)!=route.pathFingerprint()))
                issues.add("R19_PATH_FINGERPRINT_MISMATCH");
            boolean startStation=false,endStation=false;
            if(!path.isEmpty()){
                var start=reader.read(path.get(0));
                var end=reader.read(path.get(path.size()-1));
                startStation=start!=null&&start.station();endStation=end!=null&&end.station();
            }
            if(!startStation)issues.add("SOURCE_STATION_RAIL_NOT_CONFIRMED");
            if(!endStation)issues.add("DESTINATION_STATION_RAIL_NOT_CONFIRMED");
            boolean startApproach=approach(path,reader,true),endApproach=approach(path,reader,false);
            if(!startApproach)issues.add("SOURCE_STOP_APPROACH_UNVERIFIED");
            if(!endApproach)issues.add("DESTINATION_STOP_APPROACH_UNVERIFIED");
            int clear=0,bad=0,unknown=0,nativeCount=0,turns=0,slopes=0;
            for(var node:path){
                var cell=reader.read(node);
                if(cell==null||cell.kind()!=Dev51BTrackGraphR18.Kind.PLAIN){issues.add("ROUTE_RAIL_CHANGED");continue;}
                if(!straight(cell.shape())){
                    switch(cell.shape()){
                        case ASCENDING_NORTH,ASCENDING_SOUTH,ASCENDING_EAST,ASCENDING_WEST->slopes++;
                        default->turns++;
                    }
                }
                var check=witness.inspect(node);
                if(check==null)check=Witness.UNKNOWN;
                switch(check){case CLEAR->clear++;case BLOCKED->bad++;
                    case UNKNOWN->unknown++;case NATIVE_OR_DYNAMIC->nativeCount++;}
            }
            if(!path.isEmpty()&&(turns!=route.turns()||slopes!=route.slopes()))issues.add("ROUTE_SHAPE_COUNT_MISMATCH");
            if(bad>0)issues.add("PHYSICAL_CLEARANCE_BLOCKED");
            if(unknown>0)issues.add("PHYSICAL_CLEARANCE_UNKNOWN");
            if(nativeCount>0)issues.add("NATIVE_OR_DYNAMIC_RAIL_UNVERIFIED");
            String verdict=issues.isEmpty()?"OBSERVED_STOP_CLEARANCES_REVIEW_ONLY":"HOLD_FOR_MANUAL_REVIEW";
            if(issues.isEmpty())review++;else blocked++;
            totalUnknown+=unknown+nativeCount;
            checked.add(new CheckedRoute(route.from(),route.to(),path.isEmpty()?0:path.size()-1,
                    turns,slopes,clear,bad,unknown,nativeCount,startApproach&&startStation,
                    endApproach&&endStation,verdict,List.copyOf(issues)));
        }
        return new Report(checked.size(),review,blocked,totalUnknown,consistent,partial,
                false,checked.isEmpty()?"NO_STATION_ROUTE_TO_VALIDATE":
                    blocked>0?"ROUTES_REQUIRE_MANUAL_REVIEW_NO_DISPATCH":
                            "OBSERVED_ROUTES_ONLY_NO_DISPATCH",List.copyOf(checked));
    }
}
