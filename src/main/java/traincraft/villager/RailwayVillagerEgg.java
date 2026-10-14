package traincraft.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.registries.RegistryObject;

/** Profession-aware spawn egg preserving vanilla villager model, AI, NBT and trades. */
public class RailwayVillagerEgg extends Item {
    public static final String RAILWAY_ROLE_TAG = "TafariCraftRailwayRole";
    private final RegistryObject<VillagerProfession> profession;

    public RailwayVillagerEgg(Properties properties, RegistryObject<VillagerProfession> profession) {
        super(properties);
        this.profession = profession;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) return InteractionResult.FAIL;
        villager.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                MobSpawnType.SPAWN_EGG, null, null);
        VillagerProfession role = profession.get();
        villager.setVillagerData(villager.getVillagerData().setProfession(role).setLevel(1));
        villager.refreshBrain(level);
        // Save a small role marker so Minecraft's job-site AI can reattach the
        // same profession after chunk reload; other villagers remain untouched.
        String id = role.name();
        villager.getPersistentData().putString(RAILWAY_ROLE_TAG, id);
        villager.setPersistenceRequired();
        if (!level.addFreshEntity(villager)) return InteractionResult.FAIL;
        RailwayVillagerCare.bindWorkstation(level, villager, id);
        // Request normal Forge-populated trade offers; fallback only if empty.
        RailwayTrades.ensureNoviceOffers(villager);
        RailwayVillagerDebug.report("EGG " + id + " " + villager.getUUID()
                + " profession=" + villager.getVillagerData().getProfession().name()
                + " offers=" + villager.getOffers().size());
        level.playSound(null, pos, SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.0F);
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
