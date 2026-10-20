package traincraft.debug;

import java.lang.reflect.*;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import traincraft.registry.TCBlocks;
import traincraft.worldgen.RailwayVillageWorldgen;

/** R36: native station-only release smoke; never runs in saved player worlds. */
@GameTestHolder("traincraft")
@PrefixGameTestTemplate(false)
public final class Dev51BR36PlayerOperatedStationGameTests {
 private static final String P="[TC-DEV51B-R36-NATIVE]";
 private Dev51BR36PlayerOperatedStationGameTests(){}
 @GameTest(template="r30q_testbed",templateNamespace="traincraft",batch="traincraft.dev51b.r30q",timeoutTicks=110)
 public static void stationOnlyWorldgenNativeSmoke(GameTestHelper h){
  try{
   Path cwd=Path.of(System.getProperty("user.dir","")).toAbsolutePath().normalize();
   if(!(h.getLevel().getServer() instanceof GameTestServer)||cwd.getFileName()==null
      ||!cwd.getFileName().toString().startsWith("native-runtime-")
      ||cwd.getParent()==null||!cwd.getParent().getFileName().toString().equals("_DEV51B_R30Q"))
    throw new IllegalStateException("ISOLATED_GAMETEST_WORLD_REQUIRED");
   Field mode=RailwayVillageWorldgen.class.getDeclaredField("PLAYER_OPERATED_STATIONS_ONLY");mode.setAccessible(true);
   if(!mode.getBoolean(null))throw new IllegalStateException("RELEASE_FLAG_DISABLED");
   Method allowed=RailwayVillageWorldgen.class.getDeclaredMethod("dev50e2WorldAllowed",net.minecraft.server.level.ServerLevel.class);
   allowed.setAccessible(true);
   if(Boolean.TRUE.equals(allowed.invoke(null,h.getLevel())))throw new IllegalStateException("EXPERIMENTAL_WORLD_ALLOW_GATE_OPEN");
   BlockPos c=h.absolutePos(new BlockPos(28,1,22));
   Class<?> site=Class.forName("traincraft.worldgen.RailwayVillageWorldgen$Site");
   Constructor<?> ctor=site.getDeclaredConstructor(BlockPos.class,BlockPos.class);ctor.setAccessible(true);
   Object value=ctor.newInstance(c,c.offset(0,0,45));
   Method place=RailwayVillageWorldgen.class.getDeclaredMethod("placeStation",net.minecraft.server.level.ServerLevel.class,site);
   place.setAccessible(true);place.invoke(null,h.getLevel(),value);
   int platform=0;
   for(int dx=-8;dx<=8;dx++){
    BlockPos x=c.offset(dx,0,0);
    if(!h.getLevel().getBlockState(x).is(dx==0?TCBlocks.TRACK_STATION.get():TCBlocks.TRACK_NORMAL.get()))
     throw new IllegalStateException("STATION_RAIL_MISSING_"+dx);
    platform++;
   }
   if(!h.getLevel().getBlockState(c.offset(0,0,9)).is(TCBlocks.RAILWAY_TOOLBENCH.get()))
      throw new IllegalStateException("WORKBENCH_MISSING");
   if(!h.getLevel().getBlockState(c.offset(-4,0,9)).is(TCBlocks.STATION_OFFICE_DESK.get()))
      throw new IllegalStateException("DESK_MISSING");
   if(!h.getLevel().getBlockState(c.offset(4,0,9)).is(TCBlocks.TICKET_BOOTH.get()))
      throw new IllegalStateException("TICKET_BOOTH_MISSING");
   System.out.println(P+" CASE_PASS station_only_worldgen_native_smoke platformRails="+platform+
    " offices=true experimentalNetworkAllowed=false autoServiceAuthorized=false onlyIsolatedForgeWorld=true "+
    "playerSaveEdited=false naturalVillageGenerationTested=false");
   h.succeed();
  }catch(Exception e){h.fail(P+" HOLD "+e.getClass().getSimpleName()+": "+e.getMessage());}
 }
}
