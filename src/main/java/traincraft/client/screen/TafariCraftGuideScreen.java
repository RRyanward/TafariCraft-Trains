package traincraft.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;
import traincraft.menu.GuideMenu;

/** DEV51b2: 23-chapter guide, no cover start page, indexed contents with PG# heading. */
public final class TafariCraftGuideScreen extends AbstractContainerScreen<GuideMenu> {
    private static final int CONTENTS_PAGES = 3;
    private static final int INDEX_ROWS = 8;
    private static final int BODY_Y = 50;
    private static final int ROW_H = 11;
    private static final int MAX_BODY_LINES = 8;
    private static final int TAN = 0xFFEFDFB6;
    private static final int NAVY = 0xFF103957;
    private static final int GOLD = 0xFFF5C854;
    private static final String[] TITLES = {
        "Welcome",
        "History and Legacy",
        "Getting Started",
        "Riding and Controls",
        "GUIs and HUD",
        "Steam Locomotives",
        "Boiler and Water",
        "Tenders",
        "Diesel Locomotives",
        "Electric Locomotives",
        "Coupling and Consists",
        "Passenger Cars",
        "Freight and Special Cars",
        "Placing Track by Hand",
        "Curves, Slopes, Switches",
        "Energy Track",
        "Track Blueprints & Special Rails",
        "Tracks Builder: How to Use",
        "Stations and Railway Villagers",
        "Workbench and Assembly",
        "Fuel and Materials",
        "Tips and Troubleshooting",
        "Credits and Licensing"
    };
    private static final String[] BODY = {
        "Welcome to TafariCraft - Trains, a modern continuation of the classic Traincraft railway experience. Build tracks, prepare locomotives, carry passengers and freight, and operate a living railway. This guide keeps the spirit of the old Traincraft book while explaining the systems restored for Minecraft 1.20.1. Start with a small engine and a short railway, then expand into a full network.",
        "Traincraft began as the Train and Zeppelin Mod and became a favorite for its detailed locomotives, rolling stock, tracks, and industrial machinery. TafariCraft - Trains builds on that foundation while modernizing operation, rendering, and railway construction. The classic content remains the work of its original creators and contributors. This is an unofficial modernization, not an official original Traincraft release.",
        "Collect the materials needed for rails and rolling stock. Use the Train Workbench and Assembly Tables to advance from components to completed trains. Lay a short, continuous line of track and place a locomotive on it. Add suitable fuel or electrical power, release its brakes, and use its locomotive interface. Learn on a level straight first before trying curves, hills, switches, and longer consists.",
        "Enter or interact with a locomotive to use its controls. While riding, R opens the locomotive interface, Space applies the service brake, and C toggles the handbrake. Use the configured throttle and direction controls to move; use the configured whistle or horn key when needed. For precise key assignments, check Minecraft Controls because custom key bindings can be changed. Stop before coupling, servicing, or adjusting a parked train.",
        "The locomotive interface shows operational information such as speed, brakes, available fuel, and engine state. Steam equipment also has boiler and water information; diesel and electric engines have their own power controls and readouts. Tenders and cargo cars provide inventories appropriate to their role. The HUD helps you watch operation while riding. An empty power supply, active brake, or overheated boiler can prevent normal movement.",
        "Steam locomotives burn fuel to raise boiler heat and drive their wheels. Add fuel and water before leaving the station. Different engines vary in capacity and pulling power, so a heavy train may need a larger locomotive and a suitable tender. Steam and smoke effects follow operation; larger locomotives can produce heavier smoke. Avoid full-power departures with low water, and watch the boiler gauges on long grades.",
        "The steam boiler has a live temperature system. Its temperature rises with firing and falls through cooling and available water. If it becomes too hot, an overheat warning appears and the locomotive may enter a failure state. The water reserve assists cooling, but it cannot replace sensible operation. Reduce heating, check fuel feed and water, and allow the engine to recover. The controls and gauge colors help warn before damage.",
        "A tender carries the supplies needed by suitable steam locomotives. Attach the correct tender behind the engine and stock its compartments. Steam engines and tenders travel together through curves when properly coupled, but they are still separate rail vehicles. Make sure the gap is correct, the coupler is engaged, and no brake is holding the tender. Tender capacity and appearance vary with locomotive design.",
        "Diesel locomotives run on Diesel Fuel rather than boiler water or Energy Track line power. Open the diesel-specific interface to inspect fuel and operating state. The modernized fleet includes several classic diesel designs with individual models and sounds. Engine smoke and exhaust depend on the locomotive. If the locomotive does not move, inspect its fuel, direction, throttle, brakes, and the rails under its wheels.",
        "Electric locomotives prefer live Energy Track. Their interface shows whether LINE POWER is ON or OFF. In the restored powered system, backup redstone material may be used in its designated left backup slot when the train is away from line power, and it is consumed during off-line movement. An electric engine is not a diesel; diesel fuel will not replace electrical supply. Check track continuity if line power drops.",
        "Connect locomotives, tenders, freight cars, passenger coaches, and cabooses into a consist. Use the coupling interaction to join the nearest compatible ends, then confirm that they are connected before moving. The port supports forward and reverse travel with model-aware spacing across curves and slopes. Use the correct brake controls before uncoupling. Sound feedback should accompany actual changes, not every interaction.",
        "Passenger coaches carry your railway passengers and complete the appearance of a proper train. Place them on a continuous track line and couple them to a suitable locomotive or tender. Check boarding or seating behavior for the coach you use. Passenger stock and cabooses may have distinct models and colors. Test a new consist at low speed before taking it around tight curves or onto a gradient.",
        "Freight wagons can carry goods or serve special roles. The restored roster includes ordinary freight, tankers, livestock and mob transport stock, and other specialist vehicles. Use the intended inventory or loading interface for each car. Some railway tracks can support cargo transfer. Account for extra weight when hauling full wagons: heavier trains need more traction and more room to stop. Verify couplings after adding new cars.",
        "TRACK LAYING - Start with Normal Track, then choose Medium, Long or Very Long Straight, curves, switches and special rails. Energy variants appear together below the Normal track families in the Creative tab. Right-click a suitable support block beside an open rail endpoint. For paired curve, slope and switch families, hold Shift WHILE positioning and placing to request the alternate Left turn; release Shift for the normal Right/default turn. The white ghost preview should match the placed orientation. Do not click air to change the selection. Allow enough room for the full footprint and make sure endpoints line up. Check routes at slow speed.",
        "CURVES AND SWITCHES - Use larger-radius curves for long locomotives. Normal and Energy Medium, Large, Very Large and Super Large curves share their track geometry. For supported paired curves (including 45-degree families), hold Shift to choose the alternate hand and release it for the default. Large 90-degree curves select the opposite valid orientation at a real open endpoint; do not force an invalid or obstructed footprint. The placement preview follows Shift. A switch must also have the correct route selected before a train enters it. Test a small layout before building a full route.",
        "Energy Track supplies electrical line power to electric locomotives. The modern lightweight Energy Track is the supported version. A redstone source can energize a connected section, with a documented reach of up to 20 connected Energy Track blocks in the restored system. The electric GUI indicates whether line power is available. Look for broken connectivity or an unpowered source when the display reads OFF.",
        "SPECIAL TRACKS AND BLUEPRINTS - Horn Track automatically sounds a locomotive horn when activated; cargo transfer track interacts with supported wagons. The Track Blueprint is the planning tool for a builder. Hold it and scroll to change the selected track; Shift+scroll switches the rail family. In editing mode, right-click a block to append a track segment; sneak+right-click a block to remove the last planned step. Right-click in the air cycles left/right turn orientation; sneak+right-click in the air finishes or re-enters a route, according to its editing state. Inspect the ghost preview, footprint clearance and exact bill of materials. Set the Build Start anchor before starting the Builder. If a key conflicts with a different mod, confirm the preview before accepting the placement.",
        "TRACKS BUILDER - Place the Builder on a continuous railway facing the intended build direction. NORMAL right-click the Builder to open its GUI. Fill the fuel slot with coal/accepted solid fuel and load the track, roadbed or ballast and support blocks shown by its material list. The large Blueprint holder accepts a completed route plan. The Supplies and Blueprint Materials inventories have different jobs; do not put your route in the fuel slot. Check the displayed Mode (BLUEPRINT or CLASSIC), Build Start, preview and blocked/supported warnings before moving. SHIFT+right-click the Builder to START its autonomous crawl; Shift+right-click again to STOP. The current Builder crawls at about 10 km/h while constructing if powered and supplied. If it stops, check fuel, remaining required items, nearby obstruction, and unfinished blueprint sections. In CLASSIC/manual mode, select the roadbed/track settings and confirm each placement. When building tunnels, allow the CUT/COLLECT stage, then the roadbed, wall and roof passes before the normal track is placed. Always test a short section first and stop construction before changing the blueprint.",
        "Railway villages and stations bring life to the network. Specialized railway villagers include Station Chief and Train Conductor roles, with trading interfaces at their workstations. Keep their job sites and trading stations intact so the correct profession and offers can load. Stations provide natural hubs for passenger service, deliveries, and future network expansion. Villager spawning and job assignment depend on the world and workstation setup.",
        "The Train Workbench and tiered Assembly Tables are core crafting stations. Recipes progress from materials and train components to complete rolling stock. Other machines such as the Open Hearth and Distillation Tower support materials and fuel production. Consult JEI for current ingredients and recipes in this specific build; inherited 1.7.10 recipes may have changed. The guide explains the process, while JEI shows the exact current recipe.",
        "The Open Hearth produces steel from iron and graphite with fuel in the restored industrial progression. The Distillation Tower processes supported feedstocks, including oil-related materials and some plant resources, into fuel products. Steam engines need boiler fuel and water; diesels need Diesel Fuel; electrics need live track power or approved backup material. Not all supplies work in every engine. Use each machine GUI and JEI to verify current inputs.",
        "If a train will not move, check its rail alignment, direction, throttle, brakes, power, and couplers. For steam, inspect water and boiler temperature; for diesel, check fuel; for electric, verify line power and backup supply. If a consist separates or flips, test fewer cars on a straight before trying curves. Headlights use classic visible beams; experimental terrain and entity illumination was removed from the release. Use the latest build log when reporting bugs.",
        "Original Traincraft: Spitfire4466 and Mrbrutal / Mr. Brutal. Original models and artwork: DAYdiecast, Ontax, and Skspajdalfsks. Turning assistance: Naiten. Later Traincraft contributors include EternalBlueFlame, NitroxydeX, FirEmerald, Hagrud, and canitzp. TafariCraft - Trains modernization and maintenance: RRyanward. This is an unofficial derivative. Original copyrights and licenses remain with their holders; consult LICENSE.md, NOTICE.md, and CREDITS.md in the project."
    };
    private record GuidePage(int chapterIndex, List<FormattedCharSequence> lines) { }
    private final List<GuidePage> pages = new ArrayList<>();
    private final int[] chapterFirstPages = new int[TITLES.length];
    private int page = 0;

