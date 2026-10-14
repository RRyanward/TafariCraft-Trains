package traincraft.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import traincraft.Traincraft;

/** Keeps egg-spawned railway villagers attached to a matching local workstation. */
@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RailwayVillagerCare {
    private RailwayVillagerCare() {}

    /** Reserve one free matching POI ticket, then let vanilla AI perform work/pathfinding. */
    public static void bindWorkstation(ServerLevel level, Villager villager, String role) {
        if (villager.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE)) return;
        RegistryObject<PoiType> poi = RailwayVillagers.jobSite(role);
        if (poi == null) return;
        level.getPoiManager().take(holder -> holder.value() == poi.get(),
                (holder, blockPos) -> true, villager.blockPosition(), 24)
                .ifPresent(blockPos -> villager.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                        GlobalPos.of(level.dimension(), blockPos)));
    }

    @SubscribeEvent
    public static void onVillagerTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (villager.tickCount % 100 != 0) return;
        if (!villager.getPersistentData().contains(RailwayVillagerEgg.RAILWAY_ROLE_TAG, Tag.TAG_STRING)) return;
        String role = villager.getPersistentData().getString(RailwayVillagerEgg.RAILWAY_ROLE_TAG);
        RegistryObject<VillagerProfession> expected = RailwayVillagers.forEgg(role);
        if (expected == null) return;
        // Egg-spawned specialists retain their selected job even if a workstation
        // was temporarily unavailable, including after save/load.
        if (villager.getVillagerData().getProfession() != expected.get()) {
            villager.setVillagerData(villager.getVillagerData().setProfession(expected.get()));
            villager.refreshBrain(level);
            RailwayVillagerDebug.report("RESTORE role=" + role + " uuid=" + villager.getUUID());
        }
        bindWorkstation(level, villager, role);
        RailwayTrades.ensureNoviceOffers(villager);
    }
}
