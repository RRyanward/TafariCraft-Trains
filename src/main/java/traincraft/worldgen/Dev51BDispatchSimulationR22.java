/* TafariCraft – Trains DEV51B R22
 * Java 17, pure deterministic, fail-closed DISPATCH SIMULATION. NO actuators.
 * This class has no Minecraft/Forge references and cannot change a world.
 */
package traincraft.worldgen;

import java.util.*;

public final class Dev51BDispatchSimulationR22 {
    private Dev51BDispatchSimulationR22() { }
    public enum Occupancy { CLEAR_OBSERVED, OCCUPIED, UNKNOWN }
    public record Station(boolean savedService,boolean loaded,boolean stationRailObserved,
                          boolean engineerRecorded,boolean engineerLoaded) { }
    public record Consist(String locomotive,String coach,int plannedCoaches,int stagedCoaches,
                          boolean locomotiveRegistered,boolean coachRegistered,
                          boolean locomotiveLoadedMatches,boolean coachLoadedMatches,
                          boolean couplingProven) { }
    public record Controls(boolean turnoutRoutingProven,boolean signalsProven,
                           boolean brakingProven,boolean collisionSeparationProven) { }
    public record Decision(String from,String to,String verdict,boolean dispatchApproved,
                           boolean simulationReadinessOnly,List<String> blockers) { }
    public record Report(int candidatePairs,int reviewOnly,int held,int unknownOccupancy,
                         boolean dispatchEnabled,String verdict,List<Decision> decisions) { }

    private static final Set<String> VALID_PROFILES=Set.of(
        "traincraft:locomotive_diesel_gp7|traincraft:passenger_coach_blue",
        "traincraft:locomotive_diesel_cd742|traincraft:passenger_coach_blue",
        "traincraft:locomotive_steam_small|traincraft:passenger_coach_blue",
        "traincraft:locomotive_electric_br_e69|traincraft:passenger_coach_blue",
        "traincraft:locomotive_steam_gs4|traincraft:gs4_passenger_car"
    );
    public static boolean knownRoster(String locomotive,String coach){
        return locomotive!=null&&coach!=null&&VALID_PROFILES.contains(locomotive+"|"+coach);
    }
    public static Report evaluate(Dev51BRouteSafetyR21.Report safety,
               Map<String,Station> stationByAnchor,
               Map<String,Consist> consistBySource,
               Map<String,Occupancy> occupancyByPair,
               Controls controls) {
        if(safety==null||safety.routes()==null||stationByAnchor==null||
           consistBySource==null||occupancyByPair==null||controls==null||
           safety.routes().size()>Dev51BRoutePlannerR19.MAX_PAIRS)
            throw new IllegalArgumentException("Unsafe R22 dispatch inputs");
        ArrayList<Decision> decisions=new ArrayList<>();
        int review=0,held=0,unknown=0;
        for(var route:safety.routes()){
            LinkedHashSet<String> why=new LinkedHashSet<>();
            String from=route.from(),to=route.to();
            if(from==null||to==null||from.isBlank()||to.isBlank()||from.equals(to))
                why.add("INVALID_STATION_PAIR");
            if(!safety.graphConsistent()||safety.networkPartial()||route.reasons()==null||
               !route.reasons().isEmpty()||!"OBSERVED_STOP_CLEARANCES_REVIEW_ONLY".equals(route.verdict()))
                why.add("R21_ROUTE_NOT_CLEAR_FOR_REVIEW");
            if(!route.sourceStopObserved()||!route.destinationStopObserved())
                why.add("STATION_STOP_UNVERIFIED");
            Station origin=stationByAnchor.get(from),destination=stationByAnchor.get(to);
            checkStation(origin,"SOURCE",why);
            checkStation(destination,"DESTINATION",why);
            Consist consist=consistBySource.get(from);
            if(consist==null) why.add("NO_STAGED_CONSIST_WITNESS");
            else {
                if(!knownRoster(consist.locomotive(),consist.coach()))why.add("UNSUPPORTED_ROSTER_PAIR");
                if(consist.plannedCoaches()<1||consist.plannedCoaches()>3)why.add("INVALID_COACH_COUNT");
                if(consist.stagedCoaches()!=consist.plannedCoaches())why.add("STAGED_COACH_COUNT_MISMATCH");
                if(!consist.locomotiveRegistered()||!consist.coachRegistered())why.add("ROSTER_ENTITY_TYPE_UNREGISTERED");
                if(!consist.locomotiveLoadedMatches()||!consist.coachLoadedMatches())why.add("STAGED_ENTITY_UNLOADED_OR_TYPE_MISMATCH");
                if(!consist.couplingProven())why.add("COUPLING_NOT_PROVEN");
            }
            Occupancy occupied=occupancyByPair.get(from+"|"+to);
            if(occupied==null)occupied=Occupancy.UNKNOWN;
            if(occupied==Occupancy.UNKNOWN){why.add("TRACK_OCCUPANCY_UNKNOWN");unknown++;}
            else if(occupied==Occupancy.OCCUPIED)why.add("TRACK_OCCUPIED");
            if(!controls.turnoutRoutingProven())why.add("TURNOUT_ALIGNMENT_UNVERIFIED");
            if(!controls.signalsProven())why.add("SIGNALS_UNVERIFIED");
            if(!controls.brakingProven())why.add("BRAKING_DISTANCE_UNVERIFIED");
            if(!controls.collisionSeparationProven())why.add("COLLISION_SEPARATION_UNVERIFIED");
            boolean candidateOnly=why.isEmpty();
            if(candidateOnly)review++;else held++;
            // Intentional unconditional fail-closed: R22 does not authorize dispatch.
            decisions.add(new Decision(from,to,candidateOnly?"SIMULATION_READY_MANUAL_REVIEW_ONLY":"HOLD_NO_DISPATCH",
                    false,candidateOnly,List.copyOf(why)));
        }
        return new Report(decisions.size(),review,held,unknown,false,
             decisions.isEmpty()?"NO_ROUTE_NO_DISPATCH":
             held>0?"DISPATCH_HOLD_SIMULATION_ONLY":"CANDIDATE_REVIEW_ONLY_NO_DISPATCH",
             List.copyOf(decisions));
    }
    private static void checkStation(Station state,String label,Set<String> why){
        if(state==null){why.add(label+"_STATION_UNRESOLVED");return;}
        if(!state.savedService())why.add(label+"_SERVICE_NOT_ACTIVATED");
        if(!state.loaded())why.add(label+"_STATION_UNLOADED");
        if(!state.stationRailObserved())why.add(label+"_STATION_RAIL_UNVERIFIED");
        if(!state.engineerRecorded())why.add(label+"_ENGINEER_UNASSIGNED");
        if(!state.engineerLoaded())why.add(label+"_ENGINEER_UNLOADED");
    }
}
