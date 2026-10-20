package traincraft.debug;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import traincraft.registry.TCBlocks;
import traincraft.service.ReadOnlyRailwayRouteAudit;
import traincraft.service.ReadOnlyRailwayRouteAudit.Reason;

/** Synthetic NBT fixture matching actual DEV50 StationData keys; no saved worlds are edited. */
@GameTestHolder("traincraft")
@PrefixGameTestTemplate(false)
public final class Dev51BR33ReadOnlyRouteGameTests {
  private static final String P="[TC-DEV51B-R33-NATIVE]";
  private static final BlockPos WEST=new BlockPos(5,1,2),EAST=new BlockPos(9,1,2);
  private Dev51BR33ReadOnlyRouteGameTests(){}
  private static void ok(GameTestHelper h,boolean condition,String why){
    if(!condition)h.fail(P+" HOLD "+why);
  }
  private static boolean sandbox(GameTestHelper h){
    Path cwd=Path.of(System.getProperty("user.dir","")).toAbsolutePath().normalize();
    Path parent=cwd.getParent();
    return h.getLevel().getServer() instanceof GameTestServer
      && cwd.getFileName()!=null&&cwd.getFileName().toString().startsWith("native-runtime-")
      && parent!=null&&parent.getFileName()!=null&&parent.getFileName().toString().equals("_DEV51B_R30Q");
  }
  private static void straight(GameTestHelper h){
    for(int x=5;x<=9;x++){
      BlockPos p=new BlockPos(x,1,2);
      h.setBlock(p.below(),Blocks.STONE);
      h.setBlock(p,TCBlocks.TRACK_NORMAL.get().defaultBlockState()
        .setValue(RailBlock.SHAPE,RailShape.EAST_WEST));
    }
    h.setBlock(WEST,TCBlocks.TRACK_STATION.get().defaultBlockState()
      .setValue(RailBlock.SHAPE,RailShape.EAST_WEST));
    h.setBlock(EAST,TCBlocks.TRACK_STATION.get().defaultBlockState()
      .setValue(RailBlock.SHAPE,RailShape.EAST_WEST));
  }
  private static CompoundTag fixture(GameTestHelper h){
    BlockPos a=h.absolutePos(WEST),b=h.absolutePos(EAST);
    CompoundTag tag=new CompoundTag();
    long[] both={a.asLong(),b.asLong()};
    tag.putLongArray("Stations",both);
    tag.putLongArray("Networks",both);
    tag.putLongArray("WideNetworks",both);
    CompoundTag service=new CompoundTag();service.putLong("Station",a.asLong());
    service.putInt("Profile",0);service.putInt("Coaches",1);service.putInt("Rerolls",0);
    ListTag services=new ListTag();services.add(service);tag.put("StationServices",services);
    CompoundTag staged=new CompoundTag();staged.putLong("Station",a.asLong());
    staged.putUUID("Locomotive",UUID.fromString("11111111-1111-4111-8111-111111111111"));
    staged.putUUID("Coach",UUID.fromString("22222222-2222-4222-8222-222222222222"));
    ListTag staging=new ListTag();staging.add(staged);tag.put("StationStagedConsists",staging);
    return tag;
  }
  private static void pass(GameTestHelper h,String name){
    System.out.println(P+" CASE_PASS "+name+" isolatedNativeOnly=true syntheticMetadataFixture=true"+
      " realGeneratedRouteCertified=false productionDispatchAuthorized=false chunksForced=0 blocksChangedInUserWorld=0");
    h.succeed();
  }
  @GameTest(template="r30q_testbed",templateNamespace="traincraft",
    batch="traincraft.dev51b.r30q",timeoutTicks=100)
  public static void readOnlyWorldgenRecordAndStraightAudit(GameTestHelper h){
    ok(h,sandbox(h),"ISOLATION_REQUIRED");
    straight(h);
    CompoundTag data=fixture(h);
    BlockPos a=h.absolutePos(WEST),b=h.absolutePos(EAST);
    CompoundTag existing=ReadOnlyRailwayRouteAudit.existingWorldgenRecord(h.getLevel());
    System.out.println(P+" SAVED_RECORD_READ noRecordCreated=true present="+(existing!=null));
    var valid=ReadOnlyRailwayRouteAudit.inspect(h.getLevel(),data,a,b,Set.of());
    ok(h,valid.preliminaryReadiness()&&valid.checked()==5&&!valid.productionDispatchCertified(),
      "STRAIGHT_FIXTURE_SURVEY_FAILED_"+valid.reason());
    var absent=ReadOnlyRailwayRouteAudit.inspect(h.getLevel(),null,a,b,Set.of());
    ok(h,absent.reason()==Reason.NO_RECORD,"MISSING_WORLGEN_RECORD_NOT_DENIED");
    CompoundTag broken=data.copy();broken.remove("WideNetworks");
    var noNetwork=ReadOnlyRailwayRouteAudit.inspect(h.getLevel(),broken,a,b,Set.of());
    ok(h,!noNetwork.preliminaryReadiness(),"UNRECORDED_NETWORK_ACCEPTED");
    System.out.println(P+" WORLDGEN_SCHEMA source=RailwayVillageWorldgen.StationData"+
      " Stations=2 Networks=2 WideNetworks=2 service=1 staged=1"+
      " routeCells="+valid.checked()+" certificate=PRELIMINARY_STRAIGHT_ONLY");
    pass(h,"read_only_worldgen_station_record_and_straight_audit");
  }
  @GameTest(template="r30q_testbed",templateNamespace="traincraft",
    batch="traincraft.dev51b.r30q",timeoutTicks=100)
  public static void readOnlyRejectsGapJunctionAndOccupancy(GameTestHelper h){
    ok(h,sandbox(h),"ISOLATION_REQUIRED");
    straight(h);
    CompoundTag data=fixture(h);
    BlockPos a=h.absolutePos(WEST),b=h.absolutePos(EAST);
    h.setBlock(new BlockPos(7,1,2),Blocks.AIR);
    var gap=ReadOnlyRailwayRouteAudit.inspect(h.getLevel(),data,a,b,Set.of());
    ok(h,gap.reason()==Reason.GAP_OR_UNCERTIFIED_RAIL,"GAP_NOT_REJECTED_"+gap.reason());
    // R33a: this is a STATE-FIXTURE, not a track-item placement test.
    // GameTestHelper.setBlock can notify adjacent rails and rotate NORTH_SOUTH
    // back to EAST_WEST. Same-block state overwrite + UPDATE_KNOWN_SHAPE (16)
    // and UPDATE_CLIENTS (2) keeps our deliberate invalid shape for the audit.
    // Confirm what the live ServerLevel actually stores BEFORE judging auditor.
    BlockPos localShape=new BlockPos(7,1,2);
    BlockPos absoluteShape=h.absolutePos(localShape);
    BlockState wantShape=TCBlocks.TRACK_NORMAL.get().defaultBlockState()
      .setValue(RailBlock.SHAPE,RailShape.NORTH_SOUTH);
    // The first set may recalculate the rail shape as a placement side effect.
    h.getLevel().setBlock(absoluteShape,wantShape,18);
    // Re-apply with identical rail block, so onPlace cannot pick E/W neighbors.
    h.getLevel().setBlock(absoluteShape,wantShape,18);
    BlockState actualShape=h.getLevel().getBlockState(absoluteShape);
    System.out.println(P+" FIXTURE_SHAPE pos="+absoluteShape+
      " requested="+wantShape+" observed="+actualShape+
      " stable="+wantShape.equals(actualShape)+" updateFlags=18");
    ok(h,wantShape.equals(actualShape),"FIXTURE_SHAPE_NOT_STABLE_"+actualShape);
    var shape=ReadOnlyRailwayRouteAudit.inspect(h.getLevel(),data,a,b,Set.of());
    ok(h,shape.reason()==Reason.CURVE_OR_JUNCTION_UNCERTIFIED,
      "AUDITOR_FAILED_TO_REJECT_VERIFIED_NORTH_SOUTH_"+shape.reason());
    System.out.println(P+" FIXTURE_LOCKED shape=NORTH_SOUTH reason="+shape.reason()+
      " checked="+shape.checked()+" authorized="+shape.productionDispatchCertified());
    // Restore the original E/W corridor, verifying state before occupancy test.
    BlockState wantStraight=TCBlocks.TRACK_NORMAL.get().defaultBlockState()
      .setValue(RailBlock.SHAPE,RailShape.EAST_WEST);
    h.getLevel().setBlock(absoluteShape,wantStraight,18);
    BlockState actualStraight=h.getLevel().getBlockState(absoluteShape);
    ok(h,wantStraight.equals(actualStraight),"RESTORE_STRAIGHT_FIXTURE_FAILED_"+actualStraight);
    var cart=h.spawn(EntityType.MINECART,7.5f,1.1f,2.5f);
    var occupied=ReadOnlyRailwayRouteAudit.inspect(h.getLevel(),data,a,b,Set.of());
    ok(h,occupied.reason()==Reason.OCCUPIED,"OCCUPANCY_NOT_REJECTED_"+occupied.reason());
    var forged=ReadOnlyRailwayRouteAudit.inspect(h.getLevel(),data,a,b,Set.of(cart.getUUID()));
    ok(h,forged.reason()==Reason.INVALID_RECORD,"UNRECORDED_CONSIST_BYPASSED_"+forged.reason());
    ok(h,!occupied.productionDispatchCertified()&&!forged.productionDispatchCertified(),
      "MISSING_PRODUCTION_HOLD");
    System.out.println(P+" DENY_REASONS gap="+gap.reason()+" shape="+shape.reason()+
      " occupancy="+occupied.reason()+" authorized=false verifiedShapeFixture=true");
    pass(h,"read_only_route_denies_gap_shape_and_occupancy");
  }
}
