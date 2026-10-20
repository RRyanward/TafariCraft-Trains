package traincraft.debug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import java.nio.file.Path;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import traincraft.block.track.ClassicSpecialTrackBlock;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.registry.TCBlocks;
import traincraft.registry.TCEntities;

/** R30u: supported-station variant of the isolated R30q GameTest class. No user-world commands.
 * All cases use isolated structure templates and must fail closed.
 * A block shape or simulated impulse is NOT a real buffer collision certificate.
 */
@GameTestHolder("traincraft")
@PrefixGameTestTemplate(false)
public final class Dev51BR30qNativeGameTests {
 private Dev51BR30qNativeGameTests() {}
 private static final String PREFIX="[TC-DEV51B-R30Q-NATIVE]";
 private static final BlockPos STATION=new BlockPos(7,1,2);
 private static void ok(GameTestHelper h,boolean test,String why){if(!test)h.fail(PREFIX+" HOLD "+why);}
 private static void requireIsolated(GameTestHelper h){
   Path wd=Path.of(System.getProperty("user.dir", "")).toAbsolutePath().normalize();
   Path parent=wd.getParent();
   ok(h,h.getLevel().getServer() instanceof GameTestServer && parent!=null
     && wd.getFileName()!=null&&wd.getFileName().toString().startsWith("native-runtime-")
     && parent.getFileName()!=null&&parent.getFileName().toString().equals("_DEV51B_R30Q"),
     "REQUIRES_DEDICATED_GAMETEST_IN_ISOLATED_R30Q_DIRECTORY");
 }
 private static void pass(GameTestHelper h,String id){
    System.out.println(PREFIX+" CASE_PASS "+id+" NATIVE_FORGE_TESTBED_ONLY bufferStopCertified=false noUserSaveAccess=true");
    h.succeed();
 }
 private static void stationEvidence(GameTestHelper h,String stage,BlockPos at,Minecart cart,Vec3 prior){
    var level=h.getLevel();
    BlockState state=level.getBlockState(at);
    var name=ForgeRegistries.BLOCKS.getKey(state.getBlock());
    String active=state.hasProperty(ClassicSpecialTrackBlock.ACTIVE)
      ?String.valueOf(state.getValue(ClassicSpecialTrackBlock.ACTIVE)):"<absent>";
    BlockState below=level.getBlockState(at.below());
    System.out.println(PREFIX+" STATION_EVIDENCE stage="+stage+" pos="+at
      +" block="+name+" class="+state.getBlock().getClass().getName()
      +" railShape="+(state.hasProperty(RailBlock.SHAPE)?state.getValue(RailBlock.SHAPE):"<absent>")
      +" active="+active+" neighborSignal="+level.hasNeighborSignal(at)
      +" bestSignal="+level.getBestNeighborSignal(at)
      +" support="+ForgeRegistries.BLOCKS.getKey(below.getBlock())
      +" cart="+cart.getClass().getName()+" minecart="+(cart instanceof AbstractMinecart)
      +" before="+prior+" after="+cart.getDeltaMovement());
 }
 private static void requireStation(GameTestHelper h,BlockPos at,boolean expectedPower){
    var level=h.getLevel();BlockState state=level.getBlockState(at);
    ok(h,level.getBlockState(at.below()).is(Blocks.STONE),
      "STATION_SUPPORT_NOT_STONE_ID="+ForgeRegistries.BLOCKS.getKey(level.getBlockState(at.below()).getBlock()));
    ok(h,state.getBlock()==TCBlocks.TRACK_STATION.get(),
      "STATION_BLOCK_NOT_PRESENT_ID="+ForgeRegistries.BLOCKS.getKey(state.getBlock()));
    ok(h,state.getBlock() instanceof ClassicSpecialTrackBlock,"STATION_NATIVE_CLASS_MISSING");
    ok(h,state.hasProperty(ClassicSpecialTrackBlock.ACTIVE),"STATION_ACTIVE_PROPERTY_MISSING");
    ok(h,state.hasProperty(RailBlock.SHAPE),"STATION_RAIL_SHAPE_MISSING");
    ok(h,state.getValue(RailBlock.SHAPE)==RailShape.EAST_WEST,"STATION_RAIL_SHAPE_NOT_EW");
    ok(h,level.hasNeighborSignal(at)==expectedPower,"STATION_NEIGHBOR_POWER_UNEXPECTED");
    ok(h,(level.getBestNeighborSignal(at)>0)==expectedPower,"STATION_BEST_SIGNAL_MISMATCH");
 }
 private static void rail(GameTestHelper h, BlockPos p,boolean station){
    BlockState s=(station?TCBlocks.TRACK_STATION.get():TCBlocks.TRACK_NORMAL.get())
       .defaultBlockState().setValue(RailBlock.SHAPE,RailShape.EAST_WEST);
    h.setBlock(p,s);
 }
 /** R30u: Minecraft rails need a solid block directly below them. Prepare the
  * independent GameTest foundation BEFORE rail placement so neighbor updates
  * cannot immediately replace a floating station rail with air. No production
  * rails or development saves are accessed here. */
 private static void line(GameTestHelper h){
    for(int x=3;x<=12;x++)h.setBlock(new BlockPos(x,0,2),Blocks.STONE);
    for(int x=3;x<=12;x++)rail(h,new BlockPos(x,1,2),x==7);
 }
 @GameTest(template="r30q_testbed",templateNamespace="traincraft",batch="traincraft.dev51b.r30q",timeoutTicks=80)
 public static void unpoweredStationNativeStop(GameTestHelper h){
   requireIsolated(h);
   line(h);
   h.runAfterDelay(3,()->{
     var level=h.getLevel();BlockPos at=h.absolutePos(STATION);
     Minecart cart=h.spawn(EntityType.MINECART,7.5f,1.1f,2.5f);
     cart.setDeltaMovement(-0.06,0,0);
     Vec3 before=cart.getDeltaMovement();
     stationEvidence(h,"UNPOWERED_BEFORE",at,cart,before);
     requireStation(h,at,false); // fail before a false PASS if rail was lost or replaced
     ok(h,before.horizontalDistance()>0.05,"UNPOWERED_CART_IMPULSE_ABSENT");
     level.getBlockState(at).getBlock().entityInside(level.getBlockState(at),level,at,cart);
     stationEvidence(h,"UNPOWERED_AFTER",at,cart,before);
     ok(h,cart.getDeltaMovement().horizontalDistance()<1e-8,
       "UNPOWERED_STATION_DID_NOT_STOP_NATIVE_CART");
     pass(h,"unpowered_station_native_stop");
   });
 }
 @GameTest(template="r30q_testbed",templateNamespace="traincraft",batch="traincraft.dev51b.r30q",timeoutTicks=80)
 public static void poweredStationNativePass(GameTestHelper h){
   requireIsolated(h);
   line(h);h.setBlock(new BlockPos(7,1,1),Blocks.REDSTONE_BLOCK);
   h.runAfterDelay(3,()->{
     var level=h.getLevel();BlockPos at=h.absolutePos(STATION);
     Minecart cart=h.spawn(EntityType.MINECART,7.5f,1.1f,2.5f);
     cart.setDeltaMovement(-0.06,0,0);
     Vec3 before=cart.getDeltaMovement();
     stationEvidence(h,"POWERED_BEFORE",at,cart,before);
     requireStation(h,at,true);
     ok(h,before.horizontalDistance()>0.05,"POWERED_CART_IMPULSE_ABSENT");
     level.getBlockState(at).getBlock().entityInside(level.getBlockState(at),level,at,cart);
     stationEvidence(h,"POWERED_AFTER",at,cart,before);
     ok(h,Math.abs(cart.getDeltaMovement().x-before.x)<1e-8,
       "POWERED_STATION_UNEXPECTED_STOP");
     pass(h,"powered_station_native_pass");
   });
 }
 @GameTest(template="r30q_testbed",templateNamespace="traincraft",batch="traincraft.dev51b.r30q",timeoutTicks=80)
 public static void bufferCollisionShapeNative(GameTestHelper h){
   requireIsolated(h);
   BlockPos buffer=new BlockPos(3,1,2);
   rail(h,new BlockPos(4,1,2),false);
   h.setBlock(buffer,TCBlocks.STOPPER.get().defaultBlockState()
     .setValue(HorizontalDirectionalBlock.FACING,Direction.EAST));
   h.runAfterDelay(2,()->{
     var level=h.getLevel();BlockPos abs=h.absolutePos(buffer);
     var state=level.getBlockState(abs);
     ok(h,state.getBlock()==TCBlocks.STOPPER.get(),"BUFFER_NOT_REGISTERED");
     ok(h,!state.getCollisionShape(level,abs).isEmpty(),"BUFFER_COLLISION_SHAPE_EMPTY");
     ok(h,state.getValue(HorizontalDirectionalBlock.FACING)==Direction.EAST,"BUFFER_FACING_MISMATCH");
     pass(h,"buffer_native_collision_shape_only");
   });
 }
 @GameTest(template="r30q_testbed",templateNamespace="traincraft",batch="traincraft.dev51b.r30q",timeoutTicks=80)
 public static void gp7NativeSpawn(GameTestHelper h){
   requireIsolated(h);
   for(int x=4;x<=12;x++)rail(h,new BlockPos(x,1,2),false);
   LegacyPoweredLocomotive gp=h.spawn(TCEntities.LOCOMOTIVE_DIESEL_GP7.get(),8.5f,1.1f,2.5f);
   h.runAfterDelay(10,()->{
     ok(h,!gp.isRemoved(),"GP7_REMOVED_AFTER_SPAWN");
     ok(h,gp.getType()==TCEntities.LOCOMOTIVE_DIESEL_GP7.get(),"GP7_TYPE_MISMATCH");
     ok(h,Double.isFinite(gp.getX())&&Double.isFinite(gp.getZ()),"GP7_NONFINITE_POSITION");
     pass(h,"gp7_native_spawn_stationary");
   });
 }
 @GameTest(template="r30q_testbed",templateNamespace="traincraft",batch="traincraft.dev51b.r30q",timeoutTicks=100)
 public static void gp7NativeCoastProbe(GameTestHelper h){
   requireIsolated(h);
   for(int x=3;x<=12;x++)rail(h,new BlockPos(x,1,2),false);
   LegacyPoweredLocomotive gp=h.spawn(TCEntities.LOCOMOTIVE_DIESEL_GP7.get(),8.5f,1.1f,2.5f);
   double start=gp.getX();
   gp.setDeltaMovement(-0.045,0,0);
   h.runAfterDelay(8,()->{
     double delta=start-gp.getX();
     System.out.println(PREFIX+" GP7_MOTION_PROBE initialX="+start+" finalX="+gp.getX()+
       " movedWest="+delta+" finalVelocity="+gp.getDeltaMovement()+" handbrake="+gp.isHandBrakeApplied());
     ok(h,!gp.isRemoved(),"GP7_DISAPPEARED");
     // GameTestHelper#spawn uses LOCAL coordinates; gp.getX() uses WORLD coordinates.
     // The prior R30q compared world X (~46.5) to local 4..12 and failed falsely.
     double west=h.absoluteVec(new Vec3(3.0,1.1,2.5)).x;
     double east=h.absoluteVec(new Vec3(12.0,1.1,2.5)).x;
     double westLimit=Math.min(west,east);
     double eastLimit=Math.max(west,east);
     double trackZ=h.absoluteVec(new Vec3(8.5,1.1,2.5)).z;
     System.out.println(PREFIX+" GP7_ROUTE_EVIDENCE worldWestLimit="+westLimit+
       " worldEastLimit="+eastLimit+" centerZ="+trackZ+
       " initialX="+start+" finalX="+gp.getX()+" finalZ="+gp.getZ()+
       " movedWest="+delta+" handbrake="+gp.isHandBrakeApplied());
     ok(h,Double.isFinite(delta)&&Double.isFinite(gp.getX())&&Double.isFinite(gp.getZ())
       &&gp.getX()>westLimit+0.05&&gp.getX()<eastLimit-0.05
       &&Math.abs(gp.getZ()-trackZ)<1.0,
       "GP7_OUTSIDE_TRANSLATED_ISOLATED_ROUTE");
     // This is a native impulse probe, NOT a piloted acceleration/braking or buffer collision test.
     ok(h,delta>0.01,"GP7_IMPULSE_DID_NOT_CREATE_WESTWARD_MOVEMENT");
     pass(h,"gp7_native_impulse_movement_only");
   });
 }
}
