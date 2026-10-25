package traincraft.worldgen.mixin;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.world.level.levelgen.structure.StructureStart;

@Mixin(StructureStart.class)
public interface Dev56J19StructureStartAccessor {
    @Mutable @Accessor("pieceContainer")
    void tafaricraft$setPieceContainer(PiecesContainer value);
    @Accessor("cachedBoundingBox")
    void tafaricraft$setCachedBoundingBox(BoundingBox value);
}
