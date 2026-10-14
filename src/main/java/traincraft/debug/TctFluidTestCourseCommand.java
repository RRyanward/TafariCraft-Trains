package traincraft.debug;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Mod;
import traincraft.block.integration.FluidInterfaceBlockEntity;
import traincraft.entity.train.freight.TctTanker;
import traincraft.registry.TCBlocks;
import traincraft.registry.TCEntities;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** DEV47c opt-in, no dependency on Mekanism; tests Forge-capable pipes. */
@Mod.EventBusSubscriber(modid="traincraft", bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class TctFluidTestCourseCommand {
    private static final int LEN = 29;
    private static final int[] LANES = {0,12,24};
    private static final int LOAD_Z = 5;
    private static final int UNLOAD_Z = 23;
    private static final Map<UUID, Course> COURSES = new HashMap<>();
    private TctFluidTestCourseCommand() {}

    private record Course(ServerLevel level, BlockPos origin, UUID[] tankers, boolean hidden) {}

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tc_fluid_course")
                .requires(s -> s.hasPermission(2))
                .executes(c -> build(c.getSource(), false))
                .then(Commands.literal("hidden").executes(c -> build(c.getSource(), true)))
                .then(Commands.literal("unload").executes(c -> advance(c.getSource())))
                .then(Commands.literal("status").executes(c -> status(c.getSource())))
                .then(Commands.literal("reject").executes(c -> configure(c.getSource(), true)))
                .then(Commands.literal("valid").executes(c -> configure(c.getSource(), false)))
                .then(Commands.literal("universal").then(Commands.argument("fluid", StringArgumentType.greedyString())
                        .executes(c -> universal(c.getSource(), StringArgumentType.getString(c, "fluid"))))));
    }

    private static void say(CommandSourceStack s, String message) {
        s.sendSuccess(() -> Component.literal("[DEV47c Fluid Course] " + message), false);
    }

    private static int build(CommandSourceStack source, boolean hidden) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        if (COURSES.containsKey(player.getUUID())) {
            say(source, "One active test course is already tracked. Use /tc_fluid_course status or move to a fresh world after restart.");
            return 0;
        }
        int railY = player.getBlockY() + 9;
        if (railY + 6 >= level.getMaxBuildHeight() || railY - 3 < level.getMinBuildHeight()) {
            say(source, "Not enough build height. Run at a lower, open location.");
            return 0;
        }
        BlockPos origin = new BlockPos(player.getBlockX()+8, railY, player.getBlockZ()+8);
        // Full placement preflight: no existing construction is touched.
        for (int x = -3; x <= 27; x++) for (int z=-2; z<=LEN+1; z++) for (int y=-3;y<=5;y++) {
            BlockPos p=origin.offset(x,y,z);
            if (!level.getBlockState(p).isAir()) {
                say(source,"SAFETY STOP: target space is occupied at " + p + ". Move to a more open area.");
                return 0;
            }
        }
        UUID[] ids = new UUID[3];
        @SuppressWarnings("unchecked")
        EntityType<TctTanker>[] types = new EntityType[]{TCEntities.TCT_TANKER_WATER.get(),
                TCEntities.TCT_TANKER_LAVA.get(), TCEntities.TCT_TANKER_UNIVERSAL.get()};
        for (int lane=0;lane<3;lane++) {
            int x = LANES[lane];
            // Normal: interface at rail Y-1. Hidden: grass block at Y-1,
            // interface at Y-2; east-side recess permits Mekanism pipe connection.
            int dockDepth = hidden ? 2 : 1;
            for (int z=0;z<LEN;z++) for (int dx=-2;dx<=2;dx++) {
                level.setBlock(origin.offset(x+dx,-dockDepth-1,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
                if (hidden) {
                    if (dx != 0 || (z != LOAD_Z && z != UNLOAD_Z)) {
                        if (dx != 1 || (z != LOAD_Z && z != UNLOAD_Z))
                            level.setBlock(origin.offset(x+dx,-2,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
                    }
                    if (dx != 1 || (z != LOAD_Z && z != UNLOAD_Z))
                        level.setBlock(origin.offset(x+dx,-1,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);
                } else {
                    if (dx != 1 || (z != LOAD_Z && z != UNLOAD_Z))
                        level.setBlock(origin.offset(x+dx,-1,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
                }
            }
            for (int z=0;z<LEN;z++) {
                BlockPos p=origin.offset(x,0,z);
                if (z==LOAD_Z || z==UNLOAD_Z) {
                    level.setBlock(p.below(hidden ? 2 : 1), TCBlocks.FLUID_INTERFACE.get().defaultBlockState(),3);
                    level.setBlock(p, (z==LOAD_Z ? TCBlocks.TRACK_FLUID_LOAD.get() :
                            TCBlocks.TRACK_FLUID_UNLOAD.get()).defaultBlockState()
                            .setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH),3);
                } else {
                    level.setBlock(p, Blocks.RAIL.defaultBlockState()
                            .setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH),3);
                }
            }
            // Water, Lava, and Universal-water examples. An existing Mekanism pipe can
            // extract from or insert into every side of either interface.
            FluidInterfaceBlockEntity sourceDock = (FluidInterfaceBlockEntity) level.getBlockEntity(
                    origin.offset(x,hidden ? -2 : -1,LOAD_Z));
            FluidStack initial = new FluidStack(lane==1 ? Fluids.LAVA : Fluids.WATER, 24000);
            if (sourceDock == null || sourceDock.getFluidHandler().fill(initial,
                    IFluidHandler.FluidAction.EXECUTE) != 24000) {
                say(source,"ERROR: could not prime source interface at lane " + lane);
                return 0;
            }
            TctTanker tanker = types[lane].create(level);
            if (tanker == null) { say(source,"ERROR: tanker entity creation failed"); return 0; }
            BlockPos station=origin.offset(x,0,LOAD_Z);
            tanker.moveTo(station.getX()+0.5,station.getY()+1.0,station.getZ()+0.5,0f,0f);
            tanker.setDeltaMovement(Vec3.ZERO);
            if (!level.addFreshEntity(tanker)) { say(source,"ERROR: tanker spawn rejected"); return 0; }
            ids[lane]=tanker.getUUID();
        }
        COURSES.put(player.getUUID(),new Course(level,origin,ids,hidden));
        player.teleportTo(level,origin.getX()+2.5,railY+0.5,origin.getZ()+3.0,0.0F,10.0F);
        say(source,"Built three 29-block tracks with interfaces at rail Y-" + (hidden ? 2 : 1)
                + (hidden ? "; GRASS covers each interface." : "; original direct-below placement."));
        say(source,"Lane 1 X+0 WATER; lane 2 X+12 LAVA; lane 3 X+24 UNIVERSAL (water demo).");
        say(source,"Each load buffer starts with 24,000 mB; unload buffers start empty.");
        say(source,"Connect Mekanism pipes to east access at track Y-" + (hidden ? 2 : 1) + ".");
        say(source,"Commands: status, unload, reject (wrong fluids), valid (reset correct fluids), universal <fluid:id>.");
        say(source,"Manual loading: hold ONE filled fluid container and Shift + right-click a tanker.");
        return Command.SINGLE_SUCCESS;
    }

    private static int advance(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException(); Course course=COURSES.get(player.getUUID());
        if (course==null || course.level()!=player.serverLevel()) {
            say(source,"No tracked course; run /tc_fluid_course first."); return 0;
        }
        for (int lane=0;lane<3;lane++) {
            var entity=course.level().getEntity(course.tankers()[lane]);
            if (!(entity instanceof TctTanker tanker)) {
                say(source,"Missing tanker in lane " + (lane+1)); return 0;
            }
            BlockPos target=course.origin().offset(LANES[lane],0,UNLOAD_Z);
            AABB blocked=new AABB(target).inflate(1.0,0.5,1.0);
            if (!course.level().getEntitiesOfClass(TctTanker.class,blocked,e->e!=tanker).isEmpty()) {
                say(source,"Unloading station blocked in lane " + (lane+1)); return 0;
            }
        }
        for (int lane=0;lane<3;lane++) {
            TctTanker tanker=(TctTanker)course.level().getEntity(course.tankers()[lane]);
            BlockPos p=course.origin().offset(LANES[lane],0,UNLOAD_Z);
            tanker.moveTo(p.getX()+0.5,p.getY()+1.0,p.getZ()+0.5,0f,0f);
            tanker.setDeltaMovement(Vec3.ZERO);
        }
        say(source,"All three tankers moved to unload stations. Allow 2-5 seconds, then run /tc_fluid_course status.");
        return Command.SINGLE_SUCCESS;
    }

    private static FluidInterfaceBlockEntity interfaceAt(Course course, int lane, int z) {
        BlockPos p = course.origin().offset(LANES[lane], course.hidden() ? -2 : -1, z);
        var blockEntity = course.level().getBlockEntity(p);
        return blockEntity instanceof FluidInterfaceBlockEntity dock ? dock : null;
    }

    private static String show(FluidStack stack) {
        return (stack.isEmpty() ? "Empty" : stack.getDisplayName().getString()) + " " + stack.getAmount() + " mB";
    }

    private static void empty(IFluidHandler handler) {
        handler.drain(64000, IFluidHandler.FluidAction.EXECUTE);
    }

    /** Reset only course fixtures; invalid combinations trigger nearby popup warnings. */
    private static int configure(CommandSourceStack source, boolean rejection) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Course course = COURSES.get(player.getUUID());
        if (course == null || course.level() != player.serverLevel()) {
            say(source,"No tracked course. First run /tc_fluid_course or /tc_fluid_course hidden."); return 0;
        }
        TctTanker[] tankers = new TctTanker[3];
        FluidInterfaceBlockEntity[] interfaces = new FluidInterfaceBlockEntity[3];
        for (int lane=0;lane<3;lane++) {
            var entity = course.level().getEntity(course.tankers()[lane]);
            if (!(entity instanceof TctTanker tanker)) { say(source,"Missing tanker in lane " + (lane+1)); return 0; }
            FluidInterfaceBlockEntity dock=interfaceAt(course,lane,LOAD_Z);
            if (dock == null) { say(source,"Missing load interface in lane " + (lane+1)); return 0; }
            tankers[lane]=tanker; interfaces[lane]=dock;
        }
        for (int lane=0;lane<3;lane++) {
            TctTanker tanker=tankers[lane];
            IFluidHandler car=tanker.getFluidHandler();
            IFluidHandler dock=interfaces[lane].getFluidHandler();
            empty(car);
            empty(dock);
            // Wrong inputs: Water car gets lava; Lava car gets water;
            // Universal already contains water but dock presents lava.
            if (rejection && lane==2) car.fill(new FluidStack(Fluids.WATER,1000), IFluidHandler.FluidAction.EXECUTE);
            FluidStack liquid=rejection
                    ? new FluidStack(lane==0 ? Fluids.LAVA : lane==1 ? Fluids.WATER : Fluids.LAVA, 4000)
                    : new FluidStack(lane==1 ? Fluids.LAVA : Fluids.WATER, 24000);
            int added=dock.fill(liquid, IFluidHandler.FluidAction.EXECUTE);
            if (added != liquid.getAmount()) { say(source,"Failed to set source in lane " + (lane+1)); return 0; }
            interfaces[lane].setChanged();
            BlockPos target=course.origin().offset(LANES[lane],0,LOAD_Z);
            tanker.moveTo(target.getX()+0.5,target.getY()+1.0,target.getZ()+0.5,0f,0f);
            tanker.setDeltaMovement(Vec3.ZERO);
        }
        if (rejection) {
            say(source,"Rejection fixtures armed: WATER<-Lava, LAVA<-Water, UNIVERSAL(Water 1,000)<-Lava.");
            say(source,"Wait 2-5 seconds near loading station for wrong-fluid popup, then /tc_fluid_course status.");
            say(source,"Correct result: all 3 load buffers remain 4,000 mB and none of the cars gains fluid.");
        } else {
            say(source,"Valid loading restored: water/water, lava/lava, universal/water (24,000 mB each).");
            say(source,"Use /tc_fluid_course universal minecraft:lava to test universal accepting lava.");
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int universal(CommandSourceStack source, String requested) throws CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        Course course=COURSES.get(player.getUUID());
        if (course==null || course.level()!=player.serverLevel()) {
            say(source,"No tracked course; build it first."); return 0;
        }
        ResourceLocation key;
        try { key = new ResourceLocation(requested); }
        catch (Exception e) { say(source,"Invalid fluid registry ID: " + requested); return 0; }
        var fluid = ForgeRegistries.FLUIDS.getValue(key);
        if (fluid==null || fluid==net.minecraft.world.level.material.Fluids.EMPTY) {
            say(source,"Unknown fluid: " + requested); return 0;
        }
        var entity=course.level().getEntity(course.tankers()[2]);
        FluidInterfaceBlockEntity dock=interfaceAt(course,2,LOAD_Z);
        if (!(entity instanceof TctTanker tanker) || dock==null) {
            say(source,"Universal tanker or load interface missing."); return 0;
        }
        empty(tanker.getFluidHandler());
        empty(dock.getFluidHandler());
        FluidStack input=new FluidStack(fluid,24000);
        int loaded=dock.getFluidHandler().fill(input,IFluidHandler.FluidAction.EXECUTE);
        if (loaded!=24000) { say(source,"Fluid cannot be stored in the interface: " + key); return 0; }
        dock.setChanged();
        BlockPos at=course.origin().offset(LANES[2],0,LOAD_Z);
        tanker.moveTo(at.getX()+0.5,at.getY()+1.0,at.getZ()+0.5,0f,0f);
        tanker.setDeltaMovement(Vec3.ZERO);
        say(source,"Universal tanker test ready with 24,000 mB of " + key + ". Wait, then /tc_fluid_course status.");
        return Command.SINGLE_SUCCESS;
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException(); Course course=COURSES.get(player.getUUID());
        if (course==null || course.level()!=player.serverLevel()) {
            say(source,"No tracked course in this session; start with /tc_fluid_course or /tc_fluid_course hidden."); return 0;
        }
        String[] names={"WATER","LAVA","UNIVERSAL"};
        for (int lane=0;lane<3;lane++) {
            var entity=course.level().getEntity(course.tankers()[lane]);
            FluidInterfaceBlockEntity from=interfaceAt(course,lane,LOAD_Z);
            FluidInterfaceBlockEntity to=interfaceAt(course,lane,UNLOAD_Z);
            String car=entity instanceof TctTanker tank ? show(tank.getStoredFluid()) : "MISSING";
            String fromFluid=from==null ? "MISSING" : show(from.getTank().getFluid());
            String toFluid=to==null ? "MISSING" : show(to.getTank().getFluid());
            say(source,names[lane]+"  source="+fromFluid+", tanker="+car+", unload="+toFluid);
        }
        return Command.SINGLE_SUCCESS;
    }
}
