package traincraft.worldgen.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import traincraft.worldgen.Dev56J19VillageRules;

import java.util.function.Predicate;

@Mixin(Structure.class)
public abstract class Dev56J19StructureGenerationMixin {
    @Inject(method = "generate", at = @At("RETURN"))
    private void tafaricraft$j19FilterNewVillage(RegistryAccess registryAccess,
                                                ChunkGenerator generator,
                                                BiomeSource biomeSource,
                                                RandomState randomState,
                                                StructureTemplateManager templateManager,
                                                long seed,
                                                ChunkPos chunkPos,
                                                int references,
                                                LevelHeightAccessor heights,
                                                Predicate<Holder<Biome>> biomePredicate,
                                                CallbackInfoReturnable<StructureStart> cir) {
        Dev56J19VillageRules.filter((Structure) (Object) this, cir.getReturnValue(),
                registryAccess.registryOrThrow(Registries.STRUCTURE), seed, chunkPos);
    }
}
