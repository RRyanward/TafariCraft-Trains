package traincraft.worldgen;

import java.util.ArrayList;
import java.util.List;

/** R29. Pure-Java read-only observation; never grants movement authority. */
public final class Dev51BDestinationR29 {
    private Dev51BDestinationR29() {}
    public static final int MAX_SOURCE_RADIUS = 48;
    public static final int MAX_ROUTE_RADIUS = Dev51BTrackGraphR18.MAX_RADIUS;
    public static final int MIN_DISTINCT_HORIZONTAL = 24;

    public record Report(String verdict, List<String> blockers, int railLinks,
                         int clearCells, int blockedCells, int unknownCells,
                         int nativeCells, boolean topologyConnected,
                         boolean manualReviewCandidate, boolean driveAuthorized) {}

    /** Two distinct saved stations, with exact loaded station rails, are required.
     * A connected topological route is not a certified train route. */
    public static Report inspect(Dev51BTrackGraphR18.Result graph,
                                  Dev51BRoutePlannerR19.Plan plan,
                                  Dev51BRouteSafetyR21.Report safety,
                                  String sourceId, String destinationId,
                                  boolean sourceRecorded, boolean destinationRecorded,
                                  boolean sourceLoaded, boolean destinationLoaded,
                                  boolean sourceStationRail, boolean destinationStationRail,
                                  boolean stationsDistinct) {
        List<String> holds = new ArrayList<>();
        if (!sourceRecorded) holds.add("SOURCE_STATION_NOT_RECORDED");
        if (!destinationRecorded) holds.add("DESTINATION_STATION_NOT_RECORDED");
        if (sourceId == null || destinationId == null || sourceId.equals(destinationId)
                || !stationsDistinct) holds.add("NOT_TWO_DISTINCT_STATIONS");
        if (!sourceLoaded) holds.add("SOURCE_STATION_UNLOADED");
        if (!destinationLoaded) holds.add("DESTINATION_STATION_UNLOADED");
        if (!sourceStationRail) holds.add("SOURCE_STATION_RAIL_NOT_CONFIRMED");
        if (!destinationStationRail) holds.add("DESTINATION_STATION_RAIL_NOT_CONFIRMED");
        if (graph == null || plan == null || safety == null) {
            holds.add("OBSERVED_GRAPH_NOT_AVAILABLE");
            return result(holds, null, false);
        }
        if (graph.capReached() || graph.clippedPorts() > 0 || graph.unloadedBoundaries() > 0)
            holds.add("LOADED_NETWORK_INCOMPLETE");
        if (graph.complexBoundaries() > 0 || graph.ambiguousPorts() > 0)
            holds.add("NATIVE_OR_AMBIGUOUS_CONNECTIONS_UNRESOLVED");
        if (!plan.graphConsistent() || !safety.graphConsistent())
            holds.add("ROUTE_GRAPH_INCONSISTENT");
        if (plan.partialNetwork() || safety.networkPartial())
            holds.add("ROUTE_NETWORK_PARTIAL");
        if (plan.anchorLimitReached() || plan.pairLimitReached() || plan.unresolvedAnchors() > 0)
            holds.add("ROUTE_ANCHOR_OR_PAIR_UNRESOLVED");
        boolean sourceExact = plan.matches().stream().anyMatch(m -> sourceId.equals(m.id())
                && "EXACT_STATION_RAIL".equals(m.matchStatus()));
        boolean destExact = plan.matches().stream().anyMatch(m -> destinationId.equals(m.id())
                && "EXACT_STATION_RAIL".equals(m.matchStatus()));
        if (!sourceExact) holds.add("SOURCE_ANCHOR_NOT_EXACT");
        if (!destExact) holds.add("DESTINATION_ANCHOR_NOT_EXACT");
        Dev51BRoutePlannerR19.Route candidate = plan.routes().stream().filter(r ->
                sourceId.equals(r.from()) && destinationId.equals(r.to())).findFirst().orElse(null);
        // R19 may order endpoints in either direction, while path validation is symmetrical.
        if (candidate == null) candidate = plan.routes().stream().filter(r ->
                sourceId.equals(r.to()) && destinationId.equals(r.from())).findFirst().orElse(null);
        boolean connected = candidate != null && candidate.railLinks() >= 1;
        if (!connected) holds.add("NO_CONNECTED_DESTINATION_IN_OBSERVED_GRAPH");
        Dev51BRouteSafetyR21.CheckedRoute checked = safety.routes().stream().filter(r ->
                (sourceId.equals(r.from()) && destinationId.equals(r.to())) ||
                (sourceId.equals(r.to()) && destinationId.equals(r.from()))).findFirst().orElse(null);
        if (checked == null) holds.add("PHYSICAL_ROUTE_AUDIT_NOT_AVAILABLE");
        else {
            if (checked.blocked() > 0) holds.add("ROUTE_PHYSICAL_CLEARANCE_BLOCKED");
            if (checked.unknown() > 0) holds.add("ROUTE_PHYSICAL_CLEARANCE_UNKNOWN");
            if (checked.nativeCells() > 0) holds.add("ROUTE_NATIVE_RAIL_NOT_VERIFIED");
            if (!checked.sourceStopObserved() || !checked.destinationStopObserved())
                holds.add("STATION_STOP_APPROACH_UNVERIFIED");
            for (String reason : checked.reasons()) {
                if (!holds.contains(reason)) holds.add(reason);
            }
        }
        return result(holds, checked, connected);
    }

    public static Report unavailable(String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Missing HOLD reason");
        return result(List.of(reason), null, false);
    }
    private static Report result(List<String> holds, Dev51BRouteSafetyR21.CheckedRoute checked,
                                 boolean connected) {
        boolean review = holds.isEmpty() && connected && checked != null;
        // Even the best observed result never enables driver movement or dispatch.
        return new Report(review ? "OBSERVED_ROUTE_FOR_MANUAL_REVIEW_ONLY" : "HOLD_NO_DRIVE_CERTIFICATION",
                List.copyOf(holds), checked == null ? 0 : checked.links(),
                checked == null ? 0 : checked.observedClear(),
                checked == null ? 0 : checked.blocked(),
                checked == null ? 0 : checked.unknown(),
                checked == null ? 0 : checked.nativeCells(),
                connected, review, false);
    }
}
