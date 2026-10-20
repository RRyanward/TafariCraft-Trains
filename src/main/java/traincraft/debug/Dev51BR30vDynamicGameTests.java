package traincraft.debug;

import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.block.track.ClassicSpecialTrackBlock;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.registry.TCBlocks;
import traincraft.registry.TCEntities;

/** DEV51B R30v: real per-tick GP7 approach observations in ONLY a disposable
 * Forge GameTestServer. Never touches saved development worlds, worldgen,
 * original coupled consist, locomotive physics, or the production station block.
 * Tests do NOT manually call entityInside; only actual native entity ticks count.
 * Physical impact is not certified from a static buffer collision shape.
 */
@GameTestHolder("traincraft")
@PrefixGameTestTemplate(false)
public final class Dev51BR30vDynamicGameTests {
  private Dev51BR30vDynamicGameTests() {}
  private static final String P="[TC-DEV51B-R30V-NATIVE]";
  private static final BlockPos STATION=new BlockPos(7,1,2);
  private static final BlockPos BUFFER=new BlockPos(3,1,2);
  private static final int MAX_TICKS=60;
  private static void check(GameTestHelper h,boolean valid,String why) {
    if(!valid)h.fail(P+" HOLD "+why);
  }
  private static void requireIsolated(GameTestHelper h) {
    Path wd=Path.of(System.getProperty("user.dir","")).toAbsolutePath().normalize();
    Path parent=wd.getParent();
    check(h,h.getLevel().getServer() instanceof GameTestServer && parent!=null
      &&wd.getFileName()!=null&&wd.getFileName().toString().startsWith("native-runtime-")
      &&parent.getFileName()!=null&&parent.getFileName().toString().equals("_DEV51B_R30Q"),
      "ISOLATED_R30Q_GAMETESTSERVER_REQUIRED");
  }
  private static void pass(GameTestHelper h,String id) {
    System.out.println(P+" CASE_PASS "+id+
      " NATIVE_FORGE_GP7_TICKS_ONLY userWorldAuthority=false fullRouteCertified=false");
    h.succeed();
  }
  private static void buildLine(GameTestHelper h,boolean station,boolean power,boolean buffer){
    // Stone supports MUST exist before rails are placed; both stations retained.
    for(int x=3;x<=12;x++)h.setBlock(new BlockPos(x,0,2),Blocks.STONE);
    for(int x=(buffer?4:3);x<=12;x++) {
      BlockState state=(station&&x==7?TCBlocks.TRACK_STATION.get():TCBlocks.TRACK_NORMAL.get())
        .defaultBlockState().setValue(RailBlock.SHAPE,RailShape.EAST_WEST);
      h.setBlock(new BlockPos(x,1,2),state);
    }
    if(power)h.setBlock(new BlockPos(7,1,1),Blocks.REDSTONE_BLOCK);
    if(buffer)h.setBlock(BUFFER,TCBlocks.STOPPER.get().defaultBlockState()
        .setValue(HorizontalDirectionalBlock.FACING,Direction.EAST));
  }
  private static void checkStation(GameTestHelper h,boolean powered){
    BlockPos at=h.absolutePos(STATION);
    BlockState state=h.getLevel().getBlockState(at);
    check(h,h.getLevel().getBlockState(at.below()).is(Blocks.STONE),"STATION_FOUNDATION_DISAPPEARED");
    check(h,state.getBlock()==TCBlocks.TRACK_STATION.get(),"STATION_BLOCK_LOST actual="+
      ForgeRegistries.BLOCKS.getKey(state.getBlock()));
    check(h,state.getBlock() instanceof ClassicSpecialTrackBlock,"STATION_CLASS_INVALID");
    check(h,state.hasProperty(ClassicSpecialTrackBlock.ACTIVE),"STATION_ACTIVE_MISSING");
    check(h,state.hasProperty(RailBlock.SHAPE)&&state.getValue(RailBlock.SHAPE)==RailShape.EAST_WEST,
      "STATION_SHAPE_CHANGED");
    check(h,h.getLevel().hasNeighborSignal(at)==powered,"STATION_POWER_WRONG");
    check(h,(h.getLevel().getBestNeighborSignal(at)>0)==powered,"STATION_SIGNAL_WRONG");
    check(h,state.getValue(ClassicSpecialTrackBlock.ACTIVE)==powered,"STATION_ACTIVE_WRONG");
  }
  private static final class Probe {
    final double startX,startZ;int tick,steady;
    boolean contact;double minX,maxWest,closest=Double.POSITIVE_INFINITY;
    Probe(LegacyPoweredLocomotive gp){startX=gp.getX();startZ=gp.getZ();minX=startX;}
  }
  private static LegacyPoweredLocomotive spawnImpulse(GameTestHelper h,double localX,double velocity){
    LegacyPoweredLocomotive gp=h.spawn(TCEntities.LOCOMOTIVE_DIESEL_GP7.get(),
        (float)localX,1.1f,2.5f);
    check(h,!gp.isRemoved()&&gp.getType()==TCEntities.LOCOMOTIVE_DIESEL_GP7.get(),
      "GP7_NOT_ACTUALLY_SPAWNED");
    check(h,!gp.isHandBrakeApplied(),"GP7_HAND_BRAKE_ACTIVE");
    gp.setDeltaMovement(velocity,0.0,0.0); // single bounded impulse; no auto-drive or fake movement
    return gp;
  }
  private static void basicTick(GameTestHelper h,LegacyPoweredLocomotive gp,Probe p){
    check(h,!gp.isRemoved()&&gp.isAlive(),"GP7_REMOVED_TICK="+p.tick);
    check(h,Double.isFinite(gp.getX())&&Double.isFinite(gp.getZ())
      &&Double.isFinite(gp.getDeltaMovement().x),"NONFINITE_MOTION_TICK="+p.tick);
    check(h,Math.abs(gp.getZ()-p.startZ)<0.40,"GP7_LEFT_CENTERLINE_TICK="+p.tick);
    check(h,gp.getX()<p.startX+0.15&&gp.getX()>p.startX-2.8,
      "GP7_OUTSIDE_BOUNDED_PROBE_TICK="+p.tick+" x="+gp.getX());
    check(h,gp.getDeltaMovement().horizontalDistance()<0.13,
      "GP7_OVERSPEED_TICK="+p.tick);
    p.minX=Math.min(p.minX,gp.getX());p.maxWest=p.startX-p.minX;
  }
  private static void stationTick(GameTestHelper h,LegacyPoweredLocomotive gp,
                                  Probe p,boolean powered){
    ++p.tick;
    checkStation(h,powered);
    basicTick(h,gp,p);
    BlockPos abs=h.absolutePos(STATION);
    boolean overlaps=gp.getBoundingBox().intersects(new AABB(abs));
    if(overlaps)p.contact=true;
    double speed=gp.getDeltaMovement().horizontalDistance();
    if(p.contact&&speed<0.0045)p.steady++;else p.steady=0;
    if(p.tick<=3||p.tick%5==0||p.steady==4){
      System.out.println(P+" STATION_APPROACH tick="+p.tick+" powered="+powered+
        " initialX="+p.startX+" x="+gp.getX()+" westTravel="+p.maxWest+
        " speed="+speed+" touchedBlock="+p.contact+
        " currentOverlap="+overlaps+" steady="+p.steady+
        " handbrake="+gp.isHandBrakeApplied());
    }
    if(powered){
      if(p.tick>=22){
        check(h,p.contact,"POWERED_GP7_DID_NOT_REACH_STATION_BLOCK");
        check(h,p.maxWest>0.25,"POWERED_GP7_DID_NOT_TRAVEL");
        check(h,gp.getDeltaMovement().x< -0.005,"POWERED_STATION_BLOCKED_DYNAMIC_GP7");
        pass(h,"gp7_powered_station_dynamic_control");return;
      }
    }else if(p.contact && p.maxWest>0.10 && p.steady>=4){
      check(h,gp.getX()>h.absoluteVec(new Vec3(6.0,1.1,2.5)).x,
        "UNPOWERED_GP7_PASSED_THROUGH_STATION");
      pass(h,"gp7_unpowered_station_dynamic_stop");return;
    }
    if(p.tick>=MAX_TICKS){
      h.fail(P+" HOLD STATION_DYNAMIC_TIMEOUT powered="+powered+
        " westTravel="+p.maxWest+" contact="+p.contact+
        " speed="+speed+" steady="+p.steady);
      return;
    }
    h.runAfterDelay(1,()->stationTick(h,gp,p,powered));
  }
  @GameTest(template="r30q_testbed",templateNamespace="traincraft",
      batch="traincraft.dev51b.r30q",timeoutTicks=105)
  public static void gp7UnpoweredStationDynamicStop(GameTestHelper h){
    requireIsolated(h);
    buildLine(h,true,false,false);
    h.runAfterDelay(3,()->{
      checkStation(h,false);
      LegacyPoweredLocomotive gp=spawnImpulse(h,8.65,-0.065);
      Probe p=new Probe(gp);
      check(h,!gp.getBoundingBox().intersects(new AABB(h.absolutePos(STATION))),
        "GP7_STARTED_INSIDE_STATION_RAIL");
      h.runAfterDelay(1,()->stationTick(h,gp,p,false));
    });
  }
  @GameTest(template="r30q_testbed",templateNamespace="traincraft",
      batch="traincraft.dev51b.r30q",timeoutTicks=105)
  public static void gp7PoweredStationDynamicControl(GameTestHelper h){
    requireIsolated(h);
    buildLine(h,true,true,false);
    h.runAfterDelay(3,()->{
      checkStation(h,true);
      LegacyPoweredLocomotive gp=spawnImpulse(h,8.65,-0.065);
      Probe p=new Probe(gp);
      check(h,!gp.getBoundingBox().intersects(new AABB(h.absolutePos(STATION))),
        "GP7_STARTED_INSIDE_POWERED_STATION_RAIL");
      h.runAfterDelay(1,()->stationTick(h,gp,p,true));
    });
  }
  private static void bufferTick(GameTestHelper h,LegacyPoweredLocomotive gp,
                                 Probe p,double faceX){
    ++p.tick;
    BlockPos at=h.absolutePos(BUFFER);
    BlockState state=h.getLevel().getBlockState(at);
    check(h,state.getBlock()==TCBlocks.STOPPER.get(),"BUFFER_DISAPPEARED");
    check(h,state.getValue(HorizontalDirectionalBlock.FACING)==Direction.EAST,
      "BUFFER_CHANGED_ORIENTATION");
    check(h,!state.getCollisionShape(h.getLevel(),at).isEmpty(),"BUFFER_LOST_COLLISION");
    basicTick(h,gp,p);
    double clearance=gp.getBoundingBox().minX-faceX;
    p.closest=Math.min(p.closest,clearance);
    check(h,clearance>=-0.045,"GP7_PENETRATED_BUFFER clearance="+clearance+
      " tick="+p.tick+" velocity="+gp.getDeltaMovement());
    if(clearance<=0.095)p.contact=true;
    double speed=gp.getDeltaMovement().horizontalDistance();
    if(p.contact&&speed<0.005)p.steady++;else p.steady=0;
    if(p.tick<=3||p.tick%5==0||p.steady==4){
      System.out.println(P+" BUFFER_APPROACH tick="+p.tick+" x="+gp.getX()+
        " westTravel="+p.maxWest+" clearance="+clearance+
        " closest="+p.closest+" nearBuffer="+p.contact+
        " speed="+speed+" steady="+p.steady);
    }
    if(p.contact&&p.maxWest>0.15&&p.steady>=4){
      pass(h,"gp7_native_buffer_contact_and_stop");return;
    }
    if(p.tick>=MAX_TICKS){
      h.fail(P+" HOLD BUFFER_APPROACH_TIMEOUT westTravel="+p.maxWest+
        " closest="+p.closest+" contact="+p.contact+
        " speed="+speed+" steady="+p.steady);
      return;
    }
    h.runAfterDelay(1,()->bufferTick(h,gp,p,faceX));
  }
  @GameTest(template="r30q_testbed",templateNamespace="traincraft",
      batch="traincraft.dev51b.r30q",timeoutTicks=105)
  public static void gp7NativeBufferContactAndStop(GameTestHelper h){
    requireIsolated(h);
    buildLine(h,false,false,true);
    h.runAfterDelay(3,()->{
      BlockPos at=h.absolutePos(BUFFER);
      BlockState state=h.getLevel().getBlockState(at);
      check(h,state.getBlock()==TCBlocks.STOPPER.get(),"BUFFER_NOT_PRESENT");
      check(h,!state.getCollisionShape(h.getLevel(),at).isEmpty(),"BUFFER_NOT_SOLID");
      double faceX=at.getX()+state.getCollisionShape(h.getLevel(),at).bounds().maxX;
      LegacyPoweredLocomotive gp=spawnImpulse(h,4.85,-0.075);
      double gap=gp.getBoundingBox().minX-faceX;
      check(h,gap>0.15&&gap<0.85,"GP7_BUFFER_SPAWN_GAP_INVALID="+gap);
      System.out.println(P+" BUFFER_INITIAL worldBufferFaceX="+faceX+
        " initialGP7X="+gp.getX()+" gap="+gap+
        " buffer="+ForgeRegistries.BLOCKS.getKey(state.getBlock()));
      Probe p=new Probe(gp);
      h.runAfterDelay(1,()->bufferTick(h,gp,p,faceX));
    });
  }
}
