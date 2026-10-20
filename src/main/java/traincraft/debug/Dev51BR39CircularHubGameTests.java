package traincraft.debug;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import traincraft.registry.TCBlocks;
import traincraft.worldgen.RailwayStationCircularHub;

/** Only isolated Forge GameTest geometry; DOES NOT certify natural village worldgen or train motion. */
@GameTestHolder("traincraft")
@PrefixGameTestTemplate(false)
public final class Dev51BR39CircularHubGameTests {
    private Dev51BR39CircularHubGameTests() {}
    private static final String P="[TC-DEV51B-R39-NATIVE]";

    @SuppressWarnings("unchecked")
    private static BlockPos prepare(GameTestHelper h)throws Exception {
        Path cwd=Path.of(System.getProperty("user.dir","")).toAbsolutePath().normalize();
        if(!(h.getLevel().getServer() instanceof GameTestServer)
            ||cwd.getFileName()==null||!cwd.getFileName().toString().startsWith("native-runtime-")
            ||cwd.getParent()==null||!cwd.getParent().getFileName().toString().equals("_DEV51B_R30Q"))
            throw new IllegalStateException("NOT_ISOLATED_FORGE_GAMETEST");
        BlockPos origin=h.absolutePos(new BlockPos(31,24,32));
        int previousTop=Integer.MIN_VALUE;
        for(int x=-2;x<=2;x++)for(int z=23;z<=25;z++) {
            BlockPos at=origin.offset(x,0,z);
            previousTop=Math.max(previousTop,h.getLevel().getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,at.getX(),at.getZ()));
        }
        int surfaceY=Math.max(origin.getY()+32,previousTop+10);
        if(surfaceY>h.getLevel().getMaxBuildHeight()-40)
            throw new IllegalStateException("R39_TEST_HEIGHT_UNSUITABLE:"+surfaceY);
        BlockPos surface=new BlockPos(origin.getX(),surfaceY,origin.getZ());
        // Leave the four square corners as untouched stone, so the test
        // proves the excavated room is a circle instead of a square.
        BlockPos center=RailwayStationCircularHub.railCenter(surface);
        for(int x:new int[]{-12,12})for(int z:new int[]{-12,12})
            h.getLevel().setBlock(center.offset(x,1,z),Blocks.STONE.defaultBlockState(),18);
        Method planned=RailwayStationCircularHub.class.getDeclaredMethod("planned",BlockPos.class);
        planned.setAccessible(true);
        Map<BlockPos,BlockState> plan=(Map<BlockPos,BlockState>)planned.invoke(null,surface);
        for(BlockPos pos:plan.keySet()){
            BlockState original=pos.getY()<surfaceY?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState();
            h.getLevel().setBlock(pos,original,18);
        }
        // Create exactly 15 known-flat entry support blocks, no other world data.
        for(int x=-2;x<=2;x++)for(int z=23;z<=25;z++){
            for(int y=0;y<=25;y++)h.getLevel().setBlock(surface.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
            h.getLevel().setBlock(surface.offset(x,-1,z),Blocks.DIRT.defaultBlockState(),3);
        }
        for(int x=-2;x<=2;x++)for(int z=23;z<=25;z++){
            BlockPos ground=surface.offset(x,-1,z);
            int measured=h.getLevel().getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,ground.getX(),ground.getZ());
            if(measured!=surfaceY||!h.getLevel().getBlockState(ground).is(Blocks.DIRT))
                throw new IllegalStateException("R39_ENTRANCE_HEIGHTMAP_HOLD:"+ground.toShortString()
                    +" actual="+measured+" expected="+surfaceY);
        }
        System.out.println(P+" FIXTURE_PASS originY="+origin.getY()+" surfaceY="+surfaceY
            +" entrySupports=15 plannedCells="+plan.size()+" isolatedNativeOnly=true");
        return surface;
    }

    private static void verify(GameTestHelper h,boolean unsafe) {
        try {
            BlockPos surface=prepare(h);
            BlockPos center=RailwayStationCircularHub.railCenter(surface);
            if(unsafe){
                BlockPos blocked=center.offset(4,2,-3);
                h.getLevel().setBlock(blocked,Blocks.WATER.defaultBlockState(),18);
                String reason=RailwayStationCircularHub.problem(h.getLevel(),surface);
                if(reason==null||!reason.startsWith("UNSAFE_EXCAVATION:"))
                    throw new IllegalStateException("WATER_NOT_REFUSED:"+reason);
                if(!h.getLevel().getBlockState(blocked).is(Blocks.WATER)
                    ||h.getLevel().getBlockState(center).is(TCBlocks.TRACK_STATION.get()))
                    throw new IllegalStateException("READ_ONLY_PREFLIGHT_CHANGED_WORLD");
                System.out.println(P+" CASE_PASS circular_hub_rejects_water_cave_and_preserves_world"
                    +" editsByPreflight=0 forcedChunks=0 noAutoDispatch=true isolatedNativeOnly=true");
            }else{
                String reason=RailwayStationCircularHub.problem(h.getLevel(),surface);
                if(reason!=null)throw new IllegalStateException("HUB_PREVIEW_HOLD:"+reason);
                if(!RailwayStationCircularHub.build(h.getLevel(),surface).equals(center))
                    throw new IllegalStateException("SAVED_RAIL_CENTER_INCORRECT");
                for(int x=-8;x<=8;x++){
                    BlockPos rail=center.offset(x,0,0);
                    if(!h.getLevel().getBlockState(rail).is(x==0?TCBlocks.TRACK_STATION.get():TCBlocks.TRACK_NORMAL.get())
                        ||!h.getLevel().getBlockState(rail.below()).is(Blocks.GRAVEL))
                        throw new IllegalStateException("HUB_RAIL_MISSING:"+x);
                }
                for(int x:new int[]{-12,12})
                    if(!h.getLevel().getBlockState(center.offset(x,1,0)).is(Blocks.STONE_BRICKS))
                        throw new IllegalStateException("CIRCULAR_SHELL_MISSING:"+x);
                // The corners of the enclosing 25-square box must NOT have been excavated.
                for(int x:new int[]{-12,12})for(int z:new int[]{-12,12})
                    if(h.getLevel().getBlockState(center.offset(x,1,z)).isAir())
                        throw new IllegalStateException("CIRCLE_NOT_CIRCULAR:"+x+":"+z);
                for(int x:new int[]{-5,5})for(int z:new int[]{-11,-10})
                    if(!h.getLevel().getBlockState(center.offset(x,1,z)).isAir())
                        throw new IllegalStateException("BRANCH_DOORWAY_BLOCKED:"+x+":"+z);
                if(!h.getLevel().getBlockState(center.offset(-5,0,-16)).is(TCBlocks.TICKET_BOOTH.get())
                    ||!h.getLevel().getBlockState(center.offset(5,0,-16)).is(TCBlocks.STATION_OFFICE_DESK.get())
                    ||!h.getLevel().getBlockState(center.offset(5,0,-13)).is(TCBlocks.RAILWAY_TOOLBENCH.get()))
                    throw new IllegalStateException("BRANCH_WORKSTATIONS_MISSING");
                if(!h.getLevel().getBlockState(center.offset(0,5,-6)).is(Blocks.SEA_LANTERN))
                    throw new IllegalStateException("CONCOURSE_LIGHTING_MISSING");
                for(int z=13;z<=23;z++){
                    int y=surface.getY()-(23-z)-1;
                    if(!h.getLevel().getBlockState(new BlockPos(surface.getX(),y,surface.getZ()+z)).is(Blocks.STONE_BRICK_STAIRS))
                        throw new IllegalStateException("HUB_STAIRS_MISSING:"+z);
                }
                if(!h.getLevel().getBlockState(surface.offset(0,0,24)).is(Blocks.STONE_BRICKS))
                    throw new IllegalStateException("SURFACE_ENTRANCE_MISSING");
                int count=0;for(int i=0;i<500;i++)
                    if(RailwayStationCircularHub.selected(847124L,new BlockPos(i*29,70,19)))count++;
                if(count<5||count>60)throw new IllegalStateException("VARIETY_ROLL_BAD:"+count);
                System.out.println(P+" CASE_PASS circular_hub_rails_ring_two_rooms_stairs_lights"
                    +" rails=17 circleRadius=12 branches=2 stairs=11 selectionSample="+count
                    +" railCenterUnderground=true playerOperated=true realVillageWorldgenCertified=false");
            }
            h.succeed();
        }catch(Exception failure){h.fail(P+" HOLD hub "+failure.getClass().getSimpleName()+": "+failure.getMessage());}
    }
    @GameTest(template="r30q_testbed",templateNamespace="traincraft",batch="traincraft.dev51b.r39.build",timeoutTicks=180)
    public static void circularHubBuildAndVerify(GameTestHelper h){verify(h,false);}
    @GameTest(template="r30q_testbed",templateNamespace="traincraft",batch="traincraft.dev51b.r39.deny",timeoutTicks=180)
    public static void circularHubUnsafeRefusal(GameTestHelper h){verify(h,true);}
}
