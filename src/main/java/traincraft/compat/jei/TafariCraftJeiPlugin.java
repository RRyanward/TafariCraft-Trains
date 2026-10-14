package traincraft.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import traincraft.Traincraft;
import traincraft.recipe.AssemblyTableRecipeBook;
import traincraft.registry.TCBlocks;

import java.util.List;

/**
 * DEV38 r1e7
 *
 * JEI categories are separated by exact Assembly tier so the JEI recipe
 * page can use the matching classic Traincraft Tier I / II / III texture.
 */
@JeiPlugin
public final class TafariCraftJeiPlugin implements IModPlugin {
    public static final RecipeType<AssemblyTableRecipeBook.Recipe> ASSEMBLY_I =
            RecipeType.create(
                    Traincraft.MOD_ID,
                    "assembly_table_1",
                    AssemblyTableRecipeBook.Recipe.class);

    public static final RecipeType<AssemblyTableRecipeBook.Recipe> ASSEMBLY_II =
            RecipeType.create(
                    Traincraft.MOD_ID,
                    "assembly_table_2",
                    AssemblyTableRecipeBook.Recipe.class);

    public static final RecipeType<AssemblyTableRecipeBook.Recipe> ASSEMBLY_III =
            RecipeType.create(
                    Traincraft.MOD_ID,
                    "assembly_table_3",
                    AssemblyTableRecipeBook.Recipe.class);

    private AssemblyTableJeiCategory tier1;
    private AssemblyTableJeiCategory tier2;
    private AssemblyTableJeiCategory tier3;

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(Traincraft.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var gui = registration.getJeiHelpers().getGuiHelper();

        tier1 = new AssemblyTableJeiCategory(gui, 1, ASSEMBLY_I);
        tier2 = new AssemblyTableJeiCategory(gui, 2, ASSEMBLY_II);
        tier3 = new AssemblyTableJeiCategory(gui, 3, ASSEMBLY_III);

        registration.addRecipeCategories(tier1, tier2, tier3);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<AssemblyTableRecipeBook.Recipe> all = AssemblyTableRecipeBook.all();

        registration.addRecipes(
                ASSEMBLY_I,
                all.stream().filter(recipe -> recipe.tier() == 1).toList());

        registration.addRecipes(
                ASSEMBLY_II,
                all.stream().filter(recipe -> recipe.tier() == 2).toList());

        registration.addRecipes(
                ASSEMBLY_III,
                all.stream().filter(recipe -> recipe.tier() == 3).toList());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        ItemStack table1 = new ItemStack(TCBlocks.ASSEMBLY_TABLE_I.get());
        ItemStack table2 = new ItemStack(TCBlocks.ASSEMBLY_TABLE_II.get());
        ItemStack table3 = new ItemStack(TCBlocks.ASSEMBLY_TABLE_III.get());

        // Tier I recipes are valid on I / II / III.
        registration.addRecipeCatalyst(table1, ASSEMBLY_I);
        registration.addRecipeCatalyst(table2, ASSEMBLY_I);
        registration.addRecipeCatalyst(table3, ASSEMBLY_I);

        // Tier II recipes are valid on II / III.
        registration.addRecipeCatalyst(table2, ASSEMBLY_II);
        registration.addRecipeCatalyst(table3, ASSEMBLY_II);

        // Tier III recipes are valid on III only.
        registration.addRecipeCatalyst(table3, ASSEMBLY_III);
    }
}
