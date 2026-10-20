/* TafariCraft - Trains DEV51B R24 -- fail-closed signal/turnout audit.
 * All tests are observations or hypothetical. No redstone, switch toggles or train driving.
 */
package traincraft.worldgen;

import java.util.*;

public final class Dev51BInterlockingR24 {
    private Dev51BInterlockingR24() { }
    public enum Observation { VERIFIED_SAFE, VERIFIED_UNSAFE, UNKNOWN }
    public record Controls(Observation turnoutPosition,Observation signals,
                           Observation crossingClear,Observation braking,
                           Observation trainSeparation) { }
    public record Decision(String from,String to,boolean reviewOnly,boolean movementAllowed,
                           List<String> blockers) { }
    public record Report(int routes,int reviewOnly,int held,boolean trainMovementEnabled,
                         List<Decision> decisions) { }
    public static Report inspect(Dev51BRouteSafetyR21.Report safety,
             Dev51BReservationR23.Report reservations,
             Map<String,Controls> independentWitnesses){
        if(safety==null||reservations==null||independentWitnesses==null||
                safety.routes()==null||reservations.candidates()==null||
                safety.routes().size()!=reservations.candidates().size()||
                safety.routes().size()>Dev51BRoutePlannerR19.MAX_PAIRS||
                independentWitnesses.size()>Dev51BRoutePlannerR19.MAX_PAIRS)
            throw new IllegalArgumentException("Invalid R24 safety inputs");
        ArrayList<Decision> decisions=new ArrayList<>();int review=0;
        for(int i=0;i<safety.routes().size();i++){
            var a=safety.routes().get(i);var b=reservations.candidates().get(i);
            LinkedHashSet<String> issues=new LinkedHashSet<>();
            if(!Objects.equals(a.from(),b.from())||!Objects.equals(a.to(),b.to()))
                issues.add("ROUTE_WITNESS_MISMATCH");
            if(!safety.graphConsistent()||safety.networkPartial()||a.reasons()==null||
                    !a.reasons().isEmpty()||!"OBSERVED_STOP_CLEARANCES_REVIEW_ONLY".equals(a.verdict()))
                issues.add("R21_ROUTE_SAFETY_UNVERIFIED");
            if(!b.fullPathVerified())issues.add("R23_FULL_PATH_UNVERIFIED");
            if(!b.conflicts().isEmpty())issues.add("SIMULATED_LEASE_CONFLICT");
            Controls control=independentWitnesses.get(a.from()+"|"+a.to());
            if(control==null)control=new Controls(Observation.UNKNOWN,Observation.UNKNOWN,
                    Observation.UNKNOWN,Observation.UNKNOWN,Observation.UNKNOWN);
            check(issues,"TURNOUT_POSITION",control.turnoutPosition());
            check(issues,"SIGNAL_ASPECT",control.signals());
            check(issues,"CROSSING_CLEARANCE",control.crossingClear());
            check(issues,"BRAKING",control.braking());
            check(issues,"SEPARATION",control.trainSeparation());
            issues.add("NO_LIVE_LEASE_OR_TRACK_OCCUPANCY_PROOF");
            if(issues.size()==1)review++;
            // The engine is a read-only audit, not an interlocking actuator.
            decisions.add(new Decision(a.from(),a.to(),issues.size()==1,false,List.copyOf(issues)));
        }
        return new Report(decisions.size(),review,decisions.size()-review,false,List.copyOf(decisions));
    }
    private static void check(Set<String> issues,String label,Observation observation){
        if(observation==null||observation==Observation.UNKNOWN)issues.add(label+"_UNKNOWN");
        else if(observation==Observation.VERIFIED_UNSAFE)issues.add(label+"_UNSAFE");
    }
}
