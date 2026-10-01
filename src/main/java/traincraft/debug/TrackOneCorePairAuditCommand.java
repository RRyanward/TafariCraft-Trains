package traincraft.debug;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.block.track.LegacyBatchTrackSpec;
import traincraft.block.track.LegacyBatchTrackSpecs;
import traincraft.item.track.LegacyBatchTrackItem;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step 9.3g-t6-d2 â€” ALL-50 one-core tangent-pair audit.
 *
 * READ-ONLY DIAGNOSTIC.
 *
 * d1 answered "does each exposed logical endpoint have one physical core in
 * its own assembly?"  d2 answers the next question across the complete network:
 *
 *   For every ordered source -> incoming track pair that has at least one
 *   opposite production route heading, are BOTH participating endpoints backed
 *   by exactly one physical core?
 *
 * Production route headings are not reimplemented here.  This audit invokes
 * LegacyBatchTrackItem.routeHeadingForEndpoint(...) reflectively so the report
 * uses the exact currently compiled snap semantics, including the existing
 * 45-degree / diagonal / crossing special cases.
 *
 * The directional CROSSING_X case is intentionally valid: four route endpoints
 * may share one physical owner cell.  The t6 invariant is physical CORE count,
 * not "one endpoint declaration per cell."
 *
 * This audit does not place blocks, mutate the world, alter snapping, or touch
 * any production file.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackOneCorePairAuditCommand {
    private static final int EXPECTED_SPEC_COUNT = 50;
    private static final int MAX_FAILURE_DETAILS_PER_PAIR = 12;
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private TrackOneCorePairAuditCommand() {
    }

    private record SpecField(String fieldName, LegacyBatchTrackSpec spec) {
    }

    private record EndpointCase(
            String specId,
            Direction facing,
            LegacyBatchTrackSpec.Endpoint endpoint,
            BlockPos logicalCore,
            BlockPos declaredPhysicalPartCell,
            int coreMatchCount,
            List<Integer> coreMatchParts,
            Direction placementOutward,
            Object routeHeading) {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc_one_core_pair_audit")
                        .executes(context -> run(context.getSource()))
        );
    }

    private static int run(net.minecraft.commands.CommandSourceStack source) {
        try {
            List<String> report = buildReport();
            String stamp = STAMP.format(LocalDateTime.now());
            Path output = Path.of(
                    "t6-one-core-pair-audit-" + stamp + ".txt").toAbsolutePath();
            Files.write(
                    output,
                    report,
                    StandardCharsets.UTF_8);

            String result = report.stream()
                    .filter(line -> line.startsWith("RESULT="))
                    .reduce((a, b) -> b)
                    .orElse("RESULT=UNKNOWN");

            source.sendSuccess(
                    () -> Component.literal(
                            "TC t6 one-core pair audit complete: "
                                    + result + " report=" + output),
                    false);
            return Command.SINGLE_SUCCESS;
        } catch (Throwable t) {
            source.sendFailure(Component.literal(
                    "TC t6 one-core pair audit failed: "
                            + t.getClass().getSimpleName()
                            + ": " + String.valueOf(t.getMessage())));
            return 0;
        }
    }

    private static List<String> buildReport() throws Exception {
        List<String> out = new ArrayList<>();
        List<SpecField> specs = collectSpecs();

        Method routeHeadingForEndpoint =
                findDeclaredMethod(
                        LegacyBatchTrackItem.class,
                        "routeHeadingForEndpoint",
                        4);
        Method placementEndpointOutward =
                findDeclaredMethod(
                        LegacyBatchTrackItem.class,
                        "placementEndpointOutward",
                        3);

        Map<String, List<EndpointCase>> casesBySpec = new LinkedHashMap<>();

        for (SpecField field : specs) {
            LegacyBatchTrackSpec spec = field.spec();
            List<EndpointCase> endpointCases = new ArrayList<>();

            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (LegacyBatchTrackSpec.Endpoint endpoint : spec.endpoints()) {
                    BlockPos logical = spec.endpointPosition(
                            BlockPos.ZERO, facing, endpoint);
                    BlockPos declaredPhysical = spec.offsetForPart(
                            BlockPos.ZERO, facing, endpoint.part());

                    List<Integer> physicalMatches = new ArrayList<>();
                    for (int part = 0; part < spec.partCount(); part++) {
                        BlockPos physical = spec.offsetForPart(
                                BlockPos.ZERO, facing, part);
                        if (physical.equals(logical)) {
                            physicalMatches.add(part);
                        }
                    }

                    Direction outward = (Direction)
                            placementEndpointOutward.invoke(
                                    null, spec, facing, endpoint);
                    Object heading = routeHeadingForEndpoint.invoke(
                            null, spec, BlockPos.ZERO, facing, endpoint);

                    endpointCases.add(new EndpointCase(
                            spec.id(),
                            facing,
                            endpoint,
                            logical,
                            declaredPhysical,
                            physicalMatches.size(),
                            List.copyOf(physicalMatches),
                            outward,
                            heading));
                }
            }

            casesBySpec.put(spec.id(), List.copyOf(endpointCases));
        }

        out.add("TAFARICRAFT - TRAINS STEP 9.3g-t6-d2");
        out.add("ALL-50 ONE-CORE TANGENT-PAIR AUDIT");
        out.add("READ_ONLY=true");
        out.add("WORLD_BLOCKS_PLACED=0");
        out.add("EXPECTED_SPEC_COUNT=" + EXPECTED_SPEC_COUNT);
        out.add("DISCOVERED_SPEC_COUNT=" + specs.size());
        out.add("");
        out.add("INVARIANT:");
        out.add("- A directional endpoint may share a physical owner with other routes.");
        out.add("- For each participating endpoint, CORE_MATCH_COUNT must equal exactly 1.");
        out.add("- Route compatibility comes from the live production routeHeadingForEndpoint helper.");
        out.add("");

        int endpointCasesTotal = 0;
        int endpointCoreOne = 0;
        int endpointCoreZero = 0;
        int endpointCoreAmbiguous = 0;

        out.add("=== ENDPOINT CORE INVENTORY ===");
        for (SpecField field : specs) {
            List<EndpointCase> endpointCases = casesBySpec.get(field.spec().id());
            int one = 0;
            int zero = 0;
            int ambiguous = 0;

            for (EndpointCase endpointCase : endpointCases) {
                endpointCasesTotal++;
                if (endpointCase.coreMatchCount() == 1) {
                    endpointCoreOne++;
                    one++;
                } else if (endpointCase.coreMatchCount() == 0) {
                    endpointCoreZero++;
                    zero++;
                    out.add("ENDPOINT_ZERO"
                            + " id=" + endpointCase.specId()
                            + " facing=" + endpointCase.facing().getName()
                            + " part=" + endpointCase.endpoint().part()
                            + " logicalCore=" + pos(endpointCase.logicalCore())
                            + " declaredPhysical="
                            + pos(endpointCase.declaredPhysicalPartCell())
                            + " outward="
                            + endpointCase.placementOutward().getName()
                            + " heading=" + endpointCase.routeHeading());
                } else {
                    endpointCoreAmbiguous++;
                    ambiguous++;
                    out.add("ENDPOINT_AMBIGUOUS"
                            + " id=" + endpointCase.specId()
                            + " facing=" + endpointCase.facing().getName()
                            + " part=" + endpointCase.endpoint().part()
                            + " logicalCore=" + pos(endpointCase.logicalCore())
                            + " coreParts=" + endpointCase.coreMatchParts()
                            + " outward="
                            + endpointCase.placementOutward().getName()
                            + " heading=" + endpointCase.routeHeading());
                }
            }

            out.add("ENDPOINT_SPEC_SUMMARY"
                    + " id=" + field.spec().id()
                    + " cases=" + endpointCases.size()
                    + " coreOne=" + one
                    + " coreZero=" + zero
                    + " coreAmbiguous=" + ambiguous
                    + " status="
                    + ((zero == 0 && ambiguous == 0) ? "PASS" : "FAIL"));
        }

        out.add("");
        out.add("=== ORDERED SOURCE -> INCOMING TANGENT PAIRS ===");

        int compatibleOrderedSpecPairs = 0;
        int fullyOneCoreOrderedSpecPairs = 0;
        int failingOrderedSpecPairs = 0;
        int compatibleEndpointMatchCases = 0;
        int readyEndpointMatchCases = 0;
        int zeroEndpointMatchCases = 0;
        int ambiguousEndpointMatchCases = 0;

        List<String> failingPairNames = new ArrayList<>();

        List<String> ids = new ArrayList<>(casesBySpec.keySet());
        ids.sort(String::compareTo);

        for (String sourceId : ids) {
            List<EndpointCase> sourceCases = casesBySpec.get(sourceId);

            for (String incomingId : ids) {
                List<EndpointCase> incomingCases = casesBySpec.get(incomingId);

                int compatible = 0;
                int ready = 0;
                int zero = 0;
                int ambiguous = 0;
                int failureDetails = 0;
                List<String> detail = new ArrayList<>();

                for (EndpointCase sourceCase : sourceCases) {
                    Object opposite =
                            invokeNoArg(sourceCase.routeHeading(), "opposite");

                    for (EndpointCase incomingCase : incomingCases) {
                        if (!opposite.equals(incomingCase.routeHeading())) {
                            continue;
                        }

                        compatible++;
                        compatibleEndpointMatchCases++;

                        boolean sourceOne =
                                sourceCase.coreMatchCount() == 1;
                        boolean incomingOne =
                                incomingCase.coreMatchCount() == 1;

                        if (sourceOne && incomingOne) {
                            ready++;
                            readyEndpointMatchCases++;
                            continue;
                        }

                        boolean hasZero =
                                sourceCase.coreMatchCount() == 0
                                        || incomingCase.coreMatchCount() == 0;
                        boolean hasAmbiguous =
                                sourceCase.coreMatchCount() > 1
                                        || incomingCase.coreMatchCount() > 1;

                        if (hasZero) {
                            zero++;
                            zeroEndpointMatchCases++;
                        }
                        if (hasAmbiguous) {
                            ambiguous++;
                            ambiguousEndpointMatchCases++;
                        }

                        if (failureDetails < MAX_FAILURE_DETAILS_PER_PAIR) {
                            failureDetails++;
                            detail.add("  FAIL_CASE"
                                    + " source=" + sourceId
                                    + "/" + sourceCase.facing().getName()
                                    + "/p" + sourceCase.endpoint().part()
                                    + "/core=" + sourceCase.coreMatchCount()
                                    + "/heading=" + sourceCase.routeHeading()
                                    + " incoming=" + incomingId
                                    + "/" + incomingCase.facing().getName()
                                    + "/p" + incomingCase.endpoint().part()
                                    + "/core=" + incomingCase.coreMatchCount()
                                    + "/heading=" + incomingCase.routeHeading());
                        }
                    }
                }

                if (compatible == 0) {
                    continue;
                }

                compatibleOrderedSpecPairs++;
                boolean pairPass = zero == 0 && ambiguous == 0;
                if (pairPass) {
                    fullyOneCoreOrderedSpecPairs++;
                } else {
                    failingOrderedSpecPairs++;
                    failingPairNames.add(sourceId + "->" + incomingId);
                }

                out.add("PAIR"
                        + " source=" + sourceId
                        + " incoming=" + incomingId
                        + " tangentMatches=" + compatible
                        + " ready=" + ready
                        + " zero=" + zero
                        + " ambiguous=" + ambiguous
                        + " status=" + (pairPass ? "PASS" : "FAIL"));

                if (!pairPass) {
                    out.addAll(detail);
                    if (compatible - ready > detail.size()) {
                        out.add("  FAIL_CASES_OMITTED="
                                + ((compatible - ready) - detail.size()));
                    }
                }
            }
        }

        boolean specCountPass = specs.size() == EXPECTED_SPEC_COUNT;
        boolean allCoreReady = specCountPass
                && endpointCoreZero == 0
                && endpointCoreAmbiguous == 0
                && failingOrderedSpecPairs == 0;

        out.add("");
        out.add("=== FINAL SUMMARY ===");
        out.add("SPEC_COUNT_STATUS=" + (specCountPass ? "PASS" : "FAIL"));
        out.add("DISCOVERED_SPEC_COUNT=" + specs.size());
        out.add("ENDPOINT_FACING_CASES=" + endpointCasesTotal);
        out.add("ENDPOINT_CORE_ONE=" + endpointCoreOne);
        out.add("ENDPOINT_CORE_ZERO=" + endpointCoreZero);
        out.add("ENDPOINT_CORE_AMBIGUOUS=" + endpointCoreAmbiguous);
        out.add("TANGENT_COMPATIBLE_ORDERED_SPEC_PAIRS="
                + compatibleOrderedSpecPairs);
        out.add("FULLY_ONE_CORE_ORDERED_SPEC_PAIRS="
                + fullyOneCoreOrderedSpecPairs);
        out.add("FAILING_ORDERED_SPEC_PAIRS="
                + failingOrderedSpecPairs);
        out.add("COMPATIBLE_ENDPOINT_MATCH_CASES="
                + compatibleEndpointMatchCases);
        out.add("READY_ENDPOINT_MATCH_CASES="
                + readyEndpointMatchCases);
        out.add("ZERO_ENDPOINT_MATCH_CASES="
                + zeroEndpointMatchCases);
        out.add("AMBIGUOUS_ENDPOINT_MATCH_CASES="
                + ambiguousEndpointMatchCases);
        out.add("FAILING_PAIR_NAMES=" + failingPairNames);
        out.add("T6_PAIR_INVARIANT=EVERY_TANGENT_COMPATIBLE_CONNECTION_USES_ONE_PHYSICAL_CORE_PER_ENDPOINT");
        out.add("RESULT=" + (allCoreReady ? "PASS" : "FAIL"));

        return out;
    }

    private static List<SpecField> collectSpecs()
            throws ReflectiveOperationException {
        List<SpecField> out = new ArrayList<>();

        for (Field field : LegacyBatchTrackSpecs.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (field.getType() != LegacyBatchTrackSpec.class) {
                continue;
            }

            Object value = field.get(null);
            if (value instanceof LegacyBatchTrackSpec spec) {
                out.add(new SpecField(field.getName(), spec));
            }
        }

        out.sort(Comparator
                .comparing((SpecField value) -> value.spec().id())
                .thenComparing(SpecField::fieldName));
        return out;
    }

    private static Method findDeclaredMethod(
            Class<?> owner,
            String name,
            int parameterCount) {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == parameterCount) {
                method.setAccessible(true);
                return method;
            }
        }

        throw new IllegalStateException(
                "Missing method " + owner.getName()
                        + "#" + name + "/" + parameterCount);
    }

    private static Object invokeNoArg(Object target, String methodName)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static String pos(BlockPos pos) {
        return "(" + pos.getX()
                + "," + pos.getY()
                + "," + pos.getZ() + ")";
    }
}
