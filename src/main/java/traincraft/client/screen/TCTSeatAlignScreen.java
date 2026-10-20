package traincraft.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.entity.train.power.LegacyPoweredLocomotive;

/** DEV53E3H: scaled-window seat D-pad, real train picker, and one-at-a-time fleet checkout. */
public final class TCTSeatAlignScreen extends Screen {
    private static final double NEAR_RADIUS = 48.0D;
    private static final double LOOK_RADIUS = 16.0D;
    private final List<String> roster = new ArrayList<>();
    private UUID selectedVehicle;
    private int selectedEntityId = -1;
    private String selectedName = "No vehicle";
    private String status = "Point at a train and press Select";
    private int seat = 1;
    private int stepIndex = 1;
    private final double[] steps = {0.01D, 0.05D, 0.25D};
    private boolean rosterMode;
    private int rosterIndex;
    private boolean confirmReset;
    private boolean initializedPick;
    private boolean waitingRollout;
    private int rolloutWaitTicks;
    private String rolloutRequested;
    private UUID rideBeforeRollout;
    private UUID requestedRide;
    private int rideWaitTicks;
    private boolean fleetJustOpened;

    private int panelX, panelY, panelW, panelH, bh, gap;

    public TCTSeatAlignScreen() {
        super(Component.literal("TafariCraft Seat Align"));
    }

    private static boolean isStock(Entity e) {
        if (!(e instanceof AbstractMinecart)) return false;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return id != null && Traincraft.MOD_ID.equals(id.getNamespace())
                && !"tracks_builder".equals(id.getPath());
    }