    public TafariCraftGuideScreen(GuideMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 256;
        imageHeight = 190;
    }

    @Override protected void init() {
        super.init();
        if (pages.isEmpty()) buildPages();
        addRenderableWidget(Button.builder(Component.literal("<"), b -> previous())
                .bounds(leftPos + 11, topPos + 159, 25, 18).build());
        addRenderableWidget(Button.builder(Component.literal("- Chapter"), b -> chapterStep(-1))
                .bounds(leftPos + 39, topPos + 159, 61, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Contents"), b -> page = 0)
                .bounds(leftPos + 103, topPos + 159, 56, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Chapter +"), b -> chapterStep(1))
                .bounds(leftPos + 162, topPos + 159, 61, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> next())
                .bounds(leftPos + 226, topPos + 159, 25, 18).build());
    }

    private void buildPages() {
        // Contents begins at physical page 1. No separate cover/start page.
        for (int i = 0; i < CONTENTS_PAGES; i++) {
            pages.add(new GuidePage(-1, List.of()));
        }
        Font currentFont = this.font;
        for (int chapter = 0; chapter < TITLES.length; chapter++) {
            chapterFirstPages[chapter] = pages.size();
            List<FormattedCharSequence> wrapped =
                    currentFont.split(Component.literal(BODY[chapter]), imageWidth - 35);
            if (wrapped.isEmpty()) {
                pages.add(new GuidePage(chapter, List.of()));
            } else {
                for (int start = 0; start < wrapped.size(); start += MAX_BODY_LINES) {
                    int end = Math.min(start + MAX_BODY_LINES, wrapped.size());
                    pages.add(new GuidePage(chapter, List.copyOf(wrapped.subList(start, end))));
                }
            }
        }
    }

    private void previous() {page = Math.max(0, page - 1);}
    private void next() {page = Math.min(pages.size() - 1, page + 1);}
    private void chapterStep(int offset) {
        if (page < CONTENTS_PAGES) {
            page = chapterFirstPages[offset >= 0 ? 0 : TITLES.length - 1];
            return;
        }
        int chapter = pages.get(page).chapterIndex();
        int nextChapter = Math.max(0, Math.min(TITLES.length - 1, chapter + offset));
        page = chapterFirstPages[nextChapter];
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && page >= 0 && page < CONTENTS_PAGES &&
                mouseX >= leftPos + 16 && mouseX <= leftPos + imageWidth - 16 &&
                mouseY >= topPos + BODY_Y && mouseY < topPos + BODY_Y + INDEX_ROWS * ROW_H) {
            int row = (int) ((mouseY - topPos - BODY_Y) / ROW_H);
            int chapter = page * INDEX_ROWS + row;
            if (chapter >= 0 && chapter < TITLES.length) {
                page = chapterFirstPages[chapter];
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT) {previous();return true;}
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {next();return true;}
        if (keyCode == GLFW.GLFW_KEY_HOME) {page = 0;return true;}
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void drawText(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF071B2B);
        g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFFBE963E);
        g.fill(leftPos + 7, topPos + 7, leftPos + imageWidth - 7, topPos + imageHeight - 7, TAN);
        g.fill(leftPos + 10, topPos + 9, leftPos + imageWidth - 10, topPos + 25, NAVY);
        g.fill(leftPos + 10, topPos + 44, leftPos + imageWidth - 10, topPos + 46, 0xFFAC7D32);
        g.fill(leftPos + 10, topPos + 145, leftPos + imageWidth - 10, topPos + 146, 0xFFAC7D32);
        g.fill(leftPos + 10, topPos + 154, leftPos + imageWidth - 10, topPos + 155, 0xFFAC7D32);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        drawText(g, "TAFARICRAFT", 17, 13, GOLD);
        drawText(g, "TRAINS GUIDE", 141, 13, 0xFFF2E5BE);
        if (page < CONTENTS_PAGES) {
            drawText(g, "CONTENTS  " + (page + 1) + " / " + CONTENTS_PAGES, 17, 31, 0xFF143C5C);
            // PG# directly labels the right-aligned page-number column.
            drawText(g, "PG#", 240 - font.width("PG#"), 31, NAVY);
            int start = page * INDEX_ROWS;
            for (int i = 0; i < INDEX_ROWS && start + i < TITLES.length; i++) {
                int idx = start + i;
                int y = BODY_Y + i * ROW_H;
                drawText(g, String.format(java.util.Locale.ROOT, "%02d", idx + 1), 17, y, 0xFF8E4B22);
                String label = TITLES[idx];
                // Keep page numbers visible even if a chapter name is long.
                if (font.width(label) > 170) {
                    while (!label.isEmpty() && font.width(label + "...") > 170) {
                        label = label.substring(0, label.length() - 1);
                    }
                    label += "...";
                }
                drawText(g, label, 35, y, 0xFF1A344C);
                String number = Integer.toString(chapterFirstPages[idx] + 1);
                drawText(g, number, 240 - font.width(number), y, 0xFF90501E);
            }
        } else {
            GuidePage current = pages.get(page);
            int chapter = current.chapterIndex();
            String label = (chapter + 1) + ". " + TITLES[chapter];
            // A single, sharp line prevents the overlapping double-heading seen in DEV51a.
            if (font.width(label) > 221) {
                while (!label.isEmpty() && font.width(label + "...") > 221) {
                    label = label.substring(0, label.length() - 1);
                }
                label += "...";
            }
            drawText(g, label, 17, 31, 0xFF103D60);
            int y = BODY_Y;
            for (FormattedCharSequence line : current.lines()) {
                g.drawString(font, line, 17, y, 0xFF252B27, false);
                y += ROW_H;
            }
        }
        drawText(g, "Page " + (page + 1) + " / " + pages.size(), 17, 146, 0xFF18456B);
        drawText(g, "Click chapter to jump", 132, 146, 0xFF915A29);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
