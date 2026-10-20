/* TafariCraft - Trains DEV51B R23 -- deterministic reservation simulation.
 * Reconstructs complete R19 paths; no saved leases, world access, or dispatch authority.
 */
package traincraft.worldgen;

import java.util.*;

public final class Dev51BReservationR23 {
    private Dev51BReservationR23() { }
    public static final int MAX_LEASES = 32;
    public record Candidate(String from,String to,boolean fullPathVerified,int fullPathCells,
                            List<String> conflicts,List<String> blockers,boolean tokenIssued) { }
    public record Report(int pairs,int reconstructed,int conflicted,int held,
                         boolean leasesIssued,boolean occupancyCertified,List<Candidate> candidates) { }
    private static final Comparator<Dev51BTrackGraphR18.Pos> ORDER=Comparator
            .comparingInt(Dev51BTrackGraphR18.Pos::x).thenComparingInt(Dev51BTrackGraphR18.Pos::y)
            .thenComparingInt(Dev51BTrackGraphR18.Pos::z);
    private static List<Dev51BTrackGraphR18.Pos> connected(Dev51BTrackGraphR18.Reader reader,
            Dev51BTrackGraphR18.Pos at,Set<Dev51BTrackGraphR18.Pos> observed){
        var cell=reader.read(at);
        if(cell==null||cell.kind()!=Dev51BTrackGraphR18.Kind.PLAIN)return List.of();
        TreeSet<Dev51BTrackGraphR18.Pos> links=new TreeSet<>(ORDER);
        for(var dir:Dev51BTrackGraphR18.Dir.values()){
            if(!Dev51BTrackGraphR18.hasPort(cell.shape(),dir))continue;
            Dev51BTrackGraphR18.Pos found=null;
            int matches=0;
            for(int dy=-1;dy<=1;dy++){
                var otherPos=at.step(dir,dy);
                if(!observed.contains(otherPos))continue;
                var other=reader.read(otherPos);
                if(other==null||other.kind()!=Dev51BTrackGraphR18.Kind.PLAIN||
                        !Dev51BTrackGraphR18.hasPort(other.shape(),dir.reverse()))continue;
                if(at.y()+Dev51BTrackGraphR18.portRise(cell.shape(),dir)!=
                   otherPos.y()+Dev51BTrackGraphR18.portRise(other.shape(),dir.reverse()))continue;
                found=otherPos;matches++;
            }
            if(matches==1)links.add(found);
        }
        return List.copyOf(links);
    }
    private static List<Dev51BTrackGraphR18.Pos> fullPath(
          Map<Dev51BTrackGraphR18.Pos,List<Dev51BTrackGraphR18.Pos>> adj,
          Dev51BTrackGraphR18.Pos start,Dev51BTrackGraphR18.Pos end){
        if(start==null||end==null||!adj.containsKey(start)||!adj.containsKey(end)||start.equals(end))
            return List.of();
        Map<Dev51BTrackGraphR18.Pos,Dev51BTrackGraphR18.Pos> parent=new HashMap<>();
        ArrayDeque<Dev51BTrackGraphR18.Pos> pending=new ArrayDeque<>();
        parent.put(start,start);pending.add(start);
        while(!pending.isEmpty()){
            var here=pending.removeFirst();
            if(here.equals(end))break;
            for(var next:adj.getOrDefault(here,List.of())){
                if(!adj.getOrDefault(next,List.of()).contains(here))continue;
                if(parent.putIfAbsent(next,here)==null)pending.addLast(next);
            }
        }
        if(!parent.containsKey(end))return List.of();
        ArrayList<Dev51BTrackGraphR18.Pos> reverse=new ArrayList<>();
        var current=end;
        while(!current.equals(start)){reverse.add(current);current=parent.get(current);}
        reverse.add(start);Collections.reverse(reverse);return List.copyOf(reverse);
    }
    private static long fingerprint(List<Dev51BTrackGraphR18.Pos> path){
        long h=0xcbf29ce484222325L;
        for(var p:path){h=(h^p.x())*0x100000001b3L;
            h=(h^p.y())*0x100000001b3L;h=(h^p.z())*0x100000001b3L;}
        return h;
    }
    public static Report inspect(Dev51BTrackGraphR18.Reader reader,Dev51BTrackGraphR18.Result graph,
             Dev51BRoutePlannerR19.Plan plan,Dev51BRouteSafetyR21.Report safety,
             Map<String,Set<Dev51BTrackGraphR18.Pos>> existingSimulatedClaims){
        if(reader==null||graph==null||plan==null||safety==null||existingSimulatedClaims==null||
                graph.observedNodes()==null||graph.nodes()!=graph.observedNodes().size()||
                graph.nodes()>Dev51BTrackGraphR18.MAX_NODES||plan.matches()==null||
                plan.routes()==null||safety.routes()==null||plan.routes().size()>Dev51BRoutePlannerR19.MAX_PAIRS||
                plan.routes().size()!=safety.routes().size()||existingSimulatedClaims.size()>MAX_LEASES)
            throw new IllegalArgumentException("Invalid R23 reservation witness");
        for(var e:existingSimulatedClaims.entrySet()){
            if(e.getKey()==null||e.getKey().isBlank()||e.getValue()==null||e.getValue().isEmpty()||
                    e.getValue().size()>Dev51BTrackGraphR18.MAX_NODES||e.getValue().stream().anyMatch(Objects::isNull))
                throw new IllegalArgumentException("Invalid R23 simulated existing claim");
        }
        Map<Dev51BTrackGraphR18.Pos,List<Dev51BTrackGraphR18.Pos>> adj=new HashMap<>();
        for(var node:graph.observedNodes())adj.put(node,connected(reader,node,graph.observedNodes()));
        Map<String,Dev51BRoutePlannerR19.Match> anchors=new HashMap<>();
        for(var match:plan.matches()){
            if(match.id()==null||anchors.putIfAbsent(match.id(),match)!=null)
                throw new IllegalArgumentException("Duplicate station anchor");
        }
        List<List<Dev51BTrackGraphR18.Pos>> complete=new ArrayList<>();
        List<LinkedHashSet<String>> blockers=new ArrayList<>();
        for(int i=0;i<plan.routes().size();i++){
            var p=plan.routes().get(i);
            var s=safety.routes().get(i);
            var reasons=new LinkedHashSet<String>();
            if(!Objects.equals(p.from(),s.from())||!Objects.equals(p.to(),s.to()))
                reasons.add("SAFETY_ROUTE_ORDER_MISMATCH");
            if(!plan.graphConsistent()||!safety.graphConsistent()||plan.partialNetwork()||
                    safety.networkPartial()||graph.capReached()||graph.unloadedBoundaries()>0||
                    graph.ambiguousPorts()>0||graph.complexBoundaries()>0||graph.clippedPorts()>0)
                reasons.add("NETWORK_PARTIAL_OR_DYNAMIC");
            if(s.reasons()==null||!s.reasons().isEmpty()||
                    !"OBSERVED_STOP_CLEARANCES_REVIEW_ONLY".equals(s.verdict()))
                reasons.add("R21_SAFETY_HOLD");
            var start=anchors.get(p.from());var end=anchors.get(p.to());
            var path=fullPath(adj,start==null?null:start.rail(),end==null?null:end.rail());
            if(path.isEmpty()||path.size()-1!=p.railLinks()||fingerprint(path)!=p.pathFingerprint()){
                reasons.add("COMPLETE_ROUTE_FINGERPRINT_UNVERIFIED");path=List.of();
            }
            complete.add(path);blockers.add(reasons);
        }
        var conflicts=new ArrayList<LinkedHashSet<String>>();
        for(int i=0;i<plan.routes().size();i++)conflicts.add(new LinkedHashSet<>());
        for(int i=0;i<complete.size();i++){
            if(complete.get(i).isEmpty())continue;
            var one=new HashSet<>(complete.get(i));
            for(int j=i+1;j<complete.size();j++){
                if(complete.get(j).isEmpty())continue;
                if(!Collections.disjoint(one,complete.get(j))){
                    conflicts.get(i).add(plan.routes().get(j).from()+"->"+plan.routes().get(j).to());
                    conflicts.get(j).add(plan.routes().get(i).from()+"->"+plan.routes().get(i).to());
                }
            }
            for(var existing:existingSimulatedClaims.entrySet()){
                if(!Collections.disjoint(one,existing.getValue()))
                    conflicts.get(i).add("EXISTING:"+existing.getKey());
            }
        }
        ArrayList<Candidate> decisions=new ArrayList<>();int reconstructed=0,conflicted=0;
        for(int i=0;i<complete.size();i++){
            var route=plan.routes().get(i);
            boolean verified=!complete.get(i).isEmpty()&&blockers.get(i).isEmpty();
            if(verified)reconstructed++;
            if(!conflicts.get(i).isEmpty()){conflicted++;blockers.get(i).add("SIMULATED_ROUTE_RESERVATION_CONFLICT");}
            blockers.get(i).add("NO_PERSISTED_RESERVATION_LEASE");
            blockers.get(i).add("LIVE_OCCUPANCY_UNVERIFIED");
            decisions.add(new Candidate(route.from(),route.to(),verified,complete.get(i).size(),
                    List.copyOf(conflicts.get(i)),List.copyOf(blockers.get(i)),false));
        }
        return new Report(decisions.size(),reconstructed,conflicted,decisions.size(),false,false,
                List.copyOf(decisions));
    }
}
