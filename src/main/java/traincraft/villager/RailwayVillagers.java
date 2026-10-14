package traincraft.villager;

import com.google.common.collect.ImmutableSet;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import traincraft.Traincraft;
import traincraft.registry.TCBlocks;

public final class RailwayVillagers {
    public static final DeferredRegister<PoiType> POIS = DeferredRegister.create(net.minecraft.core.registries.Registries.POINT_OF_INTEREST_TYPE, Traincraft.MOD_ID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION, Traincraft.MOD_ID);
    private RailwayVillagers() {}
    private static RegistryObject<PoiType> poi(String id, RegistryObject<Block> block) {
        return POIS.register(id, () -> new PoiType(ImmutableSet.copyOf(block.get().getStateDefinition().getPossibleStates()), 1, 1));
    }
    private static RegistryObject<VillagerProfession> job(String id, RegistryObject<PoiType> poi) {
        return PROFESSIONS.register(id, () -> new VillagerProfession(id,
            holder -> holder.value() == poi.get(), holder -> holder.value() == poi.get(),
            ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_FLETCHER));
    }
    public static final RegistryObject<PoiType> STATION_CHIEF_POI = poi("station_chief_poi", TCBlocks.STATION_OFFICE_DESK);
    public static final RegistryObject<VillagerProfession> STATION_CHIEF = job("station_chief", STATION_CHIEF_POI);
    public static final RegistryObject<PoiType> RAILWAY_ENGINEER_POI = poi("railway_engineer_poi", TCBlocks.RAILWAY_TOOLBENCH);
    public static final RegistryObject<VillagerProfession> RAILWAY_ENGINEER = job("railway_engineer", RAILWAY_ENGINEER_POI);
    public static final RegistryObject<PoiType> TRACK_WORKER_POI = poi("track_worker_poi", TCBlocks.TRACK_MAINTENANCE_BENCH);
    public static final RegistryObject<VillagerProfession> TRACK_WORKER = job("track_worker", TRACK_WORKER_POI);
    public static final RegistryObject<PoiType> TRAIN_CONDUCTOR_POI = poi("train_conductor_poi", TCBlocks.TICKET_BOOTH);
    public static final RegistryObject<VillagerProfession> TRAIN_CONDUCTOR = job("train_conductor", TRAIN_CONDUCTOR_POI);
    public static final RegistryObject<PoiType> FUEL_DEPOT_OPERATOR_POI = poi("fuel_depot_operator_poi", TCBlocks.FUEL_DEPOT_COUNTER);
    public static final RegistryObject<VillagerProfession> FUEL_DEPOT_OPERATOR = job("fuel_depot_operator", FUEL_DEPOT_OPERATOR_POI);
    public static final RegistryObject<PoiType> FREIGHT_YARD_CLERK_POI = poi("freight_yard_clerk_poi", TCBlocks.FREIGHT_OFFICE_DESK);
    public static final RegistryObject<VillagerProfession> FREIGHT_YARD_CLERK = job("freight_yard_clerk", FREIGHT_YARD_CLERK_POI);
    /** Corresponding job station and profession for each railway egg. */
    public static RegistryObject<VillagerProfession> forEgg(String role) {
        return switch (role) {
            case "station_chief" -> STATION_CHIEF;
            case "railway_engineer" -> RAILWAY_ENGINEER;
            case "track_worker" -> TRACK_WORKER;
            case "train_conductor" -> TRAIN_CONDUCTOR;
            case "fuel_depot_operator" -> FUEL_DEPOT_OPERATOR;
            case "freight_yard_clerk" -> FREIGHT_YARD_CLERK;
            default -> null;
        };
    }

    public static RegistryObject<PoiType> jobSite(String role) {
        return switch (role) {
            case "station_chief" -> STATION_CHIEF_POI;
            case "railway_engineer" -> RAILWAY_ENGINEER_POI;
            case "track_worker" -> TRACK_WORKER_POI;
            case "train_conductor" -> TRAIN_CONDUCTOR_POI;
            case "fuel_depot_operator" -> FUEL_DEPOT_OPERATOR_POI;
            case "freight_yard_clerk" -> FREIGHT_YARD_CLERK_POI;
            default -> null;
        };
    }

    public static void register(IEventBus bus) { POIS.register(bus); PROFESSIONS.register(bus); }
}
