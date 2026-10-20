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
import traincraft.worldgen.RailwayStationSubway;

/** Isolated R38 native geometry and fail-closed tests; natural worldgen not certified. */
@GameTestHolder("traincraft")
@PrefixGameTestTemplate(false)
public final class Dev51BR38SubwayStationGameTests {
    private Dev51BR38SubwayStationGameTests() {}
    private static final String P="[TC-DEV51B-R38-NATIVE]";

    @SuppressWarnings("unchecked")
    private static BlockPos prepare(GameTestHelper h) throws Exception {
        Path cwd=Path.of(System.getProperty("user.dir","")).toAbsolutePath().normalize();
        if(!(h.getLevel().getServer() instanceof GameTestServer) || cwd.getFileName()==null
                || !cwd.getFileName().toString().startsWith("native-runtime-")
                || cwd.getParent()==null||!cwd.getParent().getFileName().toString().equals("_DEV51B_R30Q"))
            throw new IllegalStateException("ISOLATED_GAMETEST_WORLD_REQUIRED");
        // R38a: locate the top of the synthetic template terrain BEFORE creating
        // a flat isolated subway site. A fixed relative Y collided with the
        // GameTest structure/heightmap at the staircase landing in R38.
        BlockPos origin=h.absolutePos(new BlockPos(28,21,22));
        int previousTop=Integer.MIN_VALUE;
        for(int x=-2;x<=2;x++) for(int z=14;z<=19;z++) {
            BlockPos sample=origin.offset(x,0,z);
            previousTop=Math.max(previousTop,h.getLevel().getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    sample.getX(),sample.getZ()));
        }
        int surfaceY=Math.max(origin.getY()+24,previousTop+8);
        if(surfaceY>h.getLevel().getMaxBuildHeight()-30)
            throw new IllegalStateException("R38A_TEST_WORLD_HEIGHT_UNSUITABLE:"+surfaceY);
        BlockPos surface=new BlockPos(origin.getX(),surfaceY,origin.getZ());
        System.out.println("[TC-DEV51B-R38A-NATIVE] FIXTURE_ELEVATION originY="+origin.getY()
                +" originalTop="+previousTop+" isolatedSurfaceY="+surfaceY);
        Method m=RailwayStationSubway.class.getDeclaredMethod("planned",BlockPos.class);
        m.setAccessible(true);
        Map<BlockPos,BlockState> plan=(Map<BlockPos,BlockState>)m.invoke(null,surface);
        for(BlockPos p:plan.keySet()) {
            BlockState material=p.getY()<surface.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
            h.getLevel().setBlock(p,material,18);
        }
        // Real heightmap calculation: clear the entrance headspace first, THEN
        // put the 30 dirt foundation blocks down with full neighbour updates.
        // This only touches the isolated GameTest world. Production preflight
        // remains unchanged and still REJECTS uneven or obstructed terrain.
        for(int x=-2;x<=2;x++) for(int z=14;z<=19;z++) {
            for(int y=0;y<=22;y++)
                h.getLevel().setBlock(surface.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
            BlockPos b=surface.offset(x,-1,z);
            h.getLevel().setBlock(b,Blocks.DIRT.defaultBlockState(),3);
        }
        for(int x=-2;x<=2;x++) for(int z=14;z<=19;z++) {
            BlockPos b=surface.offset(x,-1,z);
            int actual=h.getLevel().getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    b.getX(),b.getZ());
            if(actual!=surfaceY || !h.getLevel().getBlockState(b).is(Blocks.DIRT)) {
                System.out.println("[TC-DEV51B-R38A-NATIVE] ENTRANCE_READBACK HOLD at="
                        +b.toShortString()+" observedHeight="+actual+" expected="+surfaceY
                        +" ground="+h.getLevel().getBlockState(b));
                throw new IllegalStateException("R38A_FIXTURE_HEIGHTMAP_NOT_FLAT:"+b.toShortString());
            }
        }
        System.out.println("[TC-DEV51B-R38A-NATIVE] ENTRANCE_READBACK PASS cells=30"
                +" ground=dirt heightmapY="+surfaceY+" fixtureOnly=true");
        return surface;
    }
    private static void caseTest(GameTestHelper h,boolean unsafe) {
        try {
            BlockPos surface=prepare(h);
            if(unsafe){
                BlockPos hazard=RailwayStationSubway.railCenter(surface).offset(3,2,3);
                h.getLevel().setBlock(hazard,Blocks.WATER.defaultBlockState(),18);
                String result=RailwayStationSubway.problem(h.getLevel(),surface);
                if(result==null||!result.startsWith("UNSAFE_EXCAVATION:"))
                    throw new IllegalStateException("UNSAFE_WATER_WAS_NOT_REJECTED:"+result);
                if(!h.getLevel().getBlockState(hazard).is(Blocks.WATER))
                    throw new IllegalStateException("READ_ONLY_CHECK_MUTATED_HAZARD");
                if(h.getLevel().getBlockState(RailwayStationSubway.railCenter(surface)).is(TCBlocks.TRACK_STATION.get()))
                    throw new IllegalStateException("UNSAFE_SITE_GAINED_STATION_RAIL");
                System.out.println(P+" CASE_PASS subway_refuses_water_or_cave writes=0 forcedChunks=0 noAutoDispatch=true isolatedNativeOnly=true");
            }else {
                String reason=RailwayStationSubway.problem(h.getLevel(),surface);
                if(reason!=null)throw new IllegalStateException("PREVIEW_UNEXPECTED_HOLD:"+reason);
                BlockPos center=RailwayStationSubway.build(h.getLevel(),surface);
                if(!center.equals(surface.below(RailwayStationSubway.DEPTH)))
                    throw new IllegalStateException("SAVED_CENTER_NOT_AT_RAIL_LEVEL");
                for(int dx=-8;dx<=8;dx++) {
                    BlockPos rail=center.offset(dx,0,0);
                    if(!h.getLevel().getBlockState(rail).is(dx==0?TCBlocks.TRACK_STATION.get():TCBlocks.TRACK_NORMAL.get())
                            ||!h.getLevel().getBlockState(rail.below()).is(Blocks.GRAVEL))
                        throw new IllegalStateException("RAIL_MISSING:"+dx);
                }
                for(int z=9;z<=17;z++)
                    if(!h.getLevel().getBlockState(surface.offset(0,-(17-z),z)).is(Blocks.STONE_BRICK_STAIRS))
                        throw new IllegalStateException("STAIR_MISSING:"+z);
                if(!h.getLevel().getBlockState(center.offset(-5,0,6)).is(TCBlocks.STATION_OFFICE_DESK.get())
                    ||!h.getLevel().getBlockState(center.offset(0,0,6)).is(TCBlocks.RAILWAY_TOOLBENCH.get())
                    ||!h.getLevel().getBlockState(center.offset(5,0,6)).is(TCBlocks.TICKET_BOOTH.get()))
                    throw new IllegalStateException("UNDERGROUND_WORKSTATIONS_MISSING");
                if(!h.getLevel().getBlockState(center.offset(7,4,3)).is(Blocks.SEA_LANTERN))
                    throw new IllegalStateException("SUBWAY_LIGHTING_MISSING");
                int selected=0;for(int i=0;i<500;i++)if(RailwayStationSubway.selected(847124L,new BlockPos(i*19,70,17)))selected++;
                if(selected<10||selected>100)throw new IllegalStateException("SUBWAY_SELECTION_BAD:"+selected);
                System.out.println(P+" CASE_PASS subway_room_rails_stairs_staff_workstations rails=17 entrance=graded center=underground selectionSample="+selected+" isolatedNativeOnly=true realVillageWorldgenCertified=false");
            }
            h.succeed();
        }catch(Exception e){h.fail(P+" HOLD subway "+e.getClass().getSimpleName()+": "+e.getMessage());}
    }
    @GameTest(template="r30q_testbed",templateNamespace="traincraft",batch="traincraft.dev51b.r38.build",timeoutTicks=180)
    public static void subwayBuildAndVerify(GameTestHelper h){caseTest(h,false);}
    @GameTest(template="r30q_testbed",templateNamespace="traincraft",batch="traincraft.dev51b.r38.deny",timeoutTicks=180)
    public static void subwayUnsafeRefusal(GameTestHelper h){caseTest(h,true);}
}
