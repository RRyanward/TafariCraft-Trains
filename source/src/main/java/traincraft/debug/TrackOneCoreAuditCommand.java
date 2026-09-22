package traincraft.debug;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import traincraft.block.track.LegacyBatchTrackSpec;
import traincraft.block.track.LegacyBatchTrackSpecs;

import java.io.IOException;
import java.lang.reflect.Field;
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
 * Step 9.3g-t6-d1 â€” ALL-50 one-core-match inventory.
 *
 * READ-ONLY DIAGNOSTIC.
 *
 * New t6 invariant:
 * Every exposed logical endpoint must land on exactly ONE physical assembly
 * cell in that same track specification.
 *
 * For every one of the 50 LegacyBatchTrackSpec definitions, in all four
 * horizontal facings, this audit compares:
 *
 *   logical endpoint core = spec.endpointPosition(...)
 *
 * against every real assembly cell:
 *
 *   physical cell = spec.offsetForPart(...)
 *
 * CORE_MATCH_COUNT:
 *   0 = logical endpoint is virtual / no physical core owns that cell -> FAIL
 *   1 = exactly one physical core owns that cell                 -> PASS
 *  >1 = ambiguous physical core ownership                       -> FAIL
 *
 * This is deliberately narrower than traversal and visual-mesh testing.
 * It establishes the canonical endpoint/core baseline before t6 rewrites any
 * track family. No world blocks are placed and no production state is changed.
 */