    private List<Entity> nearby() {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) return List.of();
        List<Entity> entities = new ArrayList<>(minecraft.level.getEntities(
                minecraft.player, minecraft.player.getBoundingBox().inflate(NEAR_RADIUS),
                TCTSeatAlignScreen::isStock));
        entities.sort(Comparator.comparing(TCTSeatAlignScreen::entityName).thenComparingInt(Entity::getId));
        return entities;
    }

    private static String entityName(Entity e) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return id == null ? "unknown" : id.getPath();
    }

    private Entity selected() {
        if (minecraft == null || minecraft.level == null || selectedEntityId < 0) return null;
        Entity e = minecraft.level.getEntity(selectedEntityId);
        return e != null && isStock(e) && e.getUUID().equals(selectedVehicle) ? e : null;
    }

    private Entity underCrosshairOrInFront() {
        if (minecraft == null || minecraft.player == null) return null;
        Entity ride = minecraft.player.getVehicle();
        if (ride != null && isStock(ride)) return ride;
        if (minecraft.hitResult instanceof EntityHitResult hit && isStock(hit.getEntity())
                && minecraft.player.distanceToSqr(hit.getEntity()) <= LOOK_RADIUS * LOOK_RADIUS) {
            return hit.getEntity();
        }
        Vec3 eyes = minecraft.player.getEyePosition();
        Vec3 look = minecraft.player.getLookAngle();
        Entity best = null;
        double score = Double.POSITIVE_INFINITY;
        for (Entity e : nearby()) {
            Vec3 delta = e.getBoundingBox().getCenter().subtract(eyes);
            double dist = delta.length();
            if (dist < 0.01 || dist > LOOK_RADIUS) continue;
            double facing = delta.normalize().dot(look);
            if (facing < 0.35D) continue;
            double value = dist * (1.0D + (1.0D - facing) * 2.5D);
            if (value < score) { score = value; best = e; }
        }
        return best;
    }

    private void send(String command) {
        if (minecraft != null && minecraft.player != null && minecraft.player.connection != null) {
            minecraft.player.connection.sendCommand(command);
        }
    }

    private void selectEntity(Entity e) {
        if (e == null || !isStock(e)) {
            status = "No train ahead within 16 blocks";
            return;
        }
        selectedVehicle = e.getUUID();
        selectedEntityId = e.getId();
        selectedName = entityName(e);
        confirmReset = false;
        status = "Selected " + selectedName;
        send("tct seat target " + selectedEntityId);
        send("tct seat preview " + seat);
    }

    private void pickFacing() {
        selectEntity(underCrosshairOrInFront());
    }

    private void cycleNearby(int direction) {
        if (requestedRide != null || waitingRollout) {
            status = "Wait for the previous vehicle switch";
            return;
        }
        List<Entity> candidates = nearby();
        if (candidates.isEmpty()) { status = "No placed trains within 48 blocks"; return; }
        int old = -1;
        Entity ridden = minecraft != null && minecraft.player != null
                ? minecraft.player.getVehicle() : null;
        // Navigation follows the car you are actually in, not a stale highlight.
        UUID anchor = ridden != null && isStock(ridden) ? ridden.getUUID() : selectedVehicle;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).getUUID().equals(anchor)) {old=i; break;}
        }
        int index = old < 0 ? (direction > 0 ? 0 : candidates.size()-1)
                : Math.floorMod(old + direction, candidates.size());
        Entity next = candidates.get(index);
        if (ridden != null && next.getUUID().equals(ridden.getUUID())) {
            selectEntity(next);
            status = "This is the only nearby train";
            return;
        }
        // Server validates Creative, stock identity, loaded entity and range.
        requestedRide = next.getUUID();
        rideWaitTicks = 0;
        send("tct seat ride " + next.getId());
        status = "Boarding " + entityName(next) + "...";
    }

    private static boolean stockId(String id) {
        return id.startsWith("locomotive_") || id.startsWith("steam_tender")
                || id.endsWith("_car") || id.endsWith("_cart") || id.contains("caboose")
                || id.contains("coach") || id.startsWith("gs4_") || id.startsWith("milw_");
    }

    private void fillRoster() {
        roster.clear();
        ForgeRegistries.ENTITY_TYPES.getKeys().stream()
                .filter(k -> Traincraft.MOD_ID.equals(k.getNamespace()))
                .map(ResourceLocation::getPath)
                .filter(TCTSeatAlignScreen::stockId)
                .filter(k -> !"tracks_builder".equals(k))
                .sorted().forEach(roster::add);
    }

    private void cycle(int direction) {
        if (rosterMode) {
            if (roster.isEmpty()) { status = "No rolling-stock variants found"; return; }
            if (waitingRollout || requestedRide != null) {
                status = "Wait for the previous vehicle switch";
                return;
            }
            if (fleetJustOpened) {
                fleetJustOpened = false;
                rosterIndex = direction > 0 ? 0 : roster.size() - 1;
            } else {
                rosterIndex = Math.floorMod(rosterIndex + direction, roster.size());
            }
            rolloutAndRide();
        } else {
            cycleNearby(direction);
        }
    }

    private void switchMode() {
        rosterMode = !rosterMode;
        fleetJustOpened = rosterMode;
        status = rosterMode ? "Fleet: Next boards the first test train" : "Placed: Prev / Next changes your ride";
        clearWidgets();
        buildButtons();
    }

    private void seat(int s) {
        seat = s;
        confirmReset = false;
        send("tct seat preview " + s);
        status = "Seat " + seat + " active";
        clearWidgets();
        buildButtons();
    }

    private boolean supported() {
        Entity e = selected();
        return !(e instanceof LegacyPoweredLocomotive powered) || powered.isWoodenPassengerTram() || seat == 1;
    }

    private void nudge(String direction) {
        if (selected() == null) { status = "Press Select Vehicle first"; return; }
        if (!supported()) { status = "This locomotive only has driver Seat 1"; return; }
        send("tct seat nudge " + seat + " " + direction + " " + String.format(Locale.ROOT,"%.2f",steps[stepIndex]));
        status = "Seat " + seat + " " + direction + " by " + steps[stepIndex];
        confirmReset = false;
    }

    private void save() {
        if (selected() == null) { status = "Select a train first"; return; }
        send("tct seat save " + seat);
        status = "Saved Seat " + seat + " (check chat)";
    }

    private void reset() {
        if (selected() == null) { status = "Select a train first"; return; }
        if (!confirmReset) {
            confirmReset = true;
            status = "Press Reset again to clear ONLY Seat " + seat;
        } else {
            send("tct seat reset " + seat);
            confirmReset = false;
            status = "Reset Seat " + seat + " (check chat)";
        }
        clearWidgets();
        buildButtons();
    }

    /** Explicit fleet tester: one Creative test train on existing straight rail;
     * server changes the actual rider only after the new entity is spawned. */
    private void rolloutAndRide() {
        if (!rosterMode || roster.isEmpty()) return;
        if (waitingRollout || requestedRide != null) {
            status = "Wait for the previous vehicle switch";
            return;
        }
        fleetJustOpened = false;
        rolloutRequested = roster.get(rosterIndex);
        rideBeforeRollout = minecraft != null && minecraft.player != null
                && minecraft.player.getVehicle() != null
                ? minecraft.player.getVehicle().getUUID() : null;
        // DEV53E3H3B: dedicated server command; avoids client /tct seat parser conflict.
        send("tctseatrolloutride " + rolloutRequested);
        waitingRollout = true;
        rolloutWaitTicks = 0;
        status = "Rollout and board " + (rosterIndex + 1) + "/" + roster.size();
    }

    @Override
    protected void init() {
        if (roster.isEmpty()) fillRoster();
        if (!initializedPick) {
            initializedPick = true;
            Entity initial = underCrosshairOrInFront();
            if (initial != null) selectEntity(initial);
        }
        buildButtons();
    }

    private Button button(String text, int x, int y, int w, int h, Runnable action) {
        Button b = Button.builder(Component.literal(text), btn -> action.run())
                .bounds(x, y, Math.max(16,w), Math.max(12,h)).build();
        addRenderableWidget(b);
        return b;
    }

    private void buildButtons() {
        // Minecraft uses GUI-scaled width and height; recalculated on every resize.
        panelW = Math.min(232, Math.max(74, this.width - 12));
        panelH = Math.min(264, Math.max(96, this.height - 12));
        panelX = Math.max(6, this.width - panelW - 8);
        panelY = Math.max(6, this.height - panelH - 8);
        bh = Math.max(12, Math.min(20, (panelH - 62) / 9));
        gap = panelH > 204 ? 3 : 1;
        int contentX = panelX + 7;
        int contentW = panelW - 14;
        int top = panelY + 35;
        int half = (contentW - gap) / 2;
        int third = (contentW - gap * 2) / 3;

        button("Select Vehicle", contentX, top, half, bh, this::pickFacing);
        button(rosterMode ? "Placed" : "Fleet List", contentX + half + gap, top, contentW - half - gap, bh, this::switchMode);
        top += bh + gap;
        button("< Prev & Ride", contentX, top, half, bh, () -> cycle(-1));
        button("Next & Ride >", contentX + half + gap, top, contentW - half - gap, bh, () -> cycle(+1));
        top += bh + gap;
        button("Seat 1", contentX, top, third, bh, () -> seat(1));
        button("Seat 2", contentX + third + gap, top, third, bh, () -> seat(2));
        button("Seat 3", contentX + 2 * (third + gap), top,
                contentW - 2 * (third + gap), bh, () -> seat(3));
        top += bh + gap;
        button("Front", contentX, top, half, bh, () -> nudge("front"));
        button("Up", contentX + half + gap, top, contentW - half - gap, bh, () -> nudge("up"));
        top += bh + gap;
        button("Left", contentX, top, third, bh, () -> nudge("left"));
        button("Down", contentX + third + gap, top, third, bh, () -> nudge("down"));
        button("Right", contentX + 2 * (third + gap), top,
                contentW - 2 * (third + gap), bh, () -> nudge("right"));
        top += bh + gap;
        button("Back", contentX, top, half, bh, () -> nudge("back"));
        button("Step " + steps[stepIndex], contentX + half + gap, top,
                contentW - half - gap, bh, () -> {
                    stepIndex = (stepIndex + 1) % steps.length;
                    clearWidgets(); buildButtons();
                });
        top += bh + gap;
        button(confirmReset ? "Confirm Reset" : "Reset Seat", contentX, top, half, bh, this::reset);
        button("Save Seat", contentX + half + gap, top, contentW - half - gap, bh, this::save);
        top += bh + gap;
        Button ro = button("Rollout & Ride", contentX, top, half, bh, this::rolloutAndRide);
        ro.active = rosterMode;
        button("Close", contentX + half + gap, top, contentW - half - gap, bh, this::onClose);
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft == null || minecraft.player == null) return;
        Entity actualRide = minecraft.player.getVehicle();
        // The server changes the passenger and sends the normal Minecraft mount
        // packet. Do not select the next car until the client actually rides it.
        if (requestedRide != null) {
            if (actualRide != null && requestedRide.equals(actualRide.getUUID())) {
                requestedRide = null;
                rideWaitTicks = 0;
                seat = 1;
                selectEntity(actualRide);
                status = "Now riding " + entityName(actualRide) + "; Save only if adjusted";
                clearWidgets(); buildButtons();
            } else if (++rideWaitTicks > 100) {
                requestedRide = null;
                status = "Boarding failed; check chat. Previous ride was preserved.";
            }
        }
        if (waitingRollout) {
            if (actualRide != null && isStock(actualRide)
                    && (rideBeforeRollout == null || !rideBeforeRollout.equals(actualRide.getUUID()))
                    && rolloutRequested != null
                    && rolloutRequested.equals(entityName(actualRide))) {
                waitingRollout = false;
                rolloutWaitTicks = 0;
                seat = 1;
                selectEntity(actualRide);
                status = "Riding fleet " + (rosterIndex + 1) + "/" + roster.size()
                        + "; adjust or Next to skip";
                clearWidgets(); buildButtons();
            } else if (++rolloutWaitTicks > 100) {
                waitingRollout = false;
                status = "Rollout/ride failed; check chat. Previous train preserved.";
            }
        }
    }

    private String ellipsize(String s, int maxPixels) {
        if (font.width(s) <= maxPixels) return s;
        while (s.length() > 3 && font.width(s + "...") > maxPixels) s = s.substring(0,s.length()-1);
        return s + "...";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xDA171C24);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFF6CB3E9);
        graphics.drawString(font, "TAFARICRAFT  |  SEAT ALIGN", panelX + 7, panelY + 7, 0xFFE9EEF5, false);
        String subject = rosterMode && !roster.isEmpty()
                ? "Fleet " + (rosterIndex + 1) + "/" + roster.size() + ": " + roster.get(rosterIndex)
                : "Target: " + (selected() != null ? selectedName : "None") + "  | Seat " + seat;
        graphics.drawString(font, ellipsize(subject, panelW - 14),
                panelX + 7, panelY + 20, 0xFFD5DCE6, false);
        graphics.drawString(font, ellipsize(status, panelW - 14), panelX + 7,
                panelY + panelH - 13, 0xFFFFD88B, false);
        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public void onClose() {
        send("tct seat auto");
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
