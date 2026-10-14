package traincraft.dev51;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * DEV51B R12: frozen reference of the 64 user-approved path edits layered on R10.
 * This module is READ-ONLY. Station generator integration is NOT enabled until
 * the local, frozen DEV50 worldgen source can be reviewed and rotated/graded.
 */
@Mod.EventBusSubscriber(modid="traincraft", bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev51BApprovedPathBlueprintR12 {
    private static final Logger LOG=LoggerFactory.getLogger("TC-DEV51B-R12");
    public static final BlockPos REFERENCE_ANCHOR=new BlockPos(89,67,3164);
    public record Edit(int dx,int dy,int dz,String before,String after) {}
    private record Rail(int dz,String state) {}
    // Relative to the reference rail at (89,67,3164), rail positive-Z direction SOUTH.
    private static final List<Edit> FINAL_MANUAL_EDITS=List.of(
        new Edit(4,-1,8,"minecraft:smooth_sandstone","minecraft:sandstone"),
        new Edit(4,-1,9,"minecraft:smooth_sandstone","minecraft:sandstone"),
        new Edit(4,-1,10,"minecraft:sandstone_slab[type=bottom,waterlogged=false]","minecraft:sand"),
        new Edit(5,-1,8,"minecraft:sandstone_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]","minecraft:sandstone"),
        new Edit(5,-1,9,"minecraft:sandstone_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]","minecraft:sandstone"),
        new Edit(5,-1,10,"minecraft:sandstone_slab[type=bottom,waterlogged=false]","minecraft:sand"),
        new Edit(5,-1,11,"minecraft:air","minecraft:sand"),
        new Edit(6,-3,-26,"minecraft:cobblestone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]","minecraft:dirt"),
        new Edit(6,-2,-26,"minecraft:air","minecraft:cobblestone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"),
        new Edit(6,-2,-20,"minecraft:smooth_sandstone","minecraft:sandstone"),
        new Edit(6,-2,-19,"minecraft:sandstone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]","minecraft:sand"),
        new Edit(6,-1,-19,"minecraft:air","minecraft:sandstone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"),
        new Edit(6,-1,6,"minecraft:air","minecraft:sand"),
        new Edit(6,-1,7,"minecraft:air","minecraft:sand"),
        new Edit(6,-1,8,"minecraft:sand","minecraft:sandstone"),
        new Edit(6,0,8,"minecraft:smooth_sandstone","minecraft:air"),
        new Edit(6,-1,9,"minecraft:sand","minecraft:sandstone"),
        new Edit(6,0,9,"minecraft:smooth_sandstone","minecraft:air"),
        new Edit(7,-2,-20,"minecraft:sandstone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]","minecraft:sandstone"),
        new Edit(7,-1,-19,"minecraft:smooth_sandstone","minecraft:sandstone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"),
        new Edit(7,-1,8,"minecraft:sand","minecraft:sandstone"),
        new Edit(7,0,8,"minecraft:smooth_sandstone","minecraft:air"),
        new Edit(7,-1,9,"minecraft:sand","minecraft:sandstone"),
        new Edit(7,0,9,"minecraft:smooth_sandstone","minecraft:air"),
        new Edit(8,-1,8,"minecraft:sand","minecraft:sandstone"),
        new Edit(8,0,8,"minecraft:smooth_sandstone","minecraft:air"),
        new Edit(8,-1,9,"minecraft:sand","minecraft:sandstone"),
        new Edit(8,0,9,"minecraft:smooth_sandstone","minecraft:air"),
        new Edit(9,-1,8,"minecraft:sand","minecraft:sandstone"),
        new Edit(9,0,8,"minecraft:smooth_sandstone","minecraft:air"),
        new Edit(9,-1,9,"minecraft:sand","minecraft:sandstone"),
        new Edit(9,0,9,"minecraft:smooth_sandstone","minecraft:air"),
        new Edit(10,-1,8,"minecraft:sand","minecraft:sandstone"),
        new Edit(10,0,8,"minecraft:smooth_sandstone","minecraft:air"),
        new Edit(10,-1,9,"minecraft:sand","minecraft:sandstone"),
        new Edit(10,0,9,"minecraft:smooth_sandstone","minecraft:air"),
        new Edit(11,-1,8,"minecraft:sand","minecraft:sandstone"),
        new Edit(11,0,8,"minecraft:smooth_sandstone","minecraft:air"),
        new Edit(11,-1,9,"minecraft:sand","minecraft:sandstone"),
        new Edit(11,0,9,"minecraft:smooth_sandstone","minecraft:air"),
        new Edit(12,-1,3,"minecraft:sandstone_slab[type=bottom,waterlogged=false]","minecraft:sand"),
        new Edit(12,-1,6,"minecraft:air","minecraft:sand"),
        new Edit(12,-1,7,"minecraft:sandstone_slab[type=bottom,waterlogged=false]","minecraft:sand"),
        new Edit(12,-1,8,"minecraft:sandstone_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]","minecraft:sandstone"),
        new Edit(12,-1,9,"minecraft:sandstone_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]","minecraft:sandstone"),
        new Edit(12,-1,10,"minecraft:sandstone_slab[type=bottom,waterlogged=false]","minecraft:sand"),
        new Edit(12,-1,11,"minecraft:air","minecraft:sand"),
        new Edit(13,-1,-2,"minecraft:sandstone_slab[type=bottom,waterlogged=false]","minecraft:sand"),
        new Edit(13,-1,-1,"minecraft:sandstone_slab[type=bottom,waterlogged=false]","minecraft:sand"),
        new Edit(13,-1,0,"minecraft:sandstone_slab[type=bottom,waterlogged=false]","minecraft:sand"),
        new Edit(13,-1,1,"minecraft:sandstone_slab[type=bottom,waterlogged=false]","minecraft:sand"),
        new Edit(13,-1,2,"minecraft:sandstone_slab[type=bottom,waterlogged=false]","minecraft:sand"),
        new Edit(13,-1,3,"minecraft:sandstone_slab[type=bottom,waterlogged=false]","minecraft:sand"),
        new Edit(13,-1,7,"minecraft:sandstone_slab[type=bottom,waterlogged=false]","minecraft:sand"),
        new Edit(13,-1,9,"minecraft:smooth_sandstone","minecraft:sandstone"),
        new Edit(13,-1,10,"minecraft:sandstone_slab[type=bottom,waterlogged=false]","minecraft:sand"),
        new Edit(14,-1,-1,"minecraft:sandstone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]","minecraft:sandstone"),
        new Edit(14,-1,0,"minecraft:sand","minecraft:sandstone"),
        new Edit(14,0,0,"minecraft:smooth_sandstone","minecraft:air"),
        new Edit(14,-1,1,"minecraft:sand","minecraft:sandstone"),
        new Edit(14,0,1,"minecraft:smooth_sandstone","minecraft:air"),
        new Edit(14,-1,2,"minecraft:sandstone_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]","minecraft:sandstone"),
        new Edit(16,-1,-1,"minecraft:air","minecraft:sand"),
        new Edit(16,-1,2,"minecraft:air","minecraft:sand")
    );
    private static final List<Rail> TRACK_WITNESSES=List.of(
        new Rail(-24,"traincraft:track_normal"),
        new Rail(-23,"traincraft:track_normal"),
        new Rail(-22,"traincraft:track_normal"),
        new Rail(-21,"traincraft:track_normal"),
        new Rail(-20,"traincraft:track_normal"),
        new Rail(-19,"traincraft:track_normal"),
        new Rail(-18,"traincraft:track_normal"),
        new Rail(-17,"traincraft:track_normal"),
        new Rail(-16,"traincraft:track_normal"),
        new Rail(-15,"traincraft:track_normal"),
        new Rail(-14,"traincraft:track_normal"),
        new Rail(-13,"traincraft:track_normal"),
        new Rail(-12,"traincraft:track_normal"),
        new Rail(-11,"traincraft:track_normal"),
        new Rail(-10,"traincraft:track_normal"),
        new Rail(-9,"traincraft:track_normal"),
        new Rail(-8,"traincraft:track_normal"),
        new Rail(-7,"traincraft:track_normal"),
        new Rail(-6,"traincraft:track_normal"),
        new Rail(-5,"traincraft:track_normal"),
        new Rail(-4,"traincraft:track_normal"),
        new Rail(-3,"traincraft:track_normal"),
        new Rail(-2,"traincraft:track_normal"),
        new Rail(-1,"traincraft:track_normal"),
        new Rail(0,"traincraft:track_normal"),
        new Rail(1,"traincraft:track_normal"),
        new Rail(2,"traincraft:track_normal"),
        new Rail(3,"traincraft:track_normal"),
        new Rail(4,"traincraft:track_normal"),
        new Rail(5,"traincraft:track_normal"),
        new Rail(6,"traincraft:track_normal"),
        new Rail(7,"traincraft:track_normal"),
        new Rail(8,"traincraft:track_normal"),
        new Rail(9,"traincraft:track_normal"),
        new Rail(10,"traincraft:track_normal"),
        new Rail(11,"traincraft:track_normal"),
        new Rail(12,"traincraft:track_normal"),
        new Rail(13,"traincraft:track_normal"),
        new Rail(14,"traincraft:track_normal"),
        new Rail(15,"traincraft:track_normal"),
        new Rail(16,"traincraft:track_normal"),
        new Rail(17,"traincraft:track_normal"),
        new Rail(18,"traincraft:track_normal"),
        new Rail(19,"traincraft:track_normal"),
        new Rail(20,"traincraft:track_normal"),
        new Rail(21,"traincraft:track_normal"),
        new Rail(22,"traincraft:track_normal"),
        new Rail(23,"traincraft:track_normal"),
        new Rail(24,"traincraft:track_normal")
    );
    private Dev51BApprovedPathBlueprintR12() {}

    /** This is a *data blueprint*, not a license to replay these blocks on another biome.
     * Callers must independently validate target structure, terrain, orientation and path.
     */
    public static List<Edit> approvedManualEdits() {return FINAL_MANUAL_EDITS;}

    /** Rotate relative X,Z offsets based on the rail direction. Blocks with facing
     * properties additionally need BlockState rotation when a future generator uses them.
     */
    public static BlockPos projectedPosition(BlockPos anchor, Direction railPositive, Edit edit) {
        int x=edit.dx(), z=edit.dz();
        return switch(railPositive) {
            case SOUTH -> anchor.offset(x,edit.dy(),z);
            case NORTH -> anchor.offset(-x,edit.dy(),-z);
            case EAST -> anchor.offset(z,edit.dy(),-x);
            case WEST -> anchor.offset(-z,edit.dy(),x);
            default -> throw new IllegalArgumentException("Horizontal rail direction required");
        };
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent ev) {
        LiteralArgumentBuilder<CommandSourceStack> root=Commands.literal("tcdev51b12")
            .requires(s->s.hasPermission(2))
            .then(Commands.literal("path")
                .then(Commands.literal("verify").executes(c->verify(c.getSource())))
                .then(Commands.literal("export").executes(c->export(c.getSource()))));
        ev.getDispatcher().register(root);
    }
    private static void chat(CommandSourceStack s,String msg) {
        s.sendSuccess(()->Component.literal(msg),false);
    }
    private static ServerLevel inAcceptedWorld(CommandSourceStack s) {
        ServerLevel l=s.getLevel();
        if(!l.dimension().equals(Level.OVERWORLD)
           ||!"DEV50E6 Terrain Profile Test".equals(l.getServer().getWorldData().getLevelName())
           ||!(s.getEntity() instanceof ServerPlayer player)
           ||player.blockPosition().distSqr(REFERENCE_ANCHOR)>64L*64L) {
            chat(s,"[R12 STOP] Only the accepted DEV50E6 test world, near the station. No blocks changed.");
            return null;
        }
        return l;
    }
    private static String describe(BlockState state) {
        StringBuilder b=new StringBuilder(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if(!state.getValues().isEmpty()) {
            List<Map.Entry<Property<?>,Comparable<?>>> entries=new ArrayList<>(state.getValues().entrySet());
            entries.sort(Comparator.comparing(e->e.getKey().getName()));
            b.append('[');
            for(int i=0;i<entries.size();i++) {
                if(i>0)b.append(',');
                Map.Entry<Property<?>,Comparable<?>> e=entries.get(i);
                b.append(e.getKey().getName()).append('=').append(e.getValue());
            }
            b.append(']');
        }
        return b.toString();
    }
    private static boolean chunksLoaded(ServerLevel l,CommandSourceStack s) {
        for(Edit e:FINAL_MANUAL_EDITS){
            BlockPos p=REFERENCE_ANCHOR.offset(e.dx(),e.dy(),e.dz());
            if(!l.hasChunk(p.getX()>>4,p.getZ()>>4)){
                chat(s,"[R12 STOP] Unloaded chunk near path "+p+". No forced chunks.");return false;
            }
        }
        for(Rail r:TRACK_WITNESSES){
            BlockPos p=REFERENCE_ANCHOR.offset(0,0,r.dz());
            if(!l.hasChunk(p.getX()>>4,p.getZ()>>4)){
                chat(s,"[R12 STOP] Unloaded rail chunk. No forced chunks.");return false;
            }
        }
        return true;
    }
    private static int verify(CommandSourceStack s) {
        ServerLevel l=inAcceptedWorld(s);if(l==null||!chunksLoaded(l,s))return 0;
        int matches=0,changes=0,rails=0;
        StringBuilder failures=new StringBuilder();
        for(Edit e:FINAL_MANUAL_EDITS){
            BlockPos p=REFERENCE_ANCHOR.offset(e.dx(),e.dy(),e.dz());
            String actual=describe(l.getBlockState(p));
            if(actual.equals(e.after()))matches++;
            else {changes++;if(changes<=10)failures.append("\n  ").append(p).append(" expected ").append(e.after()).append(" actual ").append(actual);}
        }
        // Witnesses record block REGISTRY IDs, not blockstate/property strings.
        // The R12a full-state comparison reported 0/49 rail witnesses with
        // additional properties, even while the 64 approved path blocks matched.
        String firstRailMismatch=null;
        for(Rail r:TRACK_WITNESSES) {
            BlockPos p=REFERENCE_ANCHOR.offset(0,0,r.dz());
            BlockState actual=l.getBlockState(p);
            String actualId=BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString();
            if(actualId.equals(r.state()))rails++;
            else if(firstRailMismatch==null)firstRailMismatch=p+" expected="+r.state()
                +" actualId="+actualId+" actualState="+describe(actual);
        }
        boolean finalAir=l.getBlockState(new BlockPos(98,67,3154)).isAir();
        boolean passed=matches==64 && rails==49 && finalAir;
        LOG.info("[TC-DEV51B-R12] VERIFIED pathMatches={}/64 rails={}/49 finalAir={} drift={} acceptedFlatStairs=6 worldEdits=0 {}",
            matches,rails,finalAir,changes,passed?"PASS":"HOLD");
        if(changes>0)LOG.warn("[TC-DEV51B-R12] PATH_DRIFT first={}",failures);
        if(rails!=49)LOG.warn("[TC-DEV51B-R12] RAIL_REGISTRY_MISMATCH first={} count={}/49",firstRailMismatch,rails);
        if(!finalAir)LOG.warn("[TC-DEV51B-R12] FINAL_SAND_NOT_AIR at=(98,67,3154) actual={}",describe(l.getBlockState(new BlockPos(98,67,3154))));
        chat(s,passed?"[R12 PASS] Final 64 manual edits + 49 rails + missing sand AIR match. Accepted flat station pathway frozen; read-only."
            :"[R12 HOLD] Path drift="+changes+" rails="+rails+"/49 finalAir="+finalAir+". Do not assume blueprint matches this world.");
        return passed?1:0;
    }
    private static int export(CommandSourceStack s) {
        ServerLevel l=inAcceptedWorld(s);if(l==null||!chunksLoaded(l,s))return 0;
        Path p=Path.of("logs","DEV51B-R12-RELATIVE-MANUAL-PATH-"+System.currentTimeMillis()+".tsv");
        try {
            Files.createDirectories(p.getParent());
            try(BufferedWriter w=Files.newBufferedWriter(p,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
                w.write("# DEV51B-R12 accepted manual path, reference anchor 89 67 3164, rail SOUTH; READ_ONLY\n");
                w.write("dx\tdy\tdz\tbefore\tafter\n");
                for(Edit e:FINAL_MANUAL_EDITS) {
                    w.write(e.dx()+"\t"+e.dy()+"\t"+e.dz()+"\t"+e.before()+"\t"+e.after());w.newLine();
                }
            }
            LOG.info("[TC-DEV51B-R12] EXPORTED entries=64 path={} sourceWorldOnly=true worldEdits=0",p.toAbsolutePath());
            chat(s,"[R12] Exported the 64 accepted *relative* edits to "+p+". World unchanged.");
            return 1;
        }catch(Exception e){LOG.error("[TC-DEV51B-R12] EXPORT_FAILED",e);chat(s,"[R12 STOP] Export failed: "+e.getMessage());return 0;}
    }
}
