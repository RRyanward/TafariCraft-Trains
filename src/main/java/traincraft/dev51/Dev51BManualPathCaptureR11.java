package traincraft.dev51;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** DEV51B R11: read-only path before/after capture; no setBlock calls. */
@Mod.EventBusSubscriber(modid="traincraft", bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev51BManualPathCaptureR11 {
    private static final Logger LOG=LoggerFactory.getLogger("TC-DEV51B-R11");
    private static final String WORLD="DEV50E6 Terrain Profile Test";
    private static final int MIN_X=91, MAX_X=110, MIN_Z=3135, MAX_Z=3182, MIN_Y=62, MAX_Y=75;
    private static final int COUNT=(MAX_X-MIN_X+1)*(MAX_Z-MIN_Z+1)*(MAX_Y-MIN_Y+1);
    private static final Path ROOT=Path.of("logs","DEV51B-R11-PATH-BASELINE.tsv");
    private Dev51BManualPathCaptureR11() {}

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tcdev51b11")
            .requires(src->src.hasPermission(2))
            .then(Commands.literal("path")
                .then(Commands.literal("baseline").executes(ctx->capture(ctx.getSource().getLevel(), ctx.getSource())))
                .then(Commands.literal("compare").executes(ctx->compare(ctx.getSource().getLevel(), ctx.getSource())))
                .then(Commands.literal("status").executes(ctx->status(ctx.getSource().getLevel(), ctx.getSource())))));
        LOG.info("[TC-DEV51B-R11] REGISTERED read-only /tcdev51b11 path baseline|compare|status");
    }
    private static boolean worldOK(ServerLevel level, net.minecraft.commands.CommandSourceStack src) {
        if (!WORLD.equals(level.getServer().getWorldData().getLevelName())) {
            src.sendFailure(Component.literal("R11 STOP: only "+WORLD)); return false;
        }
        for(int x=MIN_X;x<=MAX_X;x+=MAX_X-MIN_X) for(int z=MIN_Z;z<=MAX_Z;z+=MAX_Z-MIN_Z)
            if (!level.hasChunkAt(new BlockPos(x,67,z))) {src.sendFailure(Component.literal("R11 STOP: survey chunks not loaded"));return false;}
        return true;
    }
    private static String state(BlockState s) {
        StringBuilder b=new StringBuilder(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
        if(!s.getValues().isEmpty()) {
            b.append('[');
            boolean first=true;
            for(var property:s.getProperties()) {
                if(!first)b.append(',');first=false;
                b.append(property.getName()).append('=').append(s.getValue(property).toString());
            }
            b.append(']');
        }
        return b.toString();
    }
    private static String key(int x,int y,int z){return x+","+y+","+z;}
    private static void message(net.minecraft.commands.CommandSourceStack src,String msg){src.sendSuccess(()->Component.literal(msg),false);LOG.info("[TC-DEV51B-R11] {}",msg);}
    private static int status(ServerLevel level,net.minecraft.commands.CommandSourceStack src) {
        if(!worldOK(level,src))return 0;
        message(src, Files.isRegularFile(ROOT)?"BASELINE EXISTS. Make path edits, then /tcdev51b11 path compare":"NO BASELINE. Run /tcdev51b11 path baseline BEFORE editing.");return 1;
    }
    private static int capture(ServerLevel level,net.minecraft.commands.CommandSourceStack src) {
        if(!worldOK(level,src))return 0;
        if(Files.exists(ROOT)){src.sendFailure(Component.literal("R11 STOP: baseline already exists; will NOT overwrite. Run compare."));return 0;}
        try {
            List<String> lines=new ArrayList<>(COUNT+5);
            lines.add("# DEV51B R11 read-only manually edited path before-state; world="+WORLD);
            lines.add("# bounds x="+MIN_X+".."+MAX_X+" z="+MIN_Z+".."+MAX_Z+" y="+MIN_Y+".."+MAX_Y+"; no block writes");
            lines.add("x\ty\tz\tblockstate");
            BlockPos.MutableBlockPos p=new BlockPos.MutableBlockPos();
            for(int x=MIN_X;x<=MAX_X;x++)for(int z=MIN_Z;z<=MAX_Z;z++)for(int y=MIN_Y;y<=MAX_Y;y++){
                p.set(x,y,z);
                if(!level.hasChunkAt(p)){src.sendFailure(Component.literal("R11 STOP: unloaded chunk; snapshot not saved"));return 0;}
                lines.add(x+"\t"+y+"\t"+z+"\t"+state(level.getBlockState(p)));
            }
            Files.createDirectories(ROOT.getParent());
            Files.write(ROOT,lines,StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE_NEW);
            message(src,"BASELINE SAVED: "+COUNT+" blocks to "+ROOT+"; now repair path manually, then run compare.");return 1;
        }catch(Exception e){LOG.error("[TC-DEV51B-R11] BASELINE STOP",e);src.sendFailure(Component.literal("R11 baseline failed: "+e.getMessage()));return 0;}
    }
    private static int compare(ServerLevel level,net.minecraft.commands.CommandSourceStack src){
        if(!worldOK(level,src))return 0;
        if(!Files.isRegularFile(ROOT)){src.sendFailure(Component.literal("R11 STOP: no baseline; run baseline BEFORE changing path"));return 0;}
        try {
            Map<String,String> before=new HashMap<>();
            for(String line:Files.readAllLines(ROOT,StandardCharsets.UTF_8)){
                if(line.startsWith("#")||line.startsWith("x\t"))continue;
                String[] cells=line.split("\t",-1);
                if(cells.length!=4)throw new IllegalStateException("Invalid baseline TSV row");
                before.put(key(Integer.parseInt(cells[0]),Integer.parseInt(cells[1]),Integer.parseInt(cells[2])),cells[3]);
            }
            if(before.size()!=COUNT)throw new IllegalStateException("Incomplete baseline. Expected "+COUNT+", found "+before.size());
            List<String> report=new ArrayList<>();
            report.add("# DEV51B R11 path changes from before snapshot; world="+WORLD+"; no world changes");
            report.add("x\ty\tz\tbefore\tafter\tchange");
            BlockPos.MutableBlockPos p=new BlockPos.MutableBlockPos();
            int changed=0,added=0,removed=0,replaced=0;
            for(int x=MIN_X;x<=MAX_X;x++)for(int z=MIN_Z;z<=MAX_Z;z++)for(int y=MIN_Y;y<=MAX_Y;y++){
                p.set(x,y,z);
                if(!level.hasChunkAt(p)){src.sendFailure(Component.literal("R11 STOP: unloaded chunk; compare not saved"));return 0;}
                String original=before.get(key(x,y,z)),now=state(level.getBlockState(p));
                if(!original.equals(now)){
                    String action=original.equals("minecraft:air")?"ADD":now.equals("minecraft:air")?"REMOVE":"REPLACE";
                    if(action.equals("ADD"))added++;else if(action.equals("REMOVE"))removed++;else replaced++;
                    report.add(x+"\t"+y+"\t"+z+"\t"+original+"\t"+now+"\t"+action);changed++;
                }
            }
            Path dest=Path.of("logs","DEV51B-R11-PATH-DIFF-"+System.currentTimeMillis()+".tsv");
            Files.createDirectories(dest.getParent());Files.write(dest,report,StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE_NEW);
            message(src,"COMPARE DONE: changed="+changed+" added="+added+" removed="+removed+" replaced="+replaced+"; "+dest);
            return 1;
        }catch(Exception e){LOG.error("[TC-DEV51B-R11] COMPARE STOP",e);src.sendFailure(Component.literal("R11 compare failed: "+e.getMessage()));return 0;}
    }
}
