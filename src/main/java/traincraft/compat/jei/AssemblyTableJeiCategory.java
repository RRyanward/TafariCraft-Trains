package traincraft.compat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import traincraft.Traincraft;
import traincraft.recipe.AssemblyTableRecipeBook;
import traincraft.registry.TCBlocks;

import java.util.ArrayList;
import java.util.List;

/**
 * DEV38 r1e7
 *
 * The JEI page now uses the same classic Assembly Table artwork and the same
 * 10 input positions as the in-game Assembly GUI instead of a blank JEI panel.
 */
public final class AssemblyTableJeiCategory
        implements IRecipeCategory<AssemblyTableRecipeBook.Recipe> {

    private static final int[][] INPUT = {
            {25, 27}, {79, 27}, {115, 27}, {145, 27},
            {25, 61}, {79, 61}, {115, 61},
            {43, 93}, {79, 93}, {145, 93}
    };

    // First output-bank position in the classic Assembly texture.
    private static final int OUTPUT_X = 91;
    private static final int OUTPUT_Y = 132;

    private final int tier;
    private final RecipeType<AssemblyTableRecipeBook.Recipe> recipeType;
    private final IDrawable background;
    private final IDrawable icon;

    public AssemblyTableJeiCategory(
            IGuiHelper guiHelper,
            int tier,
            RecipeType<AssemblyTableRecipeBook.Recipe> recipeType) {
        this.tier = tier;
        this.recipeType = recipeType;

        ResourceLocation texture = new ResourceLocation(
                Traincraft.MOD_ID,
                "textures/gui/gui_assemblytable_tier" + tier + ".png");

        // Crop only the useful top portion of the original 176x256 GUI:
        // 10 classic input slots + Storage/Output row + first output row.
        this.background = guiHelper.createDrawable(texture, 0, 0, 176, 154);

        ItemStack table = switch (tier) {
            case 2 -> new ItemStack(TCBlocks.ASSEMBLY_TABLE_II.get());
            case 3 -> new ItemStack(TCBlocks.ASSEMBLY_TABLE_III.get());
            default -> new ItemStack(TCBlocks.ASSEMBLY_TABLE_I.get());
        };
        this.icon = guiHelper.createDrawableItemStack(table);
    }

    @Override
    public RecipeType<AssemblyTableRecipeBook.Recipe> getRecipeType() {
        return recipeType;
    }

    @Override
    public Component getTitle() {
        return Component.literal("TC Assembly Table " + roman(tier));
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(
            IRecipeLayoutBuilder builder,
            AssemblyTableRecipeBook.Recipe recipe,
            IFocusGroup focuses) {

        AssemblyTableRecipeBook.IngredientSpec[] ingredients =
                recipe.ingredients();

        for (int i = 0; i < 10; i++) {
            AssemblyTableRecipeBook.IngredientSpec spec = ingredients[i];
            if (spec == null) {
                continue;
            }

            List<ItemStack> choices = choices(spec);
            if (!choices.isEmpty()) {
                // These are the exact item coordinates used by the live
                // Assembly screen, so JEI ingredients line up with its art.
                builder.addSlot(
                                RecipeIngredientRole.INPUT,
                                INPUT[i][0],
                                INPUT[i][1])
                        .addItemStacks(choices);
            }
        }

        ItemStack output = recipe.output();
        if (!output.isEmpty()) {
            builder.addSlot(
                            RecipeIngredientRole.OUTPUT,
                            OUTPUT_X,
                            OUTPUT_Y)
                    .addItemStack(output);
        }
    }

    private static String roman(int tier) {
        return switch (tier) {
            case 2 -> "II";
            case 3 -> "III";
            default -> "I";
        };
    }

    private static List<ItemStack> choices(
            AssemblyTableRecipeBook.IngredientSpec spec) {
        return switch (spec.id()) {
            case "@dye" -> counted(spec.count(),
                    Items.WHITE_DYE, Items.ORANGE_DYE, Items.MAGENTA_DYE,
                    Items.LIGHT_BLUE_DYE, Items.YELLOW_DYE, Items.LIME_DYE,
                    Items.PINK_DYE, Items.GRAY_DYE, Items.LIGHT_GRAY_DYE,
                    Items.CYAN_DYE, Items.PURPLE_DYE, Items.BLUE_DYE,
                    Items.BROWN_DYE, Items.GREEN_DYE, Items.RED_DYE,
                    Items.BLACK_DYE);
            case "@planks" -> counted(spec.count(),
                    Items.OAK_PLANKS, Items.SPRUCE_PLANKS,
                    Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS,
                    Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS,
                    Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS,
                    Items.CRIMSON_PLANKS, Items.WARPED_PLANKS);
            case "@logs" -> counted(spec.count(),
                    Items.OAK_LOG, Items.SPRUCE_LOG,
                    Items.BIRCH_LOG, Items.JUNGLE_LOG,
                    Items.ACACIA_LOG, Items.DARK_OAK_LOG,
                    Items.MANGROVE_LOG, Items.CHERRY_LOG,
                    Items.CRIMSON_STEM, Items.WARPED_STEM);
            default -> {
                Item item = BuiltInRegistries.ITEM.get(
                        new ResourceLocation(spec.id()));
                if (item == Items.AIR) {
                    yield List.of();
                }
                yield List.of(new ItemStack(item, spec.count()));
            }
        };
    }

    private static List<ItemStack> counted(int count, Item... items) {
        ArrayList<ItemStack> result = new ArrayList<>(items.length);
        for (Item item : items) {
            result.add(new ItemStack(item, count));
        }
        return result;
    }
}
