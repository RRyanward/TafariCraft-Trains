package traincraft.client.event;

// TC_BATCH8_R4A4D_RESPONSIVE_BUILDER_OVERLAY

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.client.screen.PoweredLocomotiveScreen;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.item.TrackBlueprintItem;

@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TracksBuilderBlueprintOverlay {
    private static final int BUILDER_WIDTH = 255;

    @SubscribeEvent
    public static void onRender(
            ScreenEvent.Render.Post event) {
        if (!(event.getScreen()
                instanceof PoweredLocomotiveScreen screen)) {
            return;
        }

        LegacyPoweredLocomotive locomotive =
                screen.getMenu().getLocomotive();

        if (locomotive == null
                || !locomotive.isBuilder()) {
            return;
        }

        GuiGraphics graphics =
                event.getGuiGraphics();
        Font font =
                Minecraft.getInstance().font;

        ItemStack blueprint =
                locomotive.getPowerInventory()
                        .getStackInSlot(52);

        boolean blueprintMode =
                blueprint.getItem()
                                instanceof TrackBlueprintItem
                        && TrackBlueprintItem
                                .isLocked(blueprint)
                        && TrackBlueprintItem
                                .segmentCount(blueprint) > 0;

        int progress =
                Math.max(
                        0,
                        locomotive.getBuilderBlueprintProgress());

        int total =
                blueprintMode
                        ? TrackBlueprintItem
                                .segmentCount(blueprint)
                        : 0;

        renderSidePanel(
                graphics,
                font,
                screen,
                locomotive,
                blueprint,
                blueprintMode,
                progress,
                total);

        if (blueprintMode
                && progress < total) {
            renderRackRequirements(
                    graphics,
                    font,
                    screen,
                    locomotive,
                    blueprint,
                    progress,
                    total);
        }
    }

    private static void renderSidePanel(
            GuiGraphics graphics,
            Font font,
            PoweredLocomotiveScreen screen,
            LegacyPoweredLocomotive locomotive,
            ItemStack blueprint,
            boolean blueprintMode,
            int progress,
            int total) {
        // TC_BATCH8_R4A4D_RESPONSIVE_BUILDER_OVERLAY
        //
        // Keep the accepted 126x174 natural layout at full size on roomy
        // windows. On smaller windows, uniformly scale the whole panel to the
        // actual right-side width/available screen height.
        int panelWidth = 126;
        int panelHeight = 174;
        int gap = 6;
        int margin = 4;

        int guiRight =
                screen.getGuiLeft()
                        + BUILDER_WIDTH;

        int availableRight =
                Math.max(
                        1,
                        screen.width
                                - (guiRight + gap)
                                - margin);

        int availableHeight =
                Math.max(
                        1,
                        screen.height
                                - margin * 2);

        float panelScale =
                Math.min(
                        1.0F,
                        Math.min(
                                availableRight
                                        / (float)panelWidth,
                                availableHeight
                                        / (float)panelHeight));

        // Small-window screenshots are still readable around 0.60 scale.
        // A 0.50 floor avoids making text unusably tiny on extreme windows.
        panelScale =
                Math.max(
                        0.50F,
                        panelScale);

        int scaledWidth =
                Math.max(
                        1,
                        Math.round(
                                panelWidth
                                        * panelScale));

        int scaledHeight =
                Math.max(
                        1,
                        Math.round(
                                panelHeight
                                        * panelScale));

        int preferredRight =
                guiRight
                        + gap;

        int rightLimit =
                screen.width
                        - margin
                        - scaledWidth;

        int x =
                Math.max(
                        margin,
                        Math.min(
                                preferredRight,
                                rightLimit));

        int y =
                Math.max(
                        margin,
                        Math.min(
                                screen.getGuiTop(),
                                screen.height
                                        - scaledHeight
                                        - margin));

        graphics.pose().pushPose();
        graphics.pose().translate(
                x,
                y,
                500.0F);
        graphics.pose().scale(
                panelScale,
                panelScale,
                1.0F);
        graphics.pose().translate(
                -x,
                -y,
                0.0F);

        graphics.fill(
                x,
                y,
                x + panelWidth,
                y + panelHeight,
                0xE0181B1F);

        graphics.fill(
                x + 1,
                y + 1,
                x + panelWidth - 1,
                y + 22,
                0xE02B333B);

        graphics.fill(
                x,
                y + 22,
                x + panelWidth,
                y + 24,
                0xFF3CBCD6);

        graphics.fill(x, y, x + panelWidth, y + 1, 0xFF68747E);
        graphics.fill(x, y, x + 1, y + panelHeight, 0xFF68747E);
        graphics.fill(
                x,
                y + panelHeight - 1,
                x + panelWidth,
                y + panelHeight,
                0xFF08090A);
        graphics.fill(
                x + panelWidth - 1,
                y,
                x + panelWidth,
                y + panelHeight,
                0xFF08090A);

        graphics.drawString(
                font,
                "BUILDER / BLUEPRINT",
                x + 7,
                y + 7,
                0xFFFFFFFF,
                false);

        String next = "-";
        String direction = "-";

        if (blueprintMode
                && progress < total) {
            CompoundTag segment =
                    TrackBlueprintItem.segment(
                            blueprint,
                            progress);

            ItemStack nextStack =
                    TrackBlueprintItem
                            .segmentTrackStack(
                                    segment);

            next =
                    nextStack.isEmpty()
                            ? TrackBlueprintItem
                                    .segmentCanonicalItemId(
                                            segment)
                            : nextStack
                                    .getHoverName()
                                    .getString();

            String itemId =
                    normalizeCanonical(
                            TrackBlueprintItem
                                    .segmentCanonicalItemId(
                                            segment));

            if (TrackBlueprintItem
                    .isUnifiedPairId(itemId)) {
                direction =
                        TrackBlueprintItem
                                .segmentIsLeft(segment)
                                ? "LEFT"
                                : "RIGHT";
            } else {
                direction = "N/A";
            }
        }

        String buildStart =
                blueprintMode
                        ? (TrackBlueprintItem
                                        .hasPreviewAnchor(
                                                blueprint)
                                ? "SET"
                                : "AUTO")
                        : "-";

        String status =
                locomotive
                        .getBuilderBlueprintNeed();

        if (status == null
                || status.isEmpty()) {
            status =
                    blueprintMode
                            ? (progress >= total
                                    ? "COMPLETE"
                                    : "READY")
                            : "CLASSIC MODE";
        }

        LinkedHashMap<String, Need> needs =
                blueprintMode
                        ? remainingNeeds(
                                blueprint,
                                progress,
                                total)
                        : new LinkedHashMap<>();

        int missingTypes = 0;
        for (Map.Entry<String, Need> entry :
                needs.entrySet()) {
            if (availableCount(
                            locomotive,
                            entry.getKey())
                    < entry.getValue().count()) {
                missingTypes++;
            }
        }

        int tx = x + 8;
        int ty = y + 30;
        int step = 12;

        graphics.drawString(
                font,
                "Mode: "
                        + (blueprintMode
                                ? "BLUEPRINT"
                                : "CLASSIC"),
                tx,
                ty,
                blueprintMode
                        ? 0xFF65E6FF
                        : 0xFFD0D0D0,
                false);

        graphics.drawString(
                font,
                "Plan: "
                        + (blueprintMode
                                ? Math.min(progress, total)
                                        + "/"
                                        + total
                                : "-"),
                tx,
                ty + step,
                0xFF67B9FF,
                false);

        graphics.drawString(
                font,
                "Track: " + trim(next, 14),
                tx,
                ty + step * 2,
                0xFF65E6FF,
                false);

        graphics.drawString(
                font,
                "Dir: " + direction,
                tx,
                ty + step * 3,
                0xFFFFFFFF,
                false);

        graphics.drawString(
                font,
                "Start: " + buildStart,
                tx,
                ty + step * 4,
                0xFF8DFF8D,
                false);

        boolean badStatus =
                status.startsWith("NEED")
                        || status.contains("FAILED")
                        || status.contains("BLOCKED");

        graphics.drawString(
                font,
                "Status: " + trim(status, 12),
                tx,
                ty + step * 5,
                badStatus
                        ? 0xFFFF8A8A
                        : 0xFFA8D7FF,
                false);

        graphics.drawString(
                font,
                "Materials: " + needs.size(),
                tx,
                ty + step * 6,
                0xFFFFCC72,
                false);

        graphics.drawString(
                font,
                "Missing: " + missingTypes,
                tx,
                ty + step * 7,
                missingTypes > 0
                        ? 0xFFFF7777
                        : 0xFF61E779,
                false);

        graphics.drawString(
                font,
                "Overlap: ACTIVE",
                tx,
                ty + step * 8,
                0xFFFF9A8F,
                false);

        int dividerY =
                ty + step * 9 + 1;

        graphics.fill(
                tx,
                dividerY,
                x + panelWidth - 8,
                dividerY + 1,
                0xAA737373);

        graphics.drawString(
                font,
                "GREEN = full",
                tx,
                dividerY + 7,
                0xFF8DFF9D,
                false);

        graphics.drawString(
                font,
                "RED = short",
                tx,
                dividerY + 18,
                0xFFFF9A9A,
                false);

        graphics.pose().popPose();
    }

    // r4a3h keeps requirement ghosts/counts on the existing 20..51 rack.
    private static void renderRackRequirements(
            GuiGraphics graphics,
            Font font,
            PoweredLocomotiveScreen screen,
            LegacyPoweredLocomotive locomotive,
            ItemStack blueprint,
            int progress,
            int total) {
        LinkedHashMap<String, Need> needs =
                remainingNeeds(
                        blueprint,
                        progress,
                        total);

        if (needs.isEmpty()) {
            return;
        }

        Set<Integer> assigned =
                new HashSet<>();

        int left =
                screen.getGuiLeft();
        int top =
                screen.getGuiTop();

        for (Map.Entry<String, Need> entry :
                needs.entrySet()) {
            String id =
                    entry.getKey();
            Need need =
                    entry.getValue();

            int rackSlot =
                    matchingRackSlot(
                            locomotive,
                            id,
                            assigned);

            if (rackSlot < 0) {
                rackSlot =
                        emptyRackSlot(
                                locomotive,
                                assigned);
            }

            if (rackSlot < 0) {
                break;
            }

            assigned.add(rackSlot);

            int relative =
                    rackSlot - 20;
            int row =
                    relative / 8;
            int col =
                    relative % 8;

            int slotX =
                    left
                            + 106
                            + col * 18;
            int slotY =
                    top
                            + 50
                            + row * 18;

            ItemStack actual =
                    locomotive
                            .getPowerInventory()
                            .getStackInSlot(
                                    rackSlot);

            int have =
                    availableCount(
                            locomotive,
                            id);

            boolean ready =
                    have >= need.count();

            drawBorder(
                    graphics,
                    slotX,
                    slotY,
                    ready
                            ? 0xFF2EFF52
                            : 0xFFFF5555);

            if (actual.isEmpty()
                    && !need.stack().isEmpty()) {
                ItemStack ghost =
                        need.stack().copy();
                ghost.setCount(1);

                graphics.renderItem(
                        ghost,
                        slotX,
                        slotY);

                // Requirement-only ghost: clearly lower opacity than a real item.
                graphics.fill(
                        slotX,
                        slotY,
                        slotX + 16,
                        slotY + 16,
                        0x90FFFFFF);

                graphics.fill(
                        slotX,
                        slotY,
                        slotX + 16,
                        slotY + 16,
                        0x70000000);
            }

            drawCount(
                    graphics,
                    font,
                    have,
                    need.count(),
                    slotX,
                    slotY,
                    ready
                            ? 0xFF8DFF9D
                            : 0xFFFFC0C0);
        }
    }

    private static LinkedHashMap<String, Need> remainingNeeds(
            ItemStack blueprint,
            int progress,
            int total) {
        LinkedHashMap<String, Need> result =
                new LinkedHashMap<>();

        for (int index =
                        Math.max(
                                0,
                                progress);
             index < total;
             index++) {
            CompoundTag segment =
                    TrackBlueprintItem.segment(
                            blueprint,
                            index);

            String id =
                    normalizeCanonical(
                            TrackBlueprintItem
                                    .segmentCanonicalItemId(
                                            segment));

            if (id.isEmpty()) {
                continue;
            }

            ItemStack sample =
                    TrackBlueprintItem
                            .segmentTrackStack(
                                    segment);

            if (sample.isEmpty()) {
                sample =
                        stackForCanonical(
                                id);
            }

            Need previous =
                    result.get(id);

            if (previous == null) {
                result.put(
                        id,
                        new Need(
                                sample,
                                1));
            } else {
                result.put(
                        id,
                        new Need(
                                previous.stack(),
                                previous.count()
                                        + 1));
            }
        }

        return result;
    }

    private static int matchingRackSlot(
            LegacyPoweredLocomotive locomotive,
            String id,
            Set<Integer> assigned) {
        for (int slot = 20;
             slot <= 51;
             slot++) {
            if (assigned.contains(slot)) {
                continue;
            }

            ItemStack stack =
                    locomotive
                            .getPowerInventory()
                            .getStackInSlot(slot);

            if (matches(
                    stack,
                    id)) {
                return slot;
            }
        }

        return -1;
    }

    private static int emptyRackSlot(
            LegacyPoweredLocomotive locomotive,
            Set<Integer> assigned) {
        for (int slot = 20;
             slot <= 51;
             slot++) {
            if (assigned.contains(slot)) {
                continue;
            }

            if (locomotive
                    .getPowerInventory()
                    .getStackInSlot(slot)
                    .isEmpty()) {
                return slot;
            }
        }

        return -1;
    }

    private static int availableCount(
            LegacyPoweredLocomotive locomotive,
            String id) {
        int count = 0;

        for (int slot = 20;
             slot <= 51;
             slot++) {
            ItemStack stack =
                    locomotive
                            .getPowerInventory()
                            .getStackInSlot(slot);

            if (matches(
                    stack,
                    id)) {
                count += stack.getCount();
            }
        }

        int[] legacySlots =
                {1, 8, 9, 10};

        for (int slot : legacySlots) {
            ItemStack stack =
                    locomotive
                            .getPowerInventory()
                            .getStackInSlot(slot);

            if (matches(
                    stack,
                    id)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    private static boolean matches(
            ItemStack stack,
            String id) {
        // TC_BATCH8_R4A5G6_BUILDER_RACK_MATCH_SYNC
        //
        // Keep the Blueprint Materials display aligned with the actual Builder
        // laying rules. Unified paired LEFT/RIGHT families already collapse to
        // the same canonical registry ID. 29x/32x are single registry IDs whose
        // hand is NBT, so hand does not make a second material requirement.
        if (stack == null
                || stack.isEmpty()) {
            return false;
        }

        ResourceLocation key =
                ForgeRegistries.ITEMS
                        .getKey(
                                stack.getItem());

        if (key == null) {
            return false;
        }

        String wanted =
                normalizeCanonical(id);

        String supplied =
                normalizeCanonical(
                        key.toString());

        // Exact canonical family/material remains first and authoritative.
        if (supplied.equals(wanted)) {
            return true;
        }

        ResourceLocation wantedKey =
                ResourceLocation
                        .tryParse(wanted);

        ResourceLocation suppliedKey =
                ResourceLocation
                        .tryParse(supplied);

        if (wantedKey == null
                || suppliedKey == null
                || !"traincraft".equals(
                        wantedKey.getNamespace())
                || !"traincraft".equals(
                        suppliedKey.getNamespace())) {
            return false;
        }

        String wantedQuarterBase =
                builderRackSingleIdQuarterBase(
                        wantedKey.getPath());

        // Only an UNSUFFIXED Blueprint 29x/32x geometry is material-flexible.
        // A Blueprint that explicitly saved _steel/_copper/_snow/_energy stays
        // exact-material, matching r4a5g4 Builder execution.
        if (wantedQuarterBase.isEmpty()
                || !wantedKey.getPath()
                        .equals(wantedQuarterBase)) {
            return false;
        }

        return wantedQuarterBase.equals(
                builderRackSingleIdQuarterBase(
                        suppliedKey.getPath()));
    }

    private static String builderRackSingleIdQuarterBase(
            String path) {
        if (path == null) {
            return "";
        }

        String[] bases = {
                "track_curve_29x",
                "track_curve_32x"
        };

        String[] suffixes = {
                "",
                "_steel",
                "_copper",
                "_snow",
                "_energy"
        };

        for (String base : bases) {
            for (String suffix : suffixes) {
                if (path.equals(base + suffix)) {
                    return base;
                }
            }
        }

        return "";
    }
    private static String normalizeCanonical(
            String raw) {
        if (raw == null) {
            return "";
        }

        String id =
                raw.trim();

        if (id.isEmpty()) {
            return "";
        }

        if (id.indexOf(':') < 0) {
            id =
                    "traincraft:" + id;
        }

        return TrackBlueprintItem
                .canonicalTrackId(id);
    }

    private static ItemStack stackForCanonical(
            String id) {
        ResourceLocation key =
                ResourceLocation
                        .tryParse(id);

        if (key == null) {
            return ItemStack.EMPTY;
        }

        Item item =
                ForgeRegistries.ITEMS
                        .getValue(key);

        return item == null
                ? ItemStack.EMPTY
                : new ItemStack(item);
    }

    private static void drawBorder(
            GuiGraphics graphics,
            int x,
            int y,
            int color) {
        graphics.fill(
                x - 1,
                y - 1,
                x + 17,
                y,
                color);
        graphics.fill(
                x - 1,
                y + 16,
                x + 17,
                y + 17,
                color);
        graphics.fill(
                x - 1,
                y,
                x,
                y + 16,
                color);
        graphics.fill(
                x + 16,
                y,
                x + 17,
                y + 16,
                color);
    }

    private static void drawCount(
            GuiGraphics graphics,
            Font font,
            int have,
            int need,
            int x,
            int y,
            int color) {
        String text =
                have + "/" + need;

        float scale =
                Math.min(
                        0.60F,
                        15.0F
                                / Math.max(
                                        1,
                                        font.width(text)));

        int width =
                (int)Math.ceil(
                        font.width(text)
                                * scale);

        graphics.pose().pushPose();
        graphics.pose().translate(
                x + 16 - width,
                y + 9,
                350.0F);
        graphics.pose().scale(
                scale,
                scale,
                1.0F);

        graphics.drawString(
                font,
                text,
                1,
                1,
                0xD0000000,
                false);

        graphics.drawString(
                font,
                text,
                0,
                0,
                color,
                false);

        graphics.pose().popPose();
    }

    private static String trim(
            String text,
            int max) {
        if (text == null) {
            return "";
        }

        if (text.length() <= max) {
            return text;
        }

        return text.substring(
                        0,
                        Math.max(
                                0,
                                max - 3))
                + "...";
    }

    private record Need(
            ItemStack stack,
            int count) {}

    private TracksBuilderBlueprintOverlay() {}
}
