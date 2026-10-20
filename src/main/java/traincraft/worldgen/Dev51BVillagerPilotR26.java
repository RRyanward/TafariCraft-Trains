/* TafariCraft - Trains DEV51B R26. Fail-closed railway-villager pilot
 * assignment and stop readiness. No mob AI, schedules, spawn, or movement.
 */
package traincraft.worldgen;
import java.util.*;

public final class Dev51BVillagerPilotR26 {
    private Dev51BVillagerPilotR26() { }
    public record Crew(boolean originStationLoaded,boolean destinationStationLoaded,
                       boolean originEngineerAssigned,boolean originEngineerLoaded,
                       boolean destinationEngineerAssigned,boolean destinationEngineerLoaded,
                       boolean stagedLocomotiveObserved,boolean stagedCoachObserved,
                       boolean consistsCoupled,boolean routeSlotReserved) { }
    public record Decision(String from,String to,boolean supervisedCandidate,
                           boolean automaticDriverEnabled,List<String> blockers) { }
    public record Report(int routes,int held,int supervisedCandidates,boolean automationEnabled,
                         List<Decision> decisions) { }
    public static Report assess(Dev51BMovementEnvelopeR25.Report movement,
                                Dev51BInterlockingR24.Report interlocking,
                                Map<String,Crew> crewAtStation) {
        if(movement==null||interlocking==null||crewAtStation==null||
                movement.results()==null||interlocking.decisions()==null||
                movement.results().size()!=interlocking.decisions().size()||
                movement.results().size()>Dev51BRoutePlannerR19.MAX_PAIRS||
                crewAtStation.size()>Dev51BRoutePlannerR19.MAX_PAIRS)
            throw new IllegalArgumentException("R26 invalid readiness witnesses");
        List<Decision> decisions=new ArrayList<>();int maybe=0;
        for(int i=0;i<movement.results().size();i++){
            var m=movement.results().get(i);var lock=interlocking.decisions().get(i);
            LinkedHashSet<String> issues=new LinkedHashSet<>();
            if(!Objects.equals(m.from(),lock.from())||!Objects.equals(m.to(),lock.to()))
                issues.add("INTERLOCK_ROUTE_IDENTITY_MISMATCH");
            if(m.blockers()==null||!m.blockers().isEmpty()||m.movementAuthorized()||movement.movementEnabled())
                issues.add("R25_MOTION_GUARDS_UNSATISFIED");
            if(lock.blockers()==null||!lock.blockers().isEmpty()||lock.movementAllowed()||interlocking.trainMovementEnabled())
                issues.add("R24_NO_LIVE_INTERLOCK_OR_LEASE");
            Crew crew=crewAtStation.get(m.from());
            if(crew==null)issues.add("CREW_AND_STAGED_SERVICE_UNKNOWN");
            else{
                if(!crew.originStationLoaded()||!crew.destinationStationLoaded())issues.add("STATION_NOT_LOADED");
                if(!crew.originEngineerAssigned()||!crew.destinationEngineerAssigned())issues.add("ENGINEER_NOT_ASSIGNED");
                if(!crew.originEngineerLoaded()||!crew.destinationEngineerLoaded())issues.add("ENGINEER_NOT_LOADED");
                if(!crew.stagedLocomotiveObserved()||!crew.stagedCoachObserved()||!crew.consistsCoupled())
                    issues.add("STAGED_CONSIST_UNVERIFIED");
                if(!crew.routeSlotReserved())issues.add("PERSISTENT_ROUTE_RESERVATION_MISSING");
            }
            boolean supervised=issues.isEmpty();if(supervised)maybe++;
            // Explicit safety gate. Never causes a driver, engine, or crew to act.
            decisions.add(new Decision(m.from(),m.to(),supervised,false,List.copyOf(issues)));
        }
        return new Report(decisions.size(),decisions.size()-maybe,maybe,false,List.copyOf(decisions));
    }
}
