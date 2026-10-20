/* TafariCraft - Trains DEV51B R25. Pure Java 17 controlled movement-readiness
 * calculations. No Minecraft imports, throttle outputs, or world actions.
 */
package traincraft.worldgen;
import java.util.*;

public final class Dev51BMovementEnvelopeR25 {
    private Dev51BMovementEnvelopeR25() { }
    public static final int MAX_ROUTE_PAIRS=Dev51BRoutePlannerR19.MAX_PAIRS;
    public static final double MAX_SIM_SPEED=6.0;
    public record Braking(double testSpeedBlocksPerSec,double provedDecelBlocksPerSec2,
                          double reactionSeconds,double safetyMarginBlocks) { }
    public record Controls(boolean locomotiveObserved,boolean coachObserved,
                           boolean coupledObserved,boolean operatorPresent,
                           boolean allChunksLoaded,boolean occupancyClear,
                           boolean switchAlignmentProved,boolean signalsProved,
                           boolean brakingMeasured,boolean emergencyStopProved) { }
    public record Candidate(String from,String to,int railLinks,double stoppingDistanceBlocks,
                            boolean hypotheticalEnvelopeFits,boolean movementAuthorized,
                            List<String> blockers) { }
    public record Report(int candidates,int reviewOnly,int held,boolean movementEnabled,
                         List<Candidate> results) { }
    public static double stoppingDistance(Braking b) {
        if(b==null||!Double.isFinite(b.testSpeedBlocksPerSec())||
                !Double.isFinite(b.provedDecelBlocksPerSec2())||
                !Double.isFinite(b.reactionSeconds())||
                !Double.isFinite(b.safetyMarginBlocks())||
                b.testSpeedBlocksPerSec()<=0||b.testSpeedBlocksPerSec()>MAX_SIM_SPEED||
                b.provedDecelBlocksPerSec2()<=0||b.reactionSeconds()<0||
                b.safetyMarginBlocks()<0)
            throw new IllegalArgumentException("Invalid hypothetical braking witness");
        double stop=b.testSpeedBlocksPerSec()*b.reactionSeconds()+
                b.testSpeedBlocksPerSec()*b.testSpeedBlocksPerSec()/
                (2.0*b.provedDecelBlocksPerSec2())+b.safetyMarginBlocks();
        if(!Double.isFinite(stop)||stop>1_000_000)throw new IllegalArgumentException("Unsafe braking envelope");
        return stop;
    }
    public static Report examine(Dev51BRouteSafetyR21.Report safety,
                                 Dev51BInterlockingR24.Report interlocking,
                                 Braking hypothetical,
                                 Map<String,Controls> witnesses) {
        if(safety==null||interlocking==null||witnesses==null||safety.routes()==null||
                interlocking.decisions()==null||safety.routes().size()!=interlocking.decisions().size()||
                safety.routes().size()>MAX_ROUTE_PAIRS||witnesses.size()>MAX_ROUTE_PAIRS)
            throw new IllegalArgumentException("R25 route witnesses incomplete");
        double stopping=stoppingDistance(hypothetical);
        List<Candidate> result=new ArrayList<>();int review=0;
        for(int i=0;i<safety.routes().size();i++) {
            var r=safety.routes().get(i);var inter=interlocking.decisions().get(i);
            LinkedHashSet<String> reasons=new LinkedHashSet<>();
            if(r.from()==null||r.to()==null||r.from().equals(r.to())||
                    !Objects.equals(r.from(),inter.from())||!Objects.equals(r.to(),inter.to()))
                reasons.add("ROUTE_IDENTITY_UNVERIFIED");
            if(!safety.graphConsistent()||safety.networkPartial()||r.reasons()==null||
                    !r.reasons().isEmpty()||!"OBSERVED_STOP_CLEARANCES_REVIEW_ONLY".equals(r.verdict()))
                reasons.add("R21_SAFETY_NOT_CLEAR");
            if(!r.sourceStopObserved()||!r.destinationStopObserved())
                reasons.add("PLATFORM_STOP_APPROACH_UNVERIFIED");
            if(inter.blockers()==null||!inter.blockers().isEmpty()||inter.movementAllowed())
                reasons.add("R24_INTERLOCK_NOT_LIVE_VERIFIED");
            Controls c=witnesses.get(r.from()+"|"+r.to());
            if(c==null)c=new Controls(false,false,false,false,false,false,false,false,false,false);
            if(!c.locomotiveObserved()||!c.coachObserved()||!c.coupledObserved())
                reasons.add("CONSIST_AND_COUPLER_UNVERIFIED");
            if(!c.operatorPresent())reasons.add("SUPERVISING_OPERATOR_ABSENT");
            if(!c.allChunksLoaded())reasons.add("LIVE_CHUNKS_UNVERIFIED");
            if(!c.occupancyClear())reasons.add("LIVE_OCCUPANCY_UNVERIFIED");
            if(!c.switchAlignmentProved()||!c.signalsProved())
                reasons.add("TURNOUT_OR_SIGNAL_UNVERIFIED");
            if(!c.brakingMeasured()||!c.emergencyStopProved())
                reasons.add("BRAKING_OR_EMERGENCY_STOP_UNVERIFIED");
            boolean fits=r.links()>0 && stopping+4<=r.links() && r.observedClear()>=8;
            if(!fits)reasons.add("HYPOTHETICAL_STOP_ENVELOPE_TOO_SHORT");
            // R25 has NO permission to drive, even if hypothetical witnesses are all true.
            boolean ready=reasons.isEmpty();if(ready)review++;
            result.add(new Candidate(r.from(),r.to(),r.links(),stopping,fits,false,List.copyOf(reasons)));
        }
        return new Report(result.size(),review,result.size()-review,false,List.copyOf(result));
    }
}
