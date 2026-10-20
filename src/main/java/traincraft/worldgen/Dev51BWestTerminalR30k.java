package traincraft.worldgen;

import java.util.*;
import java.util.regex.Pattern;

/** R30k deterministic engineering survey. Never edits blocks, writes SavedData or grants movement. */
public final class Dev51BWestTerminalR30k {
    private Dev51BWestTerminalR30k() {}
    public static final int BUFFER_X=36, Y=-60, Z=-3;
    public static final String EXPECTED_PLAN="5594db3ecfc31d207e14233de238561c14451fc0185da43b32c2db4c6ce5520d";
    public static final String EXPECTED_SOURCE="e6a172d6337927d7ee6f28a8789eed05f18fefe6f0492f3a842d661751b9666d";
    public enum Site { AIR_SUPPORTED_CLEAR, UNLOADED, BLOCKED, SUPPORT_MISSING,
        FLUID, HEADROOM_BLOCKED, FLANK_BLOCKED, ENTITY, BLOCK_ENTITY }
    public record Result(boolean persistedSource,boolean westRailPreflight,
        boolean bufferBlockRegistryValid,boolean eastFacing,
        Site buffer,Site continuation1,Site continuation2,
        boolean candidateClear,boolean westwardExtensionReserved,
        boolean mayPlace,boolean bufferCollisionCertified,boolean mayDrive,
        boolean mayDispatch,String status) {}
    public static Result inspect(boolean persisted,String sourceSha,String planSha,
            String westPlanVerdict,boolean blockRegistered,boolean facesEast,
            Site buffer,Site continuation1,Site continuation2) {
        if(sourceSha==null||!Pattern.matches("[a-f0-9]{64}",sourceSha)
                ||planSha==null||!Pattern.matches("[a-f0-9]{64}",planSha)
                ||westPlanVerdict==null||buffer==null||continuation1==null||continuation2==null)
            throw new IllegalArgumentException("missing/malformed terminal engineering witness");
        boolean sourceOkay=persisted&&EXPECTED_SOURCE.equals(sourceSha);
        boolean planOkay=sourceOkay&&EXPECTED_PLAN.equals(planSha)
                &&"HOLD_OPEN_WEST_TERMINAL_NO_BUFFER".equals(westPlanVerdict);
        boolean bufferOkay=buffer==Site.AIR_SUPPORTED_CLEAR;
        boolean extensionClear=continuation1==Site.AIR_SUPPORTED_CLEAR
                &&continuation2==Site.AIR_SUPPORTED_CLEAR;
        String status=!sourceOkay?"HOLD_PERSISTED_SOURCE_MISMATCH":
                !planOkay?"HOLD_WEST_CONSTRUCTION_WITNESS_MISMATCH":
                !blockRegistered||!facesEast?"HOLD_TRAIN_BUFFER_BLOCK_OR_FACING":
                buffer==Site.UNLOADED?"HOLD_BUFFER_CHUNK_UNLOADED":
                !bufferOkay?"HOLD_BUFFER_SITE_"+buffer.name():
                !extensionClear?"HOLD_WESTWARD_CONTINUATION_NOT_CLEAR":
                "HOLD_BUFFER_NOT_INSTALLED_STOP_NOT_CERTIFIED";
        return new Result(sourceOkay,planOkay,blockRegistered,facesEast,buffer,
                continuation1,continuation2,bufferOkay&&blockRegistered&&facesEast,
                extensionClear,false,false,false,false,status);
    }
}
