package traincraft.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import traincraft.Traincraft;
import traincraft.menu.AssemblyTableMenu;
import traincraft.recipe.AssemblyTableRecipeBook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * DEV38 r1e5
 *
 * Same accepted r1e4 classic Traincraft look, now responsive:
 *
 * WIDE:
 *   vertical classic tab rail | original 176x256 table | blue/gold info panel
 *
 * COMPACT:
 *   original table | blue/gold info panel
 *                    [Info][Recipes][Output] icon tabs across panel top
 *
 * VERY NARROW:
 *   original table remains centered; the side panel docks against the
 *   screen edge and may overlap only the unused right edge of the GUI.
 *
 * The 10 Assembly input slots, storage/output slots and menu coordinates
 * are never scaled or moved relative to the original table artwork.
 */
public final class AssemblyTableScreen extends AbstractContainerScreen<AssemblyTableMenu> {
    private static final ResourceLocation TIER1 =
            new ResourceLocation(Traincraft.MOD_ID, "textures/gui/gui_assemblytable_tier1.png");
    private static final ResourceLocation TIER2 =
            new ResourceLocation(Traincraft.MOD_ID, "textures/gui/gui_assemblytable_tier2.png");
    private static final ResourceLocation TIER3 =
            new ResourceLocation(Traincraft.MOD_ID, "textures/gui/gui_assemblytable_tier3.png");

    private static final int[][] INPUT = {
            {25,27}, {79,27}, {115,27}, {145,27},
            {25,61}, {79,61}, {115,61},
            {43,93}, {79,93}, {145,93}
    };

    private static final int TAB_SIZE = 26;
    private static final int TAB_GAP = 3;
    private static final int PANEL_WIDE = 140;
    private static final int PANEL_MIN = 112;
    private static final int CLASSIC_RAIL_W = 34;

    private static final int COLOR_RAIL = 0xEE17191F;
    private static final int COLOR_TAB = 0xFF252933;
    private static final int COLOR_TAB_HOVER = 0xFF343A48;
    private static final int COLOR_GOLD = 0xFFD7A72B;
    private static final int COLOR_PANEL = 0xF01D3158;
    private static final int COLOR_PANEL_INNER = 0xE9253B64;
    private static final int COLOR_TEXT = 0xFFE5E5E5;
    private static final int COLOR_MUTED = 0xFFB8C2D4;

    private enum SideTab { INFO, RECIPES, OUTPUT }

    private SideTab sideTab = SideTab.RECIPES;
    private int recipeIndex = 0;
    private boolean showRecipeOverlay = false;

    private boolean verticalTabs = true;
    private boolean overlayPanel = false;
    private int panelX;
    private int panelY;
    private int panelWidth = PANEL_WIDE;
    private int panelHeight = 222;
    private int tabsX;
    private int tabsY;

    private Button recipePrevious;
    private Button recipeNext;
    private Button recipeShow;
    private Button recipeClear;
    private Button outputPrevious;
    private Button outputNext;

    public AssemblyTableScreen(AssemblyTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 256;
        inventoryLabelY = 162;
    }

