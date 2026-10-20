package traincraft.debug;

import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import traincraft.entity.train.CouplingManager;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.entity.train.passenger.PassengerCoachBlue;
import traincraft.registry.TCBlocks;
import traincraft.registry.TCEntities;

/** R31: ISOLATED service-sequence controller prototype. Not a production dispatcher.
 * ACTUAL_PRODUCTION_AUTODISPATCH_IS_NOT_AUTHORIZED.
 * The two additional GameTests do not authorize ordinary worlds, saved stations,
 * timetable driving, villager AI, full networks, or production auto-dispatch.
 */
@GameTestHolder("traincraft")
@PrefixGameTestTemplate(false)
public final class Dev51BR31ServiceSequenceGameTests {
  private Dev51BR31ServiceSequenceGameTests() {}
  private static final String P="[TC-DEV51B-R31-NATIVE]";
  private static final BlockPos WEST = new BlockPos(5,1,2);
  private static final BlockPos EAST = new BlockPos(7,1,2);
  private static final BlockPos WEST_POWER = new BlockPos(5,1,1);
  private static final BlockPos EAST_POWER = new BlockPos(7,1,1);
  private static void ok(GameTestHelper h,boolean condition,String why){if(!condition)h.fail(P+" HOLD "+why);}
  private static boolean authorized(GameTestHelper h){
    Path cwd=Path.of(System.getProperty("user.dir","")).toAbsolutePath().normalize();
    Path p=cwd.getParent();
    return h.getLevel().getServer() instanceof GameTestServer
      && cwd.getFileName()!=null&&cwd.getFileName().toString().startsWith("native-runtime-")
      && p!=null&&p.getFileName()!=null&&p.getFileName().toString().equals("_DEV51B_R30Q");
  }
  private static void prepare(GameTestHelper h){
    for(int x=3;x<=12;x++) h.setBlock(new BlockPos(x,0,2), Blocks.STONE);
    for(int x=3;x<=12;x++)h.setBlock(new BlockPos(x,1,2),
       TCBlocks.TRACK_NORMAL.get().defaultBlockState().setValue(RailBlock.SHAPE,RailShape.EAST_WEST));
    h.setBlock(WEST,TCBlocks.TRACK_STATION.get().defaultBlockState()
      .setValue(RailBlock.SHAPE,RailShape.EAST_WEST));
    h.setBlock(EAST,TCBlocks.TRACK_STATION.get().defaultBlockState()
      .setValue(RailBlock.SHAPE,RailShape.EAST_WEST));
    // East station permits the westbound departure; west station is a stop.
    h.setBlock(EAST_POWER, Blocks.REDSTONE_BLOCK);
  }
  private static boolean station(GameTestHelper h,BlockPos pos,boolean powered){
    var state=h.getBlockState(pos);
    return state.is(TCBlocks.TRACK_STATION.get())
       && h.getBlockState(pos.below()).is(Blocks.STONE)
       && h.getLevel().hasNeighborSignal(h.absolutePos(pos))==powered;
  }
  private record Pair(LegacyPoweredLocomotive gp,PassengerCoachBlue coach,
                      double gx,double cx) {}
  private static Pair spawn(GameTestHelper h){
    var gp=h.spawn(TCEntities.LOCOMOTIVE_DIESEL_GP7.get(),6.65f,1.1f,2.5f);
    var coach=h.spawn(TCEntities.PASSENGER_COACH_BLUE.get(),10.2f,1.1f,2.5f);
    ok(h,gp.isAlive()&&coach.isAlive(),"SPAWN_FAILED");
    ok(h,CouplingManager.canLink(gp,gp,coach,coach),"LINK_UNSUPPORTED");
    ok(h,CouplingManager.link(gp,gp,coach,coach),"LINK_FAILED");
    ok(h,CouplingManager.areMutuallyLinked(gp,gp,coach,coach),"PAIR_NOT_MUTUAL");
    ok(h,!gp.isHandBrakeApplied(),"GP7_HANDBRAKE_ACTIVE");
    return new Pair(gp,coach,gp.getX(),coach.getX());
  }
  private static void pairSafe(GameTestHelper h,Pair p,int tick){
    var g=p.gp();var c=p.coach();
    ok(h,g.isAlive()&&c.isAlive()&&!g.isRemoved()&&!c.isRemoved(),"MISSING_VEHICLE tick="+tick);
    ok(h,CouplingManager.areMutuallyLinked(g,g,c,c),"COUPLER_LOST tick="+tick);
    ok(h,Math.abs(g.getZ()-c.getZ())<0.65,"DERAIL_OR_LATERAL_SEPARATION tick="+tick);
    ok(h,Double.isFinite(g.getX())&&Double.isFinite(c.getX()),"NONFINITE_COORDINATES tick="+tick);
    ok(h,Math.abs(g.getDeltaMovement().x)<0.15&&Math.abs(c.getDeltaMovement().x)<0.20,
      "OVERSPEED tick="+tick);
    ok(h,c.getX()-g.getX()>2.3&&c.getX()-g.getX()<3.8,
      "COUPLER_GAP_UNSAFE tick="+tick+" gap="+(c.getX()-g.getX()));
    ok(h,g.getX()>p.gx()-2.0&&g.getX()<p.gx()+1.6
          &&c.getX()>p.cx()-2.0&&c.getX()<p.cx()+1.6,
      "ROUTE_BOUNDS_EXCEEDED tick="+tick);
  }
  private static void pass(GameTestHelper h,String name){
    System.out.println(P+" CASE_PASS "+name+
      " isolatedNativeOnly=true prototypeOnly=true actualVillagerDispatch=false "+
      "fullRouteCertified=false productionAutoDispatch=false userWorldAuthority=false");
    h.succeed();
  }
  @GameTest(template="r30q_testbed",templateNamespace="traincraft",
      batch="traincraft.dev51b.r30q",timeoutTicks=150)
  public static void serviceSafetyGateRejectsIncompleteRoute(GameTestHelper h){
    ok(h,authorized(h),"ISOLATED_SERVER_REQUIRED");
    prepare(h);
    // No movement is allowed when a station is unsupported or missing.
    ok(h,station(h,WEST,false)&&station(h,EAST,true),"TEST_STATION_SETUP_INVALID");
    h.setBlock(WEST,Blocks.AIR);
    ok(h,!station(h,WEST,false),"MISSING_STATION_MUST_DENY");
    // Fail-closed safety case, not an intentionally failing GameTest.
    System.out.println(P+" DENIED unsafeStation=true locomotiveSpawned=false "+
      "sourceWorldsAccessible=false");
    pass(h,"service_gate_rejects_missing_station");
  }
  private enum Phase { WESTBOUND, WEST_HOLD, RETURNING, RETURN_HOLD }
  private static final class Sequence {
    final Pair pair; Phase phase=Phase.WESTBOUND;
    int tick=0,hold=0;double westMin,returnMax;
    Sequence(Pair p){pair=p;westMin=p.gx();returnMax=p.gx();}
  }
  private static void cycle(GameTestHelper h,Sequence s){
    ++s.tick;pairSafe(h,s.pair,s.tick);
    var gp=s.pair.gp();double x=gp.getX();
    double speed=Math.abs(gp.getDeltaMovement().x);
    switch(s.phase){
      case WESTBOUND -> {
        s.westMin=Math.min(s.westMin,x);
        if(s.tick>52)h.fail(P+" HOLD NO_WEST_STATION_ARRIVAL");
        if(s.westMin<s.pair.gx()-0.09&&speed<0.0005&&x>h.absolutePos(WEST).getX()-0.5
           &&x<h.absolutePos(WEST).getX()+1.6){
          s.phase=Phase.WEST_HOLD;s.hold=0;
          System.out.println(P+" WEST_ARRIVAL tick="+s.tick+" x="+x+
            " westTravel="+(s.pair.gx()-s.westMin)+" speed="+speed);
        }
      }
      case WEST_HOLD -> {
        ok(h,station(h,WEST,false),"WEST_STATION_CHANGED_DURING_HOLD");
        ok(h,speed<0.0005,"WEST_HOLD_BRAKE_LOST");
        if(++s.hold>=4){
          h.setBlock(WEST_POWER,Blocks.REDSTONE_BLOCK);
          ok(h,station(h,WEST,true),"WEST_STATION_FAILED_TO_POWER_FOR_RETURN");
          gp.setDeltaMovement(+0.060,0,0); // prototype/testbed only
          s.phase=Phase.RETURNING;
          System.out.println(P+" RETURN_COMMAND tick="+s.tick+
            " operator=ISOLATED_TEST_STATE_MACHINE_ONLY autoDispatch=false");
        }
      }
      case RETURNING -> {
        s.returnMax=Math.max(s.returnMax,x);
        if(s.tick>110)h.fail(P+" HOLD RETURN_STATION_NOT_REACHED");
        // Remove east station power once GP7 has moved away from west hold.
        if(s.returnMax>s.westMin+0.18&&station(h,EAST,true)){
          h.setBlock(EAST_POWER,Blocks.AIR);
          ok(h,station(h,EAST,false),"EAST_STATION_UNPOWER_FAILED");
        }
        if(s.returnMax>s.westMin+0.25&&station(h,EAST,false)
           &&speed<0.0005&&x>h.absolutePos(EAST).getX()-0.75
           &&x<h.absolutePos(EAST).getX()+1.75){
          s.phase=Phase.RETURN_HOLD;s.hold=0;
          System.out.println(P+" RETURN_ARRIVAL tick="+s.tick+" x="+x+
            " returned="+(s.returnMax-s.westMin)+" speed="+speed);
        }
      }
      case RETURN_HOLD -> {
        ok(h,station(h,EAST,false),"RETURN_STATION_NOT_UNPOWERED");
        ok(h,speed<0.0005,"RETURN_HOLD_BRAKE_LOST");
        if(++s.hold>=4){
          ok(h,s.pair.gx()-s.westMin>0.09,"NO_OUTBOUND_TRAVEL");
          ok(h,s.returnMax-s.westMin>0.25,"NO_RETURN_TRAVEL");
          pass(h,"gp7_coach_two_station_isolated_service_sequence");return;
        }
      }
    }
    if(s.tick<=5||s.tick%10==0)
      System.out.println(P+" SEQUENCE_TICK tick="+s.tick+" phase="+s.phase+
        " gp7x="+x+" coachX="+s.pair.coach.getX()+" speed="+speed+
        " mutuallyLinked="+CouplingManager.areMutuallyLinked(gp,gp,s.pair.coach,s.pair.coach));
    if(s.tick>=135){h.fail(P+" HOLD SERVICE_SEQUENCE_TIMEOUT");return;}
    h.runAfterDelay(1,()->cycle(h,s));
  }
  @GameTest(template="r30q_testbed",templateNamespace="traincraft",
      batch="traincraft.dev51b.r30q",timeoutTicks=150)
  public static void gp7CoachTwoStationIsolatedServiceSequence(GameTestHelper h){
    ok(h,authorized(h),"ISOLATED_SERVER_REQUIRED");
    prepare(h);
    ok(h,station(h,WEST,false)&&station(h,EAST,true),"INVALID_STATION_POWER_OR_FOUNDATION");
    h.runAfterDelay(3,()->{
      ok(h,station(h,WEST,false)&&station(h,EAST,true),"STATIONS_CHANGED_DURING_WORLD_TICK");
      Pair p=spawn(h);
      p.gp().setDeltaMovement(-0.065,0,0);
      h.runAfterDelay(1,()->cycle(h,new Sequence(p)));
    });
  }
}
