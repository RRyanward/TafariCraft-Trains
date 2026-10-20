package traincraft.worldgen;

import java.util.Locale;
import java.util.regex.Pattern;

/** R30j pure observed railway construction preflight. No Minecraft mutators.
 * A persisted source record permits SURVEY ONLY, never rail placement or movement.
 */
public final class Dev51BWestPreflightR30j {
    private Dev51BWestPreflightR30j() {}
    private static final Pattern SHA = Pattern.compile("[a-f0-9]{64}");
    public record Result(boolean persistedSource,int existingRails,int normalRails,
        int stationRails,int routeHolds,int clearSide,int sideHolds,
        int nearbyVillagers,int otherStations,String westEnd,String planSha256,
        boolean mayBuild,boolean mayDrive,boolean mayDispatch,boolean routeCertified,String verdict) {}
    public static Result inspect(boolean persisted,int old,int normal,int station,
            int railIssues,int clear,int sideIssues,int villagers,int stations,
            String end,String planHash) {
        if(old<0||old>9||normal<0||normal>15||station<0||station>1||
           railIssues<0||railIssues>25||clear<0||clear>54||sideIssues<0||sideIssues>54||
           clear+sideIssues!=54||villagers < -1||stations<0||
           !java.util.Set.of("OPEN_AIR","EXISTING_EW_RAIL","BLOCKED","UNLOADED").contains(end)||
           planHash==null||!SHA.matcher(planHash).matches())
           throw new IllegalArgumentException("incomplete, unsupported or malformed R30j witness");
        String verdict=!persisted?"HOLD_R30I_SOURCE_RECORD_NOT_RELOADED":
            old!=9?"HOLD_ORIGINAL_SOURCE_RAILS":
            railIssues!=0 || normal!=15 || station!=1?"HOLD_WEST_RAIL_GEOMETRY":
            sideIssues!=0 || villagers!=0 || stations!=0?"HOLD_STATION_RESERVATION":
            end.equals("UNLOADED")?"HOLD_WEST_TERMINAL_UNLOADED":
            end.equals("OPEN_AIR")?"HOLD_OPEN_WEST_TERMINAL_NO_BUFFER":
            end.equals("BLOCKED")?"HOLD_WEST_TERMINAL_OBSTRUCTED":
            "HOLD_CONSTRUCTION_NOT_AUTHORIZED_PREVIEW_ONLY";
        // All outcomes are read-only; no end condition grants build/route power.
        return new Result(persisted,old,normal,station,railIssues,clear,sideIssues,
                villagers,stations,end,planHash,false,false,false,false,verdict);
    }
}
