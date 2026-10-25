package traincraft.debug;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.block.track.AbstractLegacyBatchRailBlock;

import java.util.LinkedHashMap;
import java.util.Map;

/** DEV50b2q: read-only three-row track state/ownership audit. */
// DEV58B9R4: legacy diagnostic/test command subscriber retired.

public final class MediumParallelDeepWorldCommand {
    private MediumParallelDeepWorldCommand() { }
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tc_parallel_deep")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> audit(ctx.getSource().getPlayerOrException())));
    }
    private static boolean isRail(BlockState state) {
        ResourceLocation id=ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if(id==null || !Traincraft.MOD_ID.equals(id.getNamespace())) return false;
        return id.getPath().startsWith("track_") || id.getPath().contains("rail_");
    }
    private static int audit(ServerPlayer player) {
        ServerLevel level=player.serverLevel();
        BlockPos center=player.blockPosition();
        Map<Integer,Row> rows=new LinkedHashMap<>();
        int total=0, unloaded=0, errors=0;
        for(int dx=-17;dx<=17;dx++) for(int dz=-10;dz<=10;dz++) for(int dy=-2;dy<=1;dy++) {
            BlockPos pos=center.offset(dx,dy,dz);
            if(!level.hasChunkAt(pos)){unloaded++;continue;}
            BlockState state=level.getBlockState(pos);
            if(!isRail(state)) continue;
            total++;
            Row row=rows.computeIfAbsent(pos.getZ(),unused -> new Row());
            row.count++;
            ResourceLocation id=ForgeRegistries.BLOCKS.getKey(state.getBlock());
            row.blocks.merge(id==null ? "null" : id.toString(),1,Integer::sum);
            if(state.getBlock() instanceof AbstractLegacyBatchRailBlock rail) {
                try {
                    BlockPos root=rail.getAssemblyRoot(pos,state);
                    String owner=(id==null ? "null" : id.toString())+"@"+root.toShortString()+":"+rail.getAssemblyFacing(state);
                    row.owners.merge(owner,1,Integer::sum);
                    if(row.details<4){
                        Traincraft.LOGGER.warn("[TC-DEV50B2Q-SERVER] SAMPLE z={} pos={} owner={} part={} state={}",pos.getZ(),pos,owner,rail.getPart(state),state);
                        row.details++;
                    }
                }catch(Exception ex){errors++;Traincraft.LOGGER.warn("[TC-DEV50B2Q-SERVER] ROOT_ERROR pos={} {}",pos,ex.toString());}
            }
        }
        Traincraft.LOGGER.warn("[TC-DEV50B2Q-SERVER] START center={} rows={} cells={} unloaded={} errors={} READ_ONLY",center,rows.size(),total,unloaded,errors);
        for(Map.Entry<Integer,Row> e:rows.entrySet())
            Traincraft.LOGGER.warn("[TC-DEV50B2Q-SERVER] ROW z={} cells={} blocks={} owners={}",e.getKey(),e.getValue().count,e.getValue().blocks,e.getValue().owners);
        Traincraft.LOGGER.warn("[TC-DEV50B2Q-SERVER] END rows={} cells={} -- move to each of front/middle/rear tracks and wait 8s for client mesh snapshots",rows.size(),total);
        player.sendSystemMessage(Component.literal("DEV50b2q deep scan: " + total + " rail cells in " + rows.size() + " rows; server rows logged. Stand near each front/middle/rear track for 8 seconds to capture CLIENT render data. Read-only."));
        return Command.SINGLE_SUCCESS;
    }
    private static final class Row {
        int count,details;
        final Map<String,Integer> blocks=new LinkedHashMap<>();
        final Map<String,Integer> owners=new LinkedHashMap<>();
    }
}
