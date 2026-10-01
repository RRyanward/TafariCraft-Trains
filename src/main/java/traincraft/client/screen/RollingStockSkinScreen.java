package traincraft.client.screen;

// TC_BATCH6_R2_LEGACY_SKIN_GUI

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import traincraft.Traincraft;
import traincraft.client.skin.ClientRollingStockSkinManager;
import traincraft.skin.SkinSyncNetwork;

import java.util.List;

/** 1.20.1 port of Traincraft's classic Paintbrush/Skin Changer selector. */
public final class RollingStockSkinScreen extends Screen {
    private static final ResourceLocation LEFT = new ResourceLocation(
            Traincraft.MOD_ID, "textures/gui/skin_changer_left.png");
    private static final ResourceLocation RIGHT = new ResourceLocation(
            Traincraft.MOD_ID, "textures/gui/skin_changer_right.png");

    private static final int PANEL_W = 412;
    private static final int PANEL_H = 200;
    private static final int PER_PAGE = 8;

    private final int entityId;
    private List<ClientRollingStockSkinManager.SkinOption> options = List.of();
    private int selectedIndex;
    private int page;

    public RollingStockSkinScreen(int entityId) {
        super(Component.literal("Train Colors Catalog"));
        this.entityId = entityId;
    }

    @Override
    protected void init() {
        Entity entity = getEntity();
        this.options = ClientRollingStockSkinManager.options(entity);
        String selectedKey = ClientRollingStockSkinManager.selectedKey(entity);

        this.selectedIndex = 0;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).key().equals(selectedKey)) {
                this.selectedIndex = i;
                break;
            }
        }
        this.page = selectedIndex / PER_PAGE;
        rebuildButtons();
    }

    private Entity getEntity() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? null : minecraft.level.getEntity(entityId);
    }

    private void rebuildButtons() {
        clearWidgets();
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;

        int start = page * PER_PAGE;
        int end = Math.min(options.size(), start + PER_PAGE);
        for (int i = start; i < end; i++) {
            final int index = i;
            int local = i - start;
            int column = local % 2;
            int row = local / 2;
            int x = left + 18 + column * 204;
            int y = top + 50 + row * 24;
            String label = options.get(i).displayName();
            if (i == selectedIndex) {
                label = "> " + label + " <";
            }
            addRenderableWidget(Button.builder(
                    Component.literal(label),
                    button -> {
                        selectedIndex = index;
                        rebuildButtons();
                    }).bounds(x, y, 172, 20).build());
        }

        int maxPage = Math.max(0, (options.size() - 1) / PER_PAGE);
        addRenderableWidget(Button.builder(
                Component.literal("< Prev"),
                button -> {
                    if (page > 0) {
                        page--;
                        rebuildButtons();
                    }
                }).bounds(left + 18, top + 168, 70, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Next >"),
                button -> {
                    if (page < maxPage) {
                        page++;
                        rebuildButtons();
                    }
                }).bounds(left + 94, top + 168, 70, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Apply"),
                button -> applySelection()).bounds(left + 248, top + 168, 70, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Close"),
                button -> onClose()).bounds(left + 324, top + 168, 70, 20).build());
    }

    private void applySelection() {
        if (selectedIndex >= 0 && selectedIndex < options.size()) {
            ClientRollingStockSkinManager.SkinOption option = options.get(selectedIndex);
            SkinSyncNetwork.sendSelectionToServer(entityId, option.key());
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        graphics.blit(LEFT, left, top, 0, 0, 206, 200);
        graphics.blit(RIGHT, left + 206, top, 0, 0, 206, 200);

        graphics.drawCenteredString(this.font, this.title, left + PANEL_W / 2, top + 10, 0xFFFFFF);
        Entity entity = getEntity();
        if (entity != null) {
            graphics.drawCenteredString(this.font, entity.getDisplayName(), left + PANEL_W / 2, top + 25, 0xD8D8D8);
        }

        int maxPage = Math.max(0, (options.size() - 1) / PER_PAGE);
        graphics.drawCenteredString(
                this.font,
                Component.literal("Page " + (page + 1) + "/" + (maxPage + 1)),
                left + PANEL_W / 2,
                top + 38,
                0xB0B0B0);

        if (!options.isEmpty() && selectedIndex >= 0 && selectedIndex < options.size()) {
            graphics.drawCenteredString(
                    this.font,
                    Component.literal("Selected: " + options.get(selectedIndex).displayName()),
                    left + PANEL_W / 2,
                    top + 149,
                    0xFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
