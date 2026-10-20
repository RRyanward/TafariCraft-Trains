package traincraft.debug;

import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import traincraft.entity.train.CouplingManager;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.entity.train.passenger.PassengerCoachBlue;
import traincraft.registry.TCBlocks;
import traincraft.registry.TCEntities;

/** R30w: disposable native coupled GP7/Blue Coach readiness probes only.
 * Contains NO worldgen, service activation, native dispatcher, station-to-station
 * autonomy, or authority to touch the named user worlds. Explicit two-way
 * impulses are TEST INPUTS, not an automatic return trip or railway service.
 */
@GameTestHolder("traincraft")
@PrefixGameTestTemplate(false)
public final class Dev51BR30wCoupledReadinessGameTests {
  private Dev51BR30wCoupledReadinessGameTests() {}
  private static final String P="[TC-DEV51B-R30W-NATIVE]";
  private static final int MAX_TICKS=36;
  private static void ok(GameTestHelper h,boolean value,String reason){
    if(!value)h.fail(P+" HOLD "+reason);
  }
  private static void isolated(GameTestHelper h){
    Path cwd=Path.of(System.getProperty("user.dir", "")).toAbsolutePath().normalize();
    Path parent=cwd.getParent();
    ok(h,h.getLevel().getServer() instanceof GameTestServer
      &&cwd.getFileName()!=null&&cwd.getFileName().toString().startsWith("native-runtime-")
      &&parent!=null&&parent.getFileName()!=null
      &&parent.getFileName().toString().equals("_DEV51B_R30Q"),
      "ONLY_ISOLATED_NATIVE_R30Q_GAMETEST_SERVER_ALLOWED");
  }
  private static void track(GameTestHelper h){
    for(int x=3;x<=12;x++)h.setBlock(new BlockPos(x,0,2),Blocks.STONE);
    for(int x=3;x<=12;x++)h.setBlock(new BlockPos(x,1,2),
      TCBlocks.TRACK_NORMAL.get().defaultBlockState()
        .setValue(RailBlock.SHAPE,RailShape.EAST_WEST));
  }
  private record Pair(LegacyPoweredLocomotive gp,PassengerCoachBlue coach,double startGX,double startCX) {}
  private static Pair spawnLinkedPair(GameTestHelper h){
    LegacyPoweredLocomotive gp=h.spawn(TCEntities.LOCOMOTIVE_DIESEL_GP7.get(),6.0f,1.1f,2.5f);
    PassengerCoachBlue coach=h.spawn(TCEntities.PASSENGER_COACH_BLUE.get(),9.55f,1.1f,2.5f);
    ok(h,gp.isAlive()&&!gp.isRemoved()&&coach.isAlive()&&!coach.isRemoved(),"NATIVE_PAIR_SPAWN_FAILED");
    ok(h,gp.getType()==TCEntities.LOCOMOTIVE_DIESEL_GP7.get(),"WRONG_GP7_ENTITY_TYPE");
    ok(h,coach.getType()==TCEntities.PASSENGER_COACH_BLUE.get(),"WRONG_COACH_ENTITY_TYPE");
    ok(h,CouplingManager.canLink(gp,gp,coach,coach),"GP7_BLUE_COACH_COUPLER_NOT_COMPATIBLE");
    ok(h,CouplingManager.link(gp,gp,coach,coach),"GP7_BLUE_COACH_LINK_REJECTED");
    ok(h,CouplingManager.areMutuallyLinked(gp,gp,coach,coach),"MUTUAL_COUPLER_LINK_NOT_INSTALLED");
    ok(h,!gp.isHandBrakeApplied(),"GP7_BRAKE_ACTIVE");
    return new Pair(gp,coach,gp.getX(),coach.getX());
  }
  private static void checkPair(GameTestHelper h,Pair pair,int tick){
    var g=pair.gp();var c=pair.coach();
    ok(h,!g.isRemoved()&&g.isAlive()&&!c.isRemoved()&&c.isAlive(),"PAIR_REMOVED tick="+tick);
    ok(h,CouplingManager.areMutuallyLinked(g,g,c,c),"COUPLER_SEPARATED tick="+tick);
    ok(h,Double.isFinite(g.getX())&&Double.isFinite(c.getX())
      &&Double.isFinite(g.getDeltaMovement().x)&&Double.isFinite(c.getDeltaMovement().x),
      "PAIR_NONFINITE tick="+tick);
    ok(h,Math.abs(g.getZ()-c.getZ())<0.65,"PAIR_LEFT_TRACK_CENTERLINE tick="+tick);
    ok(h,g.getX()>pair.startGX()-1.9&&g.getX()<pair.startGX()+1.4,
      "GP7_EXCEEDED_BOUNDED_PROBE tick="+tick+" gp7x="+g.getX());
    ok(h,c.getX()>pair.startCX()-2.4&&c.getX()<pair.startCX()+1.4,
      "COACH_EXCEEDED_BOUNDED_PROBE tick="+tick+" coachX="+c.getX());
    ok(h,g.getDeltaMovement().horizontalDistance()<0.15
      &&c.getDeltaMovement().horizontalDistance()<0.20,"PAIR_OVERSPEED tick="+tick);
  }
  private static void pass(GameTestHelper h,String name){
    System.out.println(P+" CASE_PASS "+name+
      " isolatedNativeOnly=true coupledProbeOnly=true serviceDispatchCertified=false "+
      "endToEndRouteCertified=false userWorldAuthority=false");
    h.succeed();
  }
  @GameTest(template="r30q_testbed",templateNamespace="traincraft",
      batch="traincraft.dev51b.r30q",timeoutTicks=110)
  public static void gp7BlueCoachNativeCoupling(GameTestHelper h){
    isolated(h);track(h);
    h.runAfterDelay(3,()->{
      Pair pair=spawnLinkedPair(h);
      h.runAfterDelay(4,()->{
        checkPair(h,pair,4);
        System.out.println(P+" COUPLER_STATIC gp7="+pair.gp().getUUID()+
          " coach="+pair.coach().getUUID()+" gap="+
          (pair.coach().getX()-pair.gp().getX())+
          " mutual=true serviceAutoDispatch=false");
        pass(h,"gp7_blue_coach_native_coupling_only");
      });
    });
  }
  private static final class Cycle {
    int tick;boolean turned;double minGX,minCX,maxReturn;final Pair pair;
    Cycle(Pair pair){this.pair=pair;this.minGX=pair.startGX();this.minCX=pair.startCX();}
  }
  private static void cycle(GameTestHelper h,Cycle c){
    c.tick++;
    Pair pair=c.pair;
    checkPair(h,pair,c.tick);
    double gx=pair.gp().getX(),cx=pair.coach().getX();
    c.minGX=Math.min(c.minGX,gx);c.minCX=Math.min(c.minCX,cx);
    if(c.tick==15){
      ok(h,c.minGX<pair.startGX()-0.12,"GP7_NO_WESTWARD_TRAVEL");
      ok(h,c.minCX<pair.startCX()-0.035,"COACH_DID_NOT_FOLLOW_GP7_WESTWARD");
      // Explicit TEST IMPULSE, not a production dispatcher, timetable or AI reversal.
      pair.gp().setDeltaMovement(+0.050,0,0);
      c.turned=true;
      System.out.println(P+" IMPULSE_REVERSAL_INPUT tick="+c.tick+
        " westGP7="+(pair.startGX()-c.minGX)+" westCoach="+(pair.startCX()-c.minCX)+
        " driver=TEST_IMPULSE_ONLY serviceAutoDispatch=false");
    }
    if(c.turned)c.maxReturn=Math.max(c.maxReturn,gx-c.minGX);
    if(c.tick<=3||c.tick%5==0){
      System.out.println(P+" COUPLED_MOTION tick="+c.tick+
        " gp7x="+gx+" coachX="+cx+
        " distance="+(cx-gx)+" westGP7="+(pair.startGX()-c.minGX)+
        " westCoach="+(pair.startCX()-c.minCX)+" eastReturnGP7="+c.maxReturn+
        " mutual="+CouplingManager.areMutuallyLinked(pair.gp(),pair.gp(),pair.coach(),pair.coach()));
    }
    if(c.tick>=29){
      ok(h,c.turned,"RETURN_IMPULSE_NEVER_APPLIED");
      ok(h,c.maxReturn>0.11,"GP7_DID_NOT_REVERSE_AFTER_RETURN_IMPULSE");
      pass(h,"gp7_blue_coach_bounded_bidirectional_impulse_only");return;
    }
    if(c.tick>=MAX_TICKS){h.fail(P+" HOLD COUPLED_MOTION_TIMEOUT");return;}
    h.runAfterDelay(1,()->cycle(h,c));
  }
  @GameTest(template="r30q_testbed",templateNamespace="traincraft",
      batch="traincraft.dev51b.r30q",timeoutTicks=110)
  public static void gp7BlueCoachBoundedBidirectionalImpulse(GameTestHelper h){
    isolated(h);track(h);
    h.runAfterDelay(3,()->{
      Pair pair=spawnLinkedPair(h);
      pair.gp().setDeltaMovement(-0.055,0,0);
      h.runAfterDelay(1,()->cycle(h,new Cycle(pair)));
    });
  }
}
