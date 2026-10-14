package traincraft.block.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RangedWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import traincraft.menu.AssemblyTableMenu;
import traincraft.recipe.AssemblyTableRecipeBook;
import traincraft.registry.TCBlockEntities;

import java.util.List;

public final class AssemblyTableBlockEntity extends BlockEntity implements MenuProvider {
    public static final int INPUT_START = 0;
    public static final int INPUT_END = 9;
    public static final int STORAGE_START = 10;
    public static final int STORAGE_END = 17;
    public static final int OUTPUT_START = 18;
    public static final int OUTPUT_END = 25;
    public static final int OUTPUT_SLOT_COUNT = OUTPUT_END - OUTPUT_START + 1;

    private boolean updating;
    private List<AssemblyTableRecipeBook.Recipe> currentRecipes = List.of();
    private int outputPage;

    private final ItemStackHandler items = new ItemStackHandler(26) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (!updating && slot <= STORAGE_END) {
                outputPage = 0;
                recomputeRecipe();
            }
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot < OUTPUT_START;
        }
    };

    // Automation may access only real input/storage slots 0..17.
    // Output 18..25 is virtual crafting-result state and must be taken through
    // AssemblyTableMenu so the exact selected recipe is consumed once.
    private LazyOptional<IItemHandler> itemCap =
            LazyOptional.of(() -> new RangedWrapper(items, INPUT_START, OUTPUT_START));

    public AssemblyTableBlockEntity(BlockPos pos, BlockState state) {
        super(TCBlockEntities.ASSEMBLY_TABLE.get(), pos, state);
    }

    public ItemStackHandler getItems() {
        return items;
    }

    public int getTier() {
        if (getBlockState().getBlock() instanceof AssemblyTableBlock block) {
            return block.getTier();
        }
        return 1;
    }

    public int getMatchingRecipeCount() {
        return currentRecipes.size();
    }

    public int getOutputPage() {
        return outputPage;
    }

    public int getOutputPageCount() {
        return Math.max(1, (currentRecipes.size() + OUTPUT_SLOT_COUNT - 1) / OUTPUT_SLOT_COUNT);
    }

    public void changeOutputPage(int delta) {
        if (level == null || level.isClientSide) return;
        currentRecipes = AssemblyTableRecipeBook.findAll(getTier(), items);
        int pages = Math.max(1, (currentRecipes.size() + OUTPUT_SLOT_COUNT - 1) / OUTPUT_SLOT_COUNT);
        if (pages <= 1) {
            outputPage = 0;
        } else {
            outputPage = Math.floorMod(outputPage + delta, pages);
        }
        recomputeRecipe();
    }

    /**
     * DEV37 test helper: show the page containing this exact Recipe object and
     * return its visible output-slot offset (0..7), or -1 if it does not match.
     */
    public int showRecipe(AssemblyTableRecipeBook.Recipe recipe) {
        if (level == null || level.isClientSide) return -1;
        currentRecipes = AssemblyTableRecipeBook.findAll(getTier(), items);
        int index = currentRecipes.indexOf(recipe);
        if (index < 0) return -1;
        outputPage = index / OUTPUT_SLOT_COUNT;
        recomputeRecipe();
        return index % OUTPUT_SLOT_COUNT;
    }

    private void recomputeRecipe() {
        if (level == null) return;
        updating = true;
        try {
            for (int i = OUTPUT_START; i <= OUTPUT_END; i++) {
                items.setStackInSlot(i, ItemStack.EMPTY);
            }

            currentRecipes = AssemblyTableRecipeBook.findAll(getTier(), items);
            int pageCount = Math.max(1, (currentRecipes.size() + OUTPUT_SLOT_COUNT - 1) / OUTPUT_SLOT_COUNT);
            if (outputPage >= pageCount) outputPage = pageCount - 1;
            if (outputPage < 0) outputPage = 0;

            int first = outputPage * OUTPUT_SLOT_COUNT;
            for (int visible = 0; visible < OUTPUT_SLOT_COUNT; visible++) {
                int recipeIndex = first + visible;
                if (recipeIndex >= currentRecipes.size()) break;
                ItemStack out = currentRecipes.get(recipeIndex).output();
                if (!out.isEmpty()) {
                    items.setStackInSlot(OUTPUT_START + visible, out);
                }
            }
        } finally {
            updating = false;
        }
        setChanged();
    }

    public void takeCraft(int visibleOutputSlot) {
        if (visibleOutputSlot < 0 || visibleOutputSlot >= OUTPUT_SLOT_COUNT) {
            recomputeRecipe();
            return;
        }

        List<AssemblyTableRecipeBook.Recipe> matches =
                AssemblyTableRecipeBook.findAll(getTier(), items);
        int recipeIndex = outputPage * OUTPUT_SLOT_COUNT + visibleOutputSlot;
        if (recipeIndex < 0 || recipeIndex >= matches.size()) {
            recomputeRecipe();
            return;
        }

        AssemblyTableRecipeBook.Recipe recipe = matches.get(recipeIndex);
        updating = true;
        try {
            for (int i = 0; i < 10; i++) {
                AssemblyTableRecipeBook.IngredientSpec spec = recipe.ingredients()[i];
                if (spec != null) {
                    items.extractItem(i, spec.count(), false);
                }
            }
        } finally {
            updating = false;
        }
        outputPage = 0;
        recomputeRecipe();
    }

    /** Compatibility path for any old caller: first visible recipe. */
    public void takeCraft() {
        takeCraft(0);
    }

    public void dropStoredContents() {
        if (level == null || level.isClientSide) return;
        for (int i = 0; i <= STORAGE_END; i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack.copy());
                items.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Assembly Table Tier " + getTier());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AssemblyTableMenu(id, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        tag.putInt("OutputPage", outputPage);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) items.deserializeNBT(tag.getCompound("Items"));
        outputPage = Math.max(0, tag.getInt("OutputPage"));
        recomputeRecipe();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        recomputeRecipe();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemCap = LazyOptional.of(() -> new RangedWrapper(items, INPUT_START, OUTPUT_START));
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        return super.getCapability(cap, side);
    }
}