    @Override
    protected void init() {
        super.init();
        configureResponsiveLayout();

        recipePrevious = addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        button -> cycleRecipe(-1))
                .bounds(panelX + 6, panelY + panelHeight - 28, 22, 18)
                .build());

        recipeNext = addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> cycleRecipe(1))
                .bounds(panelX + 30, panelY + panelHeight - 28, 22, 18)
                .build());

        recipeShow = addRenderableWidget(Button.builder(
                        Component.literal("Show"),
                        button -> showRecipeOverlay = true)
                .bounds(panelX + 54, panelY + panelHeight - 28, 38, 18)
                .build());

        recipeClear = addRenderableWidget(Button.builder(
                        Component.literal("Clear"),
                        button -> showRecipeOverlay = false)
                .bounds(panelX + 94, panelY + panelHeight - 28, 40, 18)
                .build());

        outputPrevious = addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        button -> requestOutputPage(AssemblyTableMenu.PAGE_PREVIOUS))
                .bounds(panelX + 27, panelY + 92, 36, 20)
                .build());

        outputNext = addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> requestOutputPage(AssemblyTableMenu.PAGE_NEXT))
                .bounds(panelX + 75, panelY + 92, 36, 20)
                .build());

        updateButtonVisibility();
    }

    private void configureResponsiveLayout() {
        // Keep the original 176x256 slot/art relationship intact.
        topPos = Math.max(0, (height - imageHeight) / 2);

        final int wideTotal = CLASSIC_RAIL_W + 4 + imageWidth + 2 + PANEL_WIDE;
        if (width >= wideTotal + 8) {
            verticalTabs = true;
            overlayPanel = false;
            panelWidth = PANEL_WIDE;

            int groupX = Math.max(4, (width - wideTotal) / 2);
            tabsX = groupX + 4;
            leftPos = groupX + CLASSIC_RAIL_W + 4;
            panelX = leftPos + imageWidth + 2;
            panelY = topPos + 2;
        } else {
            verticalTabs = false;

            int availableForPanel = width - imageWidth - 6;
            if (availableForPanel >= PANEL_MIN) {
                overlayPanel = false;
                panelWidth = Math.min(PANEL_WIDE, availableForPanel);

                int total = imageWidth + 2 + panelWidth;
                int groupX = Math.max(2, (width - total) / 2);
                leftPos = groupX;
                panelX = leftPos + imageWidth + 2;
            } else {
                // Emergency narrow layout.  Do not distort the classic table;
                // dock the side panel to the screen edge and keep controls usable.
                overlayPanel = true;
                panelWidth = Math.min(PANEL_WIDE, Math.max(PANEL_MIN, width - 8));
                leftPos = Math.max(0, (width - imageWidth) / 2);
                panelX = Math.max(4, width - panelWidth - 4);
            }

            panelY = topPos + 2;
            tabsX = panelX + 6;
        }

        panelHeight = Math.max(170, Math.min(222, height - panelY - 4));

        if (verticalTabs) {
            tabsY = topPos + 18;
        } else {
            tabsY = panelY + 5;
        }
    }

    private String ageName() {
        return switch (menu.getTier()) {
            case 2 -> "Steel age";
            case 3 -> "Advanced age";
            default -> "Iron age";
        };
    }

    private String tabName(int index) {
        return switch (index) {
            case 0 -> "Info";
            case 1 -> "Recipes";
            default -> "Output";
        };
    }

    private void setSideTab(SideTab tab) {
        sideTab = tab;
        if (tab != SideTab.RECIPES) {
            showRecipeOverlay = false;
        }
        updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        if (recipePrevious == null) return;
        boolean recipes = sideTab == SideTab.RECIPES;
        recipePrevious.visible = recipes;
        recipeNext.visible = recipes;
        recipeShow.visible = recipes;
        recipeClear.visible = recipes && panelWidth >= 136;

        boolean output = sideTab == SideTab.OUTPUT;
        outputPrevious.visible = output;
        outputNext.visible = output;
    }

    private void requestOutputPage(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private List<AssemblyTableRecipeBook.Recipe> recipesForTier() {
        ArrayList<AssemblyTableRecipeBook.Recipe> recipes = new ArrayList<>();
        for (AssemblyTableRecipeBook.Recipe recipe : AssemblyTableRecipeBook.all()) {
            if (recipe.tier() <= menu.getTier()) {
                recipes.add(recipe);
            }
        }
        recipes.sort(Comparator.comparing(AssemblyTableRecipeBook.Recipe::outputId));
        return List.copyOf(recipes);
    }

    private AssemblyTableRecipeBook.Recipe selectedRecipe() {
        List<AssemblyTableRecipeBook.Recipe> recipes = recipesForTier();
        if (recipes.isEmpty()) return null;
        recipeIndex = Math.floorMod(recipeIndex, recipes.size());
        return recipes.get(recipeIndex);
    }

    private void cycleRecipe(int delta) {
        List<AssemblyTableRecipeBook.Recipe> recipes = recipesForTier();
        if (recipes.isEmpty()) {
            recipeIndex = 0;
            return;
        }
        recipeIndex = Math.floorMod(recipeIndex + delta, recipes.size());
    }

    private ResourceLocation texture() {
        return switch (menu.getTier()) {
            case 2 -> TIER2;
            case 3 -> TIER3;
            default -> TIER1;
        };
    }

    private int tabLeft(int index) {
        return verticalTabs ? tabsX : tabsX + index * (TAB_SIZE + TAB_GAP);
    }

    private int tabTop(int index) {
        return verticalTabs ? tabsY + index * (TAB_SIZE + TAB_GAP) : tabsY;
    }

    private boolean tabHit(double mouseX, double mouseY, int index) {
        int x = tabLeft(index);
        int y = tabTop(index);
        return mouseX >= x && mouseX < x + TAB_SIZE
                && mouseY >= y && mouseY < y + TAB_SIZE;
    }

    private int hoveredTab(double mouseX, double mouseY) {
        for (int i = 0; i < 3; i++) {
            if (tabHit(mouseX, mouseY, i)) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (tabHit(mouseX, mouseY, 0)) {
                setSideTab(SideTab.INFO);
                return true;
            }
            if (tabHit(mouseX, mouseY, 1)) {
                setSideTab(SideTab.RECIPES);
                return true;
            }
            if (tabHit(mouseX, mouseY, 2)) {
                setSideTab(SideTab.OUTPUT);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        graphics.blit(texture(), leftPos, topPos, 0, 0, 176, 256, 256, 256);

        if (verticalTabs) {
            int railX = tabsX - 4;
            int railY = tabsY - 5;
            int railBottom = tabTop(2) + TAB_SIZE + 5;
            graphics.fill(railX, railY, tabsX + TAB_SIZE + 4, railBottom, COLOR_RAIL);
        }

        // In overlay emergency mode, the panel gets a slightly stronger
        // background so text remains readable over the table edge.
        int panelRail = overlayPanel ? 0xFA11141A : COLOR_RAIL;
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, panelRail);
        graphics.fill(panelX + 3, panelY + 3, panelX + panelWidth - 3, panelY + panelHeight - 3, COLOR_PANEL);

        int contentTop = verticalTabs ? panelY + 26 : panelY + 36;
        graphics.fill(
                panelX + 6,
                contentTop,
                panelX + panelWidth - 6,
                panelY + panelHeight - 34,
                COLOR_PANEL_INNER);

        renderClassicTab(graphics, mouseX, mouseY, 0, SideTab.INFO, new ItemStack(Items.GOLD_INGOT));
        renderClassicTab(graphics, mouseX, mouseY, 1, SideTab.RECIPES, new ItemStack(Items.WRITABLE_BOOK));
        renderClassicTab(graphics, mouseX, mouseY, 2, SideTab.OUTPUT, new ItemStack(Items.CHEST));

        renderInputValidity(graphics);
    }

    private void renderClassicTab(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int index,
            SideTab tab,
            ItemStack icon) {
        int x = tabLeft(index);
        int y = tabTop(index);
        boolean selected = sideTab == tab;
        boolean hover = tabHit(mouseX, mouseY, index);

        int outer = selected ? COLOR_GOLD : 0xFF0D0F13;
        graphics.fill(x, y, x + TAB_SIZE, y + TAB_SIZE, outer);
        graphics.fill(
                x + 2,
                y + 2,
                x + TAB_SIZE - 2,
                y + TAB_SIZE - 2,
                hover ? COLOR_TAB_HOVER : COLOR_TAB);

        graphics.renderItem(icon, x + 5, y + 5);
    }

    private void renderInputValidity(GuiGraphics graphics) {
        List<AssemblyTableRecipeBook.Recipe> recipes = recipesForTier();

        for (int slot = 0; slot < 10; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (stack.isEmpty()) continue;

            boolean valid = false;
            for (AssemblyTableRecipeBook.Recipe recipe : recipes) {
                AssemblyTableRecipeBook.IngredientSpec spec = recipe.ingredients()[slot];
                if (spec != null && spec.matches(stack)) {
                    valid = true;
                    break;
                }
            }

            int x = leftPos + INPUT[slot][0] - 1;
            int y = topPos + INPUT[slot][1] - 1;
            graphics.fill(x, y, x + 18, y + 18, valid ? 0x6644FF44 : 0x66FF4444);
        }
    }

    private int localPanelX() {
        return panelX - leftPos;
    }

    private int localPanelY() {
        return panelY - topPos;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, ageName(), 10, 6, COLOR_GOLD, false);
        graphics.drawString(font, "Storage", 10, 118, COLOR_MUTED, false);
        graphics.drawString(font, "Output", 90, 118, COLOR_GOLD, false);

        switch (sideTab) {
            case INFO -> renderInfoPanel(graphics);
            case RECIPES -> renderRecipesPanel(graphics);
            case OUTPUT -> renderOutputPanel(graphics);
        }
    }

    private void panelTitle(GuiGraphics graphics, String title) {
        int px = localPanelX();
        int py = localPanelY();
        int titleY = py + (verticalTabs ? 10 : 38);
        graphics.drawString(font, title, px + 8, titleY, COLOR_GOLD, false);
        graphics.fill(px + 6, titleY + 12, px + panelWidth - 6, titleY + 13, COLOR_GOLD);
    }

    private int panelBodyY() {
        return localPanelY() + (verticalTabs ? 36 : 62);
    }

    private void renderInfoPanel(GuiGraphics graphics) {
        int px = localPanelX();
        int y = panelBodyY();
        panelTitle(graphics, "INFO");

        graphics.drawString(font, "Input positions 1-10", px + 8, y, COLOR_TEXT, false);
        graphics.drawString(font, "Green = valid item", px + 8, y + 16, 0xFF7CFF7C, false);
        graphics.drawString(font, "Red = wrong/count", px + 8, y + 28, 0xFFFF7C7C, false);
        graphics.drawString(font, "Stack amount matters.", px + 8, y + 48, COLOR_MUTED, false);

        for (int i = 0; i < INPUT.length; i++) {
            graphics.drawString(
                    font,
                    Integer.toString(i + 1),
                    INPUT[i][0] + 5,
                    INPUT[i][1] + 5,
                    0xFFFFFF55,
                    true);
        }
    }

    private void renderRecipesPanel(GuiGraphics graphics) {
        int px = localPanelX();
        int y = panelBodyY();
        List<AssemblyTableRecipeBook.Recipe> recipes = recipesForTier();
        AssemblyTableRecipeBook.Recipe recipe = selectedRecipe();

        panelTitle(graphics, "RECIPES");
        graphics.drawString(
                font,
                recipes.isEmpty() ? "0 / 0" : (recipeIndex + 1) + " / " + recipes.size(),
                px + 8,
                y,
                COLOR_MUTED,
                false);

        if (recipe == null) {
            graphics.drawString(font, "No recipes.", px + 8, y + 16, 0xFFFF8888, false);
            return;
        }

        ItemStack output = recipe.output();
        graphics.renderItem(output, px + 8, y + 14);
        graphics.drawString(font, output.getHoverName(), px + 28, y + 18, COLOR_TEXT, false);
        graphics.drawString(font, "Tier " + recipe.tier(), px + 8, y + 36, COLOR_GOLD, false);

        int rowY = y + 52;
        int cutoff = localPanelY() + panelHeight - 48;
        for (int i = 0; i < 10; i++) {
            AssemblyTableRecipeBook.IngredientSpec spec = recipe.ingredients()[i];
            if (spec == null) continue;
            if (rowY > cutoff) break;
            graphics.drawString(
                    font,
                    (i + 1) + ": " + shortIngredient(spec),
                    px + 8,
                    rowY,
                    COLOR_TEXT,
                    false);
            rowY += 10;
        }

        if (panelWidth < 136 && showRecipeOverlay) {
            graphics.drawString(font, "Overlay ON", px + 8, cutoff + 10, 0xFF7CFF7C, false);
        }
    }

    private void renderOutputPanel(GuiGraphics graphics) {
        int px = localPanelX();
        int y = panelBodyY();
        panelTitle(graphics, "OUTPUT");

        graphics.drawString(font, "Duplicate-BOM", px + 8, y, COLOR_TEXT, false);
        graphics.drawString(font, "choices use pages.", px + 8, y + 12, COLOR_TEXT, false);
        graphics.drawString(font, "Use <  > below.", px + 8, y + 68, COLOR_MUTED, false);
        graphics.drawString(font, "Take result from", px + 8, y + 86, COLOR_TEXT, false);
        graphics.drawString(font, "8-slot output bank.", px + 8, y + 98, COLOR_TEXT, false);
    }

    private static String shortIngredient(AssemblyTableRecipeBook.IngredientSpec spec) {
        String id = spec.id();
        String name;
        if ("@dye".equals(id)) name = "Any Dye";
        else if ("@planks".equals(id)) name = "Planks";
        else if ("@logs".equals(id)) name = "Logs";
        else {
            int colon = id.indexOf(':');
            name = colon >= 0 ? id.substring(colon + 1) : id;
            name = name.replace('_', ' ');
        }
        return spec.count() + "x " + name;
    }

    private static ItemStack sampleStack(AssemblyTableRecipeBook.IngredientSpec spec) {
        Item item;
        switch (spec.id()) {
            case "@dye" -> item = Items.WHITE_DYE;
            case "@planks" -> item = Items.OAK_PLANKS;
            case "@logs" -> item = Items.OAK_LOG;
            default -> item = BuiltInRegistries.ITEM.get(new ResourceLocation(spec.id()));
        }
        if (item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, spec.count());
    }

    private void renderRecipeOverlay(GuiGraphics graphics) {
        if (!showRecipeOverlay || sideTab != SideTab.RECIPES) return;
        AssemblyTableRecipeBook.Recipe recipe = selectedRecipe();
        if (recipe == null) return;

        for (int i = 0; i < 10; i++) {
            if (!menu.getSlot(i).getItem().isEmpty()) continue;
            AssemblyTableRecipeBook.IngredientSpec spec = recipe.ingredients()[i];
            if (spec == null) continue;

            ItemStack ghost = sampleStack(spec);
            if (ghost.isEmpty()) continue;

            int x = leftPos + INPUT[i][0];
            int y = topPos + INPUT[i][1];
            graphics.renderItem(ghost, x, y);
            graphics.renderItemDecorations(font, ghost, x, y, Integer.toString(spec.count()));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderRecipeOverlay(graphics);

        int hover = hoveredTab(mouseX, mouseY);
        if (hover >= 0) {
            graphics.renderTooltip(font, Component.literal(tabName(hover)), mouseX, mouseY);
        }

        renderTooltip(graphics, mouseX, mouseY);
    }
}
