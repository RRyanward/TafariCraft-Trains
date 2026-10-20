package traincraft.debug;

import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import traincraft.entity.train.CouplingManager;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.entity.train.passenger.PassengerCoachBlue;
import traincraft.registry.TCBlocks;
import traincraft.registry.TCEntities;
import traincraft.service.GuardedRailwayServiceController;
import traincraft.service.GuardedRailwayServiceController.Gate;
import traincraft.service.GuardedRailwayServiceController.Instruction;
import traincraft.service.GuardedRailwayServiceController.Observation;
import traincraft.service.GuardedRailwayServiceController.Phase;

/** Native-only adapter for the inert R32 controller core. No normal-world hook or driver. */
@GameTestHolder("traincraft")
@PrefixGameTestTemplate(false)
public final class Dev51BR32GuardedDispatchGameTests {
  private Dev51BR32GuardedDispatchGameTests() {}
  private static final String P="[TC-DEV51B-R32-NATIVE]";
  private static final BlockPos WEST=new BlockPos(5,1,2);
  private static final BlockPos EAST=new BlockPos(9,1,2);
  private static final BlockPos WEST_POWER=new BlockPos(5,1,1);
  private static final BlockPos EAST_POWER=new BlockPos(9,1,1);
  private static void ok(GameTestHelper h,boolean yes,String error){if(!yes)h.fail(P+" HOLD "+error);}
  private static boolean isolated(GameTestHelper h){
    Path cwd=Path.of(System.getProperty("user.dir","")).toAbsolutePath().normalize();
    Path parent=cwd.getParent();
    return h.getLevel().getServer() instanceof GameTestServer
      &&cwd.getFileName()!=null&&cwd.getFileName().toString().startsWith("native-runtime-")
      &&parent!=null&&parent.getFileName()!=null&&parent.getFileName().toString().equals("_DEV51B_R30Q");
  }
  private static void prepare(GameTestHelper h){
    for(int x=3;x<=12;x++){
      h.setBlock(new BlockPos(x,0,2),Blocks.STONE);
      h.setBlock(new BlockPos(x,1,2),TCBlocks.TRACK_NORMAL.get().defaultBlockState()
        .setValue(RailBlock.SHAPE,RailShape.EAST_WEST));
    }
    h.setBlock(WEST,TCBlocks.TRACK_STATION.get().defaultBlockState()
      .setValue(RailBlock.SHAPE,RailShape.EAST_WEST));
    h.setBlock(EAST,TCBlocks.TRACK_STATION.get().defaultBlockState()
      .setValue(RailBlock.SHAPE,RailShape.EAST_WEST));
    h.setBlock(EAST_POWER,Blocks.REDSTONE_BLOCK);
  }
  private static boolean stations(GameTestHelper h){
    return h.getBlockState(WEST).is(TCBlocks.TRACK_STATION.get())
      &&h.getBlockState(EAST).is(TCBlocks.TRACK_STATION.get())
      &&h.getBlockState(WEST.below()).is(Blocks.STONE)
      &&h.getBlockState(EAST.below()).is(Blocks.STONE);
  }
  private static boolean continuous(GameTestHelper h){
    for(int x=3;x<=12;x++){
      BlockPos p=new BlockPos(x,1,2);
      var rail=h.getBlockState(p);
      if(!h.getBlockState(p.below()).is(Blocks.STONE)
         || !(rail.is(TCBlocks.TRACK_NORMAL.get())||rail.is(TCBlocks.TRACK_STATION.get()))
         || !rail.hasProperty(RailBlock.SHAPE)
         || rail.getValue(RailBlock.SHAPE)!=RailShape.EAST_WEST) return false;
    }
    return stations(h);
  }
  private record Pair(LegacyPoweredLocomotive gp, PassengerCoachBlue coach,
                      double originalGPX,double originalCoachX){}
  private static Pair spawn(GameTestHelper h){
    var gp=h.spawn(TCEntities.LOCOMOTIVE_DIESEL_GP7.get(),6.65f,1.1f,2.5f);
    var coach=h.spawn(TCEntities.PASSENGER_COACH_BLUE.get(),10.2f,1.1f,2.5f);
    ok(h,gp.isAlive()&&coach.isAlive(),"SPAWN_FAILURE");
    ok(h,CouplingManager.canLink(gp,gp,coach,coach),"UNSUPPORTED_PAIR");
    ok(h,CouplingManager.link(gp,gp,coach,coach),"COUPLE_FAILURE");
    ok(h,CouplingManager.areMutuallyLinked(gp,gp,coach,coach),"MUTUAL_LINK_FAILURE");
    return new Pair(gp,coach,gp.getX(),coach.getX());
  }
  private static boolean clear(GameTestHelper h,Pair p){
    BlockPos from=h.absolutePos(new BlockPos(3,0,1));
    BlockPos to=h.absolutePos(new BlockPos(13,4,4));
    AABB area=new AABB(from,to);
    return h.getLevel().getEntitiesOfClass(Entity.class,area,
      e->e!=p.gp&&e!=p.coach && (e instanceof LegacyPoweredLocomotive || e instanceof PassengerCoachBlue))
      .isEmpty();
  }
  private static Observation observe(GameTestHelper h,Pair p){
    var g=p.gp;var c=p.coach;
    return new Observation(g.getX(),g.getDeltaMovement().x,c.getX(),
      c.getBoundingBox().minX,g.getBoundingBox().maxX,
      h.absolutePos(WEST).getX(),h.absolutePos(EAST).getX(),
      g.isAlive()&&c.isAlive()&&!g.isRemoved()&&!c.isRemoved()
        &&CouplingManager.areMutuallyLinked(g,g,c,c),
      clear(h,p),continuous(h),
      h.getLevel().hasChunkAt(g.blockPosition())&&h.getLevel().hasChunkAt(c.blockPosition()),
      g.getX()>p.originalGPX-2.0&&g.getX()<p.originalGPX+4.0
        &&c.getX()>p.originalCoachX-2.0&&c.getX()<p.originalCoachX+4.0
        &&Math.abs(g.getZ()-c.getZ())<.65,
      Math.abs(g.getDeltaMovement().x)<.15&&Math.abs(c.getDeltaMovement().x)<.20,
      !g.isHandBrakeApplied(),
      h.getLevel().hasNeighborSignal(h.absolutePos(WEST)),
      h.getLevel().hasNeighborSignal(h.absolutePos(EAST)));
  }
  private static Gate gate(GameTestHelper h,Pair p,Observation o){
    return new Gate(isolated(h),continuous(h),stations(h),continuous(h),
       o.chunksLoaded(),o.corridorClear(),true,o.coupled(),
       o.handbrakeReleased(),Math.abs(o.engineDx())<.001,
       !o.westPowered(),o.eastPowered());
  }
  private static void passed(GameTestHelper h,String label){
    System.out.println(P+" CASE_PASS "+label+
      " isolatedNativeOnly=true controllerCore=true liveVillagerDispatch=false"+
      " regularWorldsAuthorized=false routeServiceCertified=false");
    h.succeed();
  }
  @GameTest(template="r30q_testbed",templateNamespace="traincraft",
      batch="traincraft.dev51b.r30q",timeoutTicks=150)
  public static void dispatchCoreDeniesMissingOrOccupiedRoute(GameTestHelper h){
    ok(h,isolated(h),"GAME_TEST_SANDBOX_REQUIRED");
    var telemetry=new Observation(6.5,0,9.5,9,7,5,9,
      true,true,true,true,true,true,true,false,true);
    var accepted=new Gate(true,true,true,true,true,true,true,true,true,true,true,true);
    ok(h,new GuardedRailwayServiceController().start(accepted,telemetry)
      ==Instruction.DEPART_WEST,"VALID_GATE_BASELINE_REJECTED");
    // Explicit fail-closed denials, with no real vehicle spawned or movement applied.
    Gate[] rejects={
      new Gate(false,true,true,true,true,true,true,true,true,true,true,true),
      new Gate(true,false,true,true,true,true,true,true,true,true,true,true),
      new Gate(true,true,true,true,true,false,true,true,true,true,true,true),
      new Gate(true,true,true,true,true,true,false,true,true,true,true,true),
      new Gate(true,true,true,true,true,true,true,false,true,true,true,true),
      new Gate(true,true,true,true,false,true,true,true,true,true,true,true)
    };
    for(int i=0;i<rejects.length;i++){
      var core=new GuardedRailwayServiceController();
      ok(h,core.start(rejects[i],telemetry)==Instruction.EMERGENCY_HOLD,
        "UNSAFE_DISPATCH_WAS_ALLOWED_CASE="+i);
      ok(h,core.phase()==Phase.HOLD,"DENIED_ROUTE_WAS_NOT_LOCKED_CASE="+i);
      ok(h,core.advance(telemetry)==Instruction.NONE,"DENIED_ROUTE_LATER_MOVED_CASE="+i);
    }
    passed(h,"dispatch_core_denies_unsafe_routes");
  }
  private static final class Run {
    final Pair pair;
    final GuardedRailwayServiceController core=new GuardedRailwayServiceController();
    int tick;
    boolean westReleased,eastArmed,westArrived,eastArrived;
    Run(Pair pair){this.pair=pair;}
  }
  private static void apply(GameTestHelper h,Run run,Instruction instruction){
    switch(instruction){
      case DEPART_WEST -> run.pair.gp.setDeltaMovement(-.065,0,0);
      case RELEASE_AND_RETURN_EAST -> {
        ok(h,!run.westReleased,"DUPLICATE_RETURN_AUTHORIZATION");
        h.setBlock(WEST_POWER,Blocks.REDSTONE_BLOCK);
        ok(h,h.getLevel().hasNeighborSignal(h.absolutePos(WEST)),"WEST_RELEASE_POWER_FAILED");
        run.pair.gp.setDeltaMovement(+.060,0,0);
        run.westReleased=true;
        System.out.println(P+" CONTROL_RETURN_COMMAND tick="+run.tick);
      }
      case ARM_EAST_STOP -> {
        ok(h,!run.eastArmed,"DUPLICATE_STATION_ARM");
        h.setBlock(EAST_POWER,Blocks.AIR);
        ok(h,!h.getLevel().hasNeighborSignal(h.absolutePos(EAST)),"EAST_ARMING_FAILED");
        run.eastArmed=true;
        System.out.println(P+" CONTROL_EAST_ARMED tick="+run.tick);
      }
      case COMPLETE -> {
        ok(h,run.westArrived&&run.eastArrived&&run.westReleased&&run.eastArmed,
          "INCOMPLETE_SERVICE_WAS_CERTIFIED");
        ok(h,Math.abs(run.pair.gp.getDeltaMovement().x)<.0005,"FINAL_STATION_NOT_STOPPED");
        passed(h,"dispatch_core_gp7_coach_two_station_journey");
      }
      case EMERGENCY_HOLD -> {
        run.pair.gp.setDeltaMovement(0,0,0);
        run.pair.coach.setDeltaMovement(0,0,0);
        h.fail(P+" HOLD controller="+run.core.holdReason()+" tick="+run.tick);
      }
      case NONE -> {}
    }
  }
  private static void cycle(GameTestHelper h,Run run){
    ++run.tick;
    Observation o=observe(h,run.pair);
    Phase previous=run.core.phase();
    Instruction action=run.core.advance(o);
    Phase current=run.core.phase();
    if(previous!=current){
      System.out.println(P+" CONTROL_PHASE tick="+run.tick+
        " from="+previous+" to="+current+
        " x="+o.engineX()+" dx="+o.engineDx()+
        " linked="+o.coupled()+" corridorClear="+o.corridorClear());
      if(current==Phase.WEST_DWELL)run.westArrived=true;
      if(current==Phase.EAST_DWELL)run.eastArrived=true;
    }
    apply(h,run,action);
    if(current==Phase.COMPLETE)return;
    if(run.tick>=125){h.fail(P+" HOLD CONTROLLER_NATIVE_TIMEOUT");return;}
    h.runAfterDelay(1,()->cycle(h,run));
  }
  @GameTest(template="r30q_testbed",templateNamespace="traincraft",
      batch="traincraft.dev51b.r30q",timeoutTicks=150)
  public static void guardedControllerDrivesIsolatedGp7CoachRoundTrip(GameTestHelper h){
    ok(h,isolated(h),"GAME_TEST_SANDBOX_REQUIRED");
    prepare(h);
    ok(h,continuous(h),"ROUTE_NOT_CONNECTED");
    h.runAfterDelay(3,()->{
      ok(h,continuous(h),"ROUTE_MUTATED_BEFORE_TEST");
      Run run=new Run(spawn(h));
      Observation o=observe(h,run.pair);
      Gate pre=gate(h,run.pair,o);
      ok(h,pre.pass(),"REAL_ROUTE_START_GATE_DENIED");
      Instruction initial=run.core.start(pre,o);
      ok(h,initial==Instruction.DEPART_WEST,"CONTROLLER_DID_NOT_AUTHORIZE_DEPARTURE");
      apply(h,run,initial);
      h.runAfterDelay(1,()->cycle(h,run));
    });
  }
}
