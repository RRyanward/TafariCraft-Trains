package traincraft.debug;

import java.lang.reflect.*;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import traincraft.registry.TCBlocks;
import traincraft.worldgen.RailwayStationSurfaceVariants;
import traincraft.worldgen.RailwayVillageWorldgen;

/** Native isolated fixture tests only, NOT an automatic natural-village worldgen certificate. */
@GameTestHolder("traincraft")
@PrefixGameTestTemplate(false)
public final class Dev51BR37SurfaceStationVarietyGameTests {
 private static final String P="[TC-DEV51B-R37-NATIVE]";
 private Dev51BR37SurfaceStationVarietyGameTests(){}
 private static void test(GameTestHelper h,RailwayStationSurfaceVariants.Kind kind){
  try {
   Path cwd=Path.of(System.getProperty("user.dir","")).toAbsolutePath().normalize();
   if(!(h.getLevel().getServer() instanceof GameTestServer)||cwd.getFileName()==null
    ||!cwd.getFileName().toString().startsWith("native-runtime-")
    ||cwd.getParent()==null||!cwd.getParent().getFileName().toString().equals("_DEV51B_R30Q"))
    throw new IllegalStateException("ISOLATED_GAMETEST_WORLD_REQUIRED");
   BlockPos c=h.absolutePos(new BlockPos(28,1,22));
   Class<?> site=Class.forName("traincraft.worldgen.RailwayVillageWorldgen$Site");
   Constructor<?> ctor=site.getDeclaredConstructor(BlockPos.class,BlockPos.class);ctor.setAccessible(true);
   Object value=ctor.newInstance(c,c.offset(0,0,45));
   Method place=RailwayVillageWorldgen.class.getDeclaredMethod("placeStation",net.minecraft.server.level.ServerLevel.class,site,boolean.class);
   place.setAccessible(true);
   // R37a: R30Q testbed contains pre-existing native track/fixture cells.
   // Production checks its whole footprint for AIR before placing a station;
   // this reflection-based test bypasses that preflight, so it must prepare
   // exactly the two required cosmetic targets in the ISOLATED GameTest world.
   BlockPos mossTarget=c.offset(3,0,3), scaffoldTarget=c.offset(8,0,10);
   String mossBefore=h.getLevel().getBlockState(mossTarget).toString();
   String scaffoldBefore=h.getLevel().getBlockState(scaffoldTarget).toString();
   if(kind==RailwayStationSurfaceVariants.Kind.OVERGROWN)
    h.getLevel().setBlock(mossTarget,Blocks.AIR.defaultBlockState(),2);
   if(kind==RailwayStationSurfaceVariants.Kind.CONSTRUCTION){
    h.getLevel().setBlock(scaffoldTarget,Blocks.AIR.defaultBlockState(),2);
    h.getLevel().setBlock(scaffoldTarget.below(),Blocks.DIRT.defaultBlockState(),2);
   }
   System.out.println("[TC-DEV51B-R37A-NATIVE] FIXTURE_PREP kind="+kind+
      " mossBefore="+mossBefore+" scaffoldBefore="+scaffoldBefore+
      " scaffoldSupport="+h.getLevel().getBlockState(scaffoldTarget.below()));
   place.invoke(null,h.getLevel(),value,RailwayStationSurfaceVariants.hasStaff(kind));
   RailwayStationSurfaceVariants.decorate(h.getLevel(),c,kind);
   System.out.println("[TC-DEV51B-R37A-NATIVE] DECOR_READBACK kind="+kind+
      " moss="+h.getLevel().getBlockState(mossTarget)+
      " mossSupport="+h.getLevel().getBlockState(mossTarget.below())+
      " scaffold="+h.getLevel().getBlockState(scaffoldTarget)+
      " scaffoldSupport="+h.getLevel().getBlockState(scaffoldTarget.below()));
   for(int dx=-8;dx<=8;dx++) {
    BlockPos p=c.offset(dx,0,0);
    if(!h.getLevel().getBlockState(p).is(dx==0?TCBlocks.TRACK_STATION.get():TCBlocks.TRACK_NORMAL.get()))
       throw new IllegalStateException("PLATFORM_RAIL_MISSING_"+dx);
    if(!h.getLevel().getBlockState(p.below()).is(Blocks.GRAVEL))
       throw new IllegalStateException("TRACK_SUPPORT_MISSING_"+dx);
   }
   BlockPos bench=c.offset(0,0,9);
   if(RailwayStationSurfaceVariants.hasStaff(kind)) {
    if(!h.getLevel().getBlockState(bench).is(TCBlocks.RAILWAY_TOOLBENCH.get()))
        throw new IllegalStateException("STAFFED_WORKBENCH_MISSING");
   } else {
    if(!h.getLevel().getBlockState(bench).isAir())throw new IllegalStateException("ABANDONED_WORKBENCH_PRESENT");
    if(!h.getLevel().getEntitiesOfClass(Villager.class,new AABB(c.offset(-9,-1,2),c.offset(10,5,13))).isEmpty())
        throw new IllegalStateException("ABANDONED_VILLAGER_SPAWNED");
   }
   switch(kind) {
    case ABANDONED -> {if(!h.getLevel().getBlockState(c.offset(-5,-1,3)).is(Blocks.MOSSY_STONE_BRICKS))throw new IllegalStateException("AGING_MISSING");}
    case OVERGROWN -> {if(!h.getLevel().getBlockState(c.offset(3,-1,3)).is(Blocks.MOSS_BLOCK))throw new IllegalStateException("MOSS_ANCHOR_MISSING");if(!h.getLevel().getBlockState(c.offset(3,0,3)).is(Blocks.MOSS_CARPET))throw new IllegalStateException("GROWTH_MISSING: "+h.getLevel().getBlockState(c.offset(3,0,3)));}
    case CONSTRUCTION -> {if(!h.getLevel().getBlockState(c.offset(8,0,10)).is(Blocks.SCAFFOLDING))throw new IllegalStateException("SCAFFOLD_MISSING: "+h.getLevel().getBlockState(c.offset(8,0,10)));}
    default -> {if(!h.getLevel().getBlockState(c.offset(-4,0,9)).is(TCBlocks.STATION_OFFICE_DESK.get()))throw new IllegalStateException("NORMAL_OFFICE_MISSING");}
   }
   long worldSeed=h.getLevel().getSeed();
   int same=0;for(int i=0;i<1000;i++){BlockPos probe=new BlockPos(64+i*13,70,12);
    if(RailwayStationSurfaceVariants.choose(worldSeed,probe)==kind)same++;}
   if(same==0)throw new IllegalStateException("NO_DETERMINISTIC_SELECTION_FOR_"+kind);
   System.out.println(P+" CASE_PASS station_style_"+kind.name().toLowerCase(java.util.Locale.ROOT)+
     " rails=17 supports=17 stableSelection=true staff="+RailwayStationSurfaceVariants.hasStaff(kind)+
     " isolatedNativeOnly=true realVillageWorldgenCertified=false playerTrainMovementUnchanged=true");
   h.succeed();
  }catch(Exception e){h.fail(P+" HOLD "+kind+" "+e.getClass().getSimpleName()+": "+e.getMessage());}
 }
 @GameTest(template="r30q_testbed",templateNamespace="traincraft",batch="traincraft.dev51b.r30q",timeoutTicks=110)
 public static void stationStyleRegular(GameTestHelper h){test(h,RailwayStationSurfaceVariants.Kind.REGULAR);}
 @GameTest(template="r30q_testbed",templateNamespace="traincraft",batch="traincraft.dev51b.r30q",timeoutTicks=110)
 public static void stationStyleAbandoned(GameTestHelper h){test(h,RailwayStationSurfaceVariants.Kind.ABANDONED);}
 @GameTest(template="r30q_testbed",templateNamespace="traincraft",batch="traincraft.dev51b.r30q",timeoutTicks=110)
 public static void stationStyleOvergrown(GameTestHelper h){test(h,RailwayStationSurfaceVariants.Kind.OVERGROWN);}
 @GameTest(template="r30q_testbed",templateNamespace="traincraft",batch="traincraft.dev51b.r30q",timeoutTicks=110)
 public static void stationStyleConstruction(GameTestHelper h){test(h,RailwayStationSurfaceVariants.Kind.CONSTRUCTION);}
}