@Mod.EventBusSubscriber(modid = "traincraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackOneCoreAuditCommand {
    private static final int EXPECTED_SPEC_COUNT = 50;
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private TrackOneCoreAuditCommand() {
    }

    private record SpecField(String fieldName, LegacyBatchTrackSpec spec) {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tc_one_core_audit")
                        .executes(context ->
                                runAudit(context.getSource().getPlayerOrException()))
        );
    }

    private static int runAudit(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        List<SpecField> specs;

        try {
            specs = collectSpecs();
        } catch (ReflectiveOperationException ex) {
            player.sendSystemMessage(Component.literal(
                    "TC t6 one-core audit failed while reading specs: " + ex));
            return 0;
        }

        out.add("TAFARICRAFT - TRAINS STEP 9.3g-t6-d1");
        out.add("ALL-50 ONE-CORE-MATCH AUDIT");
        out.add("READ_ONLY=true");
        out.add("WORLD_BLOCKS_PLACED=0");
        out.add("EXPECTED_SPEC_COUNT=" + EXPECTED_SPEC_COUNT);
        out.add("DISCOVERED_SPEC_COUNT=" + specs.size());
        out.add("");

        Map<String, Integer> idCounts = new LinkedHashMap<>();
        for (SpecField entry : specs) {
            idCounts.merge(entry.spec().id(), 1, Integer::sum);
        }

        int duplicateSpecIds = 0;
        for (Map.Entry<String, Integer> entry : idCounts.entrySet()) {
            if (entry.getValue() != 1) {
                duplicateSpecIds++;
                out.add("DUPLICATE_SPEC_ID id=" + entry.getKey()
                        + " count=" + entry.getValue());
            }
        }
        out.add("DUPLICATE_SPEC_ID_COUNT=" + duplicateSpecIds);
        out.add("");

        int totalCases = 0;
        int passCases = 0;
        int zeroCases = 0;
        int ambiguousCases = 0;
        int badEndpointDeclarationCases = 0;
        int tracksPass = 0;
        int tracksFail = 0;
        List<String> problemTracks = new ArrayList<>();

        for (SpecField entry : specs) {
            LegacyBatchTrackSpec spec = entry.spec();
            boolean trackPass = true;
            int trackCases = 0;
            int trackZero = 0;
            int trackAmbiguous = 0;
            int trackEndpointDeclarationProblems = 0;

            out.add("================================================================");
            out.add("TRACK field=" + entry.fieldName()
                    + " id=" + spec.id()
                    + " parts=" + spec.partCount()
                    + " endpoints=" + spec.endpoints().size()
                    + " dynamic=" + spec.dynamicRouting());
            out.add("================================================================");

            for (Direction facing : Direction.Plane.HORIZONTAL) {
                out.add("  FACING=" + facing.getName());

                for (LegacyBatchTrackSpec.Endpoint endpoint : spec.endpoints()) {
                    totalCases++;
                    trackCases++;

                    BlockPos logical = spec.endpointPosition(
                            BlockPos.ZERO, facing, endpoint);
                    BlockPos physicalDeclared = spec.offsetForPart(
                            BlockPos.ZERO, facing, endpoint.part());

                    List<Integer> physicalMatches = new ArrayList<>();
                    for (int part = 0; part < spec.partCount(); part++) {
                        BlockPos physical = spec.offsetForPart(
                                BlockPos.ZERO, facing, part);
                        if (physical.equals(logical)) {
                            physicalMatches.add(part);
                        }
                    }

                    int endpointDeclarationMatches = 0;
                    List<Integer> endpointDeclarationParts = new ArrayList<>();
                    for (LegacyBatchTrackSpec.Endpoint other : spec.endpoints()) {
                        BlockPos otherLogical = spec.endpointPosition(
                                BlockPos.ZERO, facing, other);
                        if (otherLogical.equals(logical)) {
                            endpointDeclarationMatches++;
                            endpointDeclarationParts.add(other.part());
                        }
                    }

                    int coreCount = physicalMatches.size();
                    String status;
                    if (coreCount == 1 && endpointDeclarationMatches == 1) {
                        status = "PASS";
                        passCases++;
                    } else {
                        status = "FAIL";
                        trackPass = false;

                        if (coreCount == 0) {
                            zeroCases++;
                            trackZero++;
                        } else if (coreCount > 1) {
                            ambiguousCases++;
                            trackAmbiguous++;
                        }

                        if (endpointDeclarationMatches != 1) {
                            badEndpointDeclarationCases++;
                            trackEndpointDeclarationProblems++;
                        }
                    }

                    BlockPos logicalMinusDeclared = logical.subtract(
                            physicalDeclared);

                    out.add("    ENDPOINT part=" + endpoint.part()
                            + " declaredOutward=" + endpoint.outward().getName()
                            + " logicalCore=" + pos(logical)
                            + " declaredPhysicalPartCell=" + pos(physicalDeclared)
                            + " logicalMinusDeclaredPhysical="
                            + pos(logicalMinusDeclared)
                            + " CORE_MATCH_COUNT=" + coreCount
                            + " CORE_MATCH_PARTS=" + physicalMatches
                            + " ENDPOINT_DECLARATION_MATCH_COUNT="
                            + endpointDeclarationMatches
                            + " ENDPOINT_DECLARATION_PARTS="
                            + endpointDeclarationParts
                            + " STATUS=" + status);
                }
            }

            if (trackPass) {
                tracksPass++;
            } else {
                tracksFail++;
                problemTracks.add(spec.id());
            }

            out.add("  TRACK_SUMMARY id=" + spec.id()
                    + " cases=" + trackCases
                    + " zero=" + trackZero
                    + " ambiguous=" + trackAmbiguous
                    + " endpointDeclarationProblems="
                    + trackEndpointDeclarationProblems
                    + " STATUS=" + (trackPass ? "PASS" : "FAIL"));
            out.add("");
        }

        boolean specCountPass = specs.size() == EXPECTED_SPEC_COUNT;
        boolean allPass = specCountPass
                && duplicateSpecIds == 0
                && zeroCases == 0
                && ambiguousCases == 0
                && badEndpointDeclarationCases == 0;

        out.add("================================================================");
        out.add("FINAL SUMMARY");
        out.add("================================================================");
        out.add("SPEC_COUNT_STATUS=" + (specCountPass ? "PASS" : "FAIL"));
        out.add("DISCOVERED_SPEC_COUNT=" + specs.size());
        out.add("TRACKS_PASS=" + tracksPass);
        out.add("TRACKS_FAIL=" + tracksFail);
        out.add("ENDPOINT_FACING_CASES=" + totalCases);
        out.add("CORE_MATCH_PASS_CASES=" + passCases);
        out.add("CORE_MATCH_ZERO_CASES=" + zeroCases);
        out.add("CORE_MATCH_AMBIGUOUS_CASES=" + ambiguousCases);
        out.add("ENDPOINT_DECLARATION_PROBLEM_CASES="
                + badEndpointDeclarationCases);
        out.add("PROBLEM_TRACKS=" + problemTracks);
        out.add("T6_INVARIANT=EVERY_EXPOSED_LOGICAL_ENDPOINT_HAS_EXACTLY_ONE_PHYSICAL_CORE");
        out.add("RESULT=" + (allPass ? "PASS" : "FAIL"));

        String stamp = STAMP.format(LocalDateTime.now());
        Path report = Path.of("t6-one-core-audit-" + stamp + ".txt");

        try {
            Files.writeString(
                    report,
                    String.join(System.lineSeparator(), out)
                            + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            player.sendSystemMessage(Component.literal(
                    "TC t6 one-core audit could not write report: " + ex));
            return 0;
        }

        player.sendSystemMessage(Component.literal(
                "TC t6 one-core audit: specs=" + specs.size()
                        + " tracksPass=" + tracksPass
                        + " tracksFail=" + tracksFail
                        + " zero=" + zeroCases
                        + " ambiguous=" + ambiguousCases
                        + " result=" + (allPass ? "PASS" : "FAIL")));
        player.sendSystemMessage(Component.literal(
                "Report: " + report.toAbsolutePath()));

        // Diagnostic command execution itself succeeded even when the audit
        // correctly found failing tracks. The report RESULT carries pass/fail.
        return Command.SINGLE_SUCCESS;
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

    private static String pos(BlockPos pos) {
        return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }
}
